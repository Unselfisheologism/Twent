package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.skill.SkillRepository

object SystemPromptConfig {

    private const val BEHAVIOR_GUIDELINES_EN = """BEHAVIOR GUIDELINES:

**CRITICAL RULE #1 — SKILLS ARE MANDATORY WHEN RELEVANT**
- When a skill exists for your task, you MUST use it. Do NOT try to figure it out yourself.
- Use `use_skill` tool: `<tool name="use_skill"><param name="skill_name">skill_name</param></tool>`
- After calling `use_skill`, you will receive instructions. FOLLOW THEM STEP-BY-STEP.
- Do NOT stop after calling `use_skill`. Continue working immediately.

**CRITICAL RULE #2 — CALL TOOLS WITH CONFIDENCE**
- Only call a tool when you are certain the parameters are correct.
- If ANY parameter is uncertain → ask the user to clarify first.
- After tool execution: process results and continue or ask for clarification. Do NOT blindly retry the same approach after a failure.

**CRITICAL RULE #3 — TOOL CALL ACCURACY OVER SPEED**
- It is better to ask a clarifying question than to call a tool with wrong parameters.
- A failed tool call wastes context tokens and frustrates the user.
- Simple questions do NOT need tools — answer directly.

**TOOL COVERAGE RULE — EXTERNAL TOOLS TAKE PRIORITY**
- External tools (Composio, MCP, Skills) are more capable than built-in tools.
- When an external tool provides the same capability, use it instead of built-in tools.
- How to use external tools:
  - **Skills**: Use the `use_skill` tool. Listed in Available Packages with descriptions.
  - **MCP Servers**: Tools listed directly in Available Packages. Call directly (e.g., `serverName:toolName`).
  - **Composio**: Use `composio_execute_tool`. Always available, no activation needed.

**BUILT-IN TOOL SELECTION (EQUAL PRIORITY)**
When no external tool applies, choose by scenario:
- `launch_url_in_browser` — Open a real browser to a website
- `open_app` — Open an installed app
- `mcp_tool(server="ddg-search", tool="search", params={"query": "query", "max_results": 10})` — Web search via DuckDuckGo MCP server. USE THIS FOR ALL WEB SEARCH!
- `http_request` — Direct API calls
- `read_file` / `apply_file` / `execute_shell` — File and shell operations

**OTHER RULES**
- Parallel tool calls: For information gathering, call all needed tools in a single turn.
- Be concise. Do not repeat previous steps.
- End every response with exactly ONE of:
  1. Tool call (must be the last thing in the response)
  2. Task complete: `<status type="complete"></status>`
  3. Wait for user: `<status type="wait_for_user_need"></status>`
- These three are mutually exclusive.

**Mini-App Creation**: Use `create_mini_app` for interactive tools, calculators, dashboards.

**File Generation**: Generate professional files using Python + shell tools. Save to /sdcard/Download/."""

    private const val TOOL_USAGE_GUIDELINES_EN = """TOOL USAGE:
- When calling a tool, the user will see your response, then automatically receive tool results.
- CORRECT XML FORMAT for ALL tool calls:
  <tool name="tool_name">
  <param name="parameter_name">parameter_value</param>
  </tool>
- Example for execute_shell:
  <tool name="execute_shell">
  <param name="command">googlesearch --query 'search query'</param>
  </tool>
- Example for use_skill:
  <tool name="use_skill">
  <param name="skill_name">skill-name-here</param>
  </tool>
- Put a newline before <tool> tags. Opening tag must be at start of line.
- For skills: CALL THE TOOL IMMEDIATELY. Do not describe what you will do first.
- After tool execution: Continue working. Do NOT stop. Process results and take next action.
- For complex tasks: Use tools step-by-step. After each tool, explain results and suggest next steps."""

