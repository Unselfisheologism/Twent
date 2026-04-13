package com.ai.assistance.operit.data.model

/**
 * Converts ACP registry entries to AgentDefinition objects.
 */
object AcpAgentConverter {

    /**
     * Convert an AcpAgentEntry to AgentDefinition.
     * Generates appropriate install/check/start commands based on available data.
     */
    fun toAgentDefinition(entry: AcpAgentEntry): AgentDefinition {
        // Determine the binary/executable name for install check
        val binaryName = entry.binary ?: extractBinaryFromInstallCommand(entry.installCommand) ?: entry.id

        // Generate install command if not provided
        val installCmd = entry.installCommand ?: generateInstallCommand(entry)

        // Generate start command from binary name
        val startCmd = binaryName

        // Generate install check command
        val checkCmd = "which $binaryName"

        // Determine required dependencies
        val deps = determineDependencies(entry.installCommand)

        // Generate default commands
        val commands = listOf(
            AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
            AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
            AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
        )

        return AgentDefinition(
            id = entry.id,
            name = entry.name,
            description = entry.description,
            icon = "ic_agent_acp", // Default ACP agent icon
            installCommand = installCmd,
            installCheckCommand = checkCmd,
            startCommand = startCmd,
            requiredDeps = deps,
            installUrl = entry.homepage ?: entry.repository,
            commands = commands,
            isCustom = false,
            isFromAcp = true,
            acpVersion = entry.version,
            acpRepository = entry.repository,
            acpTags = entry.tags
        )
    }

    /**
     * Extract binary name from install command (e.g., "npm i -g foo" -> "foo")
     */
    private fun extractBinaryFromInstallCommand(installCommand: String?): String? {
        if (installCommand == null) return null

        return when {
            installCommand.startsWith("npm i -g ") || installCommand.startsWith("npm install -g ") -> {
                installCommand.substringAfterLast("/").substringAfterLast(" ").trim()
            }
            installCommand.startsWith("pip install ") -> {
                installCommand.removePrefix("pip install ").trim().split(" ").firstOrNull()
            }
            installCommand.startsWith("cargo install ") -> {
                installCommand.removePrefix("cargo install ").trim().split(" ").firstOrNull()
            }
            installCommand.startsWith("go install ") -> {
                installCommand.removePrefix("go install ").trim().split("@").firstOrNull()?.substringAfterLast("/")
            }
            else -> null
        }
    }

    /**
     * Generate a sensible install command based on available metadata.
     */
    private fun generateInstallCommand(entry: AcpAgentEntry): String {
        // Try to infer from repository URL or tags
        val repo = entry.repository?.lowercase() ?: ""
        val tags = entry.tags?.map { it.lowercase() } ?: emptyList()

        return when {
            // npm-based
            repo.contains("npm") || tags.contains("node") || tags.contains("npm") -> {
                "npm i -g ${entry.id}"
            }
            // pip-based
            repo.contains("pip") || tags.contains("python") || tags.contains("pip") -> {
                "pip install ${entry.id}"
            }
            // cargo-based
            repo.contains("cargo") || tags.contains("rust") || tags.contains("cargo") -> {
                "cargo install ${entry.id}"
            }
            // go-based
            repo.contains("go") || tags.contains("go") || tags.contains("golang") -> {
                "go install ${entry.id}@latest"
            }
            // Default: try npm
            else -> "npm i -g ${entry.id}"
        }
    }

    /**
     * Determine required dependencies based on install command.
     */
    private fun determineDependencies(installCommand: String?): List<String> {
        if (installCommand == null) return emptyList()

        return when {
            installCommand.startsWith("npm ") -> listOf("node", "npm")
            installCommand.startsWith("pip ") -> listOf("python3", "pip")
            installCommand.startsWith("cargo ") -> listOf("rust", "cargo")
            installCommand.startsWith("go ") -> listOf("go")
            installCommand.startsWith("curl ") || installCommand.startsWith("wget ") -> listOf("curl")
            else -> emptyList()
        }
    }
}
