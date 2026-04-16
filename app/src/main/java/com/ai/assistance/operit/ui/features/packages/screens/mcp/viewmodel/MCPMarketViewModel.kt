package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * MCP Market ViewModel - fetches MCP servers from the official MCP Registry API
 * (registry.modelcontextprotocol.io)
 */
class MCPMarketViewModel(
    private val context: Context,
    private val mcpRepository: MCPRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MCPMarketViewModel"
        private const val REGISTRY_BASE_URL = "https://registry.modelcontextprotocol.io"
        private const val PAGE_SIZE = 30
    }

    private val json = Json { ignoreUnknownKeys = true }

    // --- Data models for MCP Registry API ---

    @Serializable
    data class RegistryResponse(
        val servers: List<RegistryServerEntry> = emptyList(),
        val metadata: RegistryMetadata? = null
    )

    @Serializable
    data class RegistryMetadata(
        val nextCursor: String? = null,
        val count: Int = 0
    )

    @Serializable
    data class RegistryServerEntry(
        val server: RegistryServer,
        val _meta: RegistryMeta? = null
    )

    @Serializable
    data class RegistryServer(
        val name: String = "",
        val description: String = "",
        val title: String = "",
        val version: String = "",
        val remotes: List<RegistryRemote>? = null,
        val repository: RegistryRepository? = null,
        val packages: List<RegistryPackage>? = null
    )

    @Serializable
    data class RegistryRemote(
        val type: String = "",
        val url: String = ""
    )

    @Serializable
    data class RegistryRepository(
        val url: String = "",
        val source: String = ""
    )

    @Serializable
    data class RegistryPackage(
        val registryType: String = "",
        val identifier: String = "",
        val version: String = "",
        val transport: RegistryTransport? = null
    )

    @Serializable
    data class RegistryTransport(
        val type: String = "",
        val url: String? = null
    )

    @Serializable
    data class RegistryMeta(
        val status: String = "",
        val publishedAt: String = "",
        val updatedAt: String = "",
        val isLatest: Boolean = false
    )

    // --- UI State ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _servers = MutableStateFlow<List<RegistryServerEntry>>(emptyList())
    val servers: StateFlow<List<RegistryServerEntry>> = _servers.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installingServers = MutableStateFlow<Set<String>>(emptySet())
    val installingServers: StateFlow<Set<String>> = _installingServers.asStateFlow()

    private val _installedServerNames = MutableStateFlow<Set<String>>(emptySet())
    val installedServerNames: StateFlow<Set<String>> = _installedServerNames.asStateFlow()

    private var nextCursor: String? = null
    private var isLoadingInternal = false

    // --- Public methods ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadServers() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            nextCursor = null
            try {
                val query = _searchQuery.value
                val result = fetchServers(search = query.ifBlank { null }, cursor = null)
                _servers.value = result.servers
                nextCursor = result.metadata?.nextCursor
                refreshInstalledStatus()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.mcp_market_load_failed_with_error, e.message ?: "")
                AppLogger.e(TAG, "Failed to load MCP servers from registry", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreServers() {
        if (isLoadingInternal || nextCursor == null) return
        viewModelScope.launch {
            isLoadingInternal = true
            _isLoadingMore.value = true
            try {
                val query = _searchQuery.value
                val result = fetchServers(search = query.ifBlank { null }, cursor = nextCursor)
                _servers.value = (_servers.value + result.servers).distinctBy { it.server.name + it.server.version }
                nextCursor = result.metadata?.nextCursor
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load more MCP servers", e)
            } finally {
                _isLoadingMore.value = false
                isLoadingInternal = false
            }
        }
    }

    fun installServer(entry: RegistryServerEntry) {
        val serverName = entry.server.name
        if (serverName.isBlank()) return

        viewModelScope.launch {
            _installingServers.value = _installingServers.value + serverName
            try {
                val server = entry.server
                val meta = entry._meta

                // Build the install config based on the server's transport info
                val installConfig = buildInstallConfig(server)

                if (installConfig != null) {
                    // Check if config merge is enough or physical install needed
                    val needsPhysical = mcpRepository.checkConfigNeedsPhysicalInstallation(installConfig)

                    if (!needsPhysical) {
                        // Direct config merge
                        val mcpLocalServer = MCPLocalServer.getInstance(context)
                        val mergeResult = mcpLocalServer.mergeConfigFromJson(installConfig)
                        mergeResult.fold(
                            onSuccess = { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.mcp_market_config_import_success_with_count, server.title.ifBlank { serverName }, count),
                                    Toast.LENGTH_SHORT
                                ).show()
                                mcpRepository.refreshPluginList()
                            },
                            onFailure = { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.mcp_market_config_import_failed_with_error, error.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    } else {
                        // Physical installation needed
                        val pluginId = serverName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                        val metadata = MCPLocalServer.PluginMetadata(
                            id = pluginId,
                            name = server.title.ifBlank { serverName },
                            description = server.description,
                            logoUrl = null,
                            author = extractOwnerFromName(serverName),
                            version = server.version,
                            updatedAt = meta?.updatedAt ?: "",
                            longDescription = server.description,
                            repoUrl = server.repository?.url ?: "",
                            type = if (server.remotes.isNullOrEmpty()) "local" else "remote",
                            endpoint = server.remotes?.firstOrNull()?.url,
                            connectionType = server.remotes?.firstOrNull()?.type,
                            marketConfig = installConfig
                        )
                        val result = mcpRepository.installMCPServerWithObject(metadata) { _ -> }
                        when (result) {
                            is com.ai.assistance.operit.data.mcp.InstallResult.Success -> {
                                Toast.makeText(context, context.getString(R.string.mcp_market_install_success, metadata.name), Toast.LENGTH_SHORT).show()
                            }
                            is com.ai.assistance.operit.data.mcp.InstallResult.Error -> {
                                _errorMessage.value = context.getString(R.string.mcp_market_install_failed_with_error, result.message)
                            }
                        }
                    }
                } else {
                    // No install config available - try adding as remote server
                    if (!server.remotes.isNullOrEmpty()) {
                        val remote = server.remotes.first()
                        val pluginId = serverName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                        val metadata = MCPLocalServer.PluginMetadata(
                            id = pluginId,
                            name = server.title.ifBlank { serverName },
                            description = server.description,
                            author = extractOwnerFromName(serverName),
                            version = server.version,
                            type = "remote",
                            endpoint = remote.url,
                            connectionType = remote.type
                        )
                        val result = mcpRepository.installMCPServerWithObject(metadata) { _ -> }
                        when (result) {
                            is com.ai.assistance.operit.data.mcp.InstallResult.Success -> {
                                Toast.makeText(context, context.getString(R.string.mcp_market_install_success, metadata.name), Toast.LENGTH_SHORT).show()
                            }
                            is com.ai.assistance.operit.data.mcp.InstallResult.Error -> {
                                _errorMessage.value = context.getString(R.string.mcp_market_install_failed_with_error, result.message)
                            }
                        }
                    } else {
                        _errorMessage.value = context.getString(R.string.mcp_market_parse_install_info_failed)
                    }
                }

                refreshInstalledStatus()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.mcp_market_install_failed_with_error, e.message ?: "")
                AppLogger.e(TAG, "Failed to install MCP server $serverName", e)
            } finally {
                _installingServers.value = _installingServers.value - serverName
            }
        }
    }

    // --- Private helpers ---

    private suspend fun fetchServers(search: String?, cursor: String?): RegistryResponse {
        return withContext(Dispatchers.IO) {
            val params = mutableListOf("limit=$PAGE_SIZE")
            if (!search.isNullOrBlank()) {
                params.add("search=${URLEncoder.encode(search, "UTF-8")}")
            }
            if (!cursor.isNullOrBlank()) {
                params.add("cursor=${URLEncoder.encode(cursor, "UTF-8")}")
            }
            val urlString = "$REGISTRY_BASE_URL/v0.1/servers?${params.joinToString("&")}"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "TwentAI/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            try {
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    json.decodeFromString<RegistryResponse>(body)
                } else {
                    throw Exception("HTTP $responseCode")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun buildInstallConfig(server: RegistryServer): String? {
        // If server has remotes, build a remote config
        if (!server.remotes.isNullOrEmpty()) {
            val remote = server.remotes.first()
            val configObj = buildString {
                append("{")
                append("\"mcpServers\":{")
                append("\"${server.name.replace("\"", "\\\"")}\":{")
                if (remote.type == "streamable-http" || remote.type == "http") {
                    append("\"url\":\"${remote.url.replace("\"", "\\\"")}\"")
                } else if (remote.type == "sse") {
                    append("\"url\":\"${remote.url.replace("\"", "\\\"")}\"")
                }
                append("}}}")
            }
            return configObj
        }

        // If server has packages (npm, pip, etc.), build a command-based config
        if (!server.packages.isNullOrEmpty()) {
            val pkg = server.packages.first()
            val configObj = buildString {
                append("{")
                append("\"mcpServers\":{")
                append("\"${server.name.replace("\"", "\\\"")}\":{")
                when (pkg.registryType) {
                    "npm" -> {
                        append("\"command\":\"npx\",")
                        append("\"args\":[\"-y\",\"${pkg.identifier.replace("\"", "\\\"")}\"")
                        if (pkg.version.isNotBlank()) {
                            append("@${pkg.version.replace("\"", "\\\"")}")
                        }
                        append("]")
                    }
                    "pypi" -> {
                        append("\"command\":\"uvx\",")
                        append("\"args\":[\"${pkg.identifier.replace("\"", "\\\"")}\"")
                        if (pkg.version.isNotBlank()) {
                            append("==${pkg.version.replace("\"", "\\\"")}")
                        }
                        append("]")
                    }
                    "docker" -> {
                        append("\"command\":\"docker\",")
                        append("\"args\":[\"run\",\"-i\",\"--rm\",\"${pkg.identifier.replace("\"", "\\\"")}\"")
                        if (pkg.version.isNotBlank()) {
                            append(":${pkg.version.replace("\"", "\\\"")}")
                        }
                        append("]")
                    }
                    else -> {
                        // Generic
                        append("\"command\":\"${pkg.identifier.replace("\"", "\\\"")}\"")
                    }
                }
                append("}}}")
            }
            return configObj
        }

        return null
    }

    private fun extractOwnerFromName(name: String): String {
        // Names like "io.github.user/server" -> "user"
        val parts = name.split("/")
        if (parts.size >= 2) {
            val domainParts = parts[0].split(".")
            if (domainParts.size >= 3 && domainParts[0] == "io" && domainParts[1] == "github") {
                return domainParts[2]
            }
        }
        return name
    }

    private fun refreshInstalledStatus() {
        val installed = mcpRepository.installedPluginIds.value
        _installedServerNames.value = installed
    }

    class Factory(
        private val context: Context,
        private val mcpRepository: MCPRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MCPMarketViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
