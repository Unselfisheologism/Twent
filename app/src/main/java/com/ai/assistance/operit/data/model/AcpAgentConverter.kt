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
     * Handles package-to-binary name mismatches for known packages.
     */
    private fun extractBinaryFromInstallCommand(installCommand: String?): String? {
        if (installCommand == null) return null

        val packageName = when {
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

        // Map known package names to their actual binary names
        return packageName?.let { PACKAGE_TO_BINARY_MAP[it] ?: it }
    }

    /**
     * Maps package names to their actual binary names.
     * Many packages have different package vs binary names.
     */
    private val PACKAGE_TO_BINARY_MAP = mapOf(
        // Qwen Code
        "qwen-code" to "qwen",
        // Claude Code
        "claude-code" to "claude",
        "@anthropic-ai/claude-code" to "claude",
        // OpenAI Codex
        "@openai/codex" to "codex",
        // GitHub Copilot
        "@github/copilot-cli" to "github-copilot",
        // Cursor
        "cursor" to "cursor",
        // Gemini CLI
        "gemini-cli" to "gemini",
        "@anthropic/gemini-cli" to "gemini",
        // Goose
        "goose" to "goose",
        "@block/goose" to "goose",
        // Junie
        "junie" to "junie",
        "@jetbrains/junie" to "junie",
        // Kilo Code
        "kilocode" to "kilocode",
        "@kilocode/kilocode" to "kilocode",
        // Kimi CLI
        "kimi-cli" to "kimi",
        "@moonshot/kimi-cli" to "kimi",
        // Aider (pip package = binary name)
        "aider-chat" to "aider",
        // OpenCode (go package)
        "opencode" to "opencode",
        // Factory Droid
        "factory-droid" to "factory",
        // Nova
        "nova" to "nova",
        // Qoder
        "qoder" to "qoder",
        // Stakpak
        "stakpak" to "stakpak",
        // DeepAgents
        "deepagents" to "deepagents",
        // Fast Agent
        "fast-agent" to "fast-agent",
        // Cline
        "cline" to "cline",
        "@cline/cline" to "cline",
        // Mistral Vibe
        "mistral-vibe" to "mistral-vibe",
        // Pi ACP
        "pi-acp" to "pi",
        // Autohand Code
        "autohand-code" to "autohand",
        // Corust Agent
        "corust-agent" to "corust",
        // Crow CLI
        "crow-cli" to "crow",
        // Minion Code
        "minion-code" to "minion",
        // Amp ACP
        "amp-acp" to "amp"
    )

    /**
     * Generate a sensible install command based on available metadata.
     */
    private fun generateInstallCommand(entry: AcpAgentEntry): String {
        // Special case for Qwen Code - uses scoped package name
        if (entry.id == "qwen-code") {
            return "npm install -g @qwen-code/qwen-code@latest"
        }

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
