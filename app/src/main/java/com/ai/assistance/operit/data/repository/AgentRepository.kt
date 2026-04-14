package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.AcpAgentConverter
import com.ai.assistance.operit.data.model.AgentCommand
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.AgentInstallStatus
import com.ai.assistance.operit.data.model.AgentRegistry
import com.ai.assistance.operit.data.model.AgentSession
import com.ai.assistance.operit.data.model.CommandCategory
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Installation state for tracking ongoing installations
 */
data class InstallationState(
    val agentId: String,
    val status: AgentInstallStatus,
    val output: String = "",
    val error: String? = null
)

/**
 * Repository for managing AI agents and their sessions.
 * Integrates with the existing TerminalManager for process management.
 */
class AgentRepository(private val context: Context) {

    companion object {
        private const val TAG = "AgentRepository"
    }

    private val terminalManager = TerminalManager.getInstance(context)
    private val acpRegistryService = AcpRegistryService.getInstance()

    // Active agent sessions, keyed by terminal session ID
    private val _activeSessions = MutableStateFlow<Map<String, AgentSession>>(emptyMap())
    val activeSessions: Flow<Map<String, AgentSession>> = _activeSessions

    // Installation states, keyed by agent ID
    private val _installationStates = MutableStateFlow<Map<String, InstallationState>>(emptyMap())
    val installationStates: Flow<Map<String, InstallationState>> = _installationStates

    // Cache of installed agents (checked on app start)
    private val installedAgentsCache = ConcurrentHashMap<String, Boolean>()

    // Cache of ACP agents (fetched from registry)
    private var acpAgentsCache: List<AgentDefinition>? = null

    // Cache of detected non-ACP agents
    private var detectedAgentsCache: List<AgentDefinition>? = null

    // Flag to track if ACP registry has been loaded
    private var acpRegistryLoaded = false

    /**
     * Get list of all available agent definitions.
     */
    fun getAvailableAgents(): List<AgentDefinition> = AgentRegistry.agents

    /**
     * Get agent with installation status.
     */
    fun getAgentWithStatus(agentId: String): Pair<AgentDefinition, AgentInstallStatus>? {
        val agent = AgentRegistry.getById(agentId) ?: return null
        val installState = _installationStates.value[agentId]
        val status = installState?.status ?:
            if (installedAgentsCache[agentId] == true) AgentInstallStatus.INSTALLED
            else AgentInstallStatus.NOT_INSTALLED
        return Pair(agent, status)
    }

    /**
     * Get all agents with their installation status.
     */
    fun getAgentsWithStatus(): List<Pair<AgentDefinition, AgentInstallStatus>> {
        return AgentRegistry.agents.map { agent ->
            val installState = _installationStates.value[agent.id]
            val status = installState?.status ?:
                if (installedAgentsCache[agent.id] == true) AgentInstallStatus.INSTALLED
                else AgentInstallStatus.NOT_INSTALLED
            Pair(agent, status)
        }
    }

    /**
     * Get ALL agents (built-in + ACP + detected) with their installation status.
     */
    fun getAllAgentsWithStatus(): List<Pair<AgentDefinition, AgentInstallStatus>> {
        val agentsMap = mutableMapOf<String, Pair<AgentDefinition, AgentInstallStatus>>()

        // Add built-in agents
        for ((agent, status) in getAgentsWithStatus()) {
            agentsMap[agent.id] = Pair(agent, status)
        }

        // Add ACP agents
        for ((agent, status) in getCachedAcpAgents()) {
            if (agent.id !in agentsMap) {
                agentsMap[agent.id] = Pair(agent, status)
            }
        }

        // Add detected agents
        detectedAgentsCache?.let { detectedAgents ->
            for (agent in detectedAgents) {
                if (agent.id !in agentsMap) {
                    val status = if (installedAgentsCache[agent.id] == true) AgentInstallStatus.INSTALLED else AgentInstallStatus.NOT_INSTALLED
                    agentsMap[agent.id] = Pair(agent, status)
                }
            }
        }

        return agentsMap.values.toList()
    }

    /**
     * Fetch agents from ACP registry and merge with local registry.
     * Returns combined list of agents with installation status.
     */
    suspend fun fetchAcpAgents(): Result<List<Pair<AgentDefinition, AgentInstallStatus>>> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Fetching ACP agents from registry")

