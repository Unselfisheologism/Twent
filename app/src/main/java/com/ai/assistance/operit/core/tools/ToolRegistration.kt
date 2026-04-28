package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.creator.PackageCreatorTools
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.integrations.tasker.triggerAIAgentAction
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

/**
 * This file contains all tool registrations centralized for easier maintenance and integration It
 * extracts the registerTools logic from AIToolHandler into a dedicated file
 */

/**
 * Register all available tools with the AIToolHandler
 * @param handler The AIToolHandler instance to register tools with
 * @param context Application context for tools that need it
 */
fun registerAllTools(handler: AIToolHandler, context: Context) {

    // Helper function to wrap UI tool execution with visibility changes
    suspend fun executeUiToolWithVisibility(
        tool: AITool,
        showStatusIndicator: Boolean = true,
        delayMs: Long = 50,
        action: suspend (AITool) -> ToolResult
    ): ToolResult {
        val floatingService = FloatingChatService.getInstance()
        return try {
            floatingService?.setFloatingWindowVisible(false)
            if (showStatusIndicator) {
                floatingService?.setStatusIndicatorVisible(true)
            } else {
                floatingService?.setStatusIndicatorVisible(false)
            }
            delay(delayMs)
            action(tool)
        } finally {
            floatingService?.setFloatingWindowVisible(true)
            floatingService?.setStatusIndicatorVisible(false)
        }
    }

    fun s(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    fun formatEnvInfo(environment: String?): String {
        return if (!environment.isNullOrBlank() && environment != "android") {
            s(R.string.toolreg_env_info, environment)
        } else {
            ""
        }
    }

    fun formatEnvArrowInfo(sourceEnv: String, destEnv: String): String {
        return if (sourceEnv != "android" || destEnv != "android") {
            s(R.string.toolreg_env_arrow_info, sourceEnv, destEnv)
        } else {
            ""
        }
    }

    // 不在提示词加入的工具
    handler.registerTool(
            name = "execute_shell",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val actualCmd = if (command.startsWith("ddgs ")) command + " 2>&1" else command
            },
            executor = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val actualCmd = if (command.startsWith("ddgs ")) command + " 2>&1" else command
                // Use Linux terminal with timeout
                try {
                    val terminal = com.ai.assistance.operit.core.tools.system.Terminal.getInstance(context)
                    val sessions = terminal.terminalState.value.sessions
                    val sessionId = (sessions.firstOrNull()?.id) ?: runBlocking { terminal.createSession("default") }

                    // Execute command with 30 sec timeout
                    val result = runBlocking(Dispatchers.Default) {
                        withTimeoutOrNull(30000L) {
                            runBlocking { terminal.executeCommand(sessionId, actualCmd) }
                        }
                    }

                    if (result != null) {
                        ToolResult(toolName = tool.name, success = true, result = com.ai.assistance.operit.core.tools.StringResultData(result))
                    } else {
                        ToolResult(toolName = tool.name, success = false, result = com.ai.assistance.operit.core.tools.StringResultData(""), error = "Command timed out after 30 seconds")
                    }
                } catch (e: Exception) {
                    ToolResult(toolName = tool.name, success = false, result = com.ai.assistance.operit.core.tools.StringResultData(""), error = e.message)
                }
            }
    )

    handler.registerTool(
            name = "close_all_virtual_displays",
            dangerCheck = { false },
            descriptionGenerator = { _ -> s(R.string.toolreg_close_all_virtual_displays_desc) },
            executor = { tool ->
                try {
                    VirtualDisplayOverlay.hideAll()
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("OK")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = e.message
                    )
                }
            }
    )

    // 终端命令执行工具 - 一次性收集输出
    handler.registerTool(
            name = "create_terminal_session",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val sessionName = tool.parameters.find { it.name == "session_name" }?.value
                val displayName = sessionName ?: s(R.string.toolreg_unnamed)
                s(R.string.toolreg_create_terminal_session_desc, displayName)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.createOrGetSession(tool)
            }
    )

    handler.registerTool(
            name = "execute_in_terminal_session",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val actualCmd = if (command.startsWith("ddgs ")) command + " 2>&1" else command
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                s(R.string.toolreg_execute_in_terminal_session_desc, sessionId ?: "", command)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.executeCommandInSession(tool)
            }
    )

    handler.registerTool(
            name = "close_terminal_session",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                s(R.string.toolreg_close_terminal_session_desc, sessionId ?: "")
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.closeSession(tool)
            }
    )

    // 注册问题库查询工具
    handler.registerTool(
            name = "query_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                s(R.string.toolreg_query_memory_desc, query)
            },
            executor = { tool ->
                val problemLibraryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                problemLibraryTool.invoke(tool)
            }
    )
    
    // 注册根据标题获取单个记忆工具
    handler.registerTool(
            name = "get_memory_by_title",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_get_memory_by_title_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册用户偏好更新工具
    handler.registerTool(
            name = "update_user_preferences",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val params = mutableListOf<String>()
                tool.parameters.forEach { param ->
                    val label =
                            when (param.name) {
                                "birth_date" -> s(R.string.toolreg_user_pref_birth_date)
                                "gender" -> s(R.string.toolreg_user_pref_gender)
                                "personality" -> s(R.string.toolreg_user_pref_personality)
                                "identity" -> s(R.string.toolreg_user_pref_identity)
                                "occupation" -> s(R.string.toolreg_user_pref_occupation)
                                "ai_style" -> s(R.string.toolreg_user_pref_ai_style)
                                else -> null
                            }
                    if (label != null) {
                        params.add(label)
                    }
                }
                s(
                        R.string.toolreg_update_user_preferences_desc,
                        params.joinToString(s(R.string.toolreg_list_separator))
                )
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册创建记忆工具
    handler.registerTool(
            name = "create_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_create_memory_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册更新记忆工具
    handler.registerTool(
            name = "update_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val oldTitle = tool.parameters.find { it.name == "old_title" }?.value ?: ""
                val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: oldTitle
                s(R.string.toolreg_update_memory_desc, oldTitle, newTitle)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册删除记忆工具
    handler.registerTool(
            name = "delete_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_delete_memory_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册链接记忆工具
    handler.registerTool(
            name = "link_memories",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value ?: ""
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value ?: ""
                val linkType = tool.parameters.find { it.name == "link_type" }?.value ?: "related"
                s(R.string.toolreg_link_memories_desc, sourceTitle, targetTitle, linkType)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 系统操作工具
    handler.registerTool(
            name = "use_package",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_use_package_desc, packageName)
            },
            executor = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                handler
                    .getOrCreatePackageManager()
                    .executeUsePackageTool(tool.name, packageName)
            }
    )

    // Skill usage tool
    handler.registerTool(
            name = "use_skill",
            descriptionGenerator = { tool ->
                val skillName = tool.parameters.find { it.name == "skill_name" }?.value ?: ""
                s(R.string.toolreg_use_skill_desc, skillName)
            },
            executor = { tool ->
                val skillName = tool.parameters.find { it.name == "skill_name" }?.value ?: ""
                if (skillName.isBlank()) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Missing required parameter: skill_name"
                    )
                } else {
                    try {
                        val skillManager = com.ai.assistance.operit.core.tools.skill.SkillManager.getInstance(context)
                        val skillPrompt = skillManager.getSkillSystemPrompt(skillName)
                        if (skillPrompt != null) {
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData(skillPrompt)
                            )
                        } else {
                            ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Skill not found: $skillName"
                            )
                        }
                    } catch (e: Exception) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error loading skill: ${e.message}"
                        )
                    }
                }
            }
    )

    // ADB命令执行工具

    // 计算器工具
    handler.registerTool(
            name = "calculate",
            descriptionGenerator = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                s(R.string.toolreg_calculate_desc, expression)
            },
            executor = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                try {
                    val result = ToolGetter.getCalculator().evalExpression(expression)
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Calculation result: $result")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Calculation error: ${e.message}"
                    )
                }
            }
    )

