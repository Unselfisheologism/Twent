package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * Twent Brain Conversation Loop.
 * Wraps the AI agent's tool execution loop and intercepts brain-specific commands.
 *
 * hermes-agent's conversation loop equivalent:
 * - Tool interception (tw_remember, tw_recall, tw_forget, etc.)
 * - Memory auto-save after conversations
 * - Iteration budget enforcement
 * - Mid-session note injection
 * - Session branching support
 *
 * HOW IT INTEGRATES WITH EnhancedAIService:
 * EnhancedAIService.sendMessage() already has the full tool-calling loop internally.
 * We don't replace that loop — we INTERCEPT brain-specific tool calls and handle them
 * ourselves, returning ToolResultData for those tools, so EnhancedAIService can
 * continue normally for all other tools.
 *
 * Architecture:
 *   User input
 *       ↓
 *   TwAgentChatBrain.interceptOrPass()  ← called by ChatViewModel
 *       ↓
 *   Is it a brain tool? ─YES→ TwConversationLoop.handleBrainTool()
 *       ↓ NO
 *   EnhancedAIService.sendMessage() ← normal path, brain state updated afterwards
 *       ↓
 *   TwConversationLoop.afterToolCall() ← track tool calls, check budget
 *       ↓
 *   Update TwBrainState, save memory
 */
class TwConversationLoop(private val context: Context) {

