# PLAN-001: Cross-Session Agent Memory
## Extend Brain Memory to Overlay & Executor Agents

---

## Problem Statement

Currently, **only the AI Chat agent** (`TwAgentChatBrain`) has cross-session memory via `TwMemoryManager`. The overlay agent (`core/agent/v2/Agent.kt`) and executor agents start **completely blank** every session — they have no memory, no user context, no learned behaviors.

**Root cause:** `UIAgentModeManager` (overlay) and the executor agent both use `SystemPromptConfig`-derived prompts that have zero memory injection. `TwAgentChatBrain.initialize()` is only called from the AI Chat screen's `ChatViewModel`.

---

## Architecture

```
TwGlobalBrain (new singleton, shared by ALL agents)
├── TwMemoryManager (existing — file-based memory)
├── TwAgentMemory    (new — overlay/executor session state)
├── TwBrainBridge    (new — injection helper)
│
├── attachToAIChat()     → existing TwAgentChatBrain path
├── attachToOverlay()    → NEW: inject memory into overlay agent
├── attachToExecutor()   → NEW: inject memory into executor agent
```

**Key insight:** `TwMemoryManager` is already a singleton. We don't need separate storage — we need **separate prompt injection** for each agent type. The memory data is shared; the system prompt additions differ per agent.

---

## Implementation Details

### Phase 1: Create `TwGlobalBrain` singleton

**File:** `api/chat/brain/TwGlobalBrain.kt` (NEW)

