package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
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

/**
 * Skill Node Configuration Dialog
 *
 * Provides a comprehensive interface for configuring skill nodes in the workflow builder.
 *
 * Features:
 * - Node name and description
 * - Skill selection dropdown (from TwSkillsManager)
 * - Skill path manual entry
 * - Inject as system prompt toggle
 * - Prepend to conversation toggle
 * - Skill content preview (read-only)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillNodeConfigDialog(
    node: SkillNode?,
    availableSkills: List<SkillInfo> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (SkillNode) -> Unit
) {
    // Create a new node or use existing
    var currentNode by remember {
        mutableStateOf(node ?: createDefaultSkillNode())
    }

    // UI State - Basic
    var nodeName by remember { mutableStateOf(currentNode.name) }
    var nodeDescription by remember { mutableStateOf(currentNode.description) }

    // Skill Configuration
    var selectedSkillPath by remember { mutableStateOf(currentNode.skillPath) }
    var skillTitle by remember { mutableStateOf(currentNode.skillTitle ?: "") }
    var injectAsSystemPrompt by remember { mutableStateOf(currentNode.injectAsSystemPrompt) }
    var prependToConversation by remember { mutableStateOf(currentNode.prependToConversation) }

    // Skill content preview
    var skillContentPreview by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }

    // Dropdown state
    var skillExpanded by remember { mutableStateOf(false) }

    // Available skills options
    val skillOptions = remember { availableSkills.ifEmpty { getDefaultSkillOptions() } }

    // Find selected skill info
    val selectedSkillInfo = remember(selectedSkillPath, skillOptions) {
        skillOptions.find { it.path == selectedSkillPath || it.name == selectedSkillPath }
    }

    // Update preview when skill changes
    LaunchedEffect(selectedSkillPath) {
        if (selectedSkillPath.isNotEmpty()) {
            // Try to load skill content preview
            skillContentPreview = loadSkillPreview(selectedSkillPath)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color(0xFFFF5722)
                )
                Text(
                    text = if (node != null) stringResource(R.string.skill_node_dialog_edit_title)
                           else stringResource(R.string.skill_node_dialog_create_title)
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

                // Skill Selection
                Text(
                    text = stringResource(R.string.skill_node_select_skill_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                ExposedDropdownMenuBox(
                    expanded = skillExpanded,
                    onExpandedChange = { skillExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSkillInfo?.displayName
                                ?: selectedSkillPath.ifEmpty { stringResource(R.string.skill_node_select_skill_placeholder) }
                                ?: stringResource(R.string.skill_node_select_skill_placeholder),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.skill_node_skill_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skillExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = skillExpanded,
                        onDismissRequest = { skillExpanded = false }
                    ) {
                        if (skillOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.skill_node_no_skills)) },
                                onClick = { skillExpanded = false },
                                enabled = false
                            )
                        } else {
                            skillOptions.forEach { skill ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = skill.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (skill.description.isNotEmpty()) {
                                                Text(
                                                    text = skill.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = skill.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSkillPath = skill.path
                                        skillTitle = skill.displayName
                                        skillExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Skill Path Manual Entry
                OutlinedTextField(
                    value = selectedSkillPath,
                    onValueChange = { selectedSkillPath = it },
                    label = { Text(stringResource(R.string.skill_node_path_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.skill_node_path_hint)) },
                    supportingText = { Text(stringResource(R.string.skill_node_path_supporting_text)) }
                )

                HorizontalDivider()

                // Injection Options
                Text(
                    text = stringResource(R.string.skill_node_injection_options_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Inject as System Prompt Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.skill_node_inject_as_system_prompt_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.skill_node_inject_as_system_prompt_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = injectAsSystemPrompt,
                        onCheckedChange = { injectAsSystemPrompt = it }
                    )
                }

                // Prepend to Conversation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.skill_node_prepend_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.skill_node_prepend_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prependToConversation,
                        onCheckedChange = { prependToConversation = it }
                    )
                }

                HorizontalDivider()

                // Skill Content Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.skill_node_preview_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { showPreview = !showPreview }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showPreview) stringResource(R.string.hide)
                                   else stringResource(R.string.show)
                        )
                    }
                }

                if (showPreview) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (skillContentPreview.isNotEmpty()) {
                                Text(
                                    text = skillContentPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 20,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.skill_node_preview_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selected Skill Summary
                if (selectedSkillInfo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = selectedSkillInfo.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedSkillInfo.description.isNotEmpty()) {
                                Text(
                                    text = selectedSkillInfo.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Category: ${selectedSkillInfo.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Path: ${selectedSkillInfo.path}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        name = nodeName,
                        description = nodeDescription,
                        skillPath = selectedSkillPath,
                        skillTitle = skillTitle.ifEmpty { null },
                        injectAsSystemPrompt = injectAsSystemPrompt,
                        prependToConversation = prependToConversation
                    )
                    onConfirm(updatedNode)
                },
                enabled = selectedSkillPath.isNotEmpty()
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
 * Default SkillNode factory
 */
private fun createDefaultSkillNode(): SkillNode {
    return SkillNode(
        name = "",
        injectAsSystemPrompt = true,
        prependToConversation = true
    )
}

/**
 * Skill info for dropdown display
 */
data class SkillInfo(
    val name: String,
    val path: String,
    val displayName: String,
    val description: String,
    val category: String
)

/**
 * Get default skill options (fallback when no skills available)
 */
private fun getDefaultSkillOptions(): List<SkillInfo> {
    return listOf(
        SkillInfo(
            name = "android-development",
            path = "skills/android-development/SKILL.md",
            displayName = "Android Development",
            description = "Guidelines for Android app development with Jetpack Compose",
            category = "development"
        ),
        SkillInfo(
            name = "react-development",
            path = "skills/react-development/SKILL.md",
            displayName = "React Development",
            description = "React and React Native best practices",
            category = "development"
        ),
        SkillInfo(
            name = "devops",
            path = "skills/devops/SKILL.md",
            displayName = "DevOps",
            description = "CI/CD and deployment workflows",
            category = "operations"
        )
    )
}

/**
 * Load skill content preview (placeholder implementation)
 */
private fun loadSkillPreview(skillPath: String): String {
    // In a real implementation, this would read the skill file content
    return "Skill content preview for: $skillPath\n\n" +
           "This is a placeholder. In production, this would load the actual\n" +
           "SKILL.md content and display the first portion of it."
}