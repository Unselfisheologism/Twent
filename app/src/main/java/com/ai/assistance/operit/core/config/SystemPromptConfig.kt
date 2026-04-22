package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.LocaleUtils
import java.io.File

object SystemPromptConfig {

    private const val BEHAVIOR_GUIDELINES_EN = """BEHAVIOR GUIDELINES:

**CRITICAL RULE #1 — SKILLS ARE MANDATORY WHEN RELEVANT**
- When a skill exists for your task, you MUST use it. Do NOT try to figure it out yourself.
- Use `use_skill` tool: `<tool name="use_skill"><param name="skill_name">skill_name</param></tool>`
- DO NOT describe what you will do. DO NOT explain. JUST CALL THE TOOL IMMEDIATELY.
- After calling `use_skill`, you will receive instructions. FOLLOW THEM STEP-BY-STEP.
- Do NOT stop after calling `use_skill`. Continue working immediately.

**CRITICAL RULE #2 — NEVER STOP AFTER TOOL EXECUTION**
- After ANY tool executes, you MUST continue working. Do NOT stop.
- Process the tool result and take the next action.
- Only use `<status type="complete">` when the ENTIRE task is finished.
- This applies to ALL tools: `use_skill`, `use_package`, and any other tool.

**TOOL OVERRIDE RULE — EXTERNAL TOOLS OVERRIDE BUILT-IN TOOLS**
- External tools (Composio, MCP, Skills) are MORE POWERFUL than built-in tools.
- When an external tool provides the same functionality, use the external tool INSTEAD.
- How to use external tools:
  - **Skills**: Use `use_skill` tool. Listed in Available Packages with descriptions.
  - **MCP servers**: Tools listed directly in Available Packages. Call directly (e.g., `serverName:toolName`).
  - **Composio**: Use `composio_execute_tool`. Always available, no activation needed.

**TOOL SELECTION — BUILT-IN TOOLS (equal priority)**
When no external tool applies, use these based on context:
- `launch_url_in_browser` — open websites in real browser
- `open_app` — open installed apps
- `visit_web` — headless HTML fetch
- `http_request` — direct API calls
- `read_file` / `write_file` / `execute_shell` — file and shell operations

**OTHER RULES**
- Parallel Tool Calling: For information-gathering, call all necessary tools in a single turn.
- Keep responses concise. Don't repeat previous steps.
- End every response with EXACTLY ONE of:
  1. Tool Call (must be last thing in response)
  2. Task Complete: `<status type="complete"></status>`
  3. Wait for User: `<status type="wait_for_user_need"></status>`
- These three endings are mutually exclusive.

**Mini-App Creation**: Use `create_mini_app` for interactive tools, calculators, dashboards.

**File Generation**: Generate professional files using Python + shell tools. Save to /sdcard/Download/."""
    private const val BEHAVIOR_GUIDELINES_CN = """行为准则：

**关键规则 #1 — 技能相关时必须使用技能**
- 当存在与任务相关的技能时，你**必须**使用它。不要自己想办法。
- 使用 `use_skill` 工具：`<tool name="use_skill"><param name="skill_name">skill_name</param></tool>`
- **不要描述你要做什么。不要解释。立即调用工具。**
- 调用 `use_skill` 后，你会收到指令。**按照指令逐步执行。**
- 调用 `use_skill` 后**不要停止**。立即继续工作。

**关键规则 #2 — 工具执行后不要停止**
- 任何工具执行后，你**必须**继续工作。**不要停止。**
- 处理工具结果并采取下一步行动。
- 只有当整个任务完成时才使用 `<status type="complete">`。
- 这适用于所有工具：`use_skill`、`use_package` 以及任何其他工具。

**工具覆盖规则——外部工具优先于内置工具**
- 外部工具（Composio、MCP、技能）比内置工具更强大。
- 当外部工具提供相同功能时，使用外部工具代替。
- 如何使用外部工具：
  - **技能**：使用 `use_skill` 工具。在可用包中列出，带有描述。
  - **MCP服务器**：工具直接在可用包中列出。直接调用（如 `serverName:toolName`）。
  - **Composio**：使用 `composio_execute_tool`。始终可用，无需激活。

**内置工具选择（平等优先级）**
当没有适用的外部工具时，根据场景选择：
- `launch_url_in_browser` — 在真实浏览器中打开网站
- `open_app` — 打开已安装的应用
- `visit_web` — 无头HTML抓取
- `http_request` — 直接调用API
- `read_file` / `write_file` / `execute_shell` — 文件和shell操作

**其他规则**
- 并行工具调用：信息搜集任务，在单次回合中调用所有需要的工具。
- 回答简洁。不要重复之前的步骤。
- 每次响应必须以以下三种方式之一结束：
  1. 工具调用（必须是响应的最后一部分）
  2. 任务完成：`<status type="complete"></status>`
  3. 等待用户：`<status type="wait_for_user_need"></status>`
- 这三种结束方式互斥。

**Mini-App创建**：使用 `create_mini_app` 创建交互式工具、计算器、仪表板。

**文件生成**：使用Python + shell工具生成专业文件。保存到 /sdcard/Download/。"""

