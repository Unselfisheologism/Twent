package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.data.model.AITaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINodeConfigDialog(
    node: AINode,
    onDismiss: () -> Unit,
    onSave: (AINode) -> Unit
) {
    val currentNode = remember { node }
    var name by remember { mutableStateOf(currentNode.name) }
    var description by remember { mutableStateOf(currentNode.description) }
    var taskType by remember { mutableStateOf(currentNode.taskType) }
    var userPrompt by remember { mutableStateOf(currentNode.userPrompt) }
    var systemPrompt by remember { mutableStateOf(currentNode.systemPrompt ?: "") }
    var temperature by remember { mutableStateOf(currentNode.temperature ?: 0.7f) }
    var maxTokens by remember { mutableStateOf(currentNode.maxTokens ?: 4096) }
    var timeoutSecs by remember { mutableStateOf(currentNode.timeoutSecs ?: 120) }
    var enableTools by remember { mutableStateOf(currentNode.enableTools) }
    var selectedToolNames by remember { mutableStateOf(currentNode.enabledToolNames.toMutableList()) }
    var taskTypeExpanded by remember { mutableStateOf(false) }

    var showSystemPromptSection by remember { mutableStateOf(false) }
    var showAdvancedSection by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (node.name.isEmpty()) stringResource(R.string.ai_node_dialog_create_title)
                           else stringResource(R.string.ai_node_dialog_edit_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workflow_node_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.workflow_node_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Task Type
                ExposedDropdownMenuBox(
                    expanded = taskTypeExpanded,
                    onExpandedChange = { taskTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = taskType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workflow_ai_task_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskTypeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = taskTypeExpanded,
                        onDismissRequest = { taskTypeExpanded = false }
                    ) {
                        AITaskType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    taskType = type
                                    taskTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // User Prompt
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    label = { Text(stringResource(R.string.workflow_ai_user_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.workflow_ai_user_prompt_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                // Toggle: Show System Prompt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workflow_ai_system_prompt_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = showSystemPromptSection,
                        onCheckedChange = { showSystemPromptSection = it }
                    )
                }

                if (showSystemPromptSection) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text(stringResource(R.string.workflow_ai_system_prompt_label)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Toggle: Show Advanced
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workflow_ai_params_section),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = showAdvancedSection,
                        onCheckedChange = { showAdvancedSection = it }
                    )
                }

                if (showAdvancedSection) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Temperature
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_ai_temperature_label) + ": ${String.format("%.1f", temperature)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Max Tokens
                        OutlinedTextField(
                            value = maxTokens.toString(),
                            onValueChange = { value -> maxTokens = value.toIntOrNull() ?: 4096 },
                            label = { Text(stringResource(R.string.workflow_ai_max_tokens_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Timeout
                        OutlinedTextField(
                            value = timeoutSecs.toString(),
                            onValueChange = { value -> timeoutSecs = value.toIntOrNull() ?: 120 },
                            label = { Text(stringResource(R.string.workflow_ai_timeout_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Enable Tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_ai_enable_tools_label),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = enableTools,
                                onCheckedChange = { enableTools = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedNode = currentNode.copy(
                        name = name.ifEmpty { "AI Node" },
                        description = description,
                        taskType = taskType,
                        userPrompt = userPrompt,
                        systemPrompt = systemPrompt.ifEmpty { null },
                        temperature = temperature,
                        maxTokens = maxTokens,
                        timeoutSecs = timeoutSecs,
                        enableTools = enableTools,
                        enabledToolNames = selectedToolNames
                    )
                    onSave(updatedNode)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}