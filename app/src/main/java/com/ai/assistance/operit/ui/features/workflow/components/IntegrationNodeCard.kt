package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.IntegrationNode
import com.ai.assistance.operit.data.model.IntegrationNodeConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Integration Node Card composable
 * Displays integration nodes with detailed information about the integration
 * 
 * Features:
 * - Integration type icon (tool/webhook/MCP/OAuth)
 * - Node title/name
 * - Integration name (toolkit or webhook URL)
 * - Action being performed
 * - Connection status indicator
 * - Enable/disable toggle
 * - Error state for misconfigured integrations
 */
@Composable
fun IntegrationNodeCard(
    node: IntegrationNode,
    isDragging: Boolean,
    executionState: NodeExecutionState? = null,
    isConnected: Boolean = false,
    hasError: Boolean = false,
    errorMessage: String? = null,
    onDragStart: () -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Get the style based on integration type
    val integrationStyle = getIntegrationNodeStyle(node.integrationType)
    
    // Determine border color based on execution state and error state
    val executionBorderColor = when {
        hasError -> Color(0xFFF44336) // Red for errors
        executionState is NodeExecutionState.Running -> Color(0xFF2196F3) // Blue
        executionState is NodeExecutionState.Success -> Color(0xFF4CAF50) // Green
        executionState is NodeExecutionState.Skipped -> Color(0xFF9E9E9E) // Gray
        executionState is NodeExecutionState.Failed -> Color(0xFFF44336) // Red
        else -> null
    }
    
    var hasDragged by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .width(160.dp)
            .height(120.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var longPressJob: kotlinx.coroutines.Job? = null
                    var isLongPressed = false
                    
                    detectTapGestures(
                        onPress = {
                            isLongPressed = false
                            hasDragged = false
                            
                            // Start long press detection
                            longPressJob = coroutineScope.launch {
                                delay(500)
                                if (!hasDragged) {
                                    isLongPressed = true
                                    onLongPress()
                                }
                            }
                            
                            tryAwaitRelease()
                            longPressJob?.cancel()
                            
                            // Only trigger click if not long pressed, not dragged, and not dragging
                            if (!isLongPressed && !hasDragged && !isDragging) {
                                onClick()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            hasDragged = true
                            onDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = {
                            onDragEnd()
                            coroutineScope.launch {
                                delay(100)
                                hasDragged = false
                            }
                        },
                        onDragCancel = {
                            onDragCancel()
                            hasDragged = false
                        }
                    )
                }
                .border(
                    width = if (executionBorderColor != null) 3.dp else 2.dp,
                    color = executionBorderColor ?: (if (isDragging) integrationStyle.primaryColor else integrationStyle.borderColor),
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isDragging) 12.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    hasError -> Color(0xFFFFEBEE) // Light red background for errors
                    isDragging -> integrationStyle.backgroundColor.copy(alpha = 0.8f)
                    !node.enabled -> Color(0xFFFAFAFA) // Grayed out when disabled
                    else -> Color.White
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top row: Type badge and connection status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Integration type badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = integrationStyle.primaryColor.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = integrationStyle.icon,
                                contentDescription = null,
                                tint = integrationStyle.primaryColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = integrationStyle.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = integrationStyle.primaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Connection status indicator
                    ConnectionStatusIndicator(
                        isConnected = isConnected && node.enabled,
                        hasError = hasError
                    )
                }
                
                // Node name
                Text(
                    text = node.name.ifEmpty { stringResource(R.string.integration_node_default_name) },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (node.enabled) Color(0xFF212121) else Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Integration info (toolkit name or webhook URL)
                val integrationInfo = getIntegrationInfo(node)
                if (integrationInfo.isNotEmpty()) {
                    Text(
                        text = integrationInfo,
                        fontSize = 9.sp,
                        color = Color(0xFF757575),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Action being performed
                if (node.actionId.isNotEmpty()) {
                    Text(
                        text = node.actionId,
                        fontSize = 8.sp,
                        color = integrationStyle.primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Error message
                if (hasError && !errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage,
                        fontSize = 8.sp,
                        color = Color(0xFFF44336),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Bottom row: Execution state and enable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Execution state
                    if (executionState != null) {
                        ExecutionStateIndicator(
                            executionState = executionState,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    // Enable/Disable toggle
                    Switch(
                        checked = node.enabled,
                        onCheckedChange = onEnabledChanged,
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = integrationStyle.primaryColor,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFBDBDBD)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Connection status indicator
 */
@Composable
private fun ConnectionStatusIndicator(
    isConnected: Boolean,
    hasError: Boolean
) {
    val (icon, color, contentDescription) = when {
        hasError -> Triple(
            Icons.Outlined.LinkOff,
            Color(0xFFF44336),
            stringResource(R.string.integration_status_error)
        )
        isConnected -> Triple(
            Icons.Outlined.Link,
            Color(0xFF4CAF50),
            stringResource(R.string.integration_status_connected)
        )
        else -> Triple(
            Icons.Outlined.Cloud,
            Color(0xFF9E9E9E),
            stringResource(R.string.integration_status_disconnected)
        )
    }
    
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = color,
        modifier = Modifier.size(14.dp)
    )
}

/**
 * Execution state indicator
 */
@Composable
private fun ExecutionStateIndicator(
    executionState: NodeExecutionState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (executionState) {
            is NodeExecutionState.Running -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.workflow_node_running),
                    fontSize = 8.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium
                )
            }
            is NodeExecutionState.Success -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.workflow_node_success),
                    fontSize = 8.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
            is NodeExecutionState.Skipped -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.workflow_node_skipped),
                    fontSize = 8.sp,
                    color = Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Medium
                )
            }
            is NodeExecutionState.Failed -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.workflow_node_failed),
                    fontSize = 8.sp,
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
            }
            else -> {}
        }
    }
}

