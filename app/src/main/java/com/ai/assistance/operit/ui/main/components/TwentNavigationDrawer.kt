package com.ai.assistance.operit.ui.main.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.*

/**
 * Twent AI Navigation Drawer
 * Clean, modern design inspired by reference images
 * Full-screen overlay style with numbered navigation items
 */

@Composable
fun TwentNavigationDrawerHeader(
    appName: String = "Twent AI",
    subtitle: String = "Agentic OS",
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = TwentSpacing.xl + 16.dp, // Account for status bar
                start = TwentSpacing.xl,
                end = TwentSpacing.xl,
                bottom = TwentSpacing.lg
            )
    ) {
        // Top row with logo and close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo and brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
            ) {
                // Logo icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(OrangePrimary, CyanPrimary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Brand text
                Column {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TwentNavigationDrawerSection(
    title: String,
    items: List<NavItemData>,
    selectedItemRoute: String?,
    onItemSelected: (String) -> Unit,
    startNumber: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TwentSpacing.xl)
    ) {
        // Section header
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = OrangePrimary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(
                top = TwentSpacing.lg,
                bottom = TwentSpacing.md
            )
        )
        
        // Navigation items
        items.forEachIndexed { index, item ->
            val itemNumber = startNumber + index
            val isSelected = item.route == selectedItemRoute
            
            TwentNavigationDrawerItem(
                number = itemNumber,
                item = item,
                isSelected = isSelected,
                onClick = { onItemSelected(item.route) }
            )
            
            // Add divider between items (not after last item)
            if (index < items.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = TwentSpacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun TwentNavigationDrawerItem(
    number: Int,
    item: NavItemData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    OrangePrimary.copy(alpha = 0.1f)
                } else {
                    Color.Transparent
                }
            )
            .padding(
                horizontal = TwentSpacing.md,
                vertical = TwentSpacing.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) OrangePrimary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%02d".format(number),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Item content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OrangePrimary
                       else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (item.description != null) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Selection indicator or arrow
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(OrangePrimary)
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TwentNavigationDrawerFooter(
    version: String = "v1.0.0",
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TwentSpacing.xl,
                end = TwentSpacing.xl,
                bottom = TwentSpacing.xl + 16.dp // Account for bottom system bar
            )
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = TwentSpacing.lg),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        
        // Settings and info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Version info
            Text(
                text = "Twent AI $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            
            // Settings button
            TextButton(
                onClick = onSettingsClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(TwentSpacing.xs))
                Text(
                    text = "System Settings",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Data class for navigation items
 */
data class NavItemData(
    val route: String,
    val title: String,
    val description: String? = null,
    val icon: ImageVector? = null
)