    private const val PACKAGE_SYSTEM_GUIDELINES_EN = """
PACKAGE SYSTEM — SKILLS, MCP SERVERS, AND COMPOSIO INTEGRATIONS
Packages and integrations extend your capabilities beyond built-in tools:

1. **Skills**: Reusable automation workflows with step-by-step guides for specific tasks (e.g., GitHub operations, API integrations, screen automation patterns). When activated, a skill's guide and tools become available.
2. **MCP (Model Context Protocol) Servers**: External service integrations that expose tools from remote APIs (e.g., GitHub, databases, cloud services). MCP tools work like native tools once activated.
3. **JavaScript Packages**: Custom tool bundles that add entirely new capabilities.
4. **Composio Integrations**: Pre-authenticated connections to 1000+ external services (GitHub, Slack, Notion, Google Calendar, etc.) that the user has connected in the Integrations page. Unlike MCP servers (which are packages), Composio tools are always in your toolset — use `composio_list_toolkits` to discover available services and `composio_execute_tool` to call authenticated API actions.

To use a package (skills, MCP, JS), activate it with:
  <tool name="use_package">
  <param name="package_name">package_name_here</param>
  </tool>
This will show you all the tools in the package and how to use them.
Only after activating a package, you can use its tools directly.

For Composio: No activation needed — tools are always available. Use `composio_list_connections` to see connected services.
**Pro Tip**: Always check Available Packages AND Composio integrations before attempting complex tasks — a relevant skill, MCP server, or Composio service may already exist."""

    private const val TOOL_USAGE_BRIEF_EN = """
When calling a tool, use the appropriate function call format. After tool execution, continue working."""

    private const val TOOL_USAGE_GUIDELINES_TOOL_CALL_FALLBACK_EN = """
TOOL CALL API MODE: You should use the native tool-call interface (function calls).
DO NOT write XML tool tags like <tool name="..."> in your response — those are ignored in this mode.
ONLY respond with a function_call. If you are unsure about a parameter value, do NOT guess.
Instead, respond with plain text asking the user to clarify the parameter.
After tool execution: continue working and take the next action."""

    private const val PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_EN = """
PACKAGE SYSTEM — SKILLS, MCP SERVERS, AND COMPOSIO INTEGRATIONS
Packages and integrations extend your capabilities beyond built-in tools:
1. **Skills**: Reusable automation workflows with step-by-step guides for specific tasks.
2. **MCP (Model Context Protocol) Servers**: External service integrations that expose tools from remote APIs.
3. **JavaScript Packages**: Custom tool bundles that add new capabilities.
4. **Composio Integrations**: Pre-authenticated connections to 1000+ external services (GitHub, Slack, Notion, etc.) that the user has connected in the Integrations page. Composio tools are always in your toolset — use `composio_list_toolkits` to discover services and `composio_execute_tool` to call authenticated API actions.

To use a package (skills, MCP, JS), call the use_package function with the package_name parameter.
This will show you all the tools in the package and how to use them.
Only after activating a package, you can use its tools directly.
For Composio: No activation needed — tools are always available.
**Pro Tip**: Always check Available Packages AND Composio integrations before attempting complex tasks."""

    /** Base system prompt template — English only */
    val SYSTEM_PROMPT_TEMPLATE =
"""
BEGIN_SELF_INTRODUCTION_SECTION

THINKING_GUIDANCE_SECTION

$BEHAVIOR_GUIDELINES_EN

WEB_WORKSPACE_GUIDELINES_SECTION

Formula formatting: For math formulas, use $ $ for inline LaTeX and $$ $$ for display LaTeX.

TOOL_USAGE_GUIDELINES_SECTION

PACKAGE_SYSTEM_GUIDELINES_SECTION

ACTIVE_PACKAGES_SECTION

AVAILABLE_TOOLS_SECTION""".trimIndent()

