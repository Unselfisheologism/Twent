package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.ui.theme.SteelPrimary
import com.ai.assistance.operit.ui.theme.SteelAccent

/**
 * AI Node Configuration Dialog
 * Configures AINode in workflow builder:
 * - Node name and description
 * - Task type selection
 * - Model selection
 * - System prompt and user prompt
 * - Tools, temperature, max tokens, timeout
 */
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
    var modelId by remember { mutableStateOf(currentNode.modelId) }
    var systemPrompt by remember { mutableStateOf(currentNode.systemPrompt) }
    var prompt by remember { mutableStateOf(currentNode.prompt) }
    var enableTools by remember { mutableStateOf(currentNode.enableTools) }
    var maxTokens by remember { mutableStateOf(currentNode.maxTokens) }
    var temperature by remember { mutableStateOf(currentNode.temperature) }
    var timeoutMs by remember { mutableStateOf(currentNode.timeoutMs) }

    val taskTypes = listOf(
        "generate_text" to stringResource(R.string.workflow_ai_task_type_generate_text),
        "analyze_image" to stringResource(R.string.workflow_ai_task_type_analyze_image),
        "classify" to stringResource(R.string.workflow_ai_task_type_classify),
        "reasoning" to stringResource(R.string.workflow_ai_task_type_reasoning)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = SteelPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.workflow_node_type_ai),
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workflow_node_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.workflow_node_desc_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                HorizontalDivider()

                // Task Type
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = taskTypes.find { it.first == taskType }?.second ?: taskType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workflow_ai_task_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        taskTypes.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    taskType = key
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Model ID
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text(stringResource(R.string.workflow_ai_model_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.workflow_ai_model_placeholder)) }
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.workflow_ai_prompt_section),
                    fontWeight = FontWeight.SemiBold,
                    color = SteelPrimary
                )

                // System Prompt
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.workflow_ai_system_prompt_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 5
                )

                // User Prompt
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.workflow_ai_user_prompt_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    maxLines = 8,
                    placeholder = { Text(stringResource(R.string.workflow_ai_user_prompt_hint)) }
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.workflow_ai_params_section),
                    fontWeight = FontWeight.SemiBold,
                    color = SteelPrimary
                )

                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.workflow_ai_temperature_label))
                    Text(stringResource(R.string.workflow_ai_temperature_value, temperature))
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 19
                )

                // Max Tokens
                OutlinedTextField(
                    value = maxTokens.toString(),
                    onValueChange = { maxTokens = it.toIntOrNull() ?: 4096 },
                    label = { Text(stringResource(R.string.workflow_ai_max_tokens_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Timeout
                OutlinedTextField(
                    value = (timeoutMs / 1000).toString(),
                    onValueChange = { timeoutMs = (it.toLongOrNull() ?: 60) * 1000 },
                    label = { Text(stringResource(R.string.workflow_ai_timeout_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("s") }
                )

                // Enable Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.workflow_ai_enable_tools_label))
                        Text(
                            stringResource(R.string.workflow_ai_enable_tools_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableTools,
                        onCheckedChange = { enableTools = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = currentNode.copy(
                        name = name.ifBlank { "AI Node" },
                        description = description,
                        taskType = taskType,
                        modelId = modelId,
                        systemPrompt = systemPrompt,
                        prompt = prompt,
                        enableTools = enableTools,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        timeoutMs = timeoutMs
                    )
                    onSave(updated)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}