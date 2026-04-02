package com.ai.assistance.operit.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import com.ai.assistance.operit.R
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.voice.api.Eyes
import com.ai.assistance.operit.voice.utilities.SpeechCoordinator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.ai.assistance.operit.voice.agents.ClarificationAgent
import com.ai.assistance.operit.voice.utilities.TTSManager
import com.ai.assistance.operit.voice.utilities.addResponse
import com.ai.assistance.operit.voice.utilities.getReasoningModelApiResponse
import com.ai.assistance.operit.voice.utilities.FreemiumManager
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.voice.utilities.OperitState
import com.ai.assistance.operit.voice.utilities.UserProfileManager
import com.ai.assistance.operit.voice.utilities.VisualFeedbackManager
import com.ai.assistance.operit.voice.v2.AgentService
import com.ai.assistance.operit.voice.data.UserMemory
import com.ai.assistance.operit.voice.utilities.TextPart
import com.ai.assistance.operit.voice.utilities.ServicePermissionManager
import com.ai.assistance.operit.voice.utilities.OperitStateManager
import com.ai.assistance.operit.voice.v2.perception.Perception
import com.ai.assistance.operit.voice.v2.perception.SemanticParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

data class ModelDecision(
    val type: String = "Reply",
    val reply: String,
    val instruction: String = "",
    val shouldEnd: Boolean = false
)