    /**
     * Prompt for a subtask agent that should be strictly task-focused,
     * without memory or emotional attachment. It is forbidden from waiting for user input.
     */
    val SUBTASK_AGENT_PROMPT_TEMPLATE =
        """
        BEHAVIOR GUIDELINES:
        - You are a subtask-focused AI agent. Your only goal is to complete the assigned task efficiently and accurately.
        - You have no memory of past conversations, user preferences, or personality. You must not exhibit any emotion or personality.
        - **CRITICAL EFFICIENCY MANDATE: PARALLEL TOOL CALLING**: For any information-gathering task (e.g., reading multiple files, searching for different things), you **MUST** call all necessary tools in a single turn. **Do not call them sequentially, as this will result in many unnecessary conversation turns and is considered a failure.** This is a strict efficiency requirement.
        - **Summarize and Conclude**: If the task requires using tools to gather information (e.g., reading files, searching), you **MUST** process that information and provide a concise, conclusive summary as your final output. Do not output raw data. Your final answer is the only thing passed to the next agent.
        - For data modification (e.g., writing files), you must still only call one tool at a time.
        - Be concise and factual. Avoid lengthy explanations.
        - End every response in exactly ONE of the following ways:
          1. Tool Call: To perform an action. A tool call must be the absolute last thing in your response.
          2. Task Complete: Use `<status type="complete"></status>` when the entire task is finished.
        - **CRITICAL RULE**: You are NOT allowed to use `<status type="wait_for_user_need"></status>`. If you cannot proceed without user input, you must use `<status type="complete"></status>` and the calling system will handle the user interaction.

        THINKING_GUIDANCE_SECTION

        TOOL_USAGE_GUIDELINES_SECTION

        PACKAGE_SYSTEM_GUIDELINES_SECTION

        ACTIVE_PACKAGES_SECTION

        AVAILABLE_TOOLS_SECTION
        """.trimIndent()

  /**
   * Applies custom prompt replacements from ApiPreferences to the system prompt
   *
   * @param systemPrompt The original system prompt
   * @param customIntroPrompt The custom introduction prompt (about Operit)
   * @return The system prompt with custom prompts applied
   */
  fun applyCustomPrompts(
          systemPrompt: String,
          customIntroPrompt: String
  ): String {
    // Replace the default prompts with custom ones if provided and non-empty
    var result = systemPrompt

    if (customIntroPrompt.isNotEmpty()) {
      result = result.replace("BEGIN_SELF_INTRODUCTION_SECTION", customIntroPrompt)
    }

    return result
  }

