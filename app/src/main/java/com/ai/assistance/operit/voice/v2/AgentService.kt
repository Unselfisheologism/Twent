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
import com.ai.assistance.operit.voice.utilities.ApiKeyManager
import com.ai.assistance.operit.voice.api.Eyes
import com.ai.assistance.operit.voice.api.Finger
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.voice.utilities.VisualFeedbackManager
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.voice.v2.actions.ActionExecutor
import com.ai.assistance.operit.voice.v2.fs.FileSystem
import com.ai.assistance.operit.voice.v2.llm.V2LlmApi
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

class AgentService : Service() {

    private val TAG = "AgentService"

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }

    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    private lateinit var agent: Agent
    private lateinit var settings: AgentSettings
    private lateinit var fileSystem: FileSystem
    private lateinit var memoryManager: MemoryManager
    private lateinit var perception: Perception
    private lateinit var llmApi: GeminiApi
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

        fun stop(context: Context) {
            Log.d("AgentService", "External stop request received.")
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun start(context: Context, task: String) {
            Log.d("AgentService", "Starting service with task: $task")
            val intent = Intent(context, AgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
            try {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.ai.assistance.operit.voice.utilities.FreemiumManager.getInstance(context).decrementTaskCount()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
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

        settings = AgentSettings()
        fileSystem = FileSystem(this)
        memoryManager = MemoryManager(this, "", fileSystem, settings)
        perception = Perception(Eyes(this), SemanticParser())
        llmApi = V2LlmApi(
            "gemini-2.5-flash",
            apiKeyManager = ApiKeyManager,
            context = this,
            maxRetry = 10
        )
        actionExecutor = ActionExecutor(Finger(this))
        agent = Agent(
            settings,
            memoryManager,
            perception,
            llmApi,
            actionExecutor,
            fileSystem,
            this
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_TASK)?.let {
            if (it.isNotBlank()) {
                Log.d(TAG, "Adding task to queue: $it")
                taskQueue.add(it)
            }
        }

        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            serviceScope.launch {
                processTaskQueue()
            }
        } else {
            if(isRunning) Log.d(TAG, "Task added to queue. Processor is already running.")
            else Log.d(TAG, "Service started with no task, waiting for tasks.")
        }

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true

        Log.i(TAG, "Starting task processing loop.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.poll() ?: continue
            currentTask = task

            notificationManager.notify(NOTIFICATION_ID, createNotification("Agent is running task: $task"))

            try {
                Log.i(TAG, "Executing task: $task")
                agent.run(task)
                Log.i(TAG, "Task completed successfully: $task")
            } catch (e: Exception) {
                Log.e(TAG, "Task failed with an exception: $task", e)
            }
        }

        Log.i(TAG, "Task queue is empty. Stopping service.")
        stopSelf()
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
        visualFeedbackManager.hideTtsWave()
        Log.i(TAG, "Service destroyed and all resources cleaned up.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

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
            .setContentTitle("Operit Doing Task (Expand to stop Operit)")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Operit",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
