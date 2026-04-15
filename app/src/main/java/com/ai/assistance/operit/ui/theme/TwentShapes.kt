package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Twent AI Design System Shapes
 * Based on reference images: 24dp cards, 16dp buttons, 12dp small elements
 */

val TwentShapes = Shapes(
    // Extra small - for chips, badges (12dp from reference)
    extraSmall = RoundedCornerShape(12.dp),
    
    // Small - for buttons, small cards (16dp from reference)
    small = RoundedCornerShape(16.dp),
    
    // Medium - for cards, dialogs (24dp from reference)
    medium = RoundedCornerShape(24.dp),
    
    // Large - for large cards, sheets (24dp from reference)
    large = RoundedCornerShape(24.dp),
    
    // Extra large - for full-screen elements (24dp from reference)
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Custom shapes for specific components
 * Based on reference images
 */
object TwentComponentShapes {
    // Navigation drawer - 24dp top corners (from reference)
    val drawerShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    
    // Cards in grid layouts - 24dp (from reference)
    val cardShape = RoundedCornerShape(24.dp)
    
    // Large feature cards - 24dp (from reference)
    val featureCardShape = RoundedCornerShape(24.dp)
    
    // Buttons - 16dp (from reference)
    val buttonShape = RoundedCornerShape(16.dp)
    
    // Pill buttons (fully rounded) - 50 (from reference)
    val pillShape = RoundedCornerShape(50)
    
    // Input fields - 8dp (from reference)
    val inputShape = RoundedCornerShape(8.dp)
    
    // Bottom sheets - 24dp top corners (from reference)
    val bottomSheetShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp
    )
    
    // Dialogs - 24dp (from reference)
    val dialogShape = RoundedCornerShape(24.dp)
    
    // Chips/tags - 12dp (from reference)
    val chipShape = RoundedCornerShape(12.dp)
    
    // Progress indicators - 4dp (from reference)
    val progressShape = RoundedCornerShape(4.dp)
    
    // Icon containers - 14dp (from reference)
    val iconContainerShape = RoundedCornerShape(14.dp)
    
    // Small badges - 8dp (from reference)
    val badgeShape = RoundedCornerShape(8.dp)
}

/**
 * Spacing scale based on reference images
 * 24dp main, 16dp internal, 8dp small
 */
object TwentSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
}

/**
 * Component dimensions based on reference images
 */
object TwentDimensions {
    // Standard heights
    val buttonHeight = 48.dp  // From reference
    val inputHeight = 56.dp   // From reference
    val toolbarHeight = 64.dp // From reference
    val bottomBarHeight = 80.dp // From reference
    
    // Icon sizes
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXLarge = 48.dp // From reference
    
    // Card sizes
    val cardMinWidth = 160.dp
    val cardMaxWidth = 340.dp
    
    // Avatar sizes
    val avatarSmall = 32.dp
    val avatarMedium = 40.dp
    val avatarLarge = 56.dp // From reference
    
    // Touch targets (minimum 48dp for accessibility)
    val touchTarget = 48.dp
    
    // Navigation item height - 56dp (from reference)
    val navItemHeight = 56.dp
    
    // Section header height - 48dp (from reference)
    val sectionHeaderHeight = 48.dp
}

/**
 * Elevation values based on reference images
 */
object TwentElevation {
    val none = 0.dp
    val small = 2.dp  // From reference
    val medium = 4.dp // From reference
    val large = 8.dp  // From reference
    val xLarge = 16.dp
}