  /**
   * Generates the system prompt with dynamic package information
   *
   * @param packageManager The PackageManager instance to get package information from
   * @param workspacePath The current workspace path, if available.
   * @param thinkingGuidance Whether thinking guidance is enabled
   * @param customSystemPromptTemplate Custom system prompt template (empty means use built-in)
   * @param enableTools Whether tools are enabled
   * @param enableMemoryQuery Whether the AI is allowed to query memories.
   * @param hasImageRecognition Whether a backend image recognition service is configured
   * @param chatModelHasDirectImage Whether the chat model has direct image capability
   * @return The complete system prompt with package information
   */
fun getSystemPrompt(
          packageManager: PackageManager,
          workspacePath: String? = null,
          workspaceEnv: String? = null,
          safBookmarkNames: List<String> = emptyList(),
          useEnglish: Boolean = true,
          thinkingGuidance: Boolean = false,
          customSystemPromptTemplate: String = "",
          enableTools: Boolean = true,
          enableMemoryQuery: Boolean = true,
          hasImageRecognition: Boolean = false,
          chatModelHasDirectImage: Boolean = false,
          hasAudioRecognition: Boolean = false,
          hasVideoRecognition: Boolean = false,
          chatModelHasDirectVideo: Boolean = false,
          useToolCallApi: Boolean = false,
          /**
           * Brain-level injection (memory, persona, mid-session notes, etc.)
           * Appended AFTER all tool guidance — hermes-agent layering pattern.
           * Unlike customSystemPromptTemplate (which replaces the template),
           * brainPromptInjection always preserves the base prompt's tool instructions.
           */
          brainPromptInjection: String = ""
  ): String {
    val importedPackages = packageManager.getImportedPackages()
    val mcpServers = packageManager.getAvailableServerPackages()
    val skillPackages = try {
        SkillRepository.getInstance(
            com.ai.assistance.operit.core.application.OperitApplication.instance.applicationContext
        ).getAiVisibleSkillPackages()
    } catch (_: Exception) {
        emptyMap()
    }

    // Build the available packages section
    val packagesSection = StringBuilder()

    // Filter out imported packages that no longer exist in availablePackages
    val validImportedPackages = importedPackages.filter { packageName ->
        packageManager.getPackageTools(packageName) != null
    }

    // Check if any packages (JS, MCP, or Skills) are available
    val hasPackages = validImportedPackages.isNotEmpty() || mcpServers.isNotEmpty() || skillPackages.isNotEmpty()

    if (hasPackages) {
      packagesSection.appendLine("Available packages:")

      // List imported JS packages (only those that still exist)
      for (packageName in validImportedPackages) {
        val packageTools = packageManager.getPackageTools(packageName)
        if (packageTools != null) {
          val resolvedDescription = try {
              packageTools.description.resolve("en")
          } catch (_: Exception) {
              packageTools.description.toString()
          }
          packagesSection.appendLine("- $packageName : $resolvedDescription")
        }
      }

      // List available MCP servers with their tools
      val mcpToolRegistry = mutableListOf<Pair<String, com.ai.assistance.operit.core.tools.mcp.MCPTool>>()

      for ((serverName, serverConfig) in mcpServers) {
        packagesSection.appendLine("- $serverName : ${serverConfig.description}")

        // Try to discover and list MCP tools
        try {
          val context = com.ai.assistance.operit.core.application.OperitApplication.instance.applicationContext
          val mcpPackage = com.ai.assistance.operit.core.tools.mcp.MCPPackage.fromServer(context, serverConfig)

          if (mcpPackage != null && mcpPackage.mcpTools.isNotEmpty()) {
            packagesSection.appendLine("  Available tools:")
            mcpPackage.mcpTools.forEach { tool ->
              val toolFullName = "$serverName:${tool.name}"
              packagesSection.appendLine("  - $toolFullName: ${tool.description}")
              if (tool.parameters.isNotEmpty()) {
                packagesSection.appendLine("    Parameters:")
                tool.parameters.forEach { param ->
                  val requiredText = if (param.required) "(required)" else "(optional)"
                  packagesSection.appendLine("    - ${param.name} $requiredText: ${param.description}")
                }
              }
              // Store for registration
              mcpToolRegistry.add(Pair(serverName, tool))
            }
          } else {
            packagesSection.appendLine("  [No tools available or server not responding]")
          }
        } catch (e: Exception) {
          packagesSection.appendLine("  [Tools available after server activation]")
        }
      }

      // Register MCP tools directly with the AI tool handler
      if (mcpToolRegistry.isNotEmpty()) {
        try {
          val context = com.ai.assistance.operit.core.application.OperitApplication.instance.applicationContext
          val aiToolHandler = com.ai.assistance.operit.core.tools.AIToolHandler.getInstance(context)
          val mcpManager = com.ai.assistance.operit.core.tools.mcp.MCPManager.getInstance(context)
          val mcpToolExecutor = com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor(context, mcpManager)

          mcpToolRegistry.forEach { (serverName, tool) ->
            val toolFullName = "$serverName:${tool.name}"
            // Register the MCP tool directly
            aiToolHandler.registerTool(
              name = toolFullName,
              executor = mcpToolExecutor
            )
          }
        } catch (e: Exception) {
          // If registration fails, tools will be available after use_package
        }
      }

      // List available Skills with capabilities
      for ((skillName, skill) in skillPackages) {
        val description = if (skill.description.isNotBlank()) skill.description else "No description"
        packagesSection.appendLine("- $skillName : $description")
        packagesSection.appendLine("  [Use 'use_skill' tool to load this skill]")
      }
    } else {
      packagesSection.appendLine("No packages are currently available.")
    }

    // Information about using packages
    packagesSection.appendLine()
    packagesSection.appendLine("HOW TO USE PACKAGES:")
packagesSection.appendLine("- For MCP servers: Call tools directly (e.g., serverName:toolName)")
        packagesSection.appendLine("- For skills: Use the use_skill tool with skill_name parameter")
        packagesSection.appendLine("- For JavaScript packages: Use the use_package tool with package_name parameter")
        packagesSection.appendLine("")
        packagesSection.appendLine("IMPORTANT: ddg-search is the DEFAULT web search. If it fails or rate limits, AUTOMATICALLY try another available search method (tavily, exa, apify, serper, perplexity, or any other available web search skill/MCP server) WITHOUT asking user permission.")

    // Select appropriate template (English only — Chinese removed)
    val templateToUse = if (customSystemPromptTemplate.isNotEmpty()) {
        customSystemPromptTemplate
    } else {
        SYSTEM_PROMPT_TEMPLATE
    }

    // Generate workspace guidelines (English only)
    val workspaceGuidelines = getWorkspaceGuidelines(workspacePath, workspaceEnv)

    // Build prompt with appropriate sections
    var prompt = templateToUse
        .replace("ACTIVE_PACKAGES_SECTION", if (enableTools) packagesSection.toString() else "")
        .replace("WEB_WORKSPACE_GUIDELINES_SECTION", workspaceGuidelines)

    // Add thinking guidance section if enabled (English only)
    prompt =
            if (thinkingGuidance) {
                prompt.replace("THINKING_GUIDANCE_SECTION", THINKING_GUIDANCE_PROMPT)
            } else {
                prompt.replace("THINKING_GUIDANCE_SECTION", "")
            }

    // Determine the available tools string (English only)
    val availableToolsEn = if (useToolCallApi) "" else (
        if (enableMemoryQuery) {
            MEMORY_TOOLS_EN +
                getAvailableToolsEn(
                    hasImageRecognition = hasImageRecognition,
                    chatModelHasDirectImage = chatModelHasDirectImage,
                    hasAudioRecognition = hasAudioRecognition,
                    hasVideoRecognition = hasVideoRecognition,
                    chatModelHasDirectAudio = chatModelHasDirectAudio,
                    chatModelHasDirectVideo = chatModelHasDirectVideo,
                    safBookmarkNames = safBookmarkNames
                )
        } else {
            getAvailableToolsEn(
                hasImageRecognition = hasImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasAudioRecognition = hasAudioRecognition,
                hasVideoRecognition = hasVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo,
                safBookmarkNames = safBookmarkNames
            )
        }
    )

    // Handle tools disable/enable
    if (enableTools) {
        if (useToolCallApi) {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", TOOL_USAGE_GUIDELINES_TOOL_CALL_FALLBACK_EN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_EN)
                .replace("AVAILABLE_TOOLS_SECTION", "")
        } else {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", TOOL_USAGE_GUIDELINES_EN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", PACKAGE_SYSTEM_GUIDELINES_EN)
                .replace("AVAILABLE_TOOLS_SECTION", ComprehensiveToolOverview.TOOL_OVERVIEW_EN + availableToolsEn)
        }
    } else {
        if (enableMemoryQuery) {
            // Only memory tools are available, package system is disabled
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", TOOL_USAGE_GUIDELINES_EN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
                .replace("AVAILABLE_TOOLS_SECTION", MEMORY_TOOLS_EN)
        } else {
            // Remove all guidance sections when tools and memory are disabled
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", "")
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
                .replace("AVAILABLE_TOOLS_SECTION", "")
                .replace(BEHAVIOR_GUIDELINES_EN, "")
                .replace(workspaceGuidelines, "")
        }
    }