// Web Search: use execute_shell + "ddgs text -k 'query'" instead

    // 休眠工具
    handler.registerTool(
            name = "sleep",
            descriptionGenerator = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                s(R.string.toolreg_sleep_desc, durationMs)
            },
            executor = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000

                val limitedDuration = durationMs.coerceIn(0, 10000) // Limit to max 10 seconds

                // Use runBlocking with Dispatchers.IO to ensure sleep happens on background thread
                runBlocking(Dispatchers.IO) {
                    delay(limitedDuration.toLong())
                }

                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Slept for ${limitedDuration}ms")
                )
            }
    )

    // Intent工具
    handler.registerTool(
            name = "execute_intent",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val packageName = tool.parameters.find { it.name == "package" }?.value
                val component = tool.parameters.find { it.name == "component" }?.value
                val type = tool.parameters.find { it.name == "type" }?.value ?: "activity"

                when {
                    !component.isNullOrBlank() ->
                            s(R.string.toolreg_execute_intent_component_desc, component, type)
                    !packageName.isNullOrBlank() && !action.isNullOrBlank() ->
                            s(
                                    R.string.toolreg_execute_intent_action_package_desc,
                                    action,
                                    packageName,
                                    type
                            )
                    !action.isNullOrBlank() -> s(R.string.toolreg_execute_intent_action_desc, action, type)
                    else -> s(R.string.toolreg_execute_android_intent_desc, type)
                }
            },
            executor = { tool ->
                val intentTool = ToolGetter.getIntentToolExecutor(context)
                runBlocking(Dispatchers.IO) { intentTool.invoke(tool) }
            }
    )

    handler.registerTool(
            name = "send_broadcast",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val preview = action?.takeIf { it.isNotBlank() } ?: "(no action)"
                "Send broadcast: $preview"
            },
            executor = { tool ->
                val sendBroadcastTool = ToolGetter.getSendBroadcastToolExecutor(context)
                runBlocking(Dispatchers.IO) { sendBroadcastTool.invoke(tool) }
            }
    )

    // 设备信息工具
    handler.registerTool(
            name = "device_info",
            descriptionGenerator = { _ -> s(R.string.toolreg_device_info_desc) },
            executor = { tool ->
                val deviceInfoTool = ToolGetter.getDeviceInfoToolExecutor(context)
                deviceInfoTool.invoke(tool)
            }
    )
    
    // Tasker事件触发工具
    handler.registerTool(
            name = "trigger_tasker_event",
            descriptionGenerator = { tool ->
                val taskType = tool.parameters.find { it.name == "task_type" }?.value ?: ""
                val args = tool.parameters.filter { it.name.startsWith("arg1") }.joinToString(",")
                s(R.string.toolreg_trigger_tasker_event_desc, taskType, args)
            },
            executor = { tool ->
                val params = tool.parameters.associate { it.name to it.value }
                val taskType = params["task_type"]
                if (taskType.isNullOrBlank()) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = s(R.string.toolreg_missing_required_param, "task_type")
                    )
                } else {
                    val args = params.filterKeys { it != "task_type" }
                    try {
                        context.triggerAIAgentAction(
                            taskType,
                            args
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    StringResultData(
                                            s(R.string.toolreg_tasker_event_triggered_result, taskType)
                                    )
                        )
                    } catch (e: Exception) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error =
                                    s(
                                            R.string.toolreg_failed_trigger_tasker_event,
                                            e.message ?: ""
                                    )
                        )
                    }
                }
            }
    )

    
    // 工作流工具
    val workflowTools = ToolGetter.getWorkflowTools(context)

    // 获取所有工作流
    handler.registerTool(
            name = "get_all_workflows",
            descriptionGenerator = { _ -> s(R.string.toolreg_get_all_workflows_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getAllWorkflows(tool) } }
    )

    // 创建工作流
    handler.registerTool(
            name = "create_workflow",
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                s(R.string.toolreg_create_workflow_desc, name)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.createWorkflow(tool) } }
    )

    // 获取工作流详情
    handler.registerTool(
            name = "get_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_get_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getWorkflow(tool) } }
    )

    // 更新工作流
    handler.registerTool(
            name = "update_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                val name = tool.parameters.find { it.name == "name" }?.value
                if (name != null) {
                    s(R.string.toolreg_update_workflow_with_name_desc, id, name)
                } else {
                    s(R.string.toolreg_update_workflow_desc, id)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.updateWorkflow(tool) } }
    )

    // 差异更新工作流
    handler.registerTool(
            name = "patch_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_patch_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.patchWorkflow(tool) } }
    )

    // 删除工作流
    handler.registerTool(
            name = "delete_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_delete_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.deleteWorkflow(tool) } }
    )

    // 触发工作流执行
    handler.registerTool(
            name = "trigger_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_trigger_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.triggerWorkflow(tool) } }
    )

    // 对话管理工具
    val chatManagerTool = ToolGetter.getChatManagerTool(context)

    // 启动聊天服务
    handler.registerTool(
            name = "start_chat_service",
            descriptionGenerator = { _ -> s(R.string.toolreg_start_chat_service_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.startChatService(tool) } }
    )

    // 停止聊天服务
    handler.registerTool(
            name = "stop_chat_service",
            descriptionGenerator = { _ -> s(R.string.toolreg_stop_chat_service_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.stopChatService(tool) } }
    )

    // 新建对话
    handler.registerTool(
            name = "create_new_chat",
            descriptionGenerator = { tool ->
                val group = tool.parameters.find { it.name == "group" }?.value
                if (group.isNullOrBlank()) {
                    s(R.string.toolreg_create_new_chat_desc)
                } else {
                    s(R.string.toolreg_create_new_chat_in_group_desc, group)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.createNewChat(tool) } }
    )

    // 列出所有对话
    handler.registerTool(
            name = "list_chats",
            descriptionGenerator = { _ -> s(R.string.toolreg_list_chats_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.listChats(tool) } }
    )

    // 查找对话
    handler.registerTool(
            name = "find_chat",
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                s(R.string.toolreg_find_chat_desc, query)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.findChat(tool) } }
    )

    // 查询对话输入状态
    handler.registerTool(
            name = "agent_status",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_agent_status_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.agentStatus(tool) } }
    )

    // 切换对话
    handler.registerTool(
            name = "switch_chat",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_switch_chat_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.switchChat(tool) } }
    )

    // 发送消息给AI
    handler.registerTool(
            name = "send_message_to_ai",
            descriptionGenerator = { tool ->
                val message = tool.parameters.find { it.name == "message" }?.value ?: ""
                val preview = if (message.length > 30) "${message.take(30)}..." else message
                s(R.string.toolreg_send_message_to_ai_desc, preview)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.sendMessageToAI(tool) } }
    )

    // 列出所有角色卡
    handler.registerTool(
            name = "list_character_cards",
            descriptionGenerator = { _ -> s(R.string. toolreg_list_character_cards_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.listCharacterCards(tool) } }
    )

    handler.registerTool(
            name = "get_chat_messages",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                val order = tool.parameters.find { it.name == "order" }?.value
                val limit = tool.parameters.find { it.name == "limit" }?.value
                val orderInfo = if (!order.isNullOrBlank()) " ($order)" else ""
                val limitInfo = if (!limit.isNullOrBlank()) " ($limit)" else ""
                s(R.string.toolreg_get_chat_messages_desc, chatId, orderInfo, limitInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.getChatMessages(tool) } }
    )

    // 文件系统工具
    val fileSystemTools = ToolGetter.getFileSystemTools(context)

    // 列出目录内容
    handler.registerTool(
            name = "list_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_list_files_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.listFiles(tool) }
            }
    )

    // 读取文件内容
    handler.registerTool(
            name = "read_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFile(tool) } }
    )

    // 按行号范围读取文件内容
    handler.registerTool(
            name = "read_file_part",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val startLine = tool.parameters.find { it.name == "start_line" }?.value ?: "1"
                val endLine = tool.parameters.find { it.name == "end_line" }?.value
                val envInfo = formatEnvInfo(environment)
                val rangeInfo =
                        if (endLine != null) {
                            s(R.string.toolreg_read_file_part_range_lines, startLine, endLine)
                        } else {
                            s(R.string.toolreg_read_file_part_range_from, startLine)
                        }
                s(R.string.toolreg_read_file_part_desc, rangeInfo, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.readFilePart(tool) }
            }
    )

    // 读取完整文件内容
    handler.registerTool(
            name = "read_file_full",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_full_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileFull(tool) } }
    )

    // 读取二进制文件内容（Base64编码）
    handler.registerTool(
            name = "read_file_binary",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_binary_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileBinary(tool) } }
    )

    // 写入文件
    handler.registerTool(
            name = "write_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val append = tool.parameters.find { it.name == "append" }?.value == "true"
                val envInfo = formatEnvInfo(environment)
                val operation =
                        if (append) {
                            s(R.string.toolreg_write_file_append_operation)
                        } else {
                            s(R.string.toolreg_write_file_overwrite_operation)
                        }
                s(R.string.toolreg_write_file_desc, operation, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.writeFile(tool) }
            }
    )

    // 写入二进制文件
    handler.registerTool(
        name = "write_file_binary",
        dangerCheck = { true }, // 总是危险操作
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            val environment = tool.parameters.find { it.name == "environment" }?.value
            val envInfo = formatEnvInfo(environment)
            s(R.string.toolreg_write_file_binary_desc, path, envInfo)
        },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { fileSystemTools.writeFileBinary(tool) }
        }
    )

    // 删除文件/目录
    handler.registerTool(
            name = "delete_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val recursive = tool.parameters.find { it.name == "recursive" }?.value == "true"
                val envInfo = formatEnvInfo(environment)
                val operation =
                        if (recursive) {
                            s(R.string.toolreg_delete_file_recursive_operation)
                        } else {
                            s(R.string.toolreg_delete_file_operation)
                        }
                s(R.string.toolreg_delete_file_desc, operation, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.deleteFile(tool) } }
    )

    // UI自动化工具
    val uiTools = ToolGetter.getUITools(context)

    // === Coordinate-based UI Tools ===
    handler.registerTool(
        name = "tap",
        descriptionGenerator = { tool ->
            val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
            val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
            "Tap at coordinates ($x, $y)"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.tap(tool) } }
    )

    handler.registerTool(
        name = "long_press",
        descriptionGenerator = { tool ->
            val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
            val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
            "Long press at coordinates ($x, $y)"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.longPress(tool) } }
    )

    handler.registerTool(
        name = "double_tap",
        descriptionGenerator = { tool ->
            val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
            val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
            "Double tap at ($x, $y)"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.doubleTap(tool) } }
    )

    handler.registerTool(
        name = "click_element",
        descriptionGenerator = { tool ->
            val index = tool.parameters.find { it.name == "index" }?.value
            val text = tool.parameters.find { it.name == "text" }?.value
            val desc = tool.parameters.find { it.name == "content_description" }?.value
            when {
                index != null -> "Click element at index $index"
                text != null -> "Click element containing text: $text"
                desc != null -> "Click element: $desc"
                else -> "Click element on screen"
            }
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.clickElement(tool) } }
    )

    handler.registerTool(
        name = "swipe",
        descriptionGenerator = { tool ->
            val sx = tool.parameters.find { it.name == "start_x" }?.value ?: "?"
            val sy = tool.parameters.find { it.name == "start_y" }?.value ?: "?"
            val ex = tool.parameters.find { it.name == "end_x" }?.value ?: "?"
            val ey = tool.parameters.find { it.name == "end_y" }?.value ?: "?"
            "Swipe from ($sx, $sy) to ($ex, $ey)"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.swipe(tool) } }
    )

    handler.registerTool(
        name = "swipe_left",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Swipe left by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.swipeLeft(tool) } }
    )

    handler.registerTool(
        name = "swipe_right",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Swipe right by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.swipeRight(tool) } }
    )

    handler.registerTool(
        name = "swipe_up",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Swipe up by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.swipeUp(tool) } }
    )

    handler.registerTool(
        name = "swipe_down",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Swipe down by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.swipeDown(tool) } }
    )

    handler.registerTool(
        name = "scroll_left",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Scroll left by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.scrollLeft(tool) } }
    )

    handler.registerTool(
        name = "scroll_right",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Scroll right by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.scrollRight(tool) } }
    )

    handler.registerTool(
        name = "scroll_up",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Scroll up by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.scrollUp(tool) } }
    )

    handler.registerTool(
        name = "scroll_down",
        descriptionGenerator = { tool ->
            val pixels = tool.parameters.find { it.name == "pixels" }?.value ?: "500"
            "Scroll down by $pixels pixels"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.scrollDown(tool) } }
    )

    handler.registerTool(
        name = "hold",
        descriptionGenerator = { tool ->
            val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
            val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
            "Hold at ($x, $y)"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.hold(tool) } }
    )

    handler.registerTool(
        name = "press_key",
        descriptionGenerator = { tool ->
            val key = tool.parameters.find { it.name == "key" }?.value ?: "?"
            "Press key: $key"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.pressKey(tool) } }
    )

    handler.registerTool(
        name = "type_text",
        descriptionGenerator = { tool ->
            val text = tool.parameters.find { it.name == "text" }?.value ?: ""
            "Type: \"${text.take(30)}${if (text.length > 30) "..." else ""}\""
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.setInputText(tool) } }
    )

    handler.registerTool(
        name = "open_app",
        descriptionGenerator = { tool ->
            val name = tool.parameters.find { it.name == "package_name" }?.value
                ?: tool.parameters.find { it.name == "app_name" }?.value ?: "?"
            "Open app: $name"
        },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.openApp(tool) } }
    )

    handler.registerTool(
        name = "back",
        descriptionGenerator = { _ -> "Press back button" },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.back(tool) } }
    )

    handler.registerTool(
        name = "home",
        descriptionGenerator = { _ -> "Press home button" },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.home(tool) } }
    )

    handler.registerTool(
        name = "get_page_info",
        descriptionGenerator = { _ -> "Get current UI page information" },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.getPageInfo(tool) } }
    )

    handler.registerTool(
        name = "get_current_activity",
        descriptionGenerator = { _ -> "Get current foreground activity name" },
        executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.getCurrentActivity(tool) } }
    )

    // HTTP请求工具
    val httpTools = ToolGetter.getHttpTools(context)

    // 发送HTTP请求
    handler.registerTool(
            name = "http_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val method = tool.parameters.find { it.name == "method" }?.value ?: "GET"
                s(R.string.toolreg_http_request_desc, method, url)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { httpTools.httpRequest(tool) } }
    )

    // 多部分表单请求（文件上传）
    handler.registerTool(
            name = "multipart_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val filesParam = tool.parameters.find { it.name == "files" }?.value ?: "[]"
                val filesCount =
                        try {
                            JSONArray(filesParam).length()
                        } catch (e: Exception) {
                            0
                        }
                s(R.string.toolreg_multipart_request_desc, url, filesCount)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { httpTools.multipartRequest(tool) }
            }
    )

    // 管理Cookie工具
    handler.registerTool(
            name = "manage_cookies",
            descriptionGenerator = { tool ->
                val action =
                        tool.parameters.find { it.name == "action" }?.value?.lowercase() ?: "get"
                val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
                when (action) {
                    "get" ->
                            if (domain.isBlank()) {
                                s(R.string.toolreg_manage_cookies_get_all_desc)
                            } else {
                                s(R.string.toolreg_manage_cookies_get_domain_desc, domain)
                            }
                    "set" -> s(R.string.toolreg_manage_cookies_set_domain_desc, domain)
                    "clear" ->
                            if (domain.isBlank()) {
                                s(R.string.toolreg_manage_cookies_clear_all_desc)
                            } else {
                                s(R.string.toolreg_manage_cookies_clear_domain_desc, domain)
                            }
                    else -> s(R.string.toolreg_manage_cookies_desc, action)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { httpTools.manageCookies(tool) } }
    )

    // 检查文件是否存在
    handler.registerTool(
            name = "file_exists",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_file_exists_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.fileExists(tool) }
            }
    )

    // 移动/重命名文件或目录
    handler.registerTool(
            name = "move_file",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_move_file_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.moveFile(tool) } }
    )

    // 复制文件或目录
    handler.registerTool(
            name = "copy_file",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val sourceEnv = tool.parameters.find { it.name == "source_environment" }?.value
                val destEnv = tool.parameters.find { it.name == "dest_environment" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 确定源和目标环境
                val srcEnv = sourceEnv ?: environment ?: "android"
                val dstEnv = destEnv ?: environment ?: "android"

                val envInfo = formatEnvArrowInfo(srcEnv, dstEnv)
                s(R.string.toolreg_copy_file_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.copyFile(tool) } }
    )

    // 创建目录
    handler.registerTool(
            name = "make_directory",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_make_directory_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.makeDirectory(tool) }
            }
    )

    // SSH远程文件系统工具
    val sshTools = ToolGetter.getSSHRemoteConnectionTools(context)

    // 登录SSH服务器
    handler.registerTool(
            name = "ssh_login",
            descriptionGenerator = { tool ->
                val host = tool.parameters.find { it.name == "host" }?.value ?: ""
                val username = tool.parameters.find { it.name == "username" }?.value ?: ""
                val port = tool.parameters.find { it.name == "port" }?.value ?: "22"
                s(R.string.toolreg_ssh_login_desc, username, host, port)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { sshTools.sshLogin(tool) } }
    )

    // 退出SSH
    handler.registerTool(
            name = "ssh_exit",
            descriptionGenerator = { _ -> s(R.string.toolreg_ssh_exit_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { sshTools.sshExit(tool) } }
    )

    // 搜索文件
    handler.registerTool(
            name = "find_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: "*"
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_find_files_desc, path, pattern, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.findFiles(tool) }
            }
    )

    // 获取文件信息
    handler.registerTool(
            name = "file_info",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_file_info_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.fileInfo(tool) } }
    )

    // 智能应用文件绑定
    handler.registerTool(
            name = "apply_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_apply_file_desc, path, envInfo)
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            return runBlocking { fileSystemTools.applyFile(tool).last() }
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            return fileSystemTools.applyFile(tool)
                        }
                    }
    )

    // 压缩文件/目录
    handler.registerTool(
            name = "zip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_zip_files_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.zipFiles(tool) } }
    )

    // 解压缩文件
    handler.registerTool(
            name = "unzip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_unzip_files_desc, source, destination, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.unzipFiles(tool) }
            }
    )

    // 打开文件
    handler.registerTool(
            name = "open_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_open_file_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.openFile(tool) } }
    )

    // 分享文件
    handler.registerTool(
            name = "share_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_share_file_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.shareFile(tool) }
            }
    )

    // Grep代码搜索
    handler.registerTool(
            name = "grep_code",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
                val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                if (filePattern != null && filePattern != "*") {
                    s(R.string.toolreg_grep_code_with_file_pattern_desc, path, pattern, envInfo, filePattern)
                } else {
                    s(R.string.toolreg_grep_code_desc, path, pattern, envInfo)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepCode(tool) }
            }
    )

    // Grep上下文搜索
    handler.registerTool(
            name = "grep_context",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                val preview = if (intent.length > 40) "${intent.take(40)}..." else intent
                s(R.string.toolreg_grep_context_desc, path, preview, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepContext(tool) }
            }
    )

    // 下载文件
    handler.registerTool(
            name = "download_file",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_download_file_desc, url, destination, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.downloadFile(tool) }
            }
    )

    // 系统操作工具
    val systemOperationTools = ToolGetter.getSystemOperationTools(context)

    handler.registerTool(
            name = "toast",
            dangerCheck = { false },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.toast(tool) }
            }
    )

    handler.registerTool(
            name = "send_notification",
            dangerCheck = { false },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.sendNotification(tool) }
            }
    )

    // 修改系统设置
    handler.registerTool(
            name = "modify_system_setting",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                val value = tool.parameters.find { it.name == "value" }?.value ?: ""
                s(R.string.toolreg_modify_system_setting_desc, key, value)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.modifySystemSetting(tool) }
            }
    )

    // 获取系统设置
    handler.registerTool(
            name = "get_system_setting",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                s(R.string.toolreg_get_system_setting_desc, key)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getSystemSetting(tool) }
            }
    )

    // 安装应用
    handler.registerTool(
            name = "install_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                s(R.string.toolreg_install_app_desc, path)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.installApp(tool) }
            }
    )

    // 卸载应用
    handler.registerTool(
            name = "uninstall_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_uninstall_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.uninstallApp(tool) }
            }
    )

    // 获取已安装应用列表
    handler.registerTool(
            name = "list_installed_apps",
            descriptionGenerator = { _ -> s(R.string.toolreg_list_installed_apps_desc) },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.listInstalledApps(tool) }
            }
    )

    // 启动应用
    handler.registerTool(
            name = "start_app",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_start_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.startApp(tool) }
            }
    )

    // 停止应用
    handler.registerTool(
            name = "stop_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_stop_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.stopApp(tool) }
            }
    )

    // 获取设备通知
    handler.registerTool(
            name = "get_notifications",
            descriptionGenerator = { tool ->
                val limit = tool.parameters.find { it.name == "limit" }?.value ?: "10"
                val includeOngoing =
                        tool.parameters.find { it.name == "include_ongoing" }?.value == "true"

                if (includeOngoing) {
                    s(R.string.toolreg_get_notifications_desc_with_ongoing, limit)
                } else {
                    s(R.string.toolreg_get_notifications_desc, limit)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getNotifications(tool) }
            }
    )

    // 获取设备位置
    handler.registerTool(
            name = "get_device_location",
            descriptionGenerator = { tool ->
                val highAccuracy =
                        tool.parameters.find { it.name == "high_accuracy" }?.value == "true"
                if (highAccuracy) {
                    s(R.string.toolreg_get_device_location_high_accuracy_desc)
                } else {
                    s(R.string.toolreg_get_device_location_desc)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getDeviceLocation(tool) }
            }
    )

    // FFmpeg工具 - 执行通用FFmpeg命令
    handler.registerTool(
            name = "ffmpeg_execute",
            dangerCheck = { true }, // 总是危险操作，因为可能会修改文件
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val actualCmd = if (command.startsWith("ddgs ")) command + " 2>&1" else command
                s(R.string.toolreg_ffmpeg_execute_desc, command)
            },
            executor = { tool ->
                val ffmpegTool = ToolGetter.getFFmpegToolExecutor(context)
                ffmpegTool.invoke(tool)
            }
    )

    // FFmpeg信息工具 - 获取FFmpeg信息
    handler.registerTool(
            name = "ffmpeg_info",
            descriptionGenerator = { _ -> s(R.string.toolreg_ffmpeg_info_desc) },
            executor = { tool ->
                val ffmpegInfoTool = ToolGetter.getFFmpegInfoToolExecutor()
                ffmpegInfoTool.invoke(tool)
            }
    )

    // FFmpeg视频转换工具 - 简化的视频转换接口
    handler.registerTool(
            name = "ffmpeg_convert",
            dangerCheck = { true }, // 总是危险操作，因为会创建新文件
            descriptionGenerator = { tool ->
                val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
                val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
                s(R.string.toolreg_ffmpeg_convert_desc, inputPath, outputPath)
            },
            executor = { tool ->
                val ffmpegConvertTool = ToolGetter.getFFmpegConvertToolExecutor(context)
                ffmpegConvertTool.invoke(tool)
            }
    )

    // 文本转语音工具 - 将文本转换为语音并播放
    handler.registerTool(
            name = "text_to_speech",
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                val preview = if (text.length > 30) "${text.take(30)}..." else text
                s(R.string.toolreg_text_to_speech_desc, preview)
            },
            executor = { tool ->
                val ttsTool = ToolGetter.getTextToSpeechToolExecutor(context)
                runBlocking(Dispatchers.IO) { ttsTool.invoke(tool) }
            }
    )

    // 语音转文本工具 - 将语音识别为文本
    handler.registerTool(
            name = "speech_to_text",
            descriptionGenerator = { tool ->
                val languageCode = tool.parameters.find { it.name == "language_code" }?.value ?: "zh-CN"
                s(R.string.toolreg_speech_to_text_desc, languageCode)
            },
            executor = { tool ->
                val sttTool = ToolGetter.getSpeechToTextToolExecutor(context)
                runBlocking(Dispatchers.IO) { sttTool.invoke(tool) }
            }
    )

    // Mini-App creation tool - creates a mini-app from AI-generated HTML/CSS/JS
    handler.registerTool(
            name = "create_mini_app",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                val type = tool.parameters.find { it.name == "type" }?.value ?: "persistent"
                s(R.string.toolreg_create_mini_app_desc, name, type)
            },
            executor = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: "Untitled App"
                val type = tool.parameters.find { it.name == "type" }?.value?.lowercase() ?: "persistent"
                val html = tool.parameters.find { it.name == "html" }?.value ?: ""
                val css = tool.parameters.find { it.name == "css" }?.value ?: ""
                val javascript = tool.parameters.find { it.name == "javascript" }?.value ?: ""
                val description = tool.parameters.find { it.name == "description" }?.value
                val requiredPermissions = tool.parameters.find { it.name == "required_permissions" }?.value ?: ""
                val webPermissions = tool.parameters.find { it.name == "web_permissions" }?.value ?: ""

                if (html.isBlank()) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "HTML content is required for mini-app creation"
                    )
                } else {
                    try {
                        val miniAppType = if (type == "ephemeral") {
                            com.ai.assistance.operit.data.model.MiniAppType.EPHEMERAL
                        } else {
                            com.ai.assistance.operit.data.model.MiniAppType.PERSISTENT
                        }

                        val files = mutableMapOf<String, String>()
                        files["index.html"] = html
                        if (css.isNotBlank()) files["style.css"] = css
                        if (javascript.isNotBlank()) files["app.js"] = javascript

                        val androidPerms = if (requiredPermissions.isNotBlank()) {
                            requiredPermissions.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        } else emptySet()
                        val webPerms = if (webPermissions.isNotBlank()) {
                            webPermissions.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        } else emptySet()

                        val scaffold = com.ai.assistance.operit.data.miniapp.MiniAppScaffold.FromFiles(
                                files = files,
                                name = name,
                                type = miniAppType,
                                description = description,
                                entryFile = "index.html",
                                requiredPermissions = androidPerms,
                                webPermissions = webPerms,
                                metadata = mapOf("created_by" to "ai_tool")
                        )

                        val manager = com.ai.assistance.operit.data.miniapp.MiniAppManager.getInstance(context)
                        val ensureName = runBlocking { manager.ensureUniqueName(name, miniAppType) }
                        val finalScaffold = scaffold.copy(name = ensureName)
                        val result = runBlocking { manager.createMiniApp(finalScaffold) }

                        result.fold(
                                onSuccess = { miniApp ->
                                    val url = manager.getMiniAppUrl(miniApp)
                                    ToolResult(
                                            toolName = tool.name,
                                            success = true,
                                            result = StringResultData(
                                                    "Mini-app created successfully!\n" +
                                                            "Name: $ensureName\n" +
                                                            "ID: ${miniApp.id}\n" +
                                                            "Type: ${miniAppType.name.lowercase()}\n" +
                                                            "URL: $url\n" +
                                                            "Open with: open_mini_app?id=${miniApp.id}"
                                            )
                                    )
                                },
                                onFailure = { e ->
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = "Failed to create mini-app: ${e.message}"
                                    )
                                }
                        )
                    } catch (e: Exception) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error creating mini-app: ${e.message}"
                        )
                    }
                }
            }
    )

    // Mini-App list tool - returns all available mini-apps
    handler.registerTool(
            name = "list_mini_apps",
            dangerCheck = { false },
            descriptionGenerator = { _ -> s(R.string.toolreg_list_mini_apps_desc) },
            executor = { tool ->
                try {
                    val manager = com.ai.assistance.operit.data.miniapp.MiniAppManager.getInstance(context)
                    val result = runBlocking { manager.listMiniApps() }

                    result.fold(
                            onSuccess = { miniApps ->
                                if (miniApps.isEmpty()) {
                                    ToolResult(
                                            toolName = tool.name,
                                            success = true,
                                            result = StringResultData("No mini-apps found.")
                                    )
                                } else {
                                    val output = miniApps.joinToString("\n") { app ->
                                        "- ${app.name} (ID: ${app.id}, Type: ${app.type.name.lowercase()}, Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(app.createdAt))})"
                                    }
                                    ToolResult(
                                            toolName = tool.name,
                                            success = true,
                                            result = StringResultData("Available mini-apps:\n$output")
                                    )
                                }
                            },
                            onFailure = { e ->
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Failed to list mini-apps: ${e.message}"
                                )
                            }
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error listing mini-apps: ${e.message}"
                    )
                }
            }
    )

    // Mini-App delete tool
    handler.registerTool(
            name = "delete_mini_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val appId = tool.parameters.find { it.name == "app_id" }?.value ?: ""
                s(R.string.toolreg_delete_mini_app_desc, appId)
            },
            executor = { tool ->
                val appId = tool.parameters.find { it.name == "app_id" }?.value ?: ""
                if (appId.isBlank()) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "app_id is required"
                    )
                } else {
                    try {
                        val manager = com.ai.assistance.operit.data.miniapp.MiniAppManager.getInstance(context)
                        val appResult = runBlocking { manager.getMiniApp(appId) }
                        appResult.fold(
                                onSuccess = { app ->
                                    if (app == null) {
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Mini-app not found: $appId"
                                        )
                                    } else {
                                        val deleteResult = runBlocking { manager.deleteMiniApp(app.id, app.type) }
                                        deleteResult.fold(
                                                onSuccess = {
                                                    ToolResult(
                                                            toolName = tool.name,
                                                            success = true,
                                                            result = StringResultData("Mini-app deleted: ${app.name}")
                                                    )
                                                },
                                                onFailure = { e ->
                                                    ToolResult(
                                                            toolName = tool.name,
                                                            success = false,
                                                            result = StringResultData(""),
                                                            error = "Failed to delete mini-app: ${e.message}"
                                                    )
                                                }
                                        )
                                    }
                                },
                                onFailure = { e ->
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = "Error finding mini-app: ${e.message}"
                                    )
                                }
                        )
                    } catch (e: Exception) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error deleting mini-app: ${e.message}"
                        )
                    }
                }
            }
    )

    // ==================== COMPOSIO INTEGRATION TOOLS ====================
    // These tools give the AI agent direct access to Composio external integrations
    // (GitHub, Slack, Notion, etc.) without needing to go through workflows.

    val composioService = com.ai.assistance.operit.data.integration.ComposioApiService.getInstance(context)
    val manageConnections = com.ai.assistance.operit.domain.usecase.ManageConnections.getInstance(context)

    // List available Composio toolkits
    handler.registerTool(
            name = "composio_list_toolkits",
            dangerCheck = { false },
            descriptionGenerator = { _ -> s(R.string.toolreg_composio_list_toolkits_desc) },
            executor = { tool ->
                try {
                    if (!composioService.isConfigured()) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = s(R.string.toolreg_composio_not_configured)
                        )
                    } else {
                        val category = tool.parameters.find { it.name == "category" }?.value
                        val search = tool.parameters.find { it.name == "search" }?.value
                        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 20

                        val result = runBlocking(Dispatchers.IO) {
                            composioService.listToolkits(category = category, search = search, limit = limit)
                        }

                        result.fold(
                                onSuccess = { toolkits ->
                                    if (toolkits.isEmpty()) {
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData("No toolkits found. The Composio API may not be returning data correctly.")
                                        )
                                    } else {
                                        val output = toolkits.joinToString("\n") { toolkit ->
                                            "- ${toolkit.name}: ${toolkit.displayName} (${toolkit.description})"
                                        }
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData("Available Composio toolkits:\n$output")
                                        )
                                    }
                                },
                                onFailure = { e ->
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = "Failed to list toolkits: ${e.message}"
                                    )
                                }
                        )
                    }
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error listing Composio toolkits: ${e.message}"
                    )
                }
            }
    )

    // Execute a Composio tool
    handler.registerTool(
            name = "composio_execute_tool",
            dangerCheck = { true }, // External API calls are potentially dangerous
            descriptionGenerator = { tool ->
                val toolName = tool.parameters.find { it.name == "tool_name" }?.value ?: "unknown"
                s(R.string.toolreg_composio_execute_tool_desc, toolName)
            },
            executor = { tool ->
                try {
                    if (!composioService.isConfigured()) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = s(R.string.toolreg_composio_not_configured)
                        )
                    } else {
                        val toolName = tool.parameters.find { it.name == "tool_name" }?.value
                        val parametersJson = tool.parameters.find { it.name == "parameters" }?.value ?: "{}"
                        val accountId = tool.parameters.find { it.name == "account_id" }?.value

                        if (toolName.isNullOrBlank()) {
                            ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Missing required parameter: tool_name"
                            )
                        } else {
                            // Parse parameters JSON
                            val parameters = try {
                                val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(parametersJson)
                                if (jsonElement is kotlinx.serialization.json.JsonObject) {
                                    jsonElement.entries.associate { (key, value) ->
                                        key to (when (value) {
                                            is kotlinx.serialization.json.JsonPrimitive -> {
                                                val content = value.content
                                                when {
                                                    content == "true" || content == "false" -> content.toBoolean()
                                                    content.toLongOrNull() != null -> content.toLong()
                                                    content.toDoubleOrNull() != null -> content.toDouble()
                                                    else -> content
                                                }
                                            }
                                            else -> value.toString()
                                        })
                                    }
                                } else {
                                    emptyMap()
                                }
                            } catch (e: Exception) {
                                emptyMap<String, Any>()
                            }

                            val result = runBlocking(Dispatchers.IO) {
                                composioService.executeTool(toolName, parameters, accountId)
                            }

                            result.fold(
                                    onSuccess = { response ->
                                        if (response.success) {
                                            ToolResult(
                                                    toolName = tool.name,
                                                    success = true,
                                                    result = StringResultData("Tool '$toolName' executed successfully:\n${response.result}")
                                            )
                                        } else {
                                            ToolResult(
                                                    toolName = tool.name,
                                                    success = false,
                                                    result = StringResultData(""),
                                                    error = "Tool execution failed: ${response.error ?: "Unknown error"}"
                                            )
                                        }
                                    },
                                    onFailure = { e ->
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Failed to execute tool '$toolName': ${e.message}"
                                        )
                                    }
                            )
                        }
                    }
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error executing Composio tool: ${e.message}"
                    )
                }
            }
    )

    // List connected OAuth accounts
    handler.registerTool(
            name = "composio_list_connections",
            dangerCheck = { false },
            descriptionGenerator = { _ -> s(R.string.toolreg_composio_list_connections_desc) },
            executor = { tool ->
                try {
                    if (!composioService.isConfigured()) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = s(R.string.toolreg_composio_not_configured)
                        )
                    } else {
                        val result = runBlocking(Dispatchers.IO) {
                            manageConnections.getAllConnections()
                        }

                        result.fold(
                                onSuccess = { connections ->
                                    if (connections.isEmpty()) {
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData("No connected accounts found. Use 'composio_connect' to connect an account.")
                                        )
                                    } else {
                                        val output = connections.joinToString("\n") { conn ->
                                            "- ${conn.toolkit}: ${conn.accountName} (ID: ${conn.id}, Status: ${conn.status})"
                                        }
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData("Connected accounts:\n$output")
                                        )
                                    }
                                },
                                onFailure = { e ->
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = "Failed to list connections: ${e.message}"
                                    )
                                }
                        )
                    }
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error listing connections: ${e.message}"
                    )
                }
            }
    )

    // Initiate OAuth connection for a toolkit
    handler.registerTool(
            name = "composio_connect",
            dangerCheck = { true }, // OAuth flow requires user interaction
            descriptionGenerator = { tool ->
                val toolkit = tool.parameters.find { it.name == "toolkit" }?.value ?: "unknown"
                s(R.string.toolreg_composio_connect_desc, toolkit)
            },
            executor = { tool ->
                try {
                    if (!composioService.isConfigured()) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = s(R.string.toolreg_composio_not_configured)
                        )
                    } else {
                        val toolkit = tool.parameters.find { it.name == "toolkit" }?.value
                        val redirectUri = tool.parameters.find { it.name == "redirect_uri" }?.value ?: ""

                        if (toolkit.isNullOrBlank()) {
                            ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Missing required parameter: toolkit"
                            )
                        } else {
                            val result = runBlocking(Dispatchers.IO) {
                                manageConnections.initiateOAuthFlow(toolkit, redirectUri)
                            }

                            result.fold(
                                    onSuccess = { oauthResult ->
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData("OAuth flow initiated for '$toolkit'.\nAuth URL: ${oauthResult.authUrl}\nAccount ID: ${oauthResult.accountId}\n\nPlease open the URL in a browser to complete the connection, then use 'composio_disconnect' with the account ID to manage it.")
                                        )
                                    },
                                    onFailure = { e ->
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Failed to initiate OAuth for '$toolkit': ${e.message}"
                                        )
                                    }
                            )
                        }
                    }
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error initiating connection: ${e.message}"
                    )
                }
            }
    )

    // Disconnect an OAuth account
    handler.registerTool(
            name = "composio_disconnect",
            dangerCheck = { true }, // Disconnecting is a destructive action
            descriptionGenerator = { tool ->
                val accountId = tool.parameters.find { it.name == "account_id" }?.value ?: "unknown"
                s(R.string.toolreg_composio_disconnect_desc, accountId)
            },
            executor = { tool ->
                try {
                    if (!composioService.isConfigured()) {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = s(R.string.toolreg_composio_not_configured)
                        )
                    } else {
                        val accountId = tool.parameters.find { it.name == "account_id" }?.value

                        if (accountId.isNullOrBlank()) {
                            ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Missing required parameter: account_id"
                            )
                        } else {
                            val result = runBlocking(Dispatchers.IO) {
                                manageConnections.disconnectConnection(accountId)
                            }

                            result.fold(
                                    onSuccess = { success ->
                                        if (success) {
                                            ToolResult(
                                                    toolName = tool.name,
                                                    success = true,
                                                    result = StringResultData("Account '$accountId' disconnected successfully.")
                                            )
                                        } else {
                                            ToolResult(
                                                    toolName = tool.name,
                                                    success = false,
                                                    result = StringResultData(""),
                                                    error = "Failed to disconnect account '$accountId'."
                                            )
                                        }
                                    },
                                    onFailure = { e ->
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Failed to disconnect account '$accountId': ${e.message}"
                                        )
                                    }
                            )
                        }
                    }
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error disconnecting account: ${e.message}"
                    )
                }
            }
    )

    // Register package creator tools (create packages, MCP servers, and skills)
    PackageCreatorTools.registerCreatorTools(handler, context)
}