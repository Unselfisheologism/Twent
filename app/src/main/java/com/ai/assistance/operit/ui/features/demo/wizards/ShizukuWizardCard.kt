package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * Shizuku Wizard Card - STUB
 * This card has been deprecated. Shizuku is no longer supported.
 * Kept as an empty composable to prevent build errors from existing references.
 */
@Deprecated("Shizuku is no longer supported. This composable is a no-op.")
@Composable
fun ShizukuWizardCard(
        isShizukuInstalled: Boolean = false,
        isShizukuRunning: Boolean = false,
        hasShizukuPermission: Boolean = false,
        showWizard: Boolean = false,
        onToggleWizard: (Boolean) -> Unit = {},
        onInstallFromStore: () -> Unit = {},
        onInstallBundled: () -> Unit = {},
        onOpenShizuku: () -> Unit = {},
        onWatchTutorial: () -> Unit = {},
        onRequestPermission: () -> Unit = {},
        updateNeeded: Boolean = false,
        onUpdateShizuku: () -> Unit = {},
        installedVersion: String? = null,
        bundledVersion: String? = null
) {
    // No-op: Shizuku is no longer supported.
    // This stub exists to prevent build errors from existing references.
}
