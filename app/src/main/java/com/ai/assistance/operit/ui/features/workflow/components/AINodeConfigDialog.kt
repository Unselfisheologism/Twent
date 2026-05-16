package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.model.ToolkitDefinition
import com.ai.assistance.operit.data.model.AINode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINodeConfigDialog(
    node: AINode? = null,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onSave: (AINode) -> Unit
) {
    val currentNode = remember { node ?: AINode() }
    var name by remember { mutableStateOf(initialName.ifEmpty { currentNode.name }) }
    var description by remember { mutableStateOf(initialDescription.ifEmpty { currentNode.description }) }
    var taskType by remember { mutableStateOf(currentNode.taskType) }
    var prompt by remember { mutableStateOf(currentNode.prompt) }
    var systemPrompt by remember { mutableStateOf(currentNode.systemPrompt) }
    var temperature by remember { mutableFloatStateOf(currentNode.temperature) }
    var maxTokens by remember { mutableIntStateOf(currentNode.maxTokens) }
    var timeoutMs by remember { mutableLongStateOf(currentNode.timeoutMs) }
    var enableTools by remember { mutableStateOf(currentNode.enableTools) }
    var selectedTools by remember { mutableStateOf(currentNode.enabledTools.toMutableList()) }
    var taskTypeExpanded by remember { mutableStateOf(false) }

    var showSystemPromptSection by remember { mutableStateOf(currentNode.systemPrompt.isNotEmpty()) }
    var showAdvancedSection by remember { mutableStateOf(false) }
    var showInputFilesSection by remember { mutableStateOf(currentNode.inputFiles.isNotEmpty()) }
    var inputFiles by remember { mutableStateOf(currentNode.inputFiles.toMutableList()) }

    // Load toolkits for tool selection
    val context = LocalContext.current
    val composioApi = remember { ComposioApiService.getInstance(context) }
    var availableToolkits by remember { mutableStateOf<List<ToolkitDefinition>>(emptyList()) }
    var toolkitLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enableTools) {
        if (enableTools) {
            composioApi.listToolkits(limit = 50).fold(
                onSuccess = { availableToolkits = it },
                onFailure = { toolkitLoadError = it.message }
            )
        }
    }

    val taskTypeOptions = listOf(
        "generate_text" to "Text Generation",
        "analyze_image" to "Image Analysis",
        "classify" to "Classification",
        "embed" to "Embedding",
        "reasoning" to "Reasoning"
    )

    val taskTypeDisplayName = taskTypeOptions.find { it.first == taskType }?.second ?: "Text Generation"

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
                    text = if (currentNode.name.isEmpty()) stringResource(R.string.ai_node_dialog_create_title)
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workflow_node_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.workflow_node_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = taskTypeExpanded,
                    onExpandedChange = { taskTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = taskTypeDisplayName,
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
                        taskTypeOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    taskType = value
                                    taskTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workflow_ai_input_files_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = showInputFilesSection,
                        onCheckedChange = {
                            showInputFilesSection = it
                            if (it && inputFiles.isEmpty()) {
                                inputFiles.add("")
                            }
                        }
                    )
                }

                if (showInputFilesSection) {
                    val fileLabel = when (taskType) {
                        "analyze_image" -> stringResource(R.string.workflow_ai_image_file_label)
                        "embed" -> stringResource(R.string.workflow_ai_document_file_label)
                        else -> stringResource(R.string.workflow_ai_input_file_label)
                    }
                    if (taskType == "analyze_image") {
                        Text(
                            text = stringResource(R.string.workflow_ai_supported_formats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    inputFiles.forEachIndexed { index, filePath ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = filePath,
                                onValueChange = { inputFiles[index] = it },
                                label = { Text("$fileLabel ${index + 1}") },
                                placeholder = { Text(stringResource(R.string.workflow_ai_input_file_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (inputFiles.size > 1) {
                                        inputFiles.removeAt(index)
                                    } else {
                                        inputFiles[index] = ""
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { inputFiles.add("") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.workflow_ai_add_file))
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.workflow_ai_user_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.workflow_ai_user_prompt_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

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
                        Text(
                            text = stringResource(R.string.workflow_ai_temperature_label) + ": ${String.format("%.1f", temperature)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxTokens.toString(),
                            onValueChange = { value -> maxTokens = value.toIntOrNull() ?: 4096 },
                            label = { Text(stringResource(R.string.workflow_ai_max_tokens_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = timeoutMs.toString(),
                            onValueChange = { value -> timeoutMs = value.toLongOrNull() ?: 60000L },
                            label = { Text(stringResource(R.string.workflow_ai_timeout_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_ai_enable_tools_label),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = enableTools,
                                onCheckedChange = { enableTools = it }
                            )
                        }

                        if (enableTools) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.workflow_ai_select_tools_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (availableToolkits.isEmpty() && toolkitLoadError == null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.workflow_ai_loading_toolkits), style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (toolkitLoadError != null) {
                                    Text(
                                        text = "Error: $toolkitLoadError",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    availableToolkits.forEach { toolkit ->
                                        val toolkitTools = toolkit.tools
                                        if (toolkitTools.isNotEmpty()) {
                                            var expanded by remember { mutableStateOf(false) }
                                            val selectedCount = selectedTools.count { it.startsWith("${toolkit.name}:") }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .toggleable(
                                                        value = selectedCount == toolkitTools.size,
                                                        onValueChange = { checked ->
                                                            if (checked) {
                                                                selectedTools.addAll(toolkitTools.map { "${toolkit.name}:${it.name}" })
                                                            } else {
                                                                selectedTools.removeAll { it.startsWith("${toolkit.name}:") }
                                                            }
                                                        },
                                                        role = Role.Checkbox
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(checked = selectedCount == toolkitTools.size, onCheckedChange = null)
                                                Text(toolkit.displayName.ifBlank { toolkit.name }, style = MaterialTheme.typography.bodySmall)
                                                Spacer(Modifier.weight(1f))
                                                TextButton(onClick = { expanded = !expanded }) {
                                                    Text(
                                                        if (selectedCount == toolkitTools.size) stringResource(R.string.deselect_all)
                                                        else stringResource(R.string.select_all),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                            if (expanded) {
                                                Column(modifier = Modifier.padding(start = 32.dp)) {
                                                    toolkitTools.forEach { tool ->
                                                        val toolId = "${toolkit.name}:${tool.name}"
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .toggleable(
                                                                    value = selectedTools.contains(toolId),
                                                                    onValueChange = { checked ->
                                                                        if (checked) selectedTools.add(toolId)
                                                                        else selectedTools.remove(toolId)
                                                                    },
                                                                    role = Role.Checkbox
                                                                ),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = selectedTools.contains(toolId),
                                                                onCheckedChange = null
                                                            )
                                                            Column {
                                                                Text(tool.name, style = MaterialTheme.typography.bodySmall)
                                                                if (tool.description.isNotEmpty()) {
                                                                    Text(
                                                                        tool.description.take(80),
                                                                        style = MaterialTheme.typography.labelSmall,
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
                                    if (availableToolkits.isEmpty()) {
                                        Text(
                                            stringResource(R.string.workflow_ai_no_toolkits),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
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
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        timeoutMs = timeoutMs,
                        enableTools = enableTools,
                        enabledTools = selectedTools,
                        inputFiles = inputFiles.filter { it.isNotBlank() }
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