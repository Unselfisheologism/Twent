package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Twent AI Agent Brain — Main entry point for the AI Chat page.
 *
 * This is the brain that makes the AI Chat page agent as smart as hermes-agent.
 * Only modifies the AI Agent in the AI Chat page — NOT the overlay or executor agents.
 *
 * HOW IT WIRING INTO EnhancedAIService:
 * ─────────────────────────────────────
 * EnhancedAIService.sendMessage() already has the full tool-calling loop internally.
 * We add the brain in 2 ways:
 *
 * 1. PROMPT INJECTION: Before calling sendMessage(), the ChatViewModel calls
 *    brain.buildEnhancedPrompt() to inject memory/persona/mid-session notes into
 *    the system prompt. The base prompt from SystemPromptConfig is enhanced.
 *
 * 2. TOOL INTERCEPTION: During the tool-calling loop (inside sendMessage),
 *    brain tools (tw_remember, tw_recall, etc.) are intercepted by
 *    TwConversationLoop.handleBrainTool() and handled locally.
 *
 * WIRING DIAGRAM:
 * ───────────────
 * ChatViewModel
 *   ↓
 * TwAgentChatBrain.buildEnhancedSystemPrompt()  ← inject brain into system prompt
 *   ↓
 * EnhancedAIService.sendMessage()               ← normal flow, enhanced prompt
 *   ↓ (inside the tool loop)
 * TwConversationLoop.handleBrainTool()         ← intercept brain tool calls
 *   ↓
 * EnhancedAIService continues normally          ← non-brain tools pass through
 *   ↓
 * TwAgentChatBrain.onConversationEnd()         ← save insights, auto-learn
 *
 * DOES NOT USE HERMES-AGENT AS A BRIDGE:
 * ──────────────────────────────────────
 * This is a native implementation of hermes-agent's brain patterns,
 * adapted for Android/Kotlin. No hermes-agent process is running.
 * We replicate the CONCEPTS (memory, personas, insights, branching)
 * without depending on hermes-agent itself.
 *
 * All 3 questions answered in code:
 * Q1: Should ALL 3 agents get the brain? → NO. Only AI Chat page.
 *     This class is scoped to the AI Chat page's brain. Overlay agents
 *     use the existing (dumb) system prompt.
 *
 * Q2: How to bring hermes-agent's brain WITHOUT hermes-agent as backend?
 *     → Implement the patterns natively in Kotlin. No bridge needed.
 *     → MEMORY.md = TwMemoryManager (file-based persistence)
 *     → USER.md   = TwUserProfile (TwMemoryManager)
 *     → SOUL.md   = TwPersona (TwMemoryManager)
 *     → /insights = TwBrainInsights (TwMemoryManager)
 *     → /branch   = TwSessionBranch (TwBrainState)
 *     → /btw      = TwMidSessionNote (TwBrainState)
 *     → /yolo     = TwAgentMode (TwBrainState)
 *
 * Q3: MCVP = Phase 1: MEMORY.md + USER.md + SOUL.md + tw_remember/recall
 *     → This file + TwBrainModels + TwMemoryManager + TwPromptBuilder
 */
