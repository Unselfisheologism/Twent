package com.ai.assistance.operit.api.chat.brain

import java.util.Date
import java.util.UUID

/**
 * Core brain models for the Twent AI Agent.
 * Mirrors hermes-agent's brain concepts adapted for Android.
 */

// ─── PERSONA (SOUL.md equivalent) ────────────────────────────────────────────

/**
 * Represents an agent persona/voice loaded from SOUL.md.
 * hermes-agent's /personality equivalent.
 */
data class TwPersona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val systemPromptAddition: String, // Extra instructions injected into system prompt
    val createdAt: Date = Date()
)

// ─── SLASH COMMAND RESULT ──────────────────────────────────────────────────────

/**
 * Result of processing a slash command (e.g. /android-development).
 * Returned to the caller so it can show feedback and decide whether
 * to still send the message to the AI.
 *
 * hermes-agent's skill loading + built-in command system equivalent.
 */
data class TwSlashResult(
    val wasSlashCommand: Boolean,
    val skillLoaded: String? = null,
    val displayMessage: String? = null,
    /**
     * The cleaned input — if a slash command was found, this is the arguments part.
     * If no slash command was found, this is the original input.
     */
    val cleanedInput: String? = null
)

// ─── MEMORY ───────────────────────────────────────────────────────────────────

/**
 * A single memory entry — analogous to hermes-agent's MEMORY.md entries.
 * Stored as text in MEMORY.md but also kept in a lightweight indexed map.
 */
data class TwMemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "general", // e.g. "project", "user_pref", "fact", "insight"
    val importance: Float = 0.5f, // 0.0–1.0
    val source: String = "chat", // "chat", "user_input", "summary", "skill"
    val tags: List<String> = emptyList(),
    val createdAt: Date = Date(),
    val lastAccessedAt: Date = Date(),
    val accessCount: Int = 0
)

/**
 * Full brain memory state — serializable to MEMORY.md format.
 */
data class TwBrainMemory(
    val version: Int = 1,
    val personaId: String? = null, // Currently active persona ID
    val memories: MutableList<TwMemoryEntry> = mutableListOf(),
    val insights: TwBrainInsights = TwBrainInsights()
)

/**
 * Cross-session analytics — hermes-agent's /insights equivalent.
 */
data class TwBrainInsights(
    val totalSessions: Int = 0,
    val totalTokens: Long = 0,
    val totalToolCalls: Int = 0,
    val toolCallCounts: MutableMap<String, Int> = mutableMapOf(), // toolName -> count
    val providerUsage: MutableMap<String, Int> = mutableMapOf(), // provider -> count
    val lastSessionAt: Date? = null,
    val sessionDates: MutableList<Date> = mutableListOf(),
    val stalledCount: Int = 0, // times agent got stuck
    val successfulTasks: Int = 0
)

// ─── MID-SESSION NOTES (ephemeral context) ────────────────────────────────────

/**
 * Ephemeral note injected for the current session only.
 * hermes-agent's /btw and /steer equivalent.
 * NOT persisted across sessions.
 */
data class TwMidSessionNote(
    val content: String,
    val type: NoteType,
    val createdAt: Date = Date()
)

enum class NoteType {
    /** Quick side question — hermes-agent's /btw */
    EPHEMERAL_BTW,
    /** Mid-flight steering directive — hermes-agent's /steer */
    STEER_DIRECTIVE,
    /** Model/mode toggle */
    MODE_TOGGLE,
    /** Queued next-turn instruction */
    QUEUED_TURN
}

// ─── SESSION BRANCH ───────────────────────────────────────────────────────────

/**
 * A branched session state — hermes-agent's /branch equivalent.
 * Stores a snapshot of the conversation + memory at a point in time.
 */
data class TwSessionBranch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val conversationSnapshot: List<Pair<String, String>>, // role -> content
    val memorySnapshot: TwBrainMemory,
    val createdAt: Date = Date(),
    val parentBranchId: String? = null
)

// ─── MODE FLAGS ───────────────────────────────────────────────────────────────

/**
 * Runtime mode flags for the agent — hermes-agent's /yolo, /fast, /reasoning.
 */
data class TwAgentMode(
    val yoloMode: Boolean = false,        // Skip dangerous-command approvals
    val fastMode: Boolean = false,        // Use faster/cheaper model for grunt work
    val highEffortReasoning: Boolean = false // Use max reasoning effort
)

// ─── USER PROFILE (USER.md equivalent) ───────────────────────────────────────

/**
 * What the agent knows about the user.
 * Persisted to USER.md and injected into system prompt.
 */
data class TwUserProfile(
    val name: String = "",
    val bio: String = "",
    val preferences: MutableMap<String, String> = mutableMapOf(), // key -> value
    val ongoingProjects: MutableList<String> = mutableListOf(),
    val toolsUserLikes: MutableList<String> = mutableListOf(),
    val communicationStyle: String = "friendly",
    val notes: String = "" // Free-form notes about the user
)

