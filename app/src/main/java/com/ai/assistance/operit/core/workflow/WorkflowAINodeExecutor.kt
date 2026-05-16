package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.PromptFunctionType
import com.ai.assistance.operit.api.chat.llmprovider.FunctionType
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader

/**
 * AI node executor — calls EnhancedAIService from within a workflow.
 */
class WorkflowAINodeExecutor private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WorkflowAINodeExecutor"

        @Volatile
        private var instance: WorkflowAINodeExecutor? = null

        fun getInstance(context: Context): WorkflowAINodeExecutor {
            return instance ?: synchronized(this) {
                instance ?: WorkflowAINodeExecutor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    suspend fun execute(
        node: AINode,
        triggerExtras: Map<String, String>,
        workflowId: String
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Executing AI node: ${node.name}")

            // Resolve ${nodeId} references in prompt
            val resolvedPrompt = resolveParam(node.prompt, triggerExtras)
            if (resolvedPrompt.isEmpty()) {
                return@withContext NodeExecutionState.Failed("Prompt is empty")
            }

            val maxTokens = if (node.maxTokens > 0) node.maxTokens else 4096

            // Use stream=false for synchronous result
            val responseStream: Stream<String> = EnhancedAIService.sendMessage(
                message = resolvedPrompt,
                chatId = "workflow_${workflowId}_${node.id}",
                chatHistory = emptyList(),
                systemPrompt = node.systemPrompt,
                functionType = FunctionType.CHAT,
                promptFunctionType = PromptFunctionType.CHAT,
                enableThinking = false,
                enableMemoryQuery = false,
                maxTokens = maxTokens,
                tokenUsageThreshold = 0.9,
                customSystemPromptTemplate = null,
                isSubTask = false,
                stream = false
            )

            // Stream<String> is a Java functional interface — consume synchronously
            val responseBuilder = StringBuilder()
            val iterator = responseStream.iterator()
            while (iterator.hasNext()) {
                responseBuilder.append(iterator.next())
            }

            val response = responseBuilder.toString()
            AppLogger.d(TAG, "AI node response length: ${response.length}")

            return@withContext if (response.isNotEmpty()) {
                NodeExecutionState.Success(response)
            } else {
                NodeExecutionState.Failed("Empty response from AI")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "AI node execution failed: ${e.message}", e)
            return@withContext NodeExecutionState.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Resolve ${nodeId} references in a string using triggerExtras (node results).
     */
    private fun resolveParam(param: String, triggerExtras: Map<String, String>): String {
        val regex = Regex("\$\{(\w+)}")
        var result = param
        for (match in regex.findAll(param)) {
            val nodeId = match.groupValues[1]
            val value = triggerExtras[nodeId]
            if (value != null) {
                result = result.replace(match.value, value)
            }
        }
        return result
    }
}
