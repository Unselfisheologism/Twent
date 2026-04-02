package com.ai.assistance.operit.services.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.voice.ConversationalAgentService

class OperitVoiceInteractionSessionService : VoiceInteractionSessionService() {
    
    companion object {
        private const val TAG = "OperitSessionService"
    }
    
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AppLogger.d(TAG, "Creating new voice interaction session")
        return OperitVoiceInteractionSession(this)
    }
    
    private class OperitVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
        
        companion object {
            private const val TAG = "OperitSession"
        }
        
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            AppLogger.d(TAG, "Session show requested with flags: $showFlags")
            startConversationalAgent()
            finish()
        }
        
        override fun onHide() {
            super.onHide()
            AppLogger.d(TAG, "Session hide requested")
            finish()
        }
        
        override fun onDestroy() {
            AppLogger.d(TAG, "Session destroyed")
            super.onDestroy()
        }
        
        private fun startConversationalAgent() {
            try {
                val intent = Intent(context, ConversationalAgentService::class.java).apply {
                    action = "com.ai.assistance.operit.voice.ACTION_START_FROM_VOICE_INTERACTION"
                    putExtra("source", "voice_interaction_session")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                AppLogger.d(TAG, "ConversationalAgentService started from voice interaction session")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start conversational agent service", e)
            }
        }
    }
}
