package com.ai.assistance.operit.core.agent.v2.message

import android.content.Context
import android.content.pm.PackageManager
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.v2.AgentOutput
import com.ai.assistance.operit.core.agent.v2.AgentSettings
import com.ai.assistance.operit.core.agent.v2.AgentStepInfo
import com.ai.assistance.operit.core.agent.v2.ScreenState
import com.ai.assistance.operit.core.agent.v2.ActionResult

class MemoryManager(
    private val context: Context,
    private var task: String,
    private val fileSystem: com.ai.assistance.operit.core.agent.v2.fs.FileSystem,
    private val settings: AgentSettings
) {
    private val historyItems = mutableListOf<HistoryItem>()
    private var systemMessage: String = ""
    private var stateMessage: String = ""
    private val contextMessages = mutableListOf<GeminiMessage>()
    private val installedApps: List<Pair<String, String>> = loadInstalledApps()

    init {
        systemMessage = buildSystemPrompt()
    }

    private fun loadInstalledApps(): List<Pair<String, String>> {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            apps.filter { it.enabled }
                .map { app -> app.loadLabel(pm).toString() to app.packageName }
                .sortedBy { it.first.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildSystemPrompt(): String {
        val appsList = installedApps.take(100).joinToString("\n") { (name, pkg) ->
            "  - $name ($pkg)"
        }
        val appsSection = if (installedApps.isNotEmpty()) {
            """
            
## INSTALLED APPLICATIONS (Use these names with open_app action)
You can open ANY app below by using the open_app action with the app name.
This includes hidden apps and system apps - no need to navigate app drawer!
$appsList
${if (installedApps.size > 100) "  ... and ${installedApps.size - 100} more apps" else ""}
            """.trimIndent()
        } else ""

        return """
You are an automation agent that controls an Android phone. Your ONLY output format is JSON. NO plain text.

$appsSection

═══════════════════════════════════════════════════════════════════════════════
## AVAILABLE TOOLS

### UI AUTOMATION TOOLS (Use these to interact with the screen)
- tap: Tap at coordinates {"tap": {"x": 500, "y": 800}}
- double_tap: Double tap at coordinates {"double_tap": {"x": 500, "y": 800}}
- long_press: Long press at coordinates {"long_press": {"x": 500, "y": 800, "duration_ms": 1500}}
- click_element: Click element by index, text, or content_description
  {"click_element": {"index": 5}} or {"click_element": {"text": "Submit"}}
- swipe: Custom swipe {"swipe": {"start_x": 500, "start_y": 1000, "end_x": 500, "end_y": 500}}
- swipe_left/right/up/down: Swipe in direction {"swipe_up": {"pixels": 500}}
- scroll_left/right/up/down: Scroll in direction
- type_text: Type text into focused input {"type_text": {"text": "hello"}}
- open_app: Open app by NAME (use installed apps list above!) {"open_app": {"app_name": "My Files"}}
- back: Press back button {"back": {}}
- home: Press home button {"home": {}}
- switch_app: App switcher {"switch_app": {}}
- press_key: Press key (back, home, enter, recents)
- wait: Wait 5 seconds {"wait": {}}
- done: Complete task {"done": {"success": true, "message": "Task completed"}}
- speak: Speak to user {"speak": {"text": "Message"}}

### ANDROID SYSTEM TOOLS
- list_installed_apps: Get list of all installed applications
- start_app: Launch applications by package name
- stop_app: Force stop app background processes
- get_notifications: Read device notifications
- get_current_activity: Get current foreground activity

### FILE SYSTEM TOOLS
- read_file: Read file content
- write_file: Write content to a file
- list_dir: List directory contents

### SHELL TOOLS
- execute_shell: Execute shell commands

═══════════════════════════════════════════════════════════════════════════════
## CRITICAL RULES

1. OPEN APPS DIRECTLY: When you need to open an app, use open_app with the app name from the installed apps list above. NEVER guess - use the list!

2. USE APP NAME MATCHING: The open_app action accepts app names (like "My Files", "WhatsApp"). The system will match against the installed apps list.

3. UI ELEMENT INDEXING: Interactive elements on screen are numbered like [1], [2], [3]. Use click_element with the index to tap them accurately.

4. OUTPUT MUST BE VALID JSON - Nothing else. START with "{" and END with "}"

═══════════════════════════════════════════════════════════════════════════════
## JSON RESPONSE FORMAT

{
  "thinking": "Brief reasoning about what you see and what to do next",
  "evaluationPreviousGoal": "What happened from previous action",
  "memory": "What important info to remember",
  "nextGoal": "What you plan to do in this step",
  "action": [
    {"open_app": {"app_name": "My Files"}},
    {"click_element": {"index": 1}},
    {"swipe_up": {"pixels": 300}}
  ]
}

Now execute the user's task.
        """.trimIndent()
    }

    fun addNewTask(newTask: String) {
        this.task = newTask
        historyItems.add(HistoryItem(stepNumber = 0, systemMessage = "Task: $newTask"))
    }

    fun addContextMessage(message: LlmMessage) {
        contextMessages.add(
            GeminiMessage(
                role = when (message.role) {
                    MessageRole.SYSTEM -> "system"
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                text = message.content
            )
        )
    }

    fun getMessages(): List<LlmMessage> {
        val result = mutableListOf<LlmMessage>()
        result.add(LlmMessage(MessageRole.SYSTEM, systemMessage))
        
        if (stateMessage.isNotEmpty()) {
            result.add(LlmMessage(MessageRole.USER, stateMessage))
        }
        
        contextMessages.forEach { gm ->
            result.add(LlmMessage(
                role = when (gm.role) {
                    "system" -> MessageRole.SYSTEM
                    "user" -> MessageRole.USER
                    else -> MessageRole.ASSISTANT
                },
                content = gm.text
            ))
        }
        
        return result
    }

    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo?,
        screenState: ScreenState
    ) {
        val historyText = buildHistoryDescription()
        
        stateMessage = buildString {
            append("Current Task: $task\n")
            append("Step: ${stepInfo?.stepNumber ?: 1}/${stepInfo?.maxSteps ?: 150}\n\n")
            
            append("Screen State:\n")
            append("Activity: ${screenState.activityName}\n")
            append("Keyboard: ${screenState.isKeyboardOpen}\n\n")
            append("UI Elements:\n${screenState.uiRepresentation}\n\n")
            
            if (historyText.isNotEmpty()) {
                append("History:\n$historyText\n\n")
            }
            
            result?.let { results ->
                append("Last Result:\n")
                results.forEach { res ->
                    res.longTermMemory?.let { append("$it\n") }
                    res.error?.let { append("Error: $it\n") }
                }
            }
            
            append("What should you do next? (Respond in JSON)")
        }
    }

    private fun buildHistoryDescription(): String {
        return historyItems.takeLast(5).joinToString("\n") { item ->
            val parts = mutableListOf<String>()
            item.evaluation?.let { parts.add("Eval: $it") }
            item.memory?.let { parts.add("Memory: $it") }
            item.nextGoal?.let { parts.add("Goal: $it") }
            parts.joinToString(" | ")
        }
    }
}
