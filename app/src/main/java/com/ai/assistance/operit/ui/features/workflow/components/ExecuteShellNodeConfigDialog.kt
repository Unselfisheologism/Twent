package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Console
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ExecuteShellNode

/**
 * Execute Shell Node Configuration Dialog
 *
 * Provides a comprehensive interface for configuring shell execution nodes
 * in the workflow builder.
 *
 * Features:
 * - Node name and description
 * - Shell command editor (multi-line)
 * - Working directory configuration
 * - Environment variables (key-value pairs)
 * - Timeout configuration with slider
 * - Output capture options
 * - Stderr merge option
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteShellNodeConfigDialog(
    node: ExecuteShellNode?,
    onDismiss: () -> Unit,
    onConfirm: (ExecuteShellNode) -> Unit
) {
    // Create a new node or use existing
    var currentNode by remember {
        mutableStateOf(node ?: createDefaultExecuteShellNode())
    }

    // UI State - Basic
    var nodeName by remember { mutableStateOf(currentNode.name) }
    var nodeDescription by remember { mutableStateOf(currentNode.description) }

    // Shell Configuration
    var command by remember { mutableStateOf(currentNode.command) }
    var workingDirectory by remember { mutableStateOf(currentNode.workingDir) }
    var timeoutSeconds by remember { mutableStateOf((currentNode.timeoutMs / 1000).toInt().coerceIn(10, 600)) }
    var captureOutput by remember { mutableStateOf(currentNode.captureStderr) }
    var mergeStderr by remember { mutableStateOf(currentNode.captureStderr) }

    // Environment Variables
    var envVars by remember {
        mutableStateOf(currentNode.envVariables.toMutableMap())
    }
    var newEnvKey by remember { mutableStateOf("") }
    var newEnvValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Console,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (node != null) stringResource(R.string.shell_node_dialog_edit_title)
                           else stringResource(R.string.shell_node_dialog_create_title)
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

                // Command Section
                Text(
                    text = stringResource(R.string.shell_node_command_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text(stringResource(R.string.shell_node_command_hint)) },
                    maxLines = 6,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )

                Text(
                    text = stringResource(R.string.shell_node_command_hint_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Working Directory
                OutlinedTextField(
                    value = workingDirectory,
                    onValueChange = { workingDirectory = it },
                    label = { Text(stringResource(R.string.shell_node_working_dir_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.shell_node_working_dir_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Console,
                            contentDescription = null
                        )
                    }
                )

                HorizontalDivider()

                // Environment Variables Section
                Text(
                    text = stringResource(R.string.shell_node_env_vars_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (envVars.isNotEmpty()) {
                    envVars.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = {},
                                modifier = Modifier.weight(1f),
                                readOnly = true,
                                singleLine = true,
                                label = { Text(stringResource(R.string.shell_node_env_key)) }
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    envVars = envVars.toMutableMap().apply { put(key, newValue) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(stringResource(R.string.shell_node_env_value)) }
                            )
                            IconButton(
                                onClick = {
                                    envVars = envVars.toMutableMap().apply { remove(key) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove)
                                )
                            }
                        }
                    }
                }

                // Add new environment variable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newEnvKey,
                        onValueChange = { newEnvKey = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.shell_node_env_key)) },
                        placeholder = { Text("KEY") }
                    )
                    OutlinedTextField(
                        value = newEnvValue,
                        onValueChange = { newEnvValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.shell_node_env_value)) },
                        placeholder = { Text("value") }
                    )
                    IconButton(
                        onClick = {
                            if (newEnvKey.isNotBlank()) {
                                envVars = envVars.toMutableMap().apply {
                                    put(newEnvKey, newEnvValue)
                                }
                                newEnvKey = ""
                                newEnvValue = ""
                            }
                        },
                        enabled = newEnvKey.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add)
                        )
                    }
                }

                HorizontalDivider()

                // Timeout Configuration
                Text(
                    text = stringResource(R.string.shell_node_timeout_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "10s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = timeoutSeconds.toFloat(),
                        onValueChange = { timeoutSeconds = it.toInt() },
                        valueRange = 10f..600f,
                        steps = 58,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "600s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = timeoutSeconds.toString(),
                    onValueChange = {
                        val newTimeout = it.toIntOrNull()
                        if (newTimeout != null) {
                            timeoutSeconds = newTimeout.coerceIn(10, 600)
                        }
                    },
                    label = { Text(stringResource(R.string.shell_node_timeout_seconds)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("seconds") }
                )

                HorizontalDivider()

                // Output Options
                Text(
                    text = stringResource(R.string.shell_node_output_options_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Capture Output Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.shell_node_capture_output_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.shell_node_capture_output_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = captureOutput,
                        onCheckedChange = { captureOutput = it }
                    )
                }

                // Merge Stderr Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.shell_node_merge_stderr_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.shell_node_merge_stderr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mergeStderr,
                        onCheckedChange = { mergeStderr = it },
                        enabled = captureOutput
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
                        command = command,
                        workingDir = workingDirectory,
                        timeoutMs = timeoutSeconds * 1000L,
                        captureStderr = captureOutput,
                        mergeStderr = mergeStderr,
                        envVariables = envVars
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
 * Default ExecuteShellNode factory
 */
private fun createDefaultExecuteShellNode(): ExecuteShellNode {
    return ExecuteShellNode(
        name = "",
        command = "",
        timeoutMs = 30000L,
        captureStderr = true
    )
}