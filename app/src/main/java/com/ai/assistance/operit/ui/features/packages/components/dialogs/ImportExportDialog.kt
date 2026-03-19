package com.ai.assistance.operit.ui.features.packages.components.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.exporter.ImportExportManager
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.Workflow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Import/Export Dialog for managing chats, workflows, skills, and MCP servers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportDialog(
    onDismiss: () -> Unit,
    onExportComplete: (String) -> Unit = {},
    onImportComplete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importExportManager = remember { ImportExportManager.getInstance(context) }

    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val tabs = listOf(
        stringResource(R.string.import_export_chats),
        stringResource(R.string.import_export_workflows),
        stringResource(R.string.import_export_skills),
        stringResource(R.string.import_export_mcp_servers)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.import_export_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on selected tab
                when (selectedTab) {
                    0 -> ChatsTabContent(
                        importExportManager = importExportManager,
                        isLoading = isLoading,
                        statusMessage = statusMessage,
                        onImportClick = { uri ->
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.importChats(uri)
                                result.fold(
                                    onSuccess = { count ->
                                        statusMessage = context.getString(R.string.import_export_imported_chats, count)
                                        onImportComplete("$count chats imported")
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        },
                        onExportAllClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.exportAllChats()
                                result.fold(
                                    onSuccess = { file ->
                                        statusMessage = context.getString(R.string.import_export_exported_to, file.absolutePath)
                                        onExportComplete(file.absolutePath)
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        }
                    )
                    1 -> WorkflowsTabContent(
                        importExportManager = importExportManager,
                        isLoading = isLoading,
                        statusMessage = statusMessage,
                        onImportClick = { uri ->
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.importWorkflows(uri)
                                result.fold(
                                    onSuccess = { count ->
                                        statusMessage = context.getString(R.string.import_export_imported_workflows, count)
                                        onImportComplete("$count workflows imported")
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        },
                        onExportAllClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.exportAllWorkflows()
                                result.fold(
                                    onSuccess = { file ->
                                        statusMessage = context.getString(R.string.import_export_exported_to, file.absolutePath)
                                        onExportComplete(file.absolutePath)
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        }
                    )
                    2 -> SkillsTabContent(
                        importExportManager = importExportManager,
                        isLoading = isLoading,
                        statusMessage = statusMessage,
                        onImportClick = { uri ->
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.importSkills(uri)
                                result.fold(
                                    onSuccess = { count ->
                                        statusMessage = context.getString(R.string.import_export_imported_skills, count)
                                        onImportComplete("$count skills imported")
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        },
                        onExportAllClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.exportAllSkills()
                                result.fold(
                                    onSuccess = { file ->
                                        statusMessage = context.getString(R.string.import_export_exported_to, file.absolutePath)
                                        onExportComplete(file.absolutePath)
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        }
                    )
                    3 -> MCPServersTabContent(
                        importExportManager = importExportManager,
                        isLoading = isLoading,
                        statusMessage = statusMessage,
                        onImportClick = { uri ->
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.importMCPServers(uri)
                                result.fold(
                                    onSuccess = { count ->
                                        statusMessage = context.getString(R.string.import_export_imported_mcp_servers, count)
                                        onImportComplete("$count MCP servers imported")
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        },
                        onExportAllClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = importExportManager.exportAllMCPServers()
                                result.fold(
                                    onSuccess = { file ->
                                        statusMessage = context.getString(R.string.import_export_exported_to, file.absolutePath)
                                        onExportComplete(file.absolutePath)
                                    },
                                    onFailure = { e ->
                                        statusMessage = context.getString(R.string.import_export_error, e.message)
                                    }
                                )
                                isLoading = false
                            }
                        }
                    )
                }

                // Status message
                statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Loading indicator
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_export_processing))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_export_close))
            }
        }
    )
}

@Composable
private fun ChatsTabContent(
    importExportManager: ImportExportManager,
    isLoading: Boolean,
    statusMessage: String?,
    onImportClick: (Uri) -> Unit,
    onExportAllClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var availableChats by remember { mutableStateOf<List<ChatHistory>>(emptyList()) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }

    // Load available chats
    LaunchedEffect(Unit) {
        availableChats = importExportManager.getAvailableChatsForExport()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportClick(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_export_chats_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch("application/zip,*/*") },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_action))
            }

            Button(
                onClick = onExportAllClick,
                enabled = !isLoading && availableChats.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_all))
            }
        }

        // Chat list for selective export
        if (availableChats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.import_export_select_chat_to_export),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp)
            ) {
                items(availableChats.take(5)) { chat ->
                    ChatExportItem(
                        chat = chat,
                        isSelected = selectedChatId == chat.id,
                        onSelect = { selectedChatId = if (selectedChatId == chat.id) null else chat.id }
                    )
                }
            }

            // Export selected button
            if (selectedChatId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            selectedChatId?.let { chatId ->
                                val result = importExportManager.exportChat(chatId)
                                result.onSuccess { file ->
                                    // Handle success - the status message will be shown
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.export_selected))
                }
            }
        }
    }
}

