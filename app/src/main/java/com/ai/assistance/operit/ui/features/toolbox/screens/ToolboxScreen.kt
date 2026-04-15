package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.twent.components.*
import com.ai.assistance.operit.ui.twent.components.TwentSpacing
import com.ai.assistance.operit.ui.theme.*

/**
 * COMPLETELY REDESIGNED Toolbox Screen - Different layout structure
 * List-based with categories instead of grid
 */

// Tool categories for different organization
enum class ToolCategory(val displayName: String) {
    DEVELOPMENT("Development"),
    MEDIA("Media"),
    SYSTEM("System"),
    PRODUCTIVITY("Productivity"),
    TESTING("Testing")
}

data class ToolItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
    val accentColor: Color,
    val onClick: () -> Unit
)

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
    onDefaultAssistantGuideSelected: () -> Unit,
    onProcessLimitRemoverSelected: () -> Unit,
    onHtmlPackagerSelected: () -> Unit,
    onSqlViewerSelected: () -> Unit,
    onAgentSessionsSelected: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Different tool organization - CATEGORIES instead of flat list
    val tools = remember {
        listOf(
            // Development tools
            ToolItem("Linux Terminal", "Command line", Icons.Outlined.Code, ToolCategory.DEVELOPMENT, CyanPrimary, onTerminalSelected),
            ToolItem("Shell Executor", "Run commands", Icons.Outlined.Terminal, ToolCategory.DEVELOPMENT, SteelAccent, onShellExecutorSelected),
            ToolItem("File Manager", "Browse files", Icons.Outlined.Folder, ToolCategory.DEVELOPMENT, OrangePrimary, onFileManagerSelected),
            ToolItem("SQL Data Viewer", "Database queries", Icons.Outlined.Storage, ToolCategory.DEVELOPMENT, SteelPrimary, onSqlViewerSelected),
            
            // Media tools
            ToolItem("FFmpeg Media Engine", "Audio/video processing", Icons.Outlined.Movie, ToolCategory.MEDIA, CyanSecondary, onFFmpegToolboxSelected),
            ToolItem("Text-To-Speech", "Voice output", Icons.Outlined.RecordVoiceOver, ToolCategory.MEDIA, CyanPrimary, onTextToSpeechSelected),
            ToolItem("Speech Recognition", "Voice input", Icons.Outlined.Mic, ToolCategory.MEDIA, SteelPrimary, onSpeechToTextSelected),
            
            // System tools
            ToolItem("System Logcat", "System logs", Icons.Outlined.ListAlt, ToolCategory.SYSTEM, OrangePrimary, onLogcatSelected),
            ToolItem("Permissions", "App access", Icons.Outlined.Security, ToolCategory.SYSTEM, SteelPrimary, onAppPermissionsSelected),
            ToolItem("UI Debugger", "Inspect UI", Icons.Outlined.BugReport, ToolCategory.SYSTEM, OrangeSecondary, onUIDebuggerSelected),
            ToolItem("Process Limit", "Remove limits", Icons.Outlined.Speed, ToolCategory.SYSTEM, OrangePrimary, onProcessLimitRemoverSelected),
            
            // Productivity tools
            ToolItem("Agent Sessions", "AI sessions", Icons.Outlined.SmartToy, ToolCategory.PRODUCTIVITY, OrangeSecondary, onAgentSessionsSelected),
            ToolItem("HTML Packager", "Package web", Icons.Outlined.Web, ToolCategory.PRODUCTIVITY, CyanPrimary, onHtmlPackagerSelected),
            ToolItem("Default Assistant", "Setup guide", Icons.Outlined.Assistant, ToolCategory.PRODUCTIVITY, SteelAccent, onDefaultAssistantGuideSelected),
            
            // Testing tools
            ToolItem("Tool Tester", "Test tools", Icons.Outlined.Science, ToolCategory.TESTING, OrangeSecondary, onToolTesterSelected)
        )
    }
    
    // Group tools by category
    val toolsByCategory = remember(tools) {
        tools.groupBy { it.category }
    }
    
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    
    // REDESIGNED LAYOUT - List with categories instead of grid
    TwentScreenPadding {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Different header style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Toolset",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Your agentic command center",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Different icon style
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(OrangePrimary, OrangeSecondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Construction,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(TwentSpacing.xl))
            
            // Horizontal category selector - DIFFERENT from original
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.sm),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // "All" category
                item {
                    CategoryChip(
                        name = "All",
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                }
                
                // Other categories
                items(ToolCategory.values().toList()) { category ->
                    CategoryChip(
                        name = category.displayName,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(TwentSpacing.lg))
            
            // List layout with categories - DIFFERENT from original grid
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(TwentSpacing.md),
                contentPadding = PaddingValues(bottom = TwentSpacing.xxl)
            ) {
                // Filter tools based on selected category
                val filteredTools = if (selectedCategory != null) {
                    tools.filter { it.category == selectedCategory }
                } else {
                    tools
                }
                
                // Group by category for display
                val groupedTools = filteredTools.groupBy { it.category }
                
                groupedTools.forEach { (category, categoryTools) ->
                    // Category header
                    item(key = "header_${category.name}") {
                        CategoryHeader(category = category)
                    }
                    
                    // Tools in this category
                    items(categoryTools, key = { it.name }) { tool ->
                        ToolListItem(
                            tool = tool,
                            onClick = tool.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryHeader(category: ToolCategory) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TwentSpacing.sm),
        modifier = Modifier.padding(vertical = TwentSpacing.sm)
    ) {
        // Different header style - accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(OrangePrimary)
        )
        
        Text(
            text = category.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ToolListItem(
    tool: ToolItem,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "tool_scale"
    )
    
    Surface(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        onClick = {
            isPressed = true
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
        ) {
            // Different icon style
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tool.accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Different arrow style
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
