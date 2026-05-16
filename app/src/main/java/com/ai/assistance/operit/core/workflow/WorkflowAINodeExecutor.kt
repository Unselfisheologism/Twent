package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.data.model.AITaskType
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

/**
 * AI节点执行器 - 在工作流中调用AI服务
 */
class WorkflowAINodeExecutor(
    private val context: Context
) {
    companion object {
        private const val TAG = "WorkflowAINodeExecutor"
    }

    suspend fun execute(
        node: AINode,
        triggerExtras: Map<String, NodeExecutionState>,
        workflowId: String
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Executing AI node: ${node.name}")

            // 解析参数
            val userPrompt = resolveStringParam(node.userPrompt, triggerExtras)
            val systemPrompt = node.systemPrompt?.let { resolveStringParam(it, triggerExtras) }

            if (userPrompt.isEmpty()) {
                return@withContext NodeExecutionState.Failed(
                    nodeId = node.id,
                    errorMessage = "User prompt is empty",
                    result = null
                )
            }

            // 使用 EnhancedAIService 发送消息（内部处理工具调用循环）
            val aiService = EnhancedAIService.getInstance(context)

            // 将 workflowId 作为 chatId，确保同一工作流使用相同的会话
            val chatId = "workflow_${workflowId}_${node.id}"

            // 构建聊天历史（如果需要）
            val chatHistory = node.chatHistory.take(10).map { entry ->
                entry.key to entry.value
            }

            // 计算 maxTokens
            val maxTokens = node.maxTokens ?: 4096

            // 使用非流式模式获取完整响应
            val responseBuilder = StringBuilder()
            var toolCallCount = 0
            val maxToolCalls = 20 // 防止无限循环

            // 直接调用 sendMessage，让 EnhancedAIService 内部处理工具调用循环
            // 设置 stream=false 获取完整响应
            val responseFlow = aiService.sendMessage(
                message = userPrompt,
                chatId = chatId,
                chatHistory = chatHistory,
                functionType = com.ai.assistance.operit.api.chat.llmprovider.FunctionType.CHAT,
                promptFunctionType = com.ai.assistance.operit.api.chat.PromptFunctionType.CHAT,
                enableThinking = false,
                enableMemoryQuery = false, // 工作流中禁用记忆
                maxTokens = maxTokens,
                tokenUsageThreshold = 0.9,
                customSystemPromptTemplate = systemPrompt,
                isSubTask = false,
                stream = false // 非流式，获取完整响应
            )

            // 收集完整响应
            responseFlow.onCompletion { error ->
                if (error != null) {
                    AppLogger.e(TAG, "Stream error: ${error.message}")
                }
            }.collect { chunk ->
                responseBuilder.append(chunk)
                toolCallCount++
                if (toolCallCount > maxToolCalls) {
                    AppLogger.w(TAG, "Tool call limit reached, stopping")
                }
            }

            val response = responseBuilder.toString()

            AppLogger.d(TAG, "AI node response length: ${response.length}")

            return@withContext if (response.isNotEmpty()) {
                NodeExecutionState.Success(
                    nodeId = node.id,
                    output = response,
                    metadata = mapOf(
                        "taskType" to node.taskType.name,
                        "chatId" to chatId,
                        "responseLength" to response.length
                    )
                )
            } else {
                NodeExecutionState.Failed(
                    nodeId = node.id,
                    errorMessage = "Empty response from AI",
                    result = null
                )
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "AI node execution failed: ${e.message}", e)
            return@withContext NodeExecutionState.Failed(
                nodeId = node.id,
                errorMessage = e.message ?: "Unknown error",
                result = null
            )
        }
    }

    /**
     * 解析参数值 - 支持静态值、节点引用、触发器额外数据
     */
    private fun resolveStringParam(param: ParameterValue, triggerExtras: Map<String, NodeExecutionState>): String {
        return when (param) {
            is ParameterValue.StaticValue -> param.value
            is ParameterValue.NodeReference -> {
                val referencedState = triggerExtras[param.nodeId]
                when (referencedState) {
                    is NodeExecutionState.Success -> referencedState.output ?: ""
                    else -> ""
                }
            }
            is ParameterValue.TriggerExtra -> param.defaultValue ?: ""
        }
    }
}