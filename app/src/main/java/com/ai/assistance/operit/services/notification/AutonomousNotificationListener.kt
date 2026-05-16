package com.ai.assistance.operit.services.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.SharedPreferences
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

class AutonomousNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AutonomousNotifListener"
        private const val PREFS_KEY = "autonomous_overlay_log"
        private const val MAX_ENTRIES = 50
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("autonomous_data", Context.MODE_PRIVATE)
        AppLogger.d(TAG, "AutonomousNotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            val entry = JSONObject().apply {
                put("package", sbn.packageName)
                put("text", (sbn.notification?.extras?.getCharSequence("android.text")?.toString() ?: "").take(100))
                put("timestamp", System.currentTimeMillis())
                put("type", "notification")
            }
            appendLog(entry)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error logging notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        // Optionally log removal but for observation we mostly care about what was posted
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.d(TAG, "AutonomousNotificationListener connected")
    }

    override fun onServiceDisconnected() {
        super.onServiceDisconnected()
        AppLogger.d(TAG, "AutonomousNotificationListener disconnected")
    }

    private fun appendLog(entry: JSONObject) {
        val current = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val arr = JSONArray(current)
        arr.put(entry)
        // Keep last MAX_ENTRIES
        while (arr.length() > MAX_ENTRIES) {
            arr.remove(0)
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }
}
