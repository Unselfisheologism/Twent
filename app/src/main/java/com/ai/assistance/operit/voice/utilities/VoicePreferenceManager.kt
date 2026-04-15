package com.ai.assistance.operit.voice.utilities

import android.content.Context
import com.ai.assistance.operit.voice.api.TTSVoice

object VoicePreferenceManager {
    private const val PREFS_NAME = "OperitVoiceSettings"

    private const val KEY_SELECTED_VOICE = "selected_voice"

    fun getSelectedVoice(context: Context): TTSVoice {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Ensure this default also matches your intended default (CHIRP_PUCK)
        val selectedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, TTSVoice.CHIRP_LAOMEDEIA.name)

        return TTSVoice.valueOf(selectedVoiceName ?: TTSVoice.CHIRP_LAOMEDEIA.name)
    }

    fun saveSelectedVoice(context: Context, voice: TTSVoice) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_SELECTED_VOICE, voice.name)
            .apply()
    }
}