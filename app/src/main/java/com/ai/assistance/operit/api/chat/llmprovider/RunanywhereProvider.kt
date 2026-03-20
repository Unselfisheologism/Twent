package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * Runanywhere本地AI模型Provider
 * 使用Runanywhere Kotlin SDK进行模型管理、下载和推理
 * 
 * 支持的功能:
 * - 从Runanywhere SDK发现可用模型
 * - 下载模型到本地
 * - 使用下载的模型进行推理
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

        // SDK是否已初始化
        @Volatile
        var isSdkInitialized = false
            private set

        // 已注册的模型列表
        @Volatile
        private var registeredModels = false

        /**
         * 初始化Runanywhere SDK
         * 需要在Application级别调用
         */
        fun initializeSdk(context: Context) {
            if (isSdkInitialized) return

            try {
                // Phase 0: Register backends
                val androidPlatformClass = Class.forName("ai.runanywhere.android.AndroidPlatformContext")
                val initializeMethod = androidPlatformClass.getMethod("initialize", Context::class.java)
                initializeMethod.invoke(null, context)

                // Register LlamaCPP backend
                val llamaCppClass = Class.forName("ai.runanywhere.llama.LlamaCPP")
                val registerMethod = llamaCppClass.getMethod("register", Int::class.java)
                registerMethod.invoke(null, 100)

                // Phase 1: Core initialization
                val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
                val initializeEnvMethod = runAnywhereClass.getMethod("initialize", String::class.java)
                // Use DEVELOPMENT mode for now - can be changed to PRODUCTION with API key
                initializeEnvMethod.invoke(null, "DEVELOPMENT")

                isSdkInitialized = true
                AppLogger.i(TAG, "Runanywhere SDK initialized successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize Runanywhere SDK: ${e.message}", e)
                isSdkInitialized = false
            }
        }

        /**
         * 注册可用模型到SDK
         * 需要在初始化SDK后调用
         */
        fun registerModels() {
            if (registeredModels) return
            if (!isSdkInitialized) {
                AppLogger.w(TAG, "Cannot register models - SDK not initialized")
                return
            }

            try {
                val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
                val registerModelMethod = runAnywhereClass.getMethod(
                    "registerModel",
                    String::class.java, // id
                    String::class.java, // name
                    String::class.java, // url
                    String::class.java, // framework
                    String::class.java, // modality
                    Long::class.java   // memoryRequirement
                )

                // Register language models from various sources
                val models = listOf(
                    // SmolLM2 models
                    Triple("smollm2-360m-q8_0", "SmolLM2 360M Q8_0", "https://huggingface.co/prithivMLmods/SmolLM2-360M-GGUF/resolve/main/SmolLM2-360M.Q8_0.gguf"),
                    Triple("smollm2-1.7b-q8_0", "SmolLM2 1.7B Q8_0", "https://huggingface.co/prithivMLmods/SmolLM2-1.7B-GGUF/resolve/main/SmolLM2-1.7B.Q8_0.gguf"),
                    // Qwen2.5 models
                    Triple("qwen2.5-0.5b-q8_0", "Qwen 2.5 0.5B Q8_0", "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0.5b-instruct-q5_k_m.gguf"),
                    Triple("qwen2.5-1.5b-q8_0", "Qwen 2.5 1.5B Q8_0", "https://huggingface.co/Qwen/Qwen2-1.5B-Instruct-GGUF/resolve/main/qwen2-1.5b-instruct-q5_k_m.gguf"),
                    // Llama 3.2 models
                    Triple("llama3.2-1b-q8_0", "Llama 3.2 1B Q8_0", "https://huggingface.co/ggml-org/llama-3.2-1b-instruct-q8_0/resolve/main/llama-3.2-1b-instruct-q8_0.gguf"),
                    Triple("llama3.2-3b-q8_0", "Llama 3.2 3B Q8_0", "https://huggingface.co/ggml-org/llama-3.2-3b-instruct-q8_0/resolve/main/llama-3.2-3b-instruct-q8_0.gguf"),
                    // Mistral models
                    Triple("mistral-7b-q8_0", "Mistral 7B Q8_0", "https://huggingface.co/TheBlok/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/mistral-7b-instruct-v0.3.Q8_0.gguf"),
                    // Phi-3 models
                    Triple("phi3-mini-q8_0", "Phi-3 Mini 4K Q8_0", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/phi-3-mini-4k-instruct-q8_0.gguf"),
                    // Gemma 2 models
                    Triple("gemma2-2b-q8_0", "Gemma 2 2B Q8_0", "https://huggingface.co/google/gemma-2-2b-it-gguf/resolve/main/gemma-2-2b-it-q8_0.gguf"),
                    // TinyLlama
                    Triple("tinyllama-1.1b-q8_0", "TinyLlama 1.1B Q8_0", "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.q8_0.gguf")
                )

                for ((id, name, url) in models) {
                    try {
                        registerModelMethod.invoke(
                            null,
                            id,
                            name,
                            url,
                            "LLAMA_CPP", // framework
                            "LANGUAGE",  // modality
                            500_000_000L // memory requirement ~500MB
                        )
                        AppLogger.d(TAG, "Registered model: $id")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to register model $id: ${e.message}")
                    }
                }

                registeredModels = true
                AppLogger.i(TAG, "Registered ${models.size} models to Runanywhere SDK")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to register models: ${e.message}", e)
            }
        }

        /**
         * 获取SDK是否可用
         */
        fun isSdkAvailable(): Boolean {
            return try {
                Class.forName("ai.runanywhere.RunAnywhere")
                isSdkInitialized
            } catch (e: ClassNotFoundException) {
                false
            }
        }

        /**
         * 获取SDK不可用原因
         */
        fun getUnavailableReason(): String {
            return if (isSdkAvailable()) {
                if (!isSdkInitialized) {
                    "Runanywhere SDK未初始化"
                } else {
                    ""
                }
            } else {
                "Runanywhere SDK未安装，请从应用商店下载完整版本以支持本地AI模型"
            }
        }

        /**
         * 获取可用的模型列表
         */
        suspend fun getAvailableModels(): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
            if (!isSdkInitialized) {
                return@withContext Result.failure(Exception("SDK未初始化"))
            }

            try {
                val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
                val availableModelsMethod = runAnywhereClass.getMethod("availableModels")
                @Suppress("UNCHECKED_CAST")
                val modelsList = availableModelsMethod.invoke(null) as? List<*>

                if (modelsList != null) {
                    val options = modelsList.mapNotNull { model ->
                        try {
                            // Each model has id and name properties
                            val modelClass = model!!::class.java
                            val getIdMethod = modelClass.getMethod("getId")
                            val getNameMethod = modelClass.getMethod("getName")
                            val id = getIdMethod.invoke(model) as? String
                            val name = getNameMethod.invoke(model) as? String
                            if (id != null && name != null) {
                                ModelOption(id = id, name = name)
                            } else null
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed to parse model: ${e.message}")
                            null
                        }
                    }
                    Result.success(options)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get available models: ${e.message}", e)
                Result.failure(e)
            }
        }

        /**
         * 获取已下载的模型列表
         */
        suspend fun getDownloadedModels(): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
            if (!isSdkInitialized) {
                return@withContext Result.failure(Exception("SDK未初始化"))
            }

            try {
                val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
                val downloadedModelsMethod = runAnywhereClass.getMethod("downloadedModels")
                @Suppress("UNCHECKED_CAST")
                val modelsList = downloadedModelsMethod.invoke(null) as? List<*>

                if (modelsList != null) {
                    val options = modelsList.mapNotNull { model ->
                        try {
                            val modelClass = model!!::class.java
                            val getIdMethod = modelClass.getMethod("getId")
                            val getNameMethod = modelClass.getMethod("getName")
                            val id = getIdMethod.invoke(model) as? String
                            val name = getNameMethod.invoke(model) as? String
                            if (id != null && name != null) {
                                ModelOption(id = id, name = name)
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    Result.success(options)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get downloaded models: ${e.message}", e)
                Result.failure(e)
            }
        }

        /**
         * 下载模型
         * @return Flow<DownloadProgress> - 通过反射获取
         */
        @Suppress("UNCHECKED_CAST")
        fun downloadModel(modelId: String): Flow<DownloadProgress>? {
            if (!isSdkInitialized) {
                AppLogger.w(TAG, "Cannot download - SDK not initialized")
                return null
            }

            return try {
                val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
                val downloadMethod = runAnywhereClass.getMethod("downloadModel", String::class.java)
                val flow = downloadMethod.invoke(null, modelId) as? Flow<*>
                
                // Convert to our DownloadProgress flow
                flow as? Flow<DownloadProgress>
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start model download: ${e.message}", e)
                null
            }
        }

        /**
         * 下载进度数据类
         */
        data class DownloadProgress(
            val modelId: String,
            val progress: Float, // 0.0 to 1.0
            val status: DownloadStatus,
            val message: String = ""
        )

        enum class DownloadStatus {
            DOWNLOADING,
            EXTRACTING,
            VALIDATING,
            COMPLETED,
            FAILED
        }
    }

    private var _inputTokenCount: Int = 0
    private var _outputTokenCount: Int = 0
    private var _cachedInputTokenCount: Int = 0

    @Volatile
    private var isCancelled = false

    private val sessionLock = Any()
    private var llmSession: Any? = null

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
        return getDownloadedModels()
    }

    override suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!isSdkAvailable()) {
            return@withContext Result.failure(Exception(getUnavailableReason()))
        }

        if (!isSdkInitialized) {
            return@withContext Result.failure(Exception("Runanywhere SDK未初始化，请重启应用"))
        }

        // Check if the selected model is downloaded
        val downloadedModels = getDownloadedModels()
        if (downloadedModels.isFailure) {
            return@withContext Result.failure(Exception("无法获取已下载模型列表"))
        }

        val modelList = downloadedModels.getOrNull() ?: emptyList()
        if (modelList.none { it.id == modelSlug }) {
            return@withContext Result.failure(
                Exception(context.getString(R.string.runanywhere_error_model_not_downloaded, modelSlug))
            )
        }

        // Test creating a session
        try {
            val testSession = createSession(modelSlug)
            if (testSession != null) {
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
        // Estimate tokens: Chinese ~1.5 chars/token, English ~4 chars/token
        return withContext(Dispatchers.IO) {
            val totalText = message + chatHistory.joinToString("") { it.second }
            val chineseChars = totalText.count { it.code > 127 }
            val asciiChars = totalText.length - chineseChars
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

        if (!isSdkInitialized) {
            emit("${context.getString(R.string.runanywhere_error_prefix)}: Runanywhere SDK未初始化")
            return@stream
        }

        // Check if model is downloaded
        val downloadedModels = getDownloadedModels()
        if (downloadedModels.isFailure) {
            emit("${context.getString(R.string.runanywhere_error_prefix)}: 无法获取模型列表")
            return@stream
        }

        val modelList = downloadedModels.getOrNull() ?: emptyList()
        if (modelList.none { it.id == modelSlug }) {
            emit("${context.getString(R.string.runanywhere_error_prefix)}: ${context.getString(R.string.runanywhere_error_model_not_downloaded, modelSlug)}")
            return@stream
        }

        val session = withContext(Dispatchers.IO) {
            ensureSessionLocked(modelSlug)
        }
        if (session == null) {
            emit(context.getString(R.string.runanywhere_error_session_create_failed))
            return@stream
        }

        // Build messages
        val messages = buildMessages(chatHistory, message)

        // Get sampling parameters
        val temperature = modelParameters
            .firstOrNull { it.id == "temperature" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f

        // Token estimation
        _inputTokenCount = estimateTokens(message + chatHistory.joinToString("") { it.second })
        _outputTokenCount = 0
        onTokensUpdated(_inputTokenCount, 0, 0)

        AppLogger.d(TAG, "开始Runanywhere推理，model=$modelSlug, history=${chatHistory.size}")

        var outputTokenCount = 0
        try {
            withContext(Dispatchers.IO) {
                if (stream) {
                    // Use streaming chat
                    try {
                        val streamChatMethod = session.javaClass.getMethod("streamChat", String::class.java)
                        @Suppress("UNCHECKED_CAST")
                        val flow = streamChatMethod.invoke(session, message) as? Flow<String>
                        
                        flow?.collectLatest { token ->
                            if (isCancelled) return@collectLatest
                            outputTokenCount++
                            _outputTokenCount = outputTokenCount
                            runBlocking {
                                emit(token)
                                onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Streaming not available, falling back to non-streaming: ${e.message}")
                        // Fall back to non-streaming
                        val chatMethod = session.javaClass.getMethod("chat", String::class.java)
                        val result = chatMethod.invoke(session, message) as? String
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
                    }
                } else {
                    // Non-streaming chat
                    val chatMethod = session.javaClass.getMethod("chat", String::class.java)
                    val result = chatMethod.invoke(session, message) as? String
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
     */
    private fun estimateTokens(text: String): Int {
        val chineseChars = text.count { it.code > 127 }
        val asciiChars = text.length - chineseChars
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

    private fun createSession(modelId: String): Any? {
        return try {
            val runAnywhereClass = Class.forName("ai.runanywhere.RunAnywhere")
            val getInstanceMethod = runAnywhereClass.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            
            val loadLLMMethod = instance.javaClass.getMethod(
                "loadLLMModel",
                String::class.java
            )
            
            loadLLMMethod.invoke(instance, modelId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建Runanywhere会话失败: ${e.message}", e)
            null
        }
    }

    private fun ensureSessionLocked(modelId: String): Any? {
        synchronized(sessionLock) {
            llmSession?.let { return it }
            
            val created = createSession(modelId)
            llmSession = created
            return created
        }
    }
}