/**
 * Get integration info text based on node configuration
 */
@Composable
private fun getIntegrationInfo(node: IntegrationNode): String {
    return when (node.integrationType) {
        IntegrationNodeConstants.TYPE_TOOL -> {
            // Show toolkit name for tool integrations
            node.toolkit.ifEmpty { stringResource(R.string.integration_no_toolkit) }
        }
        IntegrationNodeConstants.TYPE_WEBHOOK -> {
            // Show webhook URL for webhook integrations
            node.webhookConfig?.url?.let { url ->
                // Truncate long URLs
                if (url.length > 25) {
                    url.take(22) + "..."
                } else {
                    url
                }
            } ?: stringResource(R.string.integration_no_webhook_configured)
        }
        IntegrationNodeConstants.TYPE_MCP -> {
            // Show MCP server name
            node.mcpServerConfig?.serverName?.ifEmpty {
                stringResource(R.string.integration_no_mcp_server)
            } ?: stringResource(R.string.integration_no_mcp_server)
        }
        IntegrationNodeConstants.TYPE_OAUTH -> {
            // Show account info
            if (node.accountId != null) {
                stringResource(R.string.integration_oauth_configured)
            } else {
                stringResource(R.string.integration_oauth_not_configured)
            }
        }
        else -> stringResource(R.string.integration_unknown_type)
    }
}

/**
 * Integration node style data class
 */
private data class IntegrationNodeStyle(
    val primaryColor: Color,
    val backgroundColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
    val label: String
)

/**
 * Get the style for an integration node based on its type
 */
@Composable
private fun getIntegrationNodeStyle(integrationType: String): IntegrationNodeStyle {
    return when (integrationType) {
        IntegrationNodeConstants.TYPE_TOOL -> IntegrationNodeStyle(
            primaryColor = Color(0xFF2196F3), // Blue
            backgroundColor = Color(0xFFE3F2FD),
            borderColor = Color(0xFF64B5F6),
            icon = Icons.Default.Build,
            label = stringResource(R.string.integration_type_tool)
        )
        IntegrationNodeConstants.TYPE_WEBHOOK -> IntegrationNodeStyle(
            primaryColor = Color(0xFF00BCD4), // Cyan
            backgroundColor = Color(0xFFE0F7FA),
            borderColor = Color(0xFF4DD0E1),
            icon = Icons.Outlined.Link,
            label = stringResource(R.string.integration_type_webhook)
        )
        IntegrationNodeConstants.TYPE_MCP -> IntegrationNodeStyle(
            primaryColor = Color(0xFF9C27B0), // Purple
            backgroundColor = Color(0xFFF3E5F5),
            borderColor = Color(0xFFCE93D8),
            icon = Icons.Default.Extension,
            label = stringResource(R.string.integration_type_mcp)
        )
        IntegrationNodeConstants.TYPE_OAUTH -> IntegrationNodeStyle(
            primaryColor = Color(0xFFFF9800), // Orange
            backgroundColor = Color(0xFFFFF3E0),
            borderColor = Color(0xFFFFB74D),
            icon = Icons.Default.Security,
            label = stringResource(R.string.integration_type_oauth)
        )
        else -> IntegrationNodeStyle(
            primaryColor = Color(0xFF9E9E9E), // Gray
            backgroundColor = Color(0xFFF5F5F5),
            borderColor = Color(0xFFBDBDBD),
            icon = Icons.Default.Help,
            label = stringResource(R.string.integration_type_unknown)
        )
    }
}
