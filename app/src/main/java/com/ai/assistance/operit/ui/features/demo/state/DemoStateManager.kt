package com.ai.assistance.operit.ui.features.demo.state

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.data.mcp.plugins.MCPSharedSession
import com.ai.assistance.operit.R

private const val TAG = "DemoStateManager"

/**
 * Consolidated state management for the demo screens.
 * Handles state initialization and updates for accessibility-only mode.
 */
class DemoStateManager(private val context: Context, private val coroutineScope: CoroutineScope) {
    // Main UI state holder
    private val _uiState = MutableStateFlow(DemoScreenState())
    val uiState: StateFlow<DemoScreenState> = _uiState.asStateFlow()

    // NodeJS and Python environment state
    val isPnpmInstalled = mutableStateOf(false)
    val isPythonInstalled = mutableStateOf(false)
    val isNodejsPythonEnvironmentReady = mutableStateOf(false)

    init {
        coroutineScope.launch {
            refreshAllStates()
        }
    }

    /** Initialize state */
    fun initialize() {
        coroutineScope.launch {
            AppLogger.d(TAG, "Initializing state...")
            refreshStatusAsync()
        }
    }

    /** Refresh permissions and component status */
    fun refreshStatus() {
        coroutineScope.launch {
            refreshStatusAsync()
        }
    }

    /** Update UI state */
    fun updateOutputText(text: String) {
        // Kept for compatibility
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        _uiState.update { currentState ->
            currentState.copy(
                    resultDialogTitle = mutableStateOf(title),
                    resultDialogContent = mutableStateOf(content),
                    showResultDialogState = mutableStateOf(true)
            )
        }
    }

    fun hideResultDialog() {
        _uiState.update { currentState ->
            currentState.copy(showResultDialogState = mutableStateOf(false))
        }
    }

