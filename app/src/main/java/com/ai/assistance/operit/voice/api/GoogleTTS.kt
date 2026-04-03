package com.ai.assistance.operit.voice.api

/**
 * Stub for cloud-based Google TTS.
 * Cloud TTS has been removed. Use native Android TTS via TextToSpeech instead.
 */
object GoogleTts {
    /**
     * Always returns null - cloud TTS is disabled.
     * Callers should fall back to native Android TTS.
     */
    suspend fun synthesize(text: String, voice: TTSVoice): ByteArray? {
        return null
    }
}
