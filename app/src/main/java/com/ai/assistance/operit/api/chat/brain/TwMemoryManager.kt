package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Twent Brain Memory Manager.
 * Implements hermes-agent's MEMORY.md and USER.md persistence system.
 *
 * File structure:
 *   {app_data}/brain/
 *     MEMORY.md       — human-readable + JSON-backed memory entries
 *     USER.md         — user profile data
 *     PERSONAS/       — available personas (SOUL.md equivalents)
 *     BRANCHES/       — session branches
 *     INSIGHTS.md     — cross-session analytics
 *     SOUL.md         — active persona (default)
 *
 * Storage location: app internal files dir (Context.getFilesDir())
 * or external if internal is too small.
 */
@Serializable
data class TwPersonaFile(
    val id: String,
    val name: String,
    val description: String,
    val systemPromptAddition: String,
    val createdAt: String
)

class TwMemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "TwMemoryManager"
        private const val BRAIN_DIR = "brain"
        private const val MEMORY_FILE = "MEMORY.md"
        private const val USER_FILE = "USER.md"
        private const val INSIGHTS_FILE = "INSIGHTS.md"
        private const val SOUL_FILE = "SOUL.md"
        private const val MEMORY_JSON_FILE = "memory_data.json"
        private const val USER_JSON_FILE = "user_data.json"
        private const val PERSONAS_DIR = "PERSONAS"
        private const val BRANCHES_DIR = "BRANCHES"

        @Volatile private var INSTANCE: TwMemoryManager? = null

        fun getInstance(context: Context): TwMemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwMemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }

    private val brainDir: File by lazy {
        val internal = File(context.filesDir, BRAIN_DIR)
        if (internal.freeSpace > 10_000_000) {
            internal
        } else {
            // Fallback to external storage
            val external = File(context.getExternalFilesDir(null), BRAIN_DIR)
            external
        }
    }

    private val memoryFile: File get() = File(brainDir, MEMORY_FILE)
    private val memoryJsonFile: File get() = File(brainDir, MEMORY_JSON_FILE)
    private val userFile: File get() = File(brainDir, USER_FILE)
    private val userJsonFile: File get() = File(brainDir, USER_JSON_FILE)
    private val insightsFile: File get() = File(brainDir, INSIGHTS_FILE)
    private val soulFile: File get() = File(brainDir, SOUL_FILE)
    private val personasDir: File get() = File(brainDir, PERSONAS_DIR)
    private val branchesDir: File get() = File(brainDir, BRANCHES_DIR)

    private val mutex = Mutex()

    // ─── INIT ────────────────────────────────────────────────────────────────

    init {
        ensureDirectories()
    }

    private fun ensureDirectories() {
        listOf(brainDir, personasDir, branchesDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    // ─── MEMORY.MD (read/write) ─────────────────────────────────────────────

    /**
     * Load brain memory from disk.
     */
    suspend fun loadMemory(): TwBrainMemory = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (memoryJsonFile.exists()) {
                    val content = memoryJsonFile.readText()
                    val data = json.decodeFromString<TwBrainMemoryData>(content)
                    val memory = data.toBrainMemory()
                    AppLogger.d(TAG, "Loaded ${memory.memories.size} memory entries from disk")
                    return@withContext memory
                }

                // Legacy: try MEMORY.md
                if (memoryFile.exists()) {
                    val entries = parseLegacyMemoryFile(memoryFile)
                    if (entries.isNotEmpty()) {
                        AppLogger.d(TAG, "Migrated ${entries.size} entries from legacy MEMORY.md")
                        // Migrate to JSON
                        saveMemoryJson(TwBrainMemoryData.fromBrainMemory(TwBrainMemory(memories = entries.toMutableList())))
                        return@withContext TwBrainMemory(memories = entries.toMutableList())
                    }
                }

                TwBrainMemory()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load memory", e)
                TwBrainMemory()
            }
        }
    }

    /**
     * Save brain memory to disk.
     */
    suspend fun saveMemory(memory: TwBrainMemory) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // Save JSON (primary)
                saveMemoryJson(TwBrainMemoryData.fromBrainMemory(memory))

                // Also update human-readable MEMORY.md
                val md = buildString {
                    appendLine("# Twent Brain Memory")
                    appendLine()
                    appendLine("Last updated: ${dateFormat.format(Date())}")
                    appendLine()

                    if (memory.personaId != null) {
                        appendLine("## Active Persona: ${memory.personaId}")
                        appendLine()
                    }

                    if (memory.memories.isNotEmpty()) {
                        appendLine("## Memories")
                        appendLine()
                        for (entry in memory.memories.sortedByDescending { it.createdAt }) {
                            appendLine("### ${entry.title}")
                            appendLine("Category: ${entry.category} | Importance: ${entry.importance} | Source: ${entry.source}")
                            appendLine("Tags: ${entry.tags.joinToString(", ")}")
                            appendLine("Created: ${dateFormat.format(entry.createdAt)} | Accessed: ${dateFormat.format(entry.lastAccessedAt)} (${entry.accessCount}x)")
                            appendLine()
                            appendLine(entry.content)
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                    }

                    if (memory.insights.totalSessions > 0) {
                        appendLine("## Insights")
                        appendLine()
                        appendLine("- Sessions: ${memory.insights.totalSessions}")
                        appendLine("- Total tool calls: ${memory.insights.totalToolCalls}")
                        appendLine("- Top tools: ${memory.insights.toolCallCounts.entries.sortedByDescending { it.value }.take(5).joinToString { "${it.key}(${it.value})" }}")
                    }
                }
                memoryFile.writeText(md)
                AppLogger.d(TAG, "Saved ${memory.memories.size} memory entries")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save memory", e)
            }
        }
    }

    /**
     * Add a new memory entry.
     */
    suspend fun addMemory(
        title: String,
        content: String,
        category: String = "general",
        importance: Float = 0.5f,
        source: String = "chat",
        tags: List<String> = emptyList()
    ): TwMemoryEntry {
        val entry = TwMemoryEntry(
            title = title,
            content = content,
            category = category,
            importance = importance,
            source = source,
            tags = tags
        )
        val memory = loadMemory()
        memory.memories.add(entry)
        saveMemory(memory)
        AppLogger.d(TAG, "Added memory: $title")
        return entry
    }

    /**
     * Search memories by keyword.
     */
    suspend fun searchMemories(query: String): List<TwMemoryEntry> {
        val memory = loadMemory()
        val q = query.lowercase()
        return memory.memories.filter { entry ->
            entry.title.lowercase().contains(q) ||
            entry.content.lowercase().contains(q) ||
            entry.tags.any { it.lowercase().contains(q) }
        }.sortedByDescending { it.importance }
    }

    /**
     * Delete a memory entry by ID.
     */
    suspend fun deleteMemory(memoryId: String): Boolean {
        val memory = loadMemory()
        val removed = memory.memories.removeAll { it.id == memoryId }
        if (removed) saveMemory(memory)
        return removed
    }

    /**
     * Summarize older memories to save space.
     * hermes-agent's memory compression equivalent.
     */
    suspend fun compressMemories(maxEntries: Int = 50): Int {
        val memory = loadMemory()
        if (memory.memories.size <= maxEntries) return 0

        val oldOnes = memory.memories
            .sortedByDescending { it.lastAccessedAt }
            .drop(maxEntries)

        memory.memories.removeAll(oldOnes.toSet())
        saveMemory(memory)
        AppLogger.d(TAG, "Compressed: removed ${oldOnes.size} old memories")
        return oldOnes.size
    }

    // ─── USER.MD ───────────────────────────────────────────────────────────

    /**
     * Load user profile.
     */
    suspend fun loadUserProfile(): TwUserProfile = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (userJsonFile.exists()) {
                    val content = userJsonFile.readText()
                    return@withContext json.decodeFromString<TwUserProfile>(content)
                }
                TwUserProfile()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load user profile", e)
                TwUserProfile()
            }
        }
    }

    /**
     * Save user profile.
     */
    suspend fun saveUserProfile(profile: TwUserProfile) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                userJsonFile.writeText(json.encodeToString(profile))

                // Also update human-readable USER.md
                val md = buildString {
                    appendLine("# User Profile")
                    appendLine()
                    if (profile.name.isNotEmpty()) {
                        appendLine("## ${profile.name}")
                        appendLine()
                    }
                    if (profile.bio.isNotEmpty()) {
                        appendLine(profile.bio)
                        appendLine()
                    }
                    if (profile.preferences.isNotEmpty()) {
                        appendLine("## Preferences")
                        appendLine()
                        profile.preferences.forEach { (k, v) ->
                            appendLine("- $k: $v")
                        }
                        appendLine()
                    }
                    if (profile.ongoingProjects.isNotEmpty()) {
                        appendLine("## Ongoing Projects")
                        appendLine()
                        profile.ongoingProjects.forEach { p ->
                            appendLine("- $p")
                        }
                        appendLine()
                    }
                    if (profile.notes.isNotEmpty()) {
                        appendLine("## Notes")
                        appendLine()
                        appendLine(profile.notes)
                    }
                }
                userFile.writeText(md)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save user profile", e)
            }
        }
    }

    /**
     * Update or add user preference.
     */
    suspend fun updateUserPreference(key: String, value: String) {
        val profile = loadUserProfile()
        profile.preferences[key] = value
        saveUserProfile(profile)
    }

    /**
     * Learn user fact from conversation.
     * Called by brain when user reveals something about themselves.
     */
    suspend fun learnUserFact(fact: String, category: String = "fact") {
        addMemory(
            title = "User: $category",
            content = fact,
            category = "user_knowledge",
            importance = 0.7f,
            source = "conversation",
            tags = listOf("user", category)
        )
    }

    // ─── PERSONAS (SOUL.md) ─────────────────────────────────────────────────

    /**
     * Load a persona by ID.
     */
    suspend fun loadPersona(personaId: String): TwPersona? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = File(personasDir, "$personaId.json")
                if (file.exists()) {
                    val data = json.decodeFromString<TwPersonaFile>(file.readText())
                    return@withContext TwPersona(
                        id = data.id,
                        name = data.name,
                        description = data.description,
                        systemPromptAddition = data.systemPromptAddition
                    )
                }
                null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load persona $personaId", e)
                null
            }
        }
    }

    /**
     * Save a persona.
     */
    suspend fun savePersona(persona: TwPersona) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                personasDir.mkdirs()
                val file = File(personasDir, "${persona.id}.json")
                val data = TwPersonaFile(
                    id = persona.id,
                    name = persona.name,
                    description = persona.description,
                    systemPromptAddition = persona.systemPromptAddition,
                    createdAt = dateFormat.format(persona.createdAt)
                )
                file.writeText(json.encodeToString(data))
                AppLogger.d(TAG, "Saved persona: ${persona.name}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save persona", e)
            }
        }
    }

    /**
     * List all available personas.
     */
    suspend fun listPersonas(): List<TwPersona> = mutex.withLock {
        withContext(Dispatchers.IO) {
            personasDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val data = json.decodeFromString<TwPersonaFile>(file.readText())
                        TwPersona(
                            id = data.id,
                            name = data.name,
                            description = data.description,
                            systemPromptAddition = data.systemPromptAddition
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
        }
    }

    /**
     * Get or create the default persona.
     */
    suspend fun getDefaultPersona(): TwPersona {
        val personas = listPersonas()
        if (personas.isNotEmpty()) return personas.first()

        // Create default "Assistant" persona
        val default = TwPersona(
            name = "Assistant",
            description = "Default AI assistant persona",
            systemPromptAddition = """
                You are a helpful, knowledgeable AI assistant. Be concise but thorough.
                Think step by step for complex problems. Use tools proactively.
                Remember context from earlier in this conversation.
            """.trimIndent()
        )
        savePersona(default)
        return default
    }

    // ─── INSIGHTS ───────────────────────────────────────────────────────────

    /**
     * Load insights.
     */
    suspend fun loadInsights(): TwBrainInsights = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (insightsFile.exists()) {
                    val lines = insightsFile.readLines()
                    // Simple key-value parse
                    val insights = TwBrainInsights()
                    for (line in lines) {
                        if (line.startsWith("- Sessions:")) {
                            insights.totalSessions = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    return@withContext insights
                }
                TwBrainInsights()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load insights", e)
                TwBrainInsights()
            }
        }
    }

    /**
     * Record session stats.
     */
    suspend fun recordSession(tokens: Long, toolCalls: Int) {
        val memory = loadMemory()
        memory.insights.totalSessions++
        memory.insights.totalTokens += tokens
        memory.insights.lastSessionAt = Date()
        saveMemory(memory)
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private fun saveMemoryJson(data: TwBrainMemoryData) {
        brainDir.mkdirs()
        memoryJsonFile.writeText(json.encodeToString(data))
    }

    private fun parseLegacyMemoryFile(file: File): List<TwMemoryEntry> {
        val entries = mutableListOf<TwMemoryEntry>()
        val lines = file.readLines()
        var currentTitle = ""
        var currentContent = StringBuilder()
        var inContent = false

        for (line in lines) {
            when {
                line.startsWith("### ") -> {
                    if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
                        entries.add(TwMemoryEntry(
                            title = currentTitle,
                            content = currentContent.toString().trim()
                        ))
                    }
                    currentTitle = line.removePrefix("### ")
                    currentContent = StringBuilder()
                    inContent = false
                }
                line.startsWith("Category:") || line.startsWith("Tags:") || line.startsWith("Created:") -> {
                    inContent = true
                }
                line == "---" || line.isEmpty() -> {
                    if (currentTitle.isNotEmpty() && !inContent && currentContent.isNotEmpty()) {
                        entries.add(TwMemoryEntry(
                            title = currentTitle,
                            content = currentContent.toString().trim()
                        ))
                        currentTitle = ""
                        currentContent = StringBuilder()
                    }
                    inContent = false
                }
                !inContent && line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("Last") -> {
                    currentContent.appendLine(line)
                }
            }
        }

        return entries
    }
}

