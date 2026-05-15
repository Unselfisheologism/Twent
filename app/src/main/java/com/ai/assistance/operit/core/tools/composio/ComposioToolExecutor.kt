package com.ai.assistance.operit.core.tools.composio

import android.content.Context
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.ToolExecutionResponse
import com.ai.assistance.operit.data.integration.IntegrationRepository
import com.ai.assistance.operit.data.integration.model.AccountStatus
import com.ai.assistance.operit.data.integration.model.ConnectedAccount
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.voice.utilities.UserIdManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Composio tool executor — bridges the AI Chat agent to Composio's 100+ integration tools.
 *
 * Architecture mirrors MCPToolExecutor:
 * - composio_tool(toolkit="github", tool="create_issue", params={...})
 * - Executor looks up connected accounts → picks the right auth → calls Composio REST API
 *
 * Connected accounts from IntegrationRepository carry the Composio account IDs (ca_xxx)
 * which the ComposioApiService uses to route authenticated API calls.
 */
class ComposioToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "ComposioToolExecutor"
    }

    private val composioApi: ComposioApiService by lazy {
        ComposioApiService.getInstance(context)
    }

    private val integrationRepo: IntegrationRepository by lazy {
        IntegrationRepository.getInstance(context)
    }

    private val apiPreferences: ApiPreferences by lazy {
        ApiPreferences.getInstance(context)
    }

    /** Parse the raw tool parameter string value into appropriate types */
    private fun parseParamValue(value: String, expectedType: String?): Any {
        return when (expectedType?.lowercase()) {
            "integer", "number" -> value.toIntOrNull() ?: value
            "boolean"          -> value.toBooleanStrictOrNull() ?: value
            "array",  "object" -> try {
                val json = JSONObject(value)
                val map = mutableMapOf<String, Any>()
                json.keys().forEach { key -> map[key] = json.get(key) }
                map
            } catch (_: Exception) { value }
            else                  -> value
        }
    }

    /** Truncate result strings to avoid token overflow */
    private suspend fun truncateResult(result: String): String {
        val maxLen = apiPreferences.getMaxTextResultLength()
        return if (result.length <= maxLen) result
        else result.substring(0, maxLen) +
            "\n\n[... Result truncated. ${result.length - maxLen} additional characters omitted.]"
    }

    data class ResolvedAccount(val accountId: String, val entityId: String)
/**
     * Find the best connected account for a given toolkit slug.
     *
     * IMPORTANT: reads directly from Composio API (via listConnections), NOT from local JSON files.
     * Local JSON files are only written by the OAuth callback handler, which doesn't exist in this app.
     * loadIntegrations() also reads from Composio API directly — this must match that behavior.
     *
     * Composio v3 requires BOTH account_id AND user_id (entity_id) when executing tools.
     * The user_id was passed to Composio when the connection was created via createAuthLink.
     * Composio returns it in the connected_accounts list response as "user_id".
     *
     * We fetch BOTH active AND non-active (EXPIRED, REVOKED, ERROR) connections and look for
     * the best match. entity_id is required — must come from Composio's user_id or local UserIdManager.
     */
    private suspend fun findAccountForToolkit(toolkit: String): ResolvedAccount? {
        // Get ALL connections (not just ACTIVE) so we can detect stale ones too
        val connectionsResult = composioApi.listConnectionsAllStatuses()
        val connections = connectionsResult.getOrNull()

        val target = toolkit.lowercase()

        // Try to find an ACTIVE connection first, then fall back to any connection
        var connection = connections?.firstOrNull { conn ->
            val connToolkit = conn.toolkit.lowercase()
            connToolkit == target ||
            connToolkit.contains(target) ||
            connToolkit.endsWith("_$target") ||
            connToolkit == "composio_$target"
        }

        // If no match yet, try again with broader matching (in case toolkit name has underscores)
        if (connection == null && connections != null) {
            AppLogger.d(TAG, "No exact toolkit match for '$target', trying substring match across ${connections.size} connections")
            connection = connections.firstOrNull { conn ->
                val connToolkit = conn.toolkit.lowercase()
                connToolkit.contains(target) || target.contains(connToolkit)
            }
        }

        if (connection == null) {
            AppLogger.w(TAG, "No Composio connection found for toolkit '$target'. Available: ${connections?.map { "\"${it.toolkit}\" (${it.status})" }}")
            return null
        }

        // accountId: use the Composio connection ID (ca_xxx)
        val accountId = connection.id

        // entityId: MUST be provided. Try in order:
        // 1. Composio's user_id field (set at auth time)
        // 2. Local UserIdManager as fallback
        // NOTE: if user_id is null/blank in Composio's response, the connection was created
        // without passing a user_id during the OAuth flow — the fallback will NOT match
        // what Composio stored, and the tool will fail with 1811. In that case the user
        // needs to reconnect from the Integrations page so the new auth includes user_id.
        val localUserId = UserIdManager(context).getOrCreateUserId()
        val entityId = connection.userId?.takeIf { it.isNotBlank() } ?: run {
            AppLogger.w(TAG, "Composio connection '${connection.id}' has no user_id — using local UserIdManager fallback ('$localUserId'). If tool fails with 1811, reconnect Gmail from Integrations page.")
            localUserId
        }

        AppLogger.d(TAG, "Resolved account for '$toolkit': accountId=$accountId, entityId=$entityId, status=${connection.status}")
        return ResolvedAccount(accountId, entityId)
    }

    /** Check if Composio API is configured */
    private fun checkConfigured(): Boolean {
        if (!composioApi.isConfigured()) {
            AppLogger.w(TAG, "Composio API key not configured")
            return false
        }
        return true
    }

    override fun invoke(tool: AITool): ToolResult {
        // --- Step 1: Extract parameters ---
        // Accept both old names (toolkit/tool/params) and new names (tool_name/parameters)
        val toolkitParam = tool.parameters.find { it.name == "toolkit" || it.name == "toolkit_slug" }?.value
        val toolNameParam = tool.parameters.find { it.name == "tool" || it.name == "tool_name" }?.value
        val paramsParam = tool.parameters.find { it.name == "params" || it.name == "parameters" }?.value

        if (toolNameParam.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: 'tool' (the Composio tool name, e.g. 'github_create_issue')"
            )
        }

        // --- Step 2: Parse params JSON into Map<String, Any> ---
        val parameters: Map<String, Any> = if (!paramsParam.isNullOrBlank()) {
            try {
                val json = JSONObject(paramsParam)
                val map = mutableMapOf<String, Any>()
                json.keys().forEach { key -> map[key] = json.get(key) }
                map
            } catch (e: Exception) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to parse 'params' as JSON object: ${e.message}"
                )
            }
        } else emptyMap()

