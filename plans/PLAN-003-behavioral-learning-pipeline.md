# PLAN-003: Behavioral Learning Pipeline
## From Observations to Personalized Content Generation

---

## Problem Statement

HolaOS's magic moment: "It learns how I think and how I write. Then every morning, there's a stack of posts waiting pulled from what I did yesterday."

**What makes it impressive:** The agent doesn't just generate random content — it generates content **in the user's voice, reflecting their actual work, structured the way they'd structure it**.

Twent's current state:
- `TwInsights` tracks tool call counts and session stats — purely **quantitative**
- No **style modeling** — the agent doesn't know HOW the user writes, WHAT they care about, or their TONE
- Content generation in workflows uses static prompts — **no personalization**

**What's missing:** A pipeline that goes:
```
Raw behavior → Observation → Pattern → Style Model → Personalized Output
```

---

## Architecture

```
TwBehavioralLearner (new — the core learning engine)
│
├── TwStyleModeler    (new — extract writing style, tone, structure)
├── TwContentAnalyzer (new — extract topics, themes, interests)
├── TwInterestGraph   (new — entity + interest tracking)
│
├── TwGlobalBrain    (shared — memory persistence)
└── TwAutonomousAgent (consumer — uses style model for generation)
```

**Data flow:**
```
1. OBSERVE  → raw events (app usage, workflows, chat messages, file edits)
2. CAPTURE  → TwBehavioralLearner.recordEvent(event)
3. SYNTHESIZE → periodic: analyze events → update models
4. SERVE    → getStyleModel(), getInterests(), getTopics()
5. GENERATE → autonomous agent uses models for personalized output
```

---

## Implementation Details

### Phase 1: Behavioral Event Model

**File:** `api/chat/brain/behavioral/TwBehavioralEvent.kt` (NEW)

```kotlin
package com.ai.assistance.operit.api.chat.brain.behavioral

/**
 * A behavioral event — a single recorded fact about user behavior.
 * These are the raw inputs to the learning pipeline.
 */
data class TwBehavioralEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: EventType,
    val source: EventSource,
    val timestamp: Date = Date(),

    // What happened
    val action: String,         // "typed_message", "approved_workflow", "opened_app"
    val content: String,        // The actual content (message text, workflow name, etc.)
    val contentType: String,    // "tweet", "email", "code", "note", "comment"
    val length: Int = content.length,

    // Metadata
    val context: String? = null,   // What was the user doing when this happened?
    val outcome: String? = null,  // "approved", "dismissed", "edited", "sent"
    val tags: List<String> = emptyList()
) {
    enum class EventType {
        // Communication
        SOCIAL_POST, SOCIAL_REPLY, EMAIL_SENT, EMAIL_DRAFT,
        COMMENT_WRITTEN, DM_SENT,

        // Content creation
        NOTE_WRITTEN, DOCUMENT_CREATED, CODE_WRITTEN,
        BLOG_POST, VIDEO_IDEA,

        // Decision making
        TASK_COMPLETED, TASK_APPROVED, TASK_DISMISSED,
        AUTOMATION_CREATED, WORKFLOW_RUN,

        // Learning & discovery
        ARTICLE_READ, NEWS_CLICKED, TOPIC_RESEARCHED,
        QUESTION_ASKED, ANSWER_GIVEN,

        // App & tool usage
        APP_USED, TOOL_CALLED, SEARCH_PERFORMED
    }

    enum class EventSource {
        OVERLAY_AGENT,     // UI automation
        WORKFLOW_RESULT,  // Scheduled workflow output
        AUTONOMOUS_AGENT,  // AI-generated content
        USER_MESSAGE,      // User typed something
        AI_RESPONSE,       // AI output (for style analysis)
        APP_USAGE,         // App usage stats
        FILE_ACTIVITY      // File operations
    }
}

/**
 * Batch of events for analysis.
 */
data class TwEventBatch(
    val events: List<TwBehavioralEvent>,
    val from: Date,
    val to: Date
) {
    val count: Int get() = events.size
    val types: Set<TwBehavioralEvent.EventType> get() = events.map { it.type }.toSet()
}
```

### Phase 2: The Core `TwBehavioralLearner`

**File:** `api/chat/brain/behavioral/TwBehavioralLearner.kt` (NEW)

