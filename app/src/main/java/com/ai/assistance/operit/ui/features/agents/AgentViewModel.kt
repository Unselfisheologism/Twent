package com.ai.assistance.operit.ui.features.agents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.AgentCommand
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.AgentInstallStatus
import com.ai.assistance.operit.data.model.AgentRegistry
import com.ai.assistance.operit.data.model.AgentSession
import com.ai.assistance.operit.data.model.CommandCategory
import com.ai.assistance.operit.data.repository.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the agent sessions screen
 */
data class AgentSessionsUiState(
    val sessions: List<AgentSession> = emptyList(),
    val agents: List<AgentWithStatus> = emptyList(),
    val isLoading: Boolean = false,
    val isCheckingInstallations: Boolean = false,
    val error: String? = null
)

/**
 * Agent with status for UI display
 */
data class AgentWithStatus(
    val definition: AgentDefinition,
    val installStatus: AgentInstallStatus,
    val installError: String? = null
)

/**
 * UI State for the agent chat screen
 */
data class AgentChatUiState(
    val sessionId: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isInputEnabled: Boolean = true,
    val error: String? = null
)

/**
 * A chat message (either from user or agent)
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel for managing AI agent sessions
 */
class AgentViewModel(
    private val repository: AgentRepository,
    private val context: Context
) : ViewModel() {

    // Sessions list state
    private val _sessionsState = MutableStateFlow(AgentSessionsUiState())
    val sessionsState: StateFlow<AgentSessionsUiState> = _sessionsState.asStateFlow()

    // Chat state (for current session)
    private val _chatState = MutableStateFlow(AgentChatUiState())
    val chatState: StateFlow<AgentChatUiState> = _chatState.asStateFlow()

    // Current session output collector job
    private var outputCollectorJob: kotlinx.coroutines.Job? = null

    // Command execution state
    private val _commandOutput = MutableStateFlow<String?>(null)
    val commandOutput: StateFlow<String?> = _commandOutput.asStateFlow()

    private val _isRunningCommand = MutableStateFlow(false)
    val isRunningCommand: StateFlow<Boolean> = _isRunningCommand.asStateFlow()

    // Custom agent installation state
    private val _isInstallingCustomAgent = MutableStateFlow(false)
    val isInstallingCustomAgent: StateFlow<Boolean> = _isInstallingCustomAgent.asStateFlow()

    private val _customAgentInstallCommand = MutableStateFlow<String?>(null)
    val customAgentInstallCommand: StateFlow<String?> = _customAgentInstallCommand.asStateFlow()

    // ACP registry loading state
    private val _isAcpRegistryLoading = MutableStateFlow(false)
    val isAcpRegistryLoading: StateFlow<Boolean> = _isAcpRegistryLoading.asStateFlow()

    init {
        // Load sessions on init
        refreshSessions()

        // Load agents with status
        loadAgentsWithStatus()

        // Observe active sessions from repository
        viewModelScope.launch {
            repository.activeSessions.collect { sessions ->
                _sessionsState.value = _sessionsState.value.copy(
                    sessions = sessions.values.toList().sortedByDescending { it.lastActivityAt }
                )
            }
        }

        // Observe installation states
        viewModelScope.launch {
            repository.installationStates.collect { _ ->
                updateAgentsWithStatus()
            }
        }

        // Check all installations in background
        checkAllInstallations()

        // Load ACP registry in background
        loadAcpRegistry()

        // Detect installed non-ACP agents
        detectInstalledNonAcpAgents()
    }

    /**
     * Load agents with their installation status
     * Merges built-in agents with ACP agents
     */
    private fun loadAgentsWithStatus() {
        val mergedAgents = repository.getAllAgentsWithStatus().map { (agent, status) ->
            AgentWithStatus(agent, status)
        }
        _sessionsState.value = _sessionsState.value.copy(agents = mergedAgents)
    }

    /**
     * Update agents with status, preserving ACP agents
     */
    private fun updateAgentsWithStatus() {
        val mergedAgents = repository.getAllAgentsWithStatus().map { (agent, status) ->
            AgentWithStatus(agent, status)
        }
        _sessionsState.value = _sessionsState.value.copy(agents = mergedAgents)
    }

    /**
     * Merge built-in agents with cached ACP agents
     * Note: This is now handled by repository.getAllAgentsWithStatus()
     */
    @Suppress("UNUSED_FUNCTION")
    private fun mergeAgentsWithAcp(builtInAgents: List<Pair<com.ai.assistance.operit.data.model.AgentDefinition, AgentInstallStatus>>): List<AgentWithStatus> {
        val agentsMap = mutableMapOf<String, AgentWithStatus>()

        // Add built-in agents
        for ((agent, status) in builtInAgents) {
            agentsMap[agent.id] = AgentWithStatus(agent, status)
        }

        // Add ACP agents (don't overwrite built-in agents with same ID)
        for ((agent, status) in repository.getCachedAcpAgents()) {
            if (agent.id !in agentsMap) {
                agentsMap[agent.id] = AgentWithStatus(agent, status)
            }
        }

        return agentsMap.values.toList().sortedBy { it.definition.name }
    }

    /**
     * Check all agent installations in background
     */
    private fun checkAllInstallations() {
        viewModelScope.launch {
            _sessionsState.value = _sessionsState.value.copy(isCheckingInstallations = true)
            repository.checkAllInstallations()
            updateAgentsWithStatus()
            _sessionsState.value = _sessionsState.value.copy(isCheckingInstallations = false)
        }
    }

    /**
     * Load ACP registry and merge agents with local registry.
     */
    private fun loadAcpRegistry() {
        viewModelScope.launch {
            _isAcpRegistryLoading.value = true

            val result = repository.fetchAcpAgents()
            result.fold(
                onSuccess = { agentsWithStatus ->
                    // ACP agents are now cached in the repository
                    // Update the UI with all agents
                    val allAgents = repository.getAllAgentsWithStatus().map { (agent, status) ->
                        AgentWithStatus(agent, status)
                    }
                    _sessionsState.value = _sessionsState.value.copy(
                        agents = allAgents
                    )
                },
                onFailure = { error ->
                    // Log error but don't show to user - ACP is supplementary
                    android.util.Log.w("AgentViewModel", "Failed to load ACP registry: ${error.message}")
                }
            )

            _isAcpRegistryLoading.value = false
        }
    }

    /**
     * Refresh ACP registry manually triggered by user.
     */
    fun refreshAcpRegistry() {
        loadAcpRegistry()
    }

    /**
     * Detect installed non-ACP agents and add them to the list.
     */
    private fun detectInstalledNonAcpAgents() {
        viewModelScope.launch {
            val detectedAgents = repository.detectInstalledNonAcpAgents()
            if (detectedAgents.isNotEmpty()) {
                // Update with all agents (detected agents are now cached)
                val allAgents = repository.getAllAgentsWithStatus().map { (agent, status) ->
                    AgentWithStatus(agent, status)
                }
                _sessionsState.value = _sessionsState.value.copy(
                    agents = allAgents
                )
            }
        }
    }

    /**
     * Start custom agent installation by providing a command.
     * This will navigate to terminal where user can run the install command.
     */
    fun startCustomAgentInstallation(installCommand: String) {
        _isInstallingCustomAgent.value = true
        _customAgentInstallCommand.value = installCommand
    }

    /**
     * Complete custom agent installation.
     */
    fun completeCustomAgentInstallation() {
        _isInstallingCustomAgent.value = false
        _customAgentInstallCommand.value = null
    }

    /**
     * Add a custom agent to the registry after successful installation.
     */
    fun addCustomAgent(name: String, description: String, installCommand: String, startCommand: String, deps: List<String>) {
        val id = name.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
        val agent = AgentDefinition(
            id = id,
            name = name,
            description = description,
            icon = "ic_agent_custom",
            installCommand = installCommand,
            installCheckCommand = "which ${startCommand.split(" ").first()}",
            startCommand = startCommand,
            requiredDeps = deps,
            commands = listOf(
                AgentCommand("Version", "--version", "Show version", CommandCategory.SYSTEM),
                AgentCommand("Help", "--help", "Show help", CommandCategory.HELP),
                AgentCommand("Exit", "exit", "Exit", CommandCategory.SYSTEM)
            ),
            isCustom = true
        )

        AgentRegistry.addCustomAgent(agent)
        // The repository will handle marking this as installed
        loadAgentsWithStatus()
    }

    /**
     * Get list of available agents
     */
    fun getAvailableAgents(): List<AgentDefinition> {
        return repository.getAvailableAgents()
    }

    /**
     * Refresh the sessions list
     */
    fun refreshSessions() {
        _sessionsState.value = _sessionsState.value.copy(
            sessions = repository.getActiveSessionsList()
        )
    }

    /**
     * Install an agent
     */
    fun installAgent(agentId: String) {
        viewModelScope.launch {
            _sessionsState.value = _sessionsState.value.copy(isLoading = true, error = null)

            val result = repository.installAgent(agentId) { _ -> }

            result.fold(
                onSuccess = {
                    _sessionsState.value = _sessionsState.value.copy(isLoading = false)
                    updateAgentsWithStatus()
                },
                onFailure = { error ->
                    _sessionsState.value = _sessionsState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Installation failed"
                    )
                    updateAgentsWithStatus()
                }
            )
        }
    }

    /**
     * Start a new agent session (only if installed)
     * Note: This is now deprecated in favor of launchNativeTerminal
     */
    fun startAgentSession(agentId: String, agentName: String) {
        // This is kept for backward compatibility but should not be used
    }

    /**
     * Launch native terminal with the agent CLI command.
     * Works with both local registry agents and ACP-sourced agents.
     * For custom agents, uses the provided install command directly.
     */
    fun launchNativeTerminal(
        agentId: String,
        agentName: String,
        onNavigateToTerminal: (String) -> Unit
    ) {
        // Try to find agent in local registry first
        var agent = AgentRegistry.getById(agentId)

        // If not found in local registry, check ACP cached agents
        if (agent == null) {
            agent = repository.getCachedAcpAgents().find { it.first.id == agentId }?.first
        }

        // If not found in local registry, it might be an ACP agent or custom
        if (agent == null) {
            // For custom agent installations, we navigate with the install command
            if (agentId.startsWith("custom-")) {
                // This is a custom agent installation - user will provide commands manually
                onNavigateToTerminal("")
                return
            }
        }

        if (agent == null) {
            _sessionsState.value = _sessionsState.value.copy(
                error = "Unknown agent: $agentId"
            )
            return
        }

        // Check if agent is installed
        val agentWithStatus = repository.getAgentWithStatus(agentId)
        val isInstalled = agentWithStatus?.second == AgentInstallStatus.INSTALLED

        // Navigate to terminal with the appropriate command
        val command = if (isInstalled) agent.startCommand else agent.installCommand
        onNavigateToTerminal(command)
    }

    /**
     * Re-check installation status for all agents.
     * Call this when user returns from terminal.
     */
    fun refreshInstallationStatus() {
        viewModelScope.launch {
            _sessionsState.value = _sessionsState.value.copy(isCheckingInstallations = true)
            repository.checkAllInstallations()
            updateAgentsWithStatus()
            _sessionsState.value = _sessionsState.value.copy(isCheckingInstallations = false)
        }
    }

    /**
     * Start an agent session (requires agent to be installed).
     */
    fun startAgentSession(agentId: String, onNavigateToTerminal: (String) -> Unit) {
        val agent = AgentRegistry.getById(agentId)
            ?: repository.getCachedAcpAgents().find { it.first.id == agentId }?.first

        if (agent == null) {
            _sessionsState.value = _sessionsState.value.copy(
                error = "Unknown agent: $agentId"
            )
            return
        }

        // Check if agent is installed
        val agentWithStatus = repository.getAgentWithStatus(agentId)
        if (agentWithStatus?.second != AgentInstallStatus.INSTALLED) {
            _sessionsState.value = _sessionsState.value.copy(
                error = "${agent.name} is not installed. Please install it first."
            )
            return
        }

        // Create a session and navigate to terminal
        viewModelScope.launch {
            val title = "${agent.name} - Session ${System.currentTimeMillis() % 1000}"
            repository.startAgentSession(agentId, title)
            onNavigateToTerminal(agent.startCommand)
        }
    }

    /**
     * Switch to chat view for a specific session
     */
    fun switchToChat(sessionId: String, agentId: String, agentName: String) {
        _chatState.value = AgentChatUiState(
            sessionId = sessionId,
            agentId = agentId,
            agentName = agentName,
            messages = listOf(
                ChatMessage(
                    content = "Session started. You can now interact with $agentName.",
                    isFromUser = false
                )
            ),
            isInputEnabled = true
        )

        // Start collecting output
        startOutputCollection(sessionId)
    }

    /**
     * Send a message to the agent (including slash commands)
     */
    fun sendChatMessage(content: String) {
        if (content.isBlank() || _chatState.value.sessionId.isEmpty()) return

        val sessionId = _chatState.value.sessionId

        // Add user message to chat
        val userMessage = ChatMessage(content = content, isFromUser = true)
        _chatState.value = _chatState.value.copy(
            messages = _chatState.value.messages + userMessage,
            isInputEnabled = false
        )

        // Send to agent
        viewModelScope.launch {
            // Remove leading "/" if it's a slash command
            val inputToSend = if (content.trim().startsWith("/")) {
                content.trim().removePrefix("/").trim()
            } else {
                content
            }

            repository.sendToAgent(sessionId, inputToSend)
            _chatState.value = _chatState.value.copy(isInputEnabled = true)
        }
    }

    /**
     * Run a non-chat command
     */
    fun runAgentCommand(agentId: String, command: String) {
        viewModelScope.launch {
            _isRunningCommand.value = true
            _commandOutput.value = null

            val result = repository.runCommand(agentId, command)

            result.fold(
                onSuccess = { output ->
                    _commandOutput.value = output
                },
                onFailure = { error ->
                    _commandOutput.value = "Error: ${error.message}"
                }
            )

            _isRunningCommand.value = false
        }
    }

    /**
     * Get commands for an agent
     */
    fun getAgentCommands(agentId: String): List<AgentCommand> {
        val agent = AgentRegistry.getById(agentId) ?: return emptyList()
        return agent.commands
    }

    /**
     * Run a slash command directly from button click
     */
    fun runSlashCommand(command: AgentCommand) {
        val sessionId = _chatState.value.sessionId
        if (sessionId.isEmpty()) return

        // Add the command as a user message
        val cmdMessage = ChatMessage(
            content = "/${command.command}",
            isFromUser = true
        )

        viewModelScope.launch {
            _chatState.value = _chatState.value.copy(
                messages = _chatState.value.messages + cmdMessage,
                isInputEnabled = false
            )

            repository.sendToAgent(sessionId, command.command)
            _chatState.value = _chatState.value.copy(isInputEnabled = true)
        }
    }

    fun clearCommandOutput() {
        _commandOutput.value = null
    }

    /**
     * Start collecting output from the agent session
     */
    private fun startOutputCollection(sessionId: String) {
        outputCollectorJob?.cancel()
        outputCollectorJob = viewModelScope.launch {
            repository.getAgentOutputFlow(sessionId).collect { event ->
                if (event.outputChunk.isNotBlank() && event.sessionId == sessionId) {
                    val currentMessages = _chatState.value.messages.toMutableList()

                    // Check if the last message is from agent (append) or new (add)
                    if (currentMessages.isNotEmpty() && !currentMessages.last().isFromUser) {
                        // Append to existing agent message
                        val lastMsg = currentMessages.removeLast()
                        val updatedMsg = lastMsg.copy(content = lastMsg.content + event.outputChunk)
                        currentMessages.add(updatedMsg)
                    } else {
                        // New agent message
                        currentMessages.add(
                            ChatMessage(
                                content = event.outputChunk,
                                isFromUser = false
                            )
                        )
                    }

                    _chatState.value = _chatState.value.copy(messages = currentMessages)
                }
            }
        }
    }

    /**
     * Close an agent session
     */
    fun closeSession(sessionId: String) {
        viewModelScope.launch {
            repository.closeAgentSession(sessionId)

            if (_chatState.value.sessionId == sessionId) {
                outputCollectorJob?.cancel()
                _chatState.value = AgentChatUiState()
            }

            refreshSessions()
        }
    }

    /**
     * Check if a session is an agent session
     */
    fun isAgentSession(sessionId: String): Boolean {
        return repository.isAgentSession(sessionId)
    }

    override fun onCleared() {
        super.onCleared()
        outputCollectorJob?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = AgentRepository(context.applicationContext)
            return AgentViewModel(repository, context) as T
        }
    }
}