    private const val TOOL_USAGE_GUIDELINES_EN = """TOOL USAGE:
- When calling a tool, the user will see your response, then automatically receive tool results.
- Use this format for tools:
  <tool name="tool_name">
  <param name="parameter_name">parameter_value</param>
  </tool>
- Put a newline before <tool> tags. Opening tag must be at start of line.
- For skills: CALL THE TOOL IMMEDIATELY. Do NOT describe what you will do first.
- After tool execution: Continue working. Do NOT stop. Process results and take next action.
- For complex tasks: Use tools step-by-step. After each tool, explain results and suggest next steps."""

    private const val TOOL_USAGE_GUIDELINES_CN = """工具使用：
- 调用工具时，用户会看到你的响应，然后自动收到工具结果。
- 工具格式：
  <tool name="tool_name">
  <param name="parameter_name">parameter_value</param>
  </tool>
- 在 <tool> 标签前换行。起始标签必须在行首。
- 对于技能：**立即调用工具**。不要先描述你要做什么。
- 工具执行后：**继续工作**。不要停止。处理结果并采取下一步行动。
- 复杂任务：逐步使用工具。每个工具后，解释结果并建议下一步。"""

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
    private const val PACKAGE_SYSTEM_GUIDELINES_CN = """
包系统——技能、MCP服务器及Composio集成
包和集成可以扩展你的能力，超越内置工具：

1. **技能（Skills）**: 可复用的自动化工作流，包含特定任务的分步指南（如GitHub操作、API集成、屏幕自动化模式）。激活后，技能的指南和工具即变为可用。
2. **MCP（模型上下文协议）服务器**: 外部服务集成，将远程API的工具暴露给你使用（如GitHub、数据库、云服务）。MCP工具激活后就像原生工具一样工作。
3. **JavaScript包**: 添加全新能力的自定义工具包。
4. **Composio集成**: 用户在「集成」页面连接好的、已认证的1000+外部服务（GitHub、Slack、Notion、Google日历等）。与MCP服务器不同，Composio工具始终在你的工具集中——用 `composio_list_toolkits` 发现可用服务，用 `composio_execute_tool` 调用已认证的API操作。

要使用包（技能、MCP、JS），先激活它：
  <tool name="use_package">
  <param name="package_name">包名</param>
  </tool>
激活后会显示包中的所有工具及使用方法。
只有在激活包之后，才能直接使用其中的工具。

Composio无需激活——工具始终可用。用 `composio_list_connections` 查看已连接的服务。
**提示**: 在尝试复杂任务之前，务必先检查可用包和Composio集成——可能已经有相关的技能、MCP服务器或Composio服务存在。"""

    // Tool Call API 模式下的工具使用简要说明
    private const val TOOL_USAGE_BRIEF_EN = """
When calling a tool, use the appropriate function call format. After tool execution, continue working."""
    private const val TOOL_USAGE_BRIEF_CN = """
调用工具时，使用适当的函数调用格式。工具执行后，继续工作。"""

