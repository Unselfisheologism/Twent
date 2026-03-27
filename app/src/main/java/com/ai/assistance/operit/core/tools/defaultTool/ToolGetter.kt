package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.*
import com.ai.assistance.operit.core.tools.defaultTool.admin.*
import com.ai.assistance.operit.core.tools.defaultTool.debugger.*
import com.ai.assistance.operit.core.tools.defaultTool.root.*
import com.ai.assistance.operit.core.tools.defaultTool.standard.*
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.AppLogger

/** 工具获取器 - 根据首选权限级别获取对应的工具实现 如果特定权限级别下没有对应工具实现，则回退到标准权限级别的工具 */
object ToolGetter {

    /**
     * 获取文件系统工具
     * @param context 应用上下文
     * @return 根据首选权限级别的文件系统工具实现
     */
    fun getFileSystemTools(context: Context): StandardFileSystemTools {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootFileSystemTools(context)
            AndroidPermissionLevel.ADMIN -> AdminFileSystemTools(context)
            AndroidPermissionLevel.DEBUGGER -> DebuggerFileSystemTools(context)
            AndroidPermissionLevel.ACCESSIBILITY -> AccessibilityFileSystemTools(context)
            AndroidPermissionLevel.STANDARD -> StandardFileSystemTools(context)
            null -> StandardFileSystemTools(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取Shell工具执行器
     * @param context 应用上下文
     * @return 根据首选权限级别的Shell工具执行器实现
     */
    fun getShellToolExecutor(context: Context): StandardShellToolExecutor {
        return StandardShellToolExecutor(context)
    }

    /**
     * 获取UI工具
     * @param context 应用上下文
     * @return 根据实际Android权限级别的UI工具实现（优先检查系统级无障碍权限）
     */
    fun getUITools(context: Context): StandardUITools {
        // 优先检查系统级无障碍服务是否已启用并连接
        // 这比检查内部偏好设置更可靠，因为用户可能在系统设置中直接授予权限
        val isAccessibilityServiceEnabled = UIHierarchyManager.isAccessibilityServiceEnabled(context)
        AppLogger.d("ToolGetter", "System accessibility service enabled: $isAccessibilityServiceEnabled")
        
        // 如果系统级无障碍服务已启用，直接使用AccessibilityUITools
        if (isAccessibilityServiceEnabled) {
            AppLogger.d("ToolGetter", "Using AccessibilityUITools (system permission granted)")
            return AccessibilityUITools(context)
        }
        
        // 如果系统级无障碍服务未启用，检查内部偏好设置
        val level = androidPermissionPreferences.getPreferredPermissionLevel()
        AppLogger.d("ToolGetter", "getUITools: internal preference level = $level")
        
        return when (level) {
            AndroidPermissionLevel.ROOT -> RootUITools(context).also { AppLogger.d("ToolGetter", "Using RootUITools") }
            AndroidPermissionLevel.ADMIN -> AdminUITools(context).also { AppLogger.d("ToolGetter", "Using AdminUITools") }
            AndroidPermissionLevel.DEBUGGER -> DebuggerUITools(context).also { AppLogger.d("ToolGetter", "Using DebuggerUITools") }
            AndroidPermissionLevel.ACCESSIBILITY -> {
                // 如果偏好设置为ACCESSIBILITY但系统服务未启用，给出提示
                AppLogger.w("ToolGetter", "ACCESSIBILITY level set but service not enabled - falling back to Standard")
                StandardUITools(context)
            }
            AndroidPermissionLevel.STANDARD -> StandardUITools(context).also { AppLogger.d("ToolGetter", "Using StandardUITools") }
            null -> StandardUITools(context).also { AppLogger.d("ToolGetter", "Using StandardUITools (null fallback)") }
        }
    }

    /**
     * 获取系统操作工具
     * @param context 应用上下文
     * @return 根据首选权限级别的系统操作工具实现
     */
    fun getSystemOperationTools(context: Context): StandardSystemOperationTools {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootSystemOperationTools(context)
            AndroidPermissionLevel.ADMIN -> AdminSystemOperationTools(context)
            AndroidPermissionLevel.DEBUGGER -> DebuggerSystemOperationTools(context)
            AndroidPermissionLevel.ACCESSIBILITY -> AccessibilitySystemOperationTools(context)
            AndroidPermissionLevel.STANDARD -> StandardSystemOperationTools(context)
            null -> StandardSystemOperationTools(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取设备信息工具执行器
     * @param context 应用上下文
     * @return 根据首选权限级别的设备信息工具执行器实现
     */
    fun getDeviceInfoToolExecutor(context: Context): StandardDeviceInfoToolExecutor {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.ADMIN -> AdminDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.DEBUGGER -> DebuggerDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.ACCESSIBILITY -> AccessibilityDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.STANDARD -> StandardDeviceInfoToolExecutor(context)
            null -> StandardDeviceInfoToolExecutor(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取HTTP工具
     * @param context 应用上下文
     * @return HTTP工具实现（只有标准版本）
     */
    fun getHttpTools(context: Context): StandardHttpTools {
        return StandardHttpTools(context)
    }

    /**
     * 获取Web访问工具
     * @param context 应用上下文
     * @return Web访问工具实现（只有标准版本）
     */
    fun getWebVisitTool(context: Context): StandardWebVisitTool {
        return StandardWebVisitTool(context)
    }

    /**
     * 获取Intent工具执行器
     * @param context 应用上下文
     * @return Intent工具执行器实现（只有标准版本）
     */
    fun getIntentToolExecutor(context: Context): StandardIntentToolExecutor {
        return StandardIntentToolExecutor(context)
    }

    /**
     * 获取发送广播工具执行器
     * @param context 应用上下文
     * @return 发送广播工具执行器实现（只有标准版本）
     */
    fun getSendBroadcastToolExecutor(context: Context): StandardSendBroadcastToolExecutor {
        return StandardSendBroadcastToolExecutor(context)
    }

    /**
     * 获取终端命令执行器
     * @param context 应用上下文
     * @return 终端命令执行器实现（只有标准版本）
     */
    fun getTerminalCommandExecutor(context: Context): StandardTerminalCommandExecutor {
        return StandardTerminalCommandExecutor(context)
    }

    /**
     * 获取内存查询工具执行器
     * @param context 应用上下文
     * @return 内存查询工具执行器实现（只有标准版本）
     */
    fun getMemoryQueryToolExecutor(context: Context): MemoryQueryToolExecutor {
        return MemoryQueryToolExecutor(context)
    }

    /**
     * 获取FFmpeg工具执行器
     * @param context 应用上下文
     * @return FFmpeg工具执行器实现（只有标准版本）
     */
    fun getFFmpegToolExecutor(context: Context): StandardFFmpegToolExecutor {
        return StandardFFmpegToolExecutor(context)
    }


    /**
     * 获取FFmpeg信息工具执行器
     * @return FFmpeg信息工具执行器实现（只有标准版本）
     */
    fun getFFmpegInfoToolExecutor(): StandardFFmpegInfoToolExecutor {
        return StandardFFmpegInfoToolExecutor()
    }

    /**
     * 获取FFmpeg转换工具执行器
     * @param context 应用上下文
     * @return FFmpeg转换工具执行器实现（只有标准版本）
     */
    fun getFFmpegConvertToolExecutor(context: Context): StandardFFmpegConvertToolExecutor {
        return StandardFFmpegConvertToolExecutor(context)
    }

    /**
     * 获取计算器
     * @return 计算器实现（只有标准版本）
     */
    fun getCalculator() = StandardCalculator

    /**
     * 获取工作流工具
     * @param context 应用上下文
     * @return 工作流工具实现（只有标准版本）
     */
    fun getWorkflowTools(context: Context): StandardWorkflowTools {
        return StandardWorkflowTools(context)
    }

    /**
     * 获取对话管理工具
     * @param context 应用上下文
     * @return 对话管理工具实现（只有标准版本）
     */
    fun getChatManagerTool(context: Context): StandardChatManagerTool {
        return StandardChatManagerTool(context)
    }

    /**
     * 获取SSH远程连接工具
     * @param context 应用上下文
     * @return SSH远程连接工具实现（独立于终端）
     */
    fun getSSHRemoteConnectionTools(context: Context): SSHRemoteConnectionTools {
        return SSHRemoteConnectionTools(context)
    }

    /**
     * 获取文本转语音工具执行器
     * @param context 应用上下文
     * @return 文本转语音工具执行器实现（只有标准版本）
     */
    fun getTextToSpeechToolExecutor(context: Context): StandardTextToSpeechToolExecutor {
        return StandardTextToSpeechToolExecutor(context)
    }

    /**
     * 获取语音转文本工具执行器
     * @param context 应用上下文
     * @return 语音转文本工具执行器实现（只有标准版本）
     */
    fun getSpeechToTextToolExecutor(context: Context): StandardSpeechToTextToolExecutor {
        return StandardSpeechToTextToolExecutor(context)
    }
}
