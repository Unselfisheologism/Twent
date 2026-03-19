package com.ai.assistance.operit.data.integration

import android.content.Context
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.data.integration.model.ToolDefinition
import com.ai.assistance.operit.data.integration.model.ToolInputSchema
import com.ai.assistance.operit.data.integration.model.ToolkitDefinition
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Composio API Service
 * Provides REST API client functionality for Composio integration platform
 * 
 * API Documentation: https://docs.composio.dev/
 * 
 * Features:
 * - Tool listing (GET /v1/toolkits)
 * - Tool execution (POST /v1/tools/{tool_name}/execute)
 * - OAuth connection flow (get auth URL, exchange code for token)
 * - API key authentication via x-api-key header
 */
class ComposioApiService(private val context: Context) {

    companion object {
        private const val TAG = "ComposioApiService"
        
        // API Base URL - configurable for testing
        // In production, use: https://api.composio.dev
        const val DEFAULT_BASE_URL = "https://api.composio.dev"
        
        // Endpoints
        const val ENDPOINT_TOOLKITS = "/v1/toolkits"
        const val ENDPOINT_TOOL_EXECUTE = "/v1/tools/%s/execute"
        const val ENDPOINT_CONNECTIONS = "/v1/connections"
        const val ENDPOINT_OAUTH_URL = "/v1/connections/oauth-url"
        const val ENDPOINT_OAUTH_EXCHANGE = "/v1/connections/oauth/exchange"
        
        // Default headers
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_USER_AGENT = "User-Agent"
        const val USER_AGENT = "Operit-Android/1.0"
        
        @Volatile
        private var INSTANCE: ComposioApiService? = null
        
        fun getInstance(context: Context): ComposioApiService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComposioApiService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // OkHttp client with timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)  // Longer for tool execution
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
                .addHeader(HEADER_USER_AGENT, USER_AGENT)
                .addHeader(HEADER_CONTENT_TYPE, "application/json")
            chain.proceed(builder.build())
        }
        .build()
    
    // JSON serialization
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
    
    // API Key - loaded from BuildConfig (which reads from local.properties)
    private val apiKey: String
        get() = BuildConfig.COMPOSIO_API_KEY
    
    // Base URL - can be overridden for testing
    private var baseUrl: String = DEFAULT_BASE_URL
    
    /**
     * Set custom base URL (for testing or different environments)
     */
    fun setBaseUrl(url: String) {
        baseUrl = url
        AppLogger.d(TAG, "Base URL set to: $url")
    }
    
    /**
     * Get current base URL
     */
    fun getBaseUrl(): String = baseUrl
    
    /**
     * Check if API key is configured
     */
    fun isConfigured(): Boolean = apiKey.isNotBlank()
    
    /**
     * Build the full URL for an endpoint
     */
    private fun buildUrl(endpoint: String): String {
        return "$baseUrl$endpoint"
    }
    
