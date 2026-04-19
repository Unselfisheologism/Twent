package com.ai.assistance.operit.ui.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.twent.components.TwentSpacing
import com.ai.assistance.operit.ui.theme.OrangePrimary
import com.ai.assistance.operit.ui.theme.CyanPrimary

/**
 * Twent AI Navigation Components
 * Based on reference images: Full-screen overlay, numbered items, red accent
 */

/**
 * Modern Navigation Drawer Header - Matches reference images
 */
@Composable
fun NavigationDrawerHeader(
    appName: String = "Twent",
    appSubtitle: String = "Agentic OS",
    onCloseClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = TwentSpacing.xxl + 16.dp, // Status bar padding
                start = TwentSpacing.lg,
                end = TwentSpacing.lg,
                bottom = TwentSpacing.lg
            )
    ) {
        // Top row: Logo + Close button (from reference)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo and brand (from reference)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
            ) {
                // Logo icon - 48dp from reference
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(OrangePrimary, CyanPrimary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Brand text - 16sp, bold from reference
                Column {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Close button - 8dp rounded rectangle from reference
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)) // 8dp from reference
                    .background(Color.Black) // Black from reference
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // "Menu" label - 12sp, uppercase, red from reference
        Text(
            text = "MENU",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = OrangePrimary, // Red accent from reference
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = TwentSpacing.xxl)
        )
    }
}

/**
 * Modern Navigation Drawer Item - Numbered, matches reference images
 */
@Composable
fun ModernNavigationDrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    description: String? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "nav_scale"
    )

    // Full-width item with bottom border (from reference)
    Column(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(
                horizontal = TwentSpacing.lg, // 24dp from reference
                vertical = TwentSpacing.md // 16dp from reference
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
        ) {
            // Number badge - 12sp from reference
            if (number != null) {
                Text(
                    text = "%02d".format(number),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal, // 400 weight from reference
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(24.dp)
                )
            }

            // Label - 20sp, bold for active from reference
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp, // 24sp for active from reference
                        fontWeight = FontWeight.Bold // 700 weight from reference
                    )
                } else {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 20.sp, // 20sp for inactive from reference
                        fontWeight = FontWeight.Normal // 400 weight from reference
                    )
                },
                color = if (selected) OrangePrimary // Red accent from reference
                       else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Description - only for inactive items
            if (description != null && !selected) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Badge or arrow
            if (selected) {
                // Active indicator - 8dp dot from reference
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OrangePrimary)
                )
            } else {
                // Arrow or badge
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        // Bottom border - 1px #D0D0D0 from reference
        HorizontalDivider(
            modifier = Modifier.padding(top = TwentSpacing.md),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

/**
 * Navigation Section Header - Uppercase, 12sp from reference
 */
@Composable
fun NavigationSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold, // 600 weight from reference
        color = OrangePrimary, // Red accent from reference
        letterSpacing = 1.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = TwentSpacing.lg,
                vertical = TwentSpacing.md
            )
    )
}

/**
 * Navigation Divider - 1px from reference
 */
@Composable
fun NavigationDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp) // 1px from reference
            .padding(horizontal = TwentSpacing.lg)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Navigation Drawer Footer - Version and settings
 */
@Composable
fun NavigationDrawerFooter(
    version: String = "v1.0.0",
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TwentSpacing.lg,
                end = TwentSpacing.lg,
                bottom = TwentSpacing.xxl + 16.dp // Bottom system bar padding
            )
    ) {
        // Divider
        NavigationDivider()
        
        Spacer(modifier = Modifier.height(TwentSpacing.lg))
        
        // Settings and version row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Version info - 12sp from reference
            Text(
                text = "Twent $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            
            // Settings button - 14sp, white text from reference
            TextButton(
                onClick = onSettingsClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "System Settings",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
