# PLAN-004: Hindsight Brain Integration
## How Hindsight's Architecture Fits Into Twent's Agent System

---

## Why Hindsight + Twent = HolaOS Killer

HolaOS's demo is impressive, but its memory is **shallow** — "learns by Friday," remembers "what I did yesterday." That's just session continuity.

**Hindsight** has:
- **TEMPR retrieval** — time-aware, priority-weighted memory search
- **Observation consolidation** — multiple facts about the same entity merged into coherent knowledge
- **Disposition traits** — how the user prefers to work, not just what they do
- **Continuous reflection** — the agent thinks about what it observed

**When Hindsight's architecture is integrated into Twent's brain:**
- Every interaction becomes a memory observation
- The agent doesn't just remember WHAT you did — it remembers **WHO** you talked to, **WHY** you made decisions, **WHAT** you struggled with
- Disposition traits let the agent predict what you'd want before you ask
- TEMPR ensures the most relevant memory is always surfaced, even if it was months ago

**Net result:** The agent that learns your goals by Monday, your rhythm by Friday, AND remembers every conversation you had about those goals. HolaOS can't do this.

---

## Architecture: Where Hindsight Fits

```
Twent Agent System
│
├── Existing (TwAgentChatBrain + TwMemoryManager)
│   └── file-based MEMORY.md, keyword search, basic insights
│
└── NEW: Hindsight Layer
    │
    ├── TwHindsightMCPClient      → connects to Hindsight MCP server
    ├── TwObservationBridge       → converts Twent events → Hindsight observations
    ├── TwDispositionManager     → Hindsight disposition traits in Twent context
    ├── TwRetrievalBridge        → TEMPR search as brain tool
    └── TwReflectionEngine       → Hindsight reflect → Twent mid-session notes
```

**Two integration modes:**

| Mode | Description | Use Case |
|------|-------------|----------|
| **Local** | Hindsight runs as local MCP server (localhost:8080) | Development, privacy-first |
| **Remote** | Hindsight runs on remote server, accessed via HTTP | Production, cross-device |

---

## Implementation Details

### Phase 1: Hindsight MCP Client

**File:** `api/chat/brain/hindsight/TwHindsightMCPClient.kt` (NEW)

