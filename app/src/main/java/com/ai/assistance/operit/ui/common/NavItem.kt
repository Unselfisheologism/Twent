package com.ai.assistance.operit.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

/**
 * Navigation items with completely different icon set
 * Using Outlined icons instead of Filled to look different from original
 */

sealed class NavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
    // Main pages - completely different icon choices
    object AiChat : NavItem("ai_chat", R.string.nav_ai_chat, Icons.Outlined.Chat)
    object AgentCLIs : NavItem("agent_clis", R.string.nav_agent_clis, Icons.Outlined.DeveloperBoard)
    object Packages : NavItem("packages", R.string.nav_packages, Icons.Outlined.Inventory2)
    object MemoryBase : NavItem("memory_base", R.string.nav_memory_base, Icons.Outlined.Psychology)
    object Toolbox : NavItem("toolbox", R.string.nav_toolbox, Icons.Outlined.Construction)
    object Workflow : NavItem("workflow", R.string.nav_workflow, Icons.Outlined.AccountTree)
    object Terminal : NavItem("terminal", R.string.terminal, Icons.Outlined.Code)
    object MiniApps : NavItem("mini_apps", R.string.nav_mini_apps, Icons.Outlined.Widgets)
    object Mcp : NavItem("mcp", R.string.mcp, Icons.Outlined.Hub)
    
    // Config pages
    object TokenConfig : NavItem("token_config", R.string.token_config, Icons.Outlined.Key)
    object AssistantConfig : NavItem("assistant_config", R.string.nav_assistant_config, Icons.Outlined.Tune)
    
    // System pages
    object Settings : NavItem("settings", R.string.nav_settings, Icons.Outlined.Settings)
    object Help : NavItem("help", R.string.nav_help, Icons.Outlined.MenuBook)
    object About : NavItem("about", R.string.nav_about, Icons.Outlined.Info)
    object UpdateHistory : NavItem("update_history", R.string.update_history, Icons.Outlined.NewReleases)
    object PowerUserMode : NavItem("power_user_mode", R.string.nav_power_user_mode, Icons.Outlined.Bolt)
    
    // Permission pages
    object Permissions : NavItem("permissions", R.string.permissions, Icons.Outlined.AdminPanelSettings)
    object ToolPermissions : NavItem("tool_permissions", R.string.tool_permissions, Icons.Outlined.Security)
    
    // User preferences
    // UserPreferencesGuide removed - over-engineered personalization
    object UserPreferencesSettings : NavItem(
        "user_preferences_settings",
        R.string.user_preferences_settings,
        Icons.Outlined.ManageAccounts
    )
    object ChatHistorySettings : NavItem(
        "chat_history_settings",
        R.string.chat_history_settings,
        Icons.Outlined.History
    )
    
    // Removed: Agreement - no longer in navigation
}
