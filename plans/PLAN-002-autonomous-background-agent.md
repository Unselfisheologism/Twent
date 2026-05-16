# PLAN-002: Autonomous Background Agent
## Continuous AI Agent That Runs, Learns, and Delivers

---

## Problem Statement

HolaOS's killer demo: "I just focus on the work. Every morning there's a stack of posts waiting." The AI **runs continuously in the background**, watches user behavior, learns from it, generates deliverables, and notifies the user when it's done.

Twent's existing system:
- **Workflows**: Manual node-based automations triggered by cron — these are **rule-based**, not **AI-driven**. They execute the same steps every time.
- **AIForegroundService**: Keeps the app alive for voice/wake word but doesn't run AI autonomously
- **WorkflowAINodeExecutor**: Can call AI within a workflow, but it's still **workflow-driven** — a human designed the node graph

**What's missing:** A **continuous AI agent loop** that runs in the background, decides what to do based on learned user context, and delivers results via notification.

---

## Architecture

```
TwAutonomousAgent (new foreground service + WorkManager hybrid)
│
├── TwAgentBrain (shared — brain memory, behavioral model)
├── TwTaskQueue  (new — AI-generated task queue with user review)
├── TwNotificationManager (new — deliver results)
│
├── Modes:
│   ├── OBSERVE      — watch and record (passive)
│   ├── LEARN        — synthesize patterns (periodic)
│   ├── CREATE       — generate deliverables (on schedule)
│   └── DELIVER      — notify and review (user interaction)
```

**Execution layer:**
```
AIForegroundService (keeps process alive)
    └── TwAutonomousAgent (AI loop)
            ├── WorkManager (periodic triggers every 15min+)
            ├── TwGlobalBrain (shared memory)
            └── NotificationCompat (results delivery)
```

---

## Implementation Details

### Phase 1: `TwAutonomousAgent` — The Loop

**File:** `api/chat/autonomous/TwAutonomousAgent.kt` (NEW)

