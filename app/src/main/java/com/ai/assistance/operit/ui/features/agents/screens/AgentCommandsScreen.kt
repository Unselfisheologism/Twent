package com.ai.assistance.operit.ui.features.agents.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.AgentCommand
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.CommandCategory
import com.ai.assistance.operit.ui.features.agents.AgentViewModel

/**
 * Agent Commands Screen - Run non-chat commands like doctor, setup, mcp, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCommandsScreen(
    agentId: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AgentViewModel = viewModel(
        factory = AgentViewModel.Factory(context.applicationContext)
    )
    
    val agents by viewModel.sessionsState.collectAsState()
    val agent = agents.agents.find { it.definition.id == agentId }
    
    val commandOutput by viewModel.commandOutput.collectAsState()
    val isRunningCommand by viewModel.isRunningCommand.collectAsState()
    
    var selectedCategory by remember { mutableStateOf<CommandCategory?>(null) }
    
    if (agent == null) {
        // Agent not found
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Agent not found")
        }
        return
    }
    
    val commands = agent.definition.commands
    val filteredCommands = if (selectedCategory != null) {
        commands.filter { it.category == selectedCategory }
    } else {
        commands
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("${agent.definition.name} Commands", fontWeight = FontWeight.Bold)
                        Text(
                            "Run non-chat commands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
            // Category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All button
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
                
                // Category buttons
                CommandCategory.entries.filter { it != CommandCategory.OTHER }.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(
                                when (category) {
                                    CommandCategory.SYSTEM -> Icons.Default.Build
                                    CommandCategory.SETUP -> Icons.Default.Settings
                                    CommandCategory.MCP -> Icons.Default.Extension
                                    CommandCategory.HELP -> Icons.Default.Help
                                    else -> Icons.Default.Terminal
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            
            // Add Custom Command button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { /* TODO: Add custom command dialog */ }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Custom Command")
                }
            }
            
            // Command list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCommands) { command ->
                    CommandCard(
                        command = command,
                        isRunning = isRunningCommand,
                        onRun = {
                            viewModel.runAgentCommand(agentId, command.command)
                        }
                    )
                }
            }
            
            // Output section
            if (commandOutput != null || isRunningCommand) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Output",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearCommandOutput() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isRunningCommand) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Running command...")
                        }
                    }
                    
                    if (commandOutput != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    commandOutput!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: AgentCommand,
    isRunning: Boolean,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Icon(
                when (command.category) {
                    CommandCategory.SYSTEM -> Icons.Default.Build
                    CommandCategory.SETUP -> Icons.Default.Settings
                    CommandCategory.MCP -> Icons.Default.Extension
                    CommandCategory.HELP -> Icons.Default.Help
                    CommandCategory.OTHER -> Icons.Default.Terminal
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Command info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    command.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    command.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    command.command,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Run button
            Button(
                onClick = onRun,
                enabled = !isRunning
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Run")
            }
        }
    }
}