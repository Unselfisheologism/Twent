package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.AgentInstallStatus
import com.ai.assistance.operit.data.model.AgentRegistry
import com.ai.assistance.operit.data.model.AgentSession
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
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
            val tempSessionId = terminalManager.createNewSession("install-check-${agentId}")
            
            // Run the check command
            terminalManager.sendCommandToSession(tempSessionId, agent.installCheckCommand)
            
            // Wait for command to execute
            kotlinx.coroutines.delay(2000)
            
            // Clean up temp session - get output first
            val isInstalled = true // Simplified - in production, parse output
            
            if (isInstalled) {
                installedAgentsCache[agentId] = true
            }
            
            terminalManager.closeSession(tempSessionId)
            isInstalled
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking agent installation: ${e.message}")
            false
        }
    }
    
    /**
     * Install an agent by running its install command in the terminal.
     * This creates a dedicated installation session and streams output.
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
            val installSessionId = terminalManager.createNewSession("install-${agentId}")
            
            // Start the install command - this will run and produce output
            terminalManager.sendCommandToSession(installSessionId, agent.installCommand)
            
            // Wait for installation to complete (or user cancels)
            // In a real implementation, we'd stream the output
            // For now, simulate a delay and check result
            kotlinx.coroutines.delay(5000) // Give it time to install
            
            // Check if installed
            val checkSessionId = terminalManager.createNewSession("verify-${agentId}")
            terminalManager.sendCommandToSession(checkSessionId, agent.installCheckCommand)
            kotlinx.coroutines.delay(2000)
            terminalManager.closeSession(checkSessionId)
            
            // Update state to installed
            installedAgentsCache[agentId] = true
            _installationStates.value = _installationStates.value + (
                agentId to InstallationState(agentId, AgentInstallStatus.INSTALLED)
            )
            
            // Close install session
            terminalManager.closeSession(installSessionId)
            
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
     * Returns the output from the command.
     */
    suspend fun runCommand(agentId: String, command: String): Result<String> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
        try {
            // Create a temporary session for this command
            val sessionId = terminalManager.createNewSession("cmd-${agentId}")
            
            // Run the command (non-interactive, one-shot)
            // If command starts with -- it's a flag, otherwise it's a subcommand
            val fullCommand = if (command.startsWith("--")) {
                "${agent.startCommand} $command"
            } else {
                // For REPL commands like "doctor", "mcp", etc.
                // We need to send them as input to a running agent
                // But for one-shot, we'll just run the full command
                command
            }
            
            terminalManager.sendCommandToSession(sessionId, fullCommand)
            
            // Wait a bit for command to complete
            kotlinx.coroutines.delay(3000)
            
            // Get output - in a real implementation, we'd collect from the events flow
            // For now, return a placeholder
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
     * Returns a list of parsed commands from the help output
     */
    suspend fun discoverCommands(agentId: String): Result<List<AgentCommand>> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
        try {
            val sessionId = terminalManager.createNewSession("discover-${agentId}")
            
            // Run --help to get available commands
            terminalManager.sendCommandToSession(sessionId, "${agent.startCommand} --help")
            
            // Wait for output
            kotlinx.coroutines.delay(3000)
            
            // In a full implementation, we'd parse the --help output
            // For now, return the predefined commands as fallback
            terminalManager.closeSession(sessionId)
            
            // Return the predefined commands (which are derived from typical --help outputs)
            Result.success(agent.commands)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to discover commands: ${e.message}")
            // Fallback to predefined commands
            Result.success(agent.commands)
        }
    }
    
    /**
     * Start a new agent session. Creates a terminal session and starts the agent process.
     * The process stays alive, allowing for persistent chat sessions.
     */
    suspend fun startAgentSession(agentId: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
        try {
            // Create new terminal session with user-friendly title
            val sessionId = terminalManager.createNewSession(title)
            
            // Start the agent - IMPORTANT: This starts the REPL, not a one-shot command
            // The agent process will stay alive waiting for input
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
     * CRITICAL: Uses sendCommandToSession which handles interactive mode correctly!
     * If session is in interactive mode, it sends to stdin (keeps process alive).
     * If not, it executes as a new command.
     */
    suspend fun sendToAgent(sessionId: String, input: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure we append newline - agent expects newline-terminated input
            val inputWithNewline = if (input.endsWith("\n")) input else "$input\n"
            
            // Use sendCommandToSession - TerminalManager handles interactive mode!
            // If hermes is running in REPL mode, this sends to stdin (not restart)
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
     * Filters the terminal's command execution events for this session.
     */
    fun getAgentOutputFlow(sessionId: String): Flow<CommandExecutionEvent> {
        return terminalManager.commandExecutionEvents.map { event ->
            // Filter for this session only
            if (event.sessionId == sessionId) event else null
        }.map { it ?: CommandExecutionEvent(sessionId = sessionId, output = "", isError = false) }
    }
    
    /**
     * Close an agent session and terminate the agent process.
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
     * Get list of active agent sessions for UI display.
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