// --- Step 3: Resolve account ID and entity ID ---
        val resolvedAccount = if (!toolkitParam.isNullOrBlank()) {
            runBlocking { findAccountForToolkit(toolkitParam) }
        } else null

        val accountId: String? = resolvedAccount?.accountId
        val entityId: String? = resolvedAccount?.entityId?.takeIf { it.isNotBlank() }

        if (resolvedAccount != null) {
            AppLogger.d(TAG, "Found account for toolkit '$toolkitParam': accountId=${resolvedAccount.accountId}, entityId=${resolvedAccount.entityId}")
            if (accountId != null && entityId != null) {
                AppLogger.d(TAG, "Will execute with account_id=$accountId and entity_id=$entityId")
            } else if (accountId != null) {
                AppLogger.w(TAG, "Connected account found but entityId is empty — tool may fail with 1811. User needs to re-authenticate from Integrations page.")
            }
        } else {
            AppLogger.w(TAG, "No active connected account found for toolkit '$toolkitParam'. Proceeding without account ID (unauthenticated tool call).")
        }

        // --- Step 4: Execute via Composio REST API ---
        if (!checkConfigured()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Composio API key not configured. Please add COMPOSIO_API_KEY to your BuildConfig/local.properties."
            )
        }

        // If there is NO connected account at all, we can't call authenticated tools.
        // (entityId can be blank when no account is found — this is handled by the fallback above.)
        if (accountId == null) {
            AppLogger.w(TAG, "No connected account found for toolkit '$toolkitParam'. Cannot call authenticated tool.")
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Gmail is not connected. Please go to the Integrations page and connect Gmail first."
            )
        }

        return runBlocking {
            val execResult = composioApi.executeTool(
                toolName = toolNameParam,
                parameters = parameters,
                accountId = accountId,
                entityId = entityId ?: ""
            )

            execResult.fold(
                onSuccess = { response ->
                    if (!response.success) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = response.error ?: "Composio tool reported failure"
                        )
                    } else {
                        val resultText = extractComposioResult(response)
                        val truncated = truncateResult(resultText)
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData(truncated),
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.e(TAG, "Composio tool execution failed: ${error.message}", error)
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Composio API error: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Extract readable text from Composio's ToolExecutionResponse.
     * The API returns a `result` String field — try to parse as JSON for pretty-printing.
     */
    private fun extractComposioResult(response: ToolExecutionResponse): String {
        val raw: String = response.result?.toString() ?: ""

        if (raw.isBlank()) {
            return response.data ?: "OK"
        }

        // Try to pretty-print JSON for readability
        return try {
            val jsonObj = org.json.JSONObject(raw)
            val pretty = jsonObj.toString(2)
            val additionalData = response.data ?: ""
            if (additionalData.isNotBlank()) "$pretty\n\nAdditional data: $additionalData"
            else pretty
        } catch (_: Exception) {
            // Not JSON — return as-is
            if (raw.isNotBlank()) raw
            else response.data ?: "OK"
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val toolName = tool.parameters.find { it.name == "tool" }?.value
        if (toolName.isNullOrBlank()) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Missing required parameter: 'tool'"
            )
        }
        return ToolValidationResult(valid = true)
    }
}
