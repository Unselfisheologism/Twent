package com.ai.assistance.operit.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

class AssistEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAssistLaunch(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAssistLaunch(intent)
        finish()
    }

    private fun handleAssistLaunch(intent: Intent?) {
        Log.d("AssistEntryActivity", "Assistant invoked via ACTION_ASSIST, intent=$intent")

        if (!ConversationalAgentService.isRunning) {
            val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                action = "com.ai.assistance.operit.voice.ACTION_START_FROM_ASSIST"
                putExtra("source", "assist_gesture")
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            sendBroadcast(Intent("com.ai.assistance.operit.voice.ACTION_SHOW_OVERLAY"))
        }
    }
}