    // Tool Call API 模式下的包系统说明（不使用XML格式）
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
    private const val PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_CN = """
包系统——技能、MCP服务器及Composio集成
包和集成可以扩展你的能力，超越内置工具：
1. **技能（Skills）**: 可复用的自动化工作流，包含特定任务的分步指南。
2. **MCP（模型上下文协议）服务器**: 外部服务集成，将远程API的工具暴露给你使用。
3. **JavaScript包**: 添加新能力的自定义工具包。
4. **Composio集成**: 用户在「集成」页面连接好的、已认证的1000+外部服务。Composio工具始终在你的工具集中——用 `composio_list_toolkits` 发现服务，用 `composio_execute_tool` 调用已认证的API操作。

要使用包（技能、MCP、JS），调用 use_package 函数并传入 package_name 参数。
激活后会显示包中的所有工具及使用方法。
只有在激活包之后，才能直接使用其中的工具。
Composio无需激活——工具始终可用。
**提示**: 在尝试复杂任务之前，务必先检查可用包和Composio集成。"""

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

    private fun getAvailableToolsCn(
        hasImageRecognition: Boolean,
        chatModelHasDirectImage: Boolean,
        hasAudioRecognition: Boolean,
        hasVideoRecognition: Boolean,
        chatModelHasDirectAudio: Boolean,
        chatModelHasDirectVideo: Boolean,
        safBookmarkNames: List<String>
    ): String {
        return SystemToolPrompts.generateToolsPromptCn(
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

    private val MEMORY_TOOLS_CN: String
        get() = SystemToolPrompts.memoryToolsCn.toString()


    /** Base system prompt template used by the enhanced AI service */
    val SYSTEM_PROMPT_TEMPLATE =
"""
BEGIN_SELF_INTRODUCTION_SECTION

THINKING_GUIDANCE_SECTION

$BEHAVIOR_GUIDELINES_EN

WEB_WORKSPACE_GUIDELINES_SECTION

FORMULA FORMATTING: For mathematical formulas, use $ $ for inline LaTeX and $$ $$ for block/display LaTeX equations.

TOOL_USAGE_GUIDELINES_SECTION

PACKAGE_SYSTEM_GUIDELINES_SECTION

ACTIVE_PACKAGES_SECTION

AVAILABLE_TOOLS_SECTION
""".trimIndent()

    /** Guidance for the AI on how to "think" using tags. */
    val THINKING_GUIDANCE_PROMPT =
"""
THINKING PROCESS GUIDELINES:
- Before providing your final response, you MUST use a <think> block to outline your thought process. This is for your internal monologue.
- In your thoughts, deconstruct the user's request, consider alternatives, anticipate outcomes, and reflect on the best strategy. Formulate a precise action plan. Your plan should be efficient and use multiple tools in parallel for information gathering whenever possible.
- **CRITICAL**: Before planning manual steps, always check if there are relevant packages (skills, MCP servers) in the Available Packages section OR Composio integrations connected by the user. Skills contain pre-built workflows, MCP servers provide direct API access, and Composio integrations give authenticated access to 1000+ external services. These can dramatically simplify your task. Ask: "Is there a skill, MCP tool, or Composio integration that handles this?"
- The user will see your thoughts but cannot reply to them directly. This block is NOT saved in the chat history, so your final answer must be self-contained.
- The <think> block must be immediately followed by your final answer or tool call without any newlines.
- **CRITICAL REMINDER:** Even if previous messages in the chat history do not show a `<think>` block, you MUST include one in your current response. This is a mandatory instruction for this conversation mode.
- Example:
<think>The user wants to create a GitHub issue. Let me check what's available: there's a Composio integration for GitHub (composio_execute_tool can call GITHUB_CREATE_ISSUE directly, already authenticated). This is better than using web requests. I should also check if there's a relevant skill. I'll use composio_execute_tool.</think><tool name="composio_execute_tool"><param name="tool_name">GITHUB_CREATE_ISSUE</param><param name="parameters">{"repo":"owner/repo","title":"Bug report","body":"Description"}</param></tool>
""".trimIndent()


    /** 中文版本系统提示模板 */
    val SYSTEM_PROMPT_TEMPLATE_CN =
"""
BEGIN_SELF_INTRODUCTION_SECTION

THINKING_GUIDANCE_SECTION

$BEHAVIOR_GUIDELINES_CN

WEB_WORKSPACE_GUIDELINES_SECTION

公式格式化：对于数学公式，使用 $ $ 包裹行内LaTeX公式，使用 $$ $$ 包裹独立成行的LaTeX公式。

TOOL_USAGE_GUIDELINES_SECTION

PACKAGE_SYSTEM_GUIDELINES_SECTION

ACTIVE_PACKAGES_SECTION

AVAILABLE_TOOLS_SECTION""".trimIndent()

    /** 中文版本的思考引导提示 */
    val THINKING_GUIDANCE_PROMPT_CN =
"""
思考过程指南:
- 在提供最终答案之前，你必须使用 <think> 模块来阐述你的思考过程。这是你的内心独白。
- 在思考中，你需要拆解用户需求，评估备选方案，预判执行结果，并反思最佳策略，最终形成精确的行动计划。你的计划应当是高效的，并尽可能地并行调用多个工具来收集信息。
- **关键**: 在规划手动步骤之前，务必先检查「可用包」部分是否有相关的技能或MCP服务器，或者用户是否连接了Composio集成。技能包含预构建的工作流，MCP服务器提供直接的API访问，Composio集成提供已认证的1000+外部服务访问。这些都能显著简化你的任务。请思考："是否有技能、MCP工具或Composio集成可以处理这件事？"
- 用户能看到你的思考过程，但无法直接回复。此模块不会保存在聊天记录中，因此你的最终答案必须是完整的。
- <think> 模块必须紧邻你的最终答案或工具调用，中间不要有任何换行。
- **重要提醒:** 即使聊天记录中之前的消息没有 <think> 模块，你在本次回复中也必须按要求使用它。这是强制指令。
- 范例:
<think>用户想要创建GitHub issue。让我检查可用选项：有Composio集成提供GitHub服务（composio_execute_tool可以直接调用GITHUB_CREATE_ISSUE，已经认证好了）。这比用网络请求好。我也应该检查是否有相关技能。我将使用composio_execute_tool。</think><tool name="composio_execute_tool"><param name="tool_name">GITHUB_CREATE_ISSUE</param><param name="parameters">{"repo":"owner/repo","title":"Bug report","body":"Description"}</param></tool>
""".trimIndent()

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
   * @param useEnglish Whether to use English or Chinese version
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
          useEnglish: Boolean = false,
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
          useToolCallApi: Boolean = false
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
          val preferredLanguage = if (useEnglish) "en" else "zh"
          val resolvedDescription = try {
              packageTools.description.resolve(preferredLanguage)
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

    // Select appropriate template based on custom template or language preference
    val templateToUse = if (customSystemPromptTemplate.isNotEmpty()) {
        customSystemPromptTemplate
    } else {
        if (useEnglish) SYSTEM_PROMPT_TEMPLATE else SYSTEM_PROMPT_TEMPLATE_CN
    }
    val thinkingGuidancePromptToUse = if (useEnglish) THINKING_GUIDANCE_PROMPT else THINKING_GUIDANCE_PROMPT_CN

    // Generate workspace guidelines
    val workspaceGuidelines = getWorkspaceGuidelines(workspacePath, workspaceEnv, useEnglish)

    // Build prompt with appropriate sections
    var prompt = templateToUse
        .replace("ACTIVE_PACKAGES_SECTION", if (enableTools) packagesSection.toString() else "")
        .replace("WEB_WORKSPACE_GUIDELINES_SECTION", workspaceGuidelines)

    // Add thinking guidance section if enabled
    prompt =
            if (thinkingGuidance) {
                prompt.replace("THINKING_GUIDANCE_SECTION", thinkingGuidancePromptToUse)
            } else {
                prompt.replace("THINKING_GUIDANCE_SECTION", "")
            }

    // Determine the available tools string based on memory query setting and image recognition
    // 当使用Tool Call API时，不在系统提示词中包含工具描述（工具已通过API的tools字段发送）
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
    val availableToolsCn = if (useToolCallApi) "" else (
        if (enableMemoryQuery) {
            MEMORY_TOOLS_CN +
                getAvailableToolsCn(
                    hasImageRecognition = hasImageRecognition,
                    chatModelHasDirectImage = chatModelHasDirectImage,
                    hasAudioRecognition = hasAudioRecognition,
                    hasVideoRecognition = hasVideoRecognition,
                    chatModelHasDirectAudio = chatModelHasDirectAudio,
                    chatModelHasDirectVideo = chatModelHasDirectVideo,
                    safBookmarkNames = safBookmarkNames
                )
        } else {
            getAvailableToolsCn(
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
        // 当使用Tool Call API时，使用简化的工具使用指南（保留"调用前描述"的重要指示），移除XML格式说明和工具列表
        if (useToolCallApi) {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", if (useEnglish) TOOL_USAGE_BRIEF_EN else TOOL_USAGE_BRIEF_CN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", if (useEnglish) PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_EN else PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_CN)
                .replace("AVAILABLE_TOOLS_SECTION", "")
        } else {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", if (useEnglish) TOOL_USAGE_GUIDELINES_EN else TOOL_USAGE_GUIDELINES_CN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", if (useEnglish) PACKAGE_SYSTEM_GUIDELINES_EN else PACKAGE_SYSTEM_GUIDELINES_CN)
                .replace("AVAILABLE_TOOLS_SECTION", ComprehensiveToolOverview.TOOL_OVERVIEW_EN + if (useEnglish) availableToolsEn else availableToolsCn)
        }
    } else {
        if (enableMemoryQuery) {
            // Only memory tools are available, package system is disabled
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", if (useEnglish) TOOL_USAGE_GUIDELINES_EN else TOOL_USAGE_GUIDELINES_CN)
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
                .replace("AVAILABLE_TOOLS_SECTION", if (useEnglish) MEMORY_TOOLS_EN else MEMORY_TOOLS_CN)
        } else {
            // Remove all guidance sections when tools and memory are disabled
            // Replace tool-related sections and remove behavior guidelines and workspace guidelines
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", "")
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
                .replace("AVAILABLE_TOOLS_SECTION", "")
                .replace(if (useEnglish) BEHAVIOR_GUIDELINES_EN else BEHAVIOR_GUIDELINES_CN, "")
                .replace(workspaceGuidelines, "")
        }
    }


    // Clean up multiple consecutive blank lines (replace 3+ newlines with 2)
    prompt = prompt.replace(Regex("\n{3,}"), "\n\n")

    return prompt
  }

  /**
   * Generates the dynamic web workspace guidelines based on the provided path.
   *
   * @param workspacePath The current path of the workspace. Null if not bound.
   * @param useEnglish Whether to use the English or Chinese version of the guidelines.
   * @return A string containing the appropriate workspace guidelines.
   */
  private fun getWorkspaceGuidelines(workspacePath: String?, workspaceEnv: String?, useEnglish: Boolean): String {
      val envLabel = workspaceEnv?.trim().orEmpty()
      val shouldShowEnv = envLabel.isNotBlank() && !envLabel.equals("android", ignoreCase = true)
      return if (workspacePath != null) {
          if (useEnglish) {
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
              Web工作区指南：
              - 你的工作目录，$workspacePath${if (shouldShowEnv) "（environment=$envLabel）" else ""}，已被自动配置为Web服务器的根目录。
              - 使用 apply_file 工具创建网页文件 (HTML/CSS/JS)。
              - 主文件必须是 index.html，用户可直接预览。
              - 建议将代码拆分到不同文件，以提高稳定性和可维护性。
              - 如果项目较为复杂，可以考虑新建js文件夹和css文件夹并创建多个文件。
              - 文件引用请使用相对路径。
              ${if (shouldShowEnv) "- 通过工具读写工作区文件时，请带上 `environment=\"$envLabel\"`，并使用 `/...` 形式的绝对路径。" else ""}
              - **代码修改最佳实践**：修改任何文件之前，建议组合使用 `grep_code` 与 `grep_context` 定位并理解相关代码及其上下文，避免在未理解项目结构时盲改。
              """.trimIndent()
          }
      } else {
          if (useEnglish) {
              """
              WEB WORKSPACE GUIDELINES:
              - A web workspace is not yet configured for this chat. To enable web development features, please prompt the user to click the 'Web' button in the top-right corner of the app to bind a workspace directory.
              """.trimIndent()
          } else {
              """
              Web工作区指南：
              - 当前对话尚未配置Web工作区。如需启用Web开发功能，请提示用户点击应用右上角的 "Web" 按钮来绑定一个工作区目录。
              """.trimIndent()
          }
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
          useEnglish: Boolean = false,
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
          useToolCallApi: Boolean = false
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
        useToolCallApi = useToolCallApi
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
        useEnglish = false,
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
        useToolCallApi = false
    )
  }
}