// ─── SERIALIZABLE DATA CLASSES ────────────────────────────────────────────────

@Serializable
data class TwBrainMemoryData(
    val version: Int = 1,
    val personaId: String? = null,
    val memories: List<TwMemoryEntryData> = emptyList(),
    val insights: TwBrainInsightsData = TwBrainInsightsData()
) {
    fun toBrainMemory(): TwBrainMemory = TwBrainMemory(
        version = version,
        personaId = personaId,
        memories = memories.map { it.toEntry() }.toMutableList(),
        insights = insights.toInsights()
    )

    companion object {
        fun fromBrainMemory(m: TwBrainMemory): TwBrainMemoryData = TwBrainMemoryData(
            version = m.version,
            personaId = m.personaId,
            memories = m.memories.map { TwMemoryEntryData.fromEntry(it) },
            insights = TwBrainInsightsData.fromInsights(m.insights)
        )
    }
}

@Serializable
data class TwMemoryEntryData(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val importance: Float,
    val source: String,
    val tags: List<String>,
    val createdAt: String,
    val lastAccessedAt: String,
    val accessCount: Int
) {
    fun toEntry(): TwMemoryEntry = TwMemoryEntry(
        id = id,
        title = title,
        content = content,
        category = category,
        importance = importance,
        source = source,
        tags = tags,
        createdAt = parseDate(createdAt),
        lastAccessedAt = parseDate(lastAccessedAt),
        accessCount = accessCount
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        fun fromEntry(e: TwMemoryEntry): TwMemoryEntryData = TwMemoryEntryData(
            id = e.id,
            title = e.title,
            content = e.content,
            category = e.category,
            importance = e.importance,
            source = e.source,
            tags = e.tags,
            createdAt = dateFormat.format(e.createdAt),
            lastAccessedAt = dateFormat.format(e.lastAccessedAt),
            accessCount = e.accessCount
        )

        private fun parseDate(s: String): Date {
            return try { dateFormat.parse(s) ?: Date() } catch (e: Exception) { Date() }
        }
    }
}

@Serializable
data class TwBrainInsightsData(
    val totalSessions: Int = 0,
    val totalTokens: Long = 0,
    val totalToolCalls: Int = 0,
    val toolCallCounts: Map<String, Int> = emptyMap(),
    val providerUsage: Map<String, Int> = emptyMap(),
    val stalledCount: Int = 0,
    val successfulTasks: Int = 0
) {
    fun toInsights(): TwBrainInsights = TwBrainInsights(
        totalSessions = totalSessions,
        totalTokens = totalTokens,
        totalToolCalls = totalToolCalls,
        toolCallCounts = toolCallCounts.toMutableMap(),
        providerUsage = providerUsage.toMutableMap(),
        stalledCount = stalledCount,
        successfulTasks = successfulTasks
    )

    companion object {
        fun fromInsights(i: TwBrainInsights): TwBrainInsightsData = TwBrainInsightsData(
            totalSessions = i.totalSessions,
            totalTokens = i.totalTokens,
            totalToolCalls = i.totalToolCalls,
            toolCallCounts = i.toolCallCounts.toMap(),
            providerUsage = i.providerUsage.toMap(),
            stalledCount = i.stalledCount,
            successfulTasks = i.successfulTasks
        )
    }
}
