package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.IntegrationRepository
import com.ai.assistance.operit.data.integration.preferences.IntegrationPreferences
import com.ai.assistance.operit.data.model.IntegrationNode
import com.ai.assistance.operit.data.model.IntegrationNodeConstants
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.domain.usecase.ManageConnections
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Integration Node Executor
 * 
 * Handles execution of integration nodes in workflows. Supports:
 * - TOOL: Composio tool execution
 * - WEBHOOK: Custom webhook HTTP calls
 * - MCP: MCP server tool execution
 * - OAUTH: OAuth connection management
 * 
 * This executor integrates with the workflow system and handles:
 * - Parameter resolution with variable substitution
 * - Retry logic based on retryConfig
 * - Timeout handling
 * - Error handling configuration
 * 
 * @param context Application context
 */
class IntegrationNodeExecutor(private val context: Context) {

  companion object {
    private const val TAG = "IntegrationNodeExecutor"
    
    @Volatile
    private var INSTANCE: IntegrationNodeExecutor? = null
    
    fun getInstance(context: Context): IntegrationNodeExecutor {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: IntegrationNodeExecutor(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
  
  // Services
  private val composioApiService = ComposioApiService.getInstance(context)
  private val integrationRepository = IntegrationRepository.getInstance(context)
  private val integrationPreferences = IntegrationPreferences.getInstance(context)
  private val manageConnections = ManageConnections.getInstance(context)
  
  // JSON serialization
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    encodeDefaults = true
  }

  // Helper function to convert Map<String, Any> to JsonObject
  private fun buildJsonObject(map: Map<String, Any>): JsonObject {
    val jsonMap = map.mapValues { (_, v) ->
      when (v) {
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
      }
    }
    return JsonObject(jsonMap)
  }

  // HTTP client for webhook execution with configurable timeout
  private fun createHttpClient(timeoutMs: Long): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
      .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
      .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
      .build()
  }
  
  /**
   * Execute an integration node
   * 
   * @param node The integration node to execute
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
      // Check if node is enabled
      if (!node.enabled) {
        AppLogger.d(TAG, "Node ${node.id} is disabled, skipping")
        return@withContext NodeExecutionState.Skipped("Node is disabled")
      }
      
      AppLogger.d(TAG, "Executing integration node: ${node.name} (${node.integrationType})")
      
      // Resolve parameters with node references
      val resolvedParams = resolveParameters(node.parameters, nodeResults, triggerExtras)
      
      // Execute based on integration type
      val result = when (node.integrationType) {
        IntegrationNodeConstants.TYPE_TOOL -> executeTool(node, resolvedParams)
        IntegrationNodeConstants.TYPE_WEBHOOK -> executeWebhook(node, resolvedParams)
        IntegrationNodeConstants.TYPE_MCP -> executeMcp(node, resolvedParams)
        IntegrationNodeConstants.TYPE_OAUTH -> executeOAuth(node, resolvedParams)
        else -> Result.failure(Exception("Unknown integration type: ${node.integrationType}"))
      }
      
      // Apply retry logic if enabled
      val finalResult = applyRetryLogic(node, resolvedParams, result, startTime)
      
      // Handle result based on error handling config
      finalResult.fold(
        onSuccess = { output ->
          AppLogger.d(TAG, "Node ${node.id} executed successfully")
          NodeExecutionState.Success(output)
        },
        onFailure = { error ->
          handleError(node, error, startTime)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error executing node ${node.id}", e)
      handleError(node, e, startTime)
    }
  }
  
  // ==================== Parameter Resolution ====================
  
  /**
   * Resolve parameters with node references
   * Supports static values and node references
   */
  private fun resolveParameters(
    parameters: Map<String, ParameterValue>,
    nodeResults: Map<String, NodeExecutionState>,
    triggerExtras: Map<String, String>
  ): Map<String, Any> {
    val resolved = mutableMapOf<String, Any>()
    
    for ((key, value) in parameters) {
      resolved[key] = when (value) {
        is ParameterValue.StaticValue -> parseValue(value.value)
        is ParameterValue.NodeReference -> resolveNodeReference(value, nodeResults, triggerExtras)
      }
    }
    
    return resolved
  }
  
  /**
   * Resolve a node reference to get its output value
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
   * Parse a value attempt to convert to appropriate type
   */
  private fun parseValue(value: String): Any {
    // Try to parse as JSON
    return try {
      val trimmed = value.trim()
      if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
          (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
        json.parseToJsonElement(trimmed)
      } else {
        // Return as string
        value
      }
    } catch (e: Exception) {
      value
    }
  }
  
  // ==================== Execution Methods ====================
  
  /**
   * Execute a TOOL type node (Composio tool)
   */
  private suspend fun executeTool(
    node: IntegrationNode,
    parameters: Map<String, Any>
  ): Result<String> {
    AppLogger.d(TAG, "Executing tool: ${node.actionId} from toolkit: ${node.toolkit}")
    
    // Check if Composio is configured
    if (!composioApiService.isConfigured()) {
      return Result.failure(Exception("COMPOSIO_API_KEY not configured"))
    }
    
    // Get account if specified
    val accountId = node.accountId
    
    // Execute the tool via Composio API
    return composioApiService.executeTool(
      toolName = node.actionId,
      parameters = parameters,
      accountId = accountId
    ).map { response ->
      response.result
    }
  }
  
  /**
   * Execute a WEBHOOK type node
   */
  private suspend fun executeWebhook(
    node: IntegrationNode,
    parameters: Map<String, Any>
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val webhookConfig = node.webhookConfig
        ?: return@withContext Result.failure(Exception("Webhook configuration missing"))
      
      AppLogger.d(TAG, "Executing webhook: ${webhookConfig.name} to ${webhookConfig.url}")
      
      // Create HTTP client with node-specific timeout
      val httpClient = createHttpClient(node.timeout)
      
      // Build request URL with query parameters for GET requests
      val urlBuilder = StringBuilder(webhookConfig.url)
      val method = webhookConfig.method.uppercase()
      
      if (method == "GET" && parameters.isNotEmpty()) {
        val queryParams = parameters.map { (key, value) ->
          "$key=${java.net.URLEncoder.encode(value.toString(), "UTF-8")}"
        }.joinToString("&")
        urlBuilder.append(if (urlBuilder.contains("?")) "&" else "?").append(queryParams)
      }
      
      val requestBuilder = Request.Builder().url(urlBuilder.toString())
      
      // Add headers
      webhookConfig.headers.forEach { (key, value) ->
        requestBuilder.addHeader(key, value)
      }
      
      // Add API key if configured
      if (webhookConfig.apiKeyRequired) {
        val apiKey = integrationPreferences.getApiKey(webhookConfig.id)
        apiKey?.let {
          requestBuilder.addHeader("Authorization", "Bearer $it")
        }
      }
      
      // Add body for POST/PUT/PATCH
      val request = when (method) {
        "POST", "PUT", "PATCH" -> {
          @Suppress("UNCHECKED_CAST")
          val bodyContent = buildJsonObject(parameters).toString()
          requestBuilder
            .method(method, bodyContent.toRequestBody("application/json".toMediaType()))
            .build()
        }
        "GET", "DELETE" -> {
          requestBuilder.method(method, null).build()
        }
        else -> return@withContext Result.failure(Exception("Unsupported HTTP method: $method"))
      }
      
      val response = httpClient.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""
      
      if (response.isSuccessful) {
        Result.success(responseBody)
      } else {
        Result.failure(Exception("HTTP ${response.code}: ${response.message} - $responseBody"))
      }
    } catch (e: Exception) {
      AppLogger.e(TAG, "Webhook execution failed", e)
      Result.failure(e)
    }
  }
  
  /**
   * Execute an MCP type node
   * Uses the MCP server configuration to execute tools via MCP protocol
   * 
   * Note: MCP execution requires proper MCP server setup. This implementation
   * provides a placeholder that can be enhanced with actual MCP protocol support
   * when MCPLocalServer or MCPRepository integration is available.
   */
  private suspend fun executeMcp(
    node: IntegrationNode,
    parameters: Map<String, Any>
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val mcpConfig = node.mcpServerConfig
        ?: return@withContext Result.failure(Exception("MCP server configuration missing"))
      
      AppLogger.d(TAG, "Executing MCP tool: ${mcpConfig.toolName} on server: ${mcpConfig.serverName}")
      
      // Check if server ID is provided
      val serverId = mcpConfig.serverId
      if (serverId.isNullOrEmpty()) {
        return@withContext Result.failure(Exception("MCP server ID not configured"))
      }
      
      // For now, return a placeholder that indicates MCP execution needs proper setup
      // In a full implementation, this would:
      // 1. Get MCP server config from MCPLocalServer or MCPRepository
      // 2. Connect via stdio or SSE based on server type
      // 3. Execute the tool using MCP protocol
      // 4. Return the result
      
      // Try to get server from MCPLocalServer if available
      try {
        val mcpLocalServer = com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(context)
        val serverInfo = mcpLocalServer.getPluginMetadata(serverId)
        if (serverInfo != null) {
          // Server exists, but actual execution would require MCP protocol implementation
          AppLogger.d(TAG, "Found MCP server: ${serverInfo.name}")
        }
      } catch (e: Exception) {
        AppLogger.w(TAG, "Could not get MCP server info: ${e.message}")
      }
      
      Result.failure(Exception(
        "MCP execution is not fully implemented. " +
        "Server: ${mcpConfig.serverName}, Tool: ${mcpConfig.toolName}. " +
        "This feature requires MCP server configuration and protocol support."
      ))
    } catch (e: Exception) {
      AppLogger.e(TAG, "MCP execution failed", e)
      Result.failure(e)
    }
  }
  
  /**
   * Execute an OAUTH type node
   * Manages OAuth connections
   */
  private suspend fun executeOAuth(
    node: IntegrationNode,
    parameters: Map<String, Any>
  ): Result<String> {
    AppLogger.d(TAG, "Executing OAuth action: ${node.actionId}")
    
    return when (node.actionId) {
      "connect" -> {
        val toolkit = parameters["toolkit"] as? String
          ?: return Result.failure(Exception("Missing toolkit parameter"))
        val redirectUri = parameters["redirect_uri"] as? String ?: ""
        
        manageConnections.initiateOAuthFlow(toolkit, redirectUri).map { result ->
          "auth_url:${result.authUrl},account_id:${result.accountId}"
        }
      }
      "disconnect" -> {
        val accountId = parameters["account_id"] as? String
          ?: return Result.failure(Exception("Missing account_id parameter"))
        
        manageConnections.disconnectConnection(accountId).map { success ->
          if (success) "Disconnected successfully" else "Failed to disconnect"
        }
      }
      "refresh" -> {
        val accountId = parameters["account_id"] as? String
          ?: return Result.failure(Exception("Missing account_id parameter"))
        
        // Refresh the OAuth token - use refreshConnection
        manageConnections.refreshConnection(accountId).map { account ->
          "Token refreshed successfully for ${account.accountName}"
        }
      }
      else -> Result.failure(Exception("Unknown OAuth action: ${node.actionId}"))
    }
  }
  
  // ==================== Retry Logic ====================
  
  /**
   * Apply retry logic based on retry configuration
   */
  private suspend fun applyRetryLogic(
    node: IntegrationNode,
    parameters: Map<String, Any>,
    initialResult: Result<String>,
    startTime: Long
  ): Result<String> {
    val retryConfig = node.retryConfig
    
    if (!retryConfig.enabled) {
      return initialResult
    }
    
    // If initial execution succeeded, no need to retry
    initialResult.getOrNull()?.let { return Result.success(it) }
    
    var lastError: Throwable? = null
    var currentParams = parameters
    
    for (attempt in 1..retryConfig.maxRetries) {
      AppLogger.d(TAG, "Retry attempt $attempt/${retryConfig.maxRetries} for node: ${node.id}")
      
      // Calculate delay with exponential backoff
      val delayMs = if (retryConfig.exponentialBackoff) {
        retryConfig.retryDelay * (1 shl (attempt - 1))
      } else {
        retryConfig.retryDelay
      }
      delay(delayMs)
      
      // Execute again with fresh parameter resolution
      val result = when (node.integrationType) {
        IntegrationNodeConstants.TYPE_TOOL -> executeTool(node, currentParams)
        IntegrationNodeConstants.TYPE_WEBHOOK -> executeWebhook(node, currentParams)
        IntegrationNodeConstants.TYPE_MCP -> executeMcp(node, currentParams)
        IntegrationNodeConstants.TYPE_OAUTH -> executeOAuth(node, currentParams)
        else -> Result.failure(Exception("Unknown integration type: ${node.integrationType}"))
      }
      
      result.fold(
        onSuccess = { output ->
          return Result.success(output)
        },
        onFailure = { error ->
          lastError = error
          
          // Check if error is retryable
          if (retryConfig.retryableErrors.isNotEmpty()) {
            val isRetryable = retryConfig.retryableErrors.any { retryableError ->
              error.message?.contains(retryableError, ignoreCase = true) == true
            }
            if (!isRetryable) {
              return Result.failure(error)
            }
          }
        }
      )
    }
    
    return Result.failure(lastError ?: Exception("Max retries exceeded"))
  }
  
  // ==================== Error Handling ====================
  
  /**
   * Handle errors based on error handling configuration
   */
  private fun handleError(
    node: IntegrationNode,
    error: Throwable,
    startTime: Long
  ): NodeExecutionState {
    val errorHandling = node.errorHandling
    val executionTime = System.currentTimeMillis() - startTime
    
    AppLogger.e(TAG, "Node ${node.id} failed after ${executionTime}ms: ${error.message}")
    
    return when (errorHandling.onError) {
      "stop" -> NodeExecutionState.Failed(error.message ?: "Unknown error")
      "continue" -> NodeExecutionState.Skipped("Error ignored: ${error.message}")
      "retry" -> {
        // Retry is handled by applyRetryLogic, this is for final failure
        NodeExecutionState.Failed(error.message ?: "Max retries exceeded")
      }
      "fallback" -> {
        val fallbackValue = errorHandling.fallbackValue ?: ""
        AppLogger.d(TAG, "Using fallback value for node ${node.id}")
        NodeExecutionState.Success(fallbackValue)
      }
      else -> NodeExecutionState.Failed(error.message ?: "Unknown error")
    }
  }
}
