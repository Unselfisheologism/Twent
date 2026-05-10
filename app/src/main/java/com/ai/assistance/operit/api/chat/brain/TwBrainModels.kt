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
    var totalSessions: Int = 0,
    var totalTokens: Long = 0,
    var totalToolCalls: Int = 0,
    val toolCallCounts: MutableMap<String, Int> = mutableMapOf(), // toolName -> count
    val providerUsage: MutableMap<String, Int> = mutableMapOf(), // provider -> count
    var lastSessionAt: Date? = null,
    val sessionDates: MutableList<Date> = mutableListOf(),
    var stalledCount: Int = 0, // times agent got stuck
    var successfulTasks: Int = 0
) {

    fun trackToolCall(toolName: String) {
        totalToolCalls++
        toolCallCounts[toolName] = (toolCallCounts[toolName] ?: 0) + 1
    }
}

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
    var memory: TwBrainMemory = TwBrainMemory(),
    val userProfile: TwUserProfile = TwUserProfile(),
    val midSessionNotes: MutableList<TwMidSessionNote> = mutableListOf(),
    var mode: TwAgentMode = TwAgentMode(),
    val activeBranches: MutableList<TwSessionBranch> = mutableListOf(),
    val fileSnapshots: MutableMap<String, TwFileSnapshot> = mutableMapOf(), // path -> snapshot
    val iterationBudget: Int = 20, // Max tool-call iterations per turn
    var currentIteration: Int = 0,
    val maxContextTokens: Int = 160_000, // Conservative for mobile
    val sessionStartedAt: Date = Date(),
    /** hermes-agent-style: skills loaded via /slash-command. Persists for the session. */
    val loadedSkills: MutableList<TwLoadedSkill> = mutableListOf()
) {
    /**
     * Inject memory-relevant context for the current prompt.
     * Returns formatted string to prepend to system prompt.
     */
    /**
     * FIX 2: hermes-agent-style bounded memory injection.
     * Hard cap: ~2,200 chars total for entire memory block.
     * Simple key-value format, no metadata overhead.
     * Sorted by importance; fills until cap reached.
     */
    fun buildMemoryInjection(userQuery: String): String {
        val sb = StringBuilder()
        val MEMORY_CAP = 2000

        // MEMORY.md section (~2,200 char limit in hermes-agent)
        if (memory.memories.isNotEmpty()) {
            sb.appendLine("\n[MEMORY]")
            for (entry in memory.memories
                .sortedByDescending { it.importance }
                .take(15)
            ) {
                val line = "  ${entry.title}: ${entry.content}"
                if (sb.length + line.length > MEMORY_CAP) break
                sb.appendLine(line)
            }
        }

        // USER.md section (~1,375 char limit in hermes-agent)
        if (userProfile.name.isNotEmpty()) {
            val profileLines = buildString {
                appendLine("  user_name: ${userProfile.name}")
                if (userProfile.bio.isNotEmpty()) appendLine("  user_bio: ${userProfile.bio}")
                if (userProfile.communicationStyle.isNotEmpty()) {
                    appendLine("  user_style: ${userProfile.communicationStyle}")
                }
                if (userProfile.ongoingProjects.isNotEmpty()) {
                    appendLine("  user_projects: ${userProfile.ongoingProjects.joinToString(", ")}")
                }
                // Flatten preferences
                for ((k, v) in userProfile.preferences) {
                    appendLine("  user_pref_${k}: $v")
                }
            }
            if (sb.length + profileLines.length <= MEMORY_CAP) {
                sb.appendLine("\n[USER]")
                sb.append(profileLines)
            }
        }

        // Mid-session steer directives (only directive-type notes, kept minimal)
        if (midSessionNotes.isNotEmpty()) {
            val steerLines = buildString {
                for (note in midSessionNotes.filter { it.type == NoteType.STEER_DIRECTIVE }) {
                    val line = "  steer: ${note.content.take(150)}"
                    if (sb.length + length + line.length > MEMORY_CAP) break
                    appendLine(line)
                }
            }
            if (steerLines.isNotEmpty() && sb.length + steerLines.length <= MEMORY_CAP) {
                sb.appendLine("\n[STEER]")
                sb.append(steerLines)
            }
        }

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
     * Delegates to TwBrainInsights.trackToolCall().
     */
    fun trackToolCall(toolName: String) {
        memory.insights.trackToolCall(toolName)
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
