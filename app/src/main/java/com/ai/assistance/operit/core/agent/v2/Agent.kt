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
import com.ai.assistance.operit.api.chat.brain.TwGlobalBrain

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

    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        acquireWakeLock()

        // Inject cross-session memory from TwGlobalBrain
        val globalBrain = TwGlobalBrain.getInstance(context)
        val memoryContext = globalBrain.getOverlaySystemPromptAddition(initialTask)
        memoryManager.memoryContext = memoryContext
        state.memoryContext = memoryContext

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
                    val todoContent = try { fileSystem.getTodoContents() } catch (e: Exception) { "" }
                    if (todoContent.isNotBlank()) {
                        append("📋 Todo List:\n$todoContent")
                    }
                }.trim()



                val actionResults = mutableListOf<ActionResult>()
                for (action in agentOutput.action) {
                    val result = actionExecutor.execute(action, screenState, context, fileSystem)
                    actionResults.add(result)
                    Log.d(TAG, "  - Action executed: ${result.longTermMemory ?: result.error ?: "OK"}")
                    
                    // If action failed, stop executing further actions in this step
                    if (result.error != null) {
                        Log.d(TAG, "  - 🛑 Action failed. Stopping current step's execution.")
                        break
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
                
                // Detect if agent is stuck in a loop - repeatedly navigating back
                // If so, inject a system message to force forward progress
                if (state.nSteps >= 5) {
                    val recentItems = historyItems.takeLast(5)
                    val backCount = recentItems.count { 
                        it.actionResults?.contains("back") == true || 
                        it.actionResults?.contains("Navigated") == true 
                    }
                    val justDeleted = actionResults.any { 
                        it.longTermMemory?.contains("Deleted") == true || 
                        it.longTermMemory?.contains("deleted") == true 
                    }
                    
                    // If doing lots of back navigation after deletion, warn the agent
                    if (backCount >= 3 && justDeleted) {
                        memoryManager.addContextMessage(
                            LlmMessage(
                                role = MessageRole.SYSTEM,
                                content = "CRITICAL: You deleted files successfully. STOP navigating back and forward. Check your todo list and complete the NEXT step of the task (e.g., go to Recycle Bin, empty it)."
                            )
                        )
                    }
                }

                // Check for done action - trust the LLM's decision like Blurr does
                if (actionResults.any { it.isDone == true }) {
                    Log.d(TAG,"✅ Agent finished the task.")
                    speechCoordinator.speakToUser("Task completed successfully.")
                    state.stopped = true
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
