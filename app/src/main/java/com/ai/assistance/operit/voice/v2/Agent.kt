package com.ai.assistance.operit.voice.v2

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.voice.v2.actions.ActionExecutor
import com.ai.assistance.operit.voice.v2.fs.FileSystem
import com.ai.assistance.operit.voice.v2.llm.AgentLlmAdapter
import com.ai.assistance.operit.voice.v2.llm.GeminiMessage
import com.ai.assistance.operit.voice.v2.message_manager.MemoryManager
import com.ai.assistance.operit.voice.v2.perception.Perception
import com.ai.assistance.operit.voice.utilities.SpeechCoordinator
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayPriority
import com.ai.assistance.operit.overlay.OverlayPosition
import kotlinx.coroutines.delay

/**
 * The main conductor of the agent.
 * This class owns all the necessary components and runs the primary SENSE -> THINK -> ACT loop.
 *
 * @param settings The agent's configuration.
 * @param memoryManager The agent's short-term memory and prompt builder.
 * @param perception The agent's "eyes," responsible for analyzing the screen.
 * @param llmAdapter The LLM adapter for communicating with the AI provider (uses user's configured provider).
 * @param actionExecutor The agent's "hands," responsible for executing actions on the device.
 * @param fileSystem The agent's long-term file storage.
 * @param context The Android application context.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Agent(
    private val settings: AgentSettings,
    private val memoryManager: MemoryManager,
    private val perception: Perception,
    private val llmAdapter: AgentLlmAdapter,
    private val actionExecutor: ActionExecutor,
    private val fileSystem: FileSystem,
    private val context: Context
) {
    // The agent's internal state, which is updated at each step.
    val state: AgentState = AgentState()
    private val TAG = "AgentV2"

    // Speech coordinator for voice notifications
    private val speechCoordinator = SpeechCoordinator.getInstance(context)

    // Visual feedback manager for task glow/status
    private val visualFeedbackManager = com.ai.assistance.operit.voice.utilities.VisualFeedbackManager.getInstance(context)

    // A complete, long-term record of the entire session.
    // We use <Unit> because we haven't defined a custom structured output for the 'done' action yet.
    val history: AgentHistoryList<Unit> = AgentHistoryList()

    /**
     * The main entry point to start the agent's execution loop.
     *
     * @param initialTask The high-level task requested by the user.
     * @param maxSteps The maximum number of steps the agent can take before stopping.
     */
    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        memoryManager.addNewTask(initialTask)
        state.stopped = false

        // Show persistent glow when task starts
        visualFeedbackManager.showTaskActiveGlow()

        // Announce task start to user
        val taskAnnouncement = when {
            initialTask.contains("mini-app", ignoreCase = true) || initialTask.contains("mini app", ignoreCase = true) ->
                "Creating your mini-app. This may take a few moments..."
            initialTask.contains("create", ignoreCase = true) || initialTask.contains("build", ignoreCase = true) ->
                "Starting task: ${initialTask.take(60)}..."
            else ->
                "Starting task execution..."
        }
        speechCoordinator.speakToUser(taskAnnouncement)

        // Show persistent task status
        OverlayDispatcher.show(
            text = "🚀 Task: $initialTask",
            priority = OverlayPriority.TASKS,
            duration = 0L,
            position = OverlayPosition.TOP
        )

        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        while (!state.stopped && state.nSteps <= maxSteps) {
            // Check if task was stopped externally (via stop button)
            if (com.ai.assistance.operit.voice.v2.AgentService.shouldStopTask) {
                Log.d(TAG, "--- ⛔ External stop requested - stopping execution ---")
                speechCoordinator.speakToUser("Task stopped by user.")
                state.stopped = true
                visualFeedbackManager.hideTaskActiveGlow()
                break
            }

            // Check if task is paused - wait until resumed
            while (com.ai.assistance.operit.voice.v2.AgentService.isTaskPaused && !state.stopped) {
                // Check for stop while paused
                if (com.ai.assistance.operit.voice.v2.AgentService.shouldStopTask) {
                    Log.d(TAG, "--- ⛔ External stop requested while paused - stopping execution ---")
                    speechCoordinator.speakToUser("Task stopped by user.")
                    state.stopped = true
                    visualFeedbackManager.hideTaskActiveGlow()
                    return
                }
                Log.d(TAG, "--- ⏸️ Task paused, waiting for resume... ---")
                delay(500) // Check every 500ms
            }
            
            Log.d(TAG,"\n--- Step ${state.nSteps}/$maxSteps ---")

            // 1. SENSE: Observe the current state of the screen.
            Log.d(TAG,"👀 Sensing screen state...")
            val screenState = perception.analyze()

            // 2. THINK (Prepare Prompt): Update memory with the results of the LAST step
            // and create the new prompt using the CURRENT screen state.
            Log.d(TAG,"🧠 Preparing prompt...")
            memoryManager.createStateMessage(
                modelOutput = state.lastModelOutput,
                result = state.lastResult,
                stepInfo = AgentStepInfo(state.nSteps, maxSteps),
                screenState = screenState
            )

            // 3. THINK (Get Decision): Send the prepared messages to the LLM.
            Log.d(TAG,"🤔 Asking LLM for next action...")
            val messages = memoryManager.getMessages()
            val agentOutput = llmAdapter.generateAgentOutput(messages)

            // --- Handle LLM Failure ---
            if (agentOutput == null) {
                Log.d(TAG,"❌ LLM failed to return a valid action. Retrying...")
                state.consecutiveFailures++
                // Add a corrective message for the next attempt.
                memoryManager.addContextMessage(GeminiMessage(text = "System Note: Your previous output was not valid JSON. Please ensure your response is correctly formatted."))
                if (state.consecutiveFailures >= settings.maxFailures) {
                    Log.d(TAG,"❌ Agent failed too many times consecutively. Stopping.")
                    speechCoordinator.speakToUser("Agent failed after multiple attempts. Stopping execution.")

                    // Hide glow on failure
                    visualFeedbackManager.hideTaskActiveGlow()

                    break
                }
                delay(1000) // Wait a moment before retrying
                continue // Skip to the next loop iteration
            }
            state.consecutiveFailures = 0
            state.lastModelOutput = agentOutput
            Log.d(TAG, agentOutput.toString())
            Log.d(TAG,"🤖 LLM decided: ${agentOutput.nextGoal}")

            // Show thoughts overlay - PERSISTENT during task execution
            val thoughtText = buildString {
                    agentOutput.thinking?.let { if (it.isNotEmpty()) append("Thinking: ${agentOutput.thinking}\n") }
                    agentOutput.memory?.let { if (it.isNotEmpty()) append("Memory: ${agentOutput.memory}\n") }
                    agentOutput.nextGoal?.let { if (it.isNotEmpty()) append("Next Goal: ${agentOutput.nextGoal}") }
                }.trim()

                if (thoughtText.isNotEmpty()) {
                    // Persistent overlay: no auto-dismiss, stays until next step updates it
                    OverlayDispatcher.show(
                        text = thoughtText,
                        priority = OverlayPriority.TASKS,
                        duration = 0L, // 0 = persistent, never auto-dismiss
                        position = OverlayPosition.TOP
                    )

                    // Speak key milestones for user awareness
                    val nextGoal = agentOutput.nextGoal.orEmpty()
                    val thinking = agentOutput.thinking.orEmpty()
                    val milestoneText = when {
                        nextGoal.contains("mini-app", ignoreCase = true) || nextGoal.contains("mini app", ignoreCase = true) ->
                            "Creating mini-app: ${nextGoal.take(60)}..."
                        nextGoal.contains("write", ignoreCase = true) || nextGoal.contains("save", ignoreCase = true) ->
                            "Saving files..."
                        nextGoal.contains("list", ignoreCase = true) || nextGoal.contains("check", ignoreCase = true) ->
                            "Checking existing mini-apps..."
                        nextGoal.contains("delete", ignoreCase = true) || nextGoal.contains("remove", ignoreCase = true) ->
                            "Deleting mini-app..."
                        thinking.isNotEmpty() && thinking.length < 100 ->
                            "Working on: ${thinking.take(80)}..."
                        nextGoal.isNotEmpty() && nextGoal.length < 80 ->
                            "Step ${state.nSteps}: ${nextGoal.take(60)}..."
                        else -> null
                    }
                    milestoneText?.let { speechCoordinator.speakToUser(it) }
                }

            // 4. ACT: Execute the LLM's planned actions.
            Log.d(TAG,"💪 Executing actions...")
            val actionResults = mutableListOf<ActionResult>()
            for (action in agentOutput.action) {
                // Show action being executed
                val actionName = action::class.simpleName ?: "Action"
                OverlayDispatcher.show(
                    text = "⚡ $actionName...",
                    priority = OverlayPriority.TASKS,
                    duration = 0L,
                    position = OverlayPosition.TOP
                )

                val result = try {
                    actionExecutor.execute(action, screenState, context, fileSystem)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception executing action '${action::class.simpleName}': ${e.message}", e)
                    ActionResult(error = "Exception during execution: ${e.message}")
                }
                actionResults.add(result)
                Log.d(TAG,"  - Action '${action::class.simpleName}' executed. Result: ${result.longTermMemory ?: result.error ?: "OK"}")

                // Show action result immediately
                val resultText = when {
                    result.error != null -> "❌ ${result.error.take(80)}"
                    result.longTermMemory != null -> "✅ ${result.longTermMemory.take(120)}"
                    result.extractedContent != null -> "✅ ${result.extractedContent.take(120)}"
                    else -> "✅ Done"
                }
                OverlayDispatcher.show(
                    text = resultText,
                    priority = OverlayPriority.TASKS,
                    duration = 0L,
                    position = OverlayPosition.TOP
                )

                // Speak important action results for user awareness
                val voiceResult = when {
                    result.error != null -> "Error: ${result.error.take(60)}"
                    result.longTermMemory?.contains("mini-app", ignoreCase = true) == true ->
                        result.longTermMemory.take(80)
                    result.longTermMemory?.contains("Created", ignoreCase = true) == true ->
                        "Successfully created! ${result.longTermMemory.take(60)}"
                    result.longTermMemory?.contains("deleted", ignoreCase = true) == true ->
                        "Successfully deleted."
                    result.longTermMemory?.contains("Found", ignoreCase = true) == true ->
                        result.longTermMemory.take(80)
                    else -> null
                }
                voiceResult?.let { speechCoordinator.speakToUser(it) }

                // If an action fails, stop executing further actions in this step.
                if (result.error != null) {
                    Log.d(TAG,"  - 🛑 Action failed. Stopping current step's execution.")
                    break
                }
            }
            state.lastResult = actionResults

            // 5. RECORD: Save the complete step to the long-term history.
            history.addItem(
                AgentHistory(
                    modelOutput = agentOutput,
                    result = actionResults,
                    state = screenState,
                    metadata = null // You can add timing/token metadata here later
                )
            )

            // --- Check for Task Completion ---
            val doneAction = actionResults.find { it.isDone == true }
            if (doneAction != null) {
                Log.d(TAG,"✅ Agent finished the task.")

                // Hide glow when task is done
                visualFeedbackManager.hideTaskActiveGlow()

                // Speak detailed completion
                val completionText = when {
                    doneAction.longTermMemory?.contains("mini-app", ignoreCase = true) == true ->
                        doneAction.longTermMemory.take(100)
                    doneAction.longTermMemory?.contains("completed", ignoreCase = true) == true ->
                        doneAction.longTermMemory.take(100)
                    doneAction.success == false ->
                        "Task completed with issues: ${doneAction.error?.take(60) ?: "unknown error"}"
                    else ->
                        "Task completed successfully."
                }
                speechCoordinator.speakToUser(completionText)

                // Show final persistent status
                OverlayDispatcher.show(
                    text = "✅ Task Complete: $completionText",
                    priority = OverlayPriority.TASKS,
                    duration = 0L,
                    position = OverlayPosition.TOP
                )

                // Hide glow on completion
                visualFeedbackManager.hideTaskActiveGlow()

                state.stopped = true
            }

            state.nSteps++
            delay(1000) // A small, polite delay between steps.
        }

        // --- Loop Finished ---
        if (state.nSteps > maxSteps) {
            Log.d(TAG,"--- 🏁 Agent reached max steps. Stopping. ---")
            speechCoordinator.speakToUser("Agent reached maximum steps limit. Stopping execution.")

            // Hide glow when max steps reached
            visualFeedbackManager.hideTaskActiveGlow()
        } else {
            Log.d(TAG,"--- 🏁 Agent run finished. ---")
        }
    }
}