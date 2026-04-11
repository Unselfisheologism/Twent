package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.AppLogger

/**
 * Shizuku授权工具类 (STUB - Shizuku support removed)
 * All methods return false/not-available.
 */
class ShizukuAuthorizer {
    companion object {
        private const val TAG = "ShizukuAuthorizer"
        private val mainHandler = Handler(Looper.getMainLooper())

        private val stateChangeListeners = mutableListOf<() -> Unit>()

        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }

        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) { stateChangeListeners.remove(listener) }
        }

        private fun notifyStateChanged() {
            mainHandler.post {
                synchronized(stateChangeListeners) {
                    AppLogger.d(TAG, "Notifying ${stateChangeListeners.size} listeners about state change")
                    stateChangeListeners.forEach { it.invoke() }
                }
            }
        }

        fun isShizukuInstalled(context: Context): Boolean {
            AppLogger.i(TAG, "Shizuku support removed - always returning false")
            return false
        }

        fun getServiceErrorMessage(): String {
            return "Shizuku support has been removed"
        }

        fun getPermissionErrorMessage(): String {
            return "Shizuku support has been removed"
        }

        fun isShizukuServiceRunning(): Boolean {
            return false
        }

        fun hasShizukuPermission(): Boolean {
            return false
        }

        fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
            AppLogger.d(TAG, "Shizuku permission request - not available, returning false")
            onResult(false)
        }

        fun initialize() {
            AppLogger.d(TAG, "Shizuku initialization stub called - no-op")
            notifyStateChanged()
        }

        fun getShizukuStartupInstructions(context: Context): String {
            return "Shizuku support has been removed from this build."
        }
    }
}
