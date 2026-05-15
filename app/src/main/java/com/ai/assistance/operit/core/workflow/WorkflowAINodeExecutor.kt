package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.model.AIMessage
import com.ai.assistance.operit.api.chat.llmprovider.model.ContentBlock
import com.ai.assistance.operit.api.chat.llmprovider.model.FunctionType
import com.ai.assistance.operit.api.chat.llmprovider.model.ModelConfigData
import com.ai.assistance.operit.api.chat.llmprovider.model.ModelParameters
import com.ai.assistance.operit.api.chat.llmprovider.model.ToolPrompt
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.workflow.model.AINode
import com.ai.assistance.operit.core.workflow.model.NodeExecutionState
import com.ai.assistance.operit.core.workflow.model.ParameterValue
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

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

            // Get AI service (default or custom model)
            val aiService = getAIService(node)
            
            // Build prompt with resolved parameter references
            val resolvedPrompt = resolveParameterValue(
                ParameterValue.StaticValue(node.prompt),
                nodeResults,
                triggerExtras
            )
            
            AppLogger.d(TAG, "Resolved prompt for node ${node.id}: ${resolvedPrompt.take(100)}...")

            // Build content blocks based on task type
            val contentBlocks = buildContentBlocks(node, resolvedPrompt, triggerExtras)

            // Build system prompt
            val systemPrompt = node.systemPrompt.takeIf { it.isNotBlank() }

            // Build available tools if enabled
            val availableTools = if (node.enableTools) {
                buildToolsList(node.enabledTools)
            } else {
                emptyList()
            }

            // Build model parameters
            val modelParameters = ModelParameters(
                maxTokens = if (node.maxTokens > 0) node.maxTokens else 4096,
                temperature = if (node.temperature > 0f) node.temperature else 0.7f
            )

            // Create message
            val message = AIMessage(
                role = "user",
                content = resolvedPrompt,
                contentBlocks = contentBlocks.takeIf { it.isNotEmpty() }
            )

            // Execute with timeout
            val response = executeWithTimeout(
                aiService = aiService,
                message = message,
                systemPrompt = systemPrompt,
                modelParameters = modelParameters,
                availableTools = availableTools,
                timeoutMs = node.timeoutMs
            )

            if (response != null) {
                AppLogger.d(TAG, "AI node ${node.id} executed successfully, response length: ${response.length}")
                NodeExecutionState.Success(response)
            } else {
                AppLogger.e(TAG, "AI node ${node.id} returned null response")
                NodeExecutionState.Failed("Empty response from AI service")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing AI node ${node.id}: ${e.message}", e)
            NodeExecutionState.Failed("Error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Resolves a parameter value, handling both static values and node references.
     *
     * @param param The parameter value to resolve
     * @param nodeResults Map of previous node execution results
     * @param triggerExtras Map of trigger extras
     * @return The resolved string value
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
     * Resolves parameter references in a text template.
     *
     * @param template The text template with potential parameter references
     * @param nodeResults Map of previous node execution results
     * @param triggerExtras Map of trigger extras
     * @return The resolved text
     */
    private fun resolveTemplate(
        template: String,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>
    ): String {
        var result = template
        
        // Pattern: ${nodeId} or ${nodeId:default}
        val nodeRefPattern = Regex("\\\$\\\\$\\\\{([^:}]+)(?::([^}]*))?}")
        result = nodeRefPattern.replace(result) { match ->
            val nodeId = match.groupValues[1]
            val defaultValue = match.groupValues[2]
            val resolvedResult = nodeResults[nodeId]
            when (resolvedResult) {
                is NodeExecutionState.Success -> resolvedResult.result?.toString() ?: defaultValue
                else -> defaultValue
            }
        }
        
        // Pattern: ${extra:key} or ${extra:key:default}
        val extraPattern = Regex("\\\$\\\\$extra:([^:}]+)(?::([^}]*))?}")
        result = extraPattern.replace(result) { match ->
            val key = match.groupValues[1]
            val defaultValue = match.groupValues[2]
            triggerExtras[key] ?: defaultValue ?: ""
        }
        
        return result
    }

    /**
     * Gets the AI service for the node.
     * If node.modelId is specified, creates a service with that specific model.
     * Otherwise, uses the default chat service.
     */
    private fun getAIService(node: AINode): AIService {
        val enhancedService = EnhancedAIService.getInstance(context)
        
        return if (node.modelId.isNotBlank()) {
            try {
                val modelConfig = parseModelId(node.modelId)
                if (modelConfig != null) {
                    AppLogger.d(TAG, "Creating custom AI service for model: ${modelConfig.provider}:${modelConfig.modelName}")
                    enhancedService.multiServiceManager.createServiceForModel(modelConfig)
                } else {
                    AppLogger.w(TAG, "Failed to parse modelId, using default service")
                    enhancedService.multiServiceManager.getServiceForFunction(FunctionType.CHAT)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error creating custom service: ${e.message}", e)
                enhancedService.multiServiceManager.getServiceForFunction(FunctionType.CHAT)
            }
        } else {
            enhancedService.multiServiceManager.getServiceForFunction(FunctionType.CHAT)
        }
    }

    /**
     * Parses a modelId in format "provider:model" (e.g., "openai:gpt-4o").
     *
     * @param modelId The model ID to parse
     * @return ModelConfigData or null if parsing fails
     */
    private fun parseModelId(modelId: String): ModelConfigData? {
        return try {
            val parts = modelId.split(":", limit = 2)
            if (parts.size == 2) {
                val provider = parts[0].trim()
                val modelName = parts[1].trim()
                if (provider.isNotBlank() && modelName.isNotBlank()) {
                    ModelConfigData(
                        name = modelId,
                        provider = provider,
                        modelName = modelName
                    )
                } else {
                    null
                }
            } else {
                // If no provider specified, try to infer from model name
                val modelName = modelId.trim()
                val inferredProvider = inferProvider(modelName)
                ModelConfigData(
                    name = modelId,
                    provider = inferredProvider,
                    modelName = modelName
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing modelId '$modelId': ${e.message}", e)
            null
        }
    }

    /**
     * Infers the provider from the model name.
     */
    private fun inferProvider(modelName: String): String {
        val lowerName = modelName.lowercase()
        return when {
            lowerName.contains("gpt") -> "openai"
            lowerName.contains("claude") -> "anthropic"
            lowerName.contains("gemini") -> "google"
            lowerName.contains("llama") -> "ollama"
            lowerName.contains("mistral") -> "mistral"
            lowerName.contains("qwen") -> "qwen"
            lowerName.contains("deepseek") -> "deepseek"
            else -> "openai" // Default fallback
        }
    }

    /**
     * Builds content blocks based on the task type.
     */
    private fun buildContentBlocks(
        node: AINode,
        prompt: String,
        triggerExtras: Map<String, String>
    ): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        when (node.taskType) {
            "analyze_image" -> {
                // Build content blocks for image analysis
                val imageBase64 = triggerExtras["image_base64"]
                val imageUrl = triggerExtras["image_url"]

                if (!imageBase64.isNullOrBlank()) {
                    blocks.add(
                        ContentBlock(
                            type = "image",
                            text = prompt,
                            imageBase64 = imageBase64
                        )
                    )
                } else if (!imageUrl.isNullOrBlank()) {
                    blocks.add(
                        ContentBlock(
                            type = "image_url",
                            text = prompt,
                            imageUrl = imageUrl
                        )
                    )
                } else {
                    // Fallback to text if no image provided
                    blocks.add(
                        ContentBlock(
                            type = "text",
                            text = prompt
                        )
                    )
                }
            }

            "classify" -> {
                blocks.add(
                    ContentBlock(
                        type = "text",
                        text = prompt
                    )
                )
            }

            "embed" -> {
                blocks.add(
                    ContentBlock(
                        type = "text",
                        text = prompt
                    )
                )
            }

            "reasoning" -> {
                blocks.add(
                    ContentBlock(
                        type = "text",
                        text = prompt
                    )
                )
            }

            else -> {
                // Default: generate_text
                blocks.add(
                    ContentBlock(
                        type = "text",
                        text = prompt
                    )
                )
            }
        }

        return blocks
    }

    /**
     * Builds the list of available tools based on node configuration.
     */
    private fun buildToolsList(enabledToolNames: List<String>): List<ToolPrompt> {
        return try {
            val toolHandler = AIToolHandler.getInstance(context)
            val allTools = toolHandler.getAvailableTools()

            if (enabledToolNames.isEmpty()) {
                // Return all available tools if none specified
                allTools.map { tool ->
                    ToolPrompt(
                        name = tool.name,
                        description = tool.description,
                        parameters = tool.parameters
                    )
                }
            } else {
                // Filter to only enabled tools
                allTools
                    .filter { tool -> enabledToolNames.contains(tool.name) }
                    .map { tool ->
                        ToolPrompt(
                            name = tool.name,
                            description = tool.description,
                            parameters = tool.parameters
                        )
                    }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error building tools list: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Executes the AI service call with timeout and collects the response.
     */
    private suspend fun executeWithTimeout(
        aiService: AIService,
        message: AIMessage,
        systemPrompt: String?,
        modelParameters: ModelParameters,
        availableTools: List<ToolPrompt>,
        timeoutMs: Long
    ): String? = withTimeout(timeoutMs.coerceAtLeast(1000L)) {
        try {
            val responseDeferred = CompletableDeferred<String?>()
            val errorRef = AtomicReference<String?>(null)
            val semaphore = Semaphore(1)

            // Launch a coroutine to handle the stream
            val job = launch {
                try {
                    semaphore.withPermit {
                        val stream: Flow<String> = aiService.sendMessage(
                            context = context,
                            message = message,
                            chatHistory = emptyList(),
                            systemPrompt = systemPrompt,
                            modelParameters = modelParameters,
                            enableThinking = false,
                            stream = false,
                            availableTools = availableTools.takeIf { it.isNotEmpty() }
                        )

                        // Collect the full response
                        val responseBuilder = StringBuilder()
                        stream.onCompletion { throwable ->
                            if (throwable != null) {
                                errorRef.set(throwable.message)
                            }
                        }.collect { chunk ->
                            responseBuilder.append(chunk)
                        }

                        val fullResponse = responseBuilder.toString()
                        if (fullResponse.isNotBlank()) {
                            responseDeferred.complete(fullResponse)
                        } else {
                            responseDeferred.complete(null)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error in stream collection: ${e.message}", e)
                    errorRef.set(e.message)
                    responseDeferred.complete(null)
                }
            }

            // Wait for completion or timeout
            val result = responseDeferred.await()
            job.cancel() // Ensure the job is cancelled if we got a result

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing AI service: ${e.message}", e)
            null
        }
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