    companion object {
        private const val TAG = "TwConversationLoop"

        @Volatile private var INSTANCE: TwConversationLoop? = null

        fun getInstance(context: Context): TwConversationLoop {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwConversationLoop(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Brain tool names that this loop intercepts
        val BRAIN_TOOL_NAMES = setOf(
            "tw_remember",
            "tw_recall",
            "tw_forget",
            "tw_insights",
            "tw_snapshot",
            "tw_rollback",
            "tw_branch",
            "tw_persona",
            "tw_btw",
            "tw_steer",
            "tw_queue",
            "tw_yolo",
            "tw_fast",
            "tw_reasoning",
            "tw_forget_user",
            "tw_learn_user"
        )

        // Default iteration budget
        const val DEFAULT_ITERATION_BUDGET = 20

        /**
         * Register all brain tools with an AIToolHandler.
         * Called reflectively from ToolRegistration to avoid compile-time coupling.
         */
        @JvmStatic
        fun registerBrainTools(ctx: Context, handler: AIToolHandler) {
            val instance = getInstance(ctx)
            val toolNames = BRAIN_TOOL_NAMES
            val descriptionMap = mapOf(
                "tw_remember" to "Save a memory: title, content, optional category/importance/tags. Memories persist across sessions.",
                "tw_recall" to "Search memories: query parameter. Returns relevant memories with access count and age.",
                "tw_forget" to "Delete a memory by title or id.",
                "tw_insights" to "Show conversation insights: memory count, topic frequency, key facts learned.",
                "tw_snapshot" to "Save mid-session state: title, optional description. Useful to bookmark a moment.",
                "tw_rollback" to "Restore a previous snapshot by id.",
                "tw_branch" to "Branch conversation: new topic/tone while keeping current context.",
                "tw_persona" to "Switch between personas: list, switch, or create personas.",
                "tw_btw" to "Add a side note to the conversation that gets revealed at the next turn.",
                "tw_steer" to "Change topic direction mid-conversation.",
                "tw_queue" to "Queue user messages for later: queue items to be sent when user is ready.",
                "tw_yolo" to "Toggle YOLO mode: agent acts without tool calls or excessive explanation.",
                "tw_fast" to "Toggle fast mode: minimal reasoning, direct responses.",
                "tw_reasoning" to "Toggle deep reasoning mode: extensive chain-of-thought before answering.",
                "tw_forget_user" to "Remove all knowledge about the user.",
                "tw_learn_user" to "Store information about the user."
            )

            for (toolName in toolNames) {
                handler.registerTool(
                    name = toolName,
                    dangerCheck = { false },
                    descriptionGenerator = { descriptionMap[toolName] ?: "Brain tool: $toolName" },
                    executor = { tool ->
                        val params = tool.parameters.associate { it.name to (it.value ?: "").toString() }
                        runBlocking {
                            // Use a temporary TwBrainState for tool registration context (chatId is required)
                            val tempState = TwBrainState(chatId = "tool_registration")
                            instance.handleBrainToolSync(toolName, params, tempState)
                        }
                    }
                )
            }
        }
    }

    private val memoryManager = TwMemoryManager.getInstance(context)
    private val promptBuilder = TwPromptBuilder.getInstance(context)

    /**
     * Synchronous wrapper for handleBrainTool used during tool registration.
     * Creates a blocking bridge for the coroutine-based handler.
     */
    internal fun handleBrainToolSync(
        toolName: String,
        parameters: Map<String, String>,
        state: TwBrainState
    ): ToolResult {
        return runBlocking {
            handleBrainTool(toolName, parameters, state) ?: ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Not a brain tool"
            )
        }
    }

    /**
     * Check if a tool call is a brain tool that should be intercepted.
     */
    fun isBrainTool(toolName: String): Boolean {
        return toolName in BRAIN_TOOL_NAMES ||
               toolName.startsWith("tw_")
    }

    /**
     * Handle a brain tool call and return the result.
     * Returns null if the tool is NOT a brain tool (caller should handle normally).
     *
     * hermes-agent's brain tool handlers equivalent.
     */
    internal suspend fun handleBrainTool(
        toolName: String,
        parameters: Map<String, String>,
        state: TwBrainState
    ): ToolResult? {
        if (!isBrainTool(toolName)) return null

        AppLogger.d(TAG, "Handling brain tool: $toolName with params: $parameters")

        return withContext(Dispatchers.IO) {
            try {
                val resultText = when (toolName) {
                    "tw_remember" -> handleRemember(parameters, state)
                    "tw_recall" -> handleRecall(parameters, state)
                    "tw_forget" -> handleForget(parameters, state)
                    "tw_insights" -> handleInsights(parameters, state)
                    "tw_snapshot" -> handleSnapshot(parameters, state)
                    "tw_rollback" -> handleRollback(parameters, state)
                    "tw_branch" -> handleBranch(parameters, state)
                    "tw_persona" -> handlePersona(parameters, state)
                    "tw_btw" -> handleBtw(parameters, state)
                    "tw_steer" -> handleSteer(parameters, state)
                    "tw_queue" -> handleQueue(parameters, state)
                    "tw_yolo" -> handleYolo(parameters, state)
                    "tw_fast" -> handleFast(parameters, state)
                    "tw_reasoning" -> handleReasoning(parameters, state)
                    "tw_forget_user" -> handleForgetUser(parameters, state)
                    "tw_learn_user" -> handleLearnUser(parameters, state)
                    else -> "Error: Unknown brain tool: $toolName"
                }
                ToolResult(
                    toolName = toolName,
                    success = !resultText.startsWith("Error:"),
                    result = StringResultData(resultText),
                    error = if (resultText.startsWith("Error:")) resultText else null
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling brain tool $toolName", e)
                ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "Brain tool error: ${e.message}"
                )
            }
        }
    }

    // ─── TOOL HANDLERS ─────────────────────────────────────────────────────

    private suspend fun handleRemember(params: Map<String, String>, state: TwBrainState): String {
        val title = params["title"] ?: return "Error: title parameter required"
        val content = params["content"] ?: return "Error: content parameter required"
        val category = params["category"] ?: "general"
        val importance = params["importance"]?.toFloatOrNull() ?: 0.5f
        val tags = params["tags"]?.split(",")?.map { it.trim() } ?: emptyList()

        val entry = memoryManager.addMemory(
            title = title,
            content = content,
            category = category,
            importance = importance,
            source = "agent_memory",
            tags = tags
        )

        state.memory.memories.add(entry)

        return "✅ Memory saved!\n\n" +
            "**${entry.title}**\n" +
            "Category: ${entry.category} | Importance: ${entry.importance}\n" +
            "Content: ${entry.content.take(100)}${if (entry.content.length > 100) "..." else ""}\n\n" +
            "Memory ID: ${entry.id}\n" +
            "This will be available in all future sessions."
    }

    private suspend fun handleRecall(params: Map<String, String>, state: TwBrainState): String {
        val query = params["query"]
            ?: params["search"]
            ?: return "Error: query or search parameter required"

        val results = memoryManager.searchMemories(query)

        if (results.isEmpty()) {
            return "🔍 No memories found for: \"$query\"\n\n" +
                "No previous memories match this query. You haven't discussed this topic before."
        }

        results.forEach { state.touchMemory(it.id) }

        val summary = results.joinToString("\n\n") { entry ->
            val age = ageString(entry.createdAt)
            "[${entry.category}] **${entry.title}** ($age, ${entry.accessCount}x accessed)\n" +
            "${entry.content.take(200)}${if (entry.content.length > 200) "..." else ""}"
        }

        return "🔍 Found ${results.size} memory/ies for: \"$query\"\n\n" +
            summary + "\n\n" +
            "Use `tw_remember` to add new memories. Use `tw_forget <title>` to remove them."
    }

    private suspend fun handleForget(params: Map<String, String>, state: TwBrainState): String {
        val title = params["title"]
            ?: params["id"]
            ?: return "Error: title or id parameter required"

        val removed = if (params["id"] != null) {
            memoryManager.deleteMemory(title)
        } else {
            state.memory.memories.removeAll { it.title.equals(title, ignoreCase = true) }.also {
                memoryManager.saveMemory(state.memory)
            }
        }

        return if (removed) {
            "🗑️ Forgotten: \"$title\"\n\nThis memory has been removed from persistent storage."
        } else {
            "⚠️ Memory not found: \"$title\"\n\nNo matching memory to forget."
        }
    }

    private suspend fun handleInsights(params: Map<String, String>, state: TwBrainState): String {
        val insights = state.memory.insights

        val sb = StringBuilder()
        sb.appendLine("📊 Brain Insights")
        sb.appendLine()
        sb.appendLine("**Cross-Session Analytics**")
        sb.appendLine()
        sb.appendLine("- Total sessions: ${insights.totalSessions}")
        sb.appendLine("- Total tool calls: ${insights.totalToolCalls}")
        sb.appendLine("- Total tokens used: ${String.format("%,d", insights.totalTokens)}")
        sb.appendLine("- Stalled sessions: ${insights.stalledCount}")
        sb.appendLine("- Successful tasks: ${insights.successfulTasks}")
        sb.appendLine()

        if (insights.toolCallCounts.isNotEmpty()) {
            sb.appendLine("**Top Tools Used**")
            sb.appendLine()
            val topTools = insights.toolCallCounts.entries
                .sortedByDescending { it.value }
                .take(10)
            topTools.forEach { (tool, count) ->
                val bar = "█".repeat((count.toFloat() / (topTools.maxOfOrNull { it.value } ?: 1) * 20).toInt().coerceAtLeast(1))
                sb.appendLine("  $tool: $count $bar")
            }
            sb.appendLine()
        }

        if (insights.providerUsage.isNotEmpty()) {
            sb.appendLine("**Provider Usage**")
            sb.appendLine()
            insights.providerUsage.entries
                .sortedByDescending { it.value }
                .take(5)
                .forEach { (provider, count) ->
                    sb.appendLine("  $provider: $count uses")
                }
            sb.appendLine()
        }

        sb.appendLine("*Insights are aggregated across all sessions.*")
        return sb.toString()
    }

    private suspend fun handleSnapshot(params: Map<String, String>, state: TwBrainState): String {
        val name = params["name"] ?: "snapshot-${Date().time}"

        val snapshotId = UUID.randomUUID().toString().take(8)
        val branch = TwSessionBranch(
            id = snapshotId,
            name = name,
            conversationSnapshot = emptyList(),
            memorySnapshot = state.memory.copy(memories = state.memory.memories.toMutableList())
        )

        state.activeBranches.add(branch)

        return "📸 Snapshot created!\n\n" +
            "**Name:** $name\n" +
            "**ID:** $snapshotId\n\n" +
            "This conversation state is saved. Use `tw_rollback $snapshotId` to return to this point.\n" +
            "You now have ${state.activeBranches.size} saved snapshots."
    }

    private suspend fun handleRollback(params: Map<String, String>, state: TwBrainState): String {
        val snapshotId = params["id"]
            ?: params["snapshot_id"]
            ?: return "Error: id parameter required"

        val branch = state.activeBranches.find { it.id == snapshotId }
        if (branch == null) {
            return "⚠️ Snapshot not found: $snapshotId\n\n" +
                "Available snapshots: ${state.activeBranches.map { "${it.id} (${it.name})" }.joinToString(", ")}"
        }

        state.memory = branch.memorySnapshot.copy(memories = branch.memorySnapshot.memories.toMutableList())
        memoryManager.saveMemory(state.memory)

        return "⏪ Rolled back to snapshot: **${branch.name}** ($snapshotId)\n\n" +
            "Memory restored with ${branch.memorySnapshot.memories.size} entries.\n" +
            "Note: The conversation history in the UI needs to be updated separately."
    }

    private suspend fun handleBranch(params: Map<String, String>, state: TwBrainState): String {
        val name = params["name"]
            ?: "branch-${state.activeBranches.size + 1}"

        val branch = state.createBranch(name, emptyList())

        return "🔀 Branch created: **${branch.name}**\n\n" +
            "**Branch ID:** ${branch.id}\n" +
            "You now have ${state.activeBranches.size} branches.\n\n" +
            "Try a different approach in this branch without losing the original conversation.\n" +
            "Use `tw_rollback ${branch.id}` to return to this branch point."
    }

    private suspend fun handlePersona(params: Map<String, String>, state: TwBrainState): String {
        val action = params["action"] ?: params.getOrDefault("list", "")
        val personaId = params["id"]

        return when (action.lowercase()) {
            "list", "" -> {
                val personas = memoryManager.listPersonas()
                if (personas.isEmpty()) {
                    "No personas configured. The default assistant persona is active."
                } else {
                    val list = personas.joinToString("\n") { p ->
                        val active = if (p.id == state.memory.personaId) " ← ACTIVE" else ""
                        "- **${p.name}** (${p.id}): ${p.description}$active"
                    }
                    "🎭 Available Personas:\n\n$list\n\nUse `tw_persona action=switch id=<id>` to switch."
                }
            }
            "switch" -> {
                if (personaId == null) {
                    "Error: id parameter required for switch action"
                } else {
                    val persona = memoryManager.loadPersona(personaId)
                    if (persona != null) {
                        state.memory = state.memory.copy(personaId = personaId)
                        memoryManager.saveMemory(state.memory)
                        "✅ Persona switched to: **${persona.name}**\n\n" +
                            "${persona.description}\n\n" +
                            "New persona will be active from the next message."
                    } else {
                        "Error: Persona not found: $personaId"
                    }
                }
            }
            else -> "Error: Unknown persona action: $action. Use list, switch."
        }
    }

    private suspend fun handleBtw(params: Map<String, String>, state: TwBrainState): String {
        val note = params["note"]
            ?: params["text"]
            ?: params["content"]
            ?: return "Error: note parameter required"

        state.midSessionNotes.add(TwMidSessionNote(
            content = note,
            type = NoteType.EPHEMERAL_BTW
        ))

        return "💡 Note saved for this session.\n\n" +
            "\"$note\"\n\n" +
            "This will be visible to the agent for the rest of this session. Not saved to disk."
    }

    private suspend fun handleSteer(params: Map<String, String>, state: TwBrainState): String {
        val directive = params["directive"]
            ?: params["steer"]
            ?: params["content"]
            ?: params["text"]
            ?: return "Error: directive parameter required"

        state.midSessionNotes.add(TwMidSessionNote(
            content = directive,
            type = NoteType.STEER_DIRECTIVE
        ))

        return "🧭 Steering directive set for this session.\n\n" +
            "→ $directive\n\n" +
            "The agent will take this into account for the next tool call. " +
            "Not persisted across sessions."
    }

    private suspend fun handleQueue(params: Map<String, String>, state: TwBrainState): String {
        val turn = params["turn"]
            ?: params["next"]
            ?: params["content"]
            ?: return "Error: turn parameter required"

        state.midSessionNotes.add(TwMidSessionNote(
            content = "QUEUED: $turn",
            type = NoteType.QUEUED_TURN
        ))

        return "➡️ Next turn queued.\n\n" +
            "The queued instruction will be executed as the next user message."
    }

    private suspend fun handleYolo(params: Map<String, String>, state: TwBrainState): String {
        val enable = params["enable"]?.toBoolean() ?: !state.mode.yoloMode
        state.mode = state.mode.copy(yoloMode = enable)

        return if (enable) {
            "⚠️ YOLO MODE ENABLED\n\n" +
                "Dangerous command approvals are SKIPPED. The agent will proceed without asking.\n" +
                "Use with caution."
        } else {
            "✓ YOLO MODE DISABLED\n\n" +
                "Dangerous command approvals are back to normal."
        }
    }

    private suspend fun handleFast(params: Map<String, String>, state: TwBrainState): String {
        val enable = params["enable"]?.toBoolean() ?: !state.mode.fastMode
        state.mode = state.mode.copy(fastMode = enable)

        return if (enable) {
            "⚡ FAST MODE ENABLED\n\n" +
                "The agent will prioritize speed and use faster models where possible."
        } else {
            "✓ FAST MODE DISABLED\n\n" +
                "Normal model selection resumed."
        }
    }

    private suspend fun handleReasoning(params: Map<String, String>, state: TwBrainState): String {
        val enable = params["enable"]?.toBoolean() ?: !state.mode.highEffortReasoning
        state.mode = state.mode.copy(highEffortReasoning = enable)

        return if (enable) {
            "🧠 HIGH-EFFORT REASONING ENABLED\n\n" +
                "The agent will spend maximum tokens on reasoning for complex problems."
        } else {
            "✓ Normal reasoning mode resumed."
        }
    }

    private suspend fun handleForgetUser(params: Map<String, String>, state: TwBrainState): String {
        val key = params["key"]
            ?: params["fact"]
            ?: return "Error: key or fact parameter required"

        val profile = memoryManager.loadUserProfile()
        val removed = profile.preferences.remove(key) != null ||
                      profile.ongoingProjects.removeAll { it.equals(key, ignoreCase = true) }
        memoryManager.saveUserProfile(profile)

        return if (removed) "✓ Removed user knowledge: $key" else "⚠️ Not found: $key"
    }

    private suspend fun handleLearnUser(params: Map<String, String>, state: TwBrainState): String {
        val fact = params["fact"]
            ?: params["content"]
            ?: params["value"]
            ?: return "Error: fact parameter required"
        val category = params["category"] ?: "general"

        memoryManager.learnUserFact(fact, category)
        val profile = memoryManager.loadUserProfile()
        profile.preferences[category] = fact
        memoryManager.saveUserProfile(profile)

        return "✓ Learned about the user.\n\n" +
            "Category: $category\n" +
            "Fact: $fact\n\n" +
            "I'll remember this for future conversations."
    }

    // ─── AFTER TOOL CALL ──────────────────────────────────────────────────

    /**
     * Called after every tool execution (both brain tools AND regular tools).
     * Updates brain state, tracks metrics, checks iteration budget.
     */
    suspend fun afterToolCall(
        toolName: String,
        brainState: TwBrainState
    ) {
        // Track tool usage
        brainState.trackToolCall(toolName)

        // Reset iteration if this was the last allowed tool call
        if (!brainState.consumeIteration()) {
            AppLogger.w(TAG, "Iteration budget exhausted after $toolName")
        }
    }

    /**
     * Called after conversation ends. Auto-save session insights.
     */
    suspend fun onConversationEnd(
        brainState: TwBrainState,
        totalTokens: Long = 0
    ) {
        try {
            // Record session
            memoryManager.recordSession(
                tokens = totalTokens,
                toolCalls = brainState.memory.insights.totalToolCalls
            )

            // Auto-save memory
            memoryManager.saveMemory(brainState.memory)

            // Save user profile
            memoryManager.saveUserProfile(brainState.userProfile)

            AppLogger.d(TAG, "Conversation end: saved ${brainState.memory.memories.size} memories")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save on conversation end", e)
        }
    }

    /**
     * Analyze conversation to auto-extract facts for memory.
     * Call this periodically to learn from conversation.
     */
    suspend fun analyzeForAutoMemory(
        conversationHistory: List<Pair<String, String>>,
        brainState: TwBrainState
    ): List<String> {
        val extractedFacts = mutableListOf<String>()

        // Look for user preferences mentioned in conversation
        for ((role, content) in conversationHistory) {
            if (role != "user") continue

            val lower = content.lowercase()

            // Simple pattern matching for user facts
            val patterns = mapOf(
                "project" to Regex("i(?:'m| am) working on (.+)"),
                "language" to Regex("i speak (.+)"),
                "interest" to Regex("i(?:'m| am) interested in (.+)"),
                "location" to Regex("i live in (.+)"),
            )

            for ((category, pattern) in patterns) {
                pattern.findAll(lower).forEach { match ->
                    val fact = match.groupValues[1].trim()
                    if (fact.length > 3 && fact.length < 200) {
                        extractedFacts.add(fact)
                        memoryManager.learnUserFact(fact, category)
                    }
                }
            }
        }

        if (extractedFacts.isNotEmpty()) {
            AppLogger.d(TAG, "Auto-extracted ${extractedFacts.size} facts from conversation")
        }

        return extractedFacts
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────

    private fun ageString(date: Date): String {
        val diff = System.currentTimeMillis() - date.time
        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
        return when {
            days == 0 -> "today"
            days == 1 -> "yesterday"
            days < 7 -> "$days days ago"
            days < 30 -> "${days / 7} weeks ago"
            days < 365 -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }
    }
}
