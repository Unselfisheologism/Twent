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
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.twent.components.*
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri

/**
 * Modern Redesigned Settings Screen - Twent UI
 * Card-based layout with modern aesthetics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToUserPreferences: () -> Unit,
    navigateToGitHubAccount: () -> Unit,
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
    navigateToAgentPersonalitySettings: () -> Unit
) {
    val context = LocalContext.current
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    val isGitHubLoggedIn = githubAuth.isLoggedInFlow.collectAsState(initial = false).value
    val gitHubUser = githubAuth.userInfoFlow.collectAsState(initial = null).value

    fun initiateGitHubLogin() {
        val authUrl = githubAuth.getAuthorizationUrl()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    TwentScreenPadding {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            TwentHeading(
                text = "Settings",
                fontSize = 40.dp
            )
            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Account Card
            TwentCard(
                modifier = Modifier.fillMaxWidth(),
                gradientBorder = isGitHubLoggedIn
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
                            imageVector = if (isGitHubLoggedIn) Icons.Default.Person else Icons.Outlined.Person,
                            contentDescription = "Account",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGitHubLoggedIn) "GitHub Account" else "Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isGitHubLoggedIn)
                                (gitHubUser?.login ?: "Connected")
                            else
                                "Connect your GitHub account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (!isGitHubLoggedIn) {
                        TwentButton(
                            onClick = { initiateGitHubLogin() },
                            text = "Sign In",
                            modifier = Modifier.width(100.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Personalization Section
            TwentSectionTitle("Personalization")
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
                        icon = Icons.Outlined.Brush,
                        title = "Assistant Theme",
                        subtitle = "Customize assistant appearance",
                        onClick = navigateToAssistantThemeSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // AI & Models Section
            TwentSectionTitle("AI & Models")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Memory,
                        title = "Model Configuration",
                        subtitle = "API and model settings",
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.PersonOutline,
                        title = "Agent Personality",
                        subtitle = "Customize AI behavior",
                        onClick = navigateToAgentPersonalitySettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Data & Privacy Section
            TwentSectionTitle("Data & Privacy")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.History,
                        title = "Chat History",
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
                        icon = Icons.Outlined.VpnKey,
                        title = "Token Usage",
                        subtitle = "View API token statistics",
                        onClick = navigateToTokenUsageStatistics
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.lg))

            // Advanced Section
            TwentSectionTitle("Advanced")
            Spacer(modifier = Modifier.height(TwentSpacing.md))

            TwentCard(modifier = Modifier.fillMaxWidth()) {
                Column {
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
                        icon = Icons.Outlined.Settings,
                        title = "Functional Config",
                        subtitle = "Advanced functionality",
                        onClick = navigateToFunctionalConfig
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}