class TwAgentChatBrain private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TwAgentChatBrain"

        @Volatile private var INSTANCE: TwAgentChatBrain? = null

        // Per-chat brain instances (one per chat conversation)
        private val chatBrains = mutableMapOf<String, TwAgentChatBrain>()

        fun getInstance(context: Context): TwAgentChatBrain {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwAgentChatBrain(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Get or create a brain instance for a specific chat.
         * Each AI Chat conversation gets its own brain state.
         */
        fun getForChat(context: Context, chatId: String): TwAgentChatBrain {
            return chatBrains[chatId] ?: synchronized(chatBrains) {
                chatBrains[chatId] ?: TwAgentChatBrain(context.applicationContext).also {
                    chatBrains[chatId] = it
                }
            }
        }

        /**
         * Release a chat brain when the chat is closed.
         */
        fun releaseChatBrain(chatId: String) {
            chatBrains.remove(chatId)
            AppLogger.d(TAG, "Released brain for chat: $chatId")
        }
    }

    private val memoryManager = TwMemoryManager.getInstance(context)
    private val promptBuilder = TwPromptBuilder.getInstance(context)
    private val conversationLoop = TwConversationLoop.getInstance(context)
    private val skillsManager = TwSkillsManager
    private val packageManager: PackageManager by lazy { PackageManager.getInstance(context) }

    // Brain state for this chat
    private val _brainState = MutableStateFlow<TwBrainState?>(null)
    val brainState: StateFlow<TwBrainState?> = _brainState.asStateFlow()

    private var isInitialized = false

    // ─── INIT ─────────────────────────────────────────────────────────────

    /**
     * Initialize brain for a chat. Call once when the AI Chat page opens.
     * Loads MEMORY.md, USER.md, and active persona from disk.
     */
    suspend fun initialize(chatId: String) {
        if (isInitialized) return

        AppLogger.d(TAG, "Initializing brain for chat: $chatId")

        // Initialize skills manager (reads skills from Downloads/Twent/skills/)
        skillsManager.initialize(context)

        val memory = memoryManager.loadMemory()
        val userProfile = memoryManager.loadUserProfile()
        val personaId = memory.personaId
        val persona = if (personaId != null) {
            memoryManager.loadPersona(personaId) ?: memoryManager.getDefaultPersona()
        } else {
            memoryManager.getDefaultPersona()
        }

        _brainState.value = TwBrainState(
            chatId = chatId,
            memory = memory,
            userProfile = userProfile
        )

        isInitialized = true
        AppLogger.d(TAG, "Brain initialized: ${memory.memories.size} memories, persona=${persona.name}")
    }

    // ─── PROMPT BUILDING ─────────────────────────────────────────────────

    /**
     * Build the brain-enhanced system prompt.
     * Called by ChatViewModel BEFORE passing the prompt to EnhancedAIService.sendMessage().
     *
     * @param userQuery The user's current message (used for memory relevance)
     * @param basePrompt The existing system prompt from SystemPromptConfig
     * @return Enhanced prompt with brain injection
     */
    suspend fun buildEnhancedSystemPrompt(
        userQuery: String,
        basePrompt: String? = null
    ): String {
        val state = _brainState.value ?: return basePrompt ?: ""

        val effectiveBase = basePrompt ?: SystemPromptConfig.getSystemPrompt(packageManager)

        // Load persona if not already set
        val personaId = state.memory.personaId
        val persona = if (personaId != null) {
            memoryManager.loadPersona(personaId)
        } else null

        return promptBuilder.buildEnhancedPrompt(
            basePrompt = effectiveBase,
            brainState = state,
            userQuery = userQuery,
            persona = persona,
            loadedSkills = state.loadedSkills.toList()
        )
    }

    /**
     * Build condensed prompt for tight context windows.
     */
    fun buildCondensedPrompt(basePrompt: String? = null): String {
        val state = _brainState.value ?: return basePrompt ?: ""

        val effectiveBase = basePrompt ?: SystemPromptConfig.getSystemPrompt(packageManager)
        return promptBuilder.buildCondensedPrompt(
            effectiveBase,
            state,
            persona = null,
            loadedSkills = state.loadedSkills.toList()
        )
    }

    /**
     * Process a slash command — e.g. /android-development.
     * Called by ChatViewModel when user types a /command.
     *
     * hermes-agent equivalent: `skillsManager.loadSkill(skillId)`
     * The skill stays active for the entire session (TwBrainState.loadedSkills).
     *
     * @param command The full slash command including the slash (e.g. "/android-development")
     * @param chatId  The chat ID to scope the skill load
     * @return SlashCommandResult describing what happened
     */
    fun processSlashCommand(command: String, chatId: String): TwSlashResult {
        val state = _brainState.value
        if (state == null) {
            AppLogger.w(TAG, "processSlashCommand called but brain not initialized")
            return TwSlashResult(
                wasSlashCommand = false,
                displayMessage = "Brain not initialized. Please restart the chat."
            )
        }

        val trimmed = command.trim()
        if (!trimmed.startsWith("/")) {
            return TwSlashResult(wasSlashCommand = false)
        }

        val parts = trimmed.substringAfter("/").split(Regex("\\s+"))
        val skillName = parts[0].lowercase()
        val args = parts.drop(1)

        AppLogger.d(TAG, "Processing slash command: /$skillName with args: $args")

        // hermes-agent's built-in commands (not skills)
        when (skillName) {
            "skills", "skill" -> {
                val catalog = getSkillsCatalog()
                return TwSlashResult(
                    wasSlashCommand = true,
                    displayMessage = buildSlashCommandHelp(catalog)
                )
            }
            "unload", "unload-skill" -> {
                if (args.isEmpty()) {
                    return TwSlashResult(
                        wasSlashCommand = true,
                        displayMessage = "Usage: /unload <skill-name>\nLoaded: ${state.loadedSkills.joinToString { it.displayName }}"
                    )
                }
                val toUnload = args.joinToString("-").lowercase()
                val removed = state.loadedSkills.removeAll {
                    it.id.lowercase() == toUnload || it.displayName.lowercase() == toUnload
                }
                return TwSlashResult(
                    wasSlashCommand = true,
                    displayMessage = if (removed) "Skill unloaded." else "Skill not found: $toUnload"
                )
            }
            "loaded", "active-skills" -> {
                if (state.loadedSkills.isEmpty()) {
                    return TwSlashResult(
                        wasSlashCommand = true,
                        displayMessage = "No skills loaded. Type /skills to see available skills."
                    )
                }
                return TwSlashResult(
                    wasSlashCommand = true,
                    displayMessage = "Active skills:\n${state.loadedSkills.joinToString("\n") { "• ${it.displayName} (${it.category})" }}"
                )
            }
            "help", "?" -> {
                return TwSlashResult(
                    wasSlashCommand = true,
                    displayMessage = """
                        |Twent Skills (/slash-commands):
                        |
                        |Skill Management:
                        |  /skills         — List all available skills
                        |  /<skill-name>   — Load a skill (e.g. /android-development)
                        |  /loaded         — Show active skills
                        |  /unload <name>  — Unload a skill
                        |
                        |Brain Controls:
                        |  /remember <title>|<content> — Save a memory
                        |  /recall <query>              — Search memories
                        |  /insights                      — View session analytics
                        |  /snapshot [name]              — Save conversation state
                        |  /rollback <id>                — Restore a snapshot
                        |  /persona [name]               — Switch persona
                        |  /btw <note>                   — Ephemeral side note
                        |  /yolo [on|off]                — Skip danger confirmations
                        |  /fast [on|off]                — Fast/cheap model mode
                        |  /reasoning [on|off]           — Deep reasoning mode
                    """.trimMargin()
                )
            }
        }

        // Try to load as a skill
        val catalog = getSkillsCatalog()
        val match = catalog.find {
            it.name.lowercase() == skillName ||
            it.aliases.any { a -> a.lowercase() == skillName }
        }

        return if (match != null) {
            val loaded = skillsManager.loadSkill(match.name)
            if (loaded != null) {
                state.loadedSkills.add(loaded)
                AppLogger.d(TAG, "Loaded skill: ${loaded.displayName}")
                TwSlashResult(
                    wasSlashCommand = true,
                    skillLoaded = loaded.id,
                    displayMessage = buildSkillLoadedMessage(loaded)
                )
            } else {
                TwSlashResult(
                    wasSlashCommand = true,
                    displayMessage = "Failed to load skill: ${match.name}"
                )
            }
        } else {
            TwSlashResult(
                wasSlashCommand = false,
                displayMessage = "Unknown command: $command\nType /skills to see available skills."
            )
        }
    }

    private fun buildSlashCommandHelp(catalog: List<TwSkillInfo>): String {
        val grouped = catalog.groupBy { it.category }
        val sb = StringBuilder()
        sb.appendLine("Available Skills (${catalog.size} total):")
        sb.appendLine()
        grouped.forEach { (cat, skills) ->
            sb.appendLine("── $cat ──")
            skills.forEach { s ->
                val aliases = if (s.aliases.isNotEmpty()) " (${s.aliases.joinToString(", ")})" else ""
                sb.appendLine("  /${s.name}$aliases")
                sb.appendLine("    ${s.description}")
            }
            sb.appendLine()
        }
        sb.appendLine("Usage: /<skill-name> to load. Loaded skills stay active for the session.")
        return sb.toString().trimEnd()
    }

    private fun buildSkillLoadedMessage(loaded: TwLoadedSkill): String {
        val keywords = if (loaded.triggerKeywords.isNotEmpty()) {
            "\nAuto-triggers on: ${loaded.triggerKeywords.joinToString(", ")}"
        } else ""
        return """
            |✓ Skill loaded: ${loaded.displayName}
            |${loaded.description}$keywords
            |
            |This skill is now active for your entire session.
            |Type /unload ${loaded.id} to remove it.
        """.trimMargin()
    }

    // ─── SKILL CATALOG (no IO — in-memory from TwSkillsManager) ────────────

    /**
     * Get the in-memory skill catalog. Fast, no disk IO.
     * hermes-agent equivalent: skill directory scan at boot.
     */
    fun getSkillsCatalog(): List<TwSkillInfo> {
        return skillsManager.getSkillsCatalog()
    }

    /**
     * Get the current set of loaded skills for this session.
     */
    fun getLoadedSkills(): List<TwLoadedSkill> {
        return _brainState.value?.loadedSkills?.toList() ?: emptyList()
    }

    // ─── PERSONA MANAGEMENT ────────────────────────────────────────────────

    /**
     * hermes-agent's /personality equivalent.
     * Lists or switches active persona.
     */
    fun getPersonaList(): List<TwPersona> {
        return memoryManager.listPersonas()
    }

    /**
     * hermes-agent's /personality <name> equivalent.
     */
    fun switchPersona(personaId: String): Boolean {
        val state = _brainState.value ?: return false
        val personas = memoryManager.listPersonas()
        val found = personas.find { it.id == personaId } ?: return false
        state.memory = state.memory.copy(personaId = found.id)
        return true
    }

    // ─── TOOL HANDLING ────────────────────────────────────────────────────

    /**
     * Check if a tool is a brain tool.
     * Used by ChatViewModel to decide whether to intercept.
     */
    fun isBrainTool(toolName: String): Boolean {
        return conversationLoop.isBrainTool(toolName)
    }

    /**
     * Handle a brain tool call.
     * Returns ToolResultData for brain tools, null for normal tools.
     * Called by ChatViewModel/EnhancedAIService during the tool loop.
     */
    suspend fun handleBrainTool(
        toolName: String,
        parameters: Map<String, String>
    ): ToolResult? {
        val state = _brainState.value ?: return null
        return conversationLoop.handleBrainTool(toolName, parameters, state)
    }

    /**
     * Create a brain tool executor for EnhancedAIService.
     * The executor wraps handleBrainTool as a ToolExecutor-compatible object.
     * Uses runBlocking since ToolExecutor.invoke() is not suspend.
     */
    fun createToolExecutor(): ToolExecutor = object : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            val state = _brainState.value ?: return ToolResult(
                toolName = tool.name, success = false,
                result = StringResultData(""), error = "Brain not initialized"
            )
            // Extract name and params from AITool
            val toolName = tool.name
            val params = tool.parameters.associate { it.name to (it.value ?: "") }
            return runBlocking {
                conversationLoop.handleBrainTool(toolName, params, state)
                    ?: ToolResult(
                        toolName = tool.name, success = false,
                        result = StringResultData(""), error = "Unknown brain tool: $toolName"
                    )
            }
        }
    }
    }

    /**
     * Track a tool call for insights.
     * Called after EVERY tool execution (brain or normal).
     */
    suspend fun trackToolCall(toolName: String) {
        val state = _brainState.value ?: return
        conversationLoop.afterToolCall(toolName, state)
    }

    /**
     * Check if iteration budget is exhausted.
     * Called during the tool loop to prevent infinite loops.
     */
    fun isIterationBudgetExhausted(): Boolean {
        val state = _brainState.value ?: return false
        return !state.consumeIteration()
    }

    // ─── PUBLIC TOOL REGISTRATION ─────────────────────────────────────────
    // Registers all brain tools with the AIToolHandler.
    // This makes them available to the AI just like any other tool.
    // Call this ONCE when the AI Chat page loads.
    //
    // INTEGRATION: Call from ChatViewModel.init() or wherever you init the brain.
    /**
     * Register all brain tools with the AIToolHandler.
     * This makes them appear in the system prompt and be executable by the AI.
     *
     * Call this once when initializing the AI Chat page brain.
     *
     * @param handler The AIToolHandler from the ChatViewModel (call: AIToolHandler.getInstance(context))
     */
    fun registerBrainTools(handler: com.ai.assistance.operit.core.tools.AIToolHandler) {
        val executor: com.ai.assistance.operit.core.tools.ToolExecutor = { tool ->
            val toolName = tool.name
            val parameters = tool.parameters.associate { p -> p.name to (p.value ?: "") }
            // executeSync is called on IO dispatcher by ToolExecutionManager
            // We need to run this synchronously from the caller's context
            // Since ToolExecutor is called from a coroutine scope, we use runBlocking as a bridge
            // The alternative is to use GlobalScope but that's worse
            var result: com.ai.assistance.operit.data.model.ToolResult? = null
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                result = conversationLoop.handleBrainTool(toolName, parameters, _brainState.value ?: return@runBlocking)
            }
            result ?: com.ai.assistance.operit.data.model.ToolResult(
                toolName = toolName,
                success = false,
                result = com.ai.assistance.operit.core.tools.StringResultData(""),
                error = "Brain not initialized"
            )
        }

        // Register all 15 brain tools
        val brainToolNames = listOf(
            "tw_remember", "tw_recall", "tw_forget", "tw_insights",
            "tw_snapshot", "tw_rollback", "tw_branch", "tw_persona",
            "tw_btw", "tw_steer", "tw_queue", "tw_yolo",
            "tw_fast", "tw_reasoning", "tw_forget_user", "tw_learn_user"
        )
        brainToolNames.forEach { name ->
            handler.registerTool(
                name = name,
                dangerCheck = { false }, // Brain tools are safe
                descriptionGenerator = { getBrainToolDescription(name) },
                executor = executor
            )
        }
        com.ai.assistance.operit.util.AppLogger.d(TAG, "Registered ${brainToolNames.size} brain tools")
    }

    private fun getBrainToolDescription(name: String): String {
        return when (name) {
            "tw_remember" -> "Save a memory for the AI to recall in future sessions. Parameters: title (str), content (str), category (str, optional), importance (float, optional 0-1)."
            "tw_recall" -> "Search and recall memories from previous sessions. Parameters: query (str) - what to search for."
            "tw_forget" -> "Delete a memory. Parameters: title (str) or id (str) of the memory to forget."
            "tw_insights" -> "View cross-session analytics: tokens used, tools called, stalled sessions, top tools."
            "tw_snapshot" -> "Save a snapshot of the current conversation state. Parameters: name (str, optional)."
            "tw_rollback" -> "Rollback to a previous snapshot. Parameters: id (str) - the snapshot ID."
            "tw_branch" -> "Create a conversation branch to try different approaches without losing context."
            "tw_persona" -> "Manage AI personas. Actions: list (default), switch. Parameters: action (str), id (str)."
            "tw_btw" -> "Add an ephemeral side note for this session only. Parameters: note (str) - the note content."
            "tw_steer" -> "Set a mid-flight steering directive for the next tool call. Parameters: directive (str)."
            "tw_queue" -> "Queue the next turn without interrupting current flow. Parameters: turn (str)."
            "tw_yolo" -> "Toggle YOLO mode - skip dangerous command approvals. Parameters: enable (bool, optional)."
            "tw_fast" -> "Toggle fast mode - prioritize speed. Parameters: enable (bool, optional)."
            "tw_reasoning" -> "Toggle high-effort reasoning for complex problems. Parameters: enable (bool, optional)."
            "tw_forget_user" -> "Remove user knowledge. Parameters: key (str) - the fact to remove."
            "tw_learn_user" -> "Learn a user preference. Parameters: fact (str), category (str, optional)."
            else -> "Unknown brain tool: $name"
        }
    }
}
