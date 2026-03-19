package com.ai.assistance.operit.core.tools.creator

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Tool creators for the AI Agent to create Packages, MCPs, and Skills
 */
class PackageCreatorTools(private val context: Context) {

    companion object {
        private const val TAG = "PackageCreatorTools"

        /**
         * Register all creator tools with the AIToolHandler
         */
        fun registerCreatorTools(handler: AIToolHandler, context: Context) {
            val creatorTools = PackageCreatorTools(context)

            // Create Package tool
            handler.registerTool(
                name = "create_package",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                    "Create a new Operit Package: $name - $description"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.createPackage(tool)
                    }
                }
            )

            // Create MCP Server tool
            handler.registerTool(
                name = "create_mcp_server",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    val serverType = tool.parameters.find { it.name == "server_type" }?.value ?: "npx"
                    "Create a new MCP Server: $name (type: $serverType)"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.createMCPServer(tool)
                    }
                }
            )

            // Create Skill tool
            handler.registerTool(
                name = "create_skill",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                    "Create a new Operit Skill: $name - $description"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.createSkill(tool)
                    }
                }
            )

            // List available packages
            handler.registerTool(
                name = "list_packages",
                dangerCheck = { false },
                descriptionGenerator = { _ ->
                    "List all available Operit Packages"
                },
                executor = { tool ->
                    creatorTools.listPackages(tool)
                }
            )

            // List MCP servers
            handler.registerTool(
                name = "list_mcp_servers",
                dangerCheck = { false },
                descriptionGenerator = { _ ->
                    "List all available MCP Servers"
                },
                executor = { tool ->
                    creatorTools.listMCPServers(tool)
                }
            )

            // List Skills
            handler.registerTool(
                name = "list_skills",
                dangerCheck = { false },
                descriptionGenerator = { _ ->
                    "List all available Operit Skills"
                },
                executor = { tool ->
                    creatorTools.listSkills(tool)
                }
            )

            // Delete Package tool
            handler.registerTool(
                name = "delete_package",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Delete Operit Package: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.deletePackage(tool)
                    }
                }
            )

            // Delete MCP Server tool
            handler.registerTool(
                name = "delete_mcp_server",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Delete MCP Server: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.deleteMCPServer(tool)
                    }
                }
            )

            // Delete Skill tool
            handler.registerTool(
                name = "delete_skill",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Delete Operit Skill: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.deleteSkill(tool)
                    }
                }
            )
        }
    }

    private val packageManager by lazy {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }

    private val skillManager by lazy {
        SkillManager.getInstance(context)
    }

    private val mcpLocalServer by lazy {
        MCPLocalServer.getInstance(context)
    }

    /**
     * Create a new Operit Package
     */
    private suspend fun createPackage(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value ?: ""
        val code = tool.parameters.find { it.name == "code" }?.value ?: ""
        val tools = tool.parameters.find { it.name == "tools" }?.value ?: "[]"

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Package name is required"
            )
        }

        if (code.isBlank() && tools == "[]") {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Package code or tools definition is required"
            )
        }

        return try {
            // Generate package code if not provided
            val packageCode = if (code.isNotBlank()) {
                code
            } else {
                generatePackageCode(name, description, tools)
            }

            // Save to external packages directory
            val packageDir = packageManager.getExternalPackagesPath()
            val packageFile = File(packageDir, "$name.js")

            // Create parent directory if needed
            packageFile.parentFile?.mkdirs()

            // Write the package file
            packageFile.writeText(packageCode)

            // Import the package
            val importResult = packageManager.importPackageFromExternalStorage(packageFile.absolutePath)

            if (importResult.contains("Successfully")) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(
                        "Package '$name' created successfully!\n" +
                        "Location: ${packageFile.absolutePath}\n\n" +
                        "To use the package, say 'use package $name' or use the 'use_package' tool."
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = importResult
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create package: ${e.message}"
            )
        }
    }

    /**
     * Generate package code from tools definition
     */
    private fun generatePackageCode(name: String, description: String, toolsJson: String): String {
        val toolDefs = try {
            parseToolDefinitions(toolsJson)
        } catch (e: Exception) {
            emptyList()
        }

        val toolsArray = toolDefs.joinToString(",\n") { tool ->
            val params = tool.params.joinToString(", ") { param ->
                "${param.name}: ${param.type}"
            }
            """
    {
      name: "${tool.name}",
      description: {
        zh: "${tool.description}",
        en: "${tool.description}"
      },
      parameters: [${tool.params.joinToString(", ") { p -> "{ name: \"${p.name}\", type: \"${p.type}\", description: \"${p.description}\", required: ${p.required} }" }}]
    }""".trimIndent()
        }

        return """/* METADATA
{
  name: $name
  description: {
    zh: "$description"
    en: "$description"
  }
  enabledByDefault: false
  
  tools: [
    $toolsArray
  ]
}
*/
const $name = (function () {
${toolDefs.joinToString("\n") { tool ->
    val params = tool.params.joinToString(", ") { p -> p.name }
    """
    async function ${tool.name}($params) {
        // TODO: Implement ${tool.name}
        return { success: true, message: "${tool.name} executed" };
    }"""
}}

    return {
${toolDefs.joinToString(",\n") { "        ${it.name}: ${it.name}" }}
    };
})();

export default $name;
"""
    }

    /**
     * Parse tool definitions from JSON
     */
    private fun parseToolDefinitions(toolsJson: String): List<ToolDef> {
        val tools = mutableListOf<ToolDef>()
        try {
            // Simple JSON parsing - in production, use proper JSON parser
            val toolMatches = Regex("""\{[^}]*name\s*:\s*"([^"]+)"[^}]*\}""").findAll(toolsJson)
            for (match in toolMatches) {
                val toolContent = match.value
                val name = Regex("""name\s*:\s*"([^"]+)""").find(toolContent)?.groupValues?.get(1) ?: continue
                val desc = Regex("""description\s*:\s*"([^"]+)""").find(toolContent)?.groupValues?.get(1) ?: ""
                val params = mutableListOf<ParamDef>()
                
                val paramMatches = Regex("""parameters\s*:\s*\[[^\]]*\]""").find(toolContent)
                if (paramMatches != null) {
                    val paramContent = paramMatches.value
                    val paramNameMatches = Regex("""name\s*:\s*"([^"]+)""").findAll(paramContent)
                    for (pm in paramNameMatches) {
                        params.add(ParamDef(pm.groupValues[1], "string", "", false))
                    }
                }
                
                tools.add(ToolDef(name, desc, params))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing tool definitions", e)
        }
        return tools
    }

    data class ToolDef(val name: String, val description: String, val params: List<ParamDef>)
    data class ParamDef(val name: String, val type: String, val description: String, val required: Boolean)

    /**
     * Create a new MCP Server
     */
    private suspend fun createMCPServer(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value ?: ""
        val serverType = tool.parameters.find { it.name == "server_type" }?.value ?: "npx"
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val argsStr = tool.parameters.find { it.name == "args" }?.value ?: "[]"
        val envStr = tool.parameters.find { it.name == "env" }?.value ?: "{}"

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "MCP Server name is required"
            )
        }

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Package name is required for MCP Server"
            )
        }

        return try {
            // Generate server ID from name
            val serverId = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            
            // Parse args list
            val argsList = parseArgsList(argsStr, packageName)
            
            // Parse env map
            val envMap = parseEnvMap(envStr)
            
            // Save the MCP server configuration
            mcpLocalServer.addOrUpdateMCPServer(
                serverId = serverId,
                command = serverType,
                args = argsList,
                env = envMap
            )
            
            // Add plugin metadata
            val metadata = MCPLocalServer.PluginMetadata(
                id = serverId,
                name = name,
                description = description,
                type = serverType,
                isInstalled = false,
                version = "1.0.0",
                author = "User Created"
            )
            mcpLocalServer.addOrUpdatePluginMetadata(metadata)
            
            // Refresh available MCP servers
            skillManager.refreshAvailableSkills()

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(
                    "MCP Server '$name' created successfully!\n" +
                    "Server ID: $serverId\n" +
                    "Type: $serverType\n" +
                    "Package: $packageName\n\n" +
                    "The MCP Server has been configured. You may need to restart the AI or re-initialize MCP for changes to take effect."
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create MCP server: ${e.message}"
            )
        }
    }

    /**
     * Parse args list from string
     */
    private fun parseArgsList(argsStr: String, defaultValue: String): List<String> {
        return try {
            if (argsStr.startsWith("[")) {
                argsStr.removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
            } else {
                listOf(defaultValue)
            }
        } catch (e: Exception) {
            listOf(defaultValue)
        }
    }

    /**
     * Parse env map from string
     */
    private fun parseEnvMap(envStr: String): Map<String, String> {
        return try {
            if (envStr.startsWith("{")) {
                envStr.removePrefix("{").removeSuffix("}")
                    .split(",")
                    .mapNotNull { pair ->
                        val kv = pair.split(":")
                        if (kv.size == 2) {
                            val key = kv[0].trim().removeSurrounding("\"")
                            val value = kv[1].trim().removeSurrounding("\"")
                            if (key.isNotBlank()) key to value else null
                        } else null
                    }
                    .toMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Create a new Skill
     */
    private suspend fun createSkill(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value ?: ""
        val guidelines = tool.parameters.find { it.name == "guidelines" }?.value ?: ""
        val implementation = tool.parameters.find { it.name == "implementation" }?.value ?: ""

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Skill name is required"
            )
        }

        return try {
            // Get skills directory
            val skillsDir = skillManager.getSkillsDirectoryPath()
            val skillDir = File(skillsDir, name)

            if (skillDir.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' already exists"
                )
            }

            // Create skill directory
            skillDir.mkdirs()

            // Generate SKILL.md content
            val skillContent = buildSkillContent(name, description, guidelines, implementation)
            val skillFile = File(skillDir, "SKILL.md")
            skillFile.writeText(skillContent)

            // Refresh available skills
            skillManager.refreshAvailableSkills()

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(
                    "Skill '$name' created successfully!\n" +
                    "Location: ${skillFile.absolutePath}\n\n" +
                    "The skill has been created with SKILL.md file. You can now use this skill."
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create skill", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create skill: ${e.message}"
            )
        }
    }

    /**
     * Build skill content from parameters
     */
    private fun buildSkillContent(
        name: String,
        description: String,
        guidelines: String,
        implementation: String
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val createdDate = dateFormat.format(Date())

        return """---
name: "$name"
description: "$description"
created: "$createdDate"
version: "1.0.0"
---

# $name

$description

## Guidelines

${if (guidelines.isNotBlank()) guidelines else "Add your skill guidelines here."}

## Implementation

${if (implementation.isNotBlank()) implementation else "Add your skill implementation details here."}

## Usage

Describe how to use this skill...

## Examples

Provide usage examples here...
"""
    }

    /**
     * List all available packages
     */
    private fun listPackages(tool: AITool): ToolResult {
        return try {
            val packages = packageManager.getAvailablePackages()
            val imported = packageManager.getImportedPackages()

            val sb = StringBuilder()
            sb.appendLine("## Available Packages (${packages.size})")
            sb.appendLine()

            if (packages.isEmpty()) {
                sb.appendLine("No packages available.")
            } else {
                packages.forEach { (name, pkg) ->
                    val status = if (imported.contains(name)) "[imported]" else "[available]"
                    sb.appendLine("- **$name** $status")
                    sb.appendLine("  - ${pkg.description}")
                    sb.appendLine()
                }
            }

            sb.appendLine("## External Packages Directory")
            sb.appendLine(packageManager.getExternalPackagesPath())

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(sb.toString())
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list packages", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to list packages: ${e.message}"
            )
        }
    }

    /**
     * List all MCP servers
     */
    private fun listMCPServers(tool: AITool): ToolResult {
        return try {
            val servers = mcpLocalServer.getAllPluginMetadata()

            val sb = StringBuilder()
            sb.appendLine("## MCP Servers (${servers.size})")
            sb.appendLine()

            if (servers.isEmpty()) {
                sb.appendLine("No MCP servers configured.")
            } else {
                servers.forEach { (serverId, server) ->
                    val status = if (server.isInstalled) "[installed]" else "[not installed]"
                    sb.appendLine("- **${server.name}** $status")
                    sb.appendLine("  - ID: ${server.id}")
                    sb.appendLine("  - Type: ${server.type}")
                    sb.appendLine("  - ${server.description}")
                    sb.appendLine()
                }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(sb.toString())
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list MCP servers", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to list MCP servers: ${e.message}"
            )
        }
    }

    /**
     * List all skills
     */
    private fun listSkills(tool: AITool): ToolResult {
        return try {
            val skills = skillManager.getAvailableSkills()

            val sb = StringBuilder()
            sb.appendLine("## Skills (${skills.size})")
            sb.appendLine()

            if (skills.isEmpty()) {
                sb.appendLine("No skills available.")
            } else {
                skills.forEach { (name, skillPkg) ->
                    sb.appendLine("- **$name**")
                    sb.appendLine("  - ${skillPkg.description}")
                    sb.appendLine("  - Location: ${skillPkg.directory.absolutePath}")
                    sb.appendLine()
                }
            }

            sb.appendLine("## Skills Directory")
            sb.appendLine(skillManager.getSkillsDirectoryPath())

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(sb.toString())
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list skills", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to list skills: ${e.message}"
            )
        }
    }

    /**
     * Delete a package
     */
    private suspend fun deletePackage(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Package name is required"
            )
        }

        return try {
            val packagesDir = packageManager.getExternalPackagesPath()
            val packageFile = File(packagesDir, "$name.js")

            if (!packageFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Package '$name' not found"
                )
            }

            val deleted = packageFile.delete()

            if (deleted) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Package '$name' deleted successfully")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete package file"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete package: ${e.message}"
            )
        }
    }

    /**
     * Delete an MCP server
     */
    private suspend fun deleteMCPServer(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "MCP Server name is required"
            )
        }

        return try {
            // Find server by name
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            // Remove from configuration
            mcpLocalServer.removeMCPServer(server.id)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("MCP Server '$name' deleted successfully")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete MCP server: ${e.message}"
            )
        }
    }

    /**
     * Delete a skill
     */
    private suspend fun deleteSkill(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Skill name is required"
            )
        }

        return try {
            val deleted = skillManager.deleteSkill(name)

            if (deleted) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Skill '$name' deleted successfully")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' not found or could not be deleted"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete skill", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete skill: ${e.message}"
            )
        }
    }
}
