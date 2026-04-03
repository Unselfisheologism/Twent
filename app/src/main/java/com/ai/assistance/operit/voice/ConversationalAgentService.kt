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
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.json.JSONObject
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
    private var conversationHistory = listOf<Pair<String, String>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val overlayManager by lazy { OverlayManager.getInstance(this) }
    private val clarificationQuestionViews = mutableListOf<View>()
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val stateManager by lazy { OperitStateManager.getInstance(this) }
    private var isTextModeActive = false
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

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        const val ACTION_STOP_SERVICE = "com.ai.assistance.operit.voice.ACTION_STOP_SERVICE"
        var isRunning = false
        const val MEMORY_ENABLED = true
    }

    private fun getAIService(): AIService? {
        return try {
            val modelConfigManager = ModelConfigManager(this)
            val userPrefs = UserPreferencesManager.getInstance(this)
            val activeConfigId = runBlocking { userPrefs.activeProfileIdFlow.first() }
            if (activeConfigId.isEmpty()) {
                Log.w("ConvAgent", "No active model config found")
                return null
            }
            val config = modelConfigManager.getModelConfig(activeConfigId)
                ?: run {
                    Log.w("ConvAgent", "Model config not found for id: $activeConfigId")
                    return null
                }
            val customHeaders = runBlocking { com.ai.assistance.operit.data.preferences.ApiPreferences.getInstance(this@ConversationalAgentService).getCustomHeaders() }
            AIServiceFactory.createService(config, customHeaders, modelConfigManager, this)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Failed to create AIService", e)
            null
        }
    }

    private suspend fun getLLMResponse(messages: List<Pair<String, String>>): String {
        val aiService = getAIService()
            ?: return """{"Type": "Reply", "Reply": "Please configure an AI model in Operit settings first.", "Instruction": "", "Should End": "Continue"}"""

        val systemPrompt = messages.firstOrNull()?.second ?: ""
        val userMessages = messages.drop(1)
        val chatHistory = userMessages.map { it.first to it.second }
        val lastUserMessage = chatHistory.lastOrNull()?.second ?: "Hello"

        return try {
            val responseStream = aiService.sendMessage(
                context = this,
                message = lastUserMessage,
                chatHistory = chatHistory.dropLast(1),
                stream = false
            )
            val fullResponse = responseStream.toList().joinToString("")
            aiService.release()
            fullResponse
        } catch (e: Exception) {
            Log.e("ConvAgent", "LLM call failed", e)
            aiService.release()
            """{"Type": "Reply", "Reply": "I'm having trouble connecting to the AI service.", "Instruction": "", "Should End": "Continue"}"""
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")

        MyApplication.init(this)

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

        stateManager.startMonitoring()
        stateManager.setState(OperitState.IDLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conversational Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversational Agent")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun initializeConversation() {
        val systemPrompt = """
You are a helpful voice assistant called Operit that can either have a conversation or ask an executor to execute tasks on the user's phone.
The executor can speak, listen, see the screen, tap the screen, and basically use the phone as a normal human would.

{agent_status_context}

### Current Screen Context ###
{screen_context}
### End Screen Context ###

Some Guideline:
1. If the user ask you to do something creative, you do this task and be the most creative person in the world.
2. If you know the user's name from the memories, refer to them by their name to make the conversation more personal and friendly as often as possible.
3. Use the current screen context to better understand what the user is looking at and provide more relevant responses.
4. If the user asks about something on the screen, you can reference the screen content directly.
5. Always ask for clarification if the user's request is ambiguous or unclear.
6. When the user ask to sing, shout or produce any sound, just generate text, we will sing it for you.
7. Your code is opensource so you can tell that to user.
8. Give a warning for the tasks related to banking, games, shopping and app with Canvas (no a11y tree) that you wont be able to do them properly but you will try your best.

Use these memories to answer the user's question with his personal data
### Memory Context Start ###
{memory_context}
### Memory Context Ends ###

Analyze the user's request and respond ONLY with a single, valid JSON object.
Do not include any text, notes, or explanations outside of the JSON object.
The JSON object must have the following structure:

{
  "Type": "String",
  "Reply": "String",
  "Instruction": "String",
  "Should End": "String"
}

Here are the rules for the JSON values:
- "Type": Must be one of "Task", "Reply", or "KillTask".
  - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom").
  - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke").
  - Use "KillTask" ONLY if an automation task is running and the user wants to stop it.
- "Reply": The text to speak to the user. This is a confirmation for a "Task", or the direct answer for a "Reply".
- "Instruction": The precise, literal instruction for the task agent. This field should be an empty string "" if the "Type" is not "Task".
- "Should End": Must be either "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.

Current Time : {time_context}
        """.trimIndent()

        conversationHistory = listOf("user" to systemPrompt)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showInputBoxIfNeeded() {
        visualFeedbackManager.showInputBox(
            onActivated = { enterTextMode() },
            onSubmit = { submittedText ->
                serviceScope.launch { processUserInput(submittedText) }
            },
            onOutsideTap = {
                serviceScope.launch { instantShutdown() }
            }
        )
    }

    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")

        isTextModeActive = true
        stateManager.setState(OperitState.IDLE)
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
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted. Cannot start foreground service.")
            Toast.makeText(this, "Microphone permission required for voice assistant", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            serviceScope.launch {
                speechCoordinator.speakText("Hello, please give microphone permission or some other type of permission you have not given me!")
                delay(2000)
                stopSelf()
            }
            Log.e("ConvAgent", "Failed to start foreground service: ${e.message}")
            Toast.makeText(this, "Cannot start voice assistant - permission missing", Toast.LENGTH_LONG).show()
            return START_NOT_STICKY
        }

        if (!servicePermissionManager.isMicrophonePermissionGranted()) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted. Shutting down.")
            serviceScope.launch {
                ttsManager.speakText(getString(R.string.microphone_permission_not_granted))
                delay(2000)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        serviceScope.launch {
            Log.d("ConvAgent", "Starting immediate listening (no greeting)")
            stateManager.setState(OperitState.LISTENING)
            startImmediateListening()
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startImmediateListening() {
        Log.d("ConvAgent", "Starting immediate listening without greeting")

        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post { showInputBoxIfNeeded() }
            return
        }

        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                stateManager.setState(OperitState.PROCESSING)
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)

                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening

                if (error == "No speech match") {
                    Log.d("ConvAgent", "No speech match detected. Silently resetting to IDLE.")
                    visualFeedbackManager.hideTranscription()
                    stateManager.setState(OperitState.IDLE)
                    return@startListening
                }

                stateManager.triggerErrorState()
                visualFeedbackManager.hideTranscription()
                sttErrorAttempts++
                serviceScope.launch {
                    if (sttErrorAttempts >= maxSttErrorAttempts) {
                        val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                        gracefulShutdown(exitMessage, "stt_errors")
                    } else {
                        val retryMessage = "I'm sorry, I didn't catch that. Could you please repeat?"
                        speakAndThenListen(retryMessage)
                    }
                }
            },
            onPartialResult = { partialText ->
                if (isTextModeActive) return@startListening
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening
                    stateManager.setState(OperitState.LISTENING)
                    visualFeedbackManager.showTranscription()
                } else {
                    if (!isTextModeActive) {
                        stateManager.setState(OperitState.IDLE)
                    }
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        updateSystemPromptWithTime()
        stateManager.setState(OperitState.SPEAKING)
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Operit said: $text")

        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post { showInputBoxIfNeeded() }
            return
        }

        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                stateManager.setState(OperitState.PROCESSING)
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)

                if (!hasHeardFirstUtterance) {
                    hasHeardFirstUtterance = true
                    Log.d("ConvAgent", "First utterance received, triggering memory extraction")
                    serviceScope.launch {
                        try {
                            updateSystemPromptWithScreenContext()
                        } catch (e: Exception) {
                            Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                        }
                    }
                }

                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening

                stateManager.triggerErrorState()
                visualFeedbackManager.hideTranscription()
                sttErrorAttempts++
                serviceScope.launch {
                    if (sttErrorAttempts >= maxSttErrorAttempts) {
                        val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                        gracefulShutdown(exitMessage, "stt_errors")
                    } else {
                        speakAndThenListen("I'm sorry, I didn't catch that. Could you please repeat?")
                    }
                }
            },
            onPartialResult = { partialText ->
                if (isTextModeActive) return@startListening
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening
                    stateManager.setState(OperitState.LISTENING)
                    visualFeedbackManager.showTranscription()
                } else {
                    if (!isTextModeActive) {
                        stateManager.setState(OperitState.IDLE)
                    }
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            removeClarificationQuestions()
            updateSystemPromptWithAgentStatus()
            updateSystemPromptWithScreenContext()
            updateSystemPromptWithTime()

            if (!hasHeardFirstUtterance) {
                hasHeardFirstUtterance = true
                Log.d("ConvAgent", "First utterance received via processUserInput, triggering memory extraction")
                try {
                    updateSystemPromptWithScreenContext()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                }
            }

            conversationHistory = conversationHistory + ("user" to userInput)

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    gracefulShutdown("Goodbye!", "command")
                    return@launch
                }

                stateManager.setState(OperitState.PROCESSING)
                visualFeedbackManager.showThinkingIndicator()

                val defaultJsonResponse = """{"Type": "Reply", "Reply": "I'm sorry, I had an issue.", "Instruction": "", "Should End": "Continue"}"""
                val rawModelResponse = try {
                    getLLMResponse(conversationHistory)
                } catch (e: Exception) {
                    Log.e("ConvAgent", "LLM call failed", e)
                    defaultJsonResponse
                }

                visualFeedbackManager.hideThinkingIndicator()
                val decision = parseModelResponse(rawModelResponse)
                Log.d("ConvAgent", "Reply received from LLM: -->${rawModelResponse}<--")

                when (decision.type) {
                    "Task" -> {
                        if (AgentService.isRunning) {
                            val busyMessage = "I'm already working on '${AgentService.currentTask}'. Please let me finish that first, or you can ask me to stop it."
                            speakAndThenListen(busyMessage)
                            conversationHistory = conversationHistory + ("model" to busyMessage)
                            return@launch
                        }

                        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
                            speakAndThenListen(getString(R.string.accessibility_permission_needed_for_task))
                            conversationHistory = conversationHistory + ("model" to getString(R.string.accessibility_permission_needed_for_task))
                            return@launch
                        }

                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        removeClarificationQuestions()

                        if (clarificationAttempts < maxClarificationAttempts) {
                                val (needsClarification, questions) = checkIfClarificationNeeded(decision.instruction)
                                Log.d("ConvAgent", "Needs clarification: $needsClarification")
                                Log.d("ConvAgent", "Questions: $questions")

                                if (needsClarification) {
                                    clarificationAttempts++
                                    displayClarificationQuestions(questions)
                                    val questionToAsk = "I can help with that, but first: ${questions.joinToString(" and ")}"
                                    Log.d("ConvAgent", "Task needs clarification. Asking: '$questionToAsk' (Attempt $clarificationAttempts/$maxClarificationAttempts)")
                                    conversationHistory = conversationHistory + ("model" to "Clarification needed for task: ${decision.instruction}")
                                    speakAndThenListen(questionToAsk, false)
                                } else {
                                    Log.d("ConvAgent", "Task is clear. Executing: ${decision.instruction}")
                                    AgentService.start(applicationContext, decision.instruction)
                                    conversationHistory = conversationHistory + ("model" to decision.reply)
                                    gracefulShutdown(decision.reply, "task_executed")
                                }
                            } else {
                                Log.d("ConvAgent", "Max clarification attempts reached. Proceeding with task execution.")
                                AgentService.start(applicationContext, decision.instruction)
                                conversationHistory = conversationHistory + ("model" to decision.reply)
                                gracefulShutdown(decision.reply, "task_executed")
                            }
                        }
                        }
                    }
                    "KillTask" -> {
                        Log.d("ConvAgent", "Model requested to kill the running agent service.")
                        if (AgentService.isRunning) {
                            AgentService.stop(applicationContext)
                            conversationHistory = conversationHistory + ("model" to decision.reply)
                            gracefulShutdown(decision.reply, "task_killed")
                        } else {
                            val noTaskMessage = "There was no automation running, but I can help with something else."
                            conversationHistory = conversationHistory + ("model" to noTaskMessage)
                            speakAndThenListen(noTaskMessage)
                        }
                    }
                    else -> {
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            gracefulShutdown(decision.reply, "model_ended")
                        } else {
                            conversationHistory = conversationHistory + ("model" to rawModelResponse)
                            speakAndThenListen(decision.reply)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                stateManager.triggerErrorState()
                speakAndThenListen("closing voice mode")
            }
        }
    }

    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        Log.d("ConvAgent", "Checking for clarification on instruction: '$instruction'")
        return Pair(false, listOf())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateSystemPromptWithTime() {
        val currentPromptText = conversationHistory.firstOrNull()?.second ?: return

        val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val formattedTime = currentTime.format(formatter)

        val timeRegex = Regex("Current Time : (\\{time_context\\}|.*)")
        val newTimeLine = "Current Time : $formattedTime"
        val updatedPromptText = timeRegex.replace(currentPromptText, newTimeLine)

        conversationHistory = listOf("user" to updatedPromptText) + conversationHistory.drop(1)
        Log.d("ConvAgent", "System prompt updated with time: $formattedTime")
    }

    private fun updateSystemPromptWithAgentStatus() {
        val currentPromptText = conversationHistory.firstOrNull()?.second ?: return

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

        conversationHistory = listOf("user" to updatedPromptText) + conversationHistory.drop(1)
        Log.d("ConvAgent", "System prompt updated with agent status: ${AgentService.isRunning}")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun updateSystemPromptWithScreenContext() {
        try {
            perception = Perception(Eyes(this), SemanticParser())
            val analysis = perception.analyze(all = true)
            Log.d("ConvAgent", "Screen analysis: ${analysis.uiRepresentation}")
            val currentPrompt = conversationHistory.firstOrNull()?.second ?: return

            var updatedPrompt = currentPrompt.replace("{screen_context}", analysis.uiRepresentation)

            if (!MEMORY_ENABLED) {
                val userProfile = UserProfileManager(this@ConversationalAgentService)
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
                conversationHistory = listOf("user" to updatedPrompt) + conversationHistory.drop(1)
                Log.d("ConvAgent", "Updated system prompt with screen context and memories")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with memories and screen context", e)
        }
    }

    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val json = JSONObject(response)
            Log.d("justchecking", json.toString())
            val type = json.optString("Type", "Reply")
            val reply = json.optString("Reply", "")
            val instruction = json.optString("Instruction", "")
            val shouldEndStr = json.optString("Should End", "Continue")
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)

            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: org.json.JSONException) {
            Log.e("ConvAgent", "Error parsing JSON response, falling back. Response: $response", e)
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        } catch (e: Exception) {
            Log.e("ConvAgent", "Generic error parsing model response, falling back. Response: $response", e)
            return ModelDecision(reply = "I had a minor issue processing that. Could you try again?")
        }
    }

    private fun displayClarificationQuestions(questions: List<String>) {
        mainHandler.post {
            val topMargin = 100
            val verticalSpacing = 20
            var accumulatedHeight = 0

            questions.forEachIndexed { index, questionText ->
                val textView = TextView(this).apply {
                    text = questionText
                    val glowEffect = GradientDrawable(
                        GradientDrawable.Orientation.BL_TR,
                        intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt())
                    ).apply { cornerRadius = 32f }

                    val glassBackground = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xEE0D0D2E.toInt(), 0xEE2A0D45.toInt())
                    ).apply {
                        cornerRadius = 28f
                        setStroke(1, 0x80FFFFFF.toInt())
                    }

                    val layerDrawable = LayerDrawable(arrayOf(glowEffect, glassBackground)).apply {
                        setLayerInset(1, 4, 4, 4, 4)
                    }
                    background = layerDrawable
                    setTextColor(0xFFE0E0E0.toInt())
                    textSize = 15f
                    setPadding(40, 24, 40, 24)
                    typeface = Typeface.MONOSPACE
                }

                textView.measure(
                    View.MeasureSpec.makeMeasureSpec((windowManager.defaultDisplay.width * 0.9).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val viewHeight = textView.measuredHeight
                val finalYPosition = topMargin + accumulatedHeight
                accumulatedHeight += viewHeight + verticalSpacing

                val params = WindowManager.LayoutParams(
                    (windowManager.defaultDisplay.width * 0.9).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = -viewHeight
                    alpha = 0f
                }

                try {
                    windowManager.addView(textView, params)
                    clarificationQuestionViews.add(textView)

                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500L
                        startDelay = (index * 150).toLong()

                        addUpdateListener { animation ->
                            val progress = animation.animatedValue as Float
                            params.y = (finalYPosition * progress - viewHeight * (1 - progress)).toInt()
                            params.alpha = progress
                            windowManager.updateViewLayout(textView, params)
                        }
                    }
                    animator.start()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Failed to display clarification question.", e)
                }
            }
        }
    }

    private fun removeClarificationQuestions() {
        mainHandler.post {
            clarificationQuestionViews.forEach { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        Log.e("ConvAgent", "Error removing clarification view.", e)
                    }
                }
            }
            clarificationQuestionViews.clear()
        }
    }

    private suspend fun gracefulShutdown(exitMessage: String? = null, endReason: String = "graceful") {
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        if (exitMessage != null) {
            speechCoordinator.speakText(exitMessage)
            delay(2000)
        }

        triggerMemoryGeneration()
        stopSelf()
    }

    private suspend fun instantShutdown() {
        Log.d("ConvAgent", "Instant shutdown triggered by user.")
        withContext(Dispatchers.Main) {
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
        serviceScope.cancel("User tapped outside, forcing instant shutdown.")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")

        overlayManager.stopObserving()

        removeClarificationQuestions()
        serviceScope.cancel()
        isRunning = false

        stateManager.setState(OperitState.IDLE)
        stateManager.stopMonitoring()
        visualFeedbackManager.hideSmallDeltaGlow()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fetchMemories() {
        Log.d("ConvAgent", "Using local memories")
        cachedMemories = emptyList()
    }

    private fun triggerMemoryGeneration() {
        Log.d("ConvAgent", "Memory generation skipped")
    }
}