```kotlin
/**
 * MCP client for connecting to Hindsight server.
 * Uses the existing MCP infrastructure in Twent.
 *
 * Hindsight MCP server exposes these tools:
 * - hindsight_observation   → record observations
 * - hindsight_retrieve      → TEMPR-powered search
 * - hindsight_reflect       → reflection synthesis
 * - hindsight_dispositions  → get/update disposition traits
 * - hindsight_consolidate   → merge observations
 */
class TwHindsightMCPClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TwHindsightMCP"
        private const val DEFAULT_PORT = 8080
        private const val MCP_SERVER_NAME = "hindsight"

        @Volatile private var INSTANCE: TwHindsightMCPClient? = null

        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TwHindsightMCPClient(ctx.applicationContext).also { INSTANCE = it }
        }
    }

    private val prefs = HindsightPreferences(context)
    private var mcpServer: MCPClientConnection? = null

    data class HindsightConfig(
        val serverUrl: String = "http://localhost:$DEFAULT_PORT",
        val apiKey: String? = null,
        val enabled: Boolean = false
    )

    // ─── Connection Management ────────────────────────────────────────────

    suspend fun connect(): Boolean {
        if (!prefs.enabled) return false

        val config = HindsightConfig(
            serverUrl = prefs.serverUrl,
            apiKey = prefs.apiKey,
            enabled = true
        )

        return try {
            mcpServer = MCPClientConnection(
                serverName = MCP_SERVER_NAME,
                baseUrl = config.serverUrl,
                apiKey = config.apiKey
            )
            mcpServer?.connect() == true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to Hindsight server", e)
            false
        }
    }

    fun disconnect() {
        mcpServer?.disconnect()
        mcpServer = null
    }

    val isConnected: Boolean get() = mcpServer?.isConnected == true

    // ─── Observation Recording ──────────────────────────────────────────────

    /**
     * Record an observation to Hindsight.
     * This is called whenever the agent observes something worth remembering.
     *
     * Maps from Twent event types to Hindsight observation format.
     */
    suspend fun recordObservation(
        content: String,
        observationType: ObservationType,
        entities: List<String> = emptyList(),
        importance: Float = 0.5f,
        source: String = "twent"
    ): ObservationResult? {
        if (!isConnected) return null

        val observation = mapOf(
            "content" to content,
            "type" to observationType.name.lowercase(),
            "entities" to entities,
            "importance" to importance,
            "source" to source,
            "timestamp" to ISO8601.now()
        )

        return try {
            val result = mcpServer?.callTool("hindsight_observation", observation)
            parseObservationResult(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to record observation", e)
            null
        }
    }

    enum class ObservationType {
        WORK,         // User working on something
        COMMUNICATION,// User communicating with someone
        DECISION,     // User made a decision
        PREFERENCE,   // User expressed a preference
        RELATIONSHIP, // Interaction with a person/entity
        LEARNING,     // User learned something new
        GOAL,         // User stated a goal or intention
        FRUSTRATION,  // User expressed frustration
        SUCCESS       // User achieved something
    }

    data class ObservationResult(
        val id: String,
        val consolidated: Boolean,
        val mergedWith: List<String>? = null
    )

    // ─── TEMPR Retrieval ──────────────────────────────────────────────────

    /**
     * Retrieve memories using Hindsight's TEMPR algorithm.
     * This replaces the basic keyword search in TwMemoryManager.
     *
     * TEMPR factors:
     * - Recency (recent observations weighted higher)
     * - Priority (importance score)
     * - Mutual information (observations about same entity)
     * - Frequency (recurring patterns)
     * - Recency decay (older observations decay exponentially)
     */
    suspend fun retrieve(
        query: String,
        maxResults: Int = 10,
        timeRange: TimeRange = TimeRange.allTime(),
        entityFilter: List<String> = emptyList()
    ): List<HindsightMemory> {
        if (!isConnected) {
            // Fallback to basic search
            return fallbackBasicSearch(query, maxResults)
        }

        val params = mapOf(
            "query" to query,
            "max_results" to maxResults,
            "time_range" to timeRange.toMap(),
            "entities" to entityFilter
        )

        return try {
            val result = mcpServer?.callTool("hindsight_retrieve", params)
            parseRetrievalResults(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "TEMPR retrieval failed, falling back to basic", e)
            fallbackBasicSearch(query, maxResults)
        }
    }

    data class TimeRange(
        val startMs: Long? = null,
        val endMs: Long? = null,
        val relativeDaysBack: Int? = null
    ) {
        fun toMap() = buildMap {
            startMs?.let { put("start", ISO8601.fromMillis(it)) }
            endMs?.let { put("end", ISO8601.fromMillis(it)) }
            relativeDaysBack?.let { put("days_back", it) }
        }

        companion object {
            fun allTime() = TimeRange()
            fun lastWeek() = TimeRange(relativeDaysBack = 7)
            fun lastMonth() = TimeRange(relativeDaysBack = 30)
        }
    }

    data class HindsightMemory(
        val id: String,
        val content: String,
        val type: String,
        val importance: Float,
        val entities: List<String>,
        val source: String,
        val timestamp: String,
        val relevanceScore: Float,  // TEMPR-computed relevance
        val consolidated: Boolean   // Merged from multiple observations
    )

    // ─── Disposition Traits ───────────────────────────────────────────────

    /**
     * Get disposition traits — how the user prefers to work.
     * These are Hindsight's "dispositions" mapped to Twent context.
     *
     * Example dispositions:
     * - "morning_person" → agent schedules tasks for AM
     * - "prefers_brief" → agent keeps responses short
     * - "deep_work_blocks" → agent batches notifications
     * - "context_switching_cost" → agent avoids interrupting mid-task
     */
    suspend fun getDispositions(): Map<String, Any> {
        if (!isConnected) return getLocalDispositions()

        return try {
            val result = mcpServer?.callTool("hindsight_dispositions", emptyMap())
            parseDispositions(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get dispositions from Hindsight", e)
            getLocalDispositions()
        }
    }

    /**
     * Update a disposition trait based on observed behavior.
     */
    suspend fun updateDisposition(
        trait: String,
        value: Any,
        confidence: Float = 0.5f
    ) {
        if (!isConnected) {
            // Store locally
            storeLocalDisposition(trait, value)
            return
        }

        try {
            mcpServer?.callTool("hindsight_dispositions", mapOf(
                "update" to mapOf(
                    "trait" to trait,
                    "value" to value,
                    "confidence" to confidence
                )
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update disposition", e)
            storeLocalDisposition(trait, value)
        }
    }

    // ─── Reflection ───────────────────────────────────────────────────────

    /**
     * Trigger Hindsight reflection — the agent thinks about what it observed.
     * Results in consolidated insights and updated disposition traits.
     *
     * This is the "slow thinking" step — periodic, deep analysis.
     */
    suspend fun reflect(timeRange: TimeRange = TimeRange.lastWeek()): ReflectionResult? {
        if (!isConnected) return null

        return try {
            val result = mcpServer?.callTool("hindsight_reflect", mapOf(
                "time_range" to timeRange.toMap()
            ))
            parseReflectionResult(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Reflection failed", e)
            null
        }
    }

    data class ReflectionResult(
        val summary: String,           // What happened this week
        val insights: List<String>,    // Key insights
        val dispositionChanges: Map<String, Any>,  // Updated traits
        val entityUpdates: List<EntityUpdate>,     // Merged entities
        val recommendations: List<String>        // Suggested actions
    )

    data class EntityUpdate(
        val entityId: String,
        val name: String,
        val observationsMerged: Int,
        val updatedKnowledge: String
    )

    // ─── Consolidation ───────────────────────────────────────────────────

    /**
     * Manually trigger consolidation for a specific entity.
     * Merges multiple observations into a coherent knowledge entry.
     */
    suspend fun consolidate(entityName: String): ConsolidationResult? {
        if (!isConnected) return null

        return try {
            val result = mcpServer?.callTool("hindsight_consolidate", mapOf(
                "entity" to entityName
            ))
            parseConsolidationResult(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Consolidation failed", e)
            null
        }
    }

    data class ConsolidationResult(
        val entityId: String,
        val observationsAnalyzed: Int,
        val consolidatedKnowledge: String,
        val knowledgeGaps: List<String>
    )

    // ─── Fallback ─────────────────────────────────────────────────────────

    private suspend fun fallbackBasicSearch(query: String, maxResults: Int): List<HindsightMemory> {
        // Use TwMemoryManager's existing search as fallback
        val memoryManager = TwMemoryManager.getInstance(context)
        val results = memoryManager.searchMemories(query).take(maxResults)

        return results.map { entry ->
            HindsightMemory(
                id = entry.id,
                content = entry.content,
                type = entry.category,
                importance = entry.importance,
                entities = entry.tags,
                source = entry.source,
                timestamp = ISO8601.fromDate(entry.createdAt),
                relevanceScore = 0.5f,
                consolidated = false
            )
        }
    }

    // ─── Local Disposition Storage ─────────────────────────────────────────

    private fun getLocalDispositions(): Map<String, Any> {
        val prefs = DispositionPreferences(context)
        return mapOf(
            "communication_style" to (prefs.getString("comm_style") ?: "casual"),
            "response_length" to (prefs.getString("resp_length") ?: "medium"),
            "preferred_time" to (prefs.getString("pref_time") ?: "morning"),
            "topics_of_interest" to prefs.getStringList("topics"),
            "known_entities" to prefs.getStringList("entities")
        )
    }

    private fun storeLocalDisposition(trait: String, value: Any) {
        DispositionPreferences(context).apply {
            when (value) {
                is String -> putString(trait, value)
                is List<*> -> putStringList(trait, value.filterIsInstance<String>())
            }
        }
    }

    // ─── Parsing Helpers ──────────────────────────────────────────────────

    private fun parseObservationResult(raw: String?): ObservationResult? {
        if (raw == null) return null
        return try {
            val json = JSON.parse(raw) as? JSONObject ?: return null
            ObservationResult(
                id = json.string("id") ?: return null,
                consolidated = json.bool("consolidated") ?: false,
                mergedWith = json.array<String>("merged_with")?.toList()
            )
        } catch (e: Exception) { null }
    }

    private fun parseRetrievalResults(raw: String?): List<HindsightMemory> {
        if (raw == null) return emptyList()
        return try {
            val json = JSON.parse(raw) as? JSONObject ?: return emptyList()
            val results = json.array<JSONObject>("results") ?: return emptyList()
            results.mapNotNull { item ->
                HindsightMemory(
                    id = item.string("id") ?: return@mapNotNull null,
                    content = item.string("content") ?: "",
                    type = item.string("type") ?: "general",
                    importance = item.number("importance")?.toFloat() ?: 0.5f,
                    entities = item.array<String>("entities")?.toList() ?: emptyList(),
                    source = item.string("source") ?: "twent",
                    timestamp = item.string("timestamp") ?: "",
                    relevanceScore = item.number("relevance_score")?.toFloat() ?: 0f,
                    consolidated = item.bool("consolidated") ?: false
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse retrieval results", e)
            emptyList()
        }
    }

    // Similar parse methods for dispositions, reflection, consolidation...
}
```

