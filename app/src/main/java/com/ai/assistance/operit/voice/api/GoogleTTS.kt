package com.ai.assistance.operit.voice.api

import android.util.Log

enum class TTSVoice(val displayName: String, val voiceName: String, val description: String) {
    DEFAULT("Default", "default", "System default voice.")
}

object GoogleTts {
    const val apiKey = ""
    private const val API_URL = ""

    suspend fun synthesize(text: String): ByteArray {
        Log.w("GoogleTts", "Google TTS not configured. Use native TTS instead.")
        throw Exception("Google TTS not configured")
    }

    suspend fun synthesize(text: String, voice: TTSVoice): ByteArray {
        Log.w("GoogleTts", "Google TTS not configured. Use native TTS instead.")
        throw Exception("Google TTS not configured")
    }

    fun getAvailableVoices(): List<TTSVoice> = emptyList()
}
