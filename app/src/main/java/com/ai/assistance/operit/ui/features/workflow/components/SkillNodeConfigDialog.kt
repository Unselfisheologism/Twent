package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.SkillNode
import com.ai.assistance.operit.ui.theme.SteelPrimary

/**
 * Skill Node Configuration Dialog
 * Configures SkillNode in workflow builder:
 * - Node name and description
 * - Skill name selection (multi-select)
 * - Extra instructions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillNodeConfigDialog(
    node: SkillNode,
    onDismiss: () -> Unit,
    onSave: (SkillNode) -> Unit
) {
    val currentNode = remember { node }
    var name by remember { mutableStateOf(currentNode.name) }
    var description by remember { mutableStateOf(currentNode.description) }
    var skillNames by remember { mutableStateOf(currentNode.skillNames.toMutableList()) }
    var extraInstructions by remember { mutableStateOf(currentNode.extraInstructions) }

    // Mock skill list — replace with actual TwSkillsManager/skillsRepository
    val availableSkills = listOf(
        "android-development", "android-ai-agent-cli-integration", "android-composio-integration",
        "android-tool-registration", "android-mcp-registry-integration", "android-robust-system-prompts",
        "android-permission-navigation", "cloudflare-pages-deploy", "remotion-best-practices",
        "huggingface-hub", "llama-cpp", "whisper"
    )

    val selectedSkills = availableSkills.filter { it in skillNames }

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
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = SteelPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.workflow_node_type_skill),
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

                // Selected Skills
                Text(
                    text = stringResource(R.string.workflow_skill_selected_label, selectedSkills.size),
                    fontWeight = FontWeight.SemiBold,
                    color = SteelPrimary
                )

                if (selectedSkills.isEmpty()) {
                    Text(
                        stringResource(R.string.workflow_skill_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    selectedSkills.forEach { skill ->
                        AssistChip(
                            onClick = { skillNames = skillNames.filter { it != skill }.toMutableList() },
                            label = { Text(skill, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.dialog_close),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Available Skills
                Text(
                    text = stringResource(R.string.workflow_skill_available_label),
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableSkills.filter { it !in skillNames }) { skill ->
                        ListItem(
                            headlineContent = { Text(skill, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable {
                                skillNames = (skillNames + skill).toMutableList()
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                HorizontalDivider()

                // Extra Instructions
                OutlinedTextField(
                    value = extraInstructions,
                    onValueChange = { extraInstructions = it },
                    label = { Text(stringResource(R.string.workflow_skill_extra_instructions_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 5,
                    placeholder = { Text(stringResource(R.string.workflow_skill_extra_instructions_hint)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = currentNode.copy(
                        name = name.ifBlank { "Skill Node" },
                        description = description,
                        skillNames = skillNames.toList(),
                        extraInstructions = extraInstructions
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