### Phase 2: Observation Bridge

**File:** `api/chat/brain/hindsight/TwObservationBridge.kt` (NEW)

```kotlin
/**
 * Bridge between Twent events and Hindsight observations.
 *
 * Converts Twent's rich event data into Hindsight's observation format.
 * This is where the two systems talk to each other.
 */
class TwObservationBridge(private val context: Context) {

    private val hindsight = TwHindsightMCPClient.getInstance(context)
    private val behavioralLearner = TwBehavioralLearner.getInstance(context)

    // ─── From AI Chat ─────────────────────────────────────────────────────

    /**
     * Observe from AI Chat conversation.
     * Called after every meaningful exchange.
     */
    suspend fun fromChatExchange(
        userMessage: String,
        aiResponse: String,
        toolsUsed: List<String> = emptyList(),
        taskCompleted: Boolean = false
    ) {
        // What did the user ask about?
        val entities = extractEntities(userMessage + " " + aiResponse)

        // Is this a goal-related conversation?
        if (containsGoalKeywords(userMessage)) {
            hindsight.recordObservation(
                content = "User goal stated: $userMessage",
                observationType = TwHindsightMCPClient.ObservationType.GOAL,
                entities = entities,
                importance = 0.9f,
                source = "ai_chat"
            )
        }

        // Did the agent successfully complete something?
        if (taskCompleted && toolsUsed.isNotEmpty()) {
            hindsight.recordObservation(
                content = "Achieved via ${toolsUsed.joinToString(", ")}: ${aiResponse.take(200)}",
                observationType = TwHindsightMCPClient.ObservationType.SUCCESS,
                entities = entities,
                importance = 0.7f,
                source = "ai_chat"
            )
        }

        // User preference revealed?
        if (containsPreferenceKeywords(aiResponse)) {
            val preference = extractPreference(aiResponse)
            if (preference != null) {
                hindsight.recordObservation(
                    content = "User preference: $preference",
                    observationType = TwHindsightMCPClient.ObservationType.PREFERENCE,
                    entities = entities,
                    importance = 0.8f,
                    source = "ai_chat"
                )
                // Update disposition trait
                val (trait, value) = parseToDisposition(preference)
                if (trait != null) {
                    hindsight.updateDisposition(trait, value, confidence = 0.6f)
                }
            }
        }

        // Learning?
        if (containsLearningKeywords(aiResponse)) {
            hindsight.recordObservation(
                content = "User learned: ${extractLearning(aiResponse)}",
                observationType = TwHindsightMCPClient.ObservationType.LEARNING,
                entities = entities,
                importance = 0.6f,
                source = "ai_chat"
            )
        }
    }

    // ─── From Overlay Agent ───────────────────────────────────────────────

    /**
     * Observe from overlay agent execution.
     * Records what tasks were automated and how.
     */
    suspend fun fromOverlayExecution(
        task: String,
        outcome: String,
        durationMs: Long,
        uiElements: List<String> = emptyList()
    ) {
        hindsight.recordObservation(
            content = "Automated task: $task → $outcome (${durationMs}ms)",
            observationType = TwHindsightMCPClient.ObservationType.WORK,
            entities = listOf("overlay_agent") + uiElements,
            importance = if (outcome == "success") 0.6f else 0.4f,
            source = "overlay_agent"
        )

        // Track automation effectiveness
        behavioralLearner.record(
            type = TwBehavioralEvent.EventType.TASK_COMPLETED,
            source = TwBehavioralEvent.EventSource.OVERLAY_AGENT,
            action = "overlay_task",
            content = task,
            contentType = "automation",
            outcome = outcome,
            context = "overlay_agent",
            tags = uiElements
        )
    }

    // ─── From Autonomous Agent ─────────────────────────────────────────────

    /**
     * Observe from autonomous agent deliverables.
     * Records content generation and user feedback.
     */
    suspend fun fromAutonomousDeliverable(
        task: TwAutonomousAgent.AutonomousTask,
        userAction: String  // "approved", "edited", "dismissed"
    ) {
        hindsight.recordObservation(
            content = "AI-generated ${task.type}: ${task.summary}. User action: $userAction",
            observationType = TwHindsightMCPClient.ObservationType.DECISION,
            entities = listOf("autonomous_agent", task.type.name),
            importance = if (userAction == "approved") 0.7f else 0.5f,
            source = "autonomous_agent"
        )

        // Record in behavioral learner for style refinement
        if (userAction == "approved" || userAction == "edited") {
            behavioralLearner.record(
                type = TwBehavioralEvent.EventType.SOCIAL_POST,
                source = TwBehavioralEvent.EventSource.AUTONOMOUS_AGENT,
                action = "ai_generated_${userAction}",
                content = task.content,
                contentType = task.type.name.lowercase(),
                outcome = userAction
            )
        }
    }

    // ─── From Workflow ────────────────────────────────────────────────────

    /**
     * Observe from workflow execution results.
     */
    suspend fun fromWorkflowResult(
        workflowName: String,
        result: String,
        outputContent: String? = null
    ) {
        hindsight.recordObservation(
            content = "Workflow '$workflowName' ran: $result" +
                    (outputContent?.let { ". Output: ${it.take(200)}" } ?: ""),
            observationType = TwHindsightMCPClient.ObservationType.WORK,
            entities = listOf("workflow:$workflowName"),
            importance = 0.5f,
            source = "workflow"
        )
    }

    // ─── Entity Extraction Helpers ────────────────────────────────────────

    private fun extractEntities(text: String): List<String> {
        // Simple entity extraction — use LLM for production
        val knownEntities = listOf(
            "Twitter", "X", "GitHub", "Apollo", "LinkedIn",
            "Claude", "GPT", "Gemini", "Cursor", "VS Code",
            "Android", "Kotlin", "Java", "React", "TypeScript",
            "Cloudflare", "Vercel", "AWS", "GCP"
        )
        return knownEntities.filter { text.contains(it, ignoreCase = true) }
    }

    private fun containsGoalKeywords(text: String): Boolean {
        val goals = listOf("want to", "going to", "plan to", "need to", "try to",
            "goal is", "objective", "trying to build", "want to create")
        return goals.any { text.contains(it, ignoreCase = true) }
    }

    private fun containsPreferenceKeywords(text: String): Boolean {
        val prefs = listOf("prefer", "like", "don't like", "hate", "always",
            "never", "I usually", "better if", "instead of")
        return prefs.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractPreference(text: String): String? {
        // Extract the preference statement — simplified
        val match = Regex("""(prefer|like|don't like|hate|always|never)[^.!?]{5,100}""")
            .find(text, 0)
        return match?.value?.trim()
    }

    private fun containsLearningKeywords(text: String): Boolean {
        val learning = listOf("learned that", "figured out", "discovered",
            "turns out", "realized", "found that")
        return learning.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractLearning(text: String): String {
        val match = Regex("""(learned that|figured out|discovered|turns out|realized|found that)[^.!?]{5,150}""")
            .find(text, 0)
        return match?.value?.trim() ?: text.take(100)
    }

    private fun parseToDisposition(preference: String): Pair<String?, Any?> {
        // Map natural language preference → disposition trait
        return when {
            preference.contains("morning", ignoreCase = true) ->
                "preferred_time" to "morning"
            preference.contains("evening", ignoreCase = true) ->
                "preferred_time" to "evening"
            preference.contains("short", ignoreCase = true) && preference.contains("response", ignoreCase = true) ->
                "response_length" to "short"
            preference.contains("detailed", ignoreCase = true) ->
                "response_length" to "long"
            preference.contains("casual", ignoreCase = true) ->
                "communication_style" to "casual"
            preference.contains("formal", ignoreCase = true) ->
                "communication_style" to "formal"
            else -> null to null
        }
    }
}
```