class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val overlayManager by lazy { OverlayManager.getInstance(this) }
    private val clarificationQuestionViews = mutableListOf<View>()
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val pandaStateManager by lazy { OperitStateManager.getInstance(this) }
    private var isTextModeActive = false
    private val freemiumManager by lazy { FreemiumManager.getInstance(this) }
    private val servicePermissionManager by lazy { ServicePermissionManager(this) }

    private var clarificationAttempts = 0
    private val maxClarificationAttempts = 1
    private var sttErrorAttempts = 0
    private val maxSttErrorAttempts = 2

    private val clarificationAgent = ClarificationAgent()
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedMemories = listOf<UserMemory>()
    private var hasHeardFirstUtterance = false
    private lateinit var perception: Perception
    private val client = OkHttpClient()

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        const val ACTION_STOP_SERVICE = "com.ai.assistance.operit.voice.ACTION_STOP_SERVICE"
        var isRunning = false
        const val MEMORY_ENABLED = true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        
        isRunning = true
        createNotificationChannel()
        initializeConversation()
        clarificationAttempts = 0
        sttErrorAttempts = 0
        hasHeardFirstUtterance = false

        fetchMemories()

        OverlayDispatcher.clearAll()
        overlayManager.startObserving()
        visualFeedbackManager.showSpeakingOverlay()
        visualFeedbackManager.showTtsWave()

        showInputBoxIfNeeded()
        visualFeedbackManager.showSmallDeltaGlow()

        pandaStateManager.startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Conversational Agent", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Operit Voice")
            .setContentText("Voice assistant is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun initializeConversation() {
        val systemPrompt = """
            <system_instructions>
            Current Time : {time_context}
            
            <agent_status_context>
            CONTEXT: No automation task is currently running.
            </agent_status_context>
            
            <screen_context>
            No screen context available yet.
            </screen_context>
            
            <memory_context>
            No memories available yet.
            </memory_context>
            
            You are Operit, a helpful AI voice assistant. 
            Be concise and friendly in your responses.
            </system_instructions>
        """.trimIndent()
        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    private fun showInputBoxIfNeeded() {
        if (isTextModeActive) {
            visualFeedbackManager.showInputBox(
                onActivated = { enterTextMode() },
                onSubmit = { submittedText -> serviceScope.launch { processUserInput(submittedText) } },
                onOutsideTap = { serviceScope.launch { stopSelf() } }
            )
        }
    }

    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")
        
        isTextModeActive = true
        pandaStateManager.setState(OperitState.IDLE)
        speechCoordinator.stopListening()
        speechCoordinator.stopSpeaking()
        visualFeedbackManager.hideTranscription()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("ConvAgent", "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted.")
            Toast.makeText(this, "Microphone permission required for voice assistant", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            serviceScope.launch {
                speechCoordinator.speakText("Hello, please give microphone permission!")
                delay(2000)
                stopSelf()
            }
            Log.e("ConvAgent", "Failed to start foreground service: ${e.message}")
            Toast.makeText(this, "Cannot start voice assistant - permission missing", Toast.LENGTH_LONG).show()
            return START_NOT_STICKY
        }

        if (!servicePermissionManager.isMicrophonePermissionGranted()) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted.")
            serviceScope.launch {
                ttsManager.speakText(getString(R.string.microphone_permission_not_granted))
                delay(2000)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        serviceScope.launch {
            Log.d("ConvAgent", "Starting immediate listening (no greeting)")
            pandaStateManager.setState(OperitState.LISTENING)
            startImmediateListening()
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startImmediateListening() {
        Log.d("ConvAgent", "Starting immediate listening without greeting")
        
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                visualFeedbackManager.showInputBox(
                    onActivated = { enterTextMode() },
                    onSubmit = { submittedText -> processUserInput(submittedText) },
                    onOutsideTap = { serviceScope.launch { stopSelf() } }
                )
            }
            return
        }

        speechCoordinator.startListening(
            onResult = { text ->
                Log.d("ConvAgent", "User said: $text")
                pandaStateManager.setState(OperitState.PROCESSING)
                processUserInput(text)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
            },
            onPartialResult = { partialText ->
                if (isTextModeActive) return@startListening
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening
                    pandaStateManager.setState(OperitState.LISTENING)
                    visualFeedbackManager.showTranscription()
                } else {
                    if (!isTextModeActive) {
                        pandaStateManager.setState(OperitState.IDLE)
                    }
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        updateSystemPromptWithTime()
        pandaStateManager.setState(OperitState.SPEAKING)
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Operit said: $text")
        
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                visualFeedbackManager.showInputBox(
                    onActivated = { enterTextMode() },
                    onSubmit = { submittedText -> processUserInput(submittedText) },
                    onOutsideTap = { serviceScope.launch { stopSelf() } }
                )
            }
            return
        }

        delay(500)
        startImmediateListening()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processUserInput(text: String) {
        try {
            Log.d("ConvAgent", "Processing user input: $text")
            
            val userMessage = text.lowercase()
            
            when {
                userMessage.contains("stop") || userMessage.contains("bye") -> {
                    val goodbye = "Goodbye! Have a great day!"
                    speakAndThenListen(goodbye, false)
                    gracefulShutdown(goodbye, "command")
                }
                userMessage.contains("text mode") || userMessage.contains("type") -> {
                    enterTextMode()
                    val message = "Switched to text mode. You can type your messages now."
                    speechCoordinator.speakText(message)
                    visualFeedbackManager.showInputBox(
                        onActivated = { enterTextMode() },
onSubmit = { submittedText -> serviceScope.launch { processUserInput(submittedText) } },
                onOutsideTap = { serviceScope.launch { stopSelf() } }
                    )
                }
                else -> {
                    val reply = "I heard: $text. How can I help you?"
                    speakAndThenListen(reply)
                }
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error processing user input", e)
            val errorMsg = "Sorry, something went wrong. Please try again."
            speakAndThenListen(errorMsg)
        }
    }

    private fun gracefulShutdown(message: String, reason: String) {
        Log.d("ConvAgent", "Graceful shutdown: $reason")
        mainHandler.post {
            speechCoordinator.stopSpeaking()
            speechCoordinator.stopListening()
            visualFeedbackManager.hideTtsWave()
            visualFeedbackManager.hideTranscription()
            visualFeedbackManager.hideSpeakingOverlay()
            visualFeedbackManager.hideInputBox()
            removeClarificationQuestions()
        }
        removeClarificationQuestions()
        triggerMemoryGeneration()
        serviceScope.cancel("Graceful shutdown: $reason")
        stopSelf()
    }

    private fun removeClarificationQuestions() {
        mainHandler.post {
            clarificationQuestionViews.forEach { view ->
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Error removing clarification view", e)
                }
            }
            clarificationQuestionViews.clear()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateSystemPromptWithTime() {
        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val formattedTime = currentTime.format(formatter)

        val timeRegex = Regex("Current Time : (\\{time_context\\}|.*)")
        val newTimeLine = "Current Time : $formattedTime"
        val updatedPromptText = timeRegex.replace(currentPromptText, newTimeLine)

        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "user" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with time: $formattedTime")
    }

    private fun updateSystemPromptWithAgentStatus() {
        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val agentStatusContext = if (AgentService.isRunning) {
            """
            IMPORTANT CONTEXT: An automation task is currently running in the background.
            Task Description: "${AgentService.currentTask}".
            If the user asks to stop, cancel, or kill this task, you MUST use the "KillTask" type.
            """.trimIndent()
        } else {
            "CONTEXT: No automation task is currently running."
        }

        val updatedPromptText = currentPromptText.replace("{agent_status_context}", agentStatusContext)

        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "user" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with agent status: ${AgentService.isRunning}")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun updateSystemPromptWithScreenContext() {
        try {
            perception = Perception(Eyes(this), SemanticParser())
            val analysis = perception.analyze(all = true)
            Log.d("ConvAgent", "Screen analysis: ${analysis.uiRepresentation}")
            val currentPrompt = conversationHistory.firstOrNull()?.second
                ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

            var updatedPrompt = currentPrompt.replace("{screen_context}", analysis.uiRepresentation)

            if (!MEMORY_ENABLED) {
                var userProfile = UserProfileManager(this@ConversationalAgentService)
                Log.d("ConvAgent", "Memory is disabled, skipping memory operations")
                Log.d("ConvAgent", "User name is ${userProfile.getName()}")
                updatedPrompt = updatedPrompt.replace("{memory_context}", "User name is ${userProfile.getName()}")
            } else {
                if (cachedMemories.isNotEmpty()) {
                    Log.d("ConvAgent", "Injecting ${cachedMemories.size} cached memories into context")
                    val topMemories = cachedMemories.take(100)
                    val memoryContext = topMemories.joinToString("\n") { memory -> 
                        "- ${memory.text} (Source: ${memory.source})" 
                    }
                    updatedPrompt = updatedPrompt.replace("{memory_context}", memoryContext)
                } else {
                    Log.d("ConvAgent", "No cached memories available yet")
                    updatedPrompt = updatedPrompt.replace("{memory_context}", "No memories available yet.")
                }
            }

            if (updatedPrompt.isNotEmpty()) {
                conversationHistory = conversationHistory.toMutableList().apply {
                    set(0, "user" to listOf(TextPart(updatedPrompt)))
                }
                Log.d("ConvAgent", "Updated system prompt with screen context and memories")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with screen context", e)
        }
    }

    private fun fetchMemories() {
        Log.d("ConvAgent", "Using local memories (Firebase not available)")
        cachedMemories = emptyList()
    }

    private fun triggerMemoryGeneration() {
        Log.d("ConvAgent", "Memory generation skipped (Firebase not available)")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        
        overlayManager.stopObserving()
        
        removeClarificationQuestions()
        serviceScope.cancel()
        isRunning = false
        
        pandaStateManager.setState(OperitState.IDLE)
        pandaStateManager.stopMonitoring()
        visualFeedbackManager.hideSmallDeltaGlow()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