    /** Toggle UI visibility */
    fun toggleOperitTerminalWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                showOperitTerminalWizard = mutableStateOf(!currentState.showOperitTerminalWizard.value)
            )
        }
    }

    fun toggleAccessibilityWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                showAccessibilityWizard = mutableStateOf(!currentState.showAccessibilityWizard.value)
            )
        }
    }

    fun toggleAdbCommandExecutor() {
        _uiState.update { currentState ->
            currentState.copy(
                    showAdbCommandExecutor =
                            mutableStateOf(!currentState.showAdbCommandExecutor.value)
            )
        }
    }

    fun toggleSampleCommands() {
        _uiState.update { currentState ->
            currentState.copy(
                    showSampleCommands = mutableStateOf(!currentState.showSampleCommands.value)
            )
        }
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        _uiState.update { currentState -> currentState.copy(commandText = mutableStateOf(text)) }
    }

    fun updateResultText(text: String) {
        _uiState.update { currentState -> currentState.copy(resultText = mutableStateOf(text)) }
    }

    /** Clean up resources */
    fun cleanup() {
        // No listeners to remove in accessibility-only mode
    }

    /**
     * Refresh all states
     */
    suspend fun refreshAllStates() {
        refreshNodejsPythonEnvironment()
    }

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { currentState -> currentState.copy(isLoading = mutableStateOf(isLoading)) }
    }

    /** Initialize state asynchronously */
    suspend fun initializeAsync() {
        AppLogger.d(TAG, "Async initializing state...")
        refreshStatusAsync()
    }

    /** Refresh permissions and component status asynchronously */
    private suspend fun refreshStatusAsync() {
        _uiState.update { currentState -> currentState.copy(isRefreshing = mutableStateOf(true)) }

        try {
            // Refresh permissions and status
            refreshPermissionsAndStatus(
                    context = context,
                    updateOperitTerminalInstalled = { _uiState.value.isOperitTerminalInstalled.value = it },
                    updateStoragePermission = { _uiState.value.hasStoragePermission.value = it },
                    updateLocationPermission = { _uiState.value.hasLocationPermission.value = it },
                    updateOverlayPermission = { _uiState.value.hasOverlayPermission.value = it },
                    updateBatteryOptimizationExemption = {
                        _uiState.value.hasBatteryOptimizationExemption.value = it
                    },
                    updateAccessibilityServiceEnabled = {
                        _uiState.value.hasAccessibilityServiceEnabled.value = it
                    }
            )

            // Check NodeJS and Python environment status
            refreshNodejsPythonEnvironment()

            // Delay to ensure UI can refresh
            delay(300)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing permission states: ${e.message}", e)
        } finally {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = mutableStateOf(false))
            }
        }
    }

    /**
     * Check NodeJS and Python environment status
     */
    suspend fun refreshNodejsPythonEnvironment() {
        try {
            val sessionId = MCPSharedSession.getOrCreateSharedSession(context)
            if (sessionId == null) {
                isPnpmInstalled.value = false
                isPythonInstalled.value = false
                isNodejsPythonEnvironmentReady.value = false
                return
            }

            val terminal = Terminal.getInstance(context)

            // Check pnpm installation
            val pnpmResult = terminal.executeCommand(sessionId, "command -v pnpm")
            isPnpmInstalled.value = pnpmResult != null && pnpmResult.contains("pnpm")

            // Check python installation
            val pythonResult = terminal.executeCommand(sessionId, "command -v python")
            var hasPython = pythonResult != null && (pythonResult.contains("python") || pythonResult.contains("/python"))

            // If python doesn't exist, check python3
            if (!hasPython) {
                val python3Result = terminal.executeCommand(sessionId, "command -v python3")
                hasPython = python3Result != null && (python3Result.contains("python3") || python3Result.contains("/python3"))
            }

            // Check pip installation - only check if python exists
            var hasPip = false
            if (hasPython) {
                val pipResult = terminal.executeCommand(sessionId, "command -v pip")
                hasPip = pipResult != null && pipResult.contains("pip")

                if (!hasPip) {
                    val pip3Result = terminal.executeCommand(sessionId, "command -v pip3")
                    hasPip = pip3Result != null && pip3Result.contains("pip3")
                }
            }

            isPythonInstalled.value = hasPython && hasPip

            // Environment is ready only if both pnpm and python (with pip) are ready
            isNodejsPythonEnvironmentReady.value = isPnpmInstalled.value && isPythonInstalled.value

            AppLogger.d(TAG, "NodeJS environment check - pnpm: ${isPnpmInstalled.value}, python: $hasPython, pip: $hasPip, python env: ${isPythonInstalled.value}, overall ready: ${isNodejsPythonEnvironmentReady.value}")

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking NodeJS and Python environment", e)
            isPnpmInstalled.value = false
            isPythonInstalled.value = false
            isNodejsPythonEnvironmentReady.value = false
        }
    }
}

