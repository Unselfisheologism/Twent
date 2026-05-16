package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
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
import com.ai.assistance.operit.ui.theme.SteelPrimary

/**
 * Execute Shell Node Configuration Dialog
 * Configures ExecuteShellNode in workflow builder:
 * - Node name and description
 * - Shell command (multi-line)
 * - Working directory
 * - Timeout and stderr capture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteShellNodeConfigDialog(
    node: ExecuteShellNode,
    onDismiss: () -> Unit,
    onSave: (ExecuteShellNode) -> Unit
) {
    val currentNode = remember { node }
    var name by remember { mutableStateOf(currentNode.name) }
    var description by remember { mutableStateOf(currentNode.description) }
    var command by remember { mutableStateOf(currentNode.command) }
    var workingDir by remember { mutableStateOf(currentNode.workingDir) }
    var timeoutSecs by remember { mutableStateOf((currentNode.timeoutMs / 1000).toInt()) }
    var captureStderr by remember { mutableStateOf(currentNode.captureStderr) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = SteelPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.workflow_node_type_execute_shell),
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

                // Command
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.workflow_shell_command_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 10,
                    placeholder = { Text("echo \"Hello World\"\npwd\nls -la") }
                )

                // Working Directory
                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    label = { Text(stringResource(R.string.workflow_shell_working_dir_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/data/data/com.ai.assistance.operit/files") }
                )

                HorizontalDivider()

                // Timeout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.workflow_shell_timeout_label))
                    Text(stringResource(R.string.workflow_shell_timeout_value, timeoutSecs))
                }
                Slider(
                    value = timeoutSecs.toFloat(),
                    onValueChange = { timeoutSecs = it.toInt() },
                    valueRange = 5f..300f,
                    steps = 58
                )

                // Capture Stderr
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.workflow_shell_capture_stderr_label))
                        Text(
                            stringResource(R.string.workflow_shell_capture_stderr_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = captureStderr,
                        onCheckedChange = { captureStderr = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = currentNode.copy(
                        name = name.ifBlank { "Shell Command" },
                        description = description,
                        command = command,
                        workingDir = workingDir,
                        timeoutMs = timeoutSecs.toLong() * 1000,
                        captureStderr = captureStderr
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