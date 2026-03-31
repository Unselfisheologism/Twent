package com.ai.assistance.operit.core.agent.v2

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.core.agent.v2.actions.Action
import com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.v2.ActionResult
import com.ai.assistance.operit.core.agent.v2.llm.V2LlmApi
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.v2.fs.FileSystem
import com.ai.assistance.operit.core.agent.v2.message.HistoryItem
import com.ai.assistance.operit.core.agent.v2.message.MemoryManager
import com.ai.assistance.operit.core.agent.v2.perception.Perception
import com.ai.assistance.operit.core.agent.v2.perception.ScreenAnalysis
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayPriority
import com.ai.assistance.operit.overlay.OverlayPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val historyItems = mutableListOf<HistoryItem>()
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val speechCoordinator = object {
        fun speakToUser(message: String) {
            Log.d("AgentV2", "Speak to user: $message")
            try {
                val voiceService = com.ai.assistance.operit.api.voice.VoiceServiceFactory.getInstance(context)
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        voiceService.speak(message)
                    } catch (e: Exception) {
                        Log.e("AgentV2", "TTS failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("AgentV2", "TTS failed", e)
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Operit:AgentWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            Log.d(TAG, "✅ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✅ Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    private fun shouldAllowDone(state: AgentState, actionResults: List<ActionResult>): Boolean {
        if (state.nSteps < 1) {
            return false
        }
        
        val lastResult = actionResults.firstOrNull { it.isDone == true }
        if (lastResult?.longTermMemory?.contains("success") == true || lastResult?.success == true) {
            return true
        }
        
        if (historyItems.isEmpty()) {
            return state.nSteps >= 3
        }
        
        val recentItems = historyItems.takeLast(5)
        val hasProgress = recentItems.any { item ->
            item.actionResults?.let { res ->
                res.contains("Screen updated") ||
                res.contains("Opened app") ||
                res.contains("Scrolled") ||
                res.contains("Input") ||
                res.contains("Deleted") ||
                res.contains("Selected") ||
                res.contains("Navigated") ||
                res.contains("Pressed") ||
                res.contains("success") ||
                res.contains("Done")
            }
        }
        
        if (hasProgress) {
            return true
        }
        
        if (state.nSteps >= 5) {
            return true
        }
        
        return false
    }

    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        acquireWakeLock()
        
        memoryManager.addNewTask(initialTask)
        state.stopped = false
        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        try {
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
                    // More descriptive error message
                    val retryMsg = if (state.consecutiveFailures < 3) {
                        "System Note: Your previous output was not valid JSON. Check that your response starts with { and ends with }. Do NOT include any text before or after the JSON."
                    } else {
                        "CRITICAL: Your response must be ONLY valid JSON. No text before { or after }. Example: {\"thinking\": \"...\", \"action\": [{\"tap_element\": {\"element_id\": 1}}]}"
                    }
                    memoryManager.addContextMessage(
                        LlmMessage(
                            role = MessageRole.SYSTEM,
                            content = retryMsg
                        )
                    )
                    if (state.consecutiveFailures >= settings.maxFailures) {
                        Log.d(TAG,"❌ Agent failed too many times consecutively. Stopping.")
                        speechCoordinator.speakToUser("Agent failed after multiple attempts. Stopping execution.")
                        break
                    }
                    delay(500)
                    continue
                }

                state.consecutiveFailures = 0
                state.lastModelOutput = agentOutput
                Log.d(TAG, "🤖 LLM decided: ${agentOutput.nextGoal}, actions: ${agentOutput.action.size}")
                
                // Handle empty actions list - this is also a failure
                if (agentOutput.action.isEmpty()) {
                    Log.w(TAG, "⚠️ LLM returned empty actions list!")
                    memoryManager.addContextMessage(
                        LlmMessage(
                            role = MessageRole.SYSTEM,
                            content = "System Note: Your 'action' list cannot be empty. You must always include at least one action."
                        )
                    )
                    state.consecutiveFailures++
                    delay(500)
                    continue
                }

                val thoughtText = buildString {
                    agentOutput.thinking?.let { if (it.isNotEmpty()) append("Thinking: ${it}\n") }
                    agentOutput.memory?.let { if (it.isNotEmpty()) append("Memory: ${it}\n") }
                    agentOutput.nextGoal?.let { if (it.isNotEmpty()) append("Next Goal: $it") }
                }.trim()

                if (thoughtText.isNotEmpty()) {
                    OverlayDispatcher.show(
                        text = thoughtText,
                        priority = OverlayPriority.TASKS,
                        duration = 5000L,
                        position = OverlayPosition.TOP
                    )
                }

                val actionResults = mutableListOf<ActionResult>()
                var actionFailed = false
                for (action in agentOutput.action) {
                    val result = actionExecutor.execute(action, screenState, context, fileSystem)
                    actionResults.add(result)
                    Log.d(TAG, "  - Action executed: ${result.longTermMemory ?: result.error ?: "OK"}")
                    
                    // If action failed, mark for retry but continue (don't stop the loop)
                    if (result.error != null) {
                        actionFailed = true
                        Log.d(TAG, "  - ⚠️ Action failed: ${result.error}, will retry in next step")
                    }
                }
                state.lastResult = actionResults

                // Record history for better tracking
                historyItems.add(
                    HistoryItem(
                        stepNumber = state.nSteps,
                        evaluation = agentOutput.evaluationPreviousGoal,
                        memory = agentOutput.memory,
                        nextGoal = agentOutput.nextGoal,
                        actionResults = actionResults.joinToString("\n") { 
                            it.longTermMemory ?: it.error ?: "OK" 
                        }
                    )
                )

                // Check for done action with proper validation
                if (actionResults.any { it.isDone == true }) {
                    if (!shouldAllowDone(state, actionResults)) {
                        Log.d(TAG,"⚠️ Premature done at step ${state.nSteps} - ignoring, continuing...")
                        // Add warning message for LLM
                        memoryManager.addContextMessage(
                            LlmMessage(
                                role = MessageRole.SYSTEM,
                                content = "System Note: Do NOT use 'done' action yet. The task is not complete. Continue working on the task."
                            )
                        )
                    } else {
                        Log.d(TAG,"✅ Agent finished the task.")
                        speechCoordinator.speakToUser("Task completed successfully.")
                        state.stopped = true
                    }
                }

                state.nSteps++
                // Reduced delay for faster execution - 300ms is enough for screen to update
                delay(300)
            }

            if (state.nSteps > maxSteps) {
                Log.d(TAG,"--- 🏁 Agent reached max steps. Stopping. ---")
                speechCoordinator.speakToUser("Agent reached maximum steps limit.")
            } else {
                Log.d(TAG,"--- 🏁 Agent run finished. ---")
            }
        } finally {
            releaseWakeLock()
        }
    }
}
