package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Tool for executing ADB commands directly. This provides direct access to ADB shell commands for
 * system operations. Note: This requires Shizuku service to be running with proper permissions.
 */
open class StandardShellToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ADBToolExecutor"
        private const val DEFAULT_TIMEOUT = 15000L // 15 seconds
    }

    fun invoke(tool: AITool): ToolResult {
        // Validate parameters
        val validationResult = validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        val command = tool.parameters.find { it.name == "command" }?.value ?: ""
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value

        // Default behavior: always try to use terminal session first
        // This provides access to user-installed tools (ddgs, node, python, etc.)
        val actualSessionId = if (!sessionId.isNullOrBlank()) {
            sessionId
        } else {
            getOrCreateDefaultTerminalSession()
        }

        if (actualSessionId != null) {
            return executeInTerminalSession(actualSessionId, command)
        }

        // Fall back to Android shell if terminal not available

        return try {
            // Use AdbCommandExecutor to execute the command
            val result = runBlocking { AndroidShellExecutor.executeShellCommand(command) }

            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                ADBResultData(
                                        command = command,
                                        output = result.stdout,
                                        exitCode = result.exitCode
                                )
                )
            } else {
                // Combine stdout and stderr for error reporting
                val errorOutput =
                        if (result.stderr.isNotEmpty()) {
                            "${result.stderr.trim()}\n${result.stdout.trim()}"
                        } else {
                            result.stdout.trim()
                        }

                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "ADB command execution failed (exit code: ${result.exitCode}): $errorOutput"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing ADB command", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "ADB command execution failed: ${e.message}"
            )
        }
    }

    /** Validates the parameters for the ADB tool. */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value

        return when {
            command.isNullOrBlank() && sessionId.isNullOrBlank() -> {
                ToolValidationResult(valid = false, errorMessage = "Command parameter is required")
            }
            command.isNullOrBlank() && !sessionId.isNullOrBlank() -> {
                ToolValidationResult(valid = false, errorMessage = "Command parameter is required when session_id is provided")
            }
            command?.contains("rm -rf") == true || command?.contains("format") == true -> {
                ToolValidationResult(
                        valid = false,
                        errorMessage = "Potentially dangerous command detected"
                )
            }
            else -> {
                ToolValidationResult(valid = true)
            }
        }
    }

    /** Execute command in terminal session */
    private fun executeInTerminalSession(sessionId: String, command: String, toolName: String = "execute_shell"): ToolResult {
        return try {
            val terminal = Terminal.getInstance(context)
            val state = terminal.terminalState.value
            if (state.sessions.none { it.id == sessionId }) {
                return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = StringResultData(""),
                        error = "Terminal session not found: $sessionId"
                )
            }

            val outputFlow = terminal.executeCommandFlow(sessionId, command)
            if (outputFlow == null) {
                return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to start command execution"
                )
            }

            val events = mutableListOf<String>()
            var hasCompleted = false

            try {
                runBlocking {
                    withTimeout(DEFAULT_TIMEOUT) {
                        outputFlow.collect { event ->
                            if (event.outputChunk.isNotEmpty()) {
                                events.add(event.outputChunk)
                            }
                            if (event.isCompleted) {
                                hasCompleted = true
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                AppLogger.w(TAG, "Command execution timed out after ${DEFAULT_TIMEOUT}ms")
                hasCompleted = true
            }

            val fullOutput = events.joinToString("")
            ToolResult(
                    toolName = toolName,
                    success = hasCompleted,
                    result = ADBResultData(command = command, output = fullOutput, exitCode = if (hasCompleted) 0 else -1)
            )
        } catch (e: Exception) {
            ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "Terminal execution failed: ${e.message}"
            )
        }
    }

    /** Get or create a default terminal session */
    private fun getOrCreateDefaultTerminalSession(): String? {
        return try {
            val terminal = Terminal.getInstance(context)
            val state = terminal.terminalState.value
            // Return existing session
            state.sessions.firstOrNull()?.id
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get or create terminal session", e)
            null
        }
    }
}
