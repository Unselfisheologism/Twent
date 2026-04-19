package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenNavigationHandler
import com.ai.assistance.operit.ui.features.demo.components.PermissionLevelCard
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import com.ai.assistance.operit.ui.features.demo.wizards.AccessibilityWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.OperitTerminalWizardCard
import com.ai.assistance.operit.ui.twent.components.*
import com.ai.assistance.operit.ui.twent.components.TwentSpacing
import com.ai.assistance.operit.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * COMPLETELY REDESIGNED Permissions Page
 * Different layouts, card positions, section groupings, component sizes, spacing, visual hierarchy
 */

// Permission data class for different organization
data class PermissionItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val onClick: () -> Unit
)

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

    // Remember state values to avoid snapshot issues during navigation
    val isLoading by remember { derivedStateOf { uiState.isLoading.value } }
    val isRefreshing by remember { derivedStateOf { uiState.isRefreshing.value } }
    val hasStoragePermission by remember { derivedStateOf { uiState.hasStoragePermission.value } }
    val hasOverlayPermission by remember { derivedStateOf { uiState.hasOverlayPermission.value } }
    val hasBatteryOptimizationExemption by remember { derivedStateOf { uiState.hasBatteryOptimizationExemption.value } }
    val hasAccessibilityServiceEnabled by remember { derivedStateOf { uiState.hasAccessibilityServiceEnabled.value } }
    val hasLocationPermission by remember { derivedStateOf { uiState.hasLocationPermission.value } }
    val isOperitTerminalInstalled by remember { derivedStateOf { uiState.isOperitTerminalInstalled.value } }
    val showAccessibilityWizard by remember { derivedStateOf { uiState.showAccessibilityWizard.value } }
    val showOperitTerminalWizard by remember { derivedStateOf { uiState.showOperitTerminalWizard.value } }

    // Granular terminal package status
    val isNodejsInstalled by remember { derivedStateOf { viewModel.isNodejsInstalled.value } }
    val isPnpmInstalledState by remember { derivedStateOf { viewModel.isPnpmInstalled.value } }
    val isPythonInstalledState by remember { derivedStateOf { viewModel.isPythonInstalled.value } }
    val isPipInstalledState by remember { derivedStateOf { viewModel.isPipInstalled.value } }

    // Accessibility guide dialog state
    var showA11yGuide by remember { mutableStateOf(false) }

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

    // REDESIGNED LAYOUT - Completely different from original
    TwentScreenPadding {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Different header style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Access Control",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Manage app permissions and access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Refresh button - Different style
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            viewModel.refreshStatus(context)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TwentSpacing.xxl))

            // Loading indicator - Different style
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TwentSpacing.md)
                    ) {
                        CircularProgressIndicator(
                            color = OrangePrimary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Checking permissions...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Permission cards - DIFFERENT layout (horizontal cards instead of vertical)
            if (!isLoading) {
                // Critical permissions section
                PermissionSection(
                    title = "Critical Permissions",
                    subtitle = "Required for core functionality"
                ) {
                    PermissionCard(
                        name = "Accessibility",
                        description = "UI automation",
                        icon = Icons.Outlined.AccessibilityNew,
                        isGranted = hasAccessibilityServiceEnabled,
                        isRequired = true,
                        onClick = {
                            showA11yGuide = true
                        }
                    )

                    PermissionCard(
                        name = "Storage",
                        description = "File access",
                        icon = Icons.Outlined.Folder,
                        isGranted = hasStoragePermission,
                        isRequired = true,
                        onClick = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                } else {
                                    val intent =
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                        )
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
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
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(TwentSpacing.xl))

                // Optional permissions section
                PermissionSection(
                    title = "Optional Permissions",
                    subtitle = "Enhance functionality"
                ) {
                    PermissionCard(
                        name = "Overlay",
                        description = "Floating windows",
                        icon = Icons.Outlined.Layers,
                        isGranted = hasOverlayPermission,
                        isRequired = false,
                        onClick = {
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
                        }
                    )

                    PermissionCard(
                        name = "Location",
                        description = "Location access",
                        icon = Icons.Outlined.LocationOn,
                        isGranted = hasLocationPermission,
                        isRequired = false,
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )

                    PermissionCard(
                        name = "Battery",
                        description = "Background",
                        icon = Icons.Outlined.BatteryChargingFull,
                        isGranted = hasBatteryOptimizationExemption,
                        isRequired = false,
                        onClick = {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:" + context.packageName)
                                    }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.cannot_open_battery_settings), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(TwentSpacing.xl))

                // Setup wizards section - DIFFERENT layout
                val needOperitTerminalSetupGuide = !viewModel.isNodejsPythonEnvironmentReady.value
                val needAccessibilitySetupGuide = !hasAccessibilityServiceEnabled

                if (needOperitTerminalSetupGuide || needAccessibilitySetupGuide) {
                    PermissionSection(
                        title = "Setup Wizards",
                        subtitle = "Configure required components"
                    ) {
                        if (needAccessibilitySetupGuide) {
                            WizardCard(
                                title = "Accessibility Setup",
                                description = "Configure accessibility service",
                                icon = Icons.Outlined.AccessibilityNew,
                                isExpanded = showAccessibilityWizard,
                                onToggle = { viewModel.toggleAccessibilityWizard() }
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(TwentSpacing.md)
                                ) {
                                    Text(
                                        text = "Enable accessibility service for UI automation. On Android 13+, this requires an extra \"Allow restricted settings\" step first.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    // Step-by-step instructions
                                    val steps = listOf(
                                        "Open Settings → Apps and find Twent",
                                        "Tap the ⋮ menu → \"Allow restricted settings\" (enter PIN)",
                                        "Come back here and tap \"Open Settings\" below",
                                        "Go to Installed apps → find Twent → toggle ON"
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(12.dp)
                                    ) {
                                        steps.forEachIndexed { i, step ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "${i + 1}.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = OrangePrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = step,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }

                                    // Supademo tutorial link
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.supademo.com/demo/cmo4mfzy702e2yd0jl0j1z1c8?preview=true"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Watch Video Tutorial", fontSize = 14.sp)
                                    }

                                    TwentButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        text = "Open Accessibility Settings",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (needOperitTerminalSetupGuide) {
                            WizardCard(
                                title = "Terminal Setup",
                                description = "Configure development environment",
                                icon = Icons.Outlined.Code,
                                isExpanded = showOperitTerminalWizard,
                                onToggle = { viewModel.toggleOperitTerminalWizard() }
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(TwentSpacing.md)
                                ) {
                                    Text(
                                        text = "Install Node.js and Python for terminal tools",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    // Package status indicators
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(12.dp)
                                    ) {
                                        PackageStatusRow("Node.js", isNodejsInstalled)
                                        PackageStatusRow("pnpm", isPnpmInstalledState)
                                        PackageStatusRow("Python", isPythonInstalledState)
                                        PackageStatusRow("pip", isPipInstalledState)
                                    }

                                    TwentButton(
                                        onClick = {
                                            navigateTo?.invoke(Screen.TerminalSetup)
                                        },
                                        text = if (isOperitTerminalInstalled) "Open Setup" else "Start Setup",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Accessibility guide dialog
            if (showA11yGuide) {
                AccessibilityGuideDialog(
                    onDismiss = { showA11yGuide = false },
                    onOpenSettings = {
                        showA11yGuide = false
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWatchDemo = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.supademo.com/demo/cmo4mfzy702e2yd0jl0j1z1c8?preview=true"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        // Different section header style
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md),
            modifier = Modifier.padding(bottom = TwentSpacing.md)
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OrangePrimary)
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        
        // Content
        Column(
            verticalArrangement = Arrangement.spacedBy(TwentSpacing.md),
            content = content
        )
    }
}

@Composable
private fun PermissionCard(
    name: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        },
        shadowElevation = if (isGranted) 0.dp else 2.dp,
        onClick = {
            isPressed = true
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
        ) {
            // Different icon style
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isGranted) {
                            SuccessGreen.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) SuccessGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Status indicator
            if (isRequired) {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) {
                        SuccessGreen.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = if (isGranted) "GRANTED" else "REQUIRED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) SuccessGreen else MaterialTheme.colorScheme.error,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardCard(
    title: String,
    description: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TwentSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OrangePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            // Expandable content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(TwentSpacing.lg))
                content()
            }
        }
    }
}

@Composable
private fun PackageStatusRow(
    name: String,
    isInstalled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isInstalled) Icons.Filled.CheckCircle else Icons.Filled.RemoveCircle,
            contentDescription = null,
            tint = if (isInstalled) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isInstalled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isInstalled) "Installed" else "Not installed",
            style = MaterialTheme.typography.labelSmall,
            color = if (isInstalled) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun AccessibilityGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onWatchDemo: () -> Unit
) {
    val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val oem = remember {
        val m = Build.MANUFACTURER.lowercase()
        when {
            m.contains("samsung") -> "Samsung"
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi"
            m.contains("oppo") -> "OPPO"
            m.contains("vivo") -> "vivo"
            m.contains("oneplus") -> "OnePlus"
            m.contains("huawei") || m.contains("honor") -> "Huawei"
            m.contains("realme") -> "Realme"
            m.contains("google") || m.contains("pixel") -> "Pixel"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAndroid13Plus) "Enable Accessibility ($oem)" else "Enable Accessibility",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isAndroid13Plus) {
                    // Phase A
                    Text(
                        "PHASE A — Allow Restricted Settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    val phaseASteps = listOf(
                        "Open your phone's Settings app",
                        "Go to Settings → Apps and find \"Twent\"",
                        "Tap the ⋮ menu (top-right) → \"Allow restricted settings\"",
                        "Enter your PIN/password to confirm"
                    )
                    phaseASteps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 1}.", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Phase B
                    Text(
                        "PHASE B — Enable Accessibility",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    val phaseBSteps = listOf(
                        "Come back here and tap \"Open Settings\" below",
                        "Tap \"Installed apps\" → find \"Twent\"",
                        "Tap the toggle switch to turn it ON",
                        "Tap \"Allow\" in the confirmation popup"
                    )
                    phaseBSteps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 5}.", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    // Simple flow for Android 12 and below
                    val steps = listOf(
                        "Tap \"Open Settings\" below",
                        "Find \"Installed apps\" → tap \"Twent\"",
                        "Tap the toggle switch to turn it ON",
                        "Tap \"Allow\" in the confirmation popup"
                    )
                    steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 1}.", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Supademo link
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .clickable { onWatchDemo() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Watch video tutorial",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Interactive step-by-step demo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}
