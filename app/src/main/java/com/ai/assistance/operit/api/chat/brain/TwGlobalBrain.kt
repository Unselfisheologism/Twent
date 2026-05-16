package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking

/**
 * TwGlobalBrain — Shared cross-session memory singleton for ALL agents.
 *
 * Currently only AI Chat has memory via TwAgentChatBrain. The overlay agent
 * (core/agent/v2/Agent) and executor agents start completely blank each session.
 *
 * TwGlobalBrain fixes this by providing:
 * - Memory context injection into overlay and executor system prompts
 * - Shared skill loading across agent types
 * - Memory write access from any agent (tw_remember, tw_recall, etc.)
 * - Aggregated tool-call insights from all agent types
 *
 * ARCHITECTURE:
 *   TwGlobalBrain (singleton)
 *   ├── TwMemoryManager (existing — file-based memory, shared data)
 *   ├── overlayState     (ephemeral overlay agent session state)
 *   └── executorState    (ephemeral executor agent session state)
 *
 * The memory DATA is shared; only the PROMPT INJECTION differs per agent type.
 */
class TwGlobalBrain private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TwGlobalBrain"

        @Volatile private var INSTANCE: TwGlobalBrain? = null

        fun getInstance(ctx: Context): TwGlobalBrain {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwGlobalBrain(ctx.applicationContext).also { INSTANCE = it }
            }
        }

        // Reflective skill loading — avoids compile-time dep on TwSkillsManager
        private fun lazyLoadSkillReflective(skillName: String): TwLoadedSkill? {
            return try {
                val twSm = Class.forName("com.ai.assistance.operit.api.chat.brain.TwSkillsManager")
                val method = twSm.getMethod("loadSkill", String::class.java)
                @Suppress("UNCHECKED_CAST")
                method.invoke(null, skillName) as? TwLoadedSkill
            } catch (_: Exception) {
                null
            }
        }

        // Reflective skill catalog
        @Suppress("UNCHECKED_CAST")
        private fun getSkillsCatalogReflective(): List<TwSkillInfo> {
            return try {
                val twSm = Class.forName("com.ai.assistance.operit.api.chat.brain.TwSkillsManager")
                val method = twSm.getMethod("getSkillsCatalog")
                method.invoke(null) as? List<TwSkillInfo> ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Reuse existing memory manager — same data, different injection
    private val memoryManager = TwMemoryManager.getInstance(context)

    // Per-agent ephemeral state (not persisted)
    private val overlayState = AgentMemoryState()
    private val executorState = AgentMemoryState()

    // ─── Per-agent ephemeral state ───────────────────────────────────────────

    data class AgentMemoryState(
        val loadedSkills: MutableSet<TwLoadedSkill> = mutableSetOf(),
        val midSessionNotes: MutableList<TwMidSessionNote> = mutableListOf(),
        var activeMode: AgentMode = AgentMode.NORMAL,
        var iterationBudget: Int = 20
    )

    enum class AgentMode { NORMAL, YOLO, FAST, DEEP_REASONING }

    enum class AgentType { AI_CHAT, OVERLAY, EXECUTOR }

    // ─── Overlay agent prompt injection ────────────────────────────────────

    /**
     * Build cross-session memory context for the overlay agent.
     * Injected at the start of each overlay task.
     *
     * Format: task-focused, condensed — overlay agents need actionable context.
     */
    fun getOverlaySystemPromptAddition(
        currentTask: String? = null,
        loadedSkills: List<TwSkillInfo> = emptyList()
    ): String {
        val memory = runBlocking { memoryManager.loadMemory() }
        val userProfile = runBlocking { memoryManager.loadUserProfile() }

        return buildString {
            // Memory section — top 10 by importance
            if (memory.memories.isNotEmpty()) {
                appendLine("## CROSS-SESSION MEMORY")
                appendLine("These memories persist across all sessions:")
                appendLine()
                memory.memories
                    .sortedByDescending { it.importance }
                    .take(10)
                    .forEach { entry ->
                        val preview = if (entry.content.length > 100) {
                            entry.content.take(100) + "…"
                        } else entry.content
                        appendLine("- [${entry.category}] ${entry.title}: $preview")
                    }
                appendLine()
            }

            // User context
            if (userProfile.name.isNotEmpty()) {
                appendLine("## USER CONTEXT")
                appendLine("Name: ${userProfile.name}")
                if (userProfile.bio.isNotEmpty()) {
                    appendLine("Bio: ${userProfile.bio}")
                }
                appendLine()
            }

            // Loaded skills (overlay agents can use skills too)
            if (loadedSkills.isNotEmpty()) {
                appendLine("## ACTIVE SKILLS")
                loadedSkills.forEach { skill ->
                    appendLine("- ${skill.name}: ${skill.description}")
                }
                appendLine()
            }

            // Agent-specific mode
            when (overlayState.activeMode) {
                AgentMode.YOLO -> appendLine("MODE: YOLO — skip danger confirmations.\n")
                AgentMode.FAST -> appendLine("MODE: FAST — prioritize speed.\n")
                AgentMode.DEEP_REASONING -> appendLine("MODE: DEEP REASONING — thorough analysis.\n")
                AgentMode.NORMAL -> {}
            }
        }
    }

    /**
     * Condensed one-line memory summary for tight contexts.
     */
    fun getOverlayMemorySummary(): String {
        val memory = runBlocking { memoryManager.loadMemory() }
        val userProfile = runBlocking { memoryManager.loadUserProfile() }

        return buildString {
            if (memory.memories.isNotEmpty()) {
                val top = memory.memories
                    .sortedByDescending { it.importance }
                    .take(3)
                    .joinToString("; ") { "${it.title}: ${it.content.take(50)}" }
                appendLine("## MEMORY")
                appendLine(top)
                appendLine()
            }
            if (userProfile.name.isNotEmpty()) {
                append("User: ${userProfile.name}")
                if (userProfile.ongoingProjects.isNotEmpty()) {
                    append(" | Projects: ${userProfile.ongoingProjects.joinToString(", ")}")
                }
            }
        }.trim()
    }

    // ─── Executor agent prompt injection ───────────────────────────────────

    /**
     * Build cross-session memory context for executor agents.
     * Injected when executor is initialized for a task.
     *
     * Format: minimal, task-relevant only — executors are focused workers.
     */
    fun getExecutorSystemPromptAddition(taskContext: String? = null): String {
        val memory = runBlocking { memoryManager.loadMemory() }
        val userProfile = runBlocking { memoryManager.loadUserProfile() }

        return buildString {
            if (memory.memories.isNotEmpty()) {
                appendLine("## PERSISTENT MEMORY")
                // Find memories relevant to task context
                val relevant = if (taskContext != null) {
                    memory.memories.filter { entry ->
                        entry.content.contains(taskContext, ignoreCase = true) ||
                        entry.tags.any { it.contains(taskContext, ignoreCase = true) }
                    }.take(5)
                } else {
                    memory.memories.sortedByDescending { it.importance }.take(5)
                }

                relevant.forEach { entry ->
                    val preview = if (entry.content.length > 150) {
                        entry.content.take(150) + "…"
                    } else entry.content
                    appendLine("- ${entry.title}: $preview")
                }
                appendLine()
            }

            if (userProfile.name.isNotEmpty()) {
                appendLine("## USER: ${userProfile.name}")
                if (userProfile.preferences.isNotEmpty()) {
                    appendLine("Preferences:")
                    userProfile.preferences.forEach { (k, v) ->
                        appendLine("  - $k: $v")
                    }
                }
                appendLine()
            }
        }
    }

    // ─── Skill loading for non-AI-Chat agents ───────────────────────────────

    /**
     * Load a skill for the overlay agent context.
     * Skill stays active for the overlay session.
     */
    fun loadSkillForOverlay(skillName: String): TwLoadedSkill? {
        val loaded = lazyLoadSkillReflective(skillName) ?: return null
        overlayState.loadedSkills.add(loaded)
        AppLogger.d(TAG, "Overlay: loaded skill $skillName")
        return loaded
    }

    /**
     * Load a skill for the executor agent context.
     */
    fun loadSkillForExecutor(skillName: String): TwLoadedSkill? {
        val loaded = lazyLoadSkillReflective(skillName) ?: return null
        executorState.loadedSkills.add(loaded)
        AppLogger.d(TAG, "Executor: loaded skill $skillName")
        return loaded
    }

    /**
     * Get overlay's currently loaded skills as TwSkillInfo for prompt injection.
     */
    fun getOverlayLoadedSkills(): List<TwSkillInfo> {
        return overlayState.loadedSkills.map { skill ->
            TwSkillInfo(
                name = skill.id,
                displayName = skill.displayName,
                description = skill.description,
                category = "loaded"
            )
        }
    }

    // ─── Memory writes from any agent ─────────────────────────────────────

    /**
     * Save a memory entry from any agent type.
     * Overlay and executor agents call this to persist learned facts.
     */
    suspend fun remember(
        title: String,
        content: String,
        category: String = "general",
        importance: Float = 0.5f,
        source: String = "agent",
        tags: List<String> = emptyList()
    ): TwMemoryEntry {
        return memoryManager.addMemory(title, content, category, importance, source, tags)
    }

    /**
     * Search memories from any agent context.
     */
    suspend fun recall(query: String): List<TwMemoryEntry> {
        return memoryManager.searchMemories(query)
    }

    /**
     * Delete a memory by ID or title.
     */
    suspend fun forget(memoryId: String? = null, title: String? = null): Boolean {
        if (memoryId != null) {
            return memoryManager.deleteMemory(memoryId)
        }
        if (title != null) {
            val memories = memoryManager.searchMemories(title)
            return memories.firstOrNull()?.let { memoryManager.deleteMemory(it.id) } ?: false
        }
        return false
    }

    /**
     * Learn a fact about the user.
     */
    suspend fun learnUser(
        fact: String,
        category: String = "fact"
    ) {
        memoryManager.learnUserFact(fact, category)
    }

    // ─── Tool call tracking (shared insights) ──────────────────────────────

    /**
     * Track a tool call for cross-agent insights.
     * Called after EVERY tool execution from any agent type.
     */
    fun trackToolCall(toolName: String, agentType: AgentType) {
        runBlocking {
            val memory = memoryManager.loadMemory()
            memory.insights.trackToolCall(toolName)
            memoryManager.saveMemory(memory)
            AppLogger.d(TAG, "Tracked tool call: $toolName from $agentType")
        }
    }

    /**
     * Get insights summary string for prompt injection.
     */
    fun getInsightsSummary(): String {
        val insights = runBlocking {
            val memory = memoryManager.loadMemory()
            memory.insights
        }

        return buildString {
            if (insights.totalSessions > 0) {
                appendLine("## AGENT INSIGHTS")
                appendLine("Sessions: ${insights.totalSessions}")
                appendLine("Total tool calls: ${insights.totalToolCalls}")
                val topTools = insights.toolCallCounts.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(", ") { "${it.key}(${it.value})" }
                if (topTools.isNotEmpty()) {
                    appendLine("Top tools: $topTools")
                }
                appendLine()
            }
        }
    }

    // ─── Mode management ──────────────────────────────────────────────────

    fun setOverlayMode(mode: AgentMode) {
        overlayState.activeMode = mode
        AppLogger.d(TAG, "Overlay mode set to: $mode")
    }

    fun getOverlayMode(): AgentMode = overlayState.activeMode

    fun setExecutorMode(mode: AgentMode) {
        executorState.activeMode = mode
    }

    fun getExecutorMode(): AgentMode = executorState.activeMode

    // ─── Brain tool handler for non-AI-Chat contexts ──────────────────────

    /**
     * Handle a brain tool call from overlay or executor context.
     * Routes to the appropriate memory manager operation.
     *
     * Returns a JSON string result or null if the tool wasn't recognized.
     */
    fun handleBrainTool(toolName: String, parameters: Map<String, String>): String? {
        return when (toolName) {
            "tw_remember" -> handleRemember(parameters)
            "tw_recall" -> handleRecall(parameters)
            "tw_forget" -> handleForget(parameters)
            "tw_insights" -> handleInsights()
            "tw_learn_user" -> handleLearnUser(parameters)
            else -> null
        }
    }

    private fun handleRemember(params: Map<String, String>): String {
        val title = params["title"] ?: "Untitled"
        val content = params["content"] ?: params["query"] ?: ""
        val category = params["category"] ?: "general"
        val importance = params["importance"]?.toFloatOrNull() ?: 0.5f
        val tags = params["tags"]?.split(",")?.map { it.trim() } ?: emptyList()

        val entry = runBlocking {
            memoryManager.addMemory(title, content, category, importance, "agent", tags)
        }
        return """{"success": true, "id": "${entry.id}", "title": "${entry.title}"}"""
    }

    private fun handleRecall(params: Map<String, String>): String {
        val query = params["query"] ?: params["content"] ?: ""
        val results = runBlocking { memoryManager.searchMemories(query) }
        val items = results.take(10).joinToString(",") { entry ->
            """{"id": "${entry.id}", "title": "${entry.title}", "content": "${entry.content.take(200)}", "importance": ${entry.importance}}"""
        }
        return """{"success": true, "count": ${results.size}, "results": [$items]}"""
    }

    private fun handleForget(params: Map<String, String>): String {
        val id = params["id"]
        val title = params["title"]
        val deleted = runBlocking { forget(memoryId = id, title = title) }
        return """{"success": $deleted}"""
    }

    private fun handleInsights(): String {
        val insights = runBlocking {
            val memory = memoryManager.loadMemory()
            memory.insights
        }
        return buildString {
            append("{")
            append("\"success\": true, ")
            append("\"totalSessions\": ${insights.totalSessions}, ")
            append("\"totalToolCalls\": ${insights.totalToolCalls}, ")
            append("\"totalMemories\": ${runBlocking { memoryManager.loadMemory() }.memories.size}")
            append("}")
        }
    }

    private fun handleLearnUser(params: Map<String, String>): String {
        val fact = params["fact"] ?: params["content"] ?: ""
        val category = params["category"] ?: "general"
        runBlocking { memoryManager.learnUserFact(fact, category) }
        return """{"success": true}"""
    }
}
