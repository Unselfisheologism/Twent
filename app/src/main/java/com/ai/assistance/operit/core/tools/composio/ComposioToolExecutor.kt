package com.ai.assistance.operit.core.tools.composio

import android.content.Context
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.ComposioApiService.ToolExecutionResponse
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

    /** Find the best connected account for a given toolkit slug */
    private suspend fun findAccountForToolkit(toolkit: String): ConnectedAccount? {
        val accounts = integrationRepo.listConnectedAccountsByStatus(AccountStatus.ACTIVE).getOrNull()
        // Try exact match first, then case-insensitive
        return accounts?.find {
            it.toolkit.equals(toolkit, ignoreCase = true) ||
            it.toolkit.equals("composio_$toolkit", ignoreCase = true)
        } ?: accounts?.firstOrNull { acc ->
            // Fallback: any active account if no toolkit-specific one found
            acc.toolkit.isNotBlank()
        }
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
        val toolkitParam = tool.parameters.find { it.name == "toolkit" }?.value
        val toolNameParam = tool.parameters.find { it.name == "tool" }?.value
        val paramsParam = tool.parameters.find { it.name == "params" }?.value

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

        // --- Step 3: Resolve account ID ---
        val accountId: String? = if (!toolkitParam.isNullOrBlank()) {
            val account = runBlocking { findAccountForToolkit(toolkitParam) }
            if (account != null) {
                AppLogger.d(TAG, "Found account for toolkit '$toolkitParam': ${account.id}")
                account.accountId.takeIf { it.isNotBlank() } ?: account.id
            } else {
                AppLogger.w(TAG, "No active connected account found for toolkit '$toolkitParam'. Proceeding without account ID.")
                null
            }
        } else null

        // --- Step 4: Execute via Composio REST API ---
        if (!checkConfigured()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Composio API key not configured. Please add COMPOSIO_API_KEY to your BuildConfig/local.properties."
            )
        }

        AppLogger.d(TAG, "Executing Composio tool: $toolNameParam, toolkit: $toolkitParam, accountId: $accountId, params: $parameters")

        return runBlocking {
            val execResult = composioApi.executeTool(
                toolName = toolNameParam,
                parameters = parameters,
                accountId = accountId
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
    private fun extractComposioResult(response: ComposioApiService.ToolExecutionResponse): String {
        val raw = response.result

        if (raw.isBlank()) {
            return response.data ?: "OK"
        }

        // Try to pretty-print JSON for readability
        return try {
            val jsonObj = org.json.JSONObject(raw)
            val pretty = jsonObj.toString(2)
            response.data?.let { data ->
                if (data.isNotBlank()) "$pretty\n\nAdditional data: $data"
            } ?: pretty
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
