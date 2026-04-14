package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavGroup
import com.ai.assistance.operit.ui.main.screens.OperitRouter
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Content for the expanded navigation drawer
 * Numbered, clean design inspired by reference images
 */
@Composable
fun DrawerContent(
    navGroups: List<NavGroup>,
    currentScreen: Screen,
    selectedItem: NavItem,
    isNetworkAvailable: Boolean,
    networkType: String,
    scope: CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
    onScreenSelected: (Screen, NavItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(
                bottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            )
    ) {
        // Header with logo and close button
        NavigationDrawerHeader(
            onCloseClick = {
                scope.launch { drawerState.close() }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Grouped navigation menu with numbers
        var itemNumber = 0
        navGroups.forEach { group ->
            NavigationSectionHeader(stringResource(id = group.titleResId))
            
            group.items.forEach { item ->
                ModernNavigationDrawerItem(
                    icon = item.icon,
                    label = stringResource(id = item.titleResId),
                    selected = selectedItem == item,
                    number = itemNumber,
                    onClick = {
                        onScreenSelected(
                            OperitRouter.getScreenForNavItem(item),
                            item
                        )
                        scope.launch { drawerState.close() }
                    }
                )
                itemNumber++
            }
            
            // Add divider between groups (not after last group)
            if (group != navGroups.last()) {
                NavigationDivider()
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer with settings
        NavigationDrawerFooter(
            onSettingsClick = {
                onScreenSelected(
                    OperitRouter.getScreenForNavItem(NavItem.Settings),
                    NavItem.Settings
                )
                scope.launch { drawerState.close() }
            }
        )
    }
}

/**
 * Content for the collapsed navigation drawer (for tablet mode)
 * Shows only icons in a compact layout
 */
@Composable
fun CollapsedDrawerContent(
    navItems: List<NavItem>,
    selectedItem: NavItem,
    isNetworkAvailable: Boolean,
    onScreenSelected: (Screen, NavItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // Spacer for top
        Spacer(modifier = Modifier.height(8.dp))
        
        // Navigation items - icon only
        navItems.forEach { item ->
            val isSelected = selectedItem == item
            
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) com.ai.assistance.operit.ui.theme.OrangePrimary.copy(alpha = 0.15f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable {
                        onScreenSelected(
                            OperitRouter.getScreenForNavItem(item),
                            item
                        )
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(id = item.titleResId),
                    tint = if (isSelected) com.ai.assistance.operit.ui.theme.OrangePrimary
                           else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
