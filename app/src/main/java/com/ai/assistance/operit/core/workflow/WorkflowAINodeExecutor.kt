package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ToolPrompt
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Executor for AINode in workflow system.
 * Calls the same EnhancedAIService that AI Chat uses.
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

    /**
     * Executes an AI node in a workflow.
     *
     * @param node The AI node to execute
     * @param nodeResults Map of previous node execution results
     * @param triggerExtras Map of trigger extras (e.g., image data)
     * @param workflowId The workflow ID for logging
     * @return NodeExecutionState indicating success, failure, or skip
     */
    suspend fun execute(
        node: AINode,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>,
        workflowId: String
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Executing AI node: ${node.id}, workflow: $workflowId")

        try {
            // Validate node configuration
            if (node.prompt.isBlank() && node.taskType != "analyze_image") {
                AppLogger.w(TAG, "AI node ${node.id} has empty prompt")
                return@withContext NodeExecutionState.Skipped("Empty prompt")
            }

            // Resolve the prompt with parameter values
            val resolvedPrompt = resolveParameterValue(
                ParameterValue.StaticValue(node.prompt),
                nodeResults,
                triggerExtras
            )

            AppLogger.d(TAG, "Resolved prompt for node ${node.id}: ${resolvedPrompt.take(100)}...")

            // Resolve system prompt
            val resolvedSystemPrompt = node.systemPrompt.takeIf { it.isNotBlank() }
                ?.let { resolveParameterValue(ParameterValue.StaticValue(it), nodeResults, triggerExtras) }

            // Build available tools if enabled
            val availableTools = if (node.enableTools) {
                buildToolsList(node.enabledTools)
            } else {
                null
            }

            // Execute the AI service
            val response = executeAI(
                message = resolvedPrompt,
                systemPrompt = resolvedSystemPrompt,
                availableTools = availableTools,
                maxTokens = if (node.maxTokens > 0) node.maxTokens else 4096,
                temperature = if (node.temperature > 0f) node.temperature else 0.7f,
                timeoutMs = node.timeoutMs
            )

            if (response != null && response.isNotBlank()) {
                AppLogger.d(TAG, "AI node ${node.id} executed successfully, response length: ${response.length}")
                NodeExecutionState.Success(response)
            } else {
                AppLogger.e(TAG, "AI node ${node.id} returned null/empty response")
                NodeExecutionState.Failed("Empty response from AI service")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing AI node ${node.id}: ${e.message}", e)
            NodeExecutionState.Failed("Error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Resolves a parameter value, handling both static values and node references.
     */
    private fun resolveParameterValue(
        param: ParameterValue,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>
    ): String {
        return when (param) {
            is ParameterValue.StaticValue -> param.value

            is ParameterValue.NodeReference -> {
                val nodeId = param.nodeId
                val result = nodeResults[nodeId]
                when (result) {
                    is NodeExecutionState.Success -> result.result?.toString() ?: ""
                    is NodeExecutionState.Failed -> {
                        AppLogger.w(TAG, "Referenced node $nodeId failed: ${result.error}")
                        "[Error: ${result.error}]"
                    }
                    is NodeExecutionState.Skipped -> {
                        AppLogger.w(TAG, "Referenced node $nodeId was skipped: ${result.reason}")
                        "[Skipped: ${result.reason}]"
                    }
                    else -> "[Node $nodeId not executed]"
                }
            }

            is ParameterValue.TriggerExtra -> {
                triggerExtras[param.key] ?: param.defaultValue ?: ""
            }
        }
    }

    /**
     * Builds the list of available tools based on node configuration.
     */
    private fun buildToolsList(enabledToolNames: List<String>): List<ToolPrompt>? {
        return try {
            val toolHandler = AIToolHandler.getInstance(context)
            val allTools = toolHandler.getAvailableTools()

            if (enabledToolNames.isEmpty()) {
                allTools
            } else {
                allTools.filter { tool -> enabledToolNames.contains(tool.name) }
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error building tools list: ${e.message}", e)
            null
        }
    }

    /**
     * Executes the AI service call with timeout and collects the response.
     */
    private suspend fun executeAI(
        message: String,
        systemPrompt: String?,
        availableTools: List<ToolPrompt>?,
        maxTokens: Int,
        temperature: Float,
        timeoutMs: Long
    ): String? = withTimeout(timeoutMs.coerceAtLeast(1000L)) {
        val enhancedService = EnhancedAIService.getInstance(context)
        val chatHistory: List<Pair<String, String>> = if (systemPrompt != null) {
            listOf("system" to systemPrompt)
        } else {
            emptyList()
        }

        val stream: Stream<String> = enhancedService.sendMessage(
            message = message,
            chatHistory = chatHistory,
            functionType = FunctionType.CHAT,
            enableThinking = false,
            maxTokens = maxTokens,
            tokenUsageThreshold = 0.9,
            availableTools = availableTools
        )

        val responseBuilder = StringBuilder()
        stream.collect { chunk ->
            responseBuilder.append(chunk)
        }

        responseBuilder.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Clears the singleton instance (useful for testing).
     */
    fun clearInstance() {
        synchronized(this) {
            instance = null
        }
    }
}