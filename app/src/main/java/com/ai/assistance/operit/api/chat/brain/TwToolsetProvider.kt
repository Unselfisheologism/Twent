package com.ai.assistance.operit.api.chat.brain

import com.ai.assistance.operit.core.tools.AIToolHandler

/**
 * MCVP Toolset Provider — maps 109+ registered tools into 6 focused toolsets.
 * Each toolset is a curated subset the LLM sees, reducing token cost and
 * improving tool-selection accuracy. Tools not in any toolset are excluded.
 *
 * Inspired by hermes-agent's skills-as-toolsets: slash commands are grouped
 * into capabilities the brain can reason about. The agent activates a toolset
 * based on the task domain, not blindly handing it 109 tools every turn.
 *
 * Toolset changes require BrainToolCallProvider.rebuild() to re-inject the
 * new tool list into the LLM's function-calling schema.
 */
object TwToolsetProvider {

    // ─────────────────────────────────────────────
    // Toolset definitions — each named for the
    // mental model the brain reasons with
    // ─────────────────────────────────────────────

    enum class TwToolset(val label: String, val description: String) {
        /** Web access: HTTP calls, browser automation, content fetching */
        WEB("Web Access", "HTTP requests, browser automation, file downloads via URL"),

        /** File system: read, write, search, navigate local files */
        FILE("File Operations", "Read, write, search, and navigate the local file system"),

        /** Skills system: load skills, manage skill registry */
        SKILLS("Skills & Knowledge", "Load skills, query the skill registry, invoke knowledge systems"),

        /** Delegation: spawn subagents, manage background processes */
        DELEGATION("Delegation & Agents", "Spawn subagents, manage background processes, delegate tasks"),

        /** Safety: logs, snapshots, memory operations — low-risk introspection */
        SAFETY("Safety & Introspection", "Memory injection, session snapshots, permission checks, logs"),

        /** Android automation: UI gestures, device control, system settings */
        ANDROID("Android Automation", "Tap, swipe, type, launch apps, system settings, device control");

        companion object {
            /** All available toolsets in priority order */
            fun all() = entries.toList()
        }
    }

    // ─────────────────────────────────────────────
    // Tool membership — exact strings matching
    // ToolRegistration.kt tool names
    // ─────────────────────────────────────────────

    private val WEB_TOOLS = setOf(
        "http_request",
        "multipart_request",
        "manage_cookies",
        "download_file"
    )

    private val FILE_TOOLS = setOf(
        "file_exists",
        "move_file",
        "copy_file",
        "make_directory",
        "find_files",
        "file_info",
        "apply_file",
        "zip_files",
        "unzip_files",
        "open_file",
        "share_file",
        "grep_code",
        "grep_context"
    )

    private val SKILLS_TOOLS = setOf(
        "skill_view",
        "skill_list",
        "skill_manage",
        "skill_create",
        "skills_list",
        "memory"
    )

    private val DELEGATION_TOOLS = setOf(
        "delegate_task",
        "cronjob",
        "terminal"
    )

    private val SAFETY_TOOLS = setOf(
        "session_search",
        "tw_snapshot_session",
        "tw_restore_session",
        "tw_list_snapshots",
        "tw_branch_session",
        "tw_get_insights",
        "toast",
        "send_notification"
    )

    private val ANDROID_TOOLS = setOf(
        // Gestures
        "tap",
        "swipe",
        "swipe_left",
        "swipe_right",
        "swipe_up",
        "swipe_down",
        "scroll_left",
        "scroll_right",
        "scroll_up",
        "scroll_down",
        "hold",
        "press_key",
        "type_text",
        // Navigation
        "open_app",
        "back",
        "home",
        "get_page_info",
        "get_current_activity",
        // System
        "get_system_setting",
        "modify_system_setting",
        "get_device_location",
        "install_app",
        "uninstall_app",
        "list_installed_apps",
        "start_app",
        "stop_app",
        "get_notifications",
        // Media / conversion
        "ffmpeg_execute",
        "ffmpeg_info",
        "ffmpeg_convert",
        "text_to_speech",
        "speech_to_text",
        "create_mini_app",
        "list_mini_apps",
        "delete_mini_app",
        // Composio — pre-auth tool integrations (NOT messaging bots)
        "composio_list_toolkits",
        "composio_execute_tool",
        "composio_list_connections",
        "composio_connect",
        "composio_disconnect"
    )

