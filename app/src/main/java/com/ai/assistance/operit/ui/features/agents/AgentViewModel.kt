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
    }

    /**
     * Load agents with their installation status
     */
    private fun loadAgentsWithStatus() {
        val agentsWithStatus = repository.getAgentsWithStatus().map { (agent, status) ->
            AgentWithStatus(agent, status)
        }
        _sessionsState.value = _sessionsState.value.copy(agents = agentsWithStatus)
    }
    
    private fun updateAgentsWithStatus() {
        val agentsWithStatus = repository.getAgentsWithStatus().map { (agent, status) ->
            AgentWithStatus(agent, status)
        }
        _sessionsState.value = _sessionsState.value.copy(agents = agentsWithStatus)
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
     */
    fun startAgentSession(agentId: String, agentName: String) {
        viewModelScope.launch {
            _sessionsState.value = _sessionsState.value.copy(isLoading = true, error = null)
            
            val title = "$agentName - Session ${System.currentTimeMillis() % 1000}"
            val result = repository.startAgentSession(agentId, title)
            
            result.fold(
                onSuccess = { sessionId ->
                    _sessionsState.value = _sessionsState.value.copy(isLoading = false)
                    switchToChat(sessionId, agentId, agentName)
                },
                onFailure = { error ->
                    _sessionsState.value = _sessionsState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to start session"
                    )
                }
            )
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