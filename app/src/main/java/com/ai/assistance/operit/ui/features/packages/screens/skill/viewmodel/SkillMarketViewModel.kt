package com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

/**
 * Skill Market ViewModel - fetches skills from ClawHub (clawhub.ai)
 * and skills.sh (GitHub-based) registries.
 */
class SkillMarketViewModel(
    private val context: Context,
    private val skillRepository: SkillRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SkillMarketViewModel"
        private const val CLAWHUB_BASE_URL = "https://clawhub.ai/api/v1"
        private const val PAGE_SIZE = 30
    }

    private val json = Json { ignoreUnknownKeys = true }

    // --- ClawHub Data Models ---

    @Serializable
    data class ClawHubSearchResponse(
        val results: List<ClawHubSearchResult> = emptyList()
    )

    @Serializable
    data class ClawHubSearchResult(
        val score: Double = 0.0,
        val slug: String = "",
        val displayName: String = "",
        val summary: String = "",
        val version: String? = null,
        val updatedAt: Long = 0
    )

    @Serializable
    data class ClawHubPackagesResponse(
        val items: List<ClawHubPackage> = emptyList(),
        val nextCursor: String? = null
    )

    @Serializable
    data class ClawHubPackage(
        val name: String = "",
        val displayName: String = "",
        val summary: String = "",
        val latestVersion: String = "",
        val ownerHandle: String = "",
        val family: String = "",
        val isOfficial: Boolean = false,
        val createdAt: Long = 0,
        val updatedAt: Long = 0,
        val capabilityTags: List<String> = emptyList()
    )

    @Serializable
    data class ClawHubSkillDetail(
        val slug: String = "",
        val displayName: String = "",
        val summary: String = "",
        val description: String = "",
        val latestVersion: String = "",
        val ownerHandle: String = "",
        val createdAt: Long = 0,
        val updatedAt: Long = 0
    )

    // Unified skill item for UI display
    data class SkillItem(
        val id: String,
        val displayName: String,
        val summary: String,
        val version: String,
        val owner: String,
        val source: String, // "clawhub" or "skillsh"
        val downloadUrl: String?,
        val repoUrl: String?,
        val updatedAt: Long
    )

    // --- UI State ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillItem>>(emptyList())
    val skills: StateFlow<List<SkillItem>> = _skills.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installingSkills = MutableStateFlow<Set<String>>(emptySet())
    val installingSkills: StateFlow<Set<String>> = _installingSkills.asStateFlow()

    private val _installedSkillNames = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillNames: StateFlow<Set<String>> = _installedSkillNames.asStateFlow()

    private var clawhubCursor: String? = null
    private var isLoadingInternal = false

    // --- Public methods ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadSkills() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            clawhubCursor = null
            try {
                val query = _searchQuery.value
                val items = if (query.isNotBlank()) {
                    fetchFromClawHubSearch(query)
                } else {
                    fetchFromClawHubList()
                }
                _skills.value = items
                refreshInstalledSkills()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_load_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to load skills", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreSkills() {
        if (isLoadingInternal || clawhubCursor == null) return
        viewModelScope.launch {
            isLoadingInternal = true
            _isLoadingMore.value = true
            try {
                val moreItems = fetchFromClawHubList(cursor = clawhubCursor)
                _skills.value = (_skills.value + moreItems).distinctBy { it.id }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load more skills", e)
            } finally {
                _isLoadingMore.value = false
                isLoadingInternal = false
            }
        }
    }

    fun installSkill(skill: SkillItem) {
        viewModelScope.launch {
            _installingSkills.value = _installingSkills.value + skill.id
            try {
                val result = when (skill.source) {
                    "clawhub" -> installFromClawHub(skill)
                    "skillsh" -> installFromGitHub(skill)
                    else -> installFromGitHub(skill)
                }
                if (result) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.skill_install_success, skill.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    _errorMessage.value = context.getString(R.string.skill_install_failed, skill.displayName)
                }
                refreshInstalledSkills()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skill_install_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to install skill ${skill.displayName}", e)
            } finally {
                _installingSkills.value = _installingSkills.value - skill.id
            }
        }
    }

    fun refreshInstalledSkills() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                try {
                    skillRepository.getAvailableSkillPackages().keys.toSet()
                } catch (_: Exception) {
                    emptySet()
                }
            }
            _installedSkillNames.value = installed
        }
    }

    // --- Private helpers ---

    private suspend fun fetchFromClawHubSearch(query: String): List<SkillItem> {
        return withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$CLAWHUB_BASE_URL/search?q=$encodedQuery&limit=$PAGE_SIZE"
            val body = httpGet(urlString)
            val response = json.decodeFromString<ClawHubSearchResponse>(body)
            clawhubCursor = null // Search doesn't support cursor pagination
            response.results.map { result ->
                SkillItem(
                    id = result.slug,
                    displayName = result.displayName.ifBlank { result.slug },
                    summary = result.summary,
                    version = result.version ?: "",
                    owner = "",
                    source = "clawhub",
                    downloadUrl = "$CLAWHUB_BASE_URL/download?slug=${result.slug}",
                    repoUrl = null,
                    updatedAt = result.updatedAt
                )
            }
        }
    }

    private suspend fun fetchFromClawHubList(cursor: String? = null): List<SkillItem> {
        return withContext(Dispatchers.IO) {
            val params = mutableListOf("limit=$PAGE_SIZE")
            if (cursor != null) {
                params.add("cursor=${URLEncoder.encode(cursor, "UTF-8")}")
            }
            val urlString = "$CLAWHUB_BASE_URL/packages?${params.joinToString("&")}"
            val body = httpGet(urlString)
            val response = json.decodeFromString<ClawHubPackagesResponse>(body)
            clawhubCursor = response.nextCursor
            response.items.map { pkg ->
                SkillItem(
                    id = pkg.name,
                    displayName = pkg.displayName.ifBlank { pkg.name },
                    summary = pkg.summary,
                    version = pkg.latestVersion,
                    owner = pkg.ownerHandle,
                    source = "clawhub",
                    downloadUrl = "$CLAWHUB_BASE_URL/download?slug=${pkg.name}",
                    repoUrl = null,
                    updatedAt = pkg.updatedAt
                )
            }
        }
    }

    private suspend fun installFromClawHub(skill: SkillItem): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val downloadUrl = skill.downloadUrl ?: return@withContext false
                val tempFile = File(context.cacheDir, "skill_${skill.id}.zip")
                val success = downloadToFile(downloadUrl, tempFile)
                if (!success) return@withContext false

                val result = skillRepository.importSkillFromZip(tempFile)
                tempFile.delete()
                result.startsWith(context.getString(R.string.skill_imported))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to install from ClawHub: ${skill.id}", e)
                false
            }
        }
    }

    private suspend fun installFromGitHub(skill: SkillItem): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repoUrl = skill.repoUrl ?: return@withContext false
                val result = skillRepository.importSkillFromGitHubRepo(repoUrl)
                result.startsWith(context.getString(R.string.skill_imported))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to install from GitHub: ${skill.id}", e)
                false
            }
        }
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "TwentAI/1.0")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            val code = conn.responseCode
            if (code == 200) {
                return conn.inputStream.bufferedReader().readText()
            } else {
                throw Exception("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadToFile(urlString: String, outFile: File): Boolean {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "TwentAI/1.0")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) return false
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            return outFile.exists() && outFile.length() > 0
        } finally {
            conn.disconnect()
        }
    }

    class Factory(
        private val context: Context,
        private val skillRepository: SkillRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SkillMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SkillMarketViewModel(context, skillRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