```kotlin
class TwBehavioralLearner private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TwBehavioralLearner"
        private const val MODEL_DIR = "brain/behavioral/"
        private const val EVENTS_FILE = "behavioral_events.jsonl"
        private const val STYLE_MODEL_FILE = "style_model.json"
        private const val INTEREST_GRAPH_FILE = "interest_graph.json"
        private const val TOPIC_MODEL_FILE = "topics.json"

        private const val MAX_EVENTS_STORED = 5000
        private const val SYNTHESIS_INTERVAL_MS = 6 * 60 * 60 * 1000L // Every 6 hours

        @Volatile private var INSTANCE: TwBehavioralLearner? = null

        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TwBehavioralLearner(ctx.applicationContext).also { INSTANCE = it }
        }
    }

    private val globalBrain = TwGlobalBrain.getInstance(context)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    // In-memory cache
    private var styleModel: StyleModel? = null
    private var interestGraph: InterestGraph? = null
    private var topicModel: TopicModel? = null
    private var eventCache: MutableList<TwBehavioralEvent> = mutableListOf()

    // ─── Recording Events ─────────────────────────────────────────────────

    /**
     * Record a behavioral event.
     * Called by overlay agent, workflow executor, autonomous agent, etc.
     *
     * Usage:
     *   TwBehavioralLearner.getInstance(ctx).record(
     *       type = TwBehavioralEvent.EventType.SOCIAL_POST,
     *       source = TwBehavioralEvent.EventSource.OVERLAY_AGENT,
     *       action = "generated_tweet",
     *       content = tweetText,
     *       contentType = "tweet",
     *       outcome = "approved"
     *   )
     */
    fun record(
        type: TwBehavioralEvent.EventType,
        source: TwBehavioralEvent.EventSource,
        action: String,
        content: String,
        contentType: String,
        outcome: String? = null,
        context: String? = null,
        tags: List<String> = emptyList()
    ) {
        val event = TwBehavioralEvent(
            type = type,
            source = source,
            action = action,
            content = content,
            contentType = contentType,
            outcome = outcome,
            context = context,
            tags = tags
        )

        eventCache.add(event)

        // Persist to disk
        appendEventToStorage(event)

        // Trim old events
        if (eventCache.size > MAX_EVENTS_STORED) {
            trimOldEvents()
        }

        AppLogger.d(TAG, "Recorded behavioral event: $type from $source — ${content.take(50)}")
    }

    // Convenience methods for common event types
    fun recordSocialPost(content: String, outcome: String, source: TwBehavioralEvent.EventSource) =
        record(TwBehavioralEvent.EventType.SOCIAL_POST, source, "social_post", content, "tweet", outcome)

    fun recordApprovedWorkflow(workflowName: String, result: String) =
        record(TwBehavioralEvent.EventType.WORKFLOW_RUN, TwBehavioralEvent.EventSource.WORKFLOW_RESULT,
            "workflow_completed", result, "workflow_result", outcome = "success",
            context = workflowName, tags = listOf("workflow", workflowName))

    fun recordMessage(content: String, isUser: Boolean) =
        record(
            type = if (isUser) TwBehavioralEvent.EventType.NOTE_WRITTEN else TwBehavioralEvent.EventType.EMAIL_DRAFT,
            source = TwBehavioralEvent.EventSource.USER_MESSAGE,
            action = if (isUser) "user_typed" else "ai_generated",
            content = content,
            contentType = if (isUser) "user_message" else "ai_response"
        )

    // ─── Periodic Synthesis ───────────────────────────────────────────────

    /**
     * Analyze recent events and update the style model.
     * Called periodically (e.g., by autonomous agent or WorkManager).
     */
    suspend fun synthesize() {
        val recentEvents = getRecentEvents(daysBack = 7)
        if (recentEvents.count < 10) {
            AppLogger.d(TAG, "Not enough events to synthesize (${recentEvents.count}/10)")
            return
        }

        AppLogger.d(TAG, "Synthesizing behavioral model from ${recentEvents.count} events")

        // 1. Extract style from communication content
        val styleModel = analyzeWritingStyle(recentEvents)
        this.styleModel = styleModel
        saveStyleModel(styleModel)

        // 2. Build interest graph from topics
        val interestGraph = analyzeInterests(recentEvents)
        this.interestGraph = interestGraph
        saveInterestGraph(interestGraph)

        // 3. Extract topics and themes
        val topicModel = analyzeTopics(recentEvents)
        this.topicModel = topicModel
        saveTopicModel(topicModel)

        // 4. Store summary in brain memory
        val summary = buildMemorySummary(styleModel, interestGraph, topicModel)
        globalBrain.remember(
            title = "Behavioral style model",
            content = summary,
            category = "style_model",
            importance = 0.9f,
            source = "behavioral_learner",
            tags = listOf("style", "model", "personalization")
        )

        AppLogger.d(TAG, "Behavioral synthesis complete")
    }

    // ─── Style Analysis ───────────────────────────────────────────────────

    /**
     * Analyze writing style from user-generated content.
     * Extracts: tone, structure patterns, vocabulary, formatting preferences.
     */
    private suspend fun analyzeWritingStyle(events: List<TwBehavioralEvent>): StyleModel {
        val texts = events
            .filter { it.contentType in listOf("tweet", "user_message", "comment", "email", "note") }
            .map { it.content }
            .filter { it.length > 20 }

        if (texts.isEmpty()) {
            return StyleModel()
        }

        val prompt = buildString {
            appendLine("Analyze the writing style of this person's content. Be specific.")
            appendLine()
            appendLine("Content samples (most recent first):")
            texts.take(20).forEachIndexed { idx, text ->
                appendLine("${idx + 1}. \"${text.take(300)}\"")
            }
            appendLine()
            appendLine("""Return JSON:
            {
              "avgLength": number,           // average characters per post
              "sentenceStyle": "short punchy" | "medium balanced" | "long detailed",
              "tone": "formal" | "casual" | "humorous" | "serious" | "enthusiastic" | "analytical",
              "voiceTags": ["list of characteristic phrases or patterns"],
              "structure": "bullet points" | "paragraphs" | "single line" | "mixed",
              "emojiUsage": "heavy" | "minimal" | "none" | "occasional",
              "hashtagUsage": "always" | "sometimes" | "never",
              "ctaStyle": "question" | "call to action" | "statement" | "none",
              "humorLevel": 0-1,
              "personalStories": "often" | "sometimes" | "rarely",
              "samplePhrases": ["typical opener", "typical closer", "signature phrase"],
              "writingLevel": "simple" | "intermediate" | "advanced"
            }""")
        }

        val response = callAI(prompt)
        return parseStyleModel(response)
    }

    // ─── Interest Analysis ─────────────────────────────────────────────────

    /**
     * Build an interest graph: what does this person care about?
     * Tracks entities (people, projects, tools), topics, and relationships.
     */
    private suspend fun analyzeInterests(events: List<TwBehavioralEvent>): InterestGraph {
        val allContent = events.joinToString("\n") { "${it.type}: ${it.content}" }

        val prompt = buildString {
            appendLine("Build an interest graph from this person's activities. What do they care about?")
            appendLine()
            appendLine("Recent activity:")
            appendLine(allContent.take(3000))
            appendLine()
            appendLine("""Return JSON:
            {
              "topInterests": [
                {"topic": "string", "score": 0-1, "evidence": ["examples"]}
              ],
              "projects": [
                {"name": "string", "description": "string", "status": "active" | "paused"}
              ],
              "people": [
                {"name": "string", "relationship": "colleague" | "lead" | "mentor" | "peer"}
              ],
              "tools": ["Figma", "Cursor", "GitHub", etc],
              "platforms": ["Twitter/X", "LinkedIn", etc],
              "themes": ["AI agents", "indie dev", "startup", etc]
            }""")
        }

        val response = callAI(prompt)
        return parseInterestGraph(response)
    }

    // ─── Topic Analysis ───────────────────────────────────────────────────

    /**
     * Extract recurring topics and themes from recent content.
     * This feeds into the autonomous agent's content generation.
     */
    private suspend fun analyzeTopics(events: List<TwBehavioralEvent>): TopicModel {
        val recentTexts = events
            .sortedByDescending { it.timestamp }
            .take(50)
            .mapIndexed { idx, e -> "$idx. [${e.type}] ${e.content.take(200)}" }
            .joinToString("\n")

        val prompt = buildString {
            appendLine("What topics and themes are most important to this person right now?")
            appendLine()
            appendLine(recentTexts)
            appendLine()
            appendLine("""Return JSON:
            {
              "currentFocus": ["top 3-5 topics they are actively working on"],
              "recurringThemes": ["patterns across their work"],
              "contentPillars": ["main categories of content they share"],
              "engagementPatterns": {
                "whatGetsApproved": "types of content they approve quickly",
                "whatGetsDismissed": "types they reject"
              },
              "recommendedTopics": ["new topics they should explore based on patterns"]
            }""")
        }

        val response = callAI(prompt)
        return parseTopicModel(response)
    }

    // ─── Model Retrieval ───────────────────────────────────────────────────

    /**
     * Get the current style model for content generation.
     */
    fun getStyleModel(): StyleModel {
        return styleModel ?: loadStyleModel().also { styleModel = it }
    }

    /**
     * Get interests for personalization.
     */
    fun getInterests(): InterestGraph {
        return interestGraph ?: loadInterestGraph().also { interestGraph = it }
    }

    /**
     * Get topics for content planning.
     */
    fun getTopics(): TopicModel {
        return topicModel ?: loadTopicModel().also { topicModel = it }
    }

    /**
     * Generate a style injection string to prepend to prompts.
     * Used by the autonomous agent when generating content.
     */
    fun getStyleInjection(): String {
        val model = getStyleModel()
        return buildString {
            appendLine("## YOUR WRITING STYLE")
            appendLine("Tone: ${model.tone}")
            appendLine("Structure: ${model.structure}")
            appendLine("Voice: ${model.voiceTags.joinToString(", ")}")
            appendLine("Emoji: ${model.emojiUsage}")
            appendLine("Signature phrases: ${model.samplePhrases.joinToString(" | ")}")
            appendLine("Humor: ${(model.humorLevel * 100).toInt()}%")
            if (model.samplePhrases.isNotEmpty()) {
                appendLine("Opening style: '${model.samplePhrases.firstOrNull() ?: "concise and direct"}'")
                appendLine("Closing style: '${model.samplePhrases.lastOrNull() ?: "no fixed pattern"}'")
            }
            appendLine()
        }
    }

    // ─── Persistence ───────────────────────────────────────────────────────

    private fun appendEventToStorage(event: TwBehavioralEvent) {
        val file = File(context.filesDir, "$MODEL_DIR/$EVENTS_FILE")
        file.parentFile?.mkdirs()
        file.appendText(gson.toJson(event) + "\n")
    }

    private fun getRecentEvents(daysBack: Int): List<TwBehavioralEvent> {
        val cutoff = Date(System.currentTimeMillis() - daysBack * 24L * 60 * 60 * 1000)

        if (eventCache.isEmpty()) {
            loadEventsFromStorage()
        }

        return eventCache
            .filter { it.timestamp.after(cutoff) }
            .sortedByDescending { it.timestamp }
    }

    private fun loadEventsFromStorage() {
        val file = File(context.filesDir, "$MODEL_DIR/$EVENTS_FILE")
        if (!file.exists()) return

        eventCache.clear()
        file.forEachLine { line ->
            try {
                val event = gson.fromJson(line, TwBehavioralEvent::class.java)
                eventCache.add(event)
            } catch (e: Exception) {
                // Skip malformed lines
            }
        }
    }

    private fun trimOldEvents() {
        // Keep last MAX_EVENTS_STORED by timestamp
        val trimmed = eventCache
            .sortedByDescending { it.timestamp }
            .take(MAX_EVENTS_STORED)
            .toMutableList()

        // Rewrite file
        val file = File(context.filesDir, "$MODEL_DIR/$EVENTS_FILE")
        file.writeText(trimmed.joinToString("\n") { gson.toJson(it) })

        eventCache.clear()
        eventCache.addAll(trimmed)
    }

    private fun saveStyleModel(model: StyleModel) {
        val file = File(context.filesDir, "$MODEL_DIR/$STYLE_MODEL_FILE")
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(model))
    }

    private fun loadStyleModel(): StyleModel {
        val file = File(context.filesDir, "$MODEL_DIR/$STYLE_MODEL_FILE")
        return if (file.exists()) {
            gson.fromJson(file.readText(), StyleModel::class.java)
        } else {
            StyleModel()
        }
    }

    // Similar for InterestGraph and TopicModel...

    // ─── AI Helper ─────────────────────────────────────────────────────────

    private suspend fun callAI(prompt: String): String {
        val stream = EnhancedAIService.getInstance(context).sendMessage(
            message = prompt,
            chatId = "behavioral_learner",
            chatHistory = emptyList(),
            functionType = FunctionType.CHAT,
            promptFunctionType = PromptFunctionType.CHAT,
            enableThinking = false,
            enableMemoryQuery = false,
            maxTokens = 1024,
            stream = false
        )
        val response = StringBuilder()
        stream.collect { chunk -> response.append(chunk) }
        return response.toString()
    }
}
```

