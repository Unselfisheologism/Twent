package com.ai.assistance.operit.services.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.services.FloatingChatService
import kotlinx.coroutines.*
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

@RequiresApi(Build.VERSION_CODES.R)
class AutomationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val taskQueue: Queue<AutomationTask> = ConcurrentLinkedQueue()
    
    companion object {
        private const val TAG = "AutomationForegroundService"
        private const val NOTIFICATION_CHANNEL_ID = "AutomationServiceChannel"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_TASK = "automation_task"
        private const val EXTRA_MAX_STEPS = "max_steps"
        private const val ACTION_STOP_SERVICE = "com.ai.assistance.operit.action.STOP_AUTOMATION"

        @Volatile
        var isRunning: Boolean = false
            private set

        private val taskCallbacks = ConcurrentLinkedQueue<(Boolean, String) -> Unit>()

        fun start(context: Context, task: String, maxSteps: Int = 150, onComplete: ((Boolean, String) -> Unit)? = null) {
            Log.d(TAG, "Starting automation service with task: $task")
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                putExtra(EXTRA_TASK, task)
                putExtra(EXTRA_MAX_STEPS, maxSteps)
            }
            onComplete?.let { callback ->
                taskCallbacks.add(callback)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Log.d(TAG, "Stopping automation service")
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    data class AutomationTask(
        val task: String,
        val maxSteps: Int,
        val onComplete: ((Boolean, String) -> Unit)?
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        val task = intent?.getStringExtra(EXTRA_TASK)
        val maxSteps = intent?.getIntExtra(EXTRA_MAX_STEPS, 150) ?: 150

        if (!task.isNullOrBlank()) {
            Log.d(TAG, "Adding task to queue: $task")
            
            val callbacks = mutableListOf<(Boolean, String) -> Unit>()
            while (taskCallbacks.isNotEmpty()) {
                callbacks.add(taskCallbacks.poll())
            }
            
            taskQueue.add(AutomationTask(task, maxSteps) { success, message ->
                callbacks.forEach { it.invoke(success, message) }
            })
        }

        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Service not running, starting task processing.")
            serviceScope.launch {
                processTaskQueue()
            }
        }

        return START_STICKY
    }

    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true

        Log.i(TAG, "Starting task processing loop.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification("Starting automation..."))

        while (taskQueue.isNotEmpty()) {
            val automationTask = taskQueue.poll() ?: continue
            currentTask = automationTask.task

            notificationManager.notify(NOTIFICATION_ID, createNotification("Running: ${automationTask.task}"))

            try {
                Log.i(TAG, "Executing task: ${automationTask.task}")
                
                // Get the AutomationController and run the task
                val controller = AutomationController.getInstance(applicationContext)
                controller.runAutomationTask(
                    task = automationTask.task,
                    maxSteps = automationTask.maxSteps,
                    onStatusChange = { status ->
                        Log.d(TAG, "Status: $status")
                    },
                    onComplete = { success, message ->
                        Log.i(TAG, "Task completed: success=$success, message=$message")
                        automationTask.onComplete?.invoke(success, message)
                    }
                )
                
                // Wait for the task to complete
                while (controller.isRunning()) {
                    delay(500)
                }
                
                Log.i(TAG, "Task execution completed: ${automationTask.task}")
            } catch (e: Exception) {
                Log.e(TAG, "Task failed with exception: ${automationTask.task}", e)
                automationTask.onComplete?.invoke(false, "Error: ${e.message}")
            }
        }

        Log.i(TAG, "Task queue is empty. Stopping service.")
        stopSelf()
    }

    private var currentTask: String? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        isRunning = false
        currentTask = null
        taskQueue.clear()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Automation Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows automation progress"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, AutomationForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Operit Automation")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}