### Phase 3: TEMPR Brain Tool

**File:** `api/chat/brain/hindsight/TwTEMPRBrainTool.kt` (NEW)

```kotlin
/**
 * TEMPR search tool for the brain system.
 * Replaces basic keyword search with Hindsight's time-aware retrieval.
 *
 * Tool name: tw_hindsearch (add to BRAIN_TOOL_NAMES in TwConversationLoop)
 *
 * Usage in conversation:
 *   "What did we discuss about the landing page last week?"
 *   → tw_hindsearch(query="landing page", time_range="last_week")
 *
 *   "Who was that person I was talking to about the project?"
 *   → tw_hindsearch(query="project discussion person", entities=["person"])
 */
class TwTEMPRBrainTool(private val context: Context) {

    private val hindsight = TwHindsightMCPClient.getInstance(context)

    /**
     * Execute a TEMPR search.
     * Returns formatted results for the AI to consume.
     */
    suspend fun search(
        query: String,
        maxResults: Int = 10,
        timeRange: String = "all_time",
        entityFilter: List<String> = emptyList()
    ): String {
        val range = when (timeRange.lowercase()) {
            "today" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 1)
            "this_week", "last_week" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 7)
            "this_month", "last_month" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 30)
            "all_time" -> TwHindsightMCPClient.TimeRange.allTime()
            else -> TwHindsightMCPClient.TimeRange.allTime()
        }

        val results = hindsight.retrieve(
            query = query,
            maxResults = maxResults,
            timeRange = range,
            entityFilter = entityFilter
        )

        if (results.isEmpty()) {
            return "🔍 No memories found for: \"$query\"\n\n" +
                   "No observations match this query in the selected time range."
        }

        return buildString {
            appendLine("🔍 Found ${results.size} memory/ies for: \"$query\"")
            if (timeRange != "all_time") appendLine("(Time range: $timeRange)")
            appendLine()

            results.forEachIndexed { idx, mem ->
                val age = formatAge(mem.timestamp)
                val consolidated = if (mem.consolidated) " [consolidated]" else ""
                val importance = "★".repeat((mem.importance * 5).toInt().coerceIn(1, 5))

                appendLine("**${idx + 1}. ${mem.type.uppercase()}** $importance$consolidated")
                appendLine("   ${age} | Relevance: ${(mem.relevanceScore * 100).toInt()}%")
                appendLine("   ${mem.content.take(200)}")

                if (mem.entities.isNotEmpty()) {
                    appendLine("   Entities: ${mem.entities.joinToString(", ")}")
                }
                appendLine()
            }
        }
    }

    /**
     * Get a consolidated view of an entity/person.
     */
    suspend fun consolidateEntity(entityName: String): String {
        val result = hindsight.consolidate(entityName)
            ?: return "⚠️ Consolidation not available (Hindsight not connected)"

        return buildString {
            appendLine("🧠 **Consolidated: $entityName**")
            appendLine()
            appendLine("Observations analyzed: ${result.observationsAnalyzed}")
            appendLine()
            appendLine("**Knowledge:**")
            appendLine(result.consolidatedKnowledge)
            if (result.knowledgeGaps.isNotEmpty()) {
                appendLine()
                appendLine("**Knowledge gaps:**")
                result.knowledgeGaps.forEach { gap ->
                    appendLine("  • $gap")
                }
            }
        }
    }

    /**
     * Trigger a reflection cycle.
     */
    suspend fun reflect(timeRange: String = "last_week"): String {
        val range = when (timeRange.lowercase()) {
            "today" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 1)
            "this_week", "last_week" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 7)
            "this_month", "last_month" -> TwHindsightMCPClient.TimeRange(relativeDaysBack = 30)
            else -> TwHindsightMCPClient.TimeRange.lastWeek()
        }

        val result = hindsight.reflect(range)
            ?: return "⚠️ Reflection not available (Hindsight not connected)"

        return buildString {
            appendLine("🧠 **Weekly Reflection**")
            appendLine()
            appendLine("**Summary:**")
            appendLine(result.summary)
            appendLine()
            if (result.insights.isNotEmpty()) {
                appendLine("**Key Insights:**")
                result.insights.forEach { insight ->
                    appendLine("  • $insight")
                }
                appendLine()
            }
            if (result.dispositionChanges.isNotEmpty()) {
                appendLine("**Updated Preferences:**")
                result.dispositionChanges.forEach { (trait, value) ->
                    appendLine("  • $trait: $value")
                }
                appendLine()
            }
            if (result.recommendations.isNotEmpty()) {
                appendLine("**Recommendations:**")
                result.recommendations.forEach { rec ->
                    appendLine("  → $rec")
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun formatAge(timestamp: String): String {
        return try {
            val date = ISO8601.parse(timestamp) ?: return timestamp
            val days = ((System.currentTimeMillis() - date.time) / (24 * 60 * 60 * 1000)).toInt()
            when {
                days == 0 -> "today"
                days == 1 -> "yesterday"
                days < 7 -> "${days} days ago"
                days < 30 -> "${days / 7} weeks ago"
                else -> "${days / 30} months ago"
            }
        } catch (e: Exception) {
            timestamp
        }
    }
}
```

