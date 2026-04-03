package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.voice.api.TTSVoice
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.overlay.OverlayPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class TTSManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsInitialized = CompletableDeferred<Unit>()

    private var audioTrack: AudioTrack? = null
    private var googleTtsPlaybackDeferred: CompletableDeferred<Unit>? = null

    var utteranceListener: ((isSpeaking: Boolean) -> Unit)? = null

    private var isDebugMode: Boolean = try {
        BuildConfig.SPEAK_INSTRUCTIONS
    } catch (e: Exception) {
        true
    }

    // --- NEW: Caching System ---
    private val cacheDir by lazy { File(context.cacheDir, "tts_cache") }
    private val cache = ConcurrentHashMap<String, CachedAudio>()
    private val accessOrder = LinkedBlockingDeque<String>()
    private val cacheMutex = Any()
    private val MAX_CACHE_SIZE = 100
    private val MAX_WORDS_FOR_CACHING = 10

    companion object {
        @Volatile private var INSTANCE: TTSManager? = null
        private const val SAMPLE_RATE = 24000

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        nativeTts = TextToSpeech(context, this)
        setupAudioTrack()
        initializeCache()
    }

    /**
     * Data class for cached audio entries
     */
    private data class CachedAudio(
        val text: String,
        val audioData: ByteArray,
        val voiceName: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CachedAudio
            return text == other.text && voiceName == other.voiceName
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + voiceName.hashCode()
            return result
        }
    }

    /**
     * Cloud-based TTS chunk playback - DISABLED. Use native TTS via speak() instead.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun speakChunk(chunk: String, selectedVoice: TTSVoice) {
        Log.w("TTSManager", "Cloud TTS is disabled. Using native TTS fallback.")
        isNativeTtsInitialized.await()
        nativeTts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, this.hashCode().toString())
    }
            loadCacheFromDisk()
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to initialize cache", e)
        }
    }

    /**
     * Generate a hash for the text and voice combination
     */
    private fun generateCacheKey(text: String, voice: TTSVoice): String {
        val combined = "${text.trim().lowercase()}_${voice.name}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if text should be cached (10 words or less)
     */
    private fun shouldCache(text: String): Boolean {
        val wordCount = text.trim().split(Regex("\\s+")).size
        return wordCount <= MAX_WORDS_FOR_CACHING
    }

    /**
     * Get cached audio data if available
     */
    private fun getCachedAudio(text: String, voice: TTSVoice): ByteArray? {
        if (!shouldCache(text)) return null
        
        val cacheKey = generateCacheKey(text, voice)
        synchronized(cacheMutex) {
            val cachedAudio = cache[cacheKey]
            if (cachedAudio != null) {
                // Update access order (move to end)
                accessOrder.remove(cacheKey)
                accessOrder.addLast(cacheKey)
                Log.d("TTSManager", "Cache hit for: ${text.take(50)}...")
                return cachedAudio.audioData
            }
        }
        return null
    }

    /**
     * Store audio data in cache
     */
    private fun cacheAudio(text: String, audioData: ByteArray, voice: TTSVoice) {
        if (!shouldCache(text)) return
        
        val cacheKey = generateCacheKey(text, voice)
        synchronized(cacheMutex) {
            // Remove if already exists
            cache.remove(cacheKey)
            accessOrder.remove(cacheKey)
            
            // Add new entry
            val cachedAudio = CachedAudio(text.trim(), audioData, voice.name)
            cache[cacheKey] = cachedAudio
            accessOrder.addLast(cacheKey)
            
            // Enforce cache size limit
            if (cache.size > MAX_CACHE_SIZE) {
                val oldestKey = accessOrder.removeFirst()
                cache.remove(oldestKey)
                deleteCacheFile(oldestKey)
            }
            
            // Save to disk
            saveCacheToDisk(cacheKey, cachedAudio)
            Log.d("TTSManager", "Cached audio for: ${text.take(50)}... (Cache size: ${cache.size})")
        }
    }

    /**
     * Save cached audio to disk
     */
    private fun saveCacheToDisk(cacheKey: String, cachedAudio: CachedAudio) {
        try {
            val file = File(cacheDir, cacheKey)
            file.writeBytes(cachedAudio.audioData)
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to save cache to disk", e)
        }
    }

    /**
     * Load cache from disk
     */
    private fun loadCacheFromDisk() {
        try {
            val files = cacheDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.length() > 0) {
                    val cacheKey = file.name
                    val audioData = file.readBytes()
                    // Note: We can't fully reconstruct CachedAudio without metadata
                    // This is a simplified version - in production you might want to store metadata
                    Log.d("TTSManager", "Loaded cached audio: $cacheKey")
                }
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to load cache from disk", e)
        }
    }

    /**
     * Delete cache file from disk
     */
    private fun deleteCacheFile(cacheKey: String) {
        try {
            val file = File(cacheDir, cacheKey)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to delete cache file", e)
        }
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        synchronized(cacheMutex) {
            cache.clear()
            accessOrder.clear()
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("TTSManager", "Failed to clear cache directory", e)
            }
        }
    }

    private fun setupAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                googleTtsPlaybackDeferred?.complete(Unit)
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        }, Handler(Looper.getMainLooper()))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { utteranceListener?.invoke(true) }
                override fun onDone(utteranceId: String?) {
                    utteranceListener?.invoke(false)
                }
                override fun onError(utteranceId: String?) {
                    utteranceListener?.invoke(false)
                }
            })
            isNativeTtsInitialized.complete(Unit)
        } else {
            isNativeTtsInitialized.completeExceptionally(Exception("Native TTS Initialization failed"))
        }
    }

    // --- NEW PUBLIC FUNCTION TO STOP PLAYBACK ---
    fun stop() {
        // Stop the AudioTrack if it's currently playing
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.stop()
            audioTrack?.flush() // Clear any buffered data
        }
        // Immediately cancel any coroutine that is awaiting playback completion
        if (googleTtsPlaybackDeferred?.isActive == true) {
            googleTtsPlaybackDeferred?.completeExceptionally(CancellationException("Playback stopped by new request."))
        }
    }

    suspend fun speakText(text: String) {
        if (!isDebugMode) return
        speak(text)
    }

    suspend fun speakToUser(text: String) {
        speak(text)
    }

    fun getAudioSessionId(): Int {
        return audioTrack?.audioSessionId ?: 0
    }

    private suspend fun speak(text: String) {
        try {
            isNativeTtsInitialized.await()
            nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode().toString())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("TTSManager", "TTS failed: ${e.message}")
        }
    }
    }
    
    /**
     * Cloud-based TTS playback - DISABLED. Use native TTS via speak() instead.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun playWithSmartQueue(textChunks: List<String>, selectedVoice: TTSVoice) {
        // Cloud TTS disabled - using native TTS instead
        Log.w("TTSManager", "Cloud TTS is disabled. Use native TTS via speak() instead.")
        return
    }
    
    /**
     * Breaks text into sentences of approximately maxWordsPerChunk words each
     */
    private fun chunkTextIntoSentences(text: String, maxWordsPerChunk: Int): List<String> {
        if (text.length <= 500) {
            // For short text, return as is
            return listOf(text)
        }
        
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.trim().isNotEmpty() }
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var currentWordCount = 0
        
        for (sentence in sentences) {
            val sentenceWordCount = sentence.split(Regex("\\s+")).size
            
            // If adding this sentence would exceed the limit and we already have content
            if (currentWordCount + sentenceWordCount > maxWordsPerChunk && currentChunk.isNotEmpty()) {
                // Add current chunk to results
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
                currentWordCount = 0
            }
            
            // Add sentence to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(sentence)
            currentWordCount += sentenceWordCount
        }
        
        // Add the last chunk if it has content
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        // If no chunks were created (e.g., very long single sentence), 
        // break by words instead
        if (chunks.isEmpty() || chunks.size == 1 && chunks[0].split(Regex("\\s+")).size > maxWordsPerChunk * 2) {
            return chunkTextByWords(text, maxWordsPerChunk)
        }
        
        return chunks
    }
    
    /**
     * Fallback method to break text by words when sentence-based chunking fails
     */
    private fun chunkTextByWords(text: String, maxWordsPerChunk: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        
        for (i in words.indices step maxWordsPerChunk) {
            val chunk = words.drop(i).take(maxWordsPerChunk).joinToString(" ")
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
        }
        
        return chunks
    }
    
    /**
     * Cloud-based TTS chunk playback - DISABLED. Use native TTS via speak() instead.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun speakChunk(chunk: String, selectedVoice: TTSVoice) {
        Log.w("TTSManager", "Cloud TTS is disabled. Using native TTS fallback.")
        isNativeTtsInitialized.await()
        nativeTts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, this.hashCode().toString())
    }

    suspend fun playAudioData(audioData: ByteArray) {
        try {
            googleTtsPlaybackDeferred = CompletableDeferred()
            withContext(Dispatchers.Main) {
                utteranceListener?.invoke(true)
            }

            withContext(Dispatchers.IO) {
                audioTrack?.play()
                val numFrames = audioData.size / 2
                audioTrack?.setNotificationMarkerPosition(numFrames)
                audioTrack?.write(audioData, 0, audioData.size)
            }

            withTimeoutOrNull(30000) { googleTtsPlaybackDeferred?.await() }

            withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("TTSManager", "Error playing audio data", e)
        } finally {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
                audioTrack?.flush()
            }
            if (utteranceListener != null && Looper.myLooper() != Looper.getMainLooper()) {
                withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
            } else {
                utteranceListener?.invoke(false)
            }
        }
    }



    fun shutdown() {
        stop()
        nativeTts?.shutdown()
        audioTrack?.release()
        audioTrack = null
        INSTANCE = null
    }
}