```kotlin
class TwGlobalBrain private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: TwGlobalBrain? = null
        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TwGlobalBrain(ctx.applicationContext).also { INSTANCE = it }
        }
    }

    // Reuse existing memory manager (same data, different injection)
    private val memoryManager = TwMemoryManager.getInstance(context)

    // Per-agent memory state (ephemeral, not persisted)
    private val overlayState = AgentMemoryState()
    private val executorState = AgentMemoryState()

    data class AgentMemoryState(
        val loadedSkills: MutableSet<TwLoadedSkill> = mutableSetOf(),
        val midSessionNotes: MutableList<TwMidSessionNote> = mutableListOf(),
        val activeMode: AgentMode = AgentMode.NORMAL,
        val iterationBudget: Int = DEFAULT_ITERATION_BUDGET
    )

    enum class AgentMode { NORMAL, YOLO, FAST, DEEP_REASONING }

    const val DEFAULT_ITERATION_BUDGET = 20

    /**
     * Inject memory context into overlay agent system prompt.
     * Called when overlay agent starts (UIAgentModeManager.setEnabled(true)).
     */
    fun getOverlaySystemPromptAddition(
        currentTask: String? = null,
        loadedSkills: List<TwSkillInfo> = emptyList()
    ): String {
        val memory = runBlocking { memoryManager.loadMemory() }
        val userProfile = runBlocking { memoryManager.loadUserProfile() }

        return buildString {
            // Memory summary (condensed)
            if (memory.memories.isNotEmpty()) {
                appendLine("## CROSS-SESSION MEMORY")
                appendLine("These memories persist across all sessions:\n")
                // Show top 10 most important/recent
                memory.memories
                    .sortedByDescending { it.importance }
                    .take(10)
                    .forEach { entry ->
                        appendLine("- [${entry.category}] ${entry.title}: ${entry.content.take(100)}")
                    }
                appendLine()
            }

            // User context
            if (userProfile.name.isNotEmpty()) {
                appendLine("## USER CONTEXT")
                appendLine("Name: ${userProfile.name}")
                if (userProfile.bio.isNotEmpty()) {
                    appendLine("Bio: ${userProfile.bio}")
                }
                appendLine()
            }

            // Loaded skills (overlay agents can use skills too)
            if (loadedSkills.isNotEmpty()) {
                appendLine("## ACTIVE SKILLS")
                loadedSkills.forEach { skill ->
                    appendLine("- ${skill.name}: ${skill.description}")
                }
                appendLine()
            }

            // Agent-specific mode
            when (overlayState.activeMode) {
                AgentMode.YOLO -> appendLine("MODE: YOLO — skip danger confirmations.\n")
                AgentMode.FAST -> appendLine("MODE: FAST — prioritize speed.\n")
                AgentMode.DEEP_REASONING -> appendLine("MODE: DEEP REASONING — thorough analysis.\n")
                AgentMode.NORMAL -> {}
            }
        }
    }

    /**
     * Inject memory context into executor agent system prompt.
     * Called when executor agent is initialized for a task.
     */
    fun getExecutorSystemPromptAddition(
        taskContext: String? = null
    ): String {
        val memory = runBlocking { memoryManager.loadMemory() }
        val userProfile = runBlocking { memoryManager.loadUserProfile() }

        return buildString {
            if (memory.memories.isNotEmpty()) {
                appendLine("## PERSISTENT MEMORY")
                // Find memories relevant to task context
                val relevant = if (taskContext != null) {
                    memory.memories.filter { entry ->
                        entry.content.contains(taskContext, ignoreCase = true) ||
                        entry.tags.any { it.contains(taskContext, ignoreCase = true) }
                    }.take(5)
                } else {
                    memory.memories.sortedByDescending { it.importance }.take(5)
                }

                relevant.forEach { entry ->
                    appendLine("- ${entry.title}: ${entry.content.take(150)}")
                }
                appendLine()
            }

            if (userProfile.name.isNotEmpty()) {
                appendLine("## USER: ${userProfile.name}")
                if (userProfile.preferences.isNotEmpty()) {
                    appendLine("Preferences:")
                    userProfile.preferences.forEach { (k, v) ->
                        appendLine("  - $k: $v")
                    }
                }
                appendLine()
            }
        }
    }

    // ─── Skill loading for non-AI-Chat agents ───────────────────────────────

    fun loadSkillForOverlay(skillName: String): TwLoadedSkill? {
        return TwSkillsManager.lazyLoad(skillName)?.also {
            overlayState.loadedSkills.add(it)
        }
    }

    fun loadSkillForExecutor(skillName: String): TwLoadedSkill? {
        return TwSkillsManager.lazyLoad(skillName)?.also {
            executorState.loadedSkills.add(it)
        }
    }

    // ─── Memory writes from any agent ───────────────────────────────────────

    /**
     * Save a memory entry from any agent type.
     * Overlay and executor agents can call this to persist learned facts.
     */
    suspend fun remember(
        title: String,
        content: String,
        category: String = "general",
        importance: Float = 0.5f,
        source: String = "agent",
        tags: List<String> = emptyList()
    ): TwMemoryEntry {
        return memoryManager.addMemory(title, content, category, importance, source, tags)
    }

    /**
     * Search memories from any agent context.
     */
    suspend fun recall(query: String): List<TwMemoryEntry> {
        return memoryManager.searchMemories(query)
    }

    // ─── Tool call tracking (shared insights) ──────────────────────────────

    fun trackToolCall(toolName: String, agentType: AgentType) {
        // Update shared insights (existing TwBrainInsights)
        runBlocking {
            val memory = memoryManager.loadMemory()
            memory.insights.trackToolCall(toolName)
            memoryManager.saveMemory(memory)
        }
    }

    enum class AgentType { AI_CHAT, OVERLAY, EXECUTOR }
}
```

### Phase 2: Wire overlay agent to `TwGlobalBrain`

**File:** `core/agent/UIAgentModeManager.kt` — MODIFY

```kotlin
// Add to UIAgentModeManager:
private val globalBrain by lazy { TwGlobalBrain.getInstance(context) }

fun getOverlaySystemPromptAddition(currentTask: String? = null): String {
    return globalBrain.getOverlaySystemPromptAddition(currentTask)
}

// Also track tool calls
fun trackOverlayToolCall(toolName: String) {
    globalBrain.trackToolCall(toolName, TwGlobalBrain.AgentType.OVERLAY)
}
```

**File:** `core/agent/v2/Agent.kt` — MODIFY `run()` method

```kotlin
// In Agent.run() — inject memory at the start of each task
suspend fun run(initialTask: String, maxSteps: Int = 150) {
    acquireWakeLock()

    // NEW: Inject cross-session memory into the agent's perception
    val globalBrain = TwGlobalBrain.getInstance(context)
    val memoryContext = globalBrain.getOverlaySystemPromptAddition(initialTask)

    // Store in agent state so action executor can use it
    state.memoryContext = memoryContext

    memoryManager.addNewTask(initialTask)
    state.stopped = false
    Log.d(TAG, "--- Agent starting task: '$initialTask' ---")
    // ... rest of existing code
}
```

