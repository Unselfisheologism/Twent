package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AINode
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.ui.theme.SteelPrimary
import com.ai.assistance.operit.ui.theme.SteelAccent

/**
 * AI Node Configuration Dialog
 *
 * Provides a comprehensive interface for configuring AI nodes in the workflow builder.
 *
 * Features:
 * - Node name and description
 * - Model selection (from MultiServiceManager)
 * - System prompt configuration
 * - Initial prompt/message input
 * - Temperature slider (0.0-2.0)
 * - Max tokens configuration
 * - Iteration budget for tool calls
 * - Template prompt with variable support
 * - Tools section (multi-select)
 * - Memory toggle with memory ID
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINodeConfigDialog(
    node: AINode?,
    availableModels: List<ModelOption> = emptyList(),
    availableTools: List<AITool> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (AINode) -> Unit
) {
    // Create a new node or use existing
    var currentNode by remember {
        mutableStateOf(node ?: createDefaultAINode())
    }

    // UI State - Basic
    var nodeName by remember { mutableStateOf(currentNode.name) }
    var nodeDescription by remember { mutableStateOf(currentNode.description) }

    // AI Configuration
    var selectedModelId by remember { mutableStateOf(currentNode.modelId) }
    var systemPrompt by remember { mutableStateOf(currentNode.systemPrompt) }
    var prompt by remember { mutableStateOf(currentNode.prompt) }
    var temperature by remember { mutableStateOf(if (currentNode.temperature > 0) currentNode.temperature else 0.7f) }
    var maxTokens by remember { mutableStateOf(if (currentNode.maxTokens > 0) currentNode.maxTokens else 4096) }
    var iterationBudget by remember { mutableStateOf(if (currentNode.iterationBudget > 0) currentNode.iterationBudget else 10) }
    var templatePrompt by remember { mutableStateOf(currentNode.templatePrompt ?: "") }

    // Tools & Memory
    var enableTools by remember { mutableStateOf(currentNode.enableTools) }
    var selectedTools by remember { mutableStateOf(currentNode.enabledTools.toMutableList()) }
    var useMemory by remember { mutableStateOf(currentNode.useMemory) }
    var memoryId by remember { mutableStateOf(currentNode.memoryId ?: "") }

    // Task type
    var taskType by remember { mutableStateOf(currentNode.taskType) }

    // Dropdown states
    var modelExpanded by remember { mutableStateOf(false) }
    var taskTypeExpanded by remember { mutableStateOf(false) }
    var toolsExpanded by remember { mutableStateOf(false) }

    // Available model options
    val modelOptions = remember { availableModels.ifEmpty { getDefaultModelOptions() } }
    val taskTypes = listOf(
        "generate_text" to R.string.ai_node_task_type_text,
        "reasoning" to R.string.ai_node_task_type_reasoning,
        "classify" to R.string.ai_node_task_type_classify,
        "embed" to R.string.ai_node_task_type_embed
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = SteelPrimary
                )
                Text(
                    text = if (node != null) stringResource(R.string.ai_node_dialog_edit_title)
                           else stringResource(R.string.ai_node_dialog_create_title)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Node name
                OutlinedTextField(
                    value = nodeName,
                    onValueChange = { nodeName = it },
                    label = { Text(stringResource(R.string.workflow_node_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Node description
                OutlinedTextField(
                    value = nodeDescription,
                    onValueChange = { nodeDescription = it },
                    label = { Text(stringResource(R.string.workflow_node_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                HorizontalDivider()

                // Task Type Selector
                Text(
                    text = stringResource(R.string.ai_node_task_type_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                ExposedDropdownMenuBox(
                    expanded = taskTypeExpanded,
                    onExpandedChange = { taskTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(taskTypes.find { it.first == taskType }?.second ?: R.string.ai_node_task_type_text),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_node_task_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = taskTypeExpanded,
                        onDismissRequest = { taskTypeExpanded = false }
                    ) {
                        taskTypes.forEach { (type, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    taskType = type
                                    taskTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Model Selection
                Text(
                    text = stringResource(R.string.ai_node_model_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = modelOptions.find { it.id == selectedModelId }?.displayName
                                ?: selectedModelId.ifEmpty { stringResource(R.string.ai_node_select_model) }
                                ?: stringResource(R.string.ai_node_select_model),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_node_model_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        if (modelOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_node_no_models)) },
                                onClick = { modelExpanded = false },
                                enabled = false
                            )
                        } else {
                            modelOptions.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName) },
                                    onClick = {
                                        selectedModelId = model.id
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // System Prompt
                Text(
                    text = stringResource(R.string.ai_node_system_prompt_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text(stringResource(R.string.ai_node_system_prompt_hint)) },
                    maxLines = 5
                )

                // Initial Prompt / Message
                Text(
                    text = stringResource(R.string.ai_node_initial_prompt_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    placeholder = { Text(stringResource(R.string.ai_node_initial_prompt_hint)) },
                    maxLines = 4
                )

                // Template Prompt (with variable support)
                Text(
                    text = stringResource(R.string.ai_node_template_prompt_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = templatePrompt,
                    onValueChange = { templatePrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    placeholder = { Text(stringResource(R.string.ai_node_template_prompt_hint)) },
                    maxLines = 4
                )

                Text(
                    text = stringResource(R.string.ai_node_template_prompt_hint_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Temperature Slider
                Text(
                    text = stringResource(R.string.ai_node_temperature_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = SteelPrimary,
                            activeTrackColor = SteelPrimary
                        )
                    )
                    Text(
                        text = "2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.ai_node_temperature_value, temperature),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = SteelPrimary
                )

                // Max Tokens
                OutlinedTextField(
                    value = maxTokens.toString(),
                    onValueChange = {
                        maxTokens = it.toIntOrNull() ?: maxTokens
                    },
                    label = { Text(stringResource(R.string.ai_node_max_tokens_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("tokens") }
                )

                // Iteration Budget
                OutlinedTextField(
                    value = iterationBudget.toString(),
                    onValueChange = {
                        iterationBudget = it.toIntOrNull() ?: iterationBudget
                    },
                    label = { Text(stringResource(R.string.ai_node_iteration_budget_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.ai_node_iteration_budget_hint)) }
                )

                HorizontalDivider()

                // Tools Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ai_node_enable_tools_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Switch(
                        checked = enableTools,
                        onCheckedChange = { enableTools = it }
                    )
                }

                if (enableTools) {
                    Text(
                        text = stringResource(R.string.ai_node_tools_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    ExposedDropdownMenuBox(
                        expanded = toolsExpanded,
                        onExpandedChange = { toolsExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedTools.isEmpty()) stringResource(R.string.ai_node_select_tools)
                                    else stringResource(R.string.ai_node_selected_tools_count, selectedTools.size),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ai_node_tools_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toolsExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = toolsExpanded,
                            onDismissRequest = { toolsExpanded = false }
                        ) {
                            if (availableTools.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ai_node_no_tools)) },
                                    onClick = { toolsExpanded = false },
                                    enabled = false
                                )
                            } else {
                                availableTools.forEach { tool ->
                                    val isSelected = selectedTools.contains(tool.name)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = null
                                                )
                                                Column {
                                                    Text(tool.name)
                                                    if (tool.description.isNotEmpty()) {
                                                        Text(
                                                            text = tool.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedTools = if (isSelected) {
                                                selectedTools.toMutableList().apply { remove(tool.name) }
                                            } else {
                                                selectedTools.toMutableList().apply { add(tool.name) }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTools.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTools.forEach { toolName ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        selectedTools = selectedTools.toMutableList().apply { remove(toolName) }
                                    },
                                    label = { Text(toolName) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Memory Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ai_node_use_memory_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Switch(
                        checked = useMemory,
                        onCheckedChange = { useMemory = it }
                    )
                }

                if (useMemory) {
                    OutlinedTextField(
                        value = memoryId,
                        onValueChange = { memoryId = it },
                        label = { Text(stringResource(R.string.ai_node_memory_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ai_node_memory_id_hint)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedNode = currentNode.copy(
                        name = nodeName,
                        description = nodeDescription,
                        taskType = taskType,
                        modelId = selectedModelId,
                        systemPrompt = systemPrompt,
                        prompt = prompt,
                        templatePrompt = templatePrompt.ifEmpty { null },
                        temperature = temperature,
                        maxTokens = maxTokens,
                        iterationBudget = iterationBudget,
                        enableTools = enableTools,
                        enabledTools = selectedTools,
                        useMemory = useMemory,
                        memoryId = memoryId.ifEmpty { null }
                    )
                    onConfirm(updatedNode)
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

/**
 * Default AI node factory
 */
private fun createDefaultAINode(): AINode {
    return AINode(
        name = "",
        taskType = "generate_text",
        temperature = 0.7f,
        maxTokens = 4096,
        iterationBudget = 10
    )
}

/**
 * Model option for dropdown display
 */
data class ModelOption(
    val id: String,
    val displayName: String,
    val provider: String = ""
)

/**
 * Get default model options (fallback when no models available)
 */
private fun getDefaultModelOptions(): List<ModelOption> {
    return listOf(
        ModelOption("default", "Default Model"),
        ModelOption("gpt-4", "GPT-4", "OpenAI"),
        ModelOption("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI"),
        ModelOption("claude-3", "Claude 3", "Anthropic")
    )
}