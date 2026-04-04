package com.ai.assistance.operit.voice.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.voice.ConversationalAgentService
import com.ai.assistance.operit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EnhancedWakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "EnhancedWakeWordServiceChannel"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d("EnhancedWakeWordService", "Service onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EnhancedWakeWordService", "Service starting...")
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("EnhancedWakeWordService", "RECORD_AUDIO permission not granted.")
            Toast.makeText(this, "Microphone permission required for wake word", Toast.LENGTH_LONG).show()
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        
        createNotificationChannel()

        val notificationIntent = Intent(this, com.ai.assistance.operit.ui.main.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Operit Wake Word")
            .setContentText("Listening for wake word...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        try {
            startForeground(1338, notification)
        } catch (e: SecurityException) {
            Log.e("EnhancedWakeWordService", "Failed to start foreground service: ${e.message}")
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d("EnhancedWakeWordService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Enhanced Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
