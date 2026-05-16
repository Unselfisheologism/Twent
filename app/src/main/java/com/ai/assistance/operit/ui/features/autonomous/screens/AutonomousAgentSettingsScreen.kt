package com.ai.assistance.operit.ui.features.autonomous.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.api.chat.autonomous.TwAutonomousAgent
import com.ai.assistance.operit.data.preferences.AutonomousAgentPreferences
import com.ai.assistance.operit.ui.components.TwentCard
import com.ai.assistance.operit.ui.components.TwentHeading
import com.ai.assistance.operit.ui.components.TwentScreenPadding
import com.ai.assistance.operit.ui.components.TwentSectionTitle
import com.ai.assistance.operit.ui.theme.OrangePrimary
import com.ai.assistance.operit.ui.theme.CyanPrimary
import com.ai.assistance.operit.ui.twent.components.TwentSpacing
import kotlinx.coroutines.launch

@Composable
fun AutonomousAgentSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Preferences for settings
    val preferences = remember { AutonomousAgentPreferences(context) }

    // TwAutonomousAgent instance for status and tasks
    val agent = remember { TwAutonomousAgent.getInstance(context) }

    // Collect agent state flows
    val agentMode by agent.mode.collectAsState()
    val isAgentEnabled by agent.isEnabled.collectAsState()
    val pendingTasks by agent.pendingTasks.collectAsState()

    // Local state from preferences
    var enabled by remember { mutableStateOf(preferences.enabled) }
    var morningHour by remember { mutableStateOf(preferences.morningHour.toString()) }
    var morningMinute by remember { mutableStateOf("0") }
    var eveningHour by remember { mutableStateOf(preferences.eveningHour.toString()) }
    var eveningMinute by remember { mutableStateOf("0") }
    var observeInterval by remember { mutableStateOf(preferences.observeIntervalMinutes.toString()) }

    // Content generation toggles
    var generateSocial by remember { mutableStateOf(preferences.generateSocialPosts) }
    var generateNews by remember { mutableStateOf(preferences.generateNewsDigest) }
    var generateFollowups by remember { mutableStateOf(preferences.generateFollowups) }
    var generateAutomation by remember { mutableStateOf(preferences.generateAutomationIdeas) }

    // Observation source toggles
    var observeAppUsage by remember { mutableStateOf(preferences.observeAppUsage) }
    var observeWorkflows by remember { mutableStateOf(preferences.observeWorkflows) }
    var observeOverlay by remember { mutableStateOf(preferences.observeOverlay) }

    // Toast feedback
    var showSavedFeedback by remember { mutableStateOf(false) }

    fun savePreference() {
        preferences.enabled = enabled
        if (enabled) {
            agent.start()
        } else {
            agent.stop()
        }
        showSavedFeedback = true
    }

    LaunchedEffect(showSavedFeedback) {
        if (showSavedFeedback) {
            kotlinx.coroutines.delay(2000)
            showSavedFeedback = false
        }
    }

    TwentScreenPadding {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    TwentHeading(
                        text = "Autonomous Agent",
                        fontSize = 32.dp
                    )
                    Text(
                        text = "Your AI teammate works while you rest",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // Enable Toggle - Large prominent switch
            TwentCard(
                modifier = Modifier.fillMaxWidth(),
                gradientBorder = enabled
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Autonomous Agent",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (enabled) "Agent is running in the background" else "Agent is paused",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                savePreference()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OrangePrimary,
                                checkedTrackColor = OrangePrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Schedule Section (visible when enabled)
            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    TwentSectionTitle("Schedule")
                    Spacer(modifier = Modifier.height(TwentSpacing.md))

                    TwentCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Morning time
                            TimePickerRow(
                                label = "Morning generation",
                                hourValue = morningHour,
                                minuteValue = morningMinute,
                                onHourChange = { morningHour = it },
                                onMinuteChange = { morningMinute = it },
                                onSave = {
                                    preferences.morningHour = morningHour.toIntOrNull() ?: 9
                                    preferences.eveningHour = eveningHour.toIntOrNull() ?: 20
                                    preferences.observeIntervalMinutes = observeInterval.toIntOrNull() ?: 120
                                    showSavedFeedback = true
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            // Evening time
                            TimePickerRow(
                                label = "Evening learning",
                                hourValue = eveningHour,
                                minuteValue = morningMinute,
                                onHourChange = { eveningHour = it },
                                onMinuteChange = { eveningMinute = it },
                                onSave = {
                                    preferences.eveningHour = eveningHour.toIntOrNull() ?: 20
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            // Observation interval
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Observe every",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = observeInterval,
                                        onValueChange = { observeInterval = it.filter { c -> c.isDigit() }.take(4) },
                                        modifier = Modifier.width(72.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "minutes",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(TwentSpacing.lg))
                }
            }

            // Content Generation Section (visible when enabled)
            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    TwentSectionTitle("Content to Generate")
                    Spacer(modifier = Modifier.height(TwentSpacing.md))

                    TwentCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ToggleRow(
                                title = "Social media posts",
                                subtitle = "Generate social content from activity",
                                checked = generateSocial,
                                onCheckedChange = {
                                    generateSocial = it
                                    preferences.generateSocialPosts = it
                                    showSavedFeedback = true
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            ToggleRow(
                                title = "News digest",
                                subtitle = "Curated news based on interests",
                                checked = generateNews,
                                onCheckedChange = {
                                    generateNews = it
                                    preferences.generateNewsDigest = it
                                    showSavedFeedback = true
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            ToggleRow(
                                title = "Follow-up reminders",
                                subtitle = "Based on recent interactions",
                                checked = generateFollowups,
                                onCheckedChange = {
                                    generateFollowups = it
                                    preferences.generateFollowups = it
                                    showSavedFeedback = true
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            ToggleRow(
                                title = "Automation ideas",
                                subtitle = "Suggest workflow automations",
                                checked = generateAutomation,
                                onCheckedChange = {
                                    generateAutomation = it
                                    preferences.generateAutomationIdeas = it
                                    showSavedFeedback = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(TwentSpacing.lg))
                }
            }

            // Privacy Section (visible when enabled)
            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    TwentSectionTitle("What to Observe")
                    Spacer(modifier = Modifier.height(TwentSpacing.md))

                    TwentCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ToggleRow(
                                title = "App usage",
                                subtitle = "Requires permission",
                                checked = observeAppUsage,
                                onCheckedChange = {
                                    observeAppUsage = it
                                    preferences.observeAppUsage = it
                                    showSavedFeedback = true
                                },
                                showWarning = true
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            ToggleRow(
                                title = "Workflow history",
                                subtitle = "Record automation executions",
                                checked = observeWorkflows,
                                onCheckedChange = {
                                    observeWorkflows = it
                                    preferences.observeWorkflows = it
                                    showSavedFeedback = true
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            ToggleRow(
                                title = "Overlay activity",
                                subtitle = "Track floating assistant tasks",
                                checked = observeOverlay,
                                onCheckedChange = {
                                    observeOverlay = it
                                    preferences.observeOverlay = it
                                    showSavedFeedback = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(TwentSpacing.lg))
                }
            }

            // Status Section (always visible)
            TwentSectionTitle("Status")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow(
                        icon = Icons.Default.PlayCircle,
                        label = "Current mode",
                        value = agentMode.name
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    StatusRow(
                        icon = Icons.Default.Power,
                        label = "Agent running",
                        value = if (isAgentEnabled) "Yes" else "No"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    StatusRow(
                        icon = Icons.Default.Task,
                        label = "Pending tasks",
                        value = pendingTasks.size.toString()
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Task Review Section
            if (pendingTasks.isNotEmpty()) {
                TwentSectionTitle("Task Review")
                Spacer(modifier = Modifier.height(TwentSpacing.md))

                pendingTasks.forEach { task ->
                    TaskReviewCard(
                        task = task,
                        onApprove = {
                            scope.launch {
                                agent.approveTask(task.id)
                            }
                        },
                        onDismiss = {
                            scope.launch {
                                agent.dismissTask(task.id)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(TwentSpacing.md))
                }
            }

            // Save feedback toast
            AnimatedVisibility(
                visible = showSavedFeedback,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CyanPrimary.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Settings saved",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = CyanPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xxl))
        }
    }
}

@Composable
private fun TimePickerRow(
    label: String,
    hourValue: String,
    minuteValue: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hourValue,
                onValueChange = { onHourChange(it.filter { c -> c.isDigit() }.take(2)) },
                modifier = Modifier.width(56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            OutlinedTextField(
                value = minuteValue,
                onValueChange = { onMinuteChange(it.filter { c -> c.isDigit() }.take(2)) },
                modifier = Modifier.width(56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            IconButton(
                onClick = onSave
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save time",
                    tint = OrangePrimary
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showWarning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (showWarning) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Requires permission",
                        tint = OrangePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OrangePrimary,
                checkedTrackColor = OrangePrimary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = OrangePrimary
        )
    }
}

@Composable
private fun TaskReviewCard(
    task: TwAutonomousAgent.AutonomousTask,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    TwentCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Task type badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = OrangePrimary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = task.type.name.replace("_", " "),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Task title
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Task summary
            Text(
                text = task.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Confidence indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                LinearProgressIndicator(
                    progress = { task.confidence },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(task.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dismiss")
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangePrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve")
                }
            }
        }
    }
}