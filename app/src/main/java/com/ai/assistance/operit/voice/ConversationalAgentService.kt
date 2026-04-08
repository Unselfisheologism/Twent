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
import com.ai.assistance.operit.voice.api.GeminiApi
import com.ai.assistance.operit.voice.utilities.ApiKeyManager
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
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.voice.utilities.OperitState
import com.ai.assistance.operit.voice.utilities.UserProfileManager
import com.ai.assistance.operit.voice.utilities.VisualFeedbackManager
import com.ai.assistance.operit.voice.utilities.TextPart
import com.ai.assistance.operit.voice.v2.AgentService
import com.ai.assistance.operit.voice.data.UserMemory
import com.ai.assistance.operit.voice.utilities.ServicePermissionManager
import com.ai.assistance.operit.voice.utilities.OperitStateManager
import com.ai.assistance.operit.voice.v2.perception.Perception
import com.ai.assistance.operit.voice.v2.perception.SemanticParser
import com.ai.assistance.operit.voice.api.Eyes
import com.ai.assistance.operit.voice.utilities.FullPageCapture
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    private var conversationHistory = listOf<Pair<String, List<Any>>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val overlayManager by lazy { OverlayManager.getInstance(this) }
    private val clarificationQuestionViews = mutableListOf<View>()
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val stateManager by lazy { OperitStateManager.getInstance(this) }
    private var isTextModeActive = false
    private var actionStatusViewNotShownYet = true
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

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")

        MyApplication.init(this)

        try {
            val apiPrefs = ApiPreferences.getInstance(this)
            val apiKey = runBlocking { apiPrefs.apiKeyFlow.first() }
            if (apiKey.isNotBlank()) {
                ApiKeyManager.setApiKeys(listOf(apiKey))
                Log.d("ConvAgent", "Legacy API key loaded (note: voice agent now uses chat's AI provider config)")
            } else {
                Log.e("ConvAgent", "No API key configured")
                serviceScope.launch {
                    delay(1000)
                    ttsManager.speakText("No API key configured. Please go to settings and add your Google Gemini API key.")
                }
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Failed to load legacy API key", e)
        }

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

        stateManager.startMonitoring()
        stateManager.setState(OperitState.IDLE)
    }

    private fun initializeOverlays() {
        Log.d("ConvAgent", "initializeOverlays: starting overlay initialization")
        OverlayDispatcher.clearAll()
        overlayManager.startObserving()
        visualFeedbackManager.showSpeakingOverlay()
        Log.d("ConvAgent", "initializeOverlays: speaking overlay requested")
        visualFeedbackManager.showTtsWave()
        Log.d("ConvAgent", "initializeOverlays: tts wave requested")

        showInputBoxIfNeeded()
        Log.d("ConvAgent", "initializeOverlays: input box requested")
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
You are a helpful voice assistant called Operit that can either have a conversation or delegate tasks to an executor on the user's phone.
The executor is a UI automation agent. It sees the phone screen, taps buttons, types text, scrolls, presses home/back, and interacts with ANY app or website exactly as a human finger would.

CRITICAL RULES — NEVER VIOLATE THESE:
1. You CAN instruct the executor to open ANY app on the phone, including Brave browser, Chrome, or any other app. The executor uses the "open_app" action to launch apps by name.
2. You CAN instruct the executor to navigate to ANY website. The executor will type the URL into the browser's address bar using UI automation. It does NOT need API access or a web browser integration.
3. You CAN instruct the executor to interact with ANY website the user is logged into. If the user has x.com open in Brave and is logged in, the executor will see the logged-in page and can tap on notifications.
4. NEVER say "I cannot access", "I don't have access to", "I cannot visit", "I cannot open", or any variation of refusing a task based on lack of access. The executor uses UI automation (screen tapping), not API integration. It interacts with the visual interface, not with backend services.
5. When the user asks you to do something on their phone, you MUST respond with Type "Task". This is non-negotiable for action requests.

How the executor works:
- The executor sees the phone screen as a tree of interactive elements with numeric indexes
- The executor taps elements by their index, types text into fields, scrolls, presses back/home
- The executor can open apps by name using the "open_app" action
- The executor can type URLs into browser address bars
- The executor can tap notification icons, menu items, buttons, etc.
- If the user is logged into a website, the executor sees the logged-in state

{agent_status_context}

### Current Screen Context ###
{screen_context}
### End Screen Context ###

Guidelines:
1. ALWAYS use Type "Task" for any request involving phone actions (open apps, visit websites, check notifications, send messages).
2. Keep the Reply field SHORT and natural (under 15 words). Example: "Sure, checking your X notifications now."
3. Put detailed instructions in the Instruction field, not the Reply.
4. If you know the user's name from memories, use it.
5. Ask for clarification only if the request is truly ambiguous.
6. Warn about banking, games, shopping, and Canvas-based apps that may not work well with accessibility services.

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

Rules for JSON values:
- "Type": Must be one of "Task", "Reply", or "KillTask".
  - Use "Task" if the user is asking you to DO something on the device (e.g., "open brave browser", "go to x.com", "check my notifications", "send a text to Mom").
  - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke").
  - Use "KillTask" ONLY if an automation task is running and the user wants to stop it.
- "Reply": A SHORT, natural confirmation (under 15 words). Example: "Sure, I'm on it." NEVER include execution steps, tap coordinates, or technical details.
- "Instruction": Clear, step-by-step description for the executor. Example: "Open Brave browser. Type x.com in the address bar and press enter. Wait for page to load. Tap the notifications bell icon. Read out any new notifications." Empty string "" if Type is not "Task".
- "Should End": Must be either "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.

Current Time : {time_context}
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
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

    /**
     * Handle the "Simplify this page" button click
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun handleSimplifyPage() {
        try {
            visualFeedbackManager.updateStatusText("📄 Analyzing current page content...")
            visualFeedbackManager.showThinkingIndicator()

            val fullPageCapture = FullPageCapture.getInstance(this)

            // Get current screen content - use text extraction first (faster)
            val screenText = fullPageCapture.getCurrentScreenText()

            // Check if page needs scrolling (long content)
            val estimatedLines = screenText.count { it == '\n' }
            val needsScrolling = estimatedLines > 50 // More than 50 lines likely needs scrolling

            var fullContent = screenText

            if (needsScrolling) {
                visualFeedbackManager.updateStatusText("📄 Long page detected. Capturing full content by scrolling...")

                // Capture full page by scrolling
                val screenshots = fullPageCapture.captureFullPage()

                if (screenshots.isNotEmpty()) {
                    visualFeedbackManager.updateStatusText(
                        "📄 Captured ${screenshots.size} screenshot(s). Analyzing content..."
                    )

                    // For images in content, we'll describe them via the perception system
                    // Use the perception to analyze screen structure including images
                    perception = Perception(Eyes(this), SemanticParser())
                    val analysis = perception.analyze(all = true)

                    // Combine text content with structural analysis
                    fullContent = "${analysis.uiRepresentation}\n\n--- Full Text Content ---\n\n$screenText"
                }
            }

            // Check for images in the content and handle them
            val hasImages = screenText.contains(Regex("(?i)(image|icon|photo|picture|graphic|logo|avatar)"))
            val imageNote = if (hasImages) {
                "\n\n📷 Note: This page contains images/icons. Visual descriptions would require image recognition capabilities."
            } else {
                ""
            }

            // Send to LLM for simplification
            val simplifyPrompt = """
You are a content simplifier. The user is viewing a screen/app on their Android device.
Your job is to simplify the information on the screen into clear, easy-to-understand points.

Current screen content:
$fullContent$imageNote

Please provide:
1. A brief summary of what's on screen (1-2 sentences)
2. Key information extracted as bullet points (max 5-7 points)
3. Any actionable items or important warnings
4. If it's a PDF/document: summarize the main points in plain language
5. If it's an error/log: explain what went wrong in simple terms and suggest next steps
6. If it contains financial data/numbers: highlight the key figures

Keep language simple and direct. Focus on what the user needs to know.
Format your response clearly with headings and bullet points.
""".trimIndent()

            val simplifyHistory = listOf(
                "system" to listOf(TextPart("You are a helpful content simplifier. Keep responses concise and focused on key information. Use clear formatting.")),
                "user" to listOf(TextPart(simplifyPrompt))
            )

            val simplifiedContent = try {
                val apiPrefs = ApiPreferences.getInstance(this)
                val apiKey = apiPrefs.apiKeyFlow.first()
                if (apiKey.isNotBlank()) {
                    ApiKeyManager.setApiKeys(listOf(apiKey))
                }
                getReasoningModelApiResponse(simplifyHistory, this)
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to get simplified content", e)
                null
            }

            visualFeedbackManager.hideThinkingIndicator()

            if (simplifiedContent != null) {
                val displayText = buildString {
                    append("📋 Simplified Content:\n\n")
                    append(simplifiedContent)
                    if (hasImages) {
                        append("\n\n💡 Tip: Images detected. For visual descriptions, describe what you see and I can help explain.")
                    }
                    if (needsScrolling) {
                        append("\n\n📄 Note: This was a lengthy page. I captured all available content for simplification.")
                    }
                }

                visualFeedbackManager.updateStatusText(displayText)
                speechCoordinator.speakToUser("Here's a simplified summary of the current page.")
                visualFeedbackManager.appendStatusText("\n\n🗣️ Summary spoken")
            } else {
                visualFeedbackManager.updateStatusText("❌ Unable to simplify page. The AI service could not be reached.")
                speechCoordinator.speakToUser("I couldn't simplify the page. Please try again.")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error simplifying page", e)
            visualFeedbackManager.hideThinkingIndicator()
            visualFeedbackManager.updateStatusText("❌ Error simplifying page: ${e.message}")
            speechCoordinator.speakToUser("There was an error simplifying the page.")
        }
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

        initializeOverlays()

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

        // Reset for next text mode session
        actionStatusViewNotShownYet = true

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

        // Update status display with spoken text
        if (isTextModeActive) {
            visualFeedbackManager.appendStatusText("🗣️ $text")
        }

        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Operit said: $text")

        if (isTextModeActive) {
            // Text mode: keep input box and status visible, don't start listening
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

            conversationHistory = addResponse("user", userInput, conversationHistory)

            // Show action status display only when user actually submits a prompt
            if (isTextModeActive && actionStatusViewNotShownYet) {
                actionStatusViewNotShownYet = false
                visualFeedbackManager.showActionStatusView(
                    onSimplifyPage = {
                        serviceScope.launch { handleSimplifyPage() }
                    },
                    onPausePlay = { isPaused ->
                        if (isPaused) {
                            speechCoordinator.pause()
                        } else {
                            speechCoordinator.resume()
                        }
                    }
                )
            }

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    gracefulShutdown("Goodbye!", "command")
                    return@launch
                }

                stateManager.setState(OperitState.PROCESSING)
                visualFeedbackManager.showThinkingIndicator("🧠 Thinking...")

                // Update action status with processing status
                if (isTextModeActive) {
                    visualFeedbackManager.updateStatusText("🧠 Processing your request...")
                }

                val defaultJsonResponse = """{"Type": "Reply", "Reply": "I couldn't reach the AI service. Please check your API provider configuration in Models & Parameters settings, and make sure your API key is valid.", "Instruction": "", "Should End": "Continue"}"""
                val rawModelResponse = try {
                    Log.d("ConvAgent", "Calling LLM API with conversation history size: ${conversationHistory.size}")
                    val apiResult = getReasoningModelApiResponse(conversationHistory, this@ConversationalAgentService)
                    if (apiResult == null) {
                        Log.e("ConvAgent", "LLM API returned null — most likely cause: invalid API key (needs a Google Gemini key, not a DeepSeek key)")
                    } else {
                        Log.d("ConvAgent", "LLM API returned successfully (first 200 chars): ${apiResult.take(200)}")
                    }
                    apiResult ?: defaultJsonResponse
                } catch (e: Exception) {
                    Log.e("ConvAgent", "LLM call failed with exception: ${e.message}", e)
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
                            conversationHistory = addResponse("model", busyMessage, conversationHistory)
                            return@launch
                        }

                        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
                            speakAndThenListen(getString(R.string.accessibility_permission_needed_for_task))
                            conversationHistory = addResponse("model", getString(R.string.accessibility_permission_needed_for_task), conversationHistory)
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
                                    conversationHistory = addResponse("model", "Clarification needed for task: ${decision.instruction}", conversationHistory)
                                    speakAndThenListen(questionToAsk, false)
                                } else {
                                    Log.d("ConvAgent", "Task is clear. Executing: ${decision.instruction}")
                                    AgentService.start(applicationContext, decision.instruction)
                                    conversationHistory = addResponse("model", decision.reply, conversationHistory)
                                    gracefulShutdown(decision.reply, "task_executed")
                                }
                            } else {
                                Log.d("ConvAgent", "Max clarification attempts reached. Proceeding with task execution.")
                                AgentService.start(applicationContext, decision.instruction)
                                conversationHistory = addResponse("model", decision.reply, conversationHistory)
                                gracefulShutdown(decision.reply, "task_executed")
                            }
                    }
                    "KillTask" -> {
                        Log.d("ConvAgent", "Model requested to kill the running agent service.")
                        if (AgentService.isRunning) {
                            AgentService.stop(applicationContext)
                            conversationHistory = addResponse("model", decision.reply, conversationHistory)
                            gracefulShutdown(decision.reply, "task_killed")
                        } else {
                            val noTaskMessage = "There was no automation running, but I can help with something else."
                            conversationHistory = addResponse("model", noTaskMessage, conversationHistory)
                            speakAndThenListen(noTaskMessage)
                        }
                    }
                    else -> {
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            gracefulShutdown(decision.reply, "model_ended")
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
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
                conversationHistory = conversationHistory.toMutableList().apply {
                    set(0, "user" to listOf(TextPart(updatedPrompt)))
                }
                Log.d("ConvAgent", "Updated system prompt with screen context and memories")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with memories and screen context", e)
        }
    }

    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val cleanedResponse = extractJsonFromResponse(response)
            Log.d("ConvAgent", "Cleaned response for parsing: $cleanedResponse")
            Log.d("ConvAgent", "Raw LLM response (first 500 chars): ${response.take(500)}")
            val json = JSONObject(cleanedResponse)
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
            Log.e("ConvAgent", "JSON parse error. Raw response: ${response.take(500)}", e)
            val fallbackReply = response.take(200).replace("\"", "").replace("\n", " ")
            return ModelDecision("Reply", fallbackReply, "", false)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Generic error parsing model response. Raw response: ${response.take(500)}", e)
            val fallbackReply = response.take(200).replace("\"", "").replace("\n", " ")
            return ModelDecision("Reply", fallbackReply, "", false)
        }
    }

    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()
        if (trimmed.startsWith("{")) return trimmed

        val jsonBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        return trimmed
    }

    private fun displayClarificationQuestions(questions: List<String>) {
        mainHandler.post {
            val topMargin = 100
            val verticalSpacing = 20
            var accumulatedHeight = 0

            questions.forEachIndexed { index, questionText ->
                val textView = TextView(this).apply {
                    text = questionText
                    // Operit: Teal accent with dark navy background (not purple/blue)
                    val accentBorder = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf("#00D4AA".toColorInt(), "#FFB84C".toColorInt()) // Teal to amber
                    ).apply { cornerRadius = 20f }

                    val cardBackground = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xEE1A1A2E.toInt(), 0xEE16213E.toInt()) // Dark navy
                    ).apply {
                        cornerRadius = 18f
                        setStroke(2, 0x6600D4AA.toInt()) // Subtle teal border
                    }

                    val layerDrawable = LayerDrawable(arrayOf(accentBorder, cardBackground)).apply {
                        setLayerInset(1, 3, 3, 3, 3)
                    }
                    background = layerDrawable
                    setTextColor(0xFFE8E8E8.toInt())
                    textSize = 15f
                    setPadding(36, 22, 36, 22)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
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
        visualFeedbackManager.hideActionStatusView()
        actionStatusViewNotShownYet = true

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
            visualFeedbackManager.hideActionStatusView()
            visualFeedbackManager.hideThinkingIndicator()
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
        actionStatusViewNotShownYet = true

        stateManager.setState(OperitState.IDLE)
        stateManager.stopMonitoring()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()
        visualFeedbackManager.hideActionStatusView()
        visualFeedbackManager.hideThinkingIndicator()
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