/** Refresh app permissions and component status */
suspend fun refreshPermissionsAndStatus(
    context: Context,
    updateOperitTerminalInstalled: (Boolean) -> Unit,
    updateStoragePermission: (Boolean) -> Unit,
    updateLocationPermission: (Boolean) -> Unit,
    updateOverlayPermission: (Boolean) -> Unit,
    updateBatteryOptimizationExemption: (Boolean) -> Unit,
    updateAccessibilityServiceEnabled: (Boolean) -> Unit
) {
    AppLogger.d(TAG, "Refreshing app permission status...")

    // Check NodeJS and Python environment status (replaces OperitTerminal installation check)
    val isNodejsPythonEnvironmentReady = try {
        val sessionId = MCPSharedSession.getOrCreateSharedSession(context)
        if (sessionId != null) {
            val terminal = Terminal.getInstance(context)
            val pnpmResult = terminal.executeCommand(sessionId, "command -v pnpm")
            val isPnpmInstalled = pnpmResult != null && pnpmResult.contains("pnpm")

            val pythonResult = terminal.executeCommand(sessionId, "command -v python")
            var hasPython = pythonResult != null && (pythonResult.contains("python") || pythonResult.contains("/python"))

            if (!hasPython) {
                val python3Result = terminal.executeCommand(sessionId, "command -v python3")
                hasPython = python3Result != null && (python3Result.contains("python3") || python3Result.contains("/python3"))
            }

            var hasPip = false
            if (hasPython) {
                val pipResult = terminal.executeCommand(sessionId, "command -v pip")
                hasPip = pipResult != null && pipResult.contains("pip")

                if (!hasPip) {
                    val pip3Result = terminal.executeCommand(sessionId, "command -v pip3")
                    hasPip = pip3Result != null && pip3Result.contains("pip3")
                }
            }

            isPnpmInstalled && hasPython && hasPip
        } else {
            false
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error checking NodeJS and Python environment", e)
        false
    }
    updateOperitTerminalInstalled(isNodejsPythonEnvironmentReady)

    // Check storage permission
    val hasStoragePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    updateStoragePermission(hasStoragePermission)

    // Check location permission
    val hasLocationPermission =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    updateLocationPermission(hasLocationPermission)

    // Check overlay permission
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    updateOverlayPermission(hasOverlayPermission)

    // Check battery optimization exemption
    val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val hasBatteryOptimizationExemption =
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    updateBatteryOptimizationExemption(hasBatteryOptimizationExemption)

    // Check accessibility service status
    val hasAccessibilityServiceEnabled = UIHierarchyManager.isAccessibilityServiceEnabled(context)
    updateAccessibilityServiceEnabled(hasAccessibilityServiceEnabled)
}

/** Data class to hold all UI state */
data class DemoScreenState(
        // Permission states
        val isOperitTerminalInstalled: MutableState<Boolean> = mutableStateOf(false),
        val hasStoragePermission: MutableState<Boolean> = mutableStateOf(false),
        val hasOverlayPermission: MutableState<Boolean> = mutableStateOf(false),
        val hasBatteryOptimizationExemption: MutableState<Boolean> = mutableStateOf(false),
        val hasAccessibilityServiceEnabled: MutableState<Boolean> = mutableStateOf(false),
        val hasLocationPermission: MutableState<Boolean> = mutableStateOf(false),

        // UI states
        val isRefreshing: MutableState<Boolean> = mutableStateOf(false),
        val showHelp: MutableState<Boolean> = mutableStateOf(false),
        val permissionErrorMessage: MutableState<String?> = mutableStateOf(null),
        val showSampleCommands: MutableState<Boolean> = mutableStateOf(false),
        val showAdbCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showOperitTerminalWizard: MutableState<Boolean> = mutableStateOf(false),
        val showAccessibilityWizard: MutableState<Boolean> = mutableStateOf(false),
        val showResultDialogState: MutableState<Boolean> = mutableStateOf(false),

        // Command execution
        val commandText: MutableState<String> = mutableStateOf(""),
        val resultText: MutableState<String> = mutableStateOf(""),
        val resultDialogTitle: MutableState<String> = mutableStateOf(""),
        val resultDialogContent: MutableState<String> = mutableStateOf(""),
        val isLoading: MutableState<Boolean> = mutableStateOf(false)
)

// Sample command lists that can be reused
fun getSampleAdbCommands(context: Context) =
        listOf(
                "getprop ro.build.version.release" to context.getString(R.string.demo_cmd_get_android_version),
                "pm list packages" to context.getString(R.string.demo_cmd_list_packages),
                "dumpsys battery" to context.getString(R.string.demo_cmd_check_battery),
                "settings list system" to context.getString(R.string.demo_cmd_list_settings),
                "am start -a android.intent.action.VIEW -d https://www.example.com" to context.getString(R.string.demo_cmd_open_webpage),
                "dumpsys activity activities" to context.getString(R.string.demo_cmd_list_activities),
                "service list" to context.getString(R.string.demo_cmd_list_services),
                "wm size" to context.getString(R.string.demo_cmd_check_resolution)
        )

// Predefined OperitTerminal commands
fun getOperitTerminalSampleCommands(context: Context) =
        listOf(
                "echo 'Hello OperitTerminal'" to context.getString(R.string.demo_cmd_echo_hello),
                "ls -la" to context.getString(R.string.demo_cmd_list_files),
                "whoami" to context.getString(R.string.demo_cmd_show_user),
                "apt update" to context.getString(R.string.demo_cmd_update_package_manager),
                "apt install python3" to context.getString(R.string.demo_cmd_install_python),
                "ip addr" to context.getString(R.string.demo_cmd_show_network)
        )
