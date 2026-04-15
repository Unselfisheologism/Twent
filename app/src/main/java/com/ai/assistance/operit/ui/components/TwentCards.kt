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
 * Clean, modern cards with rounded corners and subtle shadows
 */

/**
 * Standard card with gradient border (signature Twent AI style)
 */
@Composable
fun TwentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradientBorder: Boolean = false,
    shape: Shape = TwentComponentShapes.cardShape,
    elevation: Dp = TwentElevation.small,
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
                    width = 1.dp,
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
            elevation = TwentElevation.medium,
            shape = TwentComponentShapes.featureCardShape,
            spotColor = backgroundColor.copy(alpha = 0.3f)
        )
        .clip(TwentComponentShapes.featureCardShape)
        .background(backgroundColor)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    
    Column(
        modifier = cardModifier.padding(TwentSpacing.lg),
        content = content
    )
}

/**
 * Compact info card - for stats and metrics
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
            modifier = Modifier.padding(TwentSpacing.lg)
        ) {
            // Icon and title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.sm)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
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
            
            Spacer(modifier = Modifier.height(TwentSpacing.md))
            
            // Value
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = TwentSpacing.xs)
                )
            }
        }
    }
}

/**
 * Grid card - for 2x2 grid layouts
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
            elevation = TwentElevation.small,
            shape = TwentComponentShapes.cardShape,
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
        .padding(TwentSpacing.lg)
    
    Column(
        modifier = cardModifier,
        content = content
    )
}

/**
 * List item card - for horizontal lists
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
                .padding(TwentSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
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
                        modifier = Modifier.padding(top = TwentSpacing.xxs)
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
                horizontal = TwentSpacing.lg,
                vertical = TwentSpacing.md
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (action != null) {
            action()
        }
    }
}
