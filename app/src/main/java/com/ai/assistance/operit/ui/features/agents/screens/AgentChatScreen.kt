package com.ai.assistance.operit.ui.features.agents.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.AgentCommand
import com.ai.assistance.operit.data.model.AgentRegistry
import com.ai.assistance.operit.ui.features.agents.AgentViewModel
import com.ai.assistance.operit.ui.features.agents.ChatMessage

/**
 * Agent Chat Screen - Chat interface for interacting with a running agent
 * Includes slash command support and quick action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    sessionId: String,
    agentId: String,
    agentName: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AgentViewModel = viewModel(
        factory = AgentViewModel.Factory(context.applicationContext)
    )
    
    // Find the agent ID from session (passed from navigation)
    // This will be used for getting commands
    
    // Initialize chat with this session on first composition
    LaunchedEffect(sessionId, agentId, agentName) {
        viewModel.switchToChat(sessionId, agentId, agentName)
    }
    
    val chatState by viewModel.chatState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    
    // Get available commands for this agent
    val availableCommands = remember(agentId) {
        viewModel.getAgentCommands(agentId)
    }
    
    // Show quick commands panel
    var showCommandsPanel by remember { mutableStateOf(false) }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(agentName, fontWeight = FontWeight.Bold)
                        Text(
                            "Agent Session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Toggle commands panel
                    IconButton(onClick = { showCommandsPanel = !showCommandsPanel }) {
                        Icon(
                            if (showCommandsPanel) Icons.Default.ExpandLess else Icons.Default.Terminal,
                            contentDescription = "Toggle Commands"
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.closeSession(sessionId)
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Close Session",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            AgentInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendChatMessage(inputText)
                        inputText = ""
                    }
                },
                isEnabled = chatState.isInputEnabled,
                onSlashCommandsClick = { showCommandsPanel = !showCommandsPanel }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Commands Panel (collapsible)
            if (showCommandsPanel && availableCommands.isNotEmpty()) {
                QuickCommandsPanel(
                    commands = availableCommands,
                    onCommandClick = { command ->
                        viewModel.runSlashCommand(command)
                        showCommandsPanel = false
                    }
                )
            }
            
            // Messages list
            if (chatState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Start chatting with $agentName",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Type a message or tap a command below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatState.messages) { message ->
                        ChatMessageItem(message = message)
                    }
                }
            }
            
            // Loading overlay
            if (!chatState.isInputEnabled && chatState.messages.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun QuickCommandsPanel(
    commands: List<AgentCommand>,
    onCommandClick: (AgentCommand) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Quick Commands",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Horizontal scrollable command chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commands.forEach { command ->
                    CommandChip(
                        command = command,
                        onClick = { onCommandClick(command) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandChip(
    command: AgentCommand,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = { 
            Text(
                "/${command.command}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            ) 
        },
        icon = {
            Icon(
                when (command.category) {
                    com.ai.assistance.operit.data.model.CommandCategory.SYSTEM -> Icons.Default.Build
                    com.ai.assistance.operit.data.model.CommandCategory.SETUP -> Icons.Default.Settings
                    com.ai.assistance.operit.data.model.CommandCategory.MCP -> Icons.Default.Extension
                    com.ai.assistance.operit.data.model.CommandCategory.HELP -> Icons.Default.Help
                    else -> Icons.Default.Terminal
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.isFromUser
    
    // Check if it's a slash command (starts with /)
    val isSlashCommand = message.content.trim().startsWith("/")
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Agent avatar
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    when {
                        isUser -> MaterialTheme.colorScheme.primary
                        isSlashCommand -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .padding(12.dp)
        ) {
            Column {
                if (isSlashCommand) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Command",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isSlashCommand) FontFamily.Monospace else FontFamily.Default,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        isSlashCommand -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        
        if (isUser) {
            // User avatar
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(start = 8.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AgentInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    onSlashCommandsClick: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slash commands button
            IconButton(onClick = onSlashCommandsClick) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = "Commands",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        if (inputText.startsWith("/")) "Type command..." 
                        else "Type your message..."
                    )
                },
                enabled = isEnabled,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledIconButton(
                onClick = onSend,
                enabled = isEnabled && inputText.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}