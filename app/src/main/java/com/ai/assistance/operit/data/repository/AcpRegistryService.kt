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
                
                // Log distribution types for debugging
                val npxCount = agents.count { it.distribution?.npx != null }
                val binaryCount = agents.count { it.distribution?.binary != null }
                val uvxCount = agents.count { it.distribution?.uvx != null }
                AppLogger.i(TAG, "Distribution types: npx=$npxCount, binary=$binaryCount, uvx=$uvxCount")
                
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
     * Handles the ACP registry structure with distribution objects.
     */
    private fun parseAgentEntry(obj: org.json.JSONObject): AcpAgentEntry? {
        return try {
            val id = obj.optString("id", "")
            if (id.isEmpty()) {
                AppLogger.w(TAG, "Agent entry missing id, skipping")
                return null
            }
            
            val name = obj.optString("name", id.replace("-", " ").replaceFirstChar { it.uppercase() })
            val version = obj.optString("version", "unknown")
            val description = obj.optString("description", "")
            val repository = obj.optString("repository", null)
            val icon = obj.optString("icon", null)
            val homepage = obj.optString("homepage", obj.optString("website", null))
            val license = obj.optString("license", null)
            
            // Parse authors array
            val authors = if (obj.has("authors")) {
                val arr = obj.getJSONArray("authors")
                List(arr.length()) { arr.optString(it) }
            } else {
                null
            }
            
            // Parse distribution object
            val distribution = if (obj.has("distribution")) {
                val distObj = obj.getJSONObject("distribution")
                parseDistribution(distObj)
            } else {
                null
            }
            
            AcpAgentEntry(
                id = id,
                name = name,
                version = version,
                description = description,
                repository = repository,
                icon = icon,
                homepage = homepage,
                distribution = distribution,
                license = license,
                authors = authors?.takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing agent entry: ${e.message}")
            null
        }
    }
    
    /**
     * Parse distribution object from JSON.
     */
    private fun parseDistribution(obj: org.json.JSONObject): AcpDistribution {
        // Parse npx distribution
        val npx = if (obj.has("npx")) {
            val npxObj = obj.getJSONObject("npx")
            val packageName = npxObj.optString("package", "")
            if (packageName.isNotEmpty()) {
                val args = if (npxObj.has("args")) {
                    val arr = npxObj.getJSONArray("args")
                    List(arr.length()) { arr.optString(it) }
                } else null
                
                val env = if (npxObj.has("env")) {
                    val envObj = npxObj.getJSONObject("env")
                    val envMap = mutableMapOf<String, String>()
                    for (key in envObj.keys()) {
                        envMap[key] = envObj.optString(key, "")
                    }
                    envMap
                } else null
                
                AcpNpxDistribution(package = packageName, args = args, env = env)
            } else null
        } else null
        
        // Parse binary distribution
        val binary = if (obj.has("binary")) {
            val binaryObj = obj.getJSONObject("binary")
            val binaryMap = mutableMapOf<String, AcpBinaryDistribution>()
            for (platform in binaryObj.keys()) {
                val platformObj = binaryObj.getJSONObject(platform)
                val archive = platformObj.optString("archive", "")
                val cmd = platformObj.optString("cmd", "")
                if (archive.isNotEmpty() && cmd.isNotEmpty()) {
                    val args = if (platformObj.has("args")) {
                        val arr = platformObj.getJSONArray("args")
                        List(arr.length()) { arr.optString(it) }
                    } else null
                    
                    binaryMap[platform] = AcpBinaryDistribution(
                        archive = archive,
                        cmd = cmd,
                        args = args
                    )
                }
            }
            binaryMap.takeIf { it.isNotEmpty() }
        } else null
        
        // Parse uvx distribution
        val uvx = if (obj.has("uvx")) {
            val uvxObj = obj.getJSONObject("uvx")
            val packageName = uvxObj.optString("package", "")
            if (packageName.isNotEmpty()) {
                val args = if (uvxObj.has("args")) {
                    val arr = uvxObj.getJSONArray("args")
                    List(arr.length()) { arr.optString(it) }
                } else null
                
                AcpUvxDistribution(package = packageName, args = args)
            } else null
        } else null
        
        return AcpDistribution(npx = npx, binary = binary, uvx = uvx)
    }
}
