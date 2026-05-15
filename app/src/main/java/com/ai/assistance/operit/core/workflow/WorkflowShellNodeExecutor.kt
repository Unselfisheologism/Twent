package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.data.model.ExecuteShellNode
import com.ai.assistance.operit.data.model.NodeExecutionState
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Executor for ExecuteShellNode in workflow system.
 * Executes shell commands using the shared Terminal infrastructure.
 */
class WorkflowShellNodeExecutor private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WorkflowShellNodeExecutor"
        
        @Volatile
        private var instance: WorkflowShellNodeExecutor? = null

        fun getInstance(context: Context): WorkflowShellNodeExecutor {
            return instance ?: synchronized(this) {
                instance ?: WorkflowShellNodeExecutor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Executes a shell node in a workflow.
     *
     * @param node The shell node to execute
     * @param nodeResults Map of previous node execution results
     * @param triggerExtras Map of trigger extras (e.g., image data)
     * @param workflowId The workflow ID for logging
     * @return NodeExecutionState indicating success, failure, or skip
     */
    suspend fun execute(
        node: ExecuteShellNode,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>,
        workflowId: String
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Executing shell node: ${node.id}, workflow: $workflowId")

        try {
            // Get Terminal instance
            val terminal = Terminal.getInstance(context)
            
            // Initialize terminal if not connected
            if (!terminal.isConnected()) {
                AppLogger.d(TAG, "Terminal not connected, initializing...")
                val initialized = terminal.initialize()
                if (!initialized) {
                    AppLogger.e(TAG, "Failed to initialize terminal")
                    return@withContext NodeExecutionState.Failed("Failed to initialize terminal")
                }
            }

            // Determine session: use existing or create new
            val autoCreated = node.sessionId.isBlank()
            val sessionName = if (autoCreated) {
                "workflow-${node.id.take(8)}"
            } else {
                node.sessionId
            }
            
            val sessionId: String
            if (autoCreated) {
                sessionId = terminal.createSession(sessionName)
                AppLogger.d(TAG, "Created new session: $sessionId for node ${node.id}")
            } else {
                sessionId = node.sessionId
                AppLogger.d(TAG, "Using existing session: $sessionId for node ${node.id}")
            }

            try {
                // Resolve command using parameter resolution
                val resolvedCommand = resolveParameterValue(
                    ParameterValue.StaticValue(node.command),
                    nodeResults,
                    triggerExtras
                )
                
                if (resolvedCommand.isBlank()) {
                    AppLogger.w(TAG, "Shell node ${node.id} has empty command")
                    return@withContext NodeExecutionState.Skipped("Empty command")
                }

                // Build the final command
                var finalCommand = resolvedCommand
                
                // Append stderr capture if enabled
                if (node.captureStderr) {
                    finalCommand = "$finalCommand 2>&1"
                }
                
                // Prepend working directory change if specified
                if (node.workingDir.isNotBlank()) {
                    val resolvedWorkingDir = resolveParameterValue(
                        ParameterValue.StaticValue(node.workingDir),
                        nodeResults,
                        triggerExtras
                    )
                    if (resolvedWorkingDir.isNotBlank()) {
                        finalCommand = "cd ${resolvedWorkingDir.replace(" ", "\\ ")} && $finalCommand"
                    }
                }

                AppLogger.d(TAG, "Executing command in session $sessionId: ${finalCommand.take(100)}...")

                // Execute with timeout
                val output = withTimeout(node.timeoutMs.coerceAtLeast(1000L)) {
                    terminal.executeCommand(sessionId, finalCommand) ?: ""
                }

                AppLogger.d(TAG, "Shell command completed, output length: ${output.length}")
                NodeExecutionState.Success(output)

            } catch (e: TimeoutCancellationException) {
                AppLogger.e(TAG, "Shell command timed out after ${node.timeoutMs}ms: ${e.message}")
                NodeExecutionState.Failed("Shell command timed out after ${node.timeoutMs}ms")
            } finally {
                // Close auto-created session
                if (autoCreated) {
                    try {
                        terminal.closeSession(sessionId)
                        AppLogger.d(TAG, "Closed auto-created session: $sessionId")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to close session $sessionId: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing shell node ${node.id}: ${e.message}", e)
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
     * Clears the singleton instance (useful for testing).
     */
    fun clearInstance() {
        synchronized(this) {
            instance = null
        }
    }
}