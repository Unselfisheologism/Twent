package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenNavigationHandler
import com.ai.assistance.operit.ui.features.demo.components.PermissionLevelCard
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import com.ai.assistance.operit.ui.features.demo.wizards.AccessibilityWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.OperitTerminalWizardCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen(
        viewModel: ShizukuDemoViewModel =
                viewModel(
                        factory =
                                ShizukuDemoViewModel.Factory(
                                        LocalContext.current.applicationContext as
                                                android.app.Application
                                )
                ),
        navigateTo: ScreenNavigationHandler? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Location permission launcher
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineLocationGranted || coarseLocationGranted) {
                    scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) }
                }
            }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.setLoading(true)
        viewModel.initializeAsync(context)
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Loading indicator
        if (uiState.isLoading.value) {
            Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(context.getString(R.string.loading_app_state), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Permission management card - simplified to accessibility only
        PermissionLevelCard(
                hasStoragePermission = uiState.hasStoragePermission.value,
                hasOverlayPermission = uiState.hasOverlayPermission.value,
                hasBatteryOptimizationExemption = uiState.hasBatteryOptimizationExemption.value,
                hasAccessibilityServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                hasLocationPermission = uiState.hasLocationPermission.value,
                isOperitTerminalInstalled = uiState.isOperitTerminalInstalled.value,
                isRefreshing = uiState.isRefreshing.value,
                onRefresh = {
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshStatus(context)
                    }
                },
                onStoragePermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + context.packageName)
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_permission_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onOverlayPermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_overlay_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onBatteryOptimizationClick = {
                    try {
                        val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_battery_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onAccessibilityClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onLocationPermissionClick = {
                    locationPermissionLauncher.launch(
                            arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                    )
                },
                onOperitTerminalClick = {
                    viewModel.toggleOperitTerminalWizard()
                }
        )

        // Setup wizards area
        val needOperitTerminalSetupGuide = !viewModel.isNodejsPythonEnvironmentReady.value

        val needAccessibilitySetupGuide =
                !uiState.hasAccessibilityServiceEnabled.value

        val needSetupGuide = needOperitTerminalSetupGuide || needAccessibilitySetupGuide

        if (needSetupGuide) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = context.getString(R.string.setup_wizard_icon_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                        text = context.getString(R.string.setup_wizard),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Accessibility wizard card
            if (needAccessibilitySetupGuide) {
                AccessibilityWizardCard(
                    isServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                    showWizard = uiState.showAccessibilityWizard.value,
                    onToggleWizard = { viewModel.toggleAccessibilityWizard() },
                    onOpenAccessibilitySettings = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // NodeJS and Python environment setup wizard card
            if (needOperitTerminalSetupGuide) {
                OperitTerminalWizardCard(
                    isPnpmInstalled = viewModel.isPnpmInstalled.value,
                    isPipInstalled = viewModel.isPythonInstalled.value,
                    isEnvironmentReady = viewModel.isNodejsPythonEnvironmentReady.value,
                    showWizard = uiState.showOperitTerminalWizard.value,
                    onToggleWizard = { viewModel.toggleOperitTerminalWizard() },
                    onOpenTerminalScreen = {
                        navigateTo?.invoke(Screen.TerminalSetup)
                    }
                )
            }
        }
    }
}
