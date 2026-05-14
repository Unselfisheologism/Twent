package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.util.AppLogger
import java.io.File

/**
 * Builds the enhanced system prompt for the AI Chat page agent.
 * Injects brain memory, user profile, and persona into the system prompt.
 *
 * hermes-agent's system prompt assembly equivalent:
 * - MEMORY.md injection
 * - USER.md injection
 * - SOUL.md/persona injection
 * - Iteration budget injection
 * - Mid-session notes injection
 */
class TwPromptBuilder(private val context: Context) {

    companion object {
        private const val TAG = "TwPromptBuilder"

        @Volatile private var INSTANCE: TwPromptBuilder? = null

        fun getInstance(context: Context): TwPromptBuilder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwPromptBuilder(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Build the full enhanced system prompt.
     * This is called for EVERY message sent to the AI.
     *
     * @param basePrompt The existing system prompt (from SystemPromptConfig)
     * @param brainState Current brain state for this chat
     * @param userQuery The user's current query (for relevance scoring)
     * @param persona Optional active persona
     * @param loadedSkills Skills loaded via /slash-command (from TwBrainState.loadedSkills)
     * @return The assembled system prompt string
     */
    fun buildEnhancedPrompt(
        basePrompt: String,
        brainState: TwBrainState,
        userQuery: String,
        persona: TwPersona? = null,
        loadedSkills: List<TwLoadedSkill> = emptyList()
    ): String {
        val sb = StringBuilder()

        // 1. The active persona's voice/instructions FIRST
        // hermes-agent's SOUL.md = voice of the agent
        // (Persona is pre-loaded by TwAgentChatBrain.buildEnhancedSystemPrompt before calling here)
        if (persona != null) {
            sb.appendLine("=== PERSONA (${persona.name}) ===")
            sb.appendLine(persona.systemPromptAddition)
            sb.appendLine()
        }

        // 2. Brain memory injection
        // hermes-agent's MEMORY.md injection
        val memoryInjection = brainState.buildMemoryInjection(userQuery)
        if (memoryInjection.isNotEmpty()) {
            sb.appendLine(memoryInjection)
            sb.appendLine()
        }

        // 3. Mid-session notes injection
        // hermes-agent's /btw and /steer
        if (brainState.midSessionNotes.isNotEmpty()) {
            sb.appendLine("=== MID-SESSION CONTEXT ===")
            sb.appendLine("These are active directives for THIS session only:")
            for (note in brainState.midSessionNotes) {
                val prefix = when (note.type) {
                    NoteType.EPHEMERAL_BTW -> "💡 BTW:"
                    NoteType.STEER_DIRECTIVE -> "🧭 STEER:"
                    NoteType.MODE_TOGGLE -> "⚡ MODE:"
                    NoteType.QUEUED_TURN -> "➡️ NEXT:"
                }
                sb.appendLine("  $prefix ${note.content}")
            }
            sb.appendLine()
        }

        // 4. Mode flags
        // hermes-agent's /yolo, /fast, /reasoning
        if (brainState.mode.yoloMode || brainState.mode.fastMode || brainState.mode.highEffortReasoning) {
            sb.appendLine("=== ACTIVE MODE FLAGS ===")
            if (brainState.mode.yoloMode) {
                sb.appendLine("  ⚠️ YOLO MODE: Dangerous-command approvals are SKIPPED. Proceed without asking.")
            }
            if (brainState.mode.fastMode) {
                sb.appendLine("  ⚡ FAST MODE: Use the fastest available model. Prioritize speed over depth.")
            }
            if (brainState.mode.highEffortReasoning) {
                sb.appendLine("  🧠 HIGH-EFFORT REASONING: Spend maximum tokens on reasoning. Think deeply.")
            }
            sb.appendLine()
        }

        // 4b. FIX 3: Progressive disclosure for skills — inject INDEX only, NOT full content.
        // hermes-agent pattern: inject skill names/descriptions; LLM loads full content on-demand.
        // Full skill content is loaded when relevant via the skill_view tool.
        if (loadedSkills.isNotEmpty()) {
            sb.appendLine("=== ACTIVE SKILLS ===")
            sb.appendLine("(loaded via /slash-command — remain active for this session)")
            sb.appendLine("To use a skill's full instructions, call skill_view(name=\"skill_name\")")
            sb.appendLine()
            loadedSkills.forEach { skill ->
                val keywords = if (skill.triggerKeywords.isNotEmpty()) {
                    " | Keywords: ${skill.triggerKeywords.joinToString(", ")}"
                } else ""
                val triggerHint = if (skill.triggerKeywords.isNotEmpty()) {
                    " (or say: ${skill.triggerKeywords.first()})"
                } else ""
                sb.appendLine("  - ${skill.displayName}${triggerHint}${keywords}")
                sb.appendLine("    Category: ${skill.category} | ${skill.description}")
            }
        }

        // 5. Iteration budget notice
        sb.appendLine("=== ITERATION BUDGET ===")
        sb.appendLine("You have ${brainState.iterationBudget - brainState.currentIteration} tool-call iterations remaining in this turn.")
        sb.appendLine("Use them wisely. Combine operations when possible.")
        sb.appendLine()

        // 5b. ⚠️ CRITICAL: Composio toolkit docs — hardcoded, non-negotiable, fires every single time.
        // This overrides ANY progressive disclosure filtering. The AI MUST fetch toolkit docs
        // before calling ANY Composio tool (composio_execute_tool, composio_list_connections, etc.).
        // Tool name: composio_get_toolkit_docs | Required param: toolkit_slug (e.g. "gmail", "github", "slack")
        // URL pattern: https://composio.dev/toolkits/{slug}.md (no API key needed, publicly accessible)
        // Example sequence: user="send an email via gmail" → composio_get_toolkit_docs(toolkit_slug="gmail")
        // → receives gmail.md with GMAIL_SEND_EMAIL → composio_execute_tool(toolkit="gmail", tool_name="GMAIL_SEND_EMAIL", ...)
        // NEVER try to guess a Composio tool name. ALWAYS fetch the toolkit .md first.
        sb.appendLine("=== COMPOSIO TOOLKIT DOCUMENTATION (MANDATORY) ===")
        sb.appendLine("Before calling ANY Composio tool (composio_execute_tool, composio_list_connections, composio_connect, composio_disconnect),")
        sb.appendLine("you MUST first call composio_get_toolkit_docs with the appropriate toolkit_slug to get the full list of")
        sb.appendLine("available tools, their exact names, descriptions, and parameters for that specific toolkit.")
        sb.appendLine()
        sb.appendLine("MANDATORY FLOW:")
        sb.appendLine("  1. Identify which toolkit the user's request belongs to (e.g. gmail, github, slack, notion)")
        sb.appendLine("  2. Call: composio_get_toolkit_docs(toolkit_slug=\"<toolkit_name>\")")
        sb.appendLine("  3. Parse the returned markdown for the exact tool_name you need")
        sb.appendLine("  4. Only THEN call composio_execute_tool with the correct tool_name")
        sb.appendLine()
        sb.appendLine("NEVER guess tool names. Example: \"GMAIL_SEND_EMAIL\" not \"send_email\" or \"gmail_send\".")
        sb.appendLine()

        // 6. NOTE: The base system prompt is NOT included here.
        // TwPromptBuilder's output goes to brainPromptInjection, which is appended AFTER
        // the base prompt in SystemPromptConfig.getSystemPrompt().
        // Including basePrompt here would duplicate it.
        // Layering: [tool guidance] + [base prompt] + [brain context] — hermes-agent style.

        // 7. Brain-specific additional instructions
        sb.appendLine("=== BRAIN ENHANCEMENT RULES ===")
        sb.appendLine("""
            |- You have PERSISTENT MEMORY from previous sessions. Check it before responding to questions about past topics.
            |- You know THINGS ABOUT THE USER. Use that knowledge to personalize responses.
            |- If a user asks about something you discussed before, check your memory.
            |- When you learn something important about the user or their projects, store it with memory tools.
            |- Be AWARE of mid-session directives — they override default behavior temporarily.
            |- Iteration budget is finite. Don't get stuck in loops. If 3 tool calls fail, ask the user.
            |- Remember: you are "AN AI EMPLOYEE IN THEIR POCKET" — act like a knowledgeable colleague, not a chatbot.
            |
            |Brain features available to you:
            |- `tw_remember`: Store important information in persistent memory
            |- `tw_recall`: Search your persistent memory for relevant information
            |- `tw_forget`: Remove a memory entry by title
            |- `tw_insights`: View cross-session analytics
            |- `tw_snapshot`: Save current conversation state for rollback
            |- `tw_rollback`: Restore from a saved snapshot
            |- `tw_branch`: Create a branch of the conversation to try an alternative approach
            |- `tw_persona`: Switch between agent personas
            |- `tw_btw`: Add a quick ephemeral note for the current session
            |- `tw_steer`: Give the agent a mid-session directive
        """.trimMargin())

        val finalPrompt = sb.toString()
        AppLogger.d(TAG, "Built enhanced prompt: ${finalPrompt.length} chars")
        return finalPrompt
    }

    /**
     * Build a condensed prompt for when context is tight.
     * Only includes the most critical brain elements.
     */
    fun buildCondensedPrompt(
        basePrompt: String,
        brainState: TwBrainState,
        persona: TwPersona? = null,
        loadedSkills: List<TwLoadedSkill> = emptyList()
    ): String {
        val sb = StringBuilder()

        // Persona first
        if (persona != null) {
            sb.appendLine("[PERSONA: ${persona.name}] ${persona.systemPromptAddition}")
            sb.appendLine()
        }

        // Condensed memory: only top-priority entries
        if (brainState.memory.memories.isNotEmpty()) {
            val topMemories = brainState.memory.memories
                .sortedByDescending { it.importance }
                .take(5)

            sb.appendLine("[MEMORY]")
            for (entry in topMemories) {
                sb.appendLine("  ${entry.title}: ${entry.content.take(200)}")
            }
            sb.appendLine()
        }

        // Mode flags
        if (brainState.mode.yoloMode || brainState.mode.fastMode) {
            val flags = mutableListOf<String>()
            if (brainState.mode.yoloMode) flags.add("YOLO")
            if (brainState.mode.fastMode) flags.add("FAST")
            sb.appendLine("[MODE: ${flags.joinToString(" | ")}]")
        }

        // Mid-session notes (only steer directives)
        val steers = brainState.midSessionNotes.filter { it.type == NoteType.STEER_DIRECTIVE }
        if (steers.isNotEmpty()) {
            sb.append("[STEER] ")
            sb.appendLine(steers.joinToString(" | ") { it.content })
        }

        sb.appendLine()
        sb.append(basePrompt)

        // Condensed skills: just the names so the agent knows what's active
        if (loadedSkills.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[SKILLS ACTIVE: ${loadedSkills.joinToString(", ") { it.displayName }}]")
        }

        // ⚠️ Composio toolkit docs — fires even in condensed mode
        sb.appendLine()
        sb.appendLine("[COMPOSIO] Before ANY composio_execute_tool call: FIRST call composio_get_toolkit_docs(toolkit_slug=\"<name>\") to get exact tool names. NEVER guess.")

        return sb.toString()
    }

    /**
     * Estimate token count for the brain portion of the prompt.
     * Rough estimate: ~4 chars per token.
     */
    fun estimateBrainTokenCount(
        brainState: TwBrainState,
        persona: TwPersona? = null
    ): Int {
        var count = 0

        if (persona != null) {
            count += persona.systemPromptAddition.length
        }

        count += brainState.buildMemoryInjection("").length

        for (note in brainState.midSessionNotes) {
            count += note.content.length
        }

        return count / 4
    }

    // ─── SKILL HELPERS ────────────────────────────────────────────────────────

    /**
     * Strip YAML frontmatter from skill content.
     * hermes-agent skills have `---` delimited frontmatter at the top.
     * We strip it so the injected content only contains the actual instructions.
     */
    private fun stripFrontmatter(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content
        if (!lines[0].trimStart().startsWith("---")) return content
        val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        return if (endIndex >= 0) {
            lines.drop(endIndex + 2).joinToString("\n").trim()
        } else content
    }

    /**
     * Quick category parse for skill catalog — reads only the first 15 lines.
     * No full content read = fast.
     */
    fun parseSkillCategoryQuick(skillFilePath: String): String {
        return try {
            val lines = File(skillFilePath).bufferedReader().use { it.readLines() }.take(15)
            if (lines.isNotEmpty() && lines[0].trimStart().startsWith("---")) {
                val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
                if (endIndex >= 0) {
                    lines.subList(1, endIndex + 1).forEach { raw ->
                        val line = raw.trim()
                        val colon = line.indexOf(':')
                        if (colon > 0 && line.substring(0, colon).trim().lowercase() == "category") {
                            return line.substring(colon + 1).trim().removeSurrounding("\"", "'")
                        }
                    }
                }
            }
            "general"
        } catch (_: Exception) {
            "general"
        }
    }
}
