package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * Root Wizard Card - STUB
 * This card has been deprecated. Root permissions are no longer supported.
 * Kept as an empty composable to prevent build errors from existing references.
 */
@Deprecated("Root permissions are no longer supported. This composable is a no-op.")
@Composable
fun RootWizardCard(
        isDeviceRooted: Boolean = false,
        hasRootAccess: Boolean = false,
        showWizard: Boolean = false,
        onToggleWizard: () -> Unit = {},
        onRequestRoot: () -> Unit = {},
        onWatchTutorial: () -> Unit = {}
) {
    // No-op: Root permissions are no longer supported.
    // This stub exists to prevent build errors from existing references.
}
