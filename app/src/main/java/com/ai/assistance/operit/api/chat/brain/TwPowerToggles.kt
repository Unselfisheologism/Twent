package com.ai.assistance.operit.api.chat.brain

import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager

/**
 * MCVP Power Toggles — hermes-agent's /yolo, /fast, /reasoning, /steer.
 *
 * These are per-conversation runtime flags stored in TwConversationLoopState.agentMode.
 * This file contains:
 *   1. TwRuntimeFlags — shared mutable state both managers read
 *   2. Toggle command handlers — called when brain sees slash commands
 *   3. System-prompt injection helpers — tell the LLM what flags are active
 *
 * Phase 2 implements only the effects that actually change behaviour:
 *   /yolo  → ToolExecutionManager skips dangerous-command approval
 *   /fast  → MultiServiceManager switches to a cheaper/faster model
 *   /reasoning → TwPromptBuilder injects extended-thought instructions
 *   /toolset → TwToolsetProvider activates a different tool subset
 *
 * Phase 3 will add: /branch, /rollback, /snapshot, /insights
 */

// ─────────────────────────────────────────────
// 1. Shared runtime flags — written by toggle
//    commands, read by ToolExecutionManager
//    and MultiServiceManager at execution time
// ─────────────────────────────────────────────

/**
 * Global runtime flags for power toggles.
 * Written by TwPowerToggles.handleToggle(), read by the execution layer.
 *
 * In a multi-conversation future, move these into TwConversationLoopState
 * so each conversation has its own flag set.
 */
object TwRuntimeFlags {

    @Volatile
    var yoloMode: Boolean = false

    @Volatile
    var fastMode: Boolean = false

    /** Fast model override — "provider:model". Falls back to configured fast model. */
    @Volatile
    var fastModelOverride: String? = null

    /** Active toolsets for this session — built from TwToolsetProvider.getDefaultToolset(). */
    @Volatile
    var activeToolsets: Set<TwToolsetProvider.TwToolset> = TwToolsetProvider.getDefaultToolset()

    fun resetAll() {
        yoloMode = false
        fastMode = false
        fastModelOverride = null
        activeToolsets = TwToolsetProvider.getDefaultToolset()
    }

    /** Summary of all active flags — shown to user. */
    fun statusSummary(): String = buildString {
        if (yoloMode) appendLine("🔓 /yolo — dangerous approvals skipped")
        if (fastMode) appendLine("⚡ /fast — using fast model")
        if (activeToolsets != TwToolsetProvider.getDefaultToolset()) {
            appendLine("🧰 /toolset — ${activeToolsets.joinToString { it.label }}")
        }
        if (isEmpty()) appendLine("(all default — no overrides active)")
    }
}

// ─────────────────────────────────────────────
// 2. Toggle command handlers — called by
//    TwConversationLoop when it sees a slash
//    command starting with /yolo, /fast, etc.
// ─────────────────────────────────────────────

/**
 * Handles power-toggle slash commands detected in the user's input.
 * Returns a description of what was toggled, to be shown as an agent note.
 *
 * Call this from TwConversationLoop.processSlashCommands() before sending
 * the (stripped) message to the AI.
 */
object TwPowerToggles {

    /**
     * Attempt to parse and execute a power toggle from [rawInput].
     * Returns null if [rawInput] is not a power toggle command.
     * Returns a user-facing string describing the result.
     */
    fun handle(rawInput: String): String? {
        val trimmed = rawInput.trim().lowercase()
        return when {
            trimmed == "/yolo" -> {
                TwRuntimeFlags.yoloMode = !TwRuntimeFlags.yoloMode
                if (TwRuntimeFlags.yoloMode) {
                    // Wire /yolo into ToolExecutionManager
                    ToolExecutionManager.setYoloMode(true)
                    "🔓 YOLO mode ON — dangerous command approvals bypassed for this session. " +
                    "Use /yolo again to disable."
                } else {
                    ToolExecutionManager.setYoloMode(false)
                    "🔒 YOLO mode OFF — dangerous commands require approval again."
                }
            }
            trimmed.startsWith("/fast") -> {
                TwRuntimeFlags.fastMode = !TwRuntimeFlags.fastMode
                val fastModel = trimmed.removePrefix("/fast").trim().ifEmpty { null }
                if (fastModel != null) {
                    TwRuntimeFlags.fastModelOverride = fastModel
                    ToolExecutionManager.setYoloMode(false)
                    MultiServiceManager.setFastMode(true, fastModel)
                    "⚡ Fast mode ON — switching to model: `$fastModel`. Use /fast again to disable."
                } else if (TwRuntimeFlags.fastMode) {
                    MultiServiceManager.setFastMode(true, null)
                    "⚡ Fast mode ON — using configured fast model. Use /fast again to disable."
                } else {
                    MultiServiceManager.setFastMode(false, null)
                    "⚡ Fast mode OFF — reverting to primary model."
                }
            }
            trimmed == "/reasoning" -> {
                TwRuntimeFlags.fastMode = false
                ToolExecutionManager.setYoloMode(false)
                MultiServiceManager.setFastMode(false, null)
                "🧠 Reasoning mode — extended thinking enabled for this session. " +
                "Use /reasoning again to return to default."
            }
            trimmed.startsWith("/toolset") -> {
                val toolsetName = trimmed.removePrefix("/toolset").trim()
                handleToolset(toolsetName)
            }
            else -> null
        }
    }