```kotlin
class TwAutonomousAgent(private val context: Context) {

    companion object {
        private const val TAG = "TwAutonomousAgent"
        const val NOTIFICATION_ID = 3001
        const val CHANNEL_ID = "AUTONOMOUS_AGENT"

        @Volatile private var INSTANCE: TwAutonomousAgent? = null

        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TwAutonomousAgent(ctx.applicationContext).also { INSTANCE = it }
        }
    }

    enum class AgentMode { OBSERVE, LEARN, CREATE, DELIVER, IDLE }

    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val globalBrain = TwGlobalBrain.getInstance(context)

    // Agent's working state
    private val _mode = MutableStateFlow(AgentMode.IDLE)
    val mode: StateFlow<AgentMode> = _mode.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Task queue: AI-generated tasks for user review
    private val taskQueue = mutableListOf<AutonomousTask>()

    data class AutonomousTask(
        val id: String = UUID.randomUUID().toString(),
        val type: TaskType,
        val title: String,
        val summary: String,           // What the AI did
        val content: String,           // The deliverable
        val confidence: Float,         // 0-1, how sure the AI is
        val sourceObservations: List<String>, // What the AI observed to create this
        val createdAt: Date = Date(),
        var status: TaskStatus = TaskStatus.PENDING
    )

    enum class TaskType {
        SOCIAL_POST,      // "Generated 5 tweet drafts from yesterday's work"
        EMAIL_DRAFT,      // "Follow-up email for Apollo lead John"
        CONTENT_SUMMARY,  // "Weekly digest of your learning"
        NEWS_DIGEST,      // "News you should read based on your interests"
        TASK_SUGGESTION,  // "You should follow up with Alice"
        AUTOMATION_IDEA   // "New automation: auto-reply to DMs"
    }

    enum class TaskStatus { PENDING, APPROVED, REJECTED, DISMISSED }

    // ─── Start/Stop ─────────────────────────────────────────────────────────

    fun start() {
        if (_isEnabled.value) return
        _isEnabled.value = true
        _mode.value = AgentMode.OBSERVE
        createNotificationChannel()

        agentScope.launch {
            runAutonomousLoop()
        }

        // Schedule periodic checks via WorkManager
        schedulePeriodicChecks()
    }

    fun stop() {
        _isEnabled.value = false
        _mode.value = AgentMode.IDLE
        cancelPeriodicChecks()
    }

    // ─── The Main Loop ──────────────────────────────────────────────────────

    private suspend fun runAutonomousLoop() {
        while (_isEnabled.value) {
            try {
                when (_mode.value) {
                    AgentMode.IDLE -> {
                        delay(60_000) // Check every minute in idle
                    }
                    AgentMode.OBSERVE -> {
                        performObservation()
                        delay(5 * 60_000) // Observe for 5 minutes
                    }
                    AgentMode.LEARN -> {
                        performLearning()
                        _mode.value = AgentMode.CREATE
                    }
                    AgentMode.CREATE -> {
                        val tasks = performContentGeneration()
                        taskQueue.addAll(tasks)
                        if (taskQueue.isNotEmpty()) {
                            _mode.value = AgentMode.DELIVER
                            showNotification()
                        } else {
                            _mode.value = AgentMode.OBSERVE
                        }
                    }
                    AgentMode.DELIVER -> {
                        delay(30 * 60_000) // Wait 30 min for user to review
                        // Auto-dismiss old pending tasks
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

    // ─── OBSERVE: Watch user behavior ───────────────────────────────────────

    /**
     * Observe mode: the AI watches what apps the user opens,
     * what workflows run, what the overlay agent does.
     * Records observations as memory entries.
     *
     * This is the HolaOS "learns how I think" equivalent —
     * passive observation that feeds into the behavioral model.
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

        // 3. Record overlay agent activity (what did it do?)
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

        AppLogger.d(TAG, "Observation complete: ${recentApps.size} apps, ${recentWorkflows.size} workflows")
    }

    // ─── LEARN: Synthesize patterns ─────────────────────────────────────────

    /**
     * Learn mode: analyze recent observations and synthesize
     * behavioral patterns. Like HolaOS's "learns your rhythm by Friday."
     *
     * Runs periodically (every few hours) to build a behavioral model.
     */
    private suspend fun performLearning() {
        _mode.value = AgentMode.LEARN

        val memory = globalBrain.getMemory()
        val recentObservations = memory.memories
            .filter { it.source == "autonomous_observer" }
            .filter { it.createdAt.after(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)) }

        if (recentObservations.size < 5) {
            AppLogger.d(TAG, "Not enough observations to learn from")
            return
        }

        // Synthesize via AI: "Analyze these observations and tell me what patterns exist"
        val analysisPrompt = buildString {
            appendLine("You are analyzing a user's behavior from the past week.")
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
            appendLine("5. What would be useful to generate for them?")
        }

        val analysis = callAIForAnalysis(analysisPrompt)

        // Store the behavioral model
        globalBrain.remember(
            title = "Behavioral model: ${dateFormat.format(Date())}",
            content = analysis,
            category = "behavioral_model",
            importance = 0.8f,
            source = "autonomous_synthesis",
            tags = listOf("behavior", "model", "weekly")
        )

        AppLogger.d(TAG, "Behavioral model updated")
    }

    // ─── CREATE: Generate deliverables ─────────────────────────────────────

    /**
     * Create mode: based on behavioral model, generate content
     * that would be useful for the user. Like HolaOS's
     * "every morning there's a stack of posts waiting."
     *
     * Runs on a schedule (e.g., every morning at 9 AM).
     */
    private suspend fun performContentGeneration(): List<AutonomousTask> {
        _mode.value = AgentMode.CREATE

        val memory = globalBrain.getMemory()
        val behavioralModel = memory.memories
            .filter { it.category == "behavioral_model" }
            .maxByOrNull { it.createdAt }

        if (behavioralModel == null) {
            AppLogger.d(TAG, "No behavioral model yet — skipping generation")
            return emptyList()
        }

        val pendingTasks = mutableListOf<AutonomousTask>()

        // 1. Social media generation
        val socialTask = generateSocialContent(behavioralModel)
        if (socialTask != null) pendingTasks.add(socialTask)

        // 2. News/information digest
        val newsTask = generateNewsDigest()
        if (newsTask != null) pendingTasks.add(newsTask)

        // 3. Follow-up reminders
        val followupTask = generateFollowupReminders()
        if (followupTask != null) pendingTasks.add(followupTask)

        // 4. Automation suggestions
        val automationTask = generateAutomationIdeas()
        if (automationTask != null) pendingTasks.add(automationTask)

        return pendingTasks
    }

    private suspend fun generateSocialContent(behavioralModel: TwMemoryEntry): AutonomousTask? {
        val memory = globalBrain.getMemory()

        // Find recent work content
        val recentWork = memory.memories
            .filter { it.tags.contains("project") || it.tags.contains("work") }
            .filter { it.createdAt.after(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)) }

        if (recentWork.isEmpty()) return null

        val prompt = buildString {
            appendLine("Generate social media content (tweets/X posts) based on recent work:")
            appendLine()
            recentWork.forEach { work ->
                appendLine("Recent work: ${work.title} — ${work.content.take(300)}")
            }
            appendLine()
            appendLine("Behavioral context: ${behavioralModel.content.take(500)}")
            appendLine()
            appendLine("Generate 3 tweet drafts that reflect how this person thinks and writes.")
            appendLine("Each draft should be under 280 characters.")
            appendLine("Format as: 'Draft 1: [text]' on separate lines.")
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

    // Similar methods for generateNewsDigest(), generateFollowupReminders(), generateAutomationIdeas()

    // ─── DELIVER: Notification & Review ─────────────────────────────────────

    private fun showNotification() {
        val pending = taskQueue.filter { it.status == TaskStatus.PENDING }
        if (pending.isEmpty()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle("🤖 Your AI teammate has updates")
            .setContentText("${pending.size} items ready for review")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(pending.joinToString("\n---\n") { task ->
                    "• ${task.title}\n  ${task.summary}"
                })
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createPendingIntent())
            .addAction(R.drawable.ic_approve, "Review", createPendingIntent("action=review"))
            .addAction(R.drawable.ic_dismiss, "Dismiss All", createPendingIntent("action=dismiss"))
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Agent Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Autonomous AI agent deliverables and updates"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ─── WorkManager Integration ───────────────────────────────────────────

    private fun schedulePeriodicChecks() {
        // Morning check: 9 AM — generate deliverables
        scheduleForTime("autonomous_morning", 9, 0)

        // Evening learning: 8 PM — synthesize patterns
        scheduleForTime("autonomous_evening", 20, 0)

        // Periodic observation: every 2 hours during active hours
        scheduleInterval("autonomous_observe", 2 * 60)
    }

    private fun scheduleForTime(tag: String, hour: Int, minute: Int) {
        // Calculate next occurrence
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
    }

    private fun scheduleInterval(tag: String, intervalMinutes: Long) {
        val work = PeriodicWorkRequestBuilder<AutonomousAgentWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .addTag(tag)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tw_autonomous_$tag",
            ExistingPeriodicWorkPolicy.REPLACE,
            work
        )
    }

    // ─── AI Call Helper ─────────────────────────────────────────────────────

    private suspend fun callAIForAnalysis(prompt: String): String {
        // Use EnhancedAIService with a dedicated autonomous agent chatId
        val stream = EnhancedAIService.getInstance(context).sendMessage(
            message = prompt,
            chatId = "autonomous_agent",
            chatHistory = emptyList(),
            functionType = FunctionType.CHAT,
            promptFunctionType = PromptFunctionType.CHAT,
            enableThinking = true,
            enableMemoryQuery = false,
            maxTokens = 2048,
            stream = false
        )

        val response = StringBuilder()
        stream.collect { chunk -> response.append(chunk) }
        return response.toString()
    }

    // ─── User Review ────────────────────────────────────────────────────────

    fun approveTask(taskId: String): Boolean {
        val task = taskQueue.find { it.id == taskId } ?: return false
        task.status = TaskStatus.APPROVED

        // Execute the approved task (post tweet, send email draft, etc.)
        agentScope.launch {
            executeApprovedTask(task)
        }
        return true
    }

    fun dismissTask(taskId: String) {
        taskQueue.find { it.id == taskId }?.status = TaskStatus.DISMISSED
    }

    private suspend fun executeApprovedTask(task: AutonomousTask) {
        when (task.type) {
            TaskType.SOCIAL_POST -> {
                // Copy to clipboard or open social posting screen
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Twent AI Draft", task.content)
                clipboard.setPrimaryClip(clip)
            }
            else -> {
                // For other types, inject into AI Chat for refinement
                // or save to a review inbox
            }
        }
    }
}
```

