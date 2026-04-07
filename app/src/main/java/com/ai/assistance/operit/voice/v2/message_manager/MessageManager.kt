package com.ai.assistance.operit.voice.v2.message_manager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.voice.v2.ActionResult
import com.ai.assistance.operit.voice.v2.AgentOutput
import com.ai.assistance.operit.voice.v2.AgentSettings
import com.ai.assistance.operit.voice.v2.AgentStepInfo
import com.ai.assistance.operit.voice.v2.ScreenState
import com.ai.assistance.operit.voice.v2.SystemPromptLoader
import com.ai.assistance.operit.voice.v2.UserMessageBuilder
import com.ai.assistance.operit.voice.v2.fs.FileSystem
import com.ai.assistance.operit.voice.v2.llm.GeminiMessage
import com.ai.assistance.operit.voice.v2.llm.TextPart

/**
 * Manages the agent's short-term memory, including conversation history and prompt construction.
 * This class is the central hub for managing the state that gets sent to the LLM.
 *
 * @param context The Android application context.
 * @param task The initial user request or task for the agent.
 * @param fileSystem An instance of the agent's file system.
 * @param settings The agent's configuration settings.
 * @param sensitiveData A map of placeholder keys to sensitive string values to be filtered from prompts.
 * @param initialState An optional initial state to resume from a previous session.
 */
