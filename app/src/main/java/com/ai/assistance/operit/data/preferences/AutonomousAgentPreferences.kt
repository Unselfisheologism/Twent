package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferences for the autonomous agent.
 * Controls enabled state, scheduling, and content generation toggles.
 */
class AutonomousAgentPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "autonomous_agent"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_MORNING_HOUR = "morning_hour"
        private const val KEY_EVENING_HOUR = "evening_hour"
        private const val KEY_OBSERVE_INTERVAL = "observe_interval"

        private const val KEY_GEN_SOCIAL = "gen_social"
        private const val KEY_GEN_NEWS = "gen_news"
        private const val KEY_GEN_FOLLOWUP = "gen_followup"
        private const val KEY_GEN_AUTOMATION = "gen_automation"

        private const val KEY_OBSERVE_APP_USAGE = "observe_app_usage"
        private const val KEY_OBSERVE_WORKFLOWS = "observe_workflows"
        private const val KEY_OBSERVE_OVERLAY = "observe_overlay"

        // Defaults
        private const val DEFAULT_MORNING_HOUR = 9
        private const val DEFAULT_EVENING_HOUR = 20
        private const val DEFAULT_OBSERVE_INTERVAL = 120 // minutes (2 hours)
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var morningHour: Int
        get() = prefs.getInt(KEY_MORNING_HOUR, DEFAULT_MORNING_HOUR)
        set(v) = prefs.edit().putInt(KEY_MORNING_HOUR, v.coerceIn(0, 23)).apply()

    var eveningHour: Int
        get() = prefs.getInt(KEY_EVENING_HOUR, DEFAULT_EVENING_HOUR)
        set(v) = prefs.edit().putInt(KEY_EVENING_HOUR, v.coerceIn(0, 23)).apply()

    var observeIntervalMinutes: Int
        get() = prefs.getInt(KEY_OBSERVE_INTERVAL, DEFAULT_OBSERVE_INTERVAL)
        set(v) = prefs.edit().putInt(KEY_OBSERVE_INTERVAL, v.coerceIn(15, 480)).apply()

    // Content generation toggles
    var generateSocialPosts: Boolean
        get() = prefs.getBoolean(KEY_GEN_SOCIAL, true)
        set(v) = prefs.edit().putBoolean(KEY_GEN_SOCIAL, v).apply()

    var generateNewsDigest: Boolean
        get() = prefs.getBoolean(KEY_GEN_NEWS, true)
        set(v) = prefs.edit().putBoolean(KEY_GEN_NEWS, v).apply()

    var generateFollowups: Boolean
        get() = prefs.getBoolean(KEY_GEN_FOLLOWUP, true)
        set(v) = prefs.edit().putBoolean(KEY_GEN_FOLLOWUP, v).apply()

    var generateAutomationIdeas: Boolean
        get() = prefs.getBoolean(KEY_GEN_AUTOMATION, true)
        set(v) = prefs.edit().putBoolean(KEY_GEN_AUTOMATION, v).apply()

    // Observation source toggles
    var observeAppUsage: Boolean
        get() = prefs.getBoolean(KEY_OBSERVE_APP_USAGE, false)
        set(v) = prefs.edit().putBoolean(KEY_OBSERVE_APP_USAGE, v).apply()

    var observeWorkflows: Boolean
        get() = prefs.getBoolean(KEY_OBSERVE_WORKFLOWS, true)
        set(v) = prefs.edit().putBoolean(KEY_OBSERVE_WORKFLOWS, v).apply()

    var observeOverlay: Boolean
        get() = prefs.getBoolean(KEY_OBSERVE_OVERLAY, true)
        set(v) = prefs.edit().putBoolean(KEY_OBSERVE_OVERLAY, v).apply()

    fun reset() {
        prefs.edit().clear().apply()
    }
}