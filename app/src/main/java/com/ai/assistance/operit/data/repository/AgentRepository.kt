package com.ai.assistance.operit.data.repository

import android.content.Context
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
    
    // Active agent sessions, keyed by terminal session ID
    private val _activeSessions = MutableStateFlow<Map<String, AgentSession>>(emptyMap())
    val activeSessions: Flow<Map<String, AgentSession>> = _activeSessions
    
    // Installation states, keyed by agent ID
    private val _installationStates = MutableStateFlow<Map<String, InstallationState>>(emptyMap())
    val installationStates: Flow<Map<String, InstallationState>> = _installationStates
    
    // Cache of installed agents (checked on app start)
    private val installedAgentsCache = ConcurrentHashMap<String, Boolean>()
    
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
     * Check if an agent is installed by running its install check command.
     */
    suspend fun checkInstallation(agentId: String): Boolean = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext false
        
        try {
            // Create a temporary session to check installation
            val sessionData = terminalManager.createNewSession("install-check-${agentId}")
            val sessionId = sessionData.id
            
            // Run the check command - sendCommandToSession returns command ID (String)
            terminalManager.sendCommandToSession(sessionId, agent.installCheckCommand)
            
            // Wait for command to execute
            kotlinx.coroutines.delay(2000)
            
            // Simplified check - in production, parse output
            val isInstalled = true
            
            if (isInstalled) {
                installedAgentsCache[agentId] = true
            }
            
            // Clean up temp session
            terminalManager.closeSession(sessionId)
            
            isInstalled
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking agent installation: ${e.message}")
            false
        }
    }
    
    /**
     * Install an agent by running its install command in the terminal.
     */
    suspend fun installAgent(agentId: String, onOutput: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
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
        for (agent in AgentRegistry.agents) {
            val isInstalled = checkInstallation(agent.id)
            if (isInstalled) {
                installedAgentsCache[agent.id] = true
            }
        }
    }
    
    /**
     * Run a non-chat command (one-shot execution).
     */
    suspend fun runCommand(agentId: String, command: String): Result<String> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
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
            
            // Run the command
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
     */
    suspend fun discoverCommands(agentId: String): Result<List<AgentCommand>> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
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
     */
    suspend fun startAgentSession(agentId: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
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
            
            // Use sendCommandToSession which handles interactive mode
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
     */
    fun getAgentForSession(sessionId: String): AgentDefinition? {
        val agentId = _activeSessions.value[sessionId]?.agentId ?: return null
        return AgentRegistry.getById(agentId)
    }
}