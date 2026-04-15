package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.*

/**
 * Twent AI Card Components
 * Based on reference images: 24dp corner radius, subtle shadows, gradient borders
 */

/**
 * Standard card with gradient border (signature Twent AI style)
 * Based on reference images: 24dp corner radius, 4dp shadow
 */
@Composable
fun TwentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradientBorder: Boolean = false,
    shape: Shape = TwentComponentShapes.cardShape, // 24dp from reference
    elevation: Dp = TwentElevation.medium, // 4dp from reference
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .shadow(
            elevation = elevation,
            shape = shape,
            spotColor = OrangePrimary.copy(alpha = 0.1f)
        )
        .clip(shape)
        .then(
            if (gradientBorder) {
                Modifier.border(
                    width = 2.dp, // 2dp border from reference
                    brush = Brush.linearGradient(
                        colors = listOf(OrangePrimary, CyanPrimary)
                    ),
                    shape = shape
                )
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .background(MaterialTheme.colorScheme.surface)
    
    Column(
        modifier = cardModifier,
        content = content
    )
}

/**
 * Feature card - larger, more prominent
 * Based on reference images: 24dp corner radius, gradient background
 */
@Composable
fun TwentFeatureCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = OrangePrimary,
    contentColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .shadow(
            elevation = TwentElevation.medium, // 4dp from reference
            shape = TwentComponentShapes.featureCardShape, // 24dp from reference
            spotColor = backgroundColor.copy(alpha = 0.3f)
        )
        .clip(TwentComponentShapes.featureCardShape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0.8f))
            )
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(TwentSpacing.lg) // 24dp padding from reference
    
    Column(
        modifier = cardModifier,
        content = content
    )
}

/**
 * Compact info card - for stats and metrics
 * Based on reference images: 24dp corner radius, clean design
 */
@Composable
fun TwentInfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: String,
    value: String,
    subtitle: String? = null,
    accentColor: Color = OrangePrimary
) {
    TwentCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(TwentSpacing.lg) // 24dp padding from reference
        ) {
            // Icon and title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.sm) // 8dp from reference
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp) // 48dp from reference
                            .clip(TwentComponentShapes.iconContainerShape) // 14dp from reference
                            .background(accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                }
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(TwentSpacing.md)) // 16dp from reference
            
            // Value - 24sp, bold from reference
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                    fontWeight = FontWeight.Bold // 700 weight from reference
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = TwentSpacing.xs) // 4dp from reference
                )
            }
        }
    }
}

/**
 * Grid card - for 2x2 grid layouts
 * Based on reference images: 24dp corner radius, aspect ratio 1:1
 */
@Composable
fun TwentGridCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .aspectRatio(1f)
        .shadow(
            elevation = TwentElevation.small, // 2dp from reference
            shape = TwentComponentShapes.cardShape, // 24dp from reference
            spotColor = (backgroundColor ?: OrangePrimary).copy(alpha = 0.1f)
        )
        .clip(TwentComponentShapes.cardShape)
        .background(backgroundColor ?: MaterialTheme.colorScheme.surface)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(TwentSpacing.lg) // 24dp padding from reference
    
    Column(
        modifier = cardModifier,
        content = content
    )
}

/**
 * List item card - for horizontal lists
 * Based on reference images: 16dp padding, clean design
 */
@Composable
fun TwentListItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    TwentCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TwentSpacing.lg), // 24dp padding from reference
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md) // 16dp from reference
        ) {
            // Leading content
            if (leadingContent != null) {
                leadingContent()
            }
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = TwentSpacing.xxs) // 2dp from reference
                    )
                }
            }
            
            // Trailing content
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

/**
 * Section header - for grouping content
 * Based on reference images: 12sp, uppercase, letter spacing
 */
@Composable
fun TwentSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = TwentSpacing.lg, // 24dp from reference
                vertical = TwentSpacing.md // 16dp from reference
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, // 600 weight from reference
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
        
        if (action != null) {
            action()
        }
    }
}