class MemoryManager(
    private val context: Context,
    private var task: String,
    private val fileSystem: FileSystem,
    private val settings: AgentSettings,
    private val sensitiveData: Map<String, String>? = null,
    initialState: MemoryState = MemoryState()
) {
    val state: MemoryState = initialState

    init {
        // On initialization, create and set the system message if it doesn't already exist.
        if (state.history.systemMessage == null) {
            val systemPromptLoader = SystemPromptLoader(context)
            val systemMessage = systemPromptLoader.getSystemMessage(settings)
            state.history.systemMessage = filterSensitiveData(systemMessage)
        }
    }

    /**
     * The primary method to update the memory and generate the next prompt.
     * This should be called once per agent step.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo?,
        screenState: ScreenState
    ) {
        // 1. Update the structured history with the outcome of the last step.
        updateHistory(modelOutput, result, stepInfo)

        // 2. Build the arguments for the prompt builder.
        val builderArgs = UserMessageBuilder.Args(
            task = this.task,
            screenState = screenState,
            fileSystem = this.fileSystem,
            agentHistoryDescription = getAgentHistoryDescription(),
            readStateDescription = state.readStateDescription,
            stepInfo = stepInfo,
            sensitiveDataDescription = getSensitiveDataDescription(),
            availableFilePaths = null // Assuming we get this from fileSystem or elsewhere
        )

        // 3. Construct the new user message using the builder.
        var stateMessage = UserMessageBuilder.build(builderArgs)
        stateMessage = filterSensitiveData(stateMessage)

        // 4. Update the history with the new state message, clearing old context.
        state.history.stateMessage = stateMessage
        state.history.contextMessages.clear()
    }

    /**
     * Adds a new task, replacing the old one, and records this change in the history.
     * Also includes the list of installed apps so the agent knows what's available.
     */
    fun addNewTask(newTask: String) {
        this.task = newTask

        // Fetch installed apps list
        val installedAppsList = getInstalledAppsList()

        // Build system message with user request AND installed apps info
        val systemMessage = buildString {
            append("<user_request> added: $newTask\n\n")
            if (installedAppsList.isNotEmpty()) {
                append("<installed_apps>\n")
                append("The following apps are installed on this device. Use this info to decide whether to open an app or launch a URL:\n")
                append(installedAppsList.joinToString(", "))
                append("\n</installed_apps>")
            }
        }

        val taskUpdateItem = HistoryItem(
            stepNumber = 0,
            systemMessage = systemMessage
        )

        state.agentHistoryItems.add(taskUpdateItem)
    }

    /**
     * Gets a list of installed app names and their package names.
     * Limited to commonly used apps to avoid bloating the prompt.
     */
    private fun getInstalledAppsList(): List<String> {
        return try {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            val result = mutableListOf<String>()
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString()
                result.add("$label (${app.packageName})")
            }
            result.sortedBy { s -> s.lowercase() }.take(100)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addContextMessage(message: GeminiMessage){
        //TODO implement filtering here too
        state.history.contextMessages.add(message)
    }

    /**
     * Returns the complete list of messages ready to be sent to the LLM.
     */
    fun getMessages(): List<GeminiMessage> {
        return state.history.getMessages()
    }

    /**
     * Processes the results of the last step and adds a new `HistoryItem` to the state.
     */
    private fun updateHistory(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo?
    ) {
        // Clear the one-time read state from the previous turn.
        state.readStateDescription = ""

        val actionResultsText = result?.mapIndexedNotNull { index, actionResult ->
            // Populate the one-time read state if necessary
            if (actionResult.includeExtractedContentOnlyOnce && !actionResult.extractedContent.isNullOrBlank()) {
                state.readStateDescription += actionResult.extractedContent + "\n"
            }

            // Format the action result for long-term history
            when {
                !actionResult.longTermMemory.isNullOrBlank() -> "Action ${index + 1}: ${actionResult.longTermMemory}"
                !actionResult.extractedContent.isNullOrBlank() && !actionResult.includeExtractedContentOnlyOnce -> "Action ${index + 1}: ${actionResult.extractedContent}"
                !actionResult.error.isNullOrBlank() -> "Action ${index + 1}: ERROR - ${actionResult.error.take(200)}"
                else -> null
            }
        }?.joinToString("\n")

        val historyItem = if (modelOutput == null) {
            if(stepInfo?.stepNumber != 1){
                HistoryItem(stepNumber = stepInfo?.stepNumber, error = "Agent failed to produce a valid output.")
            }else{
                HistoryItem(stepNumber = stepInfo.stepNumber, error = "Agent not asked to create output yet")
            }
        } else {
            HistoryItem(
                stepNumber = stepInfo?.stepNumber,
                evaluation = modelOutput.evaluationPreviousGoal,
                memory = modelOutput.memory,
                nextGoal = modelOutput.nextGoal,
                actionResults = actionResultsText?.let { "Action Results:\n$it" }
            )
        }
        state.agentHistoryItems.add(historyItem)
    }

    /**
     * Generates the <agent_history> string, truncating it if it exceeds `maxHistoryItems`.
     */
    private fun getAgentHistoryDescription(): String {
        val items = state.agentHistoryItems
        val maxItems = settings.maxHistoryItems ?: items.size

        if (items.size <= maxItems) {
            return items.joinToString("\n") { it.toPromptString() }
        }

        val omittedCount = items.size - maxItems
        val recentItemsCount = maxItems - 1

        val result = mutableListOf<String>()
        result.add(items.first().toPromptString()) // Always include the first item
        result.add("<sys>[... $omittedCount previous steps omitted...]</sys>")
        result.addAll(items.takeLast(recentItemsCount).map { it.toPromptString() })

        return result.joinToString("\n")
    }

    /**
     * Creates a description of available sensitive data placeholders.
     * This is simplified as the URL-matching logic is not applicable.
     */
    private fun getSensitiveDataDescription(): String? {
        val placeholders = sensitiveData?.keys
        if (placeholders.isNullOrEmpty()) return null

        return "Here are placeholders for sensitive data:\n${placeholders.joinToString()}\nTo use them, write <secret>the placeholder name</secret>"
    }

    /**
     * Scrubs sensitive data from a message before sending it to the LLM.
     */
    private fun filterSensitiveData(message: GeminiMessage): GeminiMessage {
        if (sensitiveData.isNullOrEmpty()) return message

        val newParts = message.parts.map { part ->
            if (part is TextPart) {
                var newText = part.text
                sensitiveData.forEach { (key, value) ->
                    newText = newText.replace(value, "<secret>$key</secret>")
                }
                TextPart(newText)
            } else {
                part
            }
        }
        return message.copy(parts = newParts)
    }
}