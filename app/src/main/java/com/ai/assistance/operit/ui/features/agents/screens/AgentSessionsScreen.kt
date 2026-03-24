package com.ai.assistance.operit.ui.features.agents.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.AgentInstallStatus
import com.ai.assistance.operit.data.model.AgentSession
import com.ai.assistance.operit.ui.features.agents.AgentViewModel
import com.ai.assistance.operit.ui.features.agents.AgentWithStatus

/**
 * Agent Sessions Screen - Shows running agent sessions and agent marketplace
 * Note: This screen now launches the native terminal instead of in-app chat sessions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSessionsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCommands: (String) -> Unit = { _ -> },
    onNavigateToTerminal: (String) -> Unit = { _ -> }
) {
    val context = LocalContext.current
    val viewModel: AgentViewModel = viewModel(
        factory = AgentViewModel.Factory(context.applicationContext)
    )
    
    val sessionsState by viewModel.sessionsState.collectAsState()
    
    // Tab state: 0 = Sessions, 1 = Agent Market
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent CLIs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sessions") },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Agent Market") },
                    icon = { Icon(Icons.Default.Store, contentDescription = null) }
                )
            }
            
            // Content based on tab
            when (selectedTab) {
                0 -> SessionsTab(
                    sessions = sessionsState.sessions,
                    isLoading = sessionsState.isLoading,
                    error = sessionsState.error,
                    onSessionClose = { sessionId ->
                        viewModel.closeSession(sessionId)
                    },
                    onStartNew = { selectedTab = 1 }
                )
                1 -> AgentMarketTab(
                    agents = sessionsState.agents,
                    isCheckingInstallations = sessionsState.isCheckingInstallations,
                    isLoading = sessionsState.isLoading,
                    error = sessionsState.error,
                    onInstall = { agentId -> viewModel.installAgent(agentId) },
                    onLaunchTerminal = { agentId, agentName ->
                        viewModel.launchNativeTerminal(agentId, agentName, onNavigateToTerminal)
                    },
                    onCommands = { agentId -> onNavigateToCommands(agentId) }
                )
            }
        }
    }
}

@Composable
private fun SessionsTab(
    sessions: List<AgentSession>,
    isLoading: Boolean,
    error: String?,
    onSessionClose: (String) -> Unit,
    onStartNew: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Error message
        error?.let { errorMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        if (sessions.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Active Agent Sessions",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Start an AI agent to help you with coding tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onStartNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Agent")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    AgentSessionCard(
                        session = session,
                        onClose = { onSessionClose(session.id) }
                    )
                }
            }
        }
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AgentMarketTab(
    agents: List<AgentWithStatus>,
    isCheckingInstallations: Boolean,
    isLoading: Boolean,
    error: String?,
    onInstall: (String) -> Unit,
    onLaunchTerminal: (String, String, (String) -> Unit) -> Unit,
    onCommands: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Loading indicator for checking installations
        if (isCheckingInstallations) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Checking installed agents...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Error message
        error?.let { errorMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                items(agents) { agentWithStatus ->
                AgentMarketCard(
                    agentWithStatus = agentWithStatus,
                    isLoading = isLoading,
                    onInstall = { onInstall(agentWithStatus.definition.id) },
                    onLaunchTerminal = { 
                        onLaunchTerminal(agentWithStatus.definition.id, agentWithStatus.definition.name, onNavigateToTerminal) 
                    },
                    onCommands = { onCommands(agentWithStatus.definition.id) }
                )
            }
        }
        
        // Loading indicator for operations
        if (isLoading && !isCheckingInstallations) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AgentMarketCard(
    agentWithStatus: AgentWithStatus,
    isLoading: Boolean,
    onInstall: () -> Unit,
    onLaunchTerminal: () -> Unit,
    onCommands: () -> Unit,
    onNavigateToTerminal: (String) -> Unit = { _ -> }
) {
    val agent = agentWithStatus.definition
    val status = agentWithStatus.installStatus
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Agent icon
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Agent info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Required deps
                    Text(
                        text = "Requires: ${agent.requiredDeps.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    // Commands count
                    if (agent.commands.isNotEmpty()) {
                        Text(
                            text = "${agent.commands.size} commands available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        AgentInstallStatus.NOT_INSTALLED -> {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Not Installed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        AgentInstallStatus.INSTALLING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Installing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AgentInstallStatus.INSTALLED -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Installed",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        AgentInstallStatus.FAILED -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Install Failed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Commands button (always visible if commands exist)
                    if (agent.commands.isNotEmpty() && status == AgentInstallStatus.INSTALLED) {
                        OutlinedButton(
                            onClick = onCommands,
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Commands")
                        }
                    }
                    
                    // Install/Start button
                    when (status) {
                        AgentInstallStatus.NOT_INSTALLED, AgentInstallStatus.FAILED -> {
                            Button(
                                onClick = onInstall,
                                enabled = !isLoading && status != AgentInstallStatus.INSTALLING
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install")
                            }
                        }
                        AgentInstallStatus.INSTALLING -> {
                            OutlinedButton(
                                onClick = { },
                                enabled = false
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        AgentInstallStatus.INSTALLED -> {
                            Button(onClick = onLaunchTerminal) {
                                Icon(Icons.Default.Terminal, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Launch Terminal")
                            }
                        }
                    }
                }
            }
            
            // Install command shown for reference
            if (status == AgentInstallStatus.NOT_INSTALLED || status == AgentInstallStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Install: ${agent.installCommand}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AgentSessionCard(
    session: AgentSession,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Agent icon
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Started: ${formatTimestamp(session.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Green)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Session",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "${diff / 86400_000} days ago"
    }
}