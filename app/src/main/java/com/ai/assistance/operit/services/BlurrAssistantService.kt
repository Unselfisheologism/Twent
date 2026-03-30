package com.ai.assistance.operit.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechService
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.agent.v2.Agent
import com.ai.assistance.operit.core.agent.v2.AgentSettings
import com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.v2.fs.FileSystem
import com.ai.assistance.operit.core.agent.v2.llm.OperitLlmApi
import com.ai.assistance.operit.core.agent.v2.message.MemoryManager
import com.ai.assistance.operit.core.agent.v2.perception.Perception
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.api.automation.Finger
import com.ai.assistance.operit.overlay.BlurrStyleOverlayManager
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@SuppressLint("ClickableViewAccessibility")
class BlurrAssistantService : Service() {
    private val TAG = "BlurrAssistantService"
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var overlayManager: BlurrStyleOverlayManager
    private var agent: Agent? = null
    private var isAgentRunning = false
    private var currentTask: String? = null
    private var voiceListenerJob: Job? = null
    private var speechService: SpeechService? = null
    private var pendingVoiceCallback: ((String?) -> Unit)? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "blurr_assistant_channel"
        const val ACTION_START = "com.ai.assistance.operit.action.BLURR_ASSISTANT_START"
        const val ACTION_STOP = "com.ai.assistance.operit.action.BLURR_ASSISTANT_STOP"
        const val EXTRA_TASK = "task"
        const val EXTRA_MAX_STEPS = "max_steps"
        const val EXTRA_AUTO_VOICE = "auto_voice"

        @Volatile
        private var instance: BlurrAssistantService? = null

        fun getInstance(): BlurrAssistantService? = instance

