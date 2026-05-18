package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ExecuteShellNode

@Composable
fun ExecuteShellNodeConfigDialog(
    node: ExecuteShellNode? = null,
    onDismiss: () -> Unit,
    onSave: (ExecuteShellNode) -> Unit
) {
    val isCreateMode = node == null
    val defaultNode = node ?: ExecuteShellNode(name = "", description = "")
    var name by remember { mutableStateOf(defaultNode.name) }
    var description by remember { mutableStateOf(defaultNode.description) }
    var command by remember { mutableStateOf(defaultNode.command) }
    var workingDir by remember { mutableStateOf(defaultNode.workingDir ?: "") }
    var timeoutMs by remember { mutableStateOf(defaultNode.timeoutMs.toString()) }
    var captureStderr by remember { mutableStateOf(defaultNode.captureStderr) }
    val currentNode = remember { defaultNode }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (node.name.isEmpty())
                        stringResource(R.string.shell_node_dialog_create_title)
                    else
                        stringResource(R.string.shell_node_dialog_edit_title),
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

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.workflow_shell_command_label)) },
                    placeholder = { Text("echo 'Hello World'") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    label = { Text(stringResource(R.string.workflow_shell_working_dir_label)) },
                    placeholder = { Text("/data/data/com.ai.assistance.operit/files") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeoutMs,
                    onValueChange = { timeoutMs = it },
                    label = { Text(stringResource(R.string.workflow_shell_timeout_label)) },
                    placeholder = { Text("60000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.workflow_shell_capture_stderr_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                    val updatedNode = currentNode.copy(
                        name = name.ifEmpty { "Shell Node" },
                        description = description,
                        command = command,
                        workingDir = workingDir.ifEmpty { "" },
                        timeoutMs = timeoutMs.toLongOrNull() ?: 60000L,
                        captureStderr = captureStderr
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