@Composable
private fun ChatExportItem(
    chat: ChatHistory,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Chat,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title ?: "Chat ${chat.id.take(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${chat.messages.size} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun WorkflowsTabContent(
    importExportManager: ImportExportManager,
    isLoading: Boolean,
    statusMessage: String?,
    onImportClick: (Uri) -> Unit,
    onExportAllClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var availableWorkflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }
    var selectedWorkflowId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        availableWorkflows = importExportManager.getAvailableWorkflowsForExport()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportClick(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_export_workflows_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch("application/zip,*/*") },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_action))
            }

            Button(
                onClick = onExportAllClick,
                enabled = !isLoading && availableWorkflows.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_all))
            }
        }

        if (availableWorkflows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.import_export_select_workflow_to_export),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp)
            ) {
                items(availableWorkflows.take(5)) { workflow ->
                    WorkflowExportItem(
                        workflow = workflow,
                        isSelected = selectedWorkflowId == workflow.id,
                        onSelect = { selectedWorkflowId = if (selectedWorkflowId == workflow.id) null else workflow.id }
                    )
                }
            }

            if (selectedWorkflowId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            selectedWorkflowId?.let { workflowId ->
                                val result = importExportManager.exportWorkflow(workflowId)
                                result.onSuccess { /* Handle success */ }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.export_selected))
                }
            }
        }
    }
}

@Composable
private fun WorkflowExportItem(
    workflow: Workflow,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${workflow.nodes.size} nodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SkillsTabContent(
    importExportManager: ImportExportManager,
    isLoading: Boolean,
    statusMessage: String?,
    onImportClick: (Uri) -> Unit,
    onExportAllClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var availableSkills by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSkill by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        availableSkills = importExportManager.getAvailableSkillsForExport()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportClick(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_export_skills_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch("application/zip,*/*") },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_action))
            }

            Button(
                onClick = onExportAllClick,
                enabled = !isLoading && availableSkills.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_all))
            }
        }

        if (availableSkills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.import_export_select_skill_to_export),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp)
            ) {
                items(availableSkills.take(5)) { skillName ->
                    SkillExportItem(
                        skillName = skillName,
                        isSelected = selectedSkill == skillName,
                        onSelect = { selectedSkill = if (selectedSkill == skillName) null else skillName }
                    )
                }
            }

            if (selectedSkill != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            selectedSkill?.let { skillName ->
                                val result = importExportManager.exportSkill(skillName)
                                result.onSuccess { /* Handle success */ }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.export_selected))
                }
            }
        }
    }
}

@Composable
private fun SkillExportItem(
    skillName: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = skillName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MCPServersTabContent(
    importExportManager: ImportExportManager,
    isLoading: Boolean,
    statusMessage: String?,
    onImportClick: (Uri) -> Unit,
    onExportAllClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var availableServers by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        availableServers = importExportManager.getAvailableMCPServersForExport()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportClick(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_export_mcp_servers_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch("application/zip,application/json,*/*") },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_action))
            }

            Button(
                onClick = onExportAllClick,
                enabled = !isLoading && availableServers.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_all))
            }
        }

        if (availableServers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.import_export_select_mcp_to_export),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp)
            ) {
                items(availableServers.take(5)) { serverId ->
                    MCPServerExportItem(
                        serverId = serverId,
                        isSelected = selectedServerId == serverId,
                        onSelect = { selectedServerId = if (selectedServerId == serverId) null else serverId }
                    )
                }
            }

            if (selectedServerId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            selectedServerId?.let { serverId ->
                                val result = importExportManager.exportMCPServer(serverId)
                                result.onSuccess { /* Handle success */ }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.export_selected))
                }
            }
        }
    }
}

@Composable
private fun MCPServerExportItem(
    serverId: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = serverId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Export all data dialog - exports everything at once
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportAllDataDialog(
    onDismiss: () -> Unit,
    onExportComplete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importExportManager = remember { ImportExportManager.getInstance(context) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.import_export_export_all_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Backup,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.import_export_export_all_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // What's included
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.import_export_will_include),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ImportExportItem(stringResource(R.string.import_export_chats))
                        ImportExportItem(stringResource(R.string.import_export_workflows))
                        ImportExportItem(stringResource(R.string.import_export_skills))
                        ImportExportItem(stringResource(R.string.mcp_servers))
                    }
                }

                statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = null
                        val result = importExportManager.exportAllData()
                        result.fold(
                            onSuccess = { file ->
                                statusMessage = context.getString(R.string.import_export_exported_to, file.absolutePath)
                                onExportComplete(file.absolutePath)
                            },
                            onFailure = { e ->
                                statusMessage = context.getString(R.string.import_export_error, e.message)
                            }
                        )
                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_all))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ImportExportItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
