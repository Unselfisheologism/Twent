package com.ai.assistance.operit.core.agent.v2

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.core.agent.v2.actions.Action
import com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.v2.actions.ActionResult
import com.ai.assistance.operit.core.agent.v2.llm.V2LlmApi
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.v2.fs.FileSystem
import com.ai.assistance.operit.core.agent.v2.message.MemoryManager
import com.ai.assistance.operit.core.agent.v2.perception.Perception
import com.ai.assistance.operit.core.agent.v2.perception.ScreenAnalysis
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayPriority
import com.ai.assistance.operit.overlay.OverlayPosition
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.R)
class Agent(
    private val settings: AgentSettings,
    private val memoryManager: MemoryManager,
    private val perception: Perception,
    private val llmApi: V2LlmApi,
    private val actionExecutor: ActionExecutor,
    private val fileSystem: FileSystem,
    private val context: Context
) {
    val state: AgentState = AgentState()
    private val TAG = "AgentV2"
    
    private val speechCoordinator = object {
        fun speakToUser(message: String) {
            Log.d("AgentV2", "Speak to user: $message")
        }
    }

    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        memoryManager.addNewTask(initialTask)
        state.stopped = false
        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        while (!state.stopped && state.nSteps <= maxSteps) {
            Log.d(TAG,"\n--- Step ${state.nSteps}/$maxSteps ---")

            val screenState = perception.analyze()

            memoryManager.createStateMessage(
                modelOutput = state.lastModelOutput,
                result = state.lastResult,
                stepInfo = AgentStepInfo(state.nSteps, maxSteps),
                screenState = screenState
            )

            val messages = memoryManager.getMessages()
            val agentOutput = llmApi.generateAgentOutput(messages)

            if (agentOutput == null) {
                Log.d(TAG,"❌ LLM failed to return a valid action. Retrying...")
                state.consecutiveFailures++
                memoryManager.addContextMessage(
                    LlmMessage(
                        role = MessageRole.SYSTEM,
                        content = "System Note: Your previous output was not valid JSON. Please ensure your response is correctly formatted."
                    )
                )
                if (state.consecutiveFailures >= settings.maxFailures) {
                    Log.d(TAG,"❌ Agent failed too many times consecutively. Stopping.")
                    speechCoordinator.speakToUser("Agent failed after multiple attempts. Stopping execution.")
                    break
                }
                delay(1000)
                continue
            }

            state.consecutiveFailures = 0
            state.lastModelOutput = agentOutput
            Log.d(TAG, "🤖 LLM decided: ${agentOutput.nextGoal}")

            val thoughtText = buildString {
                agentOutput.thinking?.let { if (it.isNotEmpty()) append("Thinking: ${it}\n") }
                agentOutput.memory?.let { if (it.isNotEmpty()) append("Memory: ${it}\n") }
                agentOutput.nextGoal?.let { if (it.isNotEmpty()) append("Next Goal: $it") }
            }.trim()

            if (thoughtText.isNotEmpty()) {
                OverlayDispatcher.show(
                    text = thoughtText,
                    priority = OverlayPriority.TASKS,
                    duration = 8000L,
                    position = OverlayPosition.TOP
                )
            }

            val actionResults = mutableListOf<ActionResult>()
            for (action in agentOutput.action) {
                val result = actionExecutor.execute(action, screenState, context, fileSystem)
                actionResults.add(result)
                Log.d(TAG, "  - Action executed: ${result.longTermMemory ?: result.error ?: "OK"}")

                if (result.error != null) {
                    Log.d(TAG,"  - 🛑 Action failed. Stopping.")
                    break
                }
            }
            state.lastResult = actionResults

            if (actionResults.any { it.isDone == true }) {
                Log.d(TAG,"✅ Agent finished the task.")
                speechCoordinator.speakToUser("Task completed successfully.")
                state.stopped = true
            }

            state.nSteps++
            delay(1000)
        }

        if (state.nSteps > maxSteps) {
            Log.d(TAG,"--- 🏁 Agent reached max steps. Stopping. ---")
            speechCoordinator.speakToUser("Agent reached maximum steps limit.")
        } else {
            Log.d(TAG,"--- 🏁 Agent run finished. ---")
        }
    }
}
