package com.ai.assistance.operit.core.tools.system.shell

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * 标准的 ShellProcess 实现，使用 Runtime.exec()
 */
class StandardShellProcess(command: String) : ShellProcess {
    private val process: Process = if (containsShellOperators(command)) {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    } else {
        Runtime.getRuntime().exec(command)
    }

    override val stdout: Flow<String> = callbackFlow {
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    trySend(line!!)
                }
            }
        } catch (e: Exception) {
            // Process ended or error occurred
        }
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override val stderr: Flow<String> = callbackFlow {
        try {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    trySend(line!!)
                }
            }
        } catch (e: Exception) {
            // Process ended or error occurred
        }
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }

    companion object {
        /**
         * 检测命令是否包含需要shell解释的特殊操作符
         */
        private fun containsShellOperators(command: String): Boolean {
            // 预处理：标记引号内的内容，避免检测引号内的操作符
            var inSingleQuotes = false
            var inDoubleQuotes = false
            var escaped = false
            var i = 0

            while (i < command.length) {
                val c = command[i]

                // 处理转义字符
                if (c == '\\' && !escaped) {
                    escaped = true
                    i++
                    continue
                }

                // 处理引号
                if (c == '\'' && !escaped && !inDoubleQuotes) {
                    inSingleQuotes = !inSingleQuotes
                } else if (c == '"' && !escaped && !inSingleQuotes) {
                    inDoubleQuotes = !inDoubleQuotes
                }
                // 只在不在引号内时检测操作符
                else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                    // 检测管道
                    if (c == '|') {
                        return true
                    }

                    // 检测 && 和 & 操作符
                    if (c == '&') {
                        return true
                    }

                    // 检测重定向
                    if (c == '>' || c == '<') {
                        return true
                    }

                    // 检测分号
                    if (c == ';') {
                        return true
                    }
                }

                escaped = false
                i++
            }

            return false
        }
    }
}
