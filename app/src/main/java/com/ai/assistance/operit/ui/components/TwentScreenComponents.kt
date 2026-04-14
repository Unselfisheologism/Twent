package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.*

/**
 * Twent AI Screen Components
 * Helper composables for consistent screen layouts
 */

/**
 * Standard screen padding wrapper
 */
@Composable
fun TwentScreenPadding(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = TwentSpacing.xxl, // Account for status bar
                bottom = TwentSpacing.xl,
                start = TwentSpacing.lg,
                end = TwentSpacing.lg
            ),
        content = content
    )
}

/**
 * Large heading text (like "Toolset", "System Settings")
 */
@Composable
fun TwentHeading(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Dp = 40.dp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = fontSize.value.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * Section title (like "Personalization", "AI & Models")
 */
@Composable
fun TwentSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(
            horizontal = TwentSpacing.lg,
            vertical = TwentSpacing.sm
        )
    )
}

/**
 * Page subtitle text
 */
@Composable
fun TwentSubtitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier.padding(
            horizontal = TwentSpacing.lg,
            vertical = TwentSpacing.sm
        )
    )
}

/**
 * Stat/metric display
 */
@Composable
fun TwentStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color = OrangePrimary
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TwentSpacing.xxs)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Empty state display
 */
@Composable
fun TwentEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TwentSpacing.xxl),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TwentSpacing.lg)
    ) {
        if (icon != null) {
            icon()
        }
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Loading indicator
 */
@Composable
fun TwentLoadingIndicator(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TwentSpacing.xxl),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TwentSpacing.lg)
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = OrangePrimary,
            strokeWidth = 3.dp
        )
        
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
