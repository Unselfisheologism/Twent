package com.ai.assistance.operit.api.chat.autonomous

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.brain.TwGlobalBrain
import com.ai.assistance.operit.api.chat.brain.TwMemoryEntry
import com.ai.assistance.operit.core.workflow.AutonomousAgentWorker
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.AutonomousAgentPreferences
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.services.automation.OperitAutomationService
import com.ai.assistance.operit.ui.MainActivity
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TwAutonomousAgent — Continuous AI agent that runs in the background,
 * observes user behavior, learns patterns, generates deliverables, and notifies the user.
 *
 * OBSERVE → LEARN → CREATE → DELIVER cycle:
 * - OBSERVE: Record app usage, workflow history, overlay activity as TwGlobalBrain memory
 * - LEARN: Call AI to analyze observations and build behavioral model
 * - CREATE: Generate content (social posts, news, followups, automation ideas)
 * - DELIVER: Show notification with pending tasks, user reviews and approves
 */
class TwAutonomousAgent private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TwAutonomousAgent"
        const val NOTIFICATION_ID = 3001
        const val CHANNEL_ID = "AUTONOMOUS_AGENT"

        @Volatile
        private var INSTANCE: TwAutonomousAgent? = null

        fun getInstance(ctx: Context): TwAutonomousAgent {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwAutonomousAgent(ctx.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ─── Agent Modes ────────────────────────────────────────────────────────────

    enum class AgentMode { OBSERVE, LEARN, CREATE, DELIVER, IDLE }

    enum class TaskType {
        SOCIAL_POST,
        EMAIL_DRAFT,
        CONTENT_SUMMARY,
        NEWS_DIGEST,
        TASK_SUGGESTION,
        AUTOMATION_IDEA
    }

    enum class TaskStatus { PENDING, APPROVED, REJECTED, DISMISSED }

    // ─── Task Data Class ────────────────────────────────────────────────────────

    data class AutonomousTask(
        val id: String = UUID.randomUUID().toString(),
        val type: TaskType,
        val title: String,
        val summary: String,
        val content: String,
        val confidence: Float,
        val sourceObservations: List<String>,
        val createdAt: Date = Date(),
        var status: TaskStatus = TaskStatus.PENDING
    )

    // ─── Dependencies ──────────────────────────────────────────────────────────

    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val globalBrain = TwGlobalBrain.getInstance(context)
    private val workflowRepo by lazy { WorkflowRepository(context) }
    private val prefs by lazy { AutonomousAgentPreferences(context) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    // ─── State ─────────────────────────────────────────────────────────────────

    private val _mode = MutableStateFlow(AgentMode.IDLE)
    val mode: StateFlow<AgentMode> = _mode.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _pendingTasks = MutableStateFlow<List<AutonomousTask>>(emptyList())
    val pendingTasks: StateFlow<List<AutonomousTask>> = _pendingTasks.asStateFlow()

    private var loopJob: Job? = null
    private val taskQueue = mutableListOf<AutonomousTask>()

    // ─── Start / Stop ──────────────────────────────────────────────────────────

    fun start() {
        if (_isEnabled.value) return

        AppLogger.i(TAG, "Starting autonomous agent")
        _isEnabled.value = true
        _mode.value = AgentMode.OBSERVE

        createNotificationChannel()

        loopJob = agentScope.launch {
            runAutonomousLoop()
        }

        schedulePeriodicChecks()
        AppLogger.i(TAG, "Autonomous agent started")
    }

    fun stop() {
        if (!_isEnabled.value) return

        AppLogger.i(TAG, "Stopping autonomous agent")
        _isEnabled.value = false
        _mode.value = AgentMode.IDLE
        loopJob?.cancel()
        loopJob = null
        cancelPeriodicChecks()
        AppLogger.i(TAG, "Autonomous agent stopped")
    }

    // ─── The Main Loop ─────────────────────────────────────────────────────────

    private suspend fun runAutonomousLoop() {
        while (_isEnabled.value) {
            try {
                when (_mode.value) {
                    AgentMode.IDLE -> {
                        delay(60_000)
                    }
                    AgentMode.OBSERVE -> {
                        performObservation()
                        delay(5 * 60_000L)
                        _mode.value = AgentMode.LEARN
                    }
                    AgentMode.LEARN -> {
                        performLearning()
                        _mode.value = AgentMode.CREATE
                    }
                    AgentMode.CREATE -> {
                        val tasks = performContentGeneration()
                        synchronized(taskQueue) {
                            taskQueue.addAll(tasks)
                        }
                        val pending = taskQueue.filter { it.status == TaskStatus.PENDING }
                        _pendingTasks.value = pending
                        if (pending.isNotEmpty()) {
                            _mode.value = AgentMode.DELIVER
                            showNotification()
                        } else {
                            _mode.value = AgentMode.OBSERVE
                        }
                    }
                    AgentMode.DELIVER -> {
                        delay(30 * 60_000L)
                        cleanupOldTasks()
                        _mode.value = AgentMode.OBSERVE
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Autonomous loop error", e)
                delay(60_000)
            }
        }
    }

    // ─── OBSERVE ───────────────────────────────────────────────────────────────

    /**
     * Trigger observation cycle from WorkManager.
     */
    suspend fun triggerObservation() {
        if (!_isEnabled.value) {
            _isEnabled.value = true
            _mode.value = AgentMode.OBSERVE
        }
        performObservation()
    }

    /**
     * Observe mode: record app usage, workflow history, overlay activity as memory.
     * Passive observation feeding into the behavioral model.
     */
    private suspend fun performObservation() {
        _mode.value = AgentMode.OBSERVE

        // 1. Record app usage patterns
        val recentApps = getRecentAppUsage()
        if (recentApps.isNotEmpty()) {
            globalBrain.remember(
                title = "App usage: ${dateFormat.format(Date())}",
                content = "Used apps: ${recentApps.joinToString(", ")}",
                category = "behavior",
                importance = 0.3f,
                source = "autonomous_observer",
                tags = listOf("app-usage", "behavior")
            )
        }

        // 2. Record workflow executions
        val recentWorkflows = getRecentWorkflowResults()
        recentWorkflows.forEach { result ->
            globalBrain.remember(
                title = "Workflow: ${result.name}",
                content = "Result: ${result.outcome}. Duration: ${result.duration}",
                category = "workflow",
                importance = 0.4f,
                source = "autonomous_observer",
                tags = listOf("workflow", result.name)
            )
        }

        // 3. Record overlay agent activity
        val overlayActivity = getRecentOverlayActivity()
        overlayActivity.forEach { activity ->
            globalBrain.remember(
                title = "Overlay task: ${activity.task}",
                content = "Outcome: ${activity.outcome}",
                category = "overlay_activity",
                importance = 0.4f,
                source = "autonomous_observer",
                tags = listOf("overlay", "automation")
            )
        }

        AppLogger.d(TAG, "Observation complete: ${recentApps.size} apps, ${recentWorkflows.size} workflows, ${overlayActivity.size} overlay events")
    }

    // ─── LEARN ─────────────────────────────────────────────────────────────────

    /**
     * Trigger learning cycle from WorkManager.
     */
    suspend fun triggerLearning() {
        if (!_isEnabled.value) {
            _isEnabled.value = true
            _mode.value = AgentMode.LEARN
        }
        performLearning()
    }

    /**
     * Learn mode: analyze recent observations and synthesize behavioral patterns.
     * Runs periodically to build a behavioral model.
     */
    private suspend fun performLearning() {
        _mode.value = AgentMode.LEARN

        val recentObservations = mutableListOf<TwMemoryEntry>()
        withContext(Dispatchers.IO) {
            val searchResults = globalBrain.recall("autonomous_observer behavior workflow")
            recentObservations.addAll(
                searchResults.filter {
                    it.source == "autonomous_observer" &&
                    it.createdAt.after(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L))
                }
            )
        }

        if (recentObservations.size < 5) {
            AppLogger.d(TAG, "Not enough observations to learn from (${recentObservations.size}/5)")
            return
        }

        val analysisPrompt = buildString {
            appendLine("You are analyzing a user's behavior from the past week to build a behavioral model.")
            appendLine("Observations:")
            recentObservations.forEach { obs ->
                appendLine("- [${obs.category}] ${obs.title}: ${obs.content}")
            }
            appendLine()
            appendLine("Identify patterns and create a brief behavioral summary:")
            appendLine("1. What apps/tools does this person use most?")
            appendLine("2. What kind of work are they doing?")
            appendLine("3. What recurring tasks could be automated?")
            appendLine("4. What content patterns exist in their work?")
            appendLine("5. What would be useful to generate for them tomorrow morning?")
            appendLine()
            appendLine("Respond with a concise behavioral summary (3-5 paragraphs).")
        }

        val analysis = callAIForAnalysis(analysisPrompt)

        globalBrain.remember(
            title = "Behavioral model: ${dateFormat.format(Date())}",
            content = analysis,
            category = "behavioral_model",
            importance = 0.8f,
            source = "autonomous_synthesis",
            tags = listOf("behavior", "model", "weekly")
        )

        AppLogger.d(TAG, "Behavioral model updated from ${recentObservations.size} observations")
    }

    // ─── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Trigger content generation from WorkManager.
     */
    suspend fun triggerContentGeneration() {
        if (!_isEnabled.value) {
            _isEnabled.value = true
            _mode.value = AgentMode.CREATE
        }
        performContentGeneration()
    }

    /**
     * Create mode: based on behavioral model, generate content useful for the user.
     * "Every morning there's a stack of posts waiting."
     */
    private suspend fun performContentGeneration(): List<AutonomousTask> {
        _mode.value = AgentMode.CREATE

        val behavioralModelObservations = mutableListOf<TwMemoryEntry>()
        withContext(Dispatchers.IO) {
            val results = globalBrain.recall("behavioral_model")
            behavioralModelObservations.addAll(
                results.filter { it.category == "behavioral_model" }
                    .sortedByDescending { it.createdAt }
                    .take(1)
            )
        }

        val behavioralModel = behavioralModelObservations.firstOrNull()

        if (behavioralModel == null) {
            AppLogger.d(TAG, "No behavioral model yet — skipping generation")
            return emptyList()
        }

        val pendingTasks = mutableListOf<AutonomousTask>()

        if (prefs.generateSocialPosts) {
            val socialTask = generateSocialContent(behavioralModel)
            if (socialTask != null) pendingTasks.add(socialTask)
        }

        if (prefs.generateNewsDigest) {
            val newsTask = generateNewsDigest(behavioralModel)
            if (newsTask != null) pendingTasks.add(newsTask)
        }

        if (prefs.generateFollowups) {
            val followupTask = generateFollowupReminders(behavioralModel)
            if (followupTask != null) pendingTasks.add(followupTask)
        }

        if (prefs.generateAutomationIdeas) {
            val automationTask = generateAutomationIdeas(behavioralModel)
            if (automationTask != null) pendingTasks.add(automationTask)
        }

        AppLogger.d(TAG, "Generated ${pendingTasks.size} content tasks")
        return pendingTasks
    }

    private suspend fun generateSocialContent(behavioralModel: TwMemoryEntry): AutonomousTask? {
        val recentWork = mutableListOf<TwMemoryEntry>()
        withContext(Dispatchers.IO) {
            val results = globalBrain.recall("recent work project")
            recentWork.addAll(
                results.filter {
                    it.tags.contains("project") || it.tags.contains("work") ||
                    it.content.contains("workflow", ignoreCase = true)
                }.filter {
                    it.createdAt.after(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
                }.take(5)
            )
        }

        if (recentWork.isEmpty()) return null

        val prompt = buildString {
            appendLine("Generate social media content (tweets/X posts) based on recent work activity.")
            appendLine()
            appendLine("Recent work activity:")
            recentWork.forEach { work ->
                appendLine("- ${work.title}: ${work.content.take(200)}")
            }
            appendLine()
            appendLine("Behavioral context: ${behavioralModel.content.take(500)}")
            appendLine()
            appendLine("Generate 3 tweet drafts (each under 280 characters) that reflect how this person thinks and works.")
            appendLine("Format as: 'Draft 1: [text]' on separate lines. Be authentic and concise.")
        }

        val drafts = callAIForAnalysis(prompt)

        return AutonomousTask(
            type = TaskType.SOCIAL_POST,
            title = "Social media drafts ready",
            summary = "Generated from ${recentWork.size} recent work items",
            content = drafts,
            confidence = 0.7f,
            sourceObservations = recentWork.map { it.title }
        )
    }

    private suspend fun generateNewsDigest(behavioralModel: TwMemoryEntry): AutonomousTask? {
        val prompt = buildString {
            appendLine("Based on the user's behavioral model, suggest 3-5 news topics or articles they should read.")
            appendLine()
            appendLine("Behavioral model: ${behavioralModel.content.take(600)}")
            appendLine()
            appendLine("For each suggestion, provide:")
            appendLine("- Topic title (brief)")
            appendLine("- Why it's relevant to this user (1 sentence)")
            appendLine("- Suggested search query (for finding articles)")
            appendLine()
            appendLine("Format as a numbered list. Be specific and relevant.")
        }

        val digest = callAIForAnalysis(prompt)

        return AutonomousTask(
            type = TaskType.NEWS_DIGEST,
            title = "News digest for you",
            summary = "Curated based on your interests and activity",
            content = digest,
            confidence = 0.6f,
            sourceObservations = listOf(behavioralModel.title)
        )
    }

    private suspend fun generateFollowupReminders(behavioralModel: TwMemoryEntry): AutonomousTask? {
        val followupObservations = mutableListOf<TwMemoryEntry>()
        withContext(Dispatchers.IO) {
            val results = globalBrain.recall("meeting contact followup")
            followupObservations.addAll(
                results.filter {
                    it.createdAt.after(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L))
                }.take(10)
            )
        }

        if (followupObservations.isEmpty()) return null

        val prompt = buildString {
            appendLine("Based on recent interactions, suggest follow-up tasks the user should consider.")
            appendLine()
            appendLine("Recent activity:")
            followupObservations.forEach { obs ->
                appendLine("- ${obs.title}: ${obs.content.take(100)}")
            }
            appendLine()
            appendLine("Generate 2-3 follow-up reminders (brief, actionable).")
            appendLine("Format as: 'Follow-up: [what to do] — [why]'")
        }

        val reminders = callAIForAnalysis(prompt)

        return AutonomousTask(
            type = TaskType.TASK_SUGGESTION,
            title = "Follow-up reminders",
            summary = "Based on your recent interactions",
            content = reminders,
            confidence = 0.5f,
            sourceObservations = followupObservations.map { it.title }
        )
    }

    private suspend fun generateAutomationIdeas(behavioralModel: TwMemoryEntry): AutonomousTask? {
        val workflowObservations = mutableListOf<TwMemoryEntry>()
        withContext(Dispatchers.IO) {
            val results = globalBrain.recall("workflow automation")
            workflowObservations.addAll(
                results.filter {
                    it.category == "workflow" || it.category == "overlay_activity"
                }.take(10)
            )
        }

        if (workflowObservations.isEmpty()) return null

        val prompt = buildString {
            appendLine("Based on the user's workflow and automation activity, suggest 1-2 new automations that could save them time.")
            appendLine()
            appendLine("Current automations and workflows:")
            workflowObservations.forEach { obs ->
                appendLine("- ${obs.title}: ${obs.content.take(100)}")
            }
            appendLine()
            appendLine("Behavioral context: ${behavioralModel.content.take(400)}")
            appendLine()
            appendLine("For each suggestion, describe:")
            appendLine("- What the automation would do")
            appendLine("- How often it would run")
            appendLine("- What trigger would start it")
            appendLine()
            appendLine("Be specific and actionable. Focus on repetitive tasks.")
        }

        val ideas = callAIForAnalysis(prompt)

        return AutonomousTask(
            type = TaskType.AUTOMATION_IDEA,
            title = "New automation ideas",
            summary = "Based on your workflow patterns",
            content = ideas,
            confidence = 0.6f,
            sourceObservations = workflowObservations.map { it.title }
        )
    }

    // ─── DELIVER ───────────────────────────────────────────────────────────────

    /**
     * Show notification with pending tasks for user review.
     */
    private fun showNotification() {
        val pending: List<AutonomousTask>
        synchronized(taskQueue) {
            pending = taskQueue.filter { it.status == TaskStatus.PENDING }
        }
        if (pending.isEmpty()) return

        val bigText = pending.joinToString("\n---\n") { task ->
            "• ${task.title}\n  ${task.summary}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_robot)
            .setContentTitle(context.getString(R.string.autonomous_agent_notification_title))
            .setContentText("${pending.size} items ready for review")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createPendingIntent("action=review"))
            .addAction(R.drawable.ic_agent_acp, "Review", createPendingIntent("action=review"))
            .addAction(R.drawable.ic_agent_acp, "Dismiss", createPendingIntent("action=dismiss"))
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        AppLogger.d(TAG, "Notification shown with ${pending.size} pending tasks")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.autonomous_agent_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.autonomous_agent_channel_desc)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ─── WorkManager Integration ─────────────────────────────────────────────────

    private fun schedulePeriodicChecks() {
        if (!prefs.enabled) return

        // Morning check: generate deliverables
        scheduleForTime("autonomous_morning", prefs.morningHour, 0)

        // Evening learning: synthesize patterns
        scheduleForTime("autonomous_evening", 20, 0)

        // Periodic observation: every N hours
        scheduleInterval("autonomous_observe", prefs.observeIntervalMinutes.toLong())
    }

    private fun scheduleForTime(tag: String, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()
        val work = OneTimeWorkRequestBuilder<AutonomousAgentWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(tag)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "tw_autonomous_$tag",
            ExistingWorkPolicy.REPLACE,
            work
        )
        AppLogger.d(TAG, "Scheduled one-time work: $tag at ${hour}:${minute}, delay=${delay}ms")
    }

    private fun scheduleInterval(tag: String, intervalMinutes: Long) {
        val work = PeriodicWorkRequestBuilder<AutonomousAgentWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES // flex interval
        )
            .addTag(tag)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tw_autonomous_$tag",
            ExistingPeriodicWorkPolicy.REPLACE,
            work
        )
        AppLogger.d(TAG, "Scheduled periodic work: $tag every ${intervalMinutes}min")
    }

    private fun cancelPeriodicChecks() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("autonomous_morning")
        workManager.cancelAllWorkByTag("autonomous_evening")
        workManager.cancelAllWorkByTag("autonomous_observe")
        AppLogger.d(TAG, "Cancelled all periodic checks")
    }

    // ─── AI Call Helper ─────────────────────────────────────────────────────────

    /**
     * Call AI for analysis using EnhancedAIService.
     * Returns the complete response as a String (collects the Flow).
     */
    private suspend fun callAIForAnalysis(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val service = EnhancedAIService.getInstance(context)
                val flow = service.sendMessage(
                    message = prompt,
                    chatId = "autonomous_agent",
                    chatHistory = emptyList(),
                    functionType = FunctionType.CHAT,
                    promptFunctionType = PromptFunctionType.CHAT,
                    enableThinking = false,
                    enableMemoryQuery = false,
                    maxTokens = 4096,
                    stream = true
                )

                val response = StringBuilder()
                flow.collect { chunk ->
                    response.append(chunk)
                }
                response.toString()
            } catch (e: Exception) {
                AppLogger.e(TAG, "AI call failed", e)
                "Unable to generate content at this time. Please try again later."
            }
        }
    }

    // ─── User Review ───────────────────────────────────────────────────────────

    /**
     * Approve a task — executes it and marks as approved.
     */
    fun approveTask(taskId: String): Boolean {
        val task: AutonomousTask?
        synchronized(taskQueue) {
            task = taskQueue.find { it.id == taskId }
        }
        if (task == null) return false

        task.status = TaskStatus.APPROVED
        _pendingTasks.value = taskQueue.filter { it.status == TaskStatus.PENDING }

        agentScope.launch {
            executeApprovedTask(task)
        }

        return true
    }

    /**
     * Dismiss a task — marks it as dismissed.
     */
    fun dismissTask(taskId: String): Boolean {
        val task: AutonomousTask?
        synchronized(taskQueue) {
            task = taskQueue.find { it.id == taskId }
        }
        if (task == null) return false

        task.status = TaskStatus.DISMISSED
        _pendingTasks.value = taskQueue.filter { it.status == TaskStatus.PENDING }
        return true
    }

    /**
     * Dismiss all pending tasks.
     */
    fun dismissAllTasks() {
        synchronized(taskQueue) {
            taskQueue.forEach { it.status = TaskStatus.DISMISSED }
        }
        _pendingTasks.value = emptyList()
    }

    /**
     * Get all pending tasks.
     */
    fun getPendingTasks(): List<AutonomousTask> {
        synchronized(taskQueue) {
            return taskQueue.filter { it.status == TaskStatus.PENDING }
        }
    }

    /**
     * Execute an approved task — copy to clipboard, etc.
     */
    private suspend fun executeApprovedTask(task: AutonomousTask) {
        when (task.type) {
            TaskType.SOCIAL_POST -> {
                copyToClipboard(task.content)
                AppLogger.d(TAG, "Social post copied to clipboard")
            }
            TaskType.EMAIL_DRAFT -> {
                copyToClipboard(task.content)
                AppLogger.d(TAG, "Email draft copied to clipboard")
            }
            TaskType.CONTENT_SUMMARY -> {
                copyToClipboard(task.content)
                AppLogger.d(TAG, "Content summary copied to clipboard")
            }
            TaskType.NEWS_DIGEST -> {
                copyToClipboard(task.content)
                AppLogger.d(TAG, "News digest copied to clipboard")
            }
            TaskType.TASK_SUGGESTION -> {
                copyToClipboard(task.content)
                AppLogger.d(TAG, "Task suggestions copied to clipboard")
            }
            TaskType.AUTOMATION_IDEA -> {
                globalBrain.remember(
                    title = "Automation idea: ${task.title}",
                    content = task.content,
                    category = "automation_idea",
                    importance = 0.6f,
                    source = "autonomous_agent",
                    tags = listOf("automation", "idea")
                )
                AppLogger.d(TAG, "Automation idea saved to memory")
            }
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Twent AI Content", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to copy to clipboard", e)
        }
    }

    private fun cleanupOldTasks() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        synchronized(taskQueue) {
            taskQueue.removeAll { it.status == TaskStatus.PENDING && it.createdAt.time < cutoff }
        }
        _pendingTasks.value = taskQueue.filter { it.status == TaskStatus.PENDING }
    }

    // ─── Data Sources ──────────────────────────────────────────────────────────

    /**
     * Get recent app usage using UsageStatsManager.
     * Requires PACKAGE_USAGE_STATS permission.
     */
    private fun getRecentAppUsage(): List<String> {
        if (!hasUsageStatsPermission()) {
            AppLogger.d(TAG, "No PACKAGE_USAGE_STATS permission — skipping app usage")
            return emptyList()
        }

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 30 * 60 * 1000L // Last 30 minutes

            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            stats
                ?.filter { it.totalTimeInForeground > 0 }
                ?.sortedByDescending { it.totalTimeInForeground }
                ?.take(5)
                ?.mapNotNull { stat ->
                    getAppName(stat.packageName)
                } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get app usage", e)
            emptyList()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val pm = context.packageManager
        return pm.checkPermission(
            Manifest.permission.PACKAGE_USAGE_STATS,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAppName(packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get recent workflow execution results from WorkflowRepository.
     */
    private suspend fun getRecentWorkflowResults(): List<WorkflowResult> {
        return withContext(Dispatchers.IO) {
            try {
                val workflows = workflowRepo.getAllWorkflows().getOrNull() ?: emptyList()
                workflows
                    .filter { it.totalExecutions > 0 }
                    .sortedByDescending { it.lastExecutionTime ?: 0L }
                    .take(5)
                    .mapNotNull { wf ->
                        val time = wf.lastExecutionTime ?: return@mapNotNull null
                        val age = System.currentTimeMillis() - time
                        if (age > 24 * 60 * 60 * 1000L) return@mapNotNull null

                        WorkflowResult(
                            name = wf.name,
                            outcome = wf.lastExecutionStatus?.name ?: "UNKNOWN",
                            duration = wf.lastExecutionTime?.let { "${(it / 1000)}s" } ?: "N/A"
                        )
                    }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get workflow results", e)
                emptyList()
            }
        }
    }

    data class WorkflowResult(
        val name: String,
        val outcome: String,
        val duration: String
    )

    /**
     * Get recent overlay activity from SharedPreferences.
     */
    private fun getRecentOverlayActivity(): List<OverlayActivity> {
        return try {
            val prefs: SharedPreferences = context.getSharedPreferences(
                "autonomous_overlay_log",
                Context.MODE_PRIVATE
            )
            val json = prefs.getString("activities", "[]") ?: "[]"
            val array = JSONArray(json)
            val activities = mutableListOf<OverlayActivity>()

            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timestamp = obj.optLong("timestamp", 0L)
                if (timestamp < cutoff) continue

                activities.add(
                    OverlayActivity(
                        task = obj.optString("task", "Unknown"),
                        outcome = obj.optString("outcome", "Unknown")
                    )
                )
            }
            activities.take(5)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get overlay activity", e)
            emptyList()
        }
    }

    data class OverlayActivity(
        val task: String,
        val outcome: String
    )

    // ─── Pending Intent ─────────────────────────────────────────────────────────

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("autonomous_action", action)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, action.hashCode(), intent, flags)
    }
}

