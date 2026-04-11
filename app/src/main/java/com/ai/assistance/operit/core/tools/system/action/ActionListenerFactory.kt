package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** UI操作监听器工厂类 始终返回ACCESSIBILITY级别的监听器实例 */
class ActionListenerFactory {
    companion object {
        private const val TAG = "ActionListenerFactory"

        // 缓存已创建的监听器实例
        private var listener: ActionListener? = null

        /**
         * 获取UI操作监听器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @param permissionLevel 所需权限级别（已忽略，始终返回ACCESSIBILITY）
         * @return ACCESSIBILITY级别的UI操作监听器
         */
        fun getListener(context: Context, permissionLevel: AndroidPermissionLevel): ActionListener {
            listener?.let {
                return it
            }

            val newListener = AccessibilityActionListener(context)
            newListener.initialize()
            listener = newListener

            AppLogger.d(TAG, "Created action listener for permission level: ACCESSIBILITY")
            return newListener
        }

        /**
         * 获取可用的UI操作监听器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @return ACCESSIBILITY级别的UI操作监听器，以及权限状态
         */
        suspend fun getHighestAvailableListener(
            context: Context
        ): Pair<ActionListener, ActionListener.PermissionStatus> {
            val listen = getListener(context, AndroidPermissionLevel.ACCESSIBILITY)
            val permStatus = listen.hasPermission()
            return Pair(listen, permStatus)
        }

        /**
         * 获取用户首选的UI操作监听器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @return ACCESSIBILITY级别的UI操作监听器
         */
        fun getUserPreferredListener(context: Context): ActionListener {
            return getListener(context, AndroidPermissionLevel.ACCESSIBILITY)
        }

        /**
         * 获取可用的最高权限UI操作监听器，用于向后兼容
         * @param context Android上下文
         * @return ACCESSIBILITY级别的UI操作监听器
         */
        suspend fun getHighestAvailableListenerLegacy(context: Context): ActionListener {
            return getListener(context, AndroidPermissionLevel.ACCESSIBILITY)
        }

        /**
         * 清除监听器缓存
         */
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            AppLogger.d(TAG, "Cleared action listener cache")
        }

        /**
         * 获取监听器及其权限状态
         * @param context Android上下文
         * @return ACCESSIBILITY级别监听器和权限状态的映射
         */
        suspend fun getAvailableListeners(
            context: Context
        ): Map<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>> {
            val result = mutableMapOf<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>>()
            val listen = getListener(context, AndroidPermissionLevel.ACCESSIBILITY)
            val status = listen.hasPermission()
            result[AndroidPermissionLevel.ACCESSIBILITY] = Pair(listen, status)
            return result
        }

        /**
         * 停止所有活跃的监听器
         * @return 停止操作是否成功
         */
        suspend fun stopAllListeners(): Boolean {
            val listen = listener
            if (listen != null && listen.isListening()) {
                val stopped = listen.stopListening()
                if (!stopped) {
                    AppLogger.w(TAG, "Failed to stop listener")
                    return false
                }
            }
            AppLogger.d(TAG, "All listeners stop result: true")
            return true
        }
    }
} 