### Phase 4: Register with TwConversationLoop

**File:** `api/chat/brain/TwConversationLoop.kt` — MODIFY `BRAIN_TOOL_NAMES`

```kotlin
// Add to BRAIN_TOOL_NAMES:
"tw_hindsearch",
"tw_consolidate",
"tw_reflect",
"tw_dispositions"
```

**Add handler methods:**

```kotlin
private suspend fun handleHindsearch(params: Map<String, String>, state: TwBrainState): String {
    val query = params["query"] ?: return "Error: query required"
    val maxResults = params["max_results"]?.toIntOrNull() ?: 10
    val timeRange = params["time_range"] ?: "all_time"
    val entities = params["entities"]?.split(",")?.map { it.trim() } ?: emptyList()

    val tool = TwTEMPRBrainTool(context)
    return tool.search(query, maxResults, timeRange, entities)
}

private suspend fun handleConsolidate(params: Map<String, String>, state: TwBrainState): String {
    val entity = params["entity"] ?: return "Error: entity required"
    val tool = TwTEMPRBrainTool(context)
    return tool.consolidateEntity(entity)
}

private suspend fun handleReflect(params: Map<String, String>, state: TwBrainState): String {
    val timeRange = params["time_range"] ?: "last_week"
    val tool = TwTEMPRBrainTool(context)
    return tool.reflect(timeRange)
}

private suspend fun handleDispositions(params: Map<String, String>, state: TwBrainState): String {
    val hindsight = TwHindsightMCPClient.getInstance(context)
    val dispositions = hindsight.getDispositions()

    return buildString {
        appendLine("🎯 **Your Disposition Traits**")
        appendLine()
        dispositions.forEach { (trait, value) ->
            appendLine("• **$trait**: $value")
        }
    }
}
```

