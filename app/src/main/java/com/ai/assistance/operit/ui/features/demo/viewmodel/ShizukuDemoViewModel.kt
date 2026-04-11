package com.ai.assistance.operit.ui.features.demo.viewmodel

import android.app.Application
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.demo.state.DemoStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel for the ShizukuDemoScreen. Delegates most state management to DemoStateManager */
class ShizukuDemoViewModel(application: Application) : AndroidViewModel(application) {
    private val stateManager: DemoStateManager = DemoStateManager(application, viewModelScope)

    // AIToolHandler instance
    private val toolHandler: AIToolHandler = AIToolHandler.getInstance(application)

    // Expose state from the manager
    val uiState: StateFlow<com.ai.assistance.operit.ui.features.demo.state.DemoScreenState> =
            stateManager.uiState

    // Expose NodeJS and Python environment properties
    val isPnpmInstalled
        get() = stateManager.isPnpmInstalled
    val isPythonInstalled
        get() = stateManager.isPythonInstalled
    val isNodejsPythonEnvironmentReady
        get() = stateManager.isNodejsPythonEnvironmentReady

    /** Initialize the ViewModel with context data */
    fun initialize(context: Context) {
        stateManager.initialize()
    }

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        stateManager.setLoading(isLoading)
    }

    /** Initialize the ViewModel with context data (Async version) */
    fun initializeAsync(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stateManager.initializeAsync()
            } catch (e: Exception) {
                AppLogger.e("ShizukuDemoViewModel", "Initialization error: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) { setLoading(false) }
            }
        }
    }

    /** Refresh app status */
    fun refreshStatus(context: Context) {
        stateManager.refreshStatus()
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        stateManager.showResultDialog(title, content)
    }

    fun hideResultDialog() {
        stateManager.hideResultDialog()
    }

    /** UI visibility toggles */
    fun toggleOperitTerminalWizard() {
        stateManager.toggleOperitTerminalWizard()
    }

    fun toggleAccessibilityWizard() {
        stateManager.toggleAccessibilityWizard()
    }

    fun toggleAdbCommandExecutor() {
        stateManager.toggleAdbCommandExecutor()
    }

    fun toggleSampleCommands() {
        stateManager.toggleSampleCommands()
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        stateManager.updateCommandText(text)
    }

    /** Refresh all registered tools */
    fun refreshTools(context: Context) {
        AppLogger.d("ShizukuDemoViewModel", "Refreshing all registered tools")
        toolHandler.reset()
        toolHandler.registerDefaultTools()

        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.all_tools_reregistered), Toast.LENGTH_SHORT).show()
        }
    }

    /** Cleanup when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        stateManager.cleanup()
    }

    /** ViewModelFactory for creating ShizukuDemoViewModel with dependencies */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShizukuDemoViewModel::class.java)) {
                return ShizukuDemoViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
