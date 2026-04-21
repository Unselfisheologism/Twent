package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.AcpAgentEntry
import com.ai.assistance.operit.data.model.AcpRegistryResponse
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service to fetch the ACP (Agent Client Protocol) registry.
 * Registry URL: https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json
 * GitHub: https://github.com/agentclientprotocol/registry
 */
class AcpRegistryService {

    companion object {
        private const val TAG = "AcpRegistryService"
        private const val REGISTRY_URL = "https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json"
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 15L

        @Volatile
        private var instance: AcpRegistryService? = null

        fun getInstance(): AcpRegistryService {
            return instance ?: synchronized(this) {
                instance ?: AcpRegistryService().also { instance = it }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch the ACP registry and parse agent entries.
     * Returns a list of AcpAgentEntry objects.
     */
    suspend fun fetchRegistry(): Result<List<AcpAgentEntry>> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Fetching ACP registry from $REGISTRY_URL")

            val request = Request.Builder()
                .url(REGISTRY_URL)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Failed to fetch registry: ${response.code}"
                    AppLogger.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Empty registry response"))
                }

                // Parse JSON manually since the structure may vary
                val agents = parseRegistryResponse(body)
                AppLogger.i(TAG, "Successfully fetched ${agents.size} agents from ACP registry")
                Result.success(agents)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching ACP registry: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Parse the registry JSON response into a list of AcpAgentEntry.
     * The registry JSON structure contains an "agents" array with agent objects.
     */
    private fun parseRegistryResponse(json: String): List<AcpAgentEntry> {
        val agents = mutableListOf<AcpAgentEntry>()
        try {
            // Use org.json for parsing (available in Android)
            val root = org.json.JSONObject(json)

            // The registry might have agents directly or nested under a key
            val agentsArray = when {
                root.has("agents") -> root.getJSONArray("agents")
                root.has("entries") -> root.getJSONArray("entries")
                else -> {
                    // Try to find the first array in the root object
                    root.keys().asSequence()
                        .map { root.optJSONArray(it) }
                        .firstOrNull { it != null }
                        ?: return emptyList()
                }
            }

            for (i in 0 until agentsArray.length()) {
                val agentObj = agentsArray.getJSONObject(i)
                val entry = parseAgentEntry(agentObj)
                if (entry != null) {
                    agents.add(entry)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing registry JSON: ${e.message}")
        }
        return agents
    }

    /**
     * Parse a single agent entry from JSON.
     * Handles various field name variations in the registry.
     */
    private fun parseAgentEntry(obj: org.json.JSONObject): AcpAgentEntry? {
        return try {
            val id = obj.optString("id", obj.optString("name", "").lowercase().replace(" ", "-"))
            val name = obj.optString("name", id.replace("-", " ").replaceFirstChar { it.uppercase() })
            val version = obj.optString("version", "unknown")
            val description = obj.optString("description", obj.optString("desc", ""))
            val repository = obj.optString("repository", obj.optString("repo", obj.optString("github", null)))
            val icon = obj.optString("icon", obj.optString("iconUrl", obj.optString("logo", null)))
            val homepage = obj.optString("homepage", obj.optString("website", obj.optString("url", null)))
            val installCommand = obj.optString("installCommand", obj.optString("install", obj.optString("cliInstallCmd", null)))
            val binary = obj.optString("binary", obj.optString("bin", obj.optString("executable", null)))

            // Parse auth methods
            val authMethods = if (obj.has("authMethods")) {
                val arr = obj.getJSONArray("authMethods")
                List(arr.length()) { arr.optString(it) }
            } else {
                null
            }

            // Parse tags
            val tags = if (obj.has("tags")) {
                val arr = obj.getJSONArray("tags")
                List(arr.length()) { arr.optString(it) }
            } else {
                null
            }

            AcpAgentEntry(
                id = id.takeIf { it.isNotEmpty() } ?: return null,
                name = name.takeIf { it.isNotEmpty() } ?: id,
                version = version,
                description = description,
                repository = repository,
                icon = icon,
                homepage = homepage,
                installCommand = installCommand,
                binary = binary,
                authMethods = authMethods?.takeIf { it.isNotEmpty() },
                tags = tags?.takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing agent entry: ${e.message}")
            null
        }
    }
}
