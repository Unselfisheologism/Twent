package com.ai.assistance.operit.services.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.services.BlurrAssistantService

/**
 * Operit 语音交互服务
 * 
 * 这是让 Operit 能够被 Android 系统识别为数字助理应用的核心服务。
 * 当用户长按 Home 键或触发其他助手调用方式时，系统会启动这个服务。
 */
class OperitVoiceInteractionService : VoiceInteractionService() {
    
    companion object {
        private const val TAG = "OperitVoiceInteraction"
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "VoiceInteractionService created")
    }
    
    /**
     * 当服务准备就绪时调用
     * 这里可以进行一些初始化操作
     */
    override fun onReady() {
        super.onReady()
        AppLogger.d(TAG, "VoiceInteractionService ready")
    }
    
    /**
     * 系统请求启动识别会话时调用
     * 这里应该返回 true 表示我们可以处理这个请求
     */
    override fun onGetSupportedVoiceActions(voiceActions: MutableSet<String>): MutableSet<String> {
        AppLogger.d(TAG, "onGetSupportedVoiceActions: $voiceActions")
        return super.onGetSupportedVoiceActions(voiceActions)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand received")
        
        startBlurrAssistantService()
        
        return START_NOT_STICKY
    }

    private fun startBlurrAssistantService() {
        try {
            val serviceIntent = Intent(this, BlurrAssistantService::class.java).apply {
                action = BlurrAssistantService.ACTION_START
                putExtra(BlurrAssistantService.EXTRA_TASK, "Help me automate the current screen")
                putExtra(BlurrAssistantService.EXTRA_MAX_STEPS, 100)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            AppLogger.d(TAG, "BlurrAssistantService started from VoiceInteractionService")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start BlurrAssistantService", e)
        }
    }
    
    override fun onShutdown() {
        AppLogger.d(TAG, "VoiceInteractionService shutting down")
        super.onShutdown()
    }
    
    override fun onDestroy() {
        AppLogger.d(TAG, "VoiceInteractionService destroyed")
        super.onDestroy()
    }
}

