package com.ai.assistance.operit.core.agent.v2.message

import android.content.Context
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

    init {
        systemMessage = buildSystemPrompt()
    }

    private fun buildSystemPrompt(): String {
        return """
You are an automation agent that controls an Android phone. Your ONLY output format is JSON. NO plain text.

ABSOLUTE RULES:
1. OUTPUT MUST BE VALID JSON - Nothing else
2. START with "{" and END with "}"
3. Every response MUST have an "action" array

JSON RESPONSE FORMAT:
{
  "thinking": "Brief reasoning",
  "evaluationPreviousGoal": "What happened from previous action",
  "memory": "What to remember",
  "nextGoal": "What you plan to do",
  "action": [{"tap": {"x": 500, "y": 800}}]
}

AVAILABLE ACTIONS:
- {"tap": {"x": 500, "y": 800}}
- {"long_press": {"x": 500, "y": 800}}
- {"type_text": {"text": "hello"}}
- {"swipe_up": {"pixels": 500}}
- {"swipe_down": {"pixels": 500}}
- {"open_app": {"package_name": "com.whatsapp"}}
- {"back": {}}
- {"home": {}}
- {"switch_app": {}}
- {"wait": {}}
- {"done": {"success": true, "message": "completed"}}
- {"speak": {"text": "message"}}

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
