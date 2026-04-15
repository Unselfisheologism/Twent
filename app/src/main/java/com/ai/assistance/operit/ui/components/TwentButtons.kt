package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.*
/**
 * Twent AI Button Components
 * Clean, modern buttons with pill shapes and gradients
 */

/**
 * Primary button with gradient background
 */
@Composable
fun TwentButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradientColors: List<Color> = listOf(OrangePrimary, OrangeSecondary),
    contentColor: Color = Color.White,
    shape: Shape = TwentComponentShapes.pillShape
) {
    Box(
        modifier = modifier
            .height(TwentDimensions.buttonHeight)
            .clip(shape)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(colors = gradientColors)
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) contentColor 
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Secondary button with outline
 */
@Composable
fun TwentSecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = OrangePrimary,
    contentColor: Color = OrangePrimary,
    shape: Shape = TwentComponentShapes.pillShape
) {
    Box(
        modifier = modifier
            .height(TwentDimensions.buttonHeight)
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (enabled) borderColor 
                       else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) contentColor 
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Icon button with background
 */
@Composable
fun TwentIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .size(TwentDimensions.touchTarget)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) backgroundColor
                else backgroundColor.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            icon()
        }
    }
}

/**
 * Floating action button
 */
@Composable
fun TwentFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = OrangePrimary,
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0.8f))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            icon()
        }
    }
}

/**
 * Chip/tag button
 */
@Composable
fun TwentChip(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedBackgroundColor: Color = OrangePrimary,
    unselectedBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    selectedContentColor: Color = Color.White,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(TwentComponentShapes.chipShape)
            .background(
                if (selected) selectedBackgroundColor
                else unselectedBackgroundColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) selectedContentColor
                   else unselectedContentColor
        )
    }
}

/**
 * Tab button (for tab bars)
 */
@Composable
fun TwentTabButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val backgroundColor = if (selected) OrangePrimary 
                          else Color.Transparent
    val contentColor = if (selected) Color.White
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * Text button (minimal)
 */
@Composable
fun TwentTextButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = OrangePrimary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) contentColor 
               else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
    )
}
