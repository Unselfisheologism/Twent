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
import com.ai.assistance.operit.core.agent.v2.actions.Action
import java.io.IOException

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
        systemMessage = loadSystemPromptFromAssets()
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

    private fun generateActionsDescription(): String {
        val allActionSpecs = Action.getAllSpecs()
        return buildString {
            allActionSpecs.forEach { spec ->
                append("<action>\n")
                append("  <name>${spec.name}</name>\n")
                append("  <description>${spec.description}</description>\n")
                if (spec.params.isNotEmpty()) {
                    append("  <parameters>\n")
                    spec.params.forEach { param ->
                        append("    <param>\n")
                        append("      <name>${param.name}</name>\n")
                        append("      <type>${param.type.simpleName}</type>\n")
                        append("      <description>${param.description}</description>\n")
                        append("    </param>\n")
                    }
                    append("  </parameters>\n")
                }
                append("</action>\n\n")
            }
        }.trim()
    }

    private fun generateIntentsCatalog(): String {
        val intents = com.ai.assistance.operit.intents.IntentRegistry.listIntents(context)
        if (intents.isEmpty()) return ""
        
        return buildString {
            append("\n<intents_catalog>\n")
            intents.forEach { intent ->
                append("  <intent>\n")
                append("    <name>${intent.name}</name>\n")
                append("    <description>${intent.description()}</description>\n")
                if (intent.parametersSpec().isNotEmpty()) {
                    append("    <parameters>\n")
                    intent.parametersSpec().forEach { param ->
                        append("      <param name=\"${param.name}\" type=\"${param.type}\" required=\"${param.required}\">${param.description}</param>\n")
                    }
                    append("    </parameters>\n")
                }
                append("  </intent>\n")
            }
            append("</intents_catalog>\n\n")
            append("Usage: To launch any of the above intents, add an action like {\"launch_intent\": {\"intent_name\": \"Dial\", \"parameters\": {\"phone_number\": \"+123456789\"}}}.\n")
        }
    }

    private fun loadSystemPromptFromAssets(): String {
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

        val actionsDescription = generateActionsDescription()
        
        val intentsCatalog = generateIntentsCatalog()

        return try {
            val template = context.assets.open("prompts/system_prompt.md").bufferedReader().use { it.readText() }
            template
                .replace("{max_actions}", settings.maxActionsPerStep.toString())
                .replace("{available_actions}", actionsDescription)
                .replace("{user_info}", "User: Android Owner")
                .replace("{intents_catalog}", intentsCatalog)
        } catch (e: IOException) {
            // Fallback to basic prompt if asset not found
            """
You are an automation agent that controls an Android phone.

$appsSection

## AVAILABLE ACTIONS
$actionsDescription

## CRITICAL RULES
1. OUTPUT MUST BE VALID JSON
2. NEVER use "done" in first 3 steps - explore screen first
3. Confirm action worked before declaring done
4. If no visible effect, try DIFFERENT action

## RESPONSE FORMAT
{
  "thinking": "reasoning...",
  "evaluationPreviousGoal": "what happened...",
  "memory": "info to remember...",
  "nextGoal": "what to do next...",
  "action": [{"action_name": {"param": "value"}}]
}
            """.trimIndent()
        }
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
        val todoText = try { fileSystem.getTodoContents() } catch (e: Exception) { "" }
        
        stateMessage = buildString {
            append("Current Task: $task\n")
            append("Step: ${stepInfo?.stepNumber ?: 1}/${stepInfo?.maxSteps ?: 150}\n\n")
            
            if (todoText.isNotBlank()) {
                append("Todo List (checklist from your plan):\n$todoText\n\n")
            }
            
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
            
            if (result?.any { it.error != null } == true) {
                append("\n⚠️ Previous action had an error. ADAPT your strategy - don't repeat failed action.\n")
            }
            
            append("What should you do next? (Respond in JSON)")
        }
    }

    private fun buildHistoryDescription(): String {
        if (historyItems.isEmpty()) return "No history yet."

        val recentItems = historyItems.takeLast(10)
        
        val hasRecentErrors = recentItems.any { item ->
            item.actionResults?.contains("not found") == true ||
            item.actionResults?.contains("Error") == true ||
            item.actionResults?.contains("failed") == true
        }
        
        val lastGoal = recentItems.lastOrNull()?.nextGoal
        val lastEvaluation = recentItems.lastOrNull()?.evaluation
        
        return buildString {
            if (hasRecentErrors) {
                append("⚠️ Previous actions had errors or issues. ADAPT your strategy - don't repeat failed actions.\n\n")
            }
            
            if (lastGoal != null) {
                append("🎯 Last Goal: $lastGoal\n")
                if (lastEvaluation != null) {
                    append("📊 Evaluation: $lastEvaluation\n")
                }
                append("\n")
            }
            
            recentItems.forEachIndexed { index, item ->
                append("Step ${item.stepNumber}:\n")
                item.evaluation?.let { append("  Evaluation: $it\n") }
                item.memory?.let { append("  Memory: $it\n") }
                item.nextGoal?.let { append("  Next Goal: $it\n") }
                item.actionResults?.let { append("  Action Results: $it\n") }
                append("\n")
            }
        }.trim()
    }
}
