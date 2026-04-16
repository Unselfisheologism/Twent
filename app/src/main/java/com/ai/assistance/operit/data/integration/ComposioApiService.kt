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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
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
        
        // API Base URL - Composio v3.1 API
        const val DEFAULT_BASE_URL = "https://backend.composio.dev/api/v3.1"
        
        // Endpoints (v3.1)
        const val ENDPOINT_TOOLKITS = "/toolkits"
        const val ENDPOINT_TOOLS = "/tools"
        const val ENDPOINT_TOOL_EXECUTE = "/tools/execute/%s"
        const val ENDPOINT_CONNECTED_ACCOUNTS = "/connected_accounts"
        const val ENDPOINT_CONNECTED_ACCOUNTS_LINK = "/connected_accounts/link"
        const val ENDPOINT_OAUTH_URL = "/connected_accounts/link"  // Same as auth link
        const val ENDPOINT_OAUTH_EXCHANGE = "/connected_accounts/link"  // Placeholder
        
        // Default headers
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_USER_AGENT = "User-Agent"
        const val USER_AGENT = "Twent-Android/1.0"
        
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
            val jsonBody = requestBody.toString()
            
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
            val jsonBody = requestBody.toString()

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
     * Get or create a Composio-managed auth config for a toolkit
     * 
     * First checks for existing auth configs, then creates one if needed.
     * 
     * @param toolkitSlug The toolkit slug (e.g., "github", "gmail")
     * @return Result containing the auth config ID
     */
    suspend fun getOrCreateAuthConfig(toolkitSlug: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            // First, try to list existing auth configs for this toolkit
            try {
                val listUrl = buildUrl("/auth_configs?toolkit=$toolkitSlug&limit=1")
                val listRequest = createAuthenticatedRequest(listUrl)
                val listResponse = client.newCall(listRequest).execute()
                val listBody = listResponse.body?.string()
                
                if (listResponse.isSuccessful && listBody != null) {
                    val jsonElement = json.parseToJsonElement(listBody)
                    val items = jsonElement.jsonObject["items"]
                    if (items is kotlinx.serialization.json.JsonArray && items.isNotEmpty()) {
                        val firstConfig = items[0].jsonObject
                        val configId = firstConfig["id"]?.toString()?.replace("\"", "")
                        if (!configId.isNullOrBlank()) {
                            AppLogger.d(TAG, "Found existing auth config for $toolkitSlug: $configId")
                            return@withContext Result.success(configId)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to list auth configs, will try to create: ${e.message}")
            }
            
            // No existing config found, create one with managed auth
            // The Python SDK's auth_configs.create(toolkit="github", options={type: "..."})
            // sends the toolkit as a query parameter in the URL, with auth options in the body.
            // The REST API does NOT expect 'toolkit' in the request body at all.
            AppLogger.d(TAG, "Creating new managed auth config for $toolkitSlug")
            val createUrl = buildUrl("/auth_configs?toolkit=$toolkitSlug")
            val bodyMap = mapOf(
                "name" to JsonPrimitive("$toolkitSlug Auth"),
                "type" to JsonPrimitive("use_composio_managed_auth")
            )
            val jsonBody = JsonObject(bodyMap).toString()
            
            val createRequest = createAuthenticatedRequest(
                url = createUrl,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            val createResponse = client.newCall(createRequest).execute()
            val createBody = createResponse.body?.string()
            
            if (createResponse.isSuccessful && createBody != null) {
                val jsonElement = json.parseToJsonElement(createBody)
                val configId = jsonElement.jsonObject["id"]?.toString()?.replace("\"", "")
                if (!configId.isNullOrBlank()) {
                    AppLogger.d(TAG, "Created auth config for $toolkitSlug: $configId")
                    Result.success(configId)
                } else {
                    Result.failure(Exception("Failed to parse auth config ID from response"))
                }
            } else {
                val errorMsg = "HTTP ${createResponse.code}: ${createResponse.message}. Response: $createBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get/create auth config for $toolkitSlug", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create an auth link for a user to connect their account
     * Uses Composio's "Connect Links" - hosted pages where users authorize access
     * 
     * POST /connected_accounts/link
     * 
     * @param authConfigId The auth config ID for the toolkit
     * @param userId The user ID to create the connection for
     * @param callbackUrl Optional URL to redirect after auth
     * @return Result containing the redirect URL (Connect Link)
     */
    suspend fun createAuthLink(
        authConfigId: String,
        userId: String,
        callbackUrl: String? = null
    ): Result<AuthLinkResponse> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("COMPOSIO_API_KEY not configured"))
            }
            
            val url = buildUrl(ENDPOINT_CONNECTED_ACCOUNTS_LINK)
            
            val bodyMap = mutableMapOf<String, JsonElement>(
                "auth_config_id" to JsonPrimitive(authConfigId),
                "user_id" to JsonPrimitive(userId)
            )
            callbackUrl?.let { bodyMap["callback_url"] = JsonPrimitive(it) }
            
            val jsonBody = JsonObject(bodyMap).toString()
            
            val request = createAuthenticatedRequest(
                url = url,
                method = "POST",
                body = jsonBody.toRequestBody("application/json".toMediaType())
            )
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                AppLogger.d(TAG, "Auth link response: $responseBody")
                val result = parseAuthLinkResponse(responseBody)
                Result.success(result)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                AppLogger.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create auth link", e)
            Result.failure(e)
        }
    }
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
            val jsonBody = JsonObject(requestBody.mapValues { JsonPrimitive(it.value) }).toString()
            
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
            val jsonBody = JsonObject(requestBody.mapValues { JsonPrimitive(it.value) }).toString()
            
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
            
            val urlBuilder = buildUrl(ENDPOINT_CONNECTED_ACCOUNTS).toHttpUrlOrNull()?.newBuilder()
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
            
            val url = buildUrl("$ENDPOINT_CONNECTED_ACCOUNTS/$connectionId")
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
            val itemsArray = jsonElement.jsonObject["items"] 
            
            if (itemsArray is kotlinx.serialization.json.JsonArray) {
                itemsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        ToolkitDefinition(
                            id = obj["id"]?.toString()?.replace("\"", "") ?: UUID.randomUUID().toString(),
                            name = obj["slug"]?.toString()?.replace("\"", "") ?: "",
                            displayName = obj["name"]?.toString()?.replace("\"", "") ?: "",
                            description = obj["description"]?.toString()?.replace("\"", "") ?: "",
                            icon = obj["logo"]?.toString()?.replace("\"", "") ?: "",
                            version = "1.0",
                            tools = emptyList()
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to parse toolkit item", e)
                        null
                    }
                }
            } else {
                emptyList()
            }
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
    ): JsonObject {
        val body = mutableMapOf<String, JsonElement>(
            "parameters" to buildJsonObject(parameters)
        )
        accountId?.let { body["account_id"] = JsonPrimitive(it) }
        return JsonObject(body)
    }
    
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
    
    /**
     * Parse auth link response from Composio Connect Links API
     */
    private fun parseAuthLinkResponse(responseBody: String): AuthLinkResponse {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val obj = jsonElement.jsonObject
            
            AuthLinkResponse(
                redirectUrl = obj["redirect_url"]?.toString()?.replace("\"", "") ?: "",
                connectionId = obj["id"]?.toString()?.replace("\"", "") ?: "",
                linkToken = obj["link_token"]?.toString()?.replace("\"", "")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse auth link response", e)
            AuthLinkResponse(
                redirectUrl = "",
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
            val itemsArray = jsonElement.jsonObject["items"]
            
            if (itemsArray is kotlinx.serialization.json.JsonArray) {
                itemsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        ConnectionInfo(
                            id = obj["id"]?.toString()?.replace("\"", "") ?: "",
                            toolkit = obj["toolkit"]?.jsonObject?.get("slug")?.toString()?.replace("\"", "") ?: "",
                            accountName = obj["name"]?.toString()?.replace("\"", "") ?: "",
                            status = obj["status"]?.toString()?.replace("\"", "") ?: "ACTIVE",
                            connectedAt = 0L
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to parse connection item", e)
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
    val authUrl: String = url,
    val connectionId: String
)

/**
 * Auth link response for Composio Connect Links
 * Returns the URL where users authorize access to their accounts
 */
@Serializable
data class AuthLinkResponse(
    val redirectUrl: String,
    val connectionId: String,
    val linkToken: String? = null
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
    val message: String? = null,
    val expiresIn: Long = 3600L,
    val accessToken: String = "",
    val refreshToken: String? = null,
    val accountName: String? = null,
    val entityId: String? = null
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