### Phase 2: `AutonomousAgentWorker` — WorkManager backend

**File:** `core/workflow/AutonomousAgentWorker.kt` (NEW)

```kotlin
class AutonomousAgentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val agent = TwAutonomousAgent.getInstance(applicationContext)
        val mode = inputData.getString(KEY_MODE) ?: "observe"

        when (mode) {
            "observe" -> {
                agent.triggerObservation()
            }
            "learn" -> {
                agent.triggerLearning()
            }
            "create" -> {
                agent.triggerContentGeneration()
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_MODE = "autonomous_mode"
    }
}
```

### Phase 3: User Settings & UI Toggle

**File:** `ui/features/settings/AutonomousAgentSettings.kt` (NEW screen)

- Toggle: "Enable autonomous agent"
- Schedule settings: morning time, observation frequency
- Task types to generate (checkboxes: social, news, followups, automation)
- Review queue (show pending tasks, approve/dismiss)
- Privacy: "Agent will observe app usage and workflow activity"

**File:** `data/preferences/AutonomousAgentPreferences.kt` (NEW)

```kotlin
class AutonomousAgentPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("autonomous_agent", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var morningHour: Int
        get() = prefs.getInt("morning_hour", 9)
        set(v) = prefs.edit().putInt("morning_hour", v).apply()

    var observeIntervalMinutes: Int
        get() = prefs.getInt("observe_interval", 120)
        set(v) = prefs.edit().putInt("observe_interval", v).apply()

    var generateSocialPosts: Boolean
        get() = prefs.getBoolean("gen_social", true)
        set(v) = prefs.edit().putBoolean("gen_social", v).apply()

    var generateNewsDigest: Boolean
        get() = prefs.getBoolean("gen_news", true)
        set(v) = prefs.edit().putBoolean("gen_news", v).apply()

    var generateFollowups: Boolean
        get() = prefs.getBoolean("gen_followup", true)
        set(v) = prefs.edit().putBoolean("gen_followup", v).apply()
}
```