    private val TOOLSET_MAP: Map<TwToolset, Set<String>> = mapOf(
        TwToolset.WEB       to WEB_TOOLS,
        TwToolset.FILE      to FILE_TOOLS,
        TwToolset.SKILLS    to SKILLS_TOOLS,
        TwToolset.DELEGATION to DELEGATION_TOOLS,
        TwToolset.SAFETY    to SAFETY_TOOLS,
        TwToolset.ANDROID   to ANDROID_TOOLS
    )

    /** Map of tool name → its toolset (if registered) */
    private val TOOL_TO_TOOLSET: Map<String, TwToolset> by lazy {
        TOOLSET_MAP.flatMap { (toolset, names) ->
            names.map { it to toolset }
        }.toMap()
    }

    // ─────────────────────────────────────────────
    // Public API — called by BrainToolCallProvider
    // and TwToolsetSelector (UI for toolset picker)
    // ─────────────────────────────────────────────

    /**
     * Returns all tool names belonging to [toolset].
     * Returns empty set if toolset is unknown.
     */
    fun getToolsForToolset(toolset: TwToolset): Set<String> {
        return TOOLSET_MAP[toolset] ?: emptySet()
    }

    /**
     * Returns the toolset a given tool belongs to, or null if not mapped.
     * Unmapped tools are excluded from all toolsets (not shown to LLM).
     */
    fun getToolsetForTool(toolName: String): TwToolset? {
        return TOOL_TO_TOOLSET[toolName]
    }

    /**
     * Returns the default toolset shown to the LLM on every new session.
     * Currently: ANDROID (the app's core use case) + SAFETY (introspection).
     * The brain can activate more toolsets via /toolset command.
     */
    fun getDefaultToolset(): Set<TwToolset> = setOf(
        TwToolset.ANDROID,
        TwToolset.SAFETY
    )

    /**
     * Returns the union of all tools across [toolsets].
     * Used to build the LLM function-calling schema.
     */
    fun getAllToolsInToolsets(toolsets: Set<TwToolset>): Set<String> {
        return toolsets.flatMap { getToolsForToolset(it) }.toSet()
    }

    /**
     * Returns a markdown-formatted catalog of all toolsets and their tools.
     * Injected into the system prompt so the brain knows what it can activate.
     */
    fun getToolsetCatalog(): String {
        return buildString {
            appendLine("## Available Toolsets (activate via /toolset <name>)")
            appendLine()
            TwToolset.entries.forEach { toolset ->
                val tools = getToolsForToolset(toolset)
                appendLine("### ${toolset.label}")
                appendLine("${toolset.description}")
                appendLine("Tools (${tools.size}): `${tools.joinToString("`, `")}`")
                appendLine()
            }
        }
    }

    /**
     * Checks if a tool is considered dangerous (true = requires user approval
     * in CAUTION mode unless /yolo is active).
     *
     * Dangerous = tools that modify system state: install/uninstall apps,
     * modify_system_setting, delete_file, move/copy files, shell commands.
     */
    fun isToolDangerous(toolName: String): Boolean {
        return toolName in setOf(
            "install_app", "uninstall_app", "modify_system_setting",
            "apply_file", "move_file", "copy_file", "delete_mini_app",
            "terminal", "stop_app",
            // All dangerous shell/file mutations go here
        )
    }

    /**
     * Returns the number of tools per toolset. Used to show a summary
     * in the toolset picker UI.
     */
    fun getToolsetSizes(): Map<TwToolset, Int> {
        return TwToolset.entries.associateWith { getToolsForToolset(it).size }
    }
}
