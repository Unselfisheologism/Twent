package com.ai.assistance.operit.core.tools.automation

import android.content.Context
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.StringResultData
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool for starting UI automation tasks
 * This tool allows the AI to start an automation task that will interact with the phone
 */
object AutomationTools {

    private const val TAG = "AutomationTools"

    /**
     * Start an automation task
     * Parameters:
     * - task: The task description (e.g., "打开微信并给朋友发消息")
     * - max_steps: Maximum steps to take (default 150)
     */
    suspend fun startAutomation(tool: AITool): ToolResult {
        val task = tool.parameters.find { it.name == "task" }?.value ?: run {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: task"
            )
        }
        
        val maxSteps = tool.parameters.find { it.name == "max_steps" }?.value?.toIntOrNull() ?: 150

        return try {
            withContext(Dispatchers.Main) {
                val controller = com.ai.assistance.operit.services.automation.AutomationController.getInstance(
                    tool.context
                )
                
                controller.startAutomation(
                    task = task,
                    maxSteps = maxSteps,
                    onStatusChange = { status ->
                        AppLogger.d(TAG, "Automation status: $status")
                    },
                    onComplete = { success, message ->
                        AppLogger.d(TAG, "Automation complete: success=$success, message=$message")
                    }
                )
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Automation started: $task")
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start automation", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to start automation: ${e.message}"
            )
        }
    }

    /**
     * Stop the current automation
     */
    suspend fun stopAutomation(tool: AITool): ToolResult {
        return try {
            withContext(Dispatchers.Main) {
                val controller = com.ai.assistance.operit.services.automation.AutomationController.getInstance(
                    tool.context
                )
                controller.stopAutomation()
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Automation stopped")
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to stop automation: ${e.message}"
            )
        }
    }

    /**
     * Check if automation is currently running
     */
    suspend fun isAutomationRunning(tool: AITool): ToolResult {
        return try {
            val controller = com.ai.assistance.operit.services.automation.AutomationController.getInstance(
                tool.context
            )
            val running = controller.isRunning()
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(if (running) "Automation is running" else "Automation is not running")
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to check automation status: ${e.message}"
            )
        }
    }

    /**
     * Get current screen analysis
     */
    suspend fun getScreenState(tool: AITool): ToolResult {
        return try {
            val controller = com.ai.assistance.operit.services.automation.AutomationController.getInstance(
                tool.context
            )
            
            val screenState = controller.getScreenAnalysis()
            
            if (screenState != null) {
                val stateText = buildString {
                    append("Activity: ${screenState.activityName}\n")
                    append("Keyboard Open: ${screenState.isKeyboardOpen}\n")
                    append("Scroll Up: ${screenState.scrollUp} pixels\n")
                    append("Scroll Down: ${screenState.scrollDown} pixels\n")
                    append("\n${screenState.uiRepresentation}")
                }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(stateText)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get screen state"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to get screen state: ${e.message}"
            )
        }
    }
}