    // Clean up multiple consecutive blank lines (replace 3+ newlines with 2)
    prompt = prompt.replace(Regex("\n{3,}"), "\n\n")

    // FIX 1: Append brain injection AFTER all tool guidance (hermes-agent layering)
    // Brain content (memory, persona, mid-session notes) comes LAST, never replaces
    // the base template. This preserves tool instructions as the highest priority.
    if (brainPromptInjection.isNotEmpty()) {
        prompt = prompt.trimEnd() + "\n\n" + brainPromptInjection
    }

    return prompt
  }

  /**
   * Generates the dynamic web workspace guidelines based on the provided path.
   * English only — Chinese removed.
   *
   * @param workspacePath The current path of the workspace. Null if not bound.
   * @return A string containing the appropriate workspace guidelines.
   */
  private fun getWorkspaceGuidelines(workspacePath: String?, workspaceEnv: String?): String {
      val envLabel = workspaceEnv?.trim().orEmpty()
      val shouldShowEnv = envLabel.isNotBlank() && !envLabel.equals("android", ignoreCase = true)
      return if (workspacePath != null) {
          """
          WEB WORKSPACE GUIDELINES:
          - Your working directory, `$workspacePath`${if (shouldShowEnv) " (environment=$envLabel)" else ""}, is automatically set up as a web server root.
          - Use the `apply_file` tool to create web files (HTML/CSS/JS).
          - The main file must be `index.html` for user previews.
          - It's recommended to split code into multiple files for better stability and maintainability.
          - For more complex projects, consider creating `js` and `css` folders and organizing files accordingly.
          - Always use relative paths for file references.
          ${if (shouldShowEnv) "- When reading/writing workspace files via tools, pass `environment=\"$envLabel\"` and use absolute paths like `/...`." else ""}
          - **Best Practice for Code Modifications**: Before modifying any file, use `grep_code` and `grep_context` to locate and understand relevant code with surrounding context. This ensures you understand the codebase structure before making changes.
          """.trimIndent()
      } else {
          """
          WEB WORKSPACE GUIDELINES:
          - A web workspace is not yet configured for this chat. To enable web development features, please prompt the user to click the 'Web' button in the top-right corner of the app to bind a workspace directory.
          """.trimIndent()
      }
  }

  /**
   * Generates the system prompt with dynamic package information and custom prompts
   *
   * @param packageManager The PackageManager instance to get package information from
   * @param workspacePath The current workspace path, if available.
   * @param customIntroPrompt Custom introduction prompt text
   * @param thinkingGuidance Whether thinking guidance is enabled
   * @param customSystemPromptTemplate Custom system prompt template (empty means use built-in)
   * @param enableTools Whether tools are enabled
   * @param enableMemoryQuery Whether the AI is allowed to query memories.
   * @param hasImageRecognition Whether image recognition service is configured
   * @param chatModelHasDirectImage Whether the chat model has direct image capability
   * @return The complete system prompt with custom prompts and package information
   */
  fun getSystemPromptWithCustomPrompts(
          packageManager: PackageManager,
          workspacePath: String?,
          workspaceEnv: String? = null,
          safBookmarkNames: List<String> = emptyList(),
          customIntroPrompt: String,
          useEnglish: Boolean = true,
          thinkingGuidance: Boolean = false,
          customSystemPromptTemplate: String = "",
          enableTools: Boolean = true,
          enableMemoryQuery: Boolean = true,
          hasImageRecognition: Boolean = false,
          chatModelHasDirectImage: Boolean = false,
          hasAudioRecognition: Boolean = false,
          hasVideoRecognition: Boolean = false,
          chatModelHasDirectAudio: Boolean = false,
          chatModelHasDirectVideo: Boolean = false,
          useToolCallApi: Boolean = false,
          brainPromptInjection: String = ""
  ): String {
    // Get the base system prompt
    val basePrompt = getSystemPrompt(
        packageManager = packageManager,
        workspacePath = workspacePath,
        workspaceEnv = workspaceEnv,
        safBookmarkNames = safBookmarkNames,
        useEnglish = useEnglish,
        thinkingGuidance = thinkingGuidance,
        customSystemPromptTemplate = customSystemPromptTemplate,
        enableTools = enableTools,
        enableMemoryQuery = enableMemoryQuery,
        hasImageRecognition = hasImageRecognition,
        chatModelHasDirectImage = chatModelHasDirectImage,
        hasAudioRecognition = hasAudioRecognition,
        hasVideoRecognition = hasVideoRecognition,
        chatModelHasDirectAudio = chatModelHasDirectAudio,
        chatModelHasDirectVideo = chatModelHasDirectVideo,
        useToolCallApi = useToolCallApi,
        brainPromptInjection = brainPromptInjection
    )

    // Apply custom prompts
    return applyCustomPrompts(basePrompt, customIntroPrompt)
  }

  /** Original method for backward compatibility */
  fun getSystemPrompt(packageManager: PackageManager): String {
    return getSystemPrompt(
        packageManager = packageManager,
        workspacePath = null,
        workspaceEnv = null,
        safBookmarkNames = emptyList(),
        useEnglish = true,
        thinkingGuidance = false,
        customSystemPromptTemplate = "",
        enableTools = true,
        enableMemoryQuery = true,
        hasImageRecognition = false,
        chatModelHasDirectImage = false,
        hasAudioRecognition = false,
        hasVideoRecognition = false,
        chatModelHasDirectAudio = false,
        chatModelHasDirectVideo = false,
        useToolCallApi = false,
        brainPromptInjection = ""
    )
  }

  private const val THINKING_GUIDANCE_PROMPT =