### Phase 3: Data Models

**File:** `api/chat/brain/behavioral/TwStyleModel.kt` (NEW)

```kotlin
/**
 * The user's writing style model.
 * Created by analyzing past content via AI synthesis.
 */
data class StyleModel(
    val avgLength: Int = 280,
    val sentenceStyle: String = "medium balanced",
    val tone: String = "casual",
    val voiceTags: List<String> = emptyList(),
    val structure: String = "mixed",
    val emojiUsage: String = "minimal",
    val hashtagUsage: String = "sometimes",
    val ctaStyle: String = "none",
    val humorLevel: Float = 0.3f,
    val personalStories: String = "sometimes",
    val samplePhrases: List<String> = emptyList(),
    val writingLevel: String = "intermediate",
    val lastUpdated: Date = Date()
)

/**
 * Interest graph — entities and relationships the user cares about.
 */
data class InterestGraph(
    val topInterests: List<Interest> = emptyList(),
    val projects: List<Project> = emptyList(),
    val people: List<Person> = emptyList(),
    val tools: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val lastUpdated: Date = Date()
)

data class Interest(val topic: String, val score: Float, val evidence: List<String>)
data class Project(val name: String, val description: String, val status: String)
data class Person(val name: String, val relationship: String)

/**
 * Topic model — what the user is focused on.
 */
data class TopicModel(
    val currentFocus: List<String> = emptyList(),
    val recurringThemes: List<String> = emptyList(),
    val contentPillars: List<String> = emptyList(),
    val engagementPatterns: EngagementPatterns = EngagementPatterns(),
    val recommendedTopics: List<String> = emptyList(),
    val lastUpdated: Date = Date()
)

data class EngagementPatterns(
    val whatGetsApproved: List<String> = emptyList(),
    val whatGetsDismissed: List<String> = emptyList()
)
```