---

## Integration with AIForegroundService

**File:** `AIForegroundService.kt` — MODIFY

```kotlin
// Add to service scope and lifecycle
private val autonomousAgent by lazy {
    TwAutonomousAgent.getInstance(applicationContext)
}

// In onCreate:
if (AutonomousAgentPreferences(applicationContext).enabled) {
    autonomousAgent.start()
}

// In onDestroy:
autonomousAgent.stop()
```

---

## Data Sources for Observation

The autonomous agent needs to observe user behavior. Sources:

1. **App usage** — `UsageStatsManager` (needs PACKAGE_USAGE_STATS permission)
2. **Recent workflows** — `WorkflowRepository.getRecentExecutions()`
3. **Overlay activity** — `UIAgentModeManager` state + `overlay activity log`
4. **AI Chat topics** — `AIMessageManager` recent messages (filtered for privacy)
5. **File activity** — recent files in Downloads, workspace dirs
6. **Notification activity** — via `NotificationListenerService`

All observation is **user-controlled** via preferences. Each data source can be toggled independently.

---

## Privacy Architecture

```
Observation Sources
├── App usage stats     — [ ] Enabled
├── Workflow history    — [x] Enabled  
├── Overlay activity    — [x] Enabled
├── Chat topic analysis — [ ] Enabled
├── File activity       — [x] Enabled
└── Notification log   — [ ] Enabled

Observation → Stored as memory entries with source tag
    ↓
AI synthesis (runs on-device, no external API for observation data)
    ↓
Behavioral model → injected into autonomous agent prompt
    ↓
Deliverables → Notification → User review → Execute
```

---

## File Changes Summary

| File | Change |
|------|--------|
| `api/chat/autonomous/TwAutonomousAgent.kt` | NEW — main agent loop |
| `core/workflow/AutonomousAgentWorker.kt` | NEW — WorkManager worker |
| `ui/features/settings/AutonomousAgentSettings.kt` | NEW — settings UI |
| `data/preferences/AutonomousAgentPreferences.kt` | NEW — preferences |
| `api/chat/AIForegroundService.kt` | MODIFY — start/stop autonomous agent |
| `services/notification/AutonomousNotificationListener.kt` | NEW — observe notifications |
| `AndroidManifest.xml` | ADD: PACKAGE_USAGE_STATS permission |

---

## Verification

1. Enable autonomous agent in settings
2. Use the phone normally for a day
3. At scheduled morning time — notification appears with generated content
4. Tap notification → review queue
5. Approve a task → content is delivered (clipboard, etc.)
6. Check `brain/MEMORY.md` — observation entries with `source="autonomous_observer"` appear
7. Check `brain/MEMORY.md` — behavioral model entry with `category="behavioral_model"` appears

---

## Testing Checklist

- [ ] Agent runs observation cycle every 2 hours
- [ ] Morning generation creates social post drafts
- [ ] Notification appears with pending tasks
- [ ] Approve/dismiss works from notification
- [ ] Behavioral model synthesizes from observations
- [ ] Agent doesn't use external APIs for observation data (privacy)
- [ ] Battery impact is reasonable (<5% per day estimated)
- [ ] Works with screen off / phone locked
- [ ] Survives app restart
