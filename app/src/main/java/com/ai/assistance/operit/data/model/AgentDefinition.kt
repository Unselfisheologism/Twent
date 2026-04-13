package com.ai.assistance.operit.data.model

/**
 * Represents an AI agent CLI that can be installed and run in the terminal.
 * Examples: hermes-agent, claude-code, codex, etc.
 */
data class AgentDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String, // drawable resource name or URL
    val installCommand: String,        // Command to install the agent
    val installCheckCommand: String,  // Command to check if installed
    val startCommand: String,          // Command to start the agent (REPL mode)
    val requiredDeps: List<String> = emptyList(),  // Required dependencies
    val installUrl: String? = null,    // Optional URL for curl-based installs
    val commands: List<AgentCommand> = emptyList(), // Pre-defined non-chat commands
    val isCustom: Boolean = false,       // Is this a custom agent added by user
    val isFromAcp: Boolean = false,      // Is this agent from ACP registry
    val acpVersion: String? = null,      // Version from ACP registry
    val acpRepository: String? = null,   // GitHub/repository URL from ACP
    val acpTags: List<String>? = null    // Tags from ACP registry
)

/**
 * Custom agent builder for adding new agent CLIs
 */
data class CustomAgentBuilder(
    val name: String = "",
    val description: String = "",
    val installCommand: String = "",
    val installCheckCommand: String = "",
    val startCommand: String = "",
    val requiredDeps: List<String> = emptyList()
) {
    fun build(): AgentDefinition {
        val id = name.lowercase().replace(" ", "-")
        return AgentDefinition(
            id = id,
            name = name,
            description = description,
            icon = "ic_agent_custom",
            installCommand = installCommand,
            installCheckCommand = installCheckCommand,
            startCommand = startCommand,
            requiredDeps = requiredDeps,
            commands = listOf(
                AgentCommand("Version", "--version", "Show version", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show help", CommandCategory.HELP),
                AgentCommand("Exit", "exit", "Exit", CommandCategory.SYSTEM)
            ),
            isCustom = true
        )
    }
}

/**
 * A non-chat command that can be run against an agent
 */
data class AgentCommand(
    val name: String,           // Display name, e.g., "Doctor Check"
    val command: String,        // Actual command to run, e.g., "hermes doctor"
    val description: String,   // What the command does
    val category: CommandCategory = CommandCategory.SYSTEM
)

/**
 * Category of commands
 */
enum class CommandCategory {
    SYSTEM,    // doctor, version, status
    SETUP,     // setup, init, configure
    MCP,       // MCP server management
    HELP,      // help, --help
    OTHER      // Other commands
}

/**
 * Installation status for an agent.
 */
enum class AgentInstallStatus {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    FAILED
}

/**
 * Agent with installation state.
 */
data class AgentWithStatus(
    val definition: AgentDefinition,
    val installStatus: AgentInstallStatus = AgentInstallStatus.NOT_INSTALLED,
    val installError: String? = null
)

/**
 * Registry of available AI agents.
 * Commands are discovered dynamically by running --help on each agent.
 * Supports both built-in and custom agents.
 */
object AgentRegistry {
    // Built-in agents
    private val builtInAgents = listOf(
        AgentDefinition(
            id = "hermes-agent",
            name = "Hermes Agent",
            description = "Autonomous AI agent from NousResearch. Great for coding, debugging, and complex multi-step tasks.",
            icon = "ic_agent_robot",
            installCommand = "curl -fsSL https://raw.githubusercontent.com/NousResearch/hermes-agent/main/scripts/install.sh | bash",
            installCheckCommand = "which hermes",
            startCommand = "hermes",
            requiredDeps = listOf("python3", "pip", "git"),
            installUrl = "https://raw.githubusercontent.com/NousResearch/hermes-agent/main/scripts/install.sh",
            commands = listOf(
                AgentCommand("Doctor Check", "doctor", "Run health check and verify setup", CommandCategory.SYSTEM),
                AgentCommand("Setup", "setup", "Configure hermes-agent settings", CommandCategory.SETUP),
                AgentCommand("MCP Servers", "mcp", "Manage MCP servers", CommandCategory.MCP),
                AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
                AgentCommand("List Tools", "tools list", "List available tools", CommandCategory.SYSTEM),
                AgentCommand("Config Edit", "config edit", "Edit configuration file", CommandCategory.SETUP),
                AgentCommand("Status", "status", "Show current status", CommandCategory.SYSTEM),
                AgentCommand("Clear", "clear", "Clear conversation", CommandCategory.SYSTEM),
                AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
            )
        ),
        AgentDefinition(
            id = "claude-code",
            name = "Claude Code",
            description = "Anthropic's CLI for AI-assisted coding with full code editing capabilities.",
            icon = "ic_agent_claude",
            installCommand = "curl -fsSL https://claude.ai/install.sh | bash",
            installCheckCommand = "which claude",
            startCommand = "claude",
            requiredDeps = listOf("node", "npm"),
            installUrl = "https://claude.ai/install.sh",
            commands = listOf(
                AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
                AgentCommand("Configure", "configure", "Open configuration", CommandCategory.SETUP),
                AgentCommand("MCP Servers", "mcp", "Manage MCP servers", CommandCategory.MCP),
                AgentCommand("Doctor", "doctor", "Run health check", CommandCategory.SYSTEM),
                AgentCommand("Clear", "clear", "Clear conversation", CommandCategory.SYSTEM),
                AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
            )
        ),
        AgentDefinition(
            id = "codex",
            name = "OpenAI Codex",
            description = "OpenAI's command-line coding agent.",
            icon = "ic_agent_codex",
            installCommand = "npm i -g @openai/codex",
            installCheckCommand = "which codex",
            startCommand = "codex",
            requiredDeps = listOf("node", "npm"),
            commands = listOf(
                AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
                AgentCommand("Doctor", "doctor", "Run health check", CommandCategory.SYSTEM),
                AgentCommand("Config", "config", "Manage configuration", CommandCategory.SETUP),
                AgentCommand("Clear", "clear", "Clear conversation", CommandCategory.SYSTEM),
                AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
            )
        ),
        AgentDefinition(
            id = "aider",
            name = "Aider",
            description = "AI pair programming in terminal. Works with any git repo. Requires Python >=3.10.",
            icon = "ic_agent_aider",
            installCommand = "pip install aider-chat",
            installCheckCommand = "which aider",
            startCommand = "aider",
            requiredDeps = listOf("python3", "pip", "git"),
            commands = listOf(
                AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
                AgentCommand("Pretty", "--pretty", "Enable pretty output", CommandCategory.SYSTEM),
                AgentCommand("List Models", "--list-models", "List available models", CommandCategory.SYSTEM),
                AgentCommand("MCP Config", "--mcp", "Configure MCP servers", CommandCategory.MCP),
                AgentCommand("Config", "--config", "Specify config file", CommandCategory.SETUP),
                AgentCommand("Clear", "clear", "Clear conversation", CommandCategory.SYSTEM),
                AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
            )
        ),
        AgentDefinition(
            id = "opencode",
            name = "OpenCode",
            description = "Open source AI coding assistant with VSCode-like interface.",
            icon = "ic_agent_opencode",
            installCommand = "curl -fsSL https://raw.githubusercontent.com/opencode-ai/opencode/main/scripts/install.sh | bash",
            installCheckCommand = "which opencode",
            startCommand = "opencode",
            requiredDeps = listOf("go"),
            installUrl = "https://raw.githubusercontent.com/opencode-ai/opencode/main/scripts/install.sh",
            commands = listOf(
                AgentCommand("Version", "--version", "Show version information", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show all available commands", CommandCategory.HELP),
                AgentCommand("Doctor", "doctor", "Run health check", CommandCategory.SYSTEM),
                AgentCommand("MCP Servers", "mcp", "Manage MCP servers", CommandCategory.MCP),
                AgentCommand("Config", "config", "Edit configuration", CommandCategory.SETUP),
                AgentCommand("Clear", "clear", "Clear conversation", CommandCategory.SYSTEM),
                AgentCommand("Exit", "exit", "Exit the agent", CommandCategory.SYSTEM)
            )
        )
    )

    // Custom agents (can be added at runtime)
    private val customAgents = mutableListOf<AgentDefinition>()

    // Get all agents (built-in + custom)
    val agents: List<AgentDefinition>
        get() = builtInAgents + customAgents

    fun getById(id: String): AgentDefinition? = agents.find { it.id == id }

    fun addCustomAgent(agent: AgentDefinition) {
        customAgents.add(agent)
    }

    fun removeCustomAgent(id: String) {
        customAgents.removeAll { it.id == id }
    }

    fun getCustomAgents(): List<AgentDefinition> = customAgents.toList()
}