    /**
     * Create request with API key authentication
     */
    private fun createAuthenticatedRequest(
        url: String,
        method: String = "GET",
        body: RequestBody? = null
    ): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader(HEADER_API_KEY, apiKey)
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(body ?: "".toRequestBody())
            "PUT" -> requestBuilder.put(body ?: "".toRequestBody())
            "PATCH" -> requestBuilder.patch(body ?: "".toRequestBody())
            "DELETE" -> requestBuilder.delete()
        }
        
        return requestBuilder.build()
    }
    
    // ==================== T-008: Tool Listing ====================
    
    /**
     * List all available toolkits from Composio
     * GET /v1/toolkits
     * 
     * @param category Optional category filter
     * @param search Optional search query
     * @param limit Maximum number of results
     * @param offset Offset for pagination
     * @return Result containing list of ToolkitDefinition
     */
    suspend fun listToolkits(
        category: String? = null,
        search: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<ToolkitDefinition>> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val urlBuilder = buildUrl(ENDPOINT_TOOLKITS).toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("limit", limit.toString())
                ?.addQueryParameter("offset", offset.toString())
            
            category?.let { urlBuilder?.addQueryParameter("category", it) }
            search?.let { urlBuilder?.addQueryParameter("search", it) }
            
            val url = urlBuilder?.build()?.toString()
                ?: return@withContext Result.failure(Exception("Failed to build URL"))
            
            val request = createAuthenticatedRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "Toolkits response: $responseBody")
                val toolkits = parseToolkitsResponse(responseBody)
                Result.success(toolkits)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list toolkits", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get tools for a specific toolkit
     * GET /v1/toolkits/{toolkit_name}
     * 
     * @param toolkitName Name of the toolkit
     * @return Result containing ToolkitDefinition with tools
     */
    suspend fun getToolkit(toolkitName: String): Result<ToolkitDefinition> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl("$ENDPOINT_TOOLKITS/$toolkitName")
            val request = createAuthenticatedRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "Get toolkit response: $responseBody")
                val toolkit = parseToolkitResponse(responseBody)
                Result.success(toolkit)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get toolkit: $toolkitName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all tools across all toolkits
     * 
     * @param limit Maximum number of tools per toolkit
     * @return Result containing list of all ToolDefinition
     */
    suspend fun listAllTools(limit: Int = 100): Result<List<ToolDefinition>> = withContext(Dispatchers.IO) {
        try {
            val toolkitsResult = listToolkits(limit = 50)
            toolkitsResult.fold(
                onSuccess = { toolkits ->
                    val allTools = toolkits.flatMap { it.tools }
                    Result.success(allTools)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list all tools", e)
            Result.failure(e)
        }
    }
    
    // ==================== T-009: Tool Execution ====================
    
    /**
     * Execute a tool with given parameters
     * POST /v1/tools/{tool_name}/execute
     * 
     * @param toolName Name of the tool to execute
     * @param parameters Tool parameters as Map
     * @param accountId Optional connected account ID for authenticated tools
     * @return Result containing execution result
     */
    suspend fun executeTool(
        toolName: String,
        parameters: Map<String, Any>,
        accountId: String? = null
    ): Result<ToolExecutionResponse> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl(String.format(ENDPOINT_TOOL_EXECUTE, toolName))
            
            // Build request body
            val requestBody = buildToolExecutionBody(parameters, accountId)
            val jsonBody = json.encodeToString(requestBody)
            
            AppLogger.d(TAG, "Executing tool: $toolName with params: $jsonBody")
            
            val request = createAuthenticatedRequest(
                url = url,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "Tool execution response: $responseBody")
                val result = parseToolExecutionResponse(responseBody)
                Result.success(result)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to execute tool: $toolName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Execute a tool asynchronously
     * Returns immediately with an execution ID for polling
     * 
     * @param toolName Name of the tool to execute
     * @param parameters Tool parameters
     * @param accountId Optional connected account ID
     * @return Result containing execution ID
     */
    suspend fun executeToolAsync(
        toolName: String,
        parameters: Map<String, Any>,
        accountId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl(String.format(ENDPOINT_TOOL_EXECUTE, toolName) + "?async=true")
            
            val requestBody = buildToolExecutionBody(parameters, accountId)
            val jsonBody = json.encodeToString(requestBody)
            
            val request = createAuthenticatedRequest(
                url = url,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val executionId = parseExecutionId(responseBody)
                Result.success(executionId)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to execute tool async: $toolName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get execution status by ID
     * 
     * @param executionId The execution ID to check
     * @return Result containing execution status and result
     */
    suspend fun getExecutionStatus(executionId: String): Result<ToolExecutionResponse> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl("/v1/executions/$executionId")
            val request = createAuthenticatedRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val result = parseToolExecutionResponse(responseBody)
                Result.success(result)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get execution status: $executionId", e)
            Result.failure(e)
        }
    }
    
    // ==================== T-010: OAuth Connection Flow ====================
    
    /**
     * Get OAuth authorization URL for a specific integration
     * 
     * @param toolkit The toolkit name (e.g., "github", "slack")
     * @param redirectUri The OAuth callback URI
     * @return Result containing OAuth URL and connection ID
     */
    suspend fun getOAuthUrl(
        toolkit: String,
        redirectUri: String
    ): Result<OAuthUrlResponse> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl(ENDPOINT_OAUTH_URL)
            
            val requestBody = mapOf(
                "toolkit" to toolkit,
                "redirect_uri" to redirectUri
            )
            val jsonBody = json.encodeToString(requestBody)
            
            val request = createAuthenticatedRequest(
                url = url,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "OAuth URL response: $responseBody")
                val result = parseOAuthUrlResponse(responseBody)
                Result.success(result)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get OAuth URL for: $toolkit", e)
            Result.failure(e)
        }
    }
    
    /**
     * Exchange OAuth authorization code for access token
     * 
     * @param code The authorization code from OAuth callback
     * @param connectionId The connection ID from getOAuthUrl
     * @return Result containing connection details
     */
    suspend fun exchangeOAuthCode(
        code: String,
        connectionId: String
    ): Result<OAuthExchangeResponse> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl(ENDPOINT_OAUTH_EXCHANGE)
            
            val requestBody = mapOf(
                "code" to code,
                "connection_id" to connectionId
            )
            val jsonBody = json.encodeToString(requestBody)
            
            val request = createAuthenticatedRequest(
                url = url,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "OAuth exchange response: $responseBody")
                val result = parseOAuthExchangeResponse(responseBody)
                Result.success(result)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to exchange OAuth code", e)
            Result.failure(e)
        }
    }
    
    /**
     * List connected accounts/integrations
     * 
     * @param toolkit Optional toolkit filter
     * @return Result containing list of connections
     */
    suspend fun listConnections(
        toolkit: String? = null
    ): Result<List<ConnectionInfo>> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val urlBuilder = HttpUrl.parse(buildUrl(ENDPOINT_CONNECTIONS))?.newBuilder()
            toolkit?.let { urlBuilder?.addQueryParameter("toolkit", it) }
            
            val url = urlBuilder?.build()?.toString()
                ?: return@withContext Result.failure(Exception("Failed to build URL"))
            
            val request = createAuthenticatedRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "Connections response: $responseBody")
                val connections = parseConnectionsResponse(responseBody)
                Result.success(connections)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list connections", e)
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect a connected account
     * 
     * @param connectionId The connection ID to disconnect
     * @return Result indicating success
     */
    suspend fun disconnectConnection(connectionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl("$ENDPOINT_CONNECTIONS/$connectionId")
            val request = createAuthenticatedRequest(url, method = "DELETE")
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to disconnect connection: $connectionId", e)
            Result.failure(e)
        }
    }
    
    // ==================== Response Parsing ====================
    
    private fun parseToolkitsResponse(responseBody: String): List<ToolkitDefinition> {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val toolkitsArray = jsonElement.jsonObject["toolkits"] ?: jsonElement
            
            kotlinx.serialization.json.JsonArray = toolkitsArray.let {
                when (it) {
                    is kotlinx.serialization.json.JsonArray -> it
                    else -> kotlinx.serialization.json.jsonArrayOf(it)
                }
            }
            
            // Parse each toolkit
            val toolkits = mutableListOf<ToolkitDefinition>()
            // Simplified parsing - in production, use proper serializable classes
            toolkits
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse toolkits response", e)
            emptyList()
        }
    }
    
    private fun parseToolkitResponse(responseBody: String): ToolkitDefinition {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            
            ToolkitDefinition(
                id = obj["id"]?.toString() ?: "",
                name = obj["name"]?.toString()?.replace("\"", "") ?: "",
                displayName = obj["display_name"]?.toString()?.replace("\"", "") ?: obj["name"]?.toString()?.replace("\"", "") ?: "",
                description = obj["description"]?.toString()?.replace("\"", "") ?: "",
                version = obj["version"]?.toString()?.replace("\"", "") ?: "1.0",
                tools = emptyList()  // Tools are fetched separately
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse toolkit response", e)
            throw e
        }
    }
    
    private fun buildToolExecutionBody(
        parameters: Map<String, Any>,
        accountId: String?
    ): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>(
            "parameters" to parameters
        )
        accountId?.let { body["account_id"] = it }
        return body
    }
    
    private fun parseToolExecutionResponse(responseBody: String): ToolExecutionResponse {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            
            ToolExecutionResponse(
                success = obj["success"]?.toString()?.toBoolean() ?: true,
                result = obj["result"]?.toString()?.replace("\"", "") ?: "",
                data = obj["data"]?.toString(),
                error = obj["error"]?.toString()?.replace("\"", ""),
                executionId = obj["execution_id"]?.toString()?.replace("\"", ""),
                status = obj["status"]?.toString()?.replace("\"", "") ?: "completed"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse execution response", e)
            ToolExecutionResponse(
                success = false,
                result = "",
                error = e.message
            )
        }
    }
    
    private fun parseExecutionId(responseBody: String): String {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            obj["execution_id"]?.toString()?.replace("\"", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun parseOAuthUrlResponse(responseBody: String): OAuthUrlResponse {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            
            OAuthUrlResponse(
                url = obj["url"]?.toString()?.replace("\"", "") ?: "",
                connectionId = obj["connection_id"]?.toString()?.replace("\"", "") ?: ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse OAuth URL response", e)
            OAuthUrlResponse(
                url = "",
                connectionId = ""
            )
        }
    }
    
    private fun parseOAuthExchangeResponse(responseBody: String): OAuthExchangeResponse {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            
            OAuthExchangeResponse(
                success = obj["success"]?.toString()?.toBoolean() ?: true,
                connectionId = obj["connection_id"]?.toString()?.replace("\"", "") ?: "",
                accountId = obj["account_id"]?.toString()?.replace("\"", "") ?: "",
                toolkit = obj["toolkit"]?.toString()?.replace("\"", "") ?: "",
                message = obj["message"]?.toString()?.replace("\"", "")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse OAuth exchange response", e)
            OAuthExchangeResponse(
                success = false,
                connectionId = "",
                accountId = "",
                toolkit = "",
                message = e.message
            )
        }
    }
    
    private fun parseConnectionsResponse(responseBody: String): List<ConnectionInfo> {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val connectionsArray = jsonElement.jsonObject["connections"]
            
            if (connectionsArray is kotlinx.serialization.json.JsonArray) {
                connectionsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        ConnectionInfo(
                            id = obj["id"]?.toString()?.replace("\"", "") ?: "",
                            toolkit = obj["toolkit"]?.toString()?.replace("\"", "") ?: "",
                            accountName = obj["account_name"]?.toString()?.replace("\"", "") ?: "",
                            status = obj["status"]?.toString()?.replace("\"", "") ?: "active",
                            connectedAt = obj["connected_at"]?.toString()?.toLongOrNull() ?: 0L
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse connections response", e)
            emptyList()
        }
    }
}

// ==================== Response Data Classes ====================

/**
 * Tool execution response from Composio API
 */
@Serializable
data class ToolExecutionResponse(
    val success: Boolean,
    val result: String,
    val data: String? = null,
    val error: String? = null,
    val executionId: String? = null,
    val status: String = "completed"
)

/**
 * OAuth URL response
 */
@Serializable
data class OAuthUrlResponse(
    val url: String,
    val connectionId: String
)

/**
 * OAuth exchange response
 */
@Serializable
data class OAuthExchangeResponse(
    val success: Boolean,
    val connectionId: String,
    val accountId: String,
    val toolkit: String,
    val message: String? = null
)

/**
 * Connected account information
 */
@Serializable
data class ConnectionInfo(
    val id: String,
    val toolkit: String,
    val accountName: String,
    val status: String,
    val connectedAt: Long
)
