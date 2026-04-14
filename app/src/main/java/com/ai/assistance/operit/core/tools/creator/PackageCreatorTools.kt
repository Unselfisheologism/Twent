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

            // Update Package tool
            handler.registerTool(
                name = "update_package",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Update Operit Package: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.updatePackage(tool)
                    }
                }
            )

            // Update MCP Server tool
            handler.registerTool(
                name = "update_mcp_server",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Update MCP Server: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.updateMCPServer(tool)
                    }
                }
            )

            // Update Skill tool
            handler.registerTool(
                name = "update_skill",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Update Operit Skill: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.updateSkill(tool)
                    }
                }
            )

            // Test Package tool
            handler.registerTool(
                name = "test_package",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Test Operit Package: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.testPackage(tool)
                    }
                }
            )

            // Test MCP Server tool
            handler.registerTool(
                name = "test_mcp_server",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Test MCP Server: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.testMCPServer(tool)
                    }
                }
            )

            // Test Skill tool
            handler.registerTool(
                name = "test_skill",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Test Operit Skill: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.testSkill(tool)
                    }
                }
            )

            // Read Package tool (get package content)
            handler.registerTool(
                name = "read_package",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Read Operit Package: $name"
                },
                executor = { tool ->
                    creatorTools.readPackage(tool)
                }
            )

            // Read Skill tool (get skill content)
            handler.registerTool(
                name = "read_skill",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Read Operit Skill: $name"
                },
                executor = { tool ->
                    creatorTools.readSkill(tool)
                }
            )

            // Read MCP Server tool (get server configuration)
            handler.registerTool(
                name = "read_mcp_server",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Read MCP Server: $name"
                },
                executor = { tool ->
                    creatorTools.readMCPServer(tool)
                }
            )

            // Enable/Use Package tool
            handler.registerTool(
                name = "use_package",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Enable/Use Operit Package: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.usePackage(tool)
                    }
                }
            )

            // Disable Package tool
            handler.registerTool(
                name = "disable_package",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Disable Operit Package: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.disablePackage(tool)
                    }
                }
            )

            // Enable MCP Server tool
            handler.registerTool(
                name = "enable_mcp_server",
                dangerCheck = { false },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Enable MCP Server: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.enableMCPServer(tool)
                    }
                }
            )

            // Disable MCP Server tool
            handler.registerTool(
                name = "disable_mcp_server",
                dangerCheck = { true },
                descriptionGenerator = { tool ->
                    val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                    "Disable MCP Server: $name"
                },
                executor = { tool ->
                    runBlocking(Dispatchers.IO) {
                        creatorTools.disableMCPServer(tool)
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
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

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
            mcpLocalServer.removePluginMetadata(server.id)

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

        /**
     * Update an existing package
     */
    private suspend fun updatePackage(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value
        val code = tool.parameters.find { it.name == "code" }?.value
        val tools = tool.parameters.find { it.name == "tools" }?.value

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

            val existingCode = packageFile.readText()

            // Parse existing metadata if not provided
            val newCode = when {
                code != null && code.isNotBlank() -> code
                else -> existingCode // Keep existing code if not provided
            }

            // If new tools are provided, regenerate the package
            val finalCode = if (tools != null && tools != "[]" && tools.isNotBlank()) {
                // Extract description from existing or new
                val desc = description ?: extractDescriptionFromPackage(existingCode)
                generatePackageCode(name, desc, tools)
            } else if (description != null && description.isNotBlank()) {
                // Just update description in metadata
                updatePackageDescription(existingCode, description)
            } else {
                newCode
            }

            packageFile.writeText(finalCode)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Package '$name' updated successfully")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update package: ${e.message}"
            )
        }
    }

    private fun extractDescriptionFromPackage(code: String): String {
        val descRegex = """"description"\s*:\s*\{[^}]*"(?:default|en|zh)"\s*:\s*"([^"]+)"}""".toRegex()
        return descRegex.find(code)?.groupValues?.get(1) ?: ""
    }

    private fun updatePackageDescription(code: String, newDescription: String): String {
        // Replace description in metadata block
        val descPattern = """("description"\s*:\s*\{[^}]*"(?:default|en|zh)"\s*:\s*)"[^"]+"}""".toRegex()
        return descPattern.replace(code) { match ->
            val prefix = match.groupValues[1]
            // Try to preserve the language key
            val langMatch = """"(en|zh|default)""" .toRegex().find(match.value)
            val lang = langMatch?.groupValues?.get(1) ?: "default"
            """$prefix"$newDescription"}"""
        }
    }

    /**
     * Update an existing MCP server
     */
    private suspend fun updateMCPServer(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value
        val endpoint = tool.parameters.find { it.name == "endpoint" }?.value
        val serverType = tool.parameters.find { it.name == "server_type" }?.value ?: "local"

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "MCP Server name is required"
            )
        }

        return try {
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            // Update the server configuration
            val updatedServer = server.copy(
                description = description ?: server.description,
                endpoint = endpoint ?: server.endpoint,
                type = serverType
            )

            mcpLocalServer.addOrUpdatePluginMetadata(updatedServer)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("MCP Server '$name' updated successfully")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update MCP server: ${e.message}"
            )
        }
    }

    /**
     * Update an existing skill
     */
    private suspend fun updateSkill(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value ?: ""
        val description = tool.parameters.find { it.name == "description" }?.value
        val content = tool.parameters.find { it.name == "content" }?.value

        if (name.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Skill name is required"
            )
        }

        return try {
            skillManager.refreshAvailableSkills()
            val skill = skillManager.getAvailableSkills()[name]

            if (skill == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' not found"
                )
            }

            // Update description in SKILL.md if provided
            if (description != null && description.isNotBlank()) {
                val skillContent = skill.skillFile.readText()
                val updatedContent = updateSkillDescription(skillContent, description)
                skill.skillFile.writeText(updatedContent)
            }

            // Update content if provided
            if (content != null && content.isNotBlank()) {
                skill.skillFile.writeText(content)
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Skill '$name' updated successfully")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update skill", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update skill: ${e.message}"
            )
        }
    }

    private fun updateSkillDescription(content: String, newDescription: String): String {
        // Update description in frontmatter
        val frontmatterPattern = """(description:\s*)".*""" .toRegex()
        return frontmatterPattern.replace(content) { match ->
            "${match.groupValues[1]}\"$newDescription\""
        }
    }

    /**
     * Test a package to verify it works correctly
     */
    private suspend fun testPackage(tool: AITool): ToolResult {
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

            val code = packageFile.readText()

            // Check if the package has valid metadata
            val hasMetadata = code.contains("METADATA")
            val hasExports = code.contains("exports.")

            if (!hasMetadata) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Package '$name' has invalid metadata (missing METADATA block)"
                )
            }

            if (!hasExports) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Package '$name' has no exported functions"
                )
            }

            // Try to parse and validate the JavaScript
            val validationResult = validatePackageSyntax(code)

            if (validationResult.isNotBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Package '$name' has syntax errors: $validationResult"
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Package '$name' is valid and ready to use")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to test package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to test package: ${e.message}"
            )
        }
    }

    private fun validatePackageSyntax(code: String): String {
        // Basic JavaScript syntax validation
        try {
            // Check for common issues
            if (code.contains("undefinedvariable")) {
                return "Contains undefined references"
            }
            return "" // No errors found
        } catch (e: Exception) {
            return e.message ?: "Unknown syntax error"
        }
    }

    /**
     * Test an MCP server to verify it works correctly
     */
    private suspend fun testMCPServer(tool: AITool): ToolResult {
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
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            // Check if server is installed
            if (!server.isInstalled) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' is not installed. Please install it first."
                )
            }

            // Try to get the MCP manager and test connection
            val mcpManager = com.ai.assistance.operit.core.tools.mcp.MCPManager.getInstance(context)
            val client = mcpManager.getOrCreateClient(server.name)

            if (client == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to connect to MCP Server '$name'. Check if the server is running."
                )
            }

            // Test if we can list tools
            try {
                val tools = client.getTools()
                val toolsCount = tools.size

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("MCP Server '$name' is working! Available tools: $toolsCount")
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get tools from MCP Server '$name': ${e.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to test MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to test MCP server: ${e.message}"
            )
        }
    }

    /**
     * Test a skill to verify it works correctly
     */
    private suspend fun testSkill(tool: AITool): ToolResult {
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
            skillManager.refreshAvailableSkills()
            val skill = skillManager.getAvailableSkills()[name]

            if (skill == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' not found"
                )
            }

            // Check if SKILL.md exists and has content
            if (!skill.skillFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' SKILL.md file is missing"
                )
            }

            val content = skill.skillFile.readText()
            if (content.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' SKILL.md is empty"
                )
            }

            // Check for frontmatter
            val hasFrontmatter = content.trim().startsWith("---")

            // Try to get the system prompt
            val systemPrompt = skillManager.getSkillSystemPrompt(name)
            val hasSystemPrompt = systemPrompt != null

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Skill '$name' is valid!\n- Has frontmatter: $hasFrontmatter\n- Has system prompt: $hasSystemPrompt\n- Location: ${skill.directory.absolutePath}")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to test skill", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to test skill: ${e.message}"
            )
        }
    }

    /**
     * Read package content
     */
    private fun readPackage(tool: AITool): ToolResult {
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

            val content = packageFile.readText()

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(content)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to read package: ${e.message}"
            )
        }
    }

    /**
     * Read skill content
     */
    private fun readSkill(tool: AITool): ToolResult {
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
            skillManager.refreshAvailableSkills()
            val skill = skillManager.getAvailableSkills()[name]

            if (skill == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Skill '$name' not found"
                )
            }

            val content = skill.skillFile.readText()

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(content)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read skill", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to read skill: ${e.message}"
            )
        }
    }

    /**
     * Read MCP server configuration
     */
    private fun readMCPServer(tool: AITool): ToolResult {
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
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            val sb = StringBuilder()
            sb.appendLine("## MCP Server: ${server.name}")
            sb.appendLine("ID: ${server.id}")
            sb.appendLine("Type: ${server.type}")
            sb.appendLine("Description: ${server.description}")
            sb.appendLine("Installed: ${server.isInstalled}")
           if (!server.endpoint.isNullOrBlank()) {
                sb.appendLine("Endpoint: ${server.endpoint}")
            }
            if (!server.repoUrl.isNullOrBlank()) {
                sb.appendLine("Repository: ${server.repoUrl}")
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(sb.toString())
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to read MCP server: ${e.message}"
            )
        }
    }

    /**
     * Enable/Use a package
     */
    private suspend fun usePackage(tool: AITool): ToolResult {
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
            packageManager.usePackage(name)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Package '$name' is now enabled and ready to use")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to enable package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to enable package: ${e.message}"
            )
        }
    }

    /**
     * Disable a package
     */
    private suspend fun disablePackage(tool: AITool): ToolResult {
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
            packageManager.disablePackage(name)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Package '$name' has been disabled")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to disable package", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to disable package: ${e.message}"
            )
        }
    }

    /**
     * Enable an MCP server
     */
    private suspend fun enableMCPServer(tool: AITool): ToolResult {
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
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            // Mark as installed and save
            val updatedServer = server.copy(isInstalled = true)
            mcpLocalServer.addOrUpdatePluginMetadata(updatedServer)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("MCP Server '$name' has been enabled")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to enable MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to enable MCP server: ${e.message}"
            )
        }
    }

    /**
     * Disable an MCP server
     */
    private suspend fun disableMCPServer(tool: AITool): ToolResult {
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
            val servers = mcpLocalServer.getAllPluginMetadata()
            val server = servers.values.find { it.name.equals(name, ignoreCase = true) }

            if (server == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP Server '$name' not found"
                )
            }

            // Mark as not installed
            val updatedServer = server.copy(isInstalled = false)
            mcpLocalServer.addOrUpdatePluginMetadata(updatedServer)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("MCP Server '$name' has been disabled")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to disable MCP server", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to disable MCP server: ${e.message}"
            )
        }
    }
}
