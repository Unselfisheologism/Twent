package com.ai.assistance.operit.ui.features.packages.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.twent.components.*
import com.ai.assistance.operit.ui.theme.OrangePrimary
import com.ai.assistance.operit.ui.theme.CyanPrimary
import com.ai.assistance.operit.ui.theme.SteelPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Modern Redesigned Package Manager Screen - Twent UI
 * Card-based layout with modern aesthetics
 */
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToMCPDetail: ((com.ai.assistance.operit.data.api.GitHubIssue) -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    // State
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.PACKAGES) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    // For simplicity, showing placeholder with modern design
    // Full implementation would include all the package management logic

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
                    text = "Packages",
                    fontSize = 40.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TwentSpacing.sm)) {
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(OrangePrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Import",
                            tint = OrangePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Upload,
                            contentDescription = "Export",
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.md))

            // Modern Tab Bar
            ModernTabBar(
                tabs = listOf(
                    TabItem("Packages", Icons.Outlined.Extension),
                    TabItem("Skills", Icons.Outlined.Build),
                    TabItem("MCP", Icons.Outlined.Cloud)
                ),
                selectedTab = selectedTab.ordinal,
                onTabSelected = { index ->
                    selectedTab = when (index) {
                        0 -> PackageTab.PACKAGES
                        1 -> PackageTab.SKILLS
                        2 -> PackageTab.MCP
                        else -> PackageTab.PACKAGES
                    }
                }
            )

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Content Area
            when (selectedTab) {
                PackageTab.PACKAGES -> {
                    // Package Cards
                    PackageCard(
                        name = "Automatic Tools",
                        description = "AI-powered automation packages",
                        icon = Icons.Outlined.AutoMode,
                        count = 12,
                        accentColor = OrangePrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(TwentSpacing.md))

                    PackageCard(
                        name = "Experimental",
                        description = "Beta and testing packages",
                        icon = Icons.Outlined.Science,
                        count = 5,
                        accentColor = CyanPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(TwentSpacing.md))

                    PackageCard(
                        name = "Utilities",
                        description = "General purpose tools",
                        icon = Icons.Outlined.Settings,
                        count = 8,
                        accentColor = SteelPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PackageTab.SKILLS -> {
                    TwentCard(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(TwentSpacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Build,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(TwentSpacing.md))
                                Text(
                                    text = "Skills Manager",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(TwentSpacing.sm))
                                Text(
                                    text = "Manage your AI skills and capabilities",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                PackageTab.MCP -> {
                    TwentCard(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(TwentSpacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    tint = SteelPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(TwentSpacing.md))
                                Text(
                                    text = "MCP Configuration",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(TwentSpacing.sm))
                                Text(
                                    text = "Manage Model Context Protocol servers",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xxl))
        }
    }

    // Snackbar
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.Bottom)
    )
}

data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun ModernTabBar(
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TwentSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.xs)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTab
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.98f,
                    animationSpec = tween(200),
                    label = "tab_scale"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected)
                        OrangePrimary
                    else
                        Color.Transparent,
                    onClick = { onTabSelected(index) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = TwentSpacing.sm),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(TwentSpacing.xs))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageCard(
    name: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    TwentCard(
        modifier = modifier,
        onClick = { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TwentSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(TwentSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 18.sp
                )
            }
        }
    }
}
