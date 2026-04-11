package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Root权限授权管理器 (STUB - Root support removed)
 * All methods return false/not-available.
 */
object RootAuthorizer {
    private const val TAG = "RootAuthorizer"

    private val stateChangeListeners = CopyOnWriteArrayList<() -> Unit>()

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    private val _hasRootAccess = MutableStateFlow(false)
    val hasRootAccess: StateFlow<Boolean> = _hasRootAccess.asStateFlow()

    private var useExecForCommands = false

    fun initialize(context: Context) {
        AppLogger.d(TAG, "RootAuthorizer initialization stub - no-op")
        _isRooted.value = false
        _hasRootAccess.value = false
    }

    fun checkRootStatus(context: Context): Boolean {
        AppLogger.d(TAG, "Root status check stub - returning false")
        _isRooted.value = false
        _hasRootAccess.value = false
        notifyStateChanged()
        return false
    }

    fun isDeviceRooted(): Boolean {
        return false
    }

    private fun checkKernelSu(): Boolean {
        return false
    }

    private fun checkExecSuAccess(): Boolean {
        return false
    }

    fun requestRootPermission(onResult: (Boolean) -> Unit) {
        AppLogger.d(TAG, "Root permission request stub - returning false")
        _hasRootAccess.value = false
        _isRooted.value = false
        notifyStateChanged()
        onResult(false)
    }

    suspend fun executeRootCommand(command: String, context: Context): Pair<Boolean, String> {
        return Pair(false, "Root support has been removed from this build")
    }

    fun addStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.add(listener)
    }

    fun removeStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.remove(listener)
    }

    private fun notifyStateChanged() {
        stateChangeListeners.forEach { it.invoke() }
    }
}
