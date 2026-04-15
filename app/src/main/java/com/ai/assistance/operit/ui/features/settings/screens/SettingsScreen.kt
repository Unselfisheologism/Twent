package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.twent.components.*
import com.ai.assistance.operit.ui.theme.OrangePrimary
import com.ai.assistance.operit.ui.theme.CyanPrimary
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable

/**
 * COMPLETELY REORGANIZED Settings Screen - Scattered differently from original
 * Features are moved to unexpected locations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToUserPreferences: () -> Unit,
    navigateToToolPermissions: () -> Unit,
    navigateToModelConfig: () -> Unit,
    navigateToThemeSettings: () -> Unit,
    navigateToGlobalDisplaySettings: () -> Unit,
    navigateToModelPrompts: () -> Unit,
    navigateToFunctionalConfig: () -> Unit,
    navigateToChatHistorySettings: () -> Unit,
    navigateToChatBackupSettings: () -> Unit,
    navigateToLanguageSettings: () -> Unit,
    navigateToSpeechServicesSettings: () -> Unit,
    navigateToCustomHeadersSettings: () -> Unit,
    navigateToPersonaCardGeneration: () -> Unit,
    navigateToWaifuModeSettings: () -> Unit,
    navigateToTokenUsageStatistics: () -> Unit,
    navigateToContextSummarySettings: () -> Unit,
    navigateToLayoutAdjustmentSettings: () -> Unit,
    navigateToAssistantThemeSettings: () -> Unit,
    navigateToAgentPersonalitySettings: () -> Unit,
    navigateToPowerUserModeSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    TwentScreenPadding {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            TwentHeading(
                text = "System Settings",
                fontSize = 40.dp
            )
            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Your Profile Card - replaces GitHub Account
            TwentCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(OrangePrimary, CyanPrimary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customize your agent personality",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    TwentButton(
                        onClick = navigateToAgentPersonalitySettings,
                        text = "Configure",
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // SECTION 1: Quick Setup - MOVED TO TOP (different from original)
            TwentSectionTitle("Quick Setup")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Brush,
                        title = "Agent Theme",
                        subtitle = "Customize agent appearance",
                        onClick = navigateToAssistantThemeSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Memory,
                        title = "Model Settings",
                        subtitle = "API and model configuration",
                        onClick = navigateToModelConfig
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Psychology,
                        title = "Model Prompts",
                        subtitle = "System prompts and instructions",
                        onClick = navigateToModelPrompts
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // SECTION 2: Interface - SCATTERED (different from original Personalization)
            TwentSectionTitle("Interface")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = "Appearance and colors",
                        onClick = navigateToThemeSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Display",
                        subtitle = "Global display settings",
                        onClick = navigateToGlobalDisplaySettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = "Language",
                        subtitle = "App language settings",
                        onClick = navigateToLanguageSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.AspectRatio,
                        title = "Layout Adjustment",
                        subtitle = "Adjust UI layout and spacing",
                        onClick = navigateToLayoutAdjustmentSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // SECTION 3: Intelligence - MOVED HERE (different from original AI & Models)
            TwentSectionTitle("Intelligence")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.VpnKey,
                        title = "AI API Usage",
                        subtitle = "View API token statistics and costs",
                        onClick = navigateToTokenUsageStatistics
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Settings,
                        title = "Functional Config",
                        subtitle = "Advanced functionality settings",
                        onClick = navigateToFunctionalConfig
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Summarize,
                        title = "Context & Summary",
                        subtitle = "Context and summary settings",
                        onClick = navigateToContextSummarySettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // SECTION 4: Data - MOVED HERE (different from original Data & Privacy)
            TwentSectionTitle("Data")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.History,
                        title = "Chat Sessions",
                        subtitle = "Manage conversation history",
                        onClick = navigateToChatHistorySettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Backup,
                        title = "Backup & Restore",
                        subtitle = "Export and import data",
                        onClick = navigateToChatBackupSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Http,
                        title = "Custom Headers",
                        subtitle = "HTTP request headers",
                        onClick = navigateToCustomHeadersSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xl))

            // SECTION 5: System - MOVED HERE (different from original Advanced)
            TwentSectionTitle("System")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Mic,
                        title = "Speech Services",
                        subtitle = "TTS and STT configuration",
                        onClick = navigateToSpeechServicesSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Security,
                        title = "Tool Permissions",
                        subtitle = "Manage tool access",
                        onClick = navigateToToolPermissions
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Bolt,
                        title = "Power User Mode",
                        subtitle = "Advanced features and developer tools",
                        onClick = navigateToPowerUserModeSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Face,
                        title = "Companion Mode",
                        subtitle = "AI response sentence-by-sentence mode",
                        onClick = navigateToWaifuModeSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xxl))
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(OrangePrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
