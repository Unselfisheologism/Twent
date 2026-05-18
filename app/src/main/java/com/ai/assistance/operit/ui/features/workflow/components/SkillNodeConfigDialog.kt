package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.SkillNode
import com.ai.assistance.operit.data.skill.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillNodeConfigDialog(
    node: SkillNode,
    onDismiss: () -> Unit,
    onSave: (SkillNode) -> Unit
) {
    val context = LocalContext.current

    val currentNode = remember { node }
    var name by remember { mutableStateOf(currentNode.name) }
    var description by remember { mutableStateOf(currentNode.description) }
    var skillNames by remember { mutableStateOf(currentNode.skillNames.toMutableList()) }
    var extraInstructions by remember { mutableStateOf(currentNode.extraInstructions) }
    var skillDropdownExpanded by remember { mutableStateOf(false) }

    // Real available skills loaded from SkillRepository
    var availableSkillNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSkills by remember { mutableStateOf(false) }
    var skillsLoadError by remember { mutableStateOf<String?>(null) }

    // Load available skills from the plugins system using LaunchedEffect
    LaunchedEffect(Unit) {
        isLoadingSkills = true
        skillsLoadError = null
        try {
            val skillRepo = SkillRepository.getInstance(context)
            val skills = withContext(Dispatchers.IO) {
                skillRepo.getAvailableSkillPackages()
            }
            availableSkillNames = skills.keys.toList().sorted()
        } catch (e: Exception) {
            skillsLoadError = e.message
            availableSkillNames = emptyList()
        } finally {
            isLoadingSkills = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (node.name.isEmpty()) stringResource(R.string.skill_node_dialog_create_title)
                           else stringResource(R.string.skill_node_dialog_edit_title),
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

                // Selected skills shown as chips
                if (skillNames.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        skillNames.forEach { skillName ->
                            AssistChip(
                                onClick = { skillNames.remove(skillName) },
                                label = { Text(skillName) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Dropdown for selecting skills
                ExposedDropdownMenuBox(
                    expanded = skillDropdownExpanded,
                    onExpandedChange = { skillDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workflow_skill_select_skill)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skillDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = skillDropdownExpanded,
                        onDismissRequest = { skillDropdownExpanded = false }
                    ) {
                        // Loading state
                        if (isLoadingSkills) {
                            DropdownMenuItem(
                                text = {
                                    Row {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Loading...")
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                        } else if (skillsLoadError != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Error: $skillsLoadError",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                        } else if (availableSkillNames.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_skills_available)) },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            availableSkillNames.forEach { skillName ->
                                val isSelected = skillName in skillNames
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(skillName)
                                            if (isSelected) {
                                                Spacer(Modifier.weight(1f))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (!isSelected) skillNames.add(skillName)
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = extraInstructions,
                    onValueChange = { extraInstructions = it },
                    label = { Text(stringResource(R.string.workflow_skill_extra_instructions_label)) },
                    placeholder = { Text(stringResource(R.string.workflow_skill_extra_instructions_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedNode = currentNode.copy(
                        name = name.ifEmpty { "Skill Node" },
                        description = description,
                        skillNames = skillNames.toList(),
                        extraInstructions = extraInstructions
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