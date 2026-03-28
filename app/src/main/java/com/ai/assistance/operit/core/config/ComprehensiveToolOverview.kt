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

• visit_web - Visit webpages and extract content (supports link following). **USE ONLY for scraping public data. For website interaction (login, click, navigate), use UI tools instead.**
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

════════════════════════════════════════════════════════════════════════
### 7. UI AUTOMATION TOOLS
════════════════════════════════════════════════════════════════════════

**CRITICAL EXECUTION RULES:**
1. Execute ONLY ONE UI action at a time - wait for the screenshot result before proceeding to the next action
2. After ANY UI automation tool (tap, long_press, swipe, click_element, set_input_text, press_key, capture_screenshot), you MUST take a screenshot using capture_screenshot to see the result
3. NEVER output tool syntax or parameters as plain text - you MUST actually CALL the tools via the tool calling interface
4. NEVER execute multiple commands in batch - wait for screenshot feedback after each action

• tap - Tap at screen coordinates (AFTER using: take screenshot with capture_screenshot)
• long_press - Long press at coordinates (AFTER using: take screenshot with capture_screenshot)
• swipe - Swipe gestures between coordinates (AFTER using: take screenshot with capture_screenshot)
• click_element - Click UI element by resource ID/class/content-desc (AFTER using: take screenshot with capture_screenshot)
• set_input_text - Input text in focused field (AFTER using: take screenshot with capture_screenshot)
• press_key - Press hardware/system keys (AFTER using: take screenshot with capture_screenshot)
• capture_screenshot - Take screenshot to see current screen state

════════════════════════════════════════════════════════════════════════
### 8. MATHEMATICS & CALCULATION
════════════════════════════════════════════════════════════════════════

• calculate - Evaluate mathematical expressions

════════════════════════════════════════════════════════════════════════
### 9. WORKFLOW TOOLS
════════════════════════════════════════════════════════════════════════

• get_all_workflows - List all available workflows
• get_workflow - Get workflow details
• create_workflow - Create new workflow
• update_workflow - Update existing workflow
• patch_workflow - Incrementally update workflow
• delete_workflow - Delete workflow
• trigger_workflow - Execute a workflow

════════════════════════════════════════════════════════════════════════
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

════════════════════════════════════════════════════════════════════════
### 11. TASKER INTEGRATION
════════════════════════════════════════════════════════════════════════

• trigger_tasker_event - Trigger Tasker events for automation

════════════════════════════════════════════════════════════════════════
### 12. MEDIA PROCESSING
════════════════════════════════════════════════════════════════════════

• ffmpeg_execute - Execute FFmpeg commands
• ffmpeg_info - Get FFmpeg capabilities info
• ffmpeg_convert - Convert video/audio formats

════════════════════════════════════════════════════════════════════════
### 13. PACKAGE SYSTEM (EXTENSIBILITY)
════════════════════════════════════════════════════════════════════════

Additional functionality is available through packages:
• Use use_package to activate a package
• Activating a package reveals its specific tools
• Packages can provide specialized capabilities (MCP servers, skills, etc.)

════════════════════════════════════════════════════════════════════════
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

ANDROID UI AUTOMATION:
  • get_page_info() → get UI hierarchy
  • tap(x=500, y=300) → tap coordinates
  • click_element(resourceId="com.app:id/button") → element by ID

GETTING HELP:
  • For any tool, you can infer the parameters from this overview
  • Parameters marked as "optional" can be omitted

════════════════════════════════════════════════════════════════════════
""".trimIndent()
}