        fun start(context: Context, task: String? = null, maxSteps: Int = 150, autoVoice: Boolean = true) {
            val intent = Intent(context, BlurrAssistantService::class.java).apply {
                action = ACTION_START
                task?.let { putExtra(EXTRA_TASK, it) }
                putExtra(EXTRA_MAX_STEPS, maxSteps)
                putExtra(EXTRA_AUTO_VOICE, autoVoice)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startAutomation(context: Context, task: String, maxSteps: Int = 150) {
            start(context, task, maxSteps, autoVoice = false)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlurrAssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "onCreate")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
        
        acquireWakeLock()
        
        overlayManager = BlurrStyleOverlayManager.getInstance(this)
        
        val manager = OverlayManager.getInstance(this)
        manager.startObserving()
        
        try {
            speechService = SpeechServiceFactory.getInstance(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init speech service", e)
        }
        
        Log.d(TAG, "Blurr assistant service ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val task = intent.getStringExtra(EXTRA_TASK)
                val maxSteps = intent.getIntExtra(EXTRA_MAX_STEPS, 150)
                val autoVoice = intent.getBooleanExtra(EXTRA_AUTO_VOICE, true)
                
                if (task.isNullOrBlank()) {
                    showInputAndWaitForTask(maxSteps, autoVoice)
                } else {
                    currentTask = task
                    startAgent(task, maxSteps)
                }
            }
        }

        return START_STICKY
    }

    private fun showInputAndWaitForTask(maxSteps: Int, autoVoice: Boolean) {
        overlayManager.showInputBox(
            onSubmit = { message ->
                currentTask = message
                startAgent(message, maxSteps)
            },
            onMicClick = {
                startVoiceInput { text ->
                    if (!text.isNullOrBlank()) {
                        currentTask = text
                        startAgent(text, maxSteps)
                    }
                }
            }
        )
        
        overlayManager.showStatus("What would you like me to do?")

        if (autoVoice) {
            startVoiceInput { text ->
                if (!text.isNullOrBlank()) {
                    currentTask = text
                    startAgent(text, maxSteps)
                }
            }
        }
    }

    private fun startAgent(task: String, maxSteps: Int) {
        if (isAgentRunning) {
            Log.w(TAG, "Agent already running")
            return
        }

        isAgentRunning = true
        overlayManager.hideInputBox()
        
        overlayManager.showStatus("Starting: ${task.take(30)}...")
        overlayManager.showThinking("Analyzing screen...")

        serviceScope.launch {
            try {
                val settings = AgentSettings(maxSteps = maxSteps)
                val eyes = Eyes(this@BlurrAssistantService)
                val finger = Finger(this@BlurrAssistantService)
                val semanticParser = com.ai.assistance.operit.core.agent.v2.perception.SemanticParser()
                val perception = Perception(eyes, semanticParser)
                val fileSystem = FileSystem(this@BlurrAssistantService)
                val memoryManager = MemoryManager(
                    context = this@BlurrAssistantService,
                    task = task,
                    fileSystem = fileSystem,
                    settings = settings
                )
                val llmApi = OperitLlmApi(
                    modelName = "default",
                    context = this@BlurrAssistantService
                )
                val actionExecutor = ActionExecutor(finger)

                agent = Agent(
                    settings = settings,
                    memoryManager = memoryManager,
                    perception = perception,
                    llmApi = llmApi,
                    actionExecutor = actionExecutor,
                    fileSystem = fileSystem,
                    context = this@BlurrAssistantService
                )

                overlayManager.updateThinking("Running automation...")
                
                agent?.run(task, maxSteps)
                
                overlayManager.hideThinking()
                overlayManager.showStatus("Task completed!")
                delay(3000)
                showCompletionAndWait(task)
                
            } catch (e: Exception) {
                Log.e(TAG, "Agent error", e)
                overlayManager.hideThinking()
                overlayManager.showStatus("Error: ${e.message}")
                delay(3000)
                showInputAndWaitForTask(maxSteps, false)
            } finally {
                isAgentRunning = false
            }
        }
    }

    private fun showCompletionAndWait(originalTask: String) {
        overlayManager.showInputBox(
            onSubmit = { message ->
                currentTask = message
                startAgent(message, 150)
            },
            onMicClick = {
                startVoiceInput { text ->
                    if (!text.isNullOrBlank()) {
                        currentTask = text
                        startAgent(text, 150)
                    }
                }
            }
        )
        overlayManager.showStatus("Done: $originalTask")
    }

    private fun startVoiceInput(onResult: (String?) -> Unit) {
        pendingVoiceCallback = onResult
        
        val service = speechService
        if (service == null) {
            Log.e(TAG, "Speech service not available")
            onResult(null)
            return
        }

        voiceListenerJob?.cancel()
        voiceListenerJob = serviceScope.launch {
            try {
                service.recognitionResultFlow.collect { result ->
                    if (result.isFinal && result.text.isNotBlank()) {
                        pendingVoiceCallback?.invoke(result.text)
                        pendingVoiceCallback = null
                        service.cancelRecognition()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice flow error", e)
                pendingVoiceCallback?.invoke(null)
                pendingVoiceCallback = null
            }
        }

        serviceScope.launch {
            try {
                val success = service.startRecognition(continuousMode = false, partialResults = true)
                if (!success) {
                    Log.e(TAG, "Failed to start recognition")
                    pendingVoiceCallback?.invoke(null)
                    pendingVoiceCallback = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start recognition error", e)
                pendingVoiceCallback?.invoke(null)
                pendingVoiceCallback = null
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        
        isAgentRunning = false
        agent = null
        voiceListenerJob?.cancel()
        
        serviceScope.cancel()
        
        try {
            speechService?.cancelRecognition()
            speechService?.shutdown()
            VoiceServiceFactory.getInstance(this)?.stop()
        } catch (_: Exception) {}
        
        releaseWakeLock()
        
        OverlayDispatcher.clearAll()
        val manager = OverlayManager.getInstance(this)
        manager.stopObserving()
        overlayManager.hideAll()
        
        instance = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OperitApp:BlurrAssistantWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Blurr Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Simple assistant overlay"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Blurr Assistant")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
}