### Phase 4: Wire Into Autonomous Agent

**File:** `api/chat/autonomous/TwAutonomousAgent.kt` — MODIFY `generateSocialContent()`

```kotlin
private suspend fun generateSocialContent(behavioralModel: TwMemoryEntry): AutonomousTask? {
    val learner = TwBehavioralLearner.getInstance(context)
    val styleModel = learner.getStyleModel()
    val interests = learner.getInterests()
    val topics = learner.getTopics()

    val memory = globalBrain.getMemory()
    val recentWork = memory.memories
        .filter { it.tags.contains("project") || it.tags.contains("work") }
        .filter { it.createdAt.after(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)) }

    if (recentWork.isEmpty()) return null

    // INJECT PERSONALIZED STYLE into generation prompt
    val styleInjection = learner.getStyleInjection()

    val prompt = buildString {
        appendLine("Generate social media content in THIS PERSON'S voice and style:")
        appendLine()
        appendLine(styleInjection)  // ← personalized style
        appendLine("## RECENT WORK (your raw material)")
        recentWork.forEach { work ->
            appendLine("- ${work.title}: ${work.content.take(200)}")
        }
        appendLine()
        appendLine("## TOPICS THEY CARE ABOUT")
        appendLine(interests.topInterests.take(3).joinToString(", ") { it.topic })
        appendLine()
        appendLine("## CONTENT PILLARS")
        appendLine(topics.contentPillars.joinToString(", "))
        appendLine()
        appendLine("Generate 3 tweet drafts that reflect their actual voice.")
        appendLine("Use their typical opener, structure, and closing style.")
        appendLine("Format: 'Draft 1: [text]' on separate lines.")
    }

    val drafts = callAIForAnalysis(prompt)

    return AutonomousTask(
        type = TaskType.SOCIAL_POST,
        title = "Social media drafts — personalized to your style",
        summary = "Style: ${styleModel.tone}, ${styleModel.emojiUsage} emoji, ${styleModel.sentenceStyle} sentences",
        content = drafts,
        confidence = 0.85f,  // Higher confidence because style-matched
        sourceObservations = recentWork.map { it.title }
    )
}
```

