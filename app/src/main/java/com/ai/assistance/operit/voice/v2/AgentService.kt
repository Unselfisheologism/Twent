package com.ai.assistance.operit.voice.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.voice.api.Eyes
import com.ai.assistance.operit.voice.api.Finger
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.voice.utilities.VisualFeedbackManager
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.voice.v2.actions.ActionExecutor
import com.ai.assistance.operit.voice.v2.fs.FileSystem
import com.ai.assistance.operit.voice.v2.llm.AgentLlmAdapter
import com.ai.assistance.operit.voice.v2.message_manager.MemoryManager
import com.ai.assistance.operit.voice.v2.perception.Perception
import com.ai.assistance.operit.voice.v2.perception.SemanticParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A Foreground Service responsible for hosting and running the AI Agent.
 *
 * This service manages the entire lifecycle of the agent, from initializing its components
 * to running its main loop in a background coroutine. It starts as a foreground service
 * to ensure the OS does not kill it while it's performing a long-running task.
 */
class AgentService : Service() {

    private val TAG = "AgentService"

    // A dedicated coroutine scope tied to the service's lifecycle.
    // Using a SupervisorJob ensures that if one child coroutine fails, it doesn't cancel the whole scope.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }

    // Declare agent and its dependencies. They will be initialized in onCreate.
    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    private lateinit var agent: Agent
    private lateinit var settings: AgentSettings
    private lateinit var fileSystem: FileSystem
    private lateinit var memoryManager: MemoryManager
    private lateinit var perception: Perception
    private lateinit var llmAdapter: AgentLlmAdapter
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var overlayManager: OverlayManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannelV2"
        private const val NOTIFICATION_ID = 14
        private const val EXTRA_TASK = "com.ai.assistance.operit.voice.v2.EXTRA_TASK"
        private const val ACTION_STOP_SERVICE = "com.ai.assistance.operit.voice.v2.ACTION_STOP_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentTask: String? = null
            private set
        
        // Task control flags
        @Volatile
        var isTaskPaused: Boolean = false
            private set
        
        @Volatile
        var shouldStopTask: Boolean = false
            private set

