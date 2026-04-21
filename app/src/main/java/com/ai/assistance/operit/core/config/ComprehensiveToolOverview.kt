package com.ai.assistance.operit.core.config

/**
 * Comprehensive system prompt that provides AI agents with a clear picture 
 * of all available tools and their functions.
 * 
 * This is used for AI Chat page, voice activation, and long-press activation.
 */
object ComprehensiveToolOverview {

    /**
     * English version of the comprehensive tool overview
     */
    val TOOL_OVERVIEW_EN = """
## ═══════════════════════════════════════════════════════════════════
## COMPREHENSIVE TOOL OVERVIEW - YOUR CAPABILITIES
## ═══════════════════════════════════════════════════════════════════

You have access to a wide range of tools organized into categories. 
Here's a complete overview of what you can do:

════════════════════════════════════════════════════════════════════════
### 1. BASIC OPERATIONS
════════════════════════════════════════════════════════════════════════

• sleep - Pause briefly for demonstration or timing
• use_package - Activate a package to access additional tools within it

════════════════════════════════════════════════════════════════════════
### 2. FILE SYSTEM TOOLS
════════════════════════════════════════════════════════════════════════

• ssh_login - Login to remote SSH server for remote operations
• read_file - Read file content (supports various file types)
• write_file - Write content to a file (create or overwrite)
• append_file - Append content to an existing file
• download_file - Download files from the internet
• delete_file - Delete a file or directory
• list_dir - List directory contents
• create_dir - Create a new directory
• move_file - Move or rename files/directories
• copy_file - Copy files/directories (supports cross-environment copy)
• file_exists - Check if file/directory exists
• file_info - Get detailed file information (size, permissions, etc.)
• zip_files - Compress files/directories into ZIP
• unzip_files - Extract ZIP archives
• open_file - Open file with system default application
• share_file - Share file with other apps

════════════════════════════════════════════════════════════════════════
### 3. WEB & NETWORK TOOLS
════════════════════════════════════════════════════════════════════════

• visit_web - Visit webpages and extract content (supports link following)
• http_request - Send HTTP requests (GET, POST, PUT, DELETE)
• multipart_request - Upload files via multipart form data
• manage_cookies - Manage browser cookies

════════════════════════════════════════════════════════════════════════
### 4. MEMORY & KNOWLEDGE TOOLS
════════════════════════════════════════════════════════════════════════

• query_memory - Search memory library with hybrid search (keyword + semantic)
• get_memory_by_title - Retrieve specific memory by exact title
• create_memory - Create new memory nodes for future reference
• update_memory - Update existing memory content/metadata
• delete_memory - Delete memory nodes
• link_memories - Create semantic links between memories (knowledge graph)
• update_user_preferences - Update user profile information

════════════════════════════════════════════════════════════════════════
### 5. SHELL & TERMINAL TOOLS
════════════════════════════════════════════════════════════════════════

• execute_shell - Execute device shell commands
• create_terminal_session - Create persistent terminal session
• execute_in_terminal_session - Execute command in session, get full output
• close_terminal_session - Close terminal session

════════════════════════════════════════════════════════════════════════
### 6. ANDROID SYSTEM TOOLS
════════════════════════════════════════════════════════════════════════

• execute_intent - Execute Android Intent (activity/broadcast/service)
• send_broadcast - Send broadcast intents
• device_info - Get device information
• modify_system_setting - Modify system settings
• get_system_setting - Read system settings
• install_app - Install APK files
• uninstall_app - Uninstall apps
• list_installed_apps - List all installed applications
• start_app - Launch applications
• stop_app - Force stop app background processes
• get_notifications - Read device notifications
• toast - Display toast messages
• send_notification - Send system notifications
• get_device_location - Get GPS location

### 7. MATHEMATICS & CALCULATION
═══════════════════════════════════════════════════════════════════════

• calculate - Evaluate mathematical expressions

═══════════════════════════════════════════════════════════════════════
### 8. WORKFLOW TOOLS
═══════════════════════════════════════════════════════════════════════

• get_all_workflows - List all available workflows
• get_workflow - Get workflow details
• create_workflow - Create new workflow
• update_workflow - Update existing workflow
• patch_workflow - Incrementally update workflow
• delete_workflow - Delete workflow
• trigger_workflow - Execute a workflow

═══════════════════════════════════════════════════════════════════════
### 9. COMPOSIO INTEGRATION TOOLS (EXTERNAL SERVICES)
═══════════════════════════════════════════════════════════════════════

Connect to 1000+ external services (GitHub, Slack, Notion, Google Calendar, etc.) via Composio. These tools allow direct integration with external apps without creating workflows.

• composio_list_toolkits - List available integrations/toolkits. Optional: category, search, limit
• composio_execute_tool - Execute a Composio tool (e.g., create GitHub issue, send Slack message)
• composio_list_connections - List all connected OAuth accounts
• composio_connect - Initiate OAuth connection for a toolkit (opens browser for auth)
• composio_disconnect - Disconnect an OAuth account

PREREQUISITE: COMPOSIO_API_KEY must be set in local.properties. Get your key from https://app.composio.dev

Example - List available integrations:
```
{
  "name": "composio_list_toolkits",
  "parameters": [
    {"name": "search", "value": "github"}
  ]
}
```

Example - Execute a GitHub tool:
```
{
  "name": "composio_execute_tool",
  "parameters": [
    {"name": "tool_name", "value": "GITHUB_CREATE_ISSUE"},
    {"name": "parameters", "value": "{\"repo\":\"owner/repo\",\"title\":\"Bug report\",\"body\":\"Description here\"}"},
    {"name": "account_id", "value": "your-connected-account-id"}
  ]
}
```

Example - Connect a GitHub account:
```
{
  "name": "composio_connect",
  "parameters": [
    {"name": "toolkit", "value": "github"}
  ]
}
```

═══════════════════════════════════════════════════════════════════════
### 10. CHAT & CONVERSATION TOOLS
════════════════════════════════════════════════════════════════════════

• start_chat_service - Start floating chat interface
• stop_chat_service - Stop chat service
• create_new_chat - Create new chat session
• list_chats - List all chats (with filtering/sorting)
• find_chat - Find specific chat by title
• switch_chat - Switch to different chat
• send_message_to_ai - Send message to AI in chat
• list_character_cards - List available role cards
• get_chat_messages - Retrieve messages from chat history
• agent_status - Check chat processing status

═══════════════════════════════════════════════════════════════════════
### 11. TASKER INTEGRATION
═══════════════════════════════════════════════════════════════════════

• trigger_tasker_event - Trigger Tasker events for automation

═══════════════════════════════════════════════════════════════════════
### 12. MEDIA PROCESSING
════════════════════════════════════════════════════════════════════════

• ffmpeg_execute - Execute FFmpeg commands
• ffmpeg_info - Get FFmpeg capabilities info
• ffmpeg_convert - Convert video/audio formats

═══════════════════════════════════════════════════════════════════════
### 13. PACKAGE SYSTEM (EXTENSIBILITY) — SKILLS, MCP SERVERS, AND MORE
═══════════════════════════════════════════════════════════════════════

Your capabilities are NOT limited to the built-in tools listed above. Packages and integrations dramatically extend what you can do. There are four types:

**Skills**: Reusable automation workflows with step-by-step guides for specific tasks (GitHub operations, API integrations, app-specific patterns). Skills contain critical knowledge — API endpoints, parameters, workflows — that you won't discover on your own. A skill can tell you exactly how to accomplish a complex task.

**MCP (Model Context Protocol) Servers**: External service integrations that expose tools from remote APIs. MCP servers give you direct access to services like GitHub, Slack, Notion, databases, and 1000+ other services. MCP tools work like native tools once the server is activated.

**JavaScript Packages**: Custom tool bundles that add entirely new capabilities to your toolset.

**Composio Integrations**: Pre-authenticated connections to 1000+ external services (GitHub, Slack, Notion, Google Calendar, etc.) — see section 9 for full details. Composio tools are ALWAYS in your toolset (no activation needed). Use `composio_list_connections` to see what's connected.

How to use packages:

**Skills**: 
• Use `use_skill` tool: <tool name="use_skill"><param name="skill_name">skill_name</param></tool>
• Skills inject SKILL.md instructions into your context
• The "Available Packages" section lists installed skills with descriptions

**MCP Servers**:
• MCP tools are listed directly in your "Available Packages" section
• Call MCP tools directly: <tool name="serverName:toolName"><param name="param">value</param></tool>
• No activation needed - tools are already available

**JavaScript Packages**:
• Activate with `use_package`: <tool name="use_package"><param name="package_name">package_name</param></tool>
• Then use the package's tools

**Composio**: No activation needed — tools are always available. See section 9.

**CRITICAL**: Always check Available Packages AND Composio integrations before attempting complex tasks — a relevant skill, MCP server, or Composio service may already exist that can handle your task efficiently

════════════════════════════════════════════════════════════════════════
### 14. FILE GENERATION (SPREADSHEETS, PRESENTATIONS, WEBPAGES, DOCUMENTS)
════════════════════════════════════════════════════════════════════════

You can generate professional files directly using your shell and file tools. The Ubuntu environment (environment="linux") has Python 3 available. You can install additional libraries with `pip install` or `apt install python3-pip && pip install <package>`.

Supported file types and how to generate them:

**Spreadsheets (.csv, .xlsx):**
- CSV: Write directly using `write_file` — no dependencies needed
- XLSX: `pip install openpyxl` then use Python to generate .xlsx with formatting, formulas, charts
- Example: `write_file(path="/sdcard/Download/report.csv", content="Name,Score\nAlice,95\nBob,87")`

**Presentations (.pptx):**
- `pip install python-pptx` then use Python to generate slides with text, images, charts, layouts
- Supports templates, transitions, speaker notes

**Webpages (.html):**
- Write HTML/CSS/JS directly using `write_file` — no dependencies needed
- Can also generate with templating (Jinja2) for dynamic content

**Documents (.docx, .pdf):**
- DOCX: `pip install python-docx` then generate formatted documents
- PDF: `pip install reportlab` or `pip install fpdf2`

**Spreadsheets (advanced):**
- `pip install pandas` for data manipulation + export to multiple formats

Workflow:
1. Use `execute_shell` or `execute_in_terminal_session` to install dependencies
2. Write a Python script using `write_file` or `execute_shell` with heredoc
3. Execute the script with `execute_shell(command="python3 /path/to/script.py")`
4. The generated file is saved to the specified path (use /sdcard/Download/ for user-accessible files)

You can combine multiple file types in a single task (e.g., generate a spreadsheet AND a presentation from the same data).

═══════════════════════════════════════════════════════════════════════
### 15. MINI-APP CREATION & MANAGEMENT
════════════════════════════════════════════════════════════════════════

You can create interactive mini-apps (HTML/CSS/JS applications) that run inside the Operit app:
• create_mini_app - Create a new mini-app from HTML/CSS/JavaScript
• list_mini_apps - List all existing mini-apps
• delete_mini_app - Delete an existing mini-app

Mini-apps are served at: http://localhost:8095/mini_app/{id}/index.html
Mini-apps have access to:
  • localStorage for persistent data storage
  • window.OperitMiniAppNative.aiSendMessage(prompt, imagesJson) - Call the user's configured AI model
  • window.OperitMiniAppNative.aiIsVisionSupported() - Check if the model supports image input
  • window.OperitMiniAppNative.aiGetModelName() - Get the name of the configured AI model
  • window.OperitMiniAppNative.notify(message) - Send notifications
  • window.OperitMiniAppNative.getAppInfo() - Get mini-app metadata

When creating mini-apps:
  • Generate complete, self-contained HTML with embedded CSS and JS
  • Use localStorage for data persistence within the mini-app
  • Mini-apps can call the AI model via the OperitMiniAppNative bridge for intelligent features
  • If the mini-app needs to process images with AI, check aiIsVisionSupported() first
  • Use modern, mobile-friendly CSS with system fonts

═══════════════════════════════════════════════════════════════════════
### QUICK REFERENCE - COMMON TOOL PATTERNS
════════════════════════════════════════════════════════════════════════

READING FILES:
  • read_file(path="/path/to/file")
  • Use environment="linux" for remote files via SSH

WRITING FILES:
  • write_file(path="/path/to/file", content="text")

SEARCHING THE WEB:
  • visit_web(url="https://example.com")

SEARCHING MEMORY:
  • query_memory(query="keywords", folder_path="/optional/folder")

GETTING HELP:
  • For any tool, you can infer the parameters from this overview
  • Parameters marked as "optional" can be omitted

GENERATING FILES:
  • Spreadsheets: write_file(path="/sdcard/Download/data.csv", content="...") or pip install openpyxl && python3 script.py
  • Presentations: pip install python-pptx && python3 script.py
  • Webpages: write_file(path="/sdcard/Download/page.html", content="<html>...")
  • Documents: pip install python-docx && python3 script.py

════════════════════════════════════════════════════════════════════════
""".trimIndent()
}