### Phase 5: Wire Into Overlay Agent

**File:** `core/agent/v2/Agent.kt` — MODIFY `run()` method

```kotlin
// After memory injection, add style context:
val behavioralLearner = TwBehavioralLearner.getInstance(context)
val styleContext = behavioralLearner.getStyleInjection()
if (styleContext.isNotEmpty()) {
    state.styleContext = styleContext
}
```

And when the agent generates text (in `ActionExecutor`):
```kotlin
// Inject style context into AI prompts when generating content
val styleInjection = behavioralLearner.getStyleInjection()
// Append to prompt for content generation tasks
```

### Phase 6: User Editing to Reinforce Learning

The learning pipeline needs **feedback signals**. When the user edits or approves content, that's a signal.

```kotlin
// In AutonomousAgent — when user approves and edits:
fun recordContentFeedback(task: AutonomousTask, editedContent: String) {
    val learner = TwBehavioralLearner.getInstance(context)

    // Compare original vs edited to refine style
    learner.record(
        type = TwBehavioralEvent.EventType.SOCIAL_POST,
        source = TwBehavioralEvent.EventSource.AUTONOMOUS_AGENT,
        action = "user_edited_generated_content",
        content = editedContent,
        contentType = "tweet",
        outcome = "edited",
        tags = listOf("feedback", task.type.name.lowercase())
    )

    // Trigger re-synthesis with new data
    CoroutineScope(Dispatchers.IO).launch {
        learner.synthesize()
    }
}
```

