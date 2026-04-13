package com.ai.assistance.operit.data.exporter

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.converter.ChatFormat
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Import/Export Manager for chats, workflows, skills, and MCP servers
 *
 * This manager provides comprehensive import/export functionality for:
 * - Chats: Export to JSON/Markdown, Import from JSON/Markdown
 * - Workflows: Export to JSON, Import from JSON
 * - Skills: Export to ZIP, Import from ZIP
 * - MCP Servers: Export to JSON, Import from JSON
 */
class ImportExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ImportExportManager"
        private const val EXPORT_DIR_NAME = "Twent"
        private const val CHATS_DIR = "chats"
        private const val WORKFLOWS_DIR = "workflows"
        private const val SKILLS_DIR = "skills"
        private const val MCP_DIR = "mcp_servers"

        @Volatile
        private var INSTANCE: ImportExportManager? = null

        fun getInstance(context: Context): ImportExportManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImportExportManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val chatHistoryManager by lazy { ChatHistoryManager.getInstance(context) }
    private val workflowRepository by lazy { WorkflowRepository(context) }
    private val skillRepository by lazy { SkillRepository.getInstance(context) }
    private val mcpLocalServer by lazy { MCPLocalServer.getInstance(context) }

    /**
     * Get the export directory
     */
    private fun getExportDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, EXPORT_DIR_NAME)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Generate a timestamp-based filename
     */
    private fun generateTimestampedFilename(baseName: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${baseName}_$timestamp.$extension"
    }

    // ==================== CHAT EXPORT/IMPORT ====================

    /**
     * Export all chats to a ZIP file
     */
    suspend fun exportAllChats(): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Starting export of all chats")

            // Use the existing export function from ChatHistoryManager
            val exportPath = chatHistoryManager.exportChatHistoriesToDownloads()
            if (exportPath == null) {
                return@withContext Result.failure(Exception("No chats to export or export failed"))
            }

            val exportFile = File(exportPath)
            AppLogger.d(TAG, "Exported chats to ${exportFile.absolutePath}")
            Result.success(exportFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export chats", e)
            Result.failure(e)
        }
    }

    /**
     * Export a specific chat by ID
     */
    suspend fun exportChat(chatId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Exporting chat: $chatId")

            // Get chat from database directly
            val database = com.ai.assistance.operit.data.db.AppDatabase.getDatabase(context)
            val chatEntity = database.chatDao().getChatById(chatId)
                ?: return@withContext Result.failure(Exception("Chat not found: $chatId"))
            val chat = chatEntity.toChatHistory(emptyList())

            val exportDir = getExportDirectory()
            val safeName = chat.title?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: chatId
            val zipFile = File(exportDir, generateTimestampedFilename("chat_$safeName", "zip"))

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val chatJson = json.encodeToString(chat)
                val entry = ZipEntry("${chat.id}.json")
                zos.putNextEntry(entry)
                zos.write(chatJson.toByteArray())
                zos.closeEntry()
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export chat: $chatId", e)
            Result.failure(e)
        }
    }

    /**
     * Import chats from a ZIP file
     */
    suspend fun importChats(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Importing chats from: $uri")

            // Use the existing import function from ChatHistoryManager with Operit format
            val importResult = chatHistoryManager.importChatHistoriesFromUri(uri, com.ai.assistance.operit.data.converter.ChatFormat.OPERIT)

            AppLogger.d(TAG, "Imported ${importResult.new} chats")
            Result.success(importResult.new)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import chats", e)
            Result.failure(e)
        }
    }

    // ==================== WORKFLOW EXPORT/IMPORT ====================

    /**
     * Export all workflows to a ZIP file
     */
    suspend fun exportAllWorkflows(): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Starting export of all workflows")

            val workflowsResult = workflowRepository.getAllWorkflows()
            val workflows = workflowsResult.getOrNull()
            if (workflows.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No workflows to export"))
            }

            val exportDir = getExportDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "workflows_export_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                workflows.forEach { workflow ->
                    val workflowJson = json.encodeToString(workflow)
                    val entry = ZipEntry("workflows/${workflow.id}.json")
                    zos.putNextEntry(entry)
                    zos.write(workflowJson.toByteArray())
                    zos.closeEntry()
                }
            }

            AppLogger.d(TAG, "Exported ${workflows.size} workflows to ${zipFile.absolutePath}")
            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export workflows", e)
            Result.failure(e)
        }
    }

    /**
     * Export a specific workflow by ID
     */
    suspend fun exportWorkflow(workflowId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Exporting workflow: $workflowId")

            val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
                ?: return@withContext Result.failure(Exception("Workflow not found: $workflowId"))

            val exportDir = getExportDirectory()
            val safeName = workflow.name.replace(Regex("[^a-zA-Z0-9]"), "_")
            val zipFile = File(exportDir, generateTimestampedFilename("workflow_$safeName", "zip"))

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val workflowJson = json.encodeToString(workflow)
                val entry = ZipEntry("${workflow.id}.json")
                zos.putNextEntry(entry)
                zos.write(workflowJson.toByteArray())
                zos.closeEntry()
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export workflow: $workflowId", e)
            Result.failure(e)
        }
    }

    /**
     * Import workflows from a ZIP file
     */
    suspend fun importWorkflows(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Importing workflows from: $uri")

            var importedCount = 0

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".json") && !entry.isDirectory) {
                            try {
                                val content = zis.bufferedReader().readText()
                                val workflow = json.decodeFromString<Workflow>(content)
                                // Generate new ID to avoid conflicts
                                val newWorkflow = workflow.copy(id = java.util.UUID.randomUUID().toString())
                                workflowRepository.createWorkflow(newWorkflow)
                                importedCount++
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Failed to import workflow from ${entry.name}: ${e.message}")
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            AppLogger.d(TAG, "Imported $importedCount workflows")
            Result.success(importedCount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import workflows", e)
            Result.failure(e)
        }
    }

    // ==================== SKILL EXPORT/IMPORT ====================

    /**
     * Export all skills to a ZIP file
     */
    suspend fun exportAllSkills(): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Starting export of all skills")

            val skillsDir = File(skillRepository.getSkillsDirectoryPath())
            if (!skillsDir.exists() || skillsDir.listFiles()?.isEmpty() != false) {
                return@withContext Result.failure(Exception("No skills to export"))
            }

            val exportDir = getExportDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "skills_export_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                    val skillName = skillDir.name
                    addDirectoryToZip(zos, skillDir, "skills/$skillName")
                }
            }

            AppLogger.d(TAG, "Exported skills to ${zipFile.absolutePath}")
            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export skills", e)
            Result.failure(e)
        }
    }

    /**
     * Export a specific skill by name
     */
    suspend fun exportSkill(skillName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Exporting skill: $skillName")

            val skillsDir = File(skillRepository.getSkillsDirectoryPath())
            val skillDir = File(skillsDir, skillName)

            if (!skillDir.exists() || !skillDir.isDirectory) {
                return@withContext Result.failure(Exception("Skill not found: $skillName"))
            }

            val exportDir = getExportDirectory()
            val safeName = skillName.replace(Regex("[^a-zA-Z0-9]"), "_")
            val zipFile = File(exportDir, generateTimestampedFilename("skill_$safeName", "zip"))

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                addDirectoryToZip(zos, skillDir, skillName)
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export skill: $skillName", e)
            Result.failure(e)
        }
    }

    /**
     * Import skills from a ZIP file
     */
    suspend fun importSkills(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Importing skills from: $uri")

            var importedCount = 0

            // Copy ZIP to temp file first
            val tempFile = File(context.cacheDir, "imported_skill.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            try {
                val result = skillRepository.importSkillFromZip(tempFile)
                if (result.contains("imported") || result.contains("success")) {
                    importedCount = 1
                }
            } finally {
                tempFile.delete()
            }

            AppLogger.d(TAG, "Imported $importedCount skills")
            Result.success(importedCount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import skills", e)
            Result.failure(e)
        }
    }

    // ==================== MCP SERVER EXPORT/IMPORT ====================

    /**
     * Export all MCP server configurations to a ZIP file
     */
    suspend fun exportAllMCPServers(): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Starting export of all MCP servers")

            val mcpConfig = mcpLocalServer.mcpConfig.value
            if (mcpConfig.mcpServers.isEmpty()) {
                return@withContext Result.failure(Exception("No MCP servers to export"))
            }

            val exportDir = getExportDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "mcp_servers_export_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Export MCP config
                val configJson = gson.toJson(mcpConfig)
                val configEntry = ZipEntry("mcp_config.json")
                zos.putNextEntry(configEntry)
                zos.write(configJson.toByteArray())
                zos.closeEntry()

                // Export each server config separately
                mcpConfig.mcpServers.forEach { (serverId, serverConfig) ->
                    val serverJson = gson.toJson(serverConfig)
                    val entry = ZipEntry("servers/$serverId.json")
                    zos.putNextEntry(entry)
                    zos.write(serverJson.toByteArray())
                    zos.closeEntry()
                }

                // Export plugin metadata
                if (mcpConfig.pluginMetadata.isNotEmpty()) {
                    val metadataJson = gson.toJson(mcpConfig.pluginMetadata)
                    val metadataEntry = ZipEntry("plugin_metadata.json")
                    zos.putNextEntry(metadataEntry)
                    zos.write(metadataJson.toByteArray())
                    zos.closeEntry()
                }
            }

            AppLogger.d(TAG, "Exported ${mcpConfig.mcpServers.size} MCP servers to ${zipFile.absolutePath}")
            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export MCP servers", e)
            Result.failure(e)
        }
    }

    /**
     * Export a specific MCP server by ID
     */
    suspend fun exportMCPServer(serverId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Exporting MCP server: $serverId")

            val mcpConfig = mcpLocalServer.mcpConfig.value
            val serverConfig = mcpConfig.mcpServers[serverId]
                ?: return@withContext Result.failure(Exception("MCP server not found: $serverId"))

            val exportDir = getExportDirectory()
            val safeName = serverId.replace(Regex("[^a-zA-Z0-9]"), "_")
            val zipFile = File(exportDir, generateTimestampedFilename("mcp_server_$safeName", "zip"))

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val serverJson = gson.toJson(serverConfig)
                val entry = ZipEntry("$serverId.json")
                zos.putNextEntry(entry)
                zos.write(serverJson.toByteArray())
                zos.closeEntry()

                // Also export metadata if available
                mcpConfig.pluginMetadata[serverId]?.let { metadata ->
                    val metadataJson = gson.toJson(metadata)
                    val metadataEntry = ZipEntry("metadata.json")
                    zos.putNextEntry(metadataEntry)
                    zos.write(metadataJson.toByteArray())
                    zos.closeEntry()
                }
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export MCP server: $serverId", e)
            Result.failure(e)
        }
    }

    /**
     * Import MCP servers from a ZIP file
     */
    suspend fun importMCPServers(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Importing MCP servers from: $uri")

            var importedCount = 0
            val currentConfig = mcpLocalServer.mcpConfig.value

            // Copy to temp file first
            val tempFile = File(context.cacheDir, "imported_mcp.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            ZipInputStream(FileInputStream(tempFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "mcp_config.json" -> {
                            try {
                                val content = zis.bufferedReader().readText()
                                val importedConfig = gson.fromJson(content, MCPLocalServer.MCPConfig::class.java)

                                // Merge servers and persist them
                                importedConfig.mcpServers.forEach { (serverId, serverConfig) ->
                                    if (!currentConfig.mcpServers.containsKey(serverId)) {
                                        // Persist the imported server
                                        mcpLocalServer.addOrUpdateMCPServer(
                                            serverId = serverId,
                                            command = serverConfig.command,
                                            args = serverConfig.args,
                                            env = serverConfig.env,
                                            disabled = serverConfig.disabled,
                                            autoApprove = serverConfig.autoApprove
                                        )
                                        importedCount++
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Failed to import MCP config: ${e.message}")
                            }
                        }
                        entry.name.startsWith("servers/") && entry.name.endsWith(".json") -> {
                            try {
                                val serverId = entry.name.substringAfter("servers/").substringBefore(".json")
                                val content = zis.bufferedReader().readText()
                                val serverConfig = gson.fromJson(content, MCPLocalServer.MCPConfig.ServerConfig::class.java)

                                if (!currentConfig.mcpServers.containsKey(serverId)) {
                                    // Persist the imported server
                                    mcpLocalServer.addOrUpdateMCPServer(
                                        serverId = serverId,
                                        command = serverConfig.command,
                                        args = serverConfig.args,
                                        env = serverConfig.env,
                                        disabled = serverConfig.disabled,
                                        autoApprove = serverConfig.autoApprove
                                    )
                                    importedCount++
                                }
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Failed to import server from ${entry.name}: ${e.message}")
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            tempFile.delete()

            AppLogger.d(TAG, "Imported $importedCount MCP servers successfully")
            Result.success(importedCount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import MCP servers", e)
            Result.failure(e)
        }
    }

    /**
     * Import MCP servers from a JSON file (single server)
     */
    suspend fun importMCPServerFromJson(uri: Uri, serverId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Importing MCP server from JSON: $serverId")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                val serverConfig = gson.fromJson(content, MCPLocalServer.MCPConfig.ServerConfig::class.java)
                mcpLocalServer.addOrUpdateMCPServer(
                    serverId = serverId,
                    command = serverConfig.command,
                    args = serverConfig.args,
                    env = serverConfig.env,
                    disabled = serverConfig.disabled,
                    autoApprove = serverConfig.autoApprove
                )
            }

            Result.success(true)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import MCP server from JSON", e)
            Result.failure(e)
        }
    }

    // ==================== COMPLETE EXPORT (ALL DATA) ====================

    /**
     * Export all data (chats, workflows, skills, MCP servers) to a single ZIP file
     */
    suspend fun exportAllData(): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Starting export of all data")

            val exportDir = getExportDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "operit_backup_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Export manifest
                val manifest = mapOf(
                    "version" to "1.0",
                    "exportDate" to timestamp,
                    "app" to "Twent"
                )
                val manifestJson = gson.toJson(manifest)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJson.toByteArray())
                zos.closeEntry()

                // Export chats
                try {
                    val database = com.ai.assistance.operit.data.db.AppDatabase.getDatabase(context)
                    val chatEntities = database.chatDao().getAllChatsDirectly()
                    val chats = chatEntities.map { it.toChatHistory(emptyList()) }
                    if (chats.isNotEmpty()) {
                        chats.forEach { chat ->
                            val chatJson = json.encodeToString(chat)
                            val entry = ZipEntry("chats/${chat.id}.json")
                            zos.putNextEntry(entry)
                            zos.write(chatJson.toByteArray())
                            zos.closeEntry()
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to export chats: ${e.message}")
                }

                // Export workflows
                try {
                    val workflowsResult = workflowRepository.getAllWorkflows()
                    workflowsResult.getOrNull()?.forEach { workflow ->
                        val workflowJson = json.encodeToString(workflow)
                        val entry = ZipEntry("workflows/${workflow.id}.json")
                        zos.putNextEntry(entry)
                        zos.write(workflowJson.toByteArray())
                        zos.closeEntry()
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to export workflows: ${e.message}")
                }

                // Export skills
                try {
                    val skillsDir = File(skillRepository.getSkillsDirectoryPath())
                    skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                        addDirectoryToZip(zos, skillDir, "skills/${skillDir.name}")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to export skills: ${e.message}")
                }

                // Export MCP config
                try {
                    val mcpConfig = mcpLocalServer.mcpConfig.value
                    if (mcpConfig.mcpServers.isNotEmpty()) {
                        val configJson = gson.toJson(mcpConfig)
                        val configEntry = ZipEntry("mcp_config.json")
                        zos.putNextEntry(configEntry)
                        zos.write(configJson.toByteArray())
                        zos.closeEntry()
                    } else {
                        AppLogger.d(TAG, "No MCP servers to export")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to export MCP config: ${e.message}")
                }
            }

            AppLogger.d(TAG, "Exported all data to ${zipFile.absolutePath}")
            Result.success(zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to export all data", e)
            Result.failure(e)
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Add a directory and its contents to a ZIP file
     */
    private fun addDirectoryToZip(zos: ZipOutputStream, sourceDir: File, basePath: String) {
        sourceDir.listFiles()?.forEach { file ->
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                val entry = ZipEntry("$entryPath/")
                zos.putNextEntry(entry)
                zos.closeEntry()
                addDirectoryToZip(zos, file, entryPath)
            } else {
                val entry = ZipEntry(entryPath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    /**
     * Get list of available skills for export
     */
    fun getAvailableSkillsForExport(): List<String> {
        val skillsDir = File(skillRepository.getSkillsDirectoryPath())
        return skillsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    /**
     * Get list of available workflows for export
     */
    suspend fun getAvailableWorkflowsForExport(): List<Workflow> {
        return workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
    }

    /**
     * Get list of available MCP servers for export
     */
    fun getAvailableMCPServersForExport(): List<String> {
        return mcpLocalServer.mcpConfig.value.mcpServers.keys.toList()
    }

    /**
     * Get list of available chats for export
     */
    suspend fun getAvailableChatsForExport(): List<ChatHistory> {
        return try {
            // Get from chatDao directly
            val database = com.ai.assistance.operit.data.db.AppDatabase.getDatabase(context)
            val chatEntities = database.chatDao().getAllChatsDirectly()
            chatEntities.map { entity -> entity.toChatHistory(emptyList()) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get chats for export", e)
            emptyList()
        }
    }
}

/**
 * Result class for import operations
 */
data class ImportResult(
    val success: Boolean,
    val importedCount: Int,
    val message: String
)

/**
 * Export type enum
 */
enum class ExportType {
    CHATS,
    WORKFLOWS,
    SKILLS,
    MCP_SERVERS,
    ALL
}

/**
 * Import type enum
 */
enum class ImportType {
    CHATS,
    WORKFLOWS,
    SKILLS,
    MCP_SERVERS
}
