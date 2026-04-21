package com.ai.assistance.operit.data.model

/**
 * Converts ACP registry entries to AgentDefinition objects.
 */
object AcpAgentConverter {

    /**
     * Convert an AcpAgentEntry to AgentDefinition.
     * Generates appropriate install/check/start commands based on distribution data.
     */
    fun toAgentDefinition(entry: AcpAgentEntry): AgentDefinition {
        // Generate install command from distribution data
        val installCmd = generateInstallCommand(entry)
        
        // Determine the binary/executable name for install check
        val binaryName = extractBinaryName(entry)
        
        // Generate start command from binary name
        val startCmd = binaryName
        
        // Generate install check command
        val checkCmd = "which $binaryName"
        
        // Determine required dependencies
        val deps = determineDependencies(entry)
        
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
            acpTags = null // Tags are not in the new structure
        )
    }
    
    /**
     * Generate install command from distribution data.
     * For Android, we prioritize npx (npm) distribution since it's most likely to work.
     */
    private fun generateInstallCommand(entry: AcpAgentEntry): String {
        val distribution = entry.distribution
        
        return when {
            // NPX distribution (npm package) - most common and works on Android with Node.js
            distribution?.npx != null -> {
                val npx = distribution.npx!!
                val args = npx.args?.joinToString(" ") ?: ""
                if (args.isNotEmpty()) {
                    "npx ${npx.package} $args"
                } else {
                    "npx ${npx.package}"
                }
            }
            
            // UVX distribution (Python package) - works if Python is installed
            distribution?.uvx != null -> {
                val uvx = distribution.uvx!!
                val args = uvx.args?.joinToString(" ") ?: ""
                if (args.isNotEmpty()) {
                    "uvx ${uvx.package} $args"
                } else {
                    "uvx ${uvx.package}"
                }
            }
            
            // Binary distribution - not directly installable on Android
            // We'll generate a fallback command
            distribution?.binary != null -> {
                // For binary distributions, we can't directly install on Android
                // Generate a placeholder that explains the limitation
                "echo 'Binary distribution not supported on Android. Check ${entry.repository ?: entry.homepage ?: "the project page"} for installation instructions.'"
            }
            
            // No distribution info - fallback to heuristic
            else -> {
                generateFallbackInstallCommand(entry)
            }
        }
    }
    
    /**
     * Extract binary name from distribution data.
     */
    private fun extractBinaryName(entry: AcpAgentEntry): String {
        val distribution = entry.distribution
        
        return when {
            // For npx, the binary is the package name (last part after /)
            distribution?.npx != null -> {
                val packageName = distribution.npx!!.package
                // Extract binary name from package name (e.g., "@anthropic-ai/claude-code" -> "claude-code")
                packageName.substringAfterLast("/")
            }
            
            // For uvx, similar to npx
            distribution?.uvx != null -> {
                val packageName = distribution.uvx!!.package
                packageName.substringAfterLast("/")
            }
            
            // For binary, use the command name from first available platform
            distribution?.binary != null -> {
                val firstPlatform = distribution.binary!!.values.firstOrNull()
                if (firstPlatform != null) {
                    // Extract binary name from command (e.g., "./amp-acp" -> "amp-acp")
                    firstPlatform.cmd.removePrefix("./").removeSuffix(".exe")
                } else {
                    entry.id
                }
            }
            
            // Fallback to entry ID
            else -> entry.id
        }
    }
    
    /**
     * Determine required dependencies based on distribution.
     */
    private fun determineDependencies(entry: AcpAgentEntry): List<String> {
        val distribution = entry.distribution
        
        return when {
            distribution?.npx != null -> listOf("node", "npm")
            distribution?.uvx != null -> listOf("python3", "uv")
            distribution?.binary != null -> listOf("curl", "tar") // For downloading and extracting
            else -> emptyList()
        }
    }
    
    /**
     * Generate fallback install command when no distribution info is available.
     */
    private fun generateFallbackInstallCommand(entry: AcpAgentEntry): String {
        // Try to infer from repository URL
        val repo = entry.repository?.lowercase() ?: ""
        
        return when {
            // npm-based
            repo.contains("npm") || repo.contains("node") -> {
                "npm i -g ${entry.id}"
            }
            // pip-based
            repo.contains("pip") || repo.contains("python") -> {
                "pip install ${entry.id}"
            }
            // cargo-based
            repo.contains("cargo") || repo.contains("rust") -> {
                "cargo install ${entry.id}"
            }
            // go-based
            repo.contains("go") || repo.contains("golang") -> {
                "go install ${entry.id}@latest"
            }
            // Default: try npm
            else -> "npm i -g ${entry.id}"
        }
    }
}
