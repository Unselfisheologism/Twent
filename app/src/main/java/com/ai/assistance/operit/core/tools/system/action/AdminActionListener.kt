package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** 基于ACCESSIBILITY权限的UI操作监听器 */
class AdminActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "AdminActionListener"
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ACCESSIBILITY

    override suspend fun isAvailable(): Boolean = true

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        return ActionListener.PermissionStatus.granted()
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        onResult(true)
    }

    override fun isListening(): Boolean = isListening.get()

    override fun initialize() {
        AppLogger.d(TAG, "ACCESSIBILITY UI操作监听器初始化完成")
    }

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                if (isListening.get()) {
                    return@withContext ActionListener.ListeningResult.failure("Already listening")
                }

                actionCallback = onAction
                isListening.set(true)

                AppLogger.d(TAG, "开始ACCESSIBILITY权限级别的UI操作监听")

                startBasicEventMonitoring()

                return@withContext ActionListener.ListeningResult.success("ACCESSIBILITY UI listener started")
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动UI操作监听失败", e)
                isListening.set(false)
                return@withContext ActionListener.ListeningResult.failure("Failed to start listener: ${e.message}")
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.get()) {
                return@withContext true
            }

            isListening.set(false)
            actionCallback = null

            stopBasicEventMonitoring()

            AppLogger.d(TAG, "UI操作监听已停止")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止UI操作监听失败", e)
            return@withContext false
        }
    }

    private fun startBasicEventMonitoring() {
        AppLogger.d(TAG, "开始基本事件监控")
    }

    private fun stopBasicEventMonitoring() {
        AppLogger.d(TAG, "停止基本事件监控")
    }
}
