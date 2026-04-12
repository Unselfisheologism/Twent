package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 基于ACCESSIBILITY权限的Shell命令执行器 */
class DebuggerShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "DebuggerShellExecutor"
        private const val COMMAND_TIMEOUT = 30L
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ACCESSIBILITY

    override fun isAvailable(): Boolean = true

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        return ShellExecutor.PermissionStatus.granted()
    }

    override fun initialize() {
        // No initialization needed for ACCESSIBILITY
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        onResult(true)
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "Executing ACCESSIBILITY command: $command")

                    if (containsShellOperators(command)) {
                        return@withContext executeWithShell(command)
                    }

                    val process = Runtime.getRuntime().exec(command)
                    val completed = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroy()
                        return@withContext ShellExecutor.CommandResult(
                                false,
                                "",
                                "Command timed out after $COMMAND_TIMEOUT seconds",
                                -1
                        )
                    }

                    val stdout =
                            BufferedReader(InputStreamReader(process.inputStream)).use {
                                it.readText()
                            }

                    val stderr =
                            BufferedReader(InputStreamReader(process.errorStream)).use {
                                it.readText()
                            }

                    val exitCode = process.exitValue()

                    return@withContext ShellExecutor.CommandResult(
                            exitCode == 0,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing command", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "Error: ${e.message}",
                            -1
                    )
                }
            }

    override suspend fun startProcess(command: String): ShellProcess = withContext(Dispatchers.IO) {
        StandardShellProcess(command)
    }

    private suspend fun executeWithShell(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

                    val completed = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroy()
                        return@withContext ShellExecutor.CommandResult(
                                false,
                                "",
                                "Command timed out after $COMMAND_TIMEOUT seconds",
                                -1
                        )
                    }

                    val stdout =
                            BufferedReader(InputStreamReader(process.inputStream)).use {
                                it.readText()
                            }

                    val stderr =
                            BufferedReader(InputStreamReader(process.errorStream)).use {
                                it.readText()
                            }

                    val exitCode = process.exitValue()

                    val success =
                            if (command.contains("grep")) {
                                exitCode == 0 || exitCode == 1
                            } else {
                                exitCode == 0
                            }

                    return@withContext ShellExecutor.CommandResult(
                            success,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing shell command", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "Error: ${e.message}",
                            -1
                    )
                }
            }

    private fun containsShellOperators(command: String): Boolean {
        var inSingleQuotes = false
        var inDoubleQuotes = false
        var escaped = false
        var i = 0

        while (i < command.length) {
            val c = command[i]

            if (c == '\\' && !escaped) {
                escaped = true
                i++
                continue
            }

            if (c == '\'' && !escaped && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
            } else if (c == '"' && !escaped && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
            }
            else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                if (c == '|') return true
                if (c == '&') return true
                if (c == '>' || c == '<') return true
                if (c == ';') return true
            }

            escaped = false
            i++
        }

        return false
    }
}
