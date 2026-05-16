package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.data.mcp.plugins.MCPBridge
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.data.model.IntegrationNode
import com.ai.assistance.operit.data.model.IntegrationNodeConstants
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Workflow MCP Node Executor
 * 
 * Handles MCP (Model Context Protocol) execution in workflows by delegating
 * to MCPBridge for actual MCP protocol communication.
 * 
 * Supported actions:
 * - list_tools: List available tools from an MCP server
 * - call_tool: Execute a specific tool on an MCP server
 * - server_info: Get server information/status
 * 
 * @param context Application context
 */
class WorkflowMcpNodeExecutor private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WorkflowMcpNodeExecutor"
        
        // Action constants for MCP node execution
        private const val ACTION_LIST_TOOLS = "list_tools"
        private const val ACTION_TOOL_CALL = "call_tool"
        private const val ACTION_SERVER_INFO = "server_info"
        
        @Volatile
        private var INSTANCE: WorkflowMcpNodeExecutor? = null
        
        /**
         * Get singleton instance of WorkflowMcpNodeExecutor
         */
        fun getInstance(context: Context): WorkflowMcpNodeExecutor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkflowMcpNodeExecutor(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    // MCP Bridge instance for communication
    private val mcpBridge: MCPBridge by lazy {
        MCPBridge.getInstance(context)
    }

    /**
     * Execute an MCP integration node
     * 
     * @param node The integration node to execute (must be TYPE_MCP)
     * @param nodeResults Results from previously executed nodes for parameter resolution
     * @param triggerExtras Additional trigger context data
     * @return Execution result with output data
     */
    suspend fun execute(
        node: IntegrationNode,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String> = emptyMap()
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Validate node type
            if (node.integrationType != IntegrationNodeConstants.TYPE_MCP) {
                AppLogger.e(TAG, "Invalid node type: ${node.integrationType}, expected ${IntegrationNodeConstants.TYPE_MCP}")
                return@withContext NodeExecutionState.Failed("Invalid node type: ${node.integrationType}")
            }
            
            // Get MCP config
            val mcpConfig = node.mcpServerConfig
            if (mcpConfig == null) {
                AppLogger.e(TAG, "MCP server configuration is missing")
                return@withContext NodeExecutionState.Failed("MCP server configuration is missing")
            }
            
            val serverName = mcpConfig.serverName
            if (serverName.isEmpty()) {
                AppLogger.e(TAG, "MCP server name is empty")
                return@withContext NodeExecutionState.Failed("MCP server name is required")
            }
            
            val actionId = node.actionId
            AppLogger.d(TAG, "Executing MCP action: $actionId for server: $serverName")
            
            // Execute based on action ID
            val result = when (actionId) {
                ACTION_LIST_TOOLS -> executeListTools(serverName)
                ACTION_TOOL_CALL -> {
                    val toolName = mcpConfig.toolName
                    if (toolName.isEmpty()) {
                        return@withContext NodeExecutionState.Failed("Tool name is required for call_tool action")
                    }
                    val parameters = resolveNodeParameters(mcpConfig.parameters, nodeResults, triggerExtras)
                    executeToolCall(serverName, toolName, parameters)
                }
                ACTION_SERVER_INFO -> executeServerInfo(serverName)
                else -> {
                    AppLogger.e(TAG, "Unknown MCP action: $actionId")
                    return@withContext NodeExecutionState.Failed("Unknown MCP action: $actionId")
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            AppLogger.d(TAG, "MCP execution completed in ${executionTime}ms")
            
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing MCP node ${node.id}", e)
            NodeExecutionState.Failed(e.message ?: "Unknown error during MCP execution")
        }
    }
    
    /**
     * Execute list_tools action - List all available tools from an MCP server
     */
    private suspend fun executeListTools(serverName: String): NodeExecutionState {
        AppLogger.d(TAG, "Listing tools for server: $serverName")
        
        try {
            // Build the list tools command
            val command = MCPBridgeClient.buildListToolsCommand(serverName)
            
            // Send command to MCP bridge
            val response = MCPBridge.sendCommand(command)
            
            // Handle null response (connection failure)
            if (response == null) {
                AppLogger.e(TAG, "MCP bridge connection failed for list_tools")
                return NodeExecutionState.Failed("MCP bridge connection failed")
            }
            
            // Check for error response
            if (!response.optBoolean("success", false)) {
                val error = response.optString("error", "Unknown error")
                AppLogger.e(TAG, "List tools failed: $error")
                return NodeExecutionState.Failed(error)
            }
            
            // Extract tools from response
            val result = response.optJSONObject("result")
            val toolsJson = result?.optJSONArray("tools")?.toString() ?: "[]"
            
            AppLogger.d(TAG, "Successfully listed tools, count: ${result?.optJSONArray("tools")?.length() ?: 0}")
            return NodeExecutionState.Success(toolsJson)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing tools from $serverName", e)
            return NodeExecutionState.Failed(e.message ?: "Error listing tools")
        }
    }
    
    /**
     * Execute call_tool action - Call a specific tool on an MCP server
     */
    private suspend fun executeToolCall(
        serverName: String,
        toolName: String,
        parameters: Map<String, Any>
    ): NodeExecutionState {
        AppLogger.d(TAG, "Calling tool: $toolName on server: $serverName with params: $parameters")
        
        try {
            // Build the tool call command
            val paramsJson = JSONObject(parameters)
            val command = MCPBridgeClient.buildToolCallCommand(
                name = serverName,
                method = toolName,
                params = paramsJson
            )
            
            // Send command to MCP bridge
            val response = MCPBridge.sendCommand(command)
            
            // Handle null response (connection failure)
            if (response == null) {
                AppLogger.e(TAG, "MCP bridge connection failed for tool_call")
                return NodeExecutionState.Failed("MCP bridge connection failed")
            }
            
            // Check for error response
            if (!response.optBoolean("success", false)) {
                val error = response.optJSONObject("error")?.optString("message") 
                    ?: response.optString("error", "Unknown error")
                AppLogger.e(TAG, "Tool call failed: $error")
                return NodeExecutionState.Failed(error)
            }
            
            // Extract result from response
            val result = response.opt("result")
            val resultString = when (result) {
                is JSONObject -> result.toString()
                is String -> result
                else -> result?.toString() ?: ""
            }
            
            AppLogger.d(TAG, "Tool call successful, result length: ${resultString.length}")
            return NodeExecutionState.Success(resultString)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error calling tool $toolName on $serverName", e)
            return NodeExecutionState.Failed(e.message ?: "Error calling tool")
        }
    }
    
    /**
     * Execute server_info action - Get server information/status
     */
    private suspend fun executeServerInfo(serverName: String): NodeExecutionState {
        AppLogger.d(TAG, "Getting server info for: $serverName")
        
        try {
            // Build the list services command to get server info
            val command = MCPBridgeClient.buildListServicesCommand(serverName)
            
            // Send command to MCP bridge
            val response = MCPBridge.sendCommand(command)
            
            // Handle null response (connection failure)
            if (response == null) {
                AppLogger.e(TAG, "MCP bridge connection failed for server_info")
                return NodeExecutionState.Failed("MCP bridge connection failed")
            }
            
            // Check for error response
            if (!response.optBoolean("success", false)) {
                val error = response.optString("error", "Unknown error")
                AppLogger.e(TAG, "Server info failed: $error")
                return NodeExecutionState.Failed(error)
            }
            
            // Extract result from response
            val result = response.optJSONObject("result")
            val resultString = result?.toString() ?: "{}"
            
            AppLogger.d(TAG, "Successfully retrieved server info")
            return NodeExecutionState.Success(resultString)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting server info for $serverName", e)
            return NodeExecutionState.Failed(e.message ?: "Error getting server info")
        }
    }
    
    /**
     * Resolve parameter values from node configuration
     * Supports static values and node references
     * 
     * @param parameters Map of parameter definitions
     * @param nodeResults Results from previously executed nodes
     * @param triggerExtras Additional trigger context data
     * @return Resolved parameter map with actual values
     */
    private fun resolveParameterValue(
        value: ParameterValue,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>
    ): Any {
        return when (value) {
            is ParameterValue.StaticValue -> {
                // Parse static value, try to interpret as appropriate type
                parseStaticValue(value.value)
            }
            is ParameterValue.NodeReference -> {
                // Resolve node reference
                resolveNodeReference(value, nodeResults, triggerExtras)
            }
            is ParameterValue.TriggerExtra -> value.defaultValue ?: ""
        }
    }
    
    /**
     * Resolve node reference to get its output value
     */
    private fun resolveNodeReference(
        reference: ParameterValue.NodeReference,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>
    ): String {
        // First check trigger extras
        triggerExtras[reference.nodeId]?.let { return it }
        
        // Then check node results
        val state = nodeResults[reference.nodeId]
        return when (state) {
            is NodeExecutionState.Success -> state.result
            is NodeExecutionState.Skipped -> state.reason
            is NodeExecutionState.Failed -> throw IllegalStateException(
                "Referenced node ${reference.nodeId} failed: ${state.error}"
            )
            else -> throw IllegalStateException(
                "Referenced node ${reference.nodeId} has not completed"
            )
        }
    }
    
    /**
     * Parse static value attempt to convert to appropriate type
     */
    private fun parseStaticValue(value: String): Any {
        val trimmed = value.trim()
        
        // Try to parse as JSON object
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return JSONObject(trimmed)
            } catch (e: Exception) {
                // Not valid JSON, return as string
            }
        }
        
        // Try to parse as JSON array
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return JSONObject(trimmed)
            } catch (e: Exception) {
                // Not valid JSON, return as string
            }
        }
        
        // Try to parse as boolean
        if (trimmed.equals("true", ignoreCase = true)) return true
        if (trimmed.equals("false", ignoreCase = true)) return false
        
        // Try to parse as number
        try {
            // Check if it's an integer
            if (trimmed.matches(Regex("^-?\\d+$"))) {
                return trimmed.toLong()
            }
            // Check if it's a float
            if (trimmed.matches(Regex("^-?\\d+\\.\\d+$"))) {
                return trimmed.toDouble()
            }
        } catch (e: Exception) {
            // Not a number, return as string
        }
        
        // Return as string
        return value
    }
    
    /**
     * Resolve parameters for MCP tool call
     * Converts parameter definitions to actual values
     */
    private fun resolveNodeParameters(
        parameterDefinitions: Map<String, String>,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>
    ): Map<String, Any> {
        val resolved = mutableMapOf<String, Any>()
        
        for ((key, value) in parameterDefinitions) {
            // Check if value is a node reference (starts with $)
            if (value.startsWith("$")) {
                val nodeId = value.substring(1)
                try {
                    val nodeState = nodeResults[nodeId]
                    when (nodeState) {
                        is NodeExecutionState.Success -> {
                            resolved[key] = parseStaticValue(nodeState.result)
                        }
                        is NodeExecutionState.Skipped -> {
                            resolved[key] = nodeState.reason
                        }
                        is NodeExecutionState.Failed -> {
                            AppLogger.w(TAG, "Referenced node $nodeId failed: ${nodeState.error}")
                            resolved[key] = ""
                        }
                        else -> {
                            resolved[key] = ""
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving parameter $key from node $nodeId: ${e.message}")
                    resolved[key] = ""
                }
            } else {
                // Static value
                resolved[key] = parseStaticValue(value)
            }
        }
        
        return resolved
    }
}