### Phase 5: Wire Observation Bridge Into Existing Systems

**File:** `api/chat/brain/TwAgentChatBrain.kt` — MODIFY

```kotlin
// After each meaningful exchange:
val bridge = TwObservationBridge(context)
bridge.fromChatExchange(
    userMessage = userMessage,
    aiResponse = aiResponse,
    toolsUsed = toolsUsedInSession,
    taskCompleted = didTaskComplete
)
```

**File:** `core/agent/v2/Agent.kt` — MODIFY after task completion

```kotlin
// In Agent.run() after task completes:
val bridge = TwObservationBridge(context)
bridge.fromOverlayExecution(
    task = initialTask,
    outcome = "success",  // or "failed"
    durationMs = System.currentTimeMillis() - startTimeMs,
    uiElements = extractedUIElements
)
```

**File:** `api/chat/autonomous/TwAutonomousAgent.kt` — MODIFY

```kotlin
// After user reviews a task:
val bridge = TwObservationBridge(context)
bridge.fromAutonomousDeliverable(task, userAction)
```

---

## Preferences for Hindsight Connection

**File:** `data/preferences/HindsightPreferences.kt` (NEW)

```kotlin
class HindsightPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("hindsight", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "http://localhost:8080") ?: ""
        set(v) = prefs.edit().putString("server_url", v).apply()

    var apiKey: String?
        get() = prefs.getString("api_key", null)
        set(v) = prefs.edit().putString("api_key", v).apply()

    var connectionMode: ConnectionMode
        get() = ConnectionMode.valueOf(prefs.getString("mode", "LOCAL") ?: "LOCAL")
        set(v) = prefs.edit().putString("mode", v.name).apply()

    enum class ConnectionMode { LOCAL, REMOTE }
}
```

