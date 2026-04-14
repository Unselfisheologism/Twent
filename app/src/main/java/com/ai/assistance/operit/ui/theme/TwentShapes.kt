package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Twent AI Design System Shapes
 * Clean, rounded corners inspired by reference designs
 */

val TwentShapes = Shapes(
    // Extra small - for chips, badges
    extraSmall = RoundedCornerShape(8.dp),
    
    // Small - for buttons, small cards
    small = RoundedCornerShape(12.dp),
    
    // Medium - for cards, dialogs
    medium = RoundedCornerShape(16.dp),
    
    // Large - for large cards, sheets
    large = RoundedCornerShape(20.dp),
    
    // Extra large - for full-screen elements
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Custom shapes for specific components
 */
object TwentComponentShapes {
    // Navigation drawer
    val drawerShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    
    // Cards in grid layouts
    val cardShape = RoundedCornerShape(16.dp)
    
    // Large feature cards
    val featureCardShape = RoundedCornerShape(20.dp)
    
    // Buttons
    val buttonShape = RoundedCornerShape(12.dp)
    
    // Pill buttons (fully rounded)
    val pillShape = RoundedCornerShape(50)
    
    // Input fields
    val inputShape = RoundedCornerShape(12.dp)
    
    // Bottom sheets
    val bottomSheetShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp
    )
    
    // Dialogs
    val dialogShape = RoundedCornerShape(24.dp)
    
    // Chips/tags
    val chipShape = RoundedCornerShape(8.dp)
    
    // Progress indicators
    val progressShape = RoundedCornerShape(4.dp)
}

/**
 * Spacing scale based on 8pt grid
 */
object TwentSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * Component dimensions
 */
object TwentDimensions {
    // Standard heights
    val buttonHeight = 48.dp
    val inputHeight = 56.dp
    val toolbarHeight = 64.dp
    val bottomBarHeight = 80.dp
    
    // Icon sizes
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXLarge = 48.dp
    
    // Card sizes
    val cardMinWidth = 160.dp
    val cardMaxWidth = 340.dp
    
    // Avatar sizes
    val avatarSmall = 32.dp
    val avatarMedium = 40.dp
    val avatarLarge = 56.dp
    
    // Touch targets (minimum 48dp for accessibility)
    val touchTarget = 48.dp
}

/**
 * Elevation values
 */
object TwentElevation {
    val none = 0.dp
    val small = 2.dp
    val medium = 4.dp
    val large = 8.dp
    val xLarge = 16.dp
}
