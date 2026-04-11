package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** Shell执行器工厂类 始终返回ACCESSIBILITY级别的执行器实例 */
class ShellExecutorFactory {
    companion object {
        private const val TAG = "ShellExecutorFactory"

        // 缓存已创建的执行器实例
        private var executor: ShellExecutor? = null

        /**
         * 获取Shell执行器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @param permissionLevel 所需权限级别（已忽略，始终返回ACCESSIBILITY）
         * @return ACCESSIBILITY级别的Shell执行器
         */
        fun getExecutor(context: Context, permissionLevel: AndroidPermissionLevel): ShellExecutor {
            executor?.let {
                return it
            }

            val newExecutor = AccessibilityShellExecutor(context)
            newExecutor.initialize()
            executor = newExecutor

            return newExecutor
        }

        /**
         * 获取可用的Shell执行器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @return ACCESSIBILITY级别的Shell执行器，以及权限状态
         */
        fun getHighestAvailableExecutor(
                context: Context
        ): Pair<ShellExecutor, ShellExecutor.PermissionStatus> {
            val exec = getExecutor(context, AndroidPermissionLevel.ACCESSIBILITY)
            val permStatus = exec.hasPermission()
            return Pair(exec, permStatus)
        }

        /**
         * 获取用户首选的Shell执行器（始终返回ACCESSIBILITY级别）
         * @param context Android上下文
         * @return ACCESSIBILITY级别的Shell执行器
         */
        fun getUserPreferredExecutor(context: Context): ShellExecutor {
            return getExecutor(context, AndroidPermissionLevel.ACCESSIBILITY)
        }

        /**
         * 获取可用的最高权限Shell执行器，用于向后兼容
         * @param context Android上下文
         * @return ACCESSIBILITY级别的Shell执行器
         */
        fun getHighestAvailableExecutorLegacy(context: Context): ShellExecutor {
            return getExecutor(context, AndroidPermissionLevel.ACCESSIBILITY)
        }

        /**
         * 清除执行器缓存
         */
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            AppLogger.d(TAG, "Cleared executor cache")
        }

        /**
         * 获取执行器及其权限状态
         * @param context Android上下文
         * @return ACCESSIBILITY级别执行器和权限状态的映射
         */
        fun getAvailableExecutors(
                context: Context
        ): Map<AndroidPermissionLevel, Pair<ShellExecutor, ShellExecutor.PermissionStatus>> {
            val result = mutableMapOf<AndroidPermissionLevel, Pair<ShellExecutor, ShellExecutor.PermissionStatus>>()
            val exec = getExecutor(context, AndroidPermissionLevel.ACCESSIBILITY)
            val status = exec.hasPermission()
            result[AndroidPermissionLevel.ACCESSIBILITY] = Pair(exec, status)
            return result
        }
    }
}