**Settings UI:** Simple screen with:
- Toggle: "Enable Hindsight memory"
- URL field (for remote mode)
- API key field
- Connection status indicator
- "Test Connection" button

---

## File Changes Summary

| File | Change |
|------|--------|
| `api/chat/brain/hindsight/TwHindsightMCPClient.kt` | NEW — MCP client |
| `api/chat/brain/hindsight/TwObservationBridge.kt` | NEW — event → observation bridge |
| `api/chat/brain/hindsight/TwTEMPRBrainTool.kt` | NEW — brain tool for TEMPR search |
| `api/chat/brain/TwConversationLoop.kt` | MODIFY — register new brain tools |
| `api/chat/brain/TwAgentChatBrain.kt` | MODIFY — wire observation bridge |
| `core/agent/v2/Agent.kt` | MODIFY — wire observation bridge |
| `api/chat/autonomous/TwAutonomousAgent.kt` | MODIFY — wire observation bridge |
| `data/preferences/HindsightPreferences.kt` | NEW — preferences |
| `ui/features/settings/HindsightSettings.kt` | NEW — settings UI |

---

## Integration Architecture Summary

```
HOLAOS:                          TWENT + HINDSIGHT:
────────                         ───────────────────
"Owns its own computer"          → Overlay agent + persistent workspace
"Doesn't start over"             → TwGlobalBrain (cross-session memory)
"Learns how I write"             → TwBehavioralLearner (style model)
"Every morning stack ready"      → TwAutonomousAgent (continuous generation)
                                  
                                 ＋ HINDSIGHT LAYER:
"Learns goals by Monday"         → TEMPR retrieval + observation consolidation
"Learns rhythm by Friday"        → Disposition traits + behavioral model
"Never forgets a thing"          → Time-aware memory + entity resolution
"Thinks about what it missed"    → Hindsight reflection engine

RESULT: Twent does everything HolaOS does, PLUS has deep episodic memory,
         disposition understanding, and HolaOS has no answer to this.
```

---

## Verification

1. Install Hindsight server locally (`npx @allaboss/hindsight`)
2. Configure in Twent settings → point to `http://localhost:8080`
3. Have conversations, use overlay agent, review autonomous deliverables
4. Use `tw_hindsearch "my landing page" last_week` in AI Chat
5. Results should include observations from AI Chat, overlay, AND autonomous agent
6. Use `tw_reflect last_week` — check summary matches actual activity
7. Use `tw_consolidate "Twent"` — check entity has merged knowledge
8. Check Hindsight server logs — observations are being recorded

---

## Testing Checklist

- [ ] Hindsight server connects via MCP
- [ ] Observations recorded from AI Chat exchanges
- [ ] Observations recorded from overlay agent tasks
- [ ] Observations recorded from autonomous agent deliverables
- [ ] TEMPR search returns relevant results (better than keyword search)
- [ ] Entity consolidation works (multiple observations → one entry)
- [ ] Reflection generates meaningful summaries
- [ ] Disposition traits update based on observed preferences
- [ ] Fallback to local search when Hindsight unavailable
- [ ] Privacy: user controls which observations are recorded
