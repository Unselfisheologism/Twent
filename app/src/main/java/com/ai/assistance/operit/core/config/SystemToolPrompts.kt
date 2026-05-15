package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

/**
 * Tool prompt manager — English only. Chinese content removed.
 */
object SystemToolPrompts {

    private fun buildSafBookmarksSection(safBookmarkNames: List<String>): String {
        val names = safBookmarkNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (names.isEmpty()) return ""
        val listed = names.joinToString(", ") { "repo:$it" }
        return """**Attached Local Storage Repository:**
                  - environment (optional): you can also use `environment="repo:<repositoryName>"` to operate in an attached local storage repository.
                  - Paths are absolute (e.g., `/`, `/work/index.html`).
                  - Available repositories: $listed
                  """.trimEnd()
    }

    // ==================== Basic Tools ====================
    val basicTools = SystemToolPromptCategory(
        categoryName = "Available tools",
        tools = listOf(
            ToolPrompt(
                name = "sleep",
                description = "Demonstration tool that pauses briefly.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "duration_ms", type = "integer", description = "milliseconds, default 1000, max 10000", required = false, default = "1000")
                )
            ),
            ToolPrompt(
                name = "use_package",
                description = "Activate a package for use in the current session.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "package_name", type = "string", description = "name of the package to activate", required = true)
                )
            )
        )
    )

    // ==================== File System Tools ====================
    val fileSystemTools = SystemToolPromptCategory(
        categoryName = "File System Tools",
        tools = listOf(
            ToolPrompt(
                name = "ssh_login",
                description = "Login to a remote SSH server. After logging in, all file tools with environment=\"linux\" will use this SSH connection instead of the local Ubuntu 24 terminal.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "host", type = "string", description = "SSH server address", required = true),
                    ToolParameterSchema(name = "port", type = "integer", description = "optional, default 22", required = false, default = "22"),
                    ToolParameterSchema(name = "username", type = "string", description = "required", required = true),
                    ToolParameterSchema(name = "password", type = "string", description = "required", required = true),
                    ToolParameterSchema(name = "enable_reverse_mount", type = "boolean", description = "optional, enables reverse mounting of local storage to remote server", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "ssh_exit",
                description = "Logout from the SSH connection. After logout, file tools will resume using the local Ubuntu 24 terminal.",
                parametersStructured = listOf()
            ),
            ToolPrompt(
                name = "list_files",
                description = "List files in a directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "e.g. /sdcard/Download", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false)
                )
            ),
            ToolPrompt(
                name = "read_file",
                description = "Read the content of a file. For image files (jpg, jpeg, png, gif, bmp), it automatically extracts text using OCR.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, execution environment. Values: android (default, Android file system) | linux (local Ubuntu 24 terminal via proot; Linux paths like /home/... /etc/hosts) | repo:<repositoryName> (attached local storage repository)", required = false),
                    ToolParameterSchema(name = "intent", type = "string", description = "optional, your question about the media/file (used for backend recognition)", required = false),
                    ToolParameterSchema(name = "direct_image", type = "boolean", description = "optional, when true: return an image tag for models that support vision", required = false),
                    ToolParameterSchema(name = "direct_audio", type = "boolean", description = "optional, when true: return an audio tag for models that support audio", required = false),
                    ToolParameterSchema(name = "direct_video", type = "boolean", description = "optional, when true: return a video tag for models that support video", required = false)
                )
            ),
            ToolPrompt(
                name = "read_file_part",
                description = "Read file content by line range.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "start_line", type = "integer", description = "starting line number, 1-indexed, default 1", required = false, default = "1"),
                    ToolParameterSchema(name = "end_line", type = "integer", description = "ending line number, 1-indexed, inclusive, default start_line + 99", required = false, default = "100")
                )
            ),
            ToolPrompt(
                name = "apply_file",
                description = "Applies edits to a file by finding and replacing/deleting a matched content block.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "type", type = "string", description = "operation type: replace | delete | create", required = true),
                    ToolParameterSchema(name = "old", type = "string", description = "the exact content to be matched and replaced/deleted (required for replace/delete)", required = false),
                    ToolParameterSchema(name = "new", type = "string", description = "the new content to insert (required for replace/create)", required = false)
                ),
                details = """
  - **How it works**:
    - The tool finds the best fuzzy match of `old` in the current file content (not by line numbers) and applies the requested operation.
    - You can call this tool multiple times to apply multiple independent edits.
  - **Parameters**:
    - `type`: `replace` = replace matched `old` with `new`; `delete` = remove matched `old`; `create` = create file with `new` content
    - `old`: required for replace/delete
    - `new`: required for replace/create
  - **CRITICAL RULES**:
    1. To rewrite a whole existing file: call `delete_file` first, then `write_file`. Do NOT use apply_file to overwrite.
    2. To modify an existing file: use `type=replace` with `old`/`new`. Do NOT delete and rewrite the whole file.
"""
            ),
            ToolPrompt(
                name = "delete_file",
                description = "Delete a file or directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "target path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "recursive", type = "boolean", description = "optional, default false", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "make_directory",
                description = "Create a directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "directory path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "create_parents", type = "boolean", description = "optional, create parent directories, default false", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "find_files",
                description = "Search for files matching a pattern.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "search path. Android: /sdcard/... Linux: /home/... or /etc/...", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "pattern", type = "string", description = "search pattern, e.g. *.jpg", required = true),
                    ToolParameterSchema(name = "max_depth", type = "integer", description = "optional, controls depth of subdirectory search, -1=unlimited", required = false),
                    ToolParameterSchema(name = "use_path_pattern", type = "boolean", description = "optional, default false", required = false, default = "false"),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "optional, default false", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "grep_code",
                description = "Search code content matching a regex pattern in files. Returns matches with surrounding context lines.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "search path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "pattern", type = "string", description = "regex pattern", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "optional, file filter, default *", required = false, default = "*"),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "optional, default false", required = false, default = "false"),
                    ToolParameterSchema(name = "context_lines", type = "integer", description = "optional, lines of context before/after match, default 3", required = false, default = "3"),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "optional, max matches, default 100", required = false, default = "100")
                )
            ),
            ToolPrompt(
                name = "grep_context",
                description = "Search for relevant content based on intent/context understanding. Directory mode: finds most relevant files. File mode: finds most relevant code segments within a file. Uses semantic relevance scoring.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "directory or file path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "intent", type = "string", description = "intent or context description string", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "optional, file filter for directory mode, default *", required = false, default = "*"),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "optional, maximum items to return, default 10", required = false, default = "10")
                )
            ),
            ToolPrompt(
                name = "download_file",
                description = "Download a file from the internet.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "file URL to download", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "save path", required = true),
                    ToolParameterSchema(name = "environment", type = "string", description = "optional, same as read_file environment", required = false),
                    ToolParameterSchema(name = "headers", type = "string", description = "optional, HTTP headers as JSON object string, e.g. {\"Referer\":\"...\"}", required = false)
                )
            )
        )
    )

    // ==================== Web Search / Shell Tools ====================
    val httpTools = SystemToolPromptCategory(
        categoryName = "Web Search Tools",
        tools = listOf(
            ToolPrompt(
                name = "execute_shell",
                description = "Execute shell commands in Terminal app session.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "command", type = "string", description = "shell command to execute", required = true),
                    ToolParameterSchema(name = "session_id", type = "string", description = "optional: terminal session ID to use specific session", required = false),
                    ToolParameterSchema(name = "timeout_ms", type = "integer", description = "optional, maximum time to wait for command completion in milliseconds. Default 30000. Range: 1000-300000.", required = false, default = "30000"),
                )
            )
        )
    )

    // ==================== Memory Tools ====================
    val memoryTools = SystemToolPromptCategory(
        categoryName = "Memory and Memory Library Tools",
        tools = listOf(
            ToolPrompt(
                name = "query_memory",
                description = "Searches the memory library for relevant memories using hybrid search (keyword matching + semantic understanding). Use when you need to recall past knowledge, look up specific information, or require context. Keywords can be separated by '|' or spaces. Use \"*\" as query to return all memories (optionally filtered by folder_path). Filter by creation time using start_time/end_time (Unix milliseconds). When the user attaches a memory folder, use the folder_path parameter to restrict the search. IMPORTANT: For document nodes (uploaded files), this tool uses vector search to return ONLY the most relevant chunks matching your query, NOT the entire document. Results show \"Document: [name], Chunk X/Y: [content]\" format. Use get_memory_by_title to read the complete document or specific parts. NOTE: When limit > 20, results show only titles and truncated content to save tokens.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "query", type = "string", description = "the keyword or question to search for, or \"*\" to return all memories", required = true),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "optional, the specific folder path to search within", required = false),
                    ToolParameterSchema(name = "start_time", type = "integer", description = "optional, Unix timestamp in milliseconds. Filters memories by createdAt >= start_time", required = false),
                    ToolParameterSchema(name = "end_time", type = "integer", description = "optional, Unix timestamp in milliseconds. Filters memories by createdAt <= end_time", required = false),
                    ToolParameterSchema(name = "threshold", type = "number", description = "optional, float 0.0-1.0, semantic similarity threshold, lower values return more results, default 0.25", required = false, default = "0.25"),
                    ToolParameterSchema(name = "limit", type = "integer", description = "optional, int >= 1, maximum number of results. When > 20, only titles and truncated content are returned, default 5", required = false, default = "5")
                )
            ),
            ToolPrompt(
                name = "get_memory_by_title",
                description = "Retrieves a memory by exact title. For regular memories, returns full content. For document nodes (uploaded files): read entire document (no params), read specific chunk(s) via chunk_index or chunk_range, or search within via query. Use when query_memory returns partial results and you need more complete content.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "required, the exact title of the memory", required = true),
                    ToolParameterSchema(name = "chunk_index", type = "integer", description = "optional, read a specific chunk by its number, e.g. 3 for the 3rd chunk", required = false),
                    ToolParameterSchema(name = "chunk_range", type = "string", description = "optional, read a range of chunks in start-end format, e.g. 3-7 for chunks 3 through 7", required = false),
                    ToolParameterSchema(name = "query", type = "string", description = "optional, search for matching chunks within the document using keywords or semantic search", required = false)
                )
            )
        ),
        categoryFooter = "\nNote: The memory library and user personality profile are automatically updated by a separate system after you output the task completion marker. However, if you need to manage memories immediately or update user preferences, use the appropriate tools directly."
    )

    // ==================== Mini-App Tools ====================
    val miniAppTools = SystemToolPromptCategory(
        categoryName = "Mini-App Tools",
        tools = listOf(
            ToolPrompt(
                name = "create_mini_app",
                description = "Create an interactive mini-app (HTML/CSS/JS application) that the user can launch from the app. Mini-apps support localStorage for data persistence and can call the AI model via window.OperitMiniAppNative.aiSendMessage(). Generate complete, self-contained HTML with embedded CSS and JS.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "name", type = "string", description = "Name of the mini-app", required = true),
                    ToolParameterSchema(name = "html", type = "string", description = "Complete HTML content with embedded CSS in <style> and JS in <script> tags", required = true),
                    ToolParameterSchema(name = "type", type = "string", description = "optional, App type: persistent (default) or ephemeral", required = false, default = "persistent"),
                    ToolParameterSchema(name = "css", type = "string", description = "optional, separate CSS content", required = false),
                    ToolParameterSchema(name = "javascript", type = "string", description = "optional, separate JavaScript content", required = false),
                    ToolParameterSchema(name = "description", type = "string", description = "optional, brief description of the mini-app", required = false)
                )
            ),
            ToolPrompt(
                name = "list_mini_apps",
                description = "List all existing mini-apps.",
                parametersStructured = emptyList()
            ),
            ToolPrompt(
                name = "delete_mini_app",
                description = "Delete an existing mini-app by ID.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "app_id", type = "string", description = "The ID of the mini-app to delete", required = true)
                )
            )
        ),
        categoryFooter = "\nMini-apps are served at: http://localhost:8095/mini_app/{id}/index.html"
    )

    // ==================== Composio Tools ====================
    // Composio gives access to 1000+ integrations (GitHub, Slack, Notion, Gmail, etc.)
    // IMPORTANT: Before calling ANY Composio tool, you MUST first use composio_get_toolkit_docs
    // to fetch the exact tool names, descriptions, and parameters for the relevant toolkit.
    // Never guess Composio tool names — always fetch docs first.
    // Connected accounts from the Integrations page are used automatically for auth.
    private val composioToolsEn = SystemToolPromptCategory(
        categoryName = "composio_tools",
        categoryHeader = "Composio Integration Tools — Access 1000+ apps (Gmail, GitHub, Slack, Notion, Linear, Google Calendar, etc.) connected via the Integrations page. MANDATORY WORKFLOW: (1) First call composio_get_toolkit_docs with toolkit_slug (e.g. 'gmail') to get exact tool names and parameters. (2) Then call composio_execute_tool with tool_name and parameters. NEVER guess tool names. Example for Gmail: composio_get_toolkit_docs(toolkit_slug='gmail'), then composio_execute_tool(tool_name='GMAIL_SEND_EMAIL', parameters={'to': '...', 'subject': '...', 'body': '...'}).",
        tools = listOf(
            ToolPrompt(
                name = "composio_get_toolkit_docs",
                description = "MANDATORY — Fetch full documentation for a specific Composio toolkit. Returns ALL available tool names, their descriptions, and exact parameters. MUST be called BEFORE composio_execute_tool. Example: toolkit_slug='gmail' returns GMAIL_SEND_EMAIL, GMAIL_LIST_EMAILS, etc. with full parameter details. Never call composio_execute_tool without first calling this.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "toolkit_slug", type = "string", description = "The toolkit identifier (e.g. 'gmail', 'github', 'slack', 'notion', 'linear', 'googlecalendar'). Use lowercase. Required.", required = true),
                    ToolParameterSchema(name = "limit", type = "integer", description = "Max number of tools to return (default: 50, max: 200). Optional.", required = false)
                )
            ),
            ToolPrompt(
                name = "composio_execute_tool",
                description = "Execute a specific Composio tool after getting its docs from composio_get_toolkit_docs. You MUST know the exact tool_name from the docs. Example: tool_name='GMAIL_SEND_EMAIL', parameters={'to': 'user@example.com', 'subject': 'Hello', 'body': 'Hi!'}. WARNING: Do NOT call this without first calling composio_get_toolkit_docs to get the correct tool_name.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "tool_name", type = "string", description = "The exact tool name from composio_get_toolkit_docs response (e.g. 'GMAIL_SEND_EMAIL', 'GITHUB_CREATE_ISSUE', 'SLACK_SEND_MESSAGE'). Case-sensitive. Required.", required = true),
                    ToolParameterSchema(name = "parameters", type = "object", description = "Tool-specific parameters as JSON object. Must match the parameters from composio_get_toolkit_docs exactly. Example: {'to': 'email@example.com', 'subject': 'Hello'}", required = true),
                    ToolParameterSchema(name = "account_id", type = "string", description = "Optional. Specific account ID to use. If not provided, uses the default connected account.", required = false)
                )
            ),
            ToolPrompt(
                name = "composio_list_connections",
                description = "List all Composio integrations the user has connected in the Integrations page (Gmail, GitHub, Slack, etc.). Shows which services are available for use. Use this at the start of a task to know what's connected.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "category", type = "string", description = "Optional. Filter by category: 'productivity', 'communication', 'development', 'social', 'analytics', 'storage'.", required = false),
                    ToolParameterSchema(name = "search", type = "string", description = "Optional. Search by connection name.", required = false)
                )
            ),
            ToolPrompt(
                name = "composio_list_toolkits",
                description = "List all available Composio toolkits (services/integrations). Returns toolkit names, display names, and descriptions. Use this to discover what integrations are available. After finding a toolkit, use composio_get_toolkit_docs to see its specific tools.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "category", type = "string", description = "Optional. Filter by category: 'productivity', 'communication', 'development', 'social', 'analytics', 'storage'.", required = false),
                    ToolParameterSchema(name = "search", type = "string", description = "Optional. Search by toolkit name.", required = false),
                    ToolParameterSchema(name = "limit", type = "integer", description = "Max results (default: 20).", required = false)
                )
            )
        )
    )

    // ==================== MCP Tools ====================
    // MCP tools are DIFFERENT from packages and Composio.
    // - MCP tools are always available when the server is running (no activation needed)
    // - Tool format: server_name:tool_name (e.g., ddg-search:search)
    // - Do NOT use use_package for MCP servers
    // - To use DuckDuckGo web search: mcp_tool with server="ddg-search", tool="search", params={"query": "your search", "max_results": 10}
    private val mcpToolsEn = SystemToolPromptCategory(
        categoryName = "mcp_tools",
        categoryHeader = "MCP Server Tools - Tools from Model Context Protocol servers. These are AUTOMATICALLY available when servers are running. Do NOT use use_package for MCP servers. IMPORTANT: For web search, use mcp_tool with server='ddg-search' and tool='search', params={'query': 'your search query', 'max_results': 10}.",
        tools = listOf(
            ToolPrompt(
                name = "mcp_tool",
                description = "Call a tool from a running MCP server. Format: 'server_name:tool_name' or use this tool with server/tool params. For DuckDuckGo search use: server='ddg-search', tool='search', params={'query': 'search query', 'max_results': 10}. MCP tools are different from packages — they are ready to use immediately without activation. To see available tools, check Plugins > MCP tab.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "server", type = "string", description = "The MCP server name (must be running). For web search use 'ddg-search'", required = true),
                    ToolParameterSchema(name = "tool", type = "string", description = "The tool name on the server. For search use 'search', for content fetch use 'fetch_content'", required = true),
                    ToolParameterSchema(name = "params", type = "object", description = "optional, tool parameters as JSON object. For search: {'query': 'search string', 'max_results': 10, 'region': 'us-en'}. For fetch_content: {'url': 'https://...', 'start_index': 0, 'max_length': 8000}", required = false)
                )
            )
        )
    )

    private val internalToolCategoriesEn: List<SystemToolPromptCategory> = SystemToolPromptsInternal.internalToolCategoriesEn

    /**
     * Get all English tool categories for AI-visible tools
     */
    fun getAIAllCategoriesEn(
        hasBackendImageRecognition: Boolean = false,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false,
        safBookmarkNames: List<String> = emptyList()
    ): List<SystemToolPromptCategory> {
        val shouldExposeIntent =
            (hasBackendImageRecognition && !chatModelHasDirectImage) ||
                (hasBackendAudioRecognition && !chatModelHasDirectAudio) ||
                (hasBackendVideoRecognition && !chatModelHasDirectVideo)

        val adjustedFileSystemTools = fileSystemTools.copy(
            tools = fileSystemTools.tools.map { tool ->
                if (tool.name != "read_file") return@map tool

                val filteredParams = (tool.parametersStructured ?: emptyList()).filter { param ->
                    when (param.name) {
                        "direct_image" -> false
                        "direct_audio" -> false
                        "direct_video" -> false
                        "intent" -> shouldExposeIntent
                        else -> true
                    }
                }

                val adjustedDescription =
                    if (shouldExposeIntent) {
                        "Read the content of a file. For media files, you can also provide an 'intent' parameter to use a backend recognition model for analysis."
                    } else {
                        tool.description
                    }

                tool.copy(
                    description = adjustedDescription + buildSafBookmarksSection(safBookmarkNames),
                    parametersStructured = filteredParams
                )
            }
        )

        return listOf(
            basicTools,
            adjustedFileSystemTools,
            httpTools,
            memoryTools,
            miniAppTools,
            composioToolsEn,
            mcpToolsEn
        )
    }

    /**
     * Get all English tool categories (AI + internal tools)
     */
    fun getAllCategoriesEn(
        hasBackendImageRecognition: Boolean = false,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false,
        safBookmarkNames: List<String> = emptyList()
    ): List<SystemToolPromptCategory> {
        return getAIAllCategoriesEn(
            hasBackendImageRecognition = hasBackendImageRecognition,
            chatModelHasDirectImage = chatModelHasDirectImage,
            hasBackendAudioRecognition = hasBackendAudioRecognition,
            hasBackendVideoRecognition = hasBackendVideoRecognition,
            chatModelHasDirectAudio = chatModelHasDirectAudio,
            chatModelHasDirectVideo = chatModelHasDirectVideo,
            safBookmarkNames = safBookmarkNames
        ) + internalToolCategoriesEn
    }

    /**
     * Generate the complete tools prompt text (English only)
     */
    fun generateToolsPromptEn(
        hasBackendImageRecognition: Boolean = false,
        includeMemoryTools: Boolean = true,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false,
        safBookmarkNames: List<String> = emptyList()
    ): String {
        val categories = if (includeMemoryTools) {
            getAIAllCategoriesEn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo,
                safBookmarkNames = safBookmarkNames
            )
        } else {
            getAIAllCategoriesEn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo,
                safBookmarkNames = safBookmarkNames
            )
                .filter { it.categoryName != "Memory and Memory Library Tools" }
        }

        return categories.joinToString("\n\n") { it.toString() }
    }
}