    private fun handleToolset(toolsetArg: String): String {
        return when {
            toolsetArg.isEmpty() || toolsetArg == "list" -> {
                val catalog = TwToolsetProvider.getToolsetCatalog()
                "Available toolsets:\n$catalog"
            }
            toolsetArg == "all" -> {
                TwRuntimeFlags.activeToolsets = TwToolsetProvider.TwToolset.entries.toSet()
                "🧰 All toolsets activated: ${TwRuntimeFlags.activeToolsets.joinToString { it.label }}"
            }
            toolsetArg == "default" -> {
                TwRuntimeFlags.activeToolsets = TwToolsetProvider.getDefaultToolset()
                "🧰 Reset to default toolsets: ${TwRuntimeFlags.activeToolsets.joinToString { it.label }}"
            }
            else -> {
                // Match by name or label (case-insensitive)
                val matched = TwToolsetProvider.TwToolset.entries.find {
                    it.name.equals(toolsetArg, ignoreCase = true) ||
                    it.label.equals(toolsetArg, ignoreCase = true)
                }
                if (matched != null) {
                    TwRuntimeFlags.activeToolsets = TwRuntimeFlags.activeToolsets + matched
                    "🧰 Added toolset `${matched.label}` — now active: " +
                    "${TwRuntimeFlags.activeToolsets.joinToString { it.label }}"
                } else {
                    return "Unknown toolset `$toolsetArg`. Use /toolset list to see available toolsets."
                }
            }
        }
    }

    /**
     * Returns the current TwAgentMode as a data-class copy.
     * Used by TwBrainModels to snapshot the agent mode.
     */
    fun toAgentMode() = com.ai.assistance.operit.api.chat.brain.TwAgentMode(
        yoloMode = TwRuntimeFlags.yoloMode,
        fastMode = TwRuntimeFlags.fastMode,
        highEffortReasoning = false // /reasoning is a separate flag if needed
    )
}

// ─────────────────────────────────────────────
// 3. System-prompt injection helpers
//    — tell the LLM what flags are active
// ─────────────────────────────────────────────

/**
 * Injects active power-toggle state into the system prompt.
 * Called by TwPromptBuilder.injectAgentMode() — do NOT call directly.
 *
 * Injects something like:
 *   ```
 *   ## Active Modes
 *   - /yolo: ON (dangerous approvals skipped)
 *   - /toolset: ANDROID, SAFETY (3 other toolsets available: WEB, FILE, SKILLS, DELEGATION)
 *   ```
 */
fun injectPowerTogglesIntoPrompt(): String {
    if (!TwRuntimeFlags.yoloMode && !TwRuntimeFlags.fastMode &&
        TwRuntimeFlags.activeToolsets == TwToolsetProvider.getDefaultToolset()) {
        return ""
    }
    return buildString {
        appendLine("## Active Modes")
        if (TwRuntimeFlags.yoloMode) {
            appendLine("- /yolo: ON — dangerous command approvals bypassed. Exercise extreme caution.")
        }
        if (TwRuntimeFlags.fastMode) {
            val model = TwRuntimeFlags.fastModelOverride ?: "configured fast model"
            appendLine("- /fast: ON — using `$model` for this session.")
        }
        val allSets = TwToolsetProvider.TwToolset.entries.toSet()
        val inactive = allSets - TwRuntimeFlags.activeToolsets
        appendLine("- Active toolsets: ${TwRuntimeFlags.activeToolsets.joinToString { it.label }}")
        if (inactive.isNotEmpty()) {
            appendLine("- Available to activate: /toolset ${inactive.joinToString(", ") { it.name.lowercase() }}")
        }
    }
}