**Add to `AgentState`:**
```kotlin
data class AgentState(
    var stopped: Boolean = false,
    var nSteps: Int = 0,
    var memoryContext: String = ""  // NEW: cross-session memory
)
```

### Phase 3: Wire executor agent to `TwGlobalBrain`

**File:** `core/agent/Agent.kt` — MODIFY (the v1 executor agent)

```kotlin
// At initialization, inject global brain memory
private val globalBrain by lazy { TwGlobalBrain.getInstance(context) }

// When building system prompt for executor tasks:
val executorMemoryContext = globalBrain.getExecutorSystemPromptAddition(taskContext)
// Append to system prompt
```

### Phase 4: Expose brain tools to overlay/executor via `TwConversationLoop`

The existing `TwConversationLoop.BRAIN_TOOL_NAMES` already has all 16 tools. These need to be registered with the overlay and executor agents' tool handlers too.

```kotlin
// In ToolRegistration.kt — extend brain tool registration:
fun registerBrainToolsForOverlay(ctx: Context, handler: AIToolHandler) {
    // Same brain tools, but routing through TwGlobalBrain instead of per-chat state
    TwConversationLoop.BRAIN_TOOL_NAMES.forEach { toolName ->
        handler.registerTool(
            name = toolName,
            dangerCheck = { false },
            descriptionGenerator = { getBrainToolDescription(toolName) },
            executor = { tool ->
                // Route through global brain (no chatId needed)
                runBlocking {
                    val result = TwGlobalBrain.getInstance(ctx)
                        .handleBrainToolSync(tool.name, tool.parameters.associate { it.name to it.value?.toString() ?: "" })
                    result
                }
            }
        )
    }
}
```

---

## Memory Injection Format Per Agent

### AI Chat (existing, most verbose)
```
## CROSS-SESSION MEMORY
- [project] Twent brain: The AI Chat brain system uses TwAgentChatBrain...
- [user_pref] Model preference: prefers Claude for coding tasks
...

## USER CONTEXT
Name: HP
Bio: 18-year-old indie developer
Preferences:
  - Model: Claude
  - Voice: am_adam
...

## ACTIVE SKILLS
- android-development: Build Android apps with Kotlin...
...

## PERSISTENT INSIGHTS
Sessions: 47 | Tool calls: 312 | Top tool: execute_shell (89x)
```

### Overlay Agent (task-focused, condensed)
```
## WHAT I REMEMBER
- User works on Twent app development
- Latest task: fixing overlay memory persistence
- Last session: worked on cross-session brain

## USER
Name: HP | Prefers: fast responses, concise output
```

### Executor Agent (minimal, task-relevant only)
```
## RELEVANT MEMORY
- User building HolaOS competitor analysis
- Previous attempts: keyword-only search (too shallow)

## USER CONTEXT
Name: HP | Developer: Android (Kotlin)
```

---

## File Changes Summary

| File | Change | Lines |
|------|--------|-------|
| `api/chat/brain/TwGlobalBrain.kt` | NEW — shared singleton | ~250 |
| `core/agent/UIAgentModeManager.kt` | MODIFY — add brain injection | +15 |
| `core/agent/v2/Agent.kt` | MODIFY — inject memory at task start | +10 |
| `core/agent/Agent.kt` | MODIFY — executor brain injection | +8 |
| `core/tools/ToolRegistration.kt` | MODIFY — register brain tools for overlay | +20 |
| `core/agent/v2/AgentState.kt` | MODIFY — add memoryContext field | +1 |

---

## Verification

1. Open overlay agent, do some UI tasks, close overlay
2. Open overlay again — send "what did we do last time?"
3. Agent should recall previous overlay session tasks
4. Check `brain/MEMORY.md` — entries with `source="overlay"` should appear
5. Run executor agent task, close, run again — same test
6. Check `brain/INSIGHTS.md` — tool calls from all agent types should aggregate

---

## Testing Checklist

- [ ] Overlay agent retains memory across open/close cycles
- [ ] Executor agent accesses memory during task execution
- [ ] Brain tools (tw_remember, tw_recall) work from overlay context
- [ ] User profile is injected into both agents
- [ ] Insights aggregate tool calls from all agent types
- [ ] Memory doesn't leak between users (multi-user: future consideration)