"""
- In your thoughts, deconstruct the user's request, consider alternatives, anticipate outcomes, and reflect on the best strategy. Formulate a precise action plan. Your plan should be efficient and use multiple tools in parallel for information gathering whenever possible.
- **CRITICAL**: Before planning manual steps, always check if there are relevant packages (skills, MCP servers) in the Available Packages section OR Composio integrations connected by the user. Skills contain pre-built workflows, MCP servers provide direct API access, and Composio integrations give authenticated access to 1000+ external services. These can dramatically simplify your task. Ask: "Is there a skill, MCP tool, or Composio integration that handles this?"
- The user will see your thoughts but cannot reply to them directly. This block is NOT saved in the chat history, so your final answer must be self-contained.
- The think block must be immediately followed by your final answer or tool call without any newlines.
- **CRITICAL REMINDER:** Even if previous messages in the chat history do not show a think block, you MUST include one in your current response. This is a mandatory instruction for this conversation mode."""

    private fun getAvailableToolsEn(
        hasImageRecognition: Boolean,
        chatModelHasDirectImage: Boolean,
        hasAudioRecognition: Boolean,
        hasVideoRecognition: Boolean,
        chatModelHasDirectAudio: Boolean,
        chatModelHasDirectVideo: Boolean,
        safBookmarkNames: List<String>
    ): String {
        return SystemToolPrompts.generateToolsPromptEn(
            hasBackendImageRecognition = hasImageRecognition,
            includeMemoryTools = false,
            chatModelHasDirectImage = chatModelHasDirectImage,
            hasBackendAudioRecognition = hasAudioRecognition,
            hasBackendVideoRecognition = hasVideoRecognition,
            chatModelHasDirectAudio = chatModelHasDirectAudio,
            chatModelHasDirectVideo = chatModelHasDirectVideo,
            safBookmarkNames = safBookmarkNames
        )
    }

    private val MEMORY_TOOLS_EN: String
        get() = SystemToolPrompts.memoryTools.toString()
}
