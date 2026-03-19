package com.ai.assistance.operit.domain.usecase

import android.content.Context
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.IntegrationRepository
import com.ai.assistance.operit.data.integration.model.CustomWebhook
import com.ai.assistance.operit.data.integration.model.ErrorAction
import com.ai.assistance.operit.data.integration.model.IntegrationNodeConfig
import com.ai.assistance.operit.data.integration.model.IntegrationNodeType
import com.ai.assistance.operit.data.integration.model.IntegrationParameter
import com.ai.assistance.operit.data.integration.model.IntegrationResult
import com.ai.assistance.operit.data.integration.model.IntegrationStatus
import com.ai.assistance.operit.data.integration.model.RetryConfig
import com.ai.assistance.operit.data.integration.preferences.IntegrationPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Execute Integration Node Use Case
 * 
 * Orchestrates integration node execution within workflows:
 * - Resolves node parameters (static values, node references, expressions)
 * - Routes to appropriate executor (Composio tool, webhook, MCP)
 * - Handles retry logic and error handling
 * - Returns standardized execution results
 * 
 * This use case handles:
 * - TOOL: Composio tool execution
 * - WEBHOOK: Custom webhook HTTP calls
 * - MCP: MCP server tool execution
 * - OAUTH: OAuth connection management (delegated to ManageConnections)
 * 
 * @param context Application context
 */
class ExecuteIntegrationNode(context: Context) {

