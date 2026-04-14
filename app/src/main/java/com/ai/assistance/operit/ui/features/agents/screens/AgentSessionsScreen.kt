package com.ai.assistance.operit.ui.features.agents.screens

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
                    agents = sessionsState.agents,
                    isLoading = sessionsState.isLoading,
                    isCheckingInstallations = sessionsState.isCheckingInstallations,
                    error = sessionsState.error,
                    onSessionClose = { sessionId ->
                        viewModel.closeSession(sessionId)
                    },
                    onStartAgent = { agentId ->
                        viewModel.startAgentSession(agentId) { command ->
                            onNavigateToTerminal(command)
                        }
                    },
                    onRefreshStatus = { viewModel.refreshInstallationStatus() }
                )
                1 -> AgentMarketTab(
                    agents = sessionsState.agents,
                    isCheckingInstallations = sessionsState.isCheckingInstallations,
                    isLoading = sessionsState.isLoading,
                    error = sessionsState.error,
                    onInstall = { agentId ->
                        // Navigate to terminal with install command instead of background installation
                        sessionsState.agents.find { it.definition.id == agentId }?.definition?.let { agent ->
                            onNavigateToTerminal(agent.installCommand)
                        }
                    },
                    onLaunchTerminal = { agentId, agentName ->
                        viewModel.launchNativeTerminal(agentId, agentName) { command ->
                            onNavigateToTerminal(command)
                        }
                    },
                    onCommands = { agentId -> onNavigateToCommands(agentId) },
                    onAddCustomAgent = { /* Dialog will be shown */ },
                    onRefreshAcp = { viewModel.refreshAcpRegistry() }
                )
            }
        }
    }
}

@Composable
private fun SessionsTab(
    sessions: List<AgentSession>,
    agents: List<AgentWithStatus>,
    isLoading: Boolean,
    isCheckingInstallations: Boolean,
    error: String?,
    onSessionClose: (String) -> Unit,
    onStartAgent: (String) -> Unit,
    onRefreshStatus: () -> Unit = {}
) {
    // Get installed agents
    val installedAgents = agents.filter { it.installStatus == AgentInstallStatus.INSTALLED }

    Column(modifier = Modifier.fillMaxSize()) {
        // Refresh status button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRefreshStatus) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh Status")
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

        // Show active sessions if any
        if (sessions.isNotEmpty()) {
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
        } else if (installedAgents.isNotEmpty()) {
            // No sessions but there are installed agents
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    "No Active Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    "Start an agent to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (isCheckingInstallations) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking installation status...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(installedAgents) { agentWithStatus ->
                        InstalledAgentCard(
                            agentWithStatus = agentWithStatus,
                            isLoading = isLoading,
                            onStartAgent = { onStartAgent(agentWithStatus.definition.id) }
                        )
                    }
                }
            }
        } else {
            // Empty state - no sessions and no installed agents
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
                    "No Agent CLIs Installed",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Install an Agent CLI from the Agent Market to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InstalledAgentCard(
    agentWithStatus: AgentWithStatus,
    isLoading: Boolean,
    onStartAgent: () -> Unit
) {
    val agent = agentWithStatus.definition

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
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFF4CAF50)
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
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Start button
            Button(onClick = onStartAgent, enabled = !isLoading) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start")
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
    onLaunchTerminal: (String, String) -> Unit,
    onCommands: (String) -> Unit,
    onAddCustomAgent: () -> Unit = {},
    onRefreshAcp: () -> Unit = {}
) {
    var showCustomAgentDialog by remember { mutableStateOf(false) }
    var customAgentName by remember { mutableStateOf("") }
    var customAgentDescription by remember { mutableStateOf("") }
    var customAgentInstallCommand by remember { mutableStateOf("") }
    var customAgentStartCommand by remember { mutableStateOf("") }
    var customAgentDeps by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Refresh ACP registry button
            IconButton(
                onClick = onRefreshAcp,
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh ACP Registry",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Add custom agent button
            IconButton(
                onClick = { showCustomAgentDialog = true }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Custom Agent",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Loading indicator for ACP registry
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
                        onLaunchTerminal(agentWithStatus.definition.id, agentWithStatus.definition.name)
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

    // Custom Agent Dialog
    if (showCustomAgentDialog) {
        CustomAgentDialog(
            agentName = customAgentName,
            onNameChange = { customAgentName = it },
            agentDescription = customAgentDescription,
            onDescriptionChange = { customAgentDescription = it },
            installCommand = customAgentInstallCommand,
            onInstallCommandChange = { customAgentInstallCommand = it },
            startCommand = customAgentStartCommand,
            onStartCommandChange = { customAgentStartCommand = it },
            deps = customAgentDeps,
            onDepsChange = { customAgentDeps = it },
            onDismiss = { showCustomAgentDialog = false },
            onInstallViaTerminal = { name, description, installCmd, startCmd, depsList ->
                // Launch terminal with the install command
                onLaunchTerminal(installCmd, name)
                showCustomAgentDialog = false
                // Reset fields
                customAgentName = ""
                customAgentDescription = ""
                customAgentInstallCommand = ""
                customAgentStartCommand = ""
                customAgentDeps = ""
            }
        )
    }
}

@Composable
private fun CustomAgentDialog(
    agentName: String,
    onNameChange: (String) -> Unit,
    agentDescription: String,
    onDescriptionChange: (String) -> Unit,
    installCommand: String,
    onInstallCommandChange: (String) -> Unit,
    startCommand: String,
    onStartCommandChange: (String) -> Unit,
    deps: String,
    onDepsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onInstallViaTerminal: (String, String, String, String, List<String>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Agent CLI") },
        text = {
            Column {
                OutlinedTextField(
                    value = agentName,
                    onValueChange = onNameChange,
                    label = { Text("Agent Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = agentDescription,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = installCommand,
                    onValueChange = onInstallCommandChange,
                    label = { Text("Install Command") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., npm i -g my-agent") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startCommand,
                    onValueChange = onStartCommandChange,
                    label = { Text("Start Command") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., my-agent") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deps,
                    onValueChange = onDepsChange,
                    label = { Text("Dependencies (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., node, npm") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (agentName.isNotBlank() && installCommand.isNotBlank() && startCommand.isNotBlank()) {
                        val depsList = deps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onInstallViaTerminal(agentName, agentDescription, installCommand, startCommand, depsList)
                    }
                },
                enabled = agentName.isNotBlank() &&
                          installCommand.isNotBlank() &&
                          startCommand.isNotBlank()
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Install via Terminal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AgentMarketCard(
    agentWithStatus: AgentWithStatus,
    isLoading: Boolean,
    onInstall: () -> Unit,
    onLaunchTerminal: () -> Unit,
    onCommands: () -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        // ACP badge
                        if (agent.isFromAcp) {
                            AssistChip(
                                onClick = { },
                                label = { Text("ACP", style = MaterialTheme.typography.labelSmall) },
                                enabled = false,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        // Custom badge
                        if (agent.isCustom) {
                            AssistChip(
                                onClick = { },
                                label = { Text("Custom", style = MaterialTheme.typography.labelSmall) },
                                enabled = false,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Version from ACP
                    agent.acpVersion?.let { version ->
                        Text(
                            text = "Version: $version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Required deps
                    if (agent.requiredDeps.isNotEmpty()) {
                        Text(
                            text = "Requires: ${agent.requiredDeps.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Tags from ACP
                    agent.acpTags?.let { tags ->
                        if (tags.isNotEmpty()) {
                            Text(
                                text = tags.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }

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
