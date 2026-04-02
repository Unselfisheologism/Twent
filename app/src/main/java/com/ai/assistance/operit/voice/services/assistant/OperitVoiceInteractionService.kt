package com.ai.assistance.operit.services.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.voice.ConversationalAgentService

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
    
    override fun onReady() {
        super.onReady()
        AppLogger.d(TAG, "VoiceInteractionService ready")
    }
    
    override fun onGetSupportedVoiceActions(voiceActions: MutableSet<String>): MutableSet<String> {
        AppLogger.d(TAG, "onGetSupportedVoiceActions: $voiceActions")
        return super.onGetSupportedVoiceActions(voiceActions)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand received")
        
        startConversationalAgent()
        
        return START_NOT_STICKY
    }

    private fun startConversationalAgent() {
        try {
            val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                action = "com.ai.assistance.operit.voice.ACTION_START_FROM_VOICE_INTERACTION"
                putExtra("source", "voice_interaction_service")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            AppLogger.d(TAG, "ConversationalAgentService started from VoiceInteractionService")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ConversationalAgentService", e)
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
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "VoiceInteractionService created")
    }
    
    override fun onReady() {
        super.onReady()
        AppLogger.d(TAG, "VoiceInteractionService ready")
    }
    
    override fun onGetSupportedVoiceActions(voiceActions: MutableSet<String>): MutableSet<String> {
        AppLogger.d(TAG, "onGetSupportedVoiceActions: $voiceActions")
        return super.onGetSupportedVoiceActions(voiceActions)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand received")
        
        startConversationalAgent()
        
        return START_NOT_STICKY
    }

    private fun startConversationalAgent() {
        try {
            val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                action = "com.ai.assistance.operit.voice.ACTION_START_FROM_VOICE_INTERACTION"
                putExtra("source", "voice_interaction_service")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            AppLogger.d(TAG, "ConversationalAgentService started from VoiceInteractionService")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ConversationalAgentService", e)
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