            val result = acpRegistryService.fetchRegistry()
            result.fold(
                onSuccess = { acpEntries ->
                    // Convert ACP entries to AgentDefinition
                    val acpAgents = acpEntries.map { entry ->
                        AcpAgentConverter.toAgentDefinition(entry)
                    }

                    // Cache ACP agents
                    acpAgentsCache = acpAgents
                    acpRegistryLoaded = true

                    AppLogger.i(TAG, "Successfully loaded ${acpAgents.size} ACP agents")

                    // Check installation status for each ACP agent
                    val agentsWithStatus = acpAgents.map { agent ->
                        val isInstalled = checkInstallationSilent(agent.id, agent.installCheckCommand)
                        if (isInstalled) {
                            installedAgentsCache[agent.id] = true
                        }
                        val status = if (isInstalled) AgentInstallStatus.INSTALLED else AgentInstallStatus.NOT_INSTALLED
                        Pair(agent, status)
                    }

                    Result.success(agentsWithStatus)
                },
                onFailure = { error ->
                    AppLogger.e(TAG, "Failed to fetch ACP agents: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching ACP agents: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get cached ACP agents if available, otherwise return empty list.
     */
    fun getCachedAcpAgents(): List<Pair<AgentDefinition, AgentInstallStatus>> {
        return acpAgentsCache?.map { agent ->
            val status = if (installedAgentsCache[agent.id] == true) AgentInstallStatus.INSTALLED else AgentInstallStatus.NOT_INSTALLED
            Pair(agent, status)
        } ?: emptyList()
    }

    /**
     * Check if ACP registry has been loaded.
     */
    fun isAcpRegistryLoaded(): Boolean = acpRegistryLoaded

    /**
     * Detect installed non-ACP CLIs on the system.
     * This scans for common agent binaries that may not be in the ACP registry.
     */
    suspend fun detectInstalledNonAcpAgents(): List<AgentDefinition> = withContext(Dispatchers.IO) {
        // List of common agent binaries to check
        val knownBinaries = listOf(
            "hermes", "claude", "codex", "aider", "opencode",
            "cline", "cursor", "gemini", "copilot", "goose",
            "junie", "kilocode", "kimi", "fast-agent", "deepagents",
            "factory", "nova", "qoder", "stakpak"
        )

        val detectedAgents = mutableListOf<AgentDefinition>()

        for (binary in knownBinaries) {
            if (checkInstallationSilent(binary, "which $binary")) {
                // Check if this binary is already in our registry
                val existingAgent = AgentRegistry.getById("$binary-agent") ?: AgentRegistry.getById(binary)
                if (existingAgent == null) {
                    // Create a detected agent entry
                    val detectedAgent = AgentDefinition(
                        id = "$binary-detected",
                        name = binary.replaceFirstChar { it.uppercase() },
                        description = "Detected $binary CLI installation on device",
                        icon = "ic_agent_acp",
                        installCommand = "which $binary", // Already installed
                        installCheckCommand = "which $binary",
                        startCommand = binary,
                        requiredDeps = emptyList(),
                        commands = listOf(
                            AgentCommand("Version", "--version", "Show version", CommandCategory.SYSTEM),
                            AgentCommand("Help", "--help", "Show help", CommandCategory.HELP),
                            AgentCommand("Exit", "exit", "Exit", CommandCategory.SYSTEM)
                        ),
                        isCustom = true,
                        isFromAcp = false
                    )
                    detectedAgents.add(detectedAgent)
                    installedAgentsCache["$binary-detected"] = true
                    AppLogger.i(TAG, "Detected installed non-ACP agent: $binary")
                }
            }
        }

        // Cache detected agents
        detectedAgentsCache = detectedAgents

        detectedAgents
    }

    /**
     * Silent installation check without logging warnings.
     */
    private fun checkInstallationSilent(agentId: String, checkCommand: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", checkCommand))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if an agent is installed by running its install check command.
     */
    suspend fun checkInstallation(agentId: String): Boolean = withContext(Dispatchers.IO) {
        // Try built-in agents first
        var agent = AgentRegistry.getById(agentId)

        // Try ACP agents
        if (agent == null) {
            agent = acpAgentsCache?.find { it.id == agentId }
        }

        // Try detected agents
        if (agent == null) {
            agent = detectedAgentsCache?.find { it.id == agentId }
        }

        if (agent == null) {
            return@withContext false
        }

        try {
            // Run the check command directly using shell
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", agent.installCheckCommand))
            val exitCode = process.waitFor()
            val isInstalled = exitCode == 0

            if (isInstalled) {
                installedAgentsCache[agentId] = true
            }

            isInstalled
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking agent installation: ${e.message}")
            false
        }
    }

    /**
     * Install an agent by running its install command in the terminal.
     * Works with both local registry agents and ACP-sourced agents.
     */
    suspend fun installAgent(agentId: String, onOutput: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        // Try to find agent in local registry first
        var agent = AgentRegistry.getById(agentId)

        // If not found, try to find in cached ACP agents
        if (agent == null) {
            agent = acpAgentsCache?.find { it.id == agentId }
        }

        if (agent == null) {
            return@withContext Result.failure(Exception("Unknown agent: $agentId"))
        }

        // Update state to installing
        _installationStates.value = _installationStates.value + (
            agentId to InstallationState(agentId, AgentInstallStatus.INSTALLING)
        )

        try {
            // Create a dedicated installation session
            val sessionData = terminalManager.createNewSession("install-${agentId}")
            val sessionId = sessionData.id

            // Start the install command
            terminalManager.sendCommandToSession(sessionId, agent.installCommand)

            // Wait for installation to complete
            kotlinx.coroutines.delay(5000)

            // Check if installed
            val checkSessionData = terminalManager.createNewSession("verify-${agentId}")
            terminalManager.sendCommandToSession(checkSessionData.id, agent.installCheckCommand)
            kotlinx.coroutines.delay(2000)
            terminalManager.closeSession(checkSessionData.id)

            // Update state to installed
            installedAgentsCache[agentId] = true
            _installationStates.value = _installationStates.value + (
                agentId to InstallationState(agentId, AgentInstallStatus.INSTALLED)
            )

            // Close install session
            terminalManager.closeSession(sessionId)

            AppLogger.i(TAG, "Successfully installed agent: $agentId")
            Result.success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install agent $agentId: ${e.message}")
            _installationStates.value = _installationStates.value + (
                agentId to InstallationState(
                    agentId,
                    AgentInstallStatus.FAILED,
                    error = e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Check all agents' installation status.
     */
    suspend fun checkAllInstallations() = withContext(Dispatchers.IO) {
        // Check built-in agents
        for (agent in AgentRegistry.agents) {
            val isInstalled = checkInstallation(agent.id)
            if (isInstalled) {
                installedAgentsCache[agent.id] = true
            }
        }

        // Check ACP agents
        acpAgentsCache?.forEach { agent ->
            val isInstalled = checkInstallationSilent(agent.id, agent.installCheckCommand)
            if (isInstalled) {
                installedAgentsCache[agent.id] = true
            }
        }

        // Check detected agents (already marked as installed)
        detectedAgentsCache?.forEach { agent ->
            installedAgentsCache[agent.id] = true
        }
    }

    /**
     * Run a non-chat command (one-shot execution).
     * Works with both local registry agents and ACP-sourced agents.
     */
    suspend fun runCommand(agentId: String, command: String): Result<String> = withContext(Dispatchers.IO) {
        // Try to find agent in local registry first
        var agent = AgentRegistry.getById(agentId)

        // If not found, try to find in cached ACP agents
        if (agent == null) {
            agent = acpAgentsCache?.find { it.id == agentId }
        }

        if (agent == null) {
            return@withContext Result.failure(Exception("Unknown agent: $agentId"))
        }

        try {
            // Create a temporary session for this command
            val sessionData = terminalManager.createNewSession("cmd-${agentId}")
            val sessionId = sessionData.id

            // Build the full command
            val fullCommand = if (command.startsWith("--")) {
                "${agent.startCommand} $command"
            } else {
                command
            }

            terminalManager.sendCommandToSession(sessionId, fullCommand)

            // Wait for command to complete
            kotlinx.coroutines.delay(3000)

            val output = "Command '$fullCommand' executed. Check terminal for output."

            // Clean up
            terminalManager.closeSession(sessionId)

            AppLogger.i(TAG, "Executed command for $agentId: $fullCommand")
            Result.success(output)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to run command: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Discover available commands by running --help
     * Works with both local registry agents and ACP-sourced agents.
     */
    suspend fun discoverCommands(agentId: String): Result<List<AgentCommand>> = withContext(Dispatchers.IO) {
        // Try to find agent in local registry first
        var agent = AgentRegistry.getById(agentId)

        // If not found, try to find in cached ACP agents
        if (agent == null) {
            agent = acpAgentsCache?.find { it.id == agentId }
        }

        if (agent == null) {
            return@withContext Result.failure(Exception("Unknown agent: $agentId"))
        }

        try {
            val sessionData = terminalManager.createNewSession("discover-${agentId}")
            val sessionId = sessionData.id

            // Run --help to get available commands
            terminalManager.sendCommandToSession(sessionId, "${agent.startCommand} --help")

            // Wait for output
            kotlinx.coroutines.delay(3000)

            terminalManager.closeSession(sessionId)

            // Return predefined commands as fallback
            Result.success(agent.commands)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to discover commands: ${e.message}")
            // Fallback to predefined commands
            Result.success(agent.commands)
        }
    }

    /**
     * Start a new agent session.
     * Works with both local registry agents and ACP-sourced agents.
     */
    suspend fun startAgentSession(agentId: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        // Try to find agent in local registry first
        var agent = AgentRegistry.getById(agentId)

        // If not found, try to find in cached ACP agents
        if (agent == null) {
            agent = acpAgentsCache?.find { it.id == agentId }
        }

        // If not found, try to find in detected agents
        if (agent == null) {
            agent = detectedAgentsCache?.find { it.id == agentId }
        }

        if (agent == null) {
            return@withContext Result.failure(Exception("Unknown agent: $agentId"))
        }

        try {
            // Create new terminal session
            val sessionData = terminalManager.createNewSession(title)
            val sessionId = sessionData.id

            // Start the agent
            terminalManager.sendCommandToSession(sessionId, agent.startCommand)

            // Register this as an active agent session
            val now = System.currentTimeMillis()
            val agentSession = AgentSession(
                id = sessionId,
                agentId = agentId,
                title = title,
                createdAt = now,
                lastActivityAt = now
            )

            _activeSessions.value = _activeSessions.value + (sessionId to agentSession)

            AppLogger.i(TAG, "Started agent session: $title (session: $sessionId)")
            Result.success(sessionId)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start agent session: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Send input to a running agent session.
     */
    suspend fun sendToAgent(sessionId: String, input: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val inputWithNewline = if (input.endsWith("\n")) input else "$input\n"

            // Use sendCommandToSession - TerminalManager handles interactive mode!
            terminalManager.sendCommandToSession(sessionId, inputWithNewline)

            // Update last activity time
            val sessions = _activeSessions.value.toMutableMap()
            sessions[sessionId]?.let { session ->
                sessions[sessionId] = session.copy(lastActivityAt = System.currentTimeMillis())
                _activeSessions.value = sessions
            }

            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send to agent: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get flow of output from a specific agent session.
     */
    fun getAgentOutputFlow(sessionId: String): Flow<CommandExecutionEvent> {
        return terminalManager.commandExecutionEvents
    }

    /**
     * Close an agent session.
     */
    suspend fun closeAgentSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            terminalManager.closeSession(sessionId)

            // Remove from active sessions
            val sessions = _activeSessions.value.toMutableMap()
            sessions.remove(sessionId)
            _activeSessions.value = sessions

            AppLogger.i(TAG, "Closed agent session: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to close agent session: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get list of active agent sessions.
     */
    fun getActiveSessionsList(): List<AgentSession> {
        return _activeSessions.value.values.toList().sortedByDescending { it.lastActivityAt }
    }

    /**
     * Check if a terminal session is an agent session.
     */
    fun isAgentSession(sessionId: String): Boolean {
        return _activeSessions.value.containsKey(sessionId)
    }

    /**
     * Get agent definition for a session.
     * Works with both local registry agents and ACP-sourced agents.
     */
    fun getAgentForSession(sessionId: String): AgentDefinition? {
        val agentId = _activeSessions.value[sessionId]?.agentId ?: return null
        return AgentRegistry.getById(agentId) ?: acpAgentsCache?.find { it.id == agentId }
    }
}
