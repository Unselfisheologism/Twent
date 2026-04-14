package com.ai.assistance.operit.ui.features.demo.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionLevelCard(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasAccessibilityServiceEnabled: Boolean,
        hasLocationPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onOperitTerminalClick: () -> Unit,
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var showAccessibilityHelp by remember { mutableStateOf(false) }

    // Show accessibility help dialog
    AccessibilityHelpDialog(
        show = showAccessibilityHelp,
        onDismiss = { showAccessibilityHelp = false }
    )

    // Refresh rotation animation
    var refreshRotation by remember { mutableStateOf(0f) }
    val rotationAngle by
            animateFloatAsState(
                    targetValue = refreshRotation,
                    animationSpec = tween(500),
                    label = "Refresh Rotation"
            )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshRotation += 360f
        }
    }

    Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon and title row
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Permission level icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                        text = stringResource(R.string.permission_level),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Accessibility permission content only
            AccessibilityPermissionSection(
                    hasStoragePermission = hasStoragePermission,
                    hasOverlayPermission = hasOverlayPermission,
                    hasBatteryOptimizationExemption = hasBatteryOptimizationExemption,
                    hasLocationPermission = hasLocationPermission,
                    hasAccessibilityServiceEnabled = hasAccessibilityServiceEnabled,
                    isOperitTerminalInstalled = isOperitTerminalInstalled,
                    onStoragePermissionClick = onStoragePermissionClick,
                    onOverlayPermissionClick = onOverlayPermissionClick,
                    onBatteryOptimizationClick = onBatteryOptimizationClick,
                    onLocationPermissionClick = onLocationPermissionClick,
                    onAccessibilityClick = onAccessibilityClick,
                    onOperitTerminalClick = onOperitTerminalClick,
                    showAccessibilityHelp = showAccessibilityHelp,
                    onShowAccessibilityHelpChange = { showAccessibilityHelp = it }
            )

            // Refresh button
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Accessibility mode active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            "Accessibility mode active",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                        onClick = {
                            refreshRotation += 360f
                            onRefresh()
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription =
                                    if (isRefreshing) stringResource(R.string.refreshing)
                                    else stringResource(R.string.refresh_permission_status),
                            modifier = Modifier.graphicsLayer(rotationZ = rotationAngle).size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Permission status item composable
@Composable
fun PermissionStatusItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    val contentColor =
            if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

    val statusText =
            if (isGranted) stringResource(R.string.status_granted)
            else stringResource(R.string.status_not_granted)

    Row(
            modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(contentColor))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = contentColor)
    }
}

// Accessibility permission section - the ONLY section now
@Composable
private fun AccessibilityPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        hasAccessibilityServiceEnabled: Boolean,
        isOperitTerminalInstalled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onOperitTerminalClick: () -> Unit,
        showAccessibilityHelp: Boolean,
        onShowAccessibilityHelpChange: (Boolean) -> Unit
) {
    Column {
        Text(
                text = stringResource(R.string.basic_permissions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.accessibility_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth()
                                .clickable(onClick = onAccessibilityClick)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                                modifier = Modifier.size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                                if (hasAccessibilityServiceEnabled)
                                                        MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                        )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = stringResource(R.string.accessibility_service),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusText =
                                if (hasAccessibilityServiceEnabled) {
                                    stringResource(R.string.status_granted)
                                } else {
                                    stringResource(R.string.status_not_granted)
                                }

                        Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasAccessibilityServiceEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                        )

                        // Help icon for accessibility issues
                        if (!hasAccessibilityServiceEnabled) {
                            IconButton(
                                onClick = { onShowAccessibilityHelpChange(true) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.accessibility_help_dialog_title),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Feature grid showing capabilities (always shows accessibility-level features)
@Composable
private fun FeatureGrid() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val features = listOf(
                context.getString(R.string.feature_overlay_window) to true,
                context.getString(R.string.feature_file_operations) to true,
                "Android/data" to false,
                "data/data" to false,
                context.getString(R.string.feature_screen_auto_click) to true,
                context.getString(R.string.feature_system_permission_modification) to false,
                context.getString(R.string.feature_termux_support) to true,
                context.getString(R.string.feature_run_js) to true,
                context.getString(R.string.feature_plugin_market_mcp) to false
        )

        val rows = features.chunked(3)
        rows.forEach { rowFeatures ->
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowFeatures.forEach { (feature, supported) ->
                    FeatureItem(
                            name = feature,
                            isSupported = supported,
                            modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowFeatures.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FeatureItem(name: String, isSupported: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
                modifier = Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(
                                if (isSupported)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        .border(
                                width = 1.dp,
                                color = if (isSupported)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                shape = CircleShape
                        ),
                contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val icon = when (name) {
                context.getString(R.string.feature_overlay_window) -> Icons.Default.Web
                context.getString(R.string.feature_file_operations) -> Icons.Default.Folder
                "Android/data" -> Icons.Default.Storage
                "data/data" -> Icons.Default.Storage
                context.getString(R.string.feature_screen_auto_click) -> Icons.Default.TouchApp
                context.getString(R.string.feature_system_permission_modification) -> Icons.Default.Settings
                context.getString(R.string.feature_termux_support) -> Icons.Default.Terminal
                context.getString(R.string.feature_run_js) -> Icons.Default.Code
                context.getString(R.string.feature_plugin_market_mcp) -> Icons.Default.Store
                else -> Icons.Default.CheckCircle
            }

            Icon(
                    imageVector = if (isSupported) icon else Icons.Default.Lock,
                    contentDescription = if (isSupported) context.getString(R.string.supported) else context.getString(R.string.not_supported),
                    tint = if (isSupported) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSupported) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                maxLines = 1
        )
    }
}
