package com.ai.assistance.operit.core.agent.fs

import android.content.Context
import java.io.File

class FileSystem(private val context: Context) {

    private val agentDir: File by lazy {
        File(context.filesDir, "agent").also { it.mkdirs() }
    }

    fun readFile(fileName: String): String? {
        return try {
            val file = File(agentDir, fileName)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun writeFile(fileName: String, content: String) {
        try {
            val file = File(agentDir, fileName)
            file.writeText(content)
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun appendFile(fileName: String, content: String) {
        try {
            val file = File(agentDir, fileName)
            file.appendText(content + "\n")
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun deleteFile(fileName: String): Boolean {
        return try {
            val file = File(agentDir, fileName)
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun listFiles(): List<String> {
        return agentDir.list()?.toList() ?: emptyList()
    }
}