// ─── FILE SNAPSHOT (filesystem rollback) ─────────────────────────────────────

/**
 * Filesystem checkpoint for rollback — hermes-agent's /rollback equivalent.
 */
data class TwFileSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val content: String,
    val createdAt: Date = Date()
)

// ─── BRAIN STATE ─────────────────────────────────────────────────────────────

/**
 * The complete brain state for a single chat session.
 * This is the object that gets passed around the brain system.
 */
data class TwBrainState(
    val chatId: String,
    val memory: TwBrainMemory = TwBrainMemory(),
    val userProfile: TwUserProfile = TwUserProfile(),
    val midSessionNotes: MutableList<TwMidSessionNote> = mutableListOf(),
    val mode: TwAgentMode = TwAgentMode(),
    val activeBranches: MutableList<TwSessionBranch> = mutableListOf(),
    val fileSnapshots: MutableMap<String, TwFileSnapshot> = mutableMapOf(), // path -> snapshot
    val iterationBudget: Int = 20, // Max tool-call iterations per turn
    val currentIteration: Int = 0,
    val maxContextTokens: Int = 160_000, // Conservative for mobile
    val sessionStartedAt: Date = Date(),
    /** hermes-agent-style: skills loaded via /slash-command. Persists for the session. */
    val loadedSkills: MutableList<TwLoadedSkill> = mutableListOf()
) {
    /**
     * Inject memory-relevant context for the current prompt.
     * Returns formatted string to prepend to system prompt.
     */
    fun buildMemoryInjection(userQuery: String): String {
        if (memory.memories.isEmpty() && userProfile.name.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.appendLine("\n[BRAIN MEMORY - READ CAREFULLY]")
        sb.appendLine("These are your persistent memories from previous sessions.")
        sb.appendLine("Use them to give context-aware, personalized responses.")

        if (userProfile.name.isNotEmpty()) {
            sb.appendLine("\nUSER PROFILE:")
            sb.appendLine("  Name: ${userProfile.name}")
            if (userProfile.bio.isNotEmpty()) sb.appendLine("  Bio: ${userProfile.bio}")
            if (userProfile.communicationStyle.isNotEmpty()) {
                sb.appendLine("  Communication style: ${userProfile.communicationStyle}")
            }
            if (userProfile.ongoingProjects.isNotEmpty()) {
                sb.appendLine("  Ongoing projects: ${userProfile.ongoingProjects.joinToString(", ")}")
            }
        }

        if (memory.memories.isNotEmpty()) {
            // Sort by relevance (importance + recency) and take top entries
            val relevant = memory.memories
                .sortedByDescending { it.importance * (1 + it.accessCount * 0.1) }
                .take(15)

            sb.appendLine("\nPERSISTENT MEMORIES:")
            for (entry in relevant) {
                sb.appendLine("  [${entry.category}] ${entry.title}: ${entry.content}")
            }
        }

        if (midSessionNotes.isNotEmpty()) {
            val steers = midSessionNotes.filter { it.type == NoteType.STEER_DIRECTIVE }
            if (steers.isNotEmpty()) {
                sb.appendLine("\nMID-SESSION DIRECTIVES:")
                for (note in steers) {
                    sb.appendLine("  → ${note.content}")
                }
            }
        }

        sb.appendLine("[END BRAIN MEMORY]")
        return sb.toString()
    }

    /**
     * Create a branch of the current session.
     */
    fun createBranch(name: String, conversation: List<Pair<String, String>>): TwSessionBranch {
        val branch = TwSessionBranch(
            name = name,
            conversationSnapshot = conversation.toList(),
            memorySnapshot = memory.copy(memories = memory.memories.toMutableList()),
            parentBranchId = null
        )
        activeBranches.add(branch)
        return branch
    }

    /**
     * Access a memory entry and update its access metadata.
     */
    fun touchMemory(memoryId: String) {
        val entry = memory.memories.find { it.id == memoryId } ?: return
        val index = memory.memories.indexOf(entry)
        memory.memories[index] = entry.copy(
            lastAccessedAt = Date(),
            accessCount = entry.accessCount + 1
        )
    }

    /**
     * Track a tool call for insights.
     */
    fun trackToolCall(toolName: String) {
        memory.insights.totalToolCalls++
        memory.insights.toolCallCounts[toolName] =
            (memory.insights.toolCallCounts[toolName] ?: 0) + 1
    }

    /**
     * Decrement iteration budget. Returns false if exhausted.
     */
    fun consumeIteration(): Boolean {
        currentIteration++
        return currentIteration < iterationBudget
    }

    fun resetIteration() {
        currentIteration = 0
    }
}