---

## Style Model Example Output

```json
{
  "avgLength": 245,
  "sentenceStyle": "short punchy",
  "tone": "casual",
  "voiceTags": ["first-person narrative", "self-deprecating humor", "technical but accessible"],
  "structure": "single line",
  "emojiUsage": "heavy",
  "hashtagUsage": "sometimes",
  "ctaStyle": "question",
  "humorLevel": 0.7,
  "personalStories": "often",
  "samplePhrases": [
    "the thing about",
    "basically",
    "lowkey",
    "not gonna lie"
  ],
  "writingLevel": "intermediate",
  "lastUpdated": "2026-05-16T10:30:00Z"
}
```

**What this generates in practice:**
> "the thing about building AI agents is they always forget the boring stuff ￣_,￣ anyway here's what actually worked for me"

vs. generic:
> "Building AI agents requires careful attention to memory management and persistent state. Here are five best practices..."

---

## File Changes Summary

| File | Change |
|------|--------|
| `api/chat/brain/behavioral/TwBehavioralEvent.kt` | NEW — event model |
| `api/chat/brain/behavioral/TwStyleModel.kt` | NEW — style/interest/topic models |
| `api/chat/brain/behavioral/TwBehavioralLearner.kt` | NEW — core learning engine |
| `api/chat/autonomous/TwAutonomousAgent.kt` | MODIFY — inject style into generation |
| `core/agent/v2/Agent.kt` | MODIFY — wire behavioral learner |
| `core/agent/ActionExecutor.kt` | MODIFY — record content events |
| `core/workflow/WorkflowExecutor.kt` | MODIFY — record workflow outcomes |
| `AndroidManifest.xml` | ADD: PACKAGE_USAGE_STATS permission |

---

## Verification

1. Use overlay agent to write some posts / notes
2. Approve some autonomous agent-generated content, edit others
3. After synthesis (6 hours later), check `brain/behavioral/style_model.json`
4. Check that style model reflects actual writing patterns
5. Next morning — check generated content matches style model
6. Edit generated content → new events recorded → re-synthesis captures changes

---

## Testing Checklist

- [ ] Style model accurately reflects writing tone after 10+ events
- [ ] Interest graph correctly identifies projects and tools
- [ ] Topic model extracts relevant themes
- [ ] Generated content actually sounds like the user (not generic)
- [ ] Editing feedback improves subsequent generations
- [ ] Model updates after user approves/edits content
- [ ] Privacy: no raw content sent to external APIs (analysis is local)
- [ ] Model persists across app restarts
- [ ] Storage size is reasonable (<5MB for 5000 events)