        /**
         * A public method to request the service to stop from outside.
         */
        fun stop(context: Context) {
            Log.d("AgentService", "External stop request received.")
            shouldStopTask = true
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
        
        /**
         * Pause the current task
         */
        fun pauseTask() {
            Log.d("AgentService", "Task pause requested.")
            isTaskPaused = true
        }
        
        /**
         * Resume the current task
         */
        fun resumeTask() {
            Log.d("AgentService", "Task resume requested.")
            isTaskPaused = false
        }

        fun start(context: Context, task: String) {
            Log.d("AgentService", "Starting service with task: $task")
            val intent = Intent(context, AgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
            context.startService(intent)
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        overlayManager = OverlayManager.getInstance(this)
        OverlayDispatcher.clearAll()
        overlayManager.startObserving()

        visualFeedbackManager.showTtsWave()
        createNotificationChannel()

        settings = AgentSettings() // Use default settings for now
        fileSystem = FileSystem(this,)
        memoryManager = MemoryManager(this, "", fileSystem, settings)
        perception = Perception(Eyes(this), SemanticParser())
        llmAdapter = AgentLlmAdapter(
            context = this,
            maxRetry = 10
        )
        actionExecutor = ActionExecutor(Finger(this))
        agent = Agent(
            settings,
            memoryManager,
            perception,
            llmAdapter,
            actionExecutor,
            fileSystem,
            this
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        // Handle stop action
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            stopSelf() // onDestroy will handle cleanup
            return START_NOT_STICKY
        }

        // Add new task to the queue
        intent?.getStringExtra(EXTRA_TASK)?.let {
            if (it.isNotBlank()) {
                Log.d(TAG, "Adding task to queue: $it")
                taskQueue.add(it)
            }
        }

        // If the agent is not already processing tasks, start the loop.
        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            serviceScope.launch {
                processTaskQueue()
            }
        } else {
            if(isRunning) Log.d(TAG, "Task added to queue. Processor is already running.")
            else Log.d(TAG, "Service started with no task, waiting for tasks.")
        }

        // Use START_STICKY to ensure the service stays running in the background
        // until we explicitly stop it. This is crucial for a queue-based system.
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true
        shouldStopTask = false
        isTaskPaused = false

        Log.i(TAG, "Starting task processing loop.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))
        
        // Show top-left task controls when task starts
        visualFeedbackManager.showTopLeftTaskControls(
            onStopClicked = {
                Log.i(TAG, "Stop button clicked - stopping immediately")
                shouldStopTask = true
                // Hide controls immediately
                visualFeedbackManager.hideTopLeftTaskControls()
                visualFeedbackManager.hideTaskActiveGlow()
                // Stop the service
                stopSelf()
            },
            onPauseClicked = {
                Log.i(TAG, "Pause button clicked")
                isTaskPaused = true
                visualFeedbackManager.updateTaskPauseButtonIcon(isPaused = true)
            },
            onResumeClicked = {
                Log.i(TAG, "Resume button clicked")
                isTaskPaused = false
                visualFeedbackManager.updateTaskPauseButtonIcon(isPaused = false)
            }
        )
        visualFeedbackManager.showTaskActiveGlow()

        while (taskQueue.isNotEmpty()) {
            // Check if stop was requested
            if (shouldStopTask) {
                Log.i(TAG, "Stop requested during task execution")
                break
            }
            
            val task = taskQueue.poll() ?: continue
            currentTask = task

            // Update notification for the new task
            notificationManager.notify(NOTIFICATION_ID, createNotification("Agent is running task: $task"))

            try {
                Log.i(TAG, "Executing task: $task")
                agent.run(task)
                Log.i(TAG, "Task completed successfully: $task")
            } catch (e: Exception) {
                Log.e(TAG, "Task failed with an exception: $task", e)
            }
        }

        Log.i(TAG, "Task queue empty or stop requested. Cleaning up.")
        currentTask = null
        
        // Hide top-left controls after task completes
        visualFeedbackManager.hideTopLeftTaskControls()
        visualFeedbackManager.hideTaskActiveGlow()
        
        // Show input box for follow-up message
        visualFeedbackManager.showInputBox(
            onActivated = {},
            onSubmit = { submittedText ->
                Log.i(TAG, "Follow-up message: $submittedText")
                // Start a new task with the follow-up
                start(this@AgentService, submittedText)
            },
            onOutsideTap = {
                Log.i(TAG, "Follow-up input outside tap")
                visualFeedbackManager.hideInputBox()
            }
        )
        
        stopSelf()
    }

    /**
     * Ensures that the OperitAutomationService is connected and available.
     * Waits up to 5 seconds for the service to become available.
     */
    private suspend fun ensureAutomationServiceAvailable(): Boolean {
        var attempts = 0
        val maxAttempts = 50 // 5 seconds with 100ms delays
        
        while (attempts < maxAttempts) {
            if (com.ai.assistance.operit.services.automation.OperitAutomationService.instance != null) {
                Log.d(TAG, "OperitAutomationService is connected and available.")
                return true
            }
            
            if (attempts == 0) {
                Log.w(TAG, "OperitAutomationService not connected yet. Waiting for connection...")
            }
            
            kotlinx.coroutines.delay(100)
            attempts++
        }
        
        Log.e(TAG, "OperitAutomationService failed to connect after ${maxAttempts * 100}ms")
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        OverlayDispatcher.clearAll()
        overlayManager.stopObserving()
        isRunning = false
        currentTask = null
        taskQueue.clear()
        serviceScope.cancel()
        visualFeedbackManager.hideTopLeftTaskControls()
        visualFeedbackManager.hideTaskActiveGlow()
        visualFeedbackManager.hideTtsWave()
        // Do NOT hide input box - it may be needed for follow-up messages
        Log.i(TAG, "Service destroyed and all resources cleaned up.")
    }

    /**
     * This service does not provide binding, so we return null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Creates the NotificationChannel for the foreground service.
     * This is required for Android 8.0 (API level 26) and higher.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates the persistent notification for the foreground service.
     */
    private fun createNotification(contentText: String): Notification {

        val stopIntent = Intent(this, AgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Operit Doing Task (Expand to stop)")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true) // Makes notification persistent and harder to dismiss
             .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
