package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Runanywhere本地AI模型Provider
 * 支持Qwen 2.5, Llama 3.2, Mistral等本地模型
 * 
 * 注意: 此Provider需要Runanywhere SDK依赖
 * 当前为框架实现，需添加SDK后完善
 */
class RunanywhereProvider(
    private val context: Context,
    private val modelSlug: String,
    private val modelName: String,
    private val threadCount: Int,
    private val contextSize: Int,
    private val providerType: ApiProviderType = ApiProviderType.RUNANYWHERE
) : AIService {

    companion object {
        private const val TAG = "RunanywhereProvider"

        // 可用的Runanywhere模型列表
        // 注意: modelSlug需要与下载的GGUF文件名匹配（不含扩展名）
        val AVAILABLE_MODELS = listOf(
            ModelOption(
                id = "qwen2.5-0.5b",
                name = "Qwen 2.5 0.5B"
            ),
            ModelOption(
                id = "qwen2.5-1.5b",
                name = "Qwen 2.5 1.5B"
            ),
            ModelOption(
                id = "llama3.2-1b",
                name = "Llama 3.2 1B"
            ),
            ModelOption(
                id = "mistral-7b-q4",
                name = "Mistral 7B Q4"
            ),
            ModelOption(
                id = "smollm2-360m",
                name = "SmolLM2 360M"
            )
        )

        fun getModelsDir(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/runanywhere"
            )
        }

        /**
         * 获取模型文件路径
         * @param modelSlug 模型标识符（来自AVAILABLE_MODELS的id）
         * @return 模型文件对象
         */
        fun getModelFile(modelSlug: String): File {
            return File(getModelsDir(), "$modelSlug.gguf")
        }

        /**
         * 列出Models目录中实际存在的GGUF模型文件
         * @return 可用的模型文件列表（不含.gguf扩展名）
         */
        fun getAvailableModelFiles(): List<String> {
            val modelsDir = getModelsDir()
            if (!modelsDir.exists()) {
                return emptyList()
            }
            return modelsDir.listFiles()
                ?.filter { it.extension == "gguf" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }

        /**
         * 检查Runanywhere SDK是否可用
         * 注意: 此实现允许在没有SDK的情况下构建APK，通过运行时反射调用
         */
        fun isSdkAvailable(): Boolean {
            return try {
                // 尝试加载Runanywhere类
                Class.forName("ai.runanywhere.RunAnywhere")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

        /**
         * 获取SDK不可用的原因
         */
        fun getUnavailableReason(): String {
            return if (isSdkAvailable()) {
                ""
            } else {
                // 允许无SDK运行，但提示用户需要SDK才能进行推理
                "Runanywhere SDK未安装，请从应用商店下载完整版本以支持本地AI模型"
            }
        }
    }

    private var _inputTokenCount: Int = 0
    private var _outputTokenCount: Int = 0
    private var _cachedInputTokenCount: Int = 0

    @Volatile
    private var isCancelled = false

    private val sessionLock = Any()
    private var llmSession: Any? = null // RunAnywhereLLMSession

    override val inputTokenCount: Int
        get() = _inputTokenCount

    override val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount

    override val outputTokenCount: Int
        get() = _outputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
        _cachedInputTokenCount = 0
    }

    override fun cancelStreaming() {
        isCancelled = true
        synchronized(sessionLock) {
            try {
                llmSession?.let { session ->
                    // 调用取消方法
                    val method = session.javaClass.getMethod("cancel")
                    method.invoke(session)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "取消推理失败: ${e.message}")
            }
        }
    }

    override fun release() {
        synchronized(sessionLock) {
            try {
                llmSession?.let { session ->
                    // 调用释放方法
                    val method = session.javaClass.getMethod("unload")
                    method.invoke(session)
                }
                llmSession = null
            } catch (e: Exception) {
                AppLogger.w(TAG, "释放会话失败: ${e.message}")
            }
        }
    }

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return Result.success(AVAILABLE_MODELS)
    }

    override suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!isSdkAvailable()) {
            return@withContext Result.failure(Exception(getUnavailableReason()))
        }

        val modelFile = getModelFile(modelSlug)
        if (!modelFile.exists()) {
            return@withContext Result.failure(
                Exception(context.getString(R.string.runanywhere_error_model_file_not_exist, modelFile.absolutePath))
            )
        }

        // 测试创建会话
        try {
            val testSession = createSession(modelFile.absolutePath)
            if (testSession != null) {
                // 释放测试会话
                val unloadMethod = testSession.javaClass.getMethod("unload")
                unloadMethod.invoke(testSession)
                Result.success("Runanywhere backend is available.")
            } else {
                Result.failure(Exception(context.getString(R.string.runanywhere_error_create_session_failed)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Runanywhere测试失败: ${e.message}"))
        }
    }

    override suspend fun calculateInputTokens(
        message: String,
        chatHistory: List<Pair<String, String>>,
        availableTools: List<ToolPrompt>?
    ): Int {
        // 使用更准确的估算：中文约1.5字符/token，英文约4字符/token
        // 这里使用平均值约3字符/token作为估算
        return withContext(Dispatchers.IO) {
            val totalText = message + chatHistory.joinToString("") { it.second }
            // 更准确的估算：基于中英文混合文本的平均值
            val chineseChars = totalText.count { it.code > 127 }
            val asciiChars = totalText.length - chineseChars
            // 中文约1.5 token/字符，英文约4 token/字符
            ((chineseChars * 1.5) + (asciiChars * 0.25)).toInt().coerceAtLeast(1)
        }
    }

    override suspend fun sendMessage(
        context: Context,
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isCancelled = false

        if (!isSdkAvailable()) {
            emit("${context.getString(R.string.runanywhere_error_prefix)}: ${getUnavailableReason()}")
            return@stream
        }

        val modelFile = getModelFile(modelSlug)
        if (!modelFile.exists()) {
            emit("${context.getString(R.string.runanywhere_error_prefix)}: ${context.getString(R.string.runanywhere_error_model_file_not_exist, modelFile.absolutePath)}")
            return@stream
        }

        val session = withContext(Dispatchers.IO) {
            ensureSessionLocked(modelFile.absolutePath)
        }
        if (session == null) {
            emit(context.getString(R.string.runanywhere_error_session_create_failed))
            return@stream
        }

        // 构建消息格式
        val messages = buildMessages(chatHistory, message)

        // 获取采样参数
        val temperature = modelParameters
            .firstOrNull { it.id == "temperature" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        // topP保持为Float，传给SDK
        val topP = modelParameters
            .firstOrNull { it.id == "top_p" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f

        // 改进的token估算
        _inputTokenCount = estimateTokens(message + chatHistory.joinToString("") { it.second })
        _outputTokenCount = 0
        onTokensUpdated(_inputTokenCount, 0, 0)

        AppLogger.d(TAG, "开始Runanywhere推理，model=$modelSlug, history=${chatHistory.size}")

        var outputTokenCount = 0
        try {
            withContext(Dispatchers.IO) {
                // 尝试查找并调用流式生成方法
                // Runanywhere SDK的API可能变化，这里使用更健壮的反射调用
                try {
                    val generateMethod = session.javaClass.getMethod(
                        "generateCompletion",
                        Map::class.java,
                        Integer::class.java
                    )
                    
                    @Suppress("UNCHECKED_CAST")
                    val result = generateMethod.invoke(
                        session,
                        messages as Map<String, Any>,
                        temperature.toInt()
                    ) as? String
                    
                    // 非流式结果，逐字发送以模拟流式
                    result?.let { response ->
                        response.forEach { char ->
                            if (isCancelled) return@forEach
                            outputTokenCount++
                            _outputTokenCount = outputTokenCount
                            runBlocking {
                                emit(char.toString())
                                onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                            }
                        }
                    }
                } catch (e: NoSuchMethodException) {
                    // 尝试带回调的方法
                    AppLogger.d(TAG, "尝试使用回调方法")
                    try {
                        val callbackClass = Class.forName("ai.runanywhere.Callback")
                        val generateMethod = session.javaClass.getMethod(
                            "generateCompletion",
                            Map::class.java,
                            Integer::class.java,
                            callbackClass
                        )
                        
                        // 创建回调对象
                        val callback = java.lang.reflect.Proxy.newProxyInstance(
                            callbackClass.classLoader,
                            arrayOf(callbackClass)
                        ) { _, _, args ->
                            if (isCancelled) {
                                false
                            } else {
                                val token = args[0] as? String
                                token?.let {
                                    outputTokenCount++
                                    _outputTokenCount = outputTokenCount
                                    runBlocking {
                                        emit(it)
                                        onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                                    }
                                }
                                true
                            }
                        }
                        
                        @Suppress("UNCHECKED_CAST")
                        generateMethod.invoke(
                            session,
                            messages as Map<String, Any>,
                            temperature.toInt(),
                            callback
                        )
                    } catch (e2: Exception) {
                        AppLogger.e(TAG, "无法调用生成方法: ${e2.message}", e2)
                        throw e2
                    }
                }
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                AppLogger.e(TAG, "推理失败: ${e.message}", e)
                runBlocking {
                    onNonFatalError(context.getString(R.string.runanywhere_error_inference_failed))
                }
                emit("\n\n${context.getString(R.string.runanywhere_error_inference_tag)}")
            }
        }

        AppLogger.i(TAG, "Runanywhere推理完成，输出token数: $_outputTokenCount")
    }

    /**
     * 估算文本的token数量
     * 使用中英文混合的估算方法
     */
    private fun estimateTokens(text: String): Int {
        val chineseChars = text.count { it.code > 127 }
        val asciiChars = text.length - chineseChars
        // 中文约1.5 token/字符，英文约4 token/字符
        return ((chineseChars * 1.5) + (asciiChars * 0.25)).toInt().coerceAtLeast(1)
    }

    private fun buildMessages(chatHistory: List<Pair<String, String>>, currentMessage: String): Map<String, Any> {
        val messages = mutableListOf<Map<String, String>>()
        
        for ((role, content) in chatHistory) {
            messages.add(mapOf(
                "role" to role,
                "content" to content
            ))
        }
        
        messages.add(mapOf(
            "role" to "user",
            "content" to currentMessage
        ))
        
        return mapOf("messages" to messages)
    }

    private fun createSession(modelPath: String): Any? {
        return try {
            val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
            val getInstanceMethod = runAnywhereClass.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            
            val loadLLMMethod = instance.javaClass.getMethod(
                "loadLLMModel",
                String::class.java,
                Int::class.java,
                Int::class.java
            )
            
            loadLLMMethod.invoke(instance, modelPath, threadCount, contextSize)
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建Runanywhere会话失败: ${e.message}", e)
            null
        }
    }

    private fun ensureSessionLocked(modelPath: String): Any? {
        synchronized(sessionLock) {
            llmSession?.let { return it }
            
            val created = createSession(modelPath)
            llmSession = created
            return created
        }
    }
}
