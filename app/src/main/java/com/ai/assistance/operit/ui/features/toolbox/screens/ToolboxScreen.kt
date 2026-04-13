package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.twent.components.*

/**
 * Modern Redesigned Toolbox Screen - Twent UI
 * Grid-based layout with modern card design
 */
@Composable
fun ToolboxScreen(
    navController: NavController,
    onFileManagerSelected: () -> Unit,
    onTerminalSelected: () -> Unit,
    onAppPermissionsSelected: () -> Unit,
    onUIDebuggerSelected: () -> Unit,
    onFFmpegToolboxSelected: () -> Unit,
    onShellExecutorSelected: () -> Unit,
    onLogcatSelected: () -> Unit,
    onTextToSpeechSelected: () -> Unit,
    onSpeechToTextSelected: () -> Unit,
    onToolTesterSelected: () -> Unit,
    onAgreementSelected: () -> Unit,
    onDefaultAssistantGuideSelected: () -> Unit,
    onProcessLimitRemoverSelected: () -> Unit,
    onHtmlPackagerSelected: () -> Unit,
    onSqlViewerSelected: () -> Unit,
    onAgentSessionsSelected: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = screenWidth >= 600.dp

    val columns = if (isTablet) GridCells.Adaptive(160.dp) else GridCells.Fixed(2)

    TwentScreenPadding {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TwentHeading(
                    text = "Toolbox",
                    fontSize = 40.dp
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OrangePrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Build,
                        contentDescription = "Tools",
                        tint = OrangePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.sm))

            Text(
                text = "Powerful utilities at your fingertips",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Tool Grid
            LazyVerticalGrid(
                columns = columns,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TwentSpacing.md),
                contentPadding = PaddingValues(bottom = TwentSpacing.xxl)
            ) {
                items(getTools(
                    onFileManagerSelected,
                    onTerminalSelected,
                    onAppPermissionsSelected,
                    onUIDebuggerSelected,
                    onFFmpegToolboxSelected,
                    onShellExecutorSelected,
                    onLogcatSelected,
                    onTextToSpeechSelected,
                    onSpeechToTextSelected,
                    onToolTesterSelected,
                    onAgreementSelected,
                    onDefaultAssistantGuideSelected,
                    onProcessLimitRemoverSelected,
                    onHtmlPackagerSelected,
                    onSqlViewerSelected,
                    onAgentSessionsSelected
                )) { tool ->
                    ToolCard(
                        tool = tool,
                        modifier = Modifier.aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: Tool,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "tool_scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shadowElevation = 4.dp,
        onClick = {
            isPressed = true
            tool.onClick()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(tool.accentColor, tool.accentColor.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.name,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
    }
}

data class Tool(
    val name: String,
    val shortDescription: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

private fun getTools(
    onFileManagerSelected: () -> Unit,
    onTerminalSelected: () -> Unit,
    onAppPermissionsSelected: () -> Unit,
    onUIDebuggerSelected: () -> Unit,
    onFFmpegToolboxSelected: () -> Unit,
    onShellExecutorSelected: () -> Unit,
    onLogcatSelected: () -> Unit,
    onTextToSpeechSelected: () -> Unit,
    onSpeechToTextSelected: () -> Unit,
    onToolTesterSelected: () -> Unit,
    onAgreementSelected: () -> Unit,
    onDefaultAssistantGuideSelected: () -> Unit,
    onProcessLimitRemoverSelected: () -> Unit,
    onHtmlPackagerSelected: () -> Unit,
    onSqlViewerSelected: () -> Unit,
    onAgentSessionsSelected: () -> Unit
): List<Tool> {
    return listOf(
        Tool(
            name = "File Manager",
            shortDescription = "Browse files",
            icon = Icons.Outlined.Folder,
            accentColor = OrangePrimary,
            onClick = onFileManagerSelected
        ),
        Tool(
            name = "Terminal",
            shortDescription = "Command line",
            icon = Icons.Outlined.Terminal,
            accentColor = CyanPrimary,
            onClick = onTerminalSelected
        ),
        Tool(
            name = "Permissions",
            shortDescription = "App access",
            icon = Icons.Outlined.Security,
            accentColor = SteelPrimary,
            onClick = onAppPermissionsSelected
        ),
        Tool(
            name = "UI Debugger",
            shortDescription = "Inspect UI",
            icon = Icons.Outlined.BugReport,
            accentColor = OrangeSecondary,
            onClick = onUIDebuggerSelected
        ),
        Tool(
            name = "FFmpeg",
            shortDescription = "Media tools",
            icon = Icons.Outlined.Movie,
            accentColor = CyanSecondary,
            onClick = onFFmpegToolboxSelected
        ),
        Tool(
            name = "Shell",
            shortDescription = "Execute commands",
            icon = Icons.Outlined.Code,
            accentColor = SteelAccent,
            onClick = onShellExecutorSelected
        ),
        Tool(
            name = "Logcat",
            shortDescription = "System logs",
            icon = Icons.Outlined.ListAlt,
            accentColor = OrangePrimary,
            onClick = onLogcatSelected
        ),
        Tool(
            name = "Text to Speech",
            shortDescription = "Voice output",
            icon = Icons.Outlined.RecordVoiceOver,
            accentColor = CyanPrimary,
            onClick = onTextToSpeechSelected
        ),
        Tool(
            name = "Speech to Text",
            shortDescription = "Voice input",
            icon = Icons.Outlined.Mic,
            accentColor = SteelPrimary,
            onClick = onSpeechToTextSelected
        ),
        Tool(
            name = "Tool Tester",
            shortDescription = "Test tools",
            icon = Icons.Outlined.Science,
            accentColor = OrangeSecondary,
            onClick = onToolTesterSelected
        ),
        Tool(
            name = "Agreement",
            shortDescription = "Terms of use",
            icon = Icons.Outlined.Description,
            accentColor = CyanSecondary,
            onClick = onAgreementSelected
        ),
        Tool(
            name = "Assistant",
            shortDescription = "Setup guide",
            icon = Icons.Outlined.Assistant,
            accentColor = SteelAccent,
            onClick = onDefaultAssistantGuideSelected
        ),
        Tool(
            name = "Process Limit",
            shortDescription = "Remove limits",
            icon = Icons.Outlined.Speed,
            accentColor = OrangePrimary,
            onClick = onProcessLimitRemoverSelected
        ),
        Tool(
            name = "HTML Packager",
            shortDescription = "Package web",
            icon = Icons.Outlined.Web,
            accentColor = CyanPrimary,
            onClick = onHtmlPackagerSelected
        ),
        Tool(
            name = "SQL Viewer",
            shortDescription = "Database",
            icon = Icons.Outlined.Storage,
            accentColor = SteelPrimary,
            onClick = onSqlViewerSelected
        ),
        Tool(
            name = "Agent Sessions",
            shortDescription = "AI sessions",
            icon = Icons.Outlined.SmartToy,
            accentColor = OrangeSecondary,
            onClick = onAgentSessionsSelected
        )
    )
}
