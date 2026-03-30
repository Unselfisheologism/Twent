package com.ai.assistance.operit.core.agent

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.core.agent.actions.Action
import com.ai.assistance.operit.core.agent.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.fs.FileSystem
import com.ai.assistance.operit.core.agent.model.AgentSettings
import com.ai.assistance.operit.core.agent.model.AgentState
import com.ai.assistance.operit.core.agent.model.AgentHistoryList
import com.ai.assistance.operit.core.agent.model.AgentStepInfo
import com.ai.assistance.operit.core.agent.model.AgentOutput
import com.ai.assistance.operit.core.agent.perception.Perception
import com.ai.assistance.operit.core.agent.message.MessageManager
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayPriority
import com.ai.assistance.operit.overlay.OverlayPosition
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.R)
class Agent(
    private val settings: AgentSettings,
    private val messageManager: MessageManager,
    private val perception: Perception,
    private val llmApi: com.ai.assistance.operit.core.agent.llm.LlmApi,
    private val actionExecutor: ActionExecutor,
    private val fileSystem: FileSystem,
    private val context: Context
) {
    val state = AgentState()
    private val TAG = "Agent"

    val history = AgentHistoryList<Unit>()

    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        messageManager.addNewTask(initialTask)
        state.stopped = false
        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        while (!state.stopped && state.nSteps <= maxSteps) {
            Log.d(TAG, "\n--- Step ${state.nSteps}/$maxSteps ---")

            // 1. SENSE: Observe the current state of the screen
            Log.d(TAG, "👀 Sensing screen state...")
            val screenState = perception.analyze()

            // 2. THINK (Prepare Prompt)
            Log.d(TAG, "🧠 Preparing prompt...")
            messageManager.createStateMessage(
                modelOutput = state.lastModelOutput,
                result = state.lastResult,
                stepInfo = AgentStepInfo(state.nSteps, maxSteps),
                screenState = screenState
            )

            // 3. THINK (Get Decision) - Call LLM
            Log.d(TAG, "🤔 Asking LLM for next action...")
            val messages = messageManager.getMessages()
            val agentOutput = llmApi.generateAgentOutput(messages)

            if (agentOutput == null) {
                Log.d(TAG, "❌ LLM failed to return a valid action. Retrying...")
                state.consecutiveFailures++
                if (state.consecutiveFailures >= settings.maxFailures) {
                    Log.d(TAG, "❌ Agent failed too many times consecutively. Stopping.")
                    break
                }
                delay(1000)
                continue
            }

            state.consecutiveFailures = 0
            state.lastModelOutput = agentOutput
            Log.d(TAG, agentOutput.toString())
            Log.d(TAG, "🤖 LLM decided: ${agentOutput.nextGoal}")

            // Show thoughts as overlay (Blurr-style)
            val thoughtText = buildString {
                agentOutput.thinking?.let { if (it.isNotEmpty()) append("Thinking: ${it}\n") }
                agentOutput.memory?.let { if (it.isNotEmpty()) append("Memory: ${it}\n") }
                agentOutput.nextGoal?.let { if (it.isNotEmpty()) append("Next Goal: ${it}") }
            }.trim()

            if (thoughtText.isNotEmpty()) {
                OverlayDispatcher.show(
                    text = thoughtText,
                    priority = OverlayPriority.TASKS,
                    duration = 8000L,
                    position = OverlayPosition.TOP
                )
            }

            // 4. ACT: Execute the LLM's planned actions
            Log.d(TAG, "💪 Executing actions...")
            val actionResults = mutableListOf<com.ai.assistance.operit.core.agent.actions.ActionResult>()

            for (action in agentOutput.action) {
                val result = actionExecutor.execute(action, screenState, context, fileSystem)
                actionResults.add(result)
                Log.d(TAG, "  - Action '${action::class.simpleName}' executed. Result: ${result.longTermMemory ?: result.error ?: "OK"}")

                if (result.error != null) {
                    Log.d(TAG, "  - 🛑 Action failed. Stopping current step's execution.")
                    break
                }
            }

            state.lastResult = actionResults

            // 5. RECORD: Save to history
            history.addItem(
                com.ai.assistance.operit.core.agent.model.AgentHistory(
                    modelOutput = agentOutput,
                    result = actionResults,
                    state = screenState,
                    metadata = null
                )
            )

            // Check for task completion
            if (actionResults.any { it.isDone }) {
                Log.d(TAG, "✅ Agent finished the task.")
                state.stopped = true
            }

            state.nSteps++
            delay(1000)
        }

        if (state.nSteps > maxSteps) {
            Log.d(TAG, "--- 🏁 Agent reached max steps. Stopping. ---")
        } else {
            Log.d(TAG, "--- 🏁 Agent run finished. ---")
        }
    }
}