  companion object {
    private const val TAG = "ExecuteIntegrationNode"
    
    @Volatile
    private var INSTANCE: ExecuteIntegrationNode? = null
    
    fun getInstance(context: Context): ExecuteIntegrationNode {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: ExecuteIntegrationNode(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
  
  private val composioApiService = ComposioApiService.getInstance(context)
  private val integrationRepository = IntegrationRepository.getInstance(context)
  private val integrationPreferences = IntegrationPreferences.getInstance(context)
  private val manageConnections = ManageConnections.getInstance(context)
  
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
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

  // HTTP client for webhook execution
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
  
  // State flow for execution status
  private val _executionStatus = MutableStateFlow<Map<String, IntegrationStatus>>(emptyMap())
  val executionStatus: StateFlow<Map<String, IntegrationStatus>> = _executionStatus
  
  // Node output cache for node reference resolution
  private val nodeOutputCache = mutableMapOf<String, Map<String, String>>()
  
  // ==================== Main Execution ====================
  
  /**
   * Execute an integration node
   * 
   * @param nodeConfig The integration node configuration
   * @param contextData Optional context data from previous node executions
   * @return Result containing execution result
   */
  suspend fun executeNode(
    nodeConfig: IntegrationNodeConfig,
    contextData: Map<String, Map<String, String>> = emptyMap()
  ): Result<IntegrationResult> = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    
    try {
      // Update status to running
      updateNodeStatus(nodeConfig.id, IntegrationStatus.RUNNING)
      
      AppLogger.d(TAG, "Executing node: ${nodeConfig.id} (${nodeConfig.type})")
      
      // Check if node is enabled
      if (!nodeConfig.enabled) {
        return@withContext Result.success(
          IntegrationResult(
            nodeId = nodeConfig.id,
            success = false,
            errorMessage = "Node is disabled",
            executionTime = System.currentTimeMillis() - startTime
          )
        )
      }
      
      // Resolve parameters
      val resolvedParams = resolveParameters(nodeConfig.parameters, contextData)
      
      // Execute based on node type
      val result = when (nodeConfig.type) {
        IntegrationNodeType.TOOL -> executeToolNode(nodeConfig, resolvedParams)
        IntegrationNodeType.WEBHOOK -> executeWebhookNode(nodeConfig, resolvedParams)
        IntegrationNodeType.MCP -> executeMcpNode(nodeConfig, resolvedParams)
        IntegrationNodeType.OAUTH -> executeOAuthNode(nodeConfig, resolvedParams)
      }
      
      // Handle retry logic if needed
      val finalResult = result.fold(
        onSuccess = { execResult ->
          if (!execResult.success && nodeConfig.retryConfig.enabled) {
            handleRetry(nodeConfig, contextData, startTime)
          } else {
            Result.success(execResult)
          }
        },
        onFailure = { error ->
          if (nodeConfig.retryConfig.enabled) {
            handleRetry(nodeConfig, contextData, startTime)
          } else {
            Result.failure(error)
          }
        }
      )
      
      // Update status based on result
      finalResult.fold(
        onSuccess = { result ->
          val status = if (result.success) IntegrationStatus.SUCCESS else IntegrationStatus.FAILED
          updateNodeStatus(nodeConfig.id, status)
          
          // Cache output for node reference resolution
          if (result.success) {
            cacheNodeOutput(nodeConfig.id, result.output, result.metadata)
          }
          
          Result.success(result)
        },
        onFailure = {
          updateNodeStatus(nodeConfig.id, IntegrationStatus.FAILED)
          Result.failure(it)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error executing node: ${nodeConfig.id}", e)
      updateNodeStatus(nodeConfig.id, IntegrationStatus.FAILED)
      
      // Handle error based on error handling config
      handleError(nodeConfig, e, startTime)
    }
  }
  
  /**
   * Execute multiple integration nodes in sequence
   * 
   * @param nodeConfigs List of node configurations in execution order
   * @return List of execution results
   */
  suspend fun executeNodes(
    nodeConfigs: List<IntegrationNodeConfig>
  ): List<Result<IntegrationResult>> = withContext(Dispatchers.IO) {
    val results = mutableListOf<Result<IntegrationResult>>()
    var contextData = emptyMap<String, Map<String, String>>()
    
    for (nodeConfig in nodeConfigs) {
      val result = executeNode(nodeConfig, contextData)
      results.add(result)

      // Build context for next node
      val integrationResult = result.getOrNull()
      if (integrationResult != null) {
        if (integrationResult.success) {
          contextData = contextData + (nodeConfig.id to (
            integrationResult.metadata + mapOf("result" to integrationResult.output)
          ))
        } else if (!nodeConfig.errorHandling.continueOnError) {
          // Stop execution if error handling is set to stop
          break
        }
      }
    }
    
    results
  }
  
  // ==================== Parameter Resolution ====================
  
  /**
   * Resolve all parameters in a node configuration
   * 
   * @param parameters The parameter map to resolve
   * @param contextData Data from previously executed nodes
   * @return Resolved parameter map
   */
  private suspend fun resolveParameters(
    parameters: Map<String, IntegrationParameter>,
    contextData: Map<String, Map<String, String>>
  ): Map<String, Any> {
    val resolved = mutableMapOf<String, Any>()
    
    for ((key, param) in parameters) {
      val value = when (param) {
        is IntegrationParameter.StaticValue -> param.value
        is IntegrationParameter.NodeReference -> resolveNodeReference(param, contextData)
        is IntegrationParameter.Expression -> resolveExpression(param.expression, contextData)
      }
      resolved[key] = value
    }
    
    return resolved
  }
  
  /**
   * Resolve a node reference parameter
   */
  private fun resolveNodeReference(
    param: IntegrationParameter.NodeReference,
    contextData: Map<String, Map<String, String>>
  ): String {
    val nodeOutput = contextData[param.nodeId] 
      ?: nodeOutputCache[param.nodeId]
      ?: return ""
    
    return nodeOutput[param.outputKey] ?: ""
  }
  
  /**
   * Resolve an expression parameter (simple template substitution)
   */
  private fun resolveExpression(
    expression: String,
    contextData: Map<String, Map<String, String>>
  ): String {
    var resolved = expression
    
    // Simple template resolution: ${nodeId.outputKey}
    val templateRegex = """\$\{([^}]+)\}""".toRegex()
    val matches = templateRegex.findAll(expression)
    
    for (match in matches) {
      val template = match.value // e.g., ${node1.result}
      val path = match.groupValues[1] // e.g., node1.result
      val parts = path.split(".")
      
      if (parts.size >= 2) {
        val nodeId = parts[0]
        val outputKey = parts[1]
        
        val nodeOutput = contextData[nodeId] 
          ?: nodeOutputCache[nodeId]
          ?: continue
        
        val value = nodeOutput[outputKey] ?: ""
        resolved = resolved.replace(template, value)
      }
    }
    
    return resolved
  }
  
  // ==================== Node Type Executors ====================
  
  /**
   * Execute a TOOL type node (Composio tool)
   */
  private suspend fun executeToolNode(
    nodeConfig: IntegrationNodeConfig,
    parameters: Map<String, Any>
  ): Result<IntegrationResult> {
    AppLogger.d(TAG, "Executing tool node: ${nodeConfig.actionId}")
    
    // Check if Composio is configured
    if (!composioApiService.isConfigured()) {
      return Result.failure(Exception("COMPOSIO_API_KEY not configured"))
    }
    
    // Get account if specified
    val accountId = nodeConfig.accountId
    
    // Execute the tool
    val result = composioApiService.executeTool(
      toolName = nodeConfig.actionId,
      parameters = parameters,
      accountId = accountId
    )
    
    return result.map { response ->
      IntegrationResult(
        nodeId = nodeConfig.id,
        success = response.success,
        output = response.result,
        errorMessage = response.error,
        executionTime = 0L,
        metadata = mapOf(
          "toolName" to nodeConfig.actionId,
          "toolkit" to nodeConfig.toolkit,
          "accountId" to (accountId ?: "")
        )
      )
    }
  }
  
  /**
   * Execute a WEBHOOK type node
   */
  private suspend fun executeWebhookNode(
    nodeConfig: IntegrationNodeConfig,
    parameters: Map<String, Any>
  ): Result<IntegrationResult> = withContext(Dispatchers.IO) {
    try {
      val webhook = nodeConfig.webhookConfig
        ?: return@withContext Result.failure(Exception("Webhook configuration missing"))
      
      AppLogger.d(TAG, "Executing webhook: ${webhook.name}")
      
      // Build request
      val url = webhook.url
      val method = webhook.method.name.uppercase()
      
      val requestBuilder = Request.Builder().url(url)
      
      // Add headers
      webhook.headers.forEach { (key, value) ->
        requestBuilder.addHeader(key, value)
      }
      
      // Add API key if configured
      if (webhook.apiKeyRequired) {
        val apiKey = integrationPreferences.getApiKey(webhook.id)
        apiKey?.let {
          requestBuilder.addHeader("Authorization", "Bearer $it")
        }
      }
      
      // Add body for POST/PUT/PATCH
      val body = when (method) {
        "POST", "PUT", "PATCH" -> {
          val jsonBody = buildJsonObject(parameters).toString()
          requestBuilder.post(jsonBody.toRequestBody("application/json".toMediaType()))
        }
        else -> null
      }
      
      val request = requestBuilder.build()
      val response = httpClient.newCall(request).execute()
      
      val responseBody = response.body?.string() ?: ""
      val success = response.isSuccessful
      
      IntegrationResult(
        nodeId = nodeConfig.id,
        success = success,
        output = responseBody,
        errorMessage = if (!success) "HTTP ${response.code}: ${response.message}" else null,
        executionTime = 0L,
        metadata = mapOf(
          "url" to url,
          "method" to method,
          "statusCode" to response.code.toString()
        )
      ).let { Result.success(it) }
    } catch (e: Exception) {
      AppLogger.e(TAG, "Webhook execution failed", e)
      Result.failure(e)
    }
  }
  
  /**
   * Execute an MCP type node
   * Note: This is a placeholder - actual MCP execution would need MCP server integration
   */
  private suspend fun executeMcpNode(
    nodeConfig: IntegrationNodeConfig,
    parameters: Map<String, Any>
  ): Result<IntegrationResult> {
    AppLogger.d(TAG, "Executing MCP node: ${nodeConfig.actionId}")
    
    val mcpConfig = nodeConfig.mcpServerConfig
      ?: return Result.failure(Exception("MCP server configuration missing"))
    
    // Placeholder for MCP execution
    // In a full implementation, this would:
    // 1. Get the MCP server from MCPRepository
    // 2. Connect to the server via stdio or SSE
    // 3. Execute the tool
    // 4. Return the result
    
    return Result.failure(Exception("MCP execution not yet implemented"))
  }
  
  /**
   * Execute an OAUTH type node (delegates to ManageConnections)
   */
  private suspend fun executeOAuthNode(
    nodeConfig: IntegrationNodeConfig,
    parameters: Map<String, Any>
  ): Result<IntegrationResult> {
    AppLogger.d(TAG, "Executing OAuth node: ${nodeConfig.actionId}")
    
    return when (nodeConfig.actionId) {
      "connect" -> {
        val toolkit = parameters["toolkit"] as? String 
          ?: return Result.failure(Exception("Missing toolkit parameter"))
        val redirectUri = parameters["redirect_uri"] as? String ?: ""
        
        manageConnections.initiateOAuthFlow(toolkit, redirectUri).map { result ->
          IntegrationResult(
            nodeId = nodeConfig.id,
            success = true,
            output = """{"accountId":"${result.accountId}","authUrl":"${result.authUrl}","connectionId":"${result.connectionId}"}""",
            executionTime = 0L,
            metadata = mapOf("action" to "connect", "toolkit" to toolkit)
          )
        }
      }
      "disconnect" -> {
        val accountId = parameters["account_id"] as? String
          ?: return Result.failure(Exception("Missing account_id parameter"))
        
        manageConnections.disconnectConnection(accountId).map { result ->
          IntegrationResult(
            nodeId = nodeConfig.id,
            success = result,
            output = "",
            errorMessage = if (!result) "Failed to disconnect" else null,
            executionTime = 0L,
            metadata = mapOf("action" to "disconnect", "accountId" to accountId)
          )
        }
      }
      else -> Result.failure(Exception("Unknown OAuth action: ${nodeConfig.actionId}"))
    }
  }
  
  // ==================== Retry Logic ====================
  
  /**
   * Handle retry logic for failed executions
   */
  private suspend fun handleRetry(
    nodeConfig: IntegrationNodeConfig,
    contextData: Map<String, Map<String, String>>,
    startTime: Long
  ): Result<IntegrationResult> {
    val retryConfig = nodeConfig.retryConfig
    var lastError: Exception? = null
    
    for (attempt in 1..retryConfig.maxRetries) {
      AppLogger.d(TAG, "Retry attempt $attempt/${retryConfig.maxRetries} for node: ${nodeConfig.id}")
      
      // Calculate delay with exponential backoff
      val delayMs = if (retryConfig.exponentialBackoff) {
        retryConfig.retryDelay * (1 shl (attempt - 1))
      } else {
        retryConfig.retryDelay
      }
      delay(delayMs)
      
      // Resolve parameters again
      val resolvedParams = resolveParameters(nodeConfig.parameters, contextData)
      
      // Execute again
      val result = when (nodeConfig.type) {
        IntegrationNodeType.TOOL -> executeToolNode(nodeConfig, resolvedParams)
        IntegrationNodeType.WEBHOOK -> executeWebhookNode(nodeConfig, resolvedParams)
        IntegrationNodeType.MCP -> executeMcpNode(nodeConfig, resolvedParams)
        IntegrationNodeType.OAUTH -> executeOAuthNode(nodeConfig, resolvedParams)
      }
      
      result.fold(
        onSuccess = { execResult ->
          if (execResult.success) {
            return Result.success(execResult)
          }
          lastError = Exception(execResult.errorMessage)
        },
        onFailure = { error ->
          lastError = error as Exception
          
          // Check if error is retryable
          if (retryConfig.retryableErrors.isNotEmpty()) {
            val isRetryable = retryConfig.retryableErrors.any { 
              error.message?.contains(it, ignoreCase = true) == true 
            }
            if (!isRetryable) {
              return Result.failure(error)
            }
          }
        }
      )
    }
    
    // All retries exhausted
    return Result.failure(lastError ?: Exception("Max retries exceeded"))
  }
  
  // ==================== Error Handling ====================
  
  /**
   * Handle errors based on error handling configuration
   */
  private fun handleError(
    nodeConfig: IntegrationNodeConfig,
    error: Exception,
    startTime: Long
  ): Result<IntegrationResult> {
    val errorHandling = nodeConfig.errorHandling
    
    return when (errorHandling.onError) {
      ErrorAction.STOP -> Result.failure(error)
      ErrorAction.CONTINUE -> {
        Result.success(
          IntegrationResult(
            nodeId = nodeConfig.id,
            success = false,
            errorMessage = error.message,
            executionTime = System.currentTimeMillis() - startTime,
            metadata = mapOf("error_handled" to "continue")
          )
        )
      }
      ErrorAction.RETRY -> {
        // Retry is handled separately
        Result.failure(error)
      }
      ErrorAction.FALLBACK -> {
        Result.success(
          IntegrationResult(
            nodeId = nodeConfig.id,
            success = true, // Mark as success with fallback
            output = errorHandling.fallbackValue ?: "",
            errorMessage = error.message,
            executionTime = System.currentTimeMillis() - startTime,
            metadata = mapOf("used_fallback" to "true")
          )
        )
      }
    }
  }
  
  // ==================== State Management ====================
  
  /**
   * Update execution status for a node
   */
  private fun updateNodeStatus(nodeId: String, status: IntegrationStatus) {
    val currentStatus = _executionStatus.value.toMutableMap()
    currentStatus[nodeId] = status
    _executionStatus.value = currentStatus
  }
  
  /**
   * Cache node output for reference resolution
   */
  private fun cacheNodeOutput(nodeId: String, output: String, metadata: Map<String, String>) {
    nodeOutputCache[nodeId] = metadata + mapOf("result" to output)
  }
  
  /**
   * Clear node output cache
   */
  fun clearCache() {
    nodeOutputCache.clear()
  }
  
  /**
   * Clear execution status
   */
  fun clearStatus() {
    _executionStatus.value = emptyMap()
  }
  
  /**
   * Get current execution status for a node
   */
  fun getNodeStatus(nodeId: String): IntegrationStatus? {
    return _executionStatus.value[nodeId]
  }
}
