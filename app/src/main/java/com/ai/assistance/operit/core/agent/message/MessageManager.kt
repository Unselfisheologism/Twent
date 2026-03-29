package com.ai.assistance.operit.core.agent.message

import com.ai.assistance.operit.core.agent.actions.ActionResult
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.model.AgentOutput
import com.ai.assistance.operit.core.agent.model.AgentStepInfo
import com.ai.assistance.operit.core.agent.perception.ScreenAnalysis

class MessageManager(
    private val systemPrompt: String
) {
    private val messages = mutableListOf<LlmMessage>()
    private var currentTask: String = ""

    init {
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
    }

    fun addNewTask(task: String) {
        messages.clear()
        currentTask = task
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
    }

    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo,
        screenState: ScreenAnalysis
    ) {
        val contextBuilder = StringBuilder()
        
        contextBuilder.append("═══ SCREEN CONTEXT ═══\n")
        contextBuilder.append("Activity: ${screenState.activityName}\n")
        
        if (screenState.isKeyboardOpen) {
            contextBuilder.append("Keyboard: OPEN\n")
        } else {
            contextBuilder.append("Keyboard: CLOSED\n")
        }
        
        if (screenState.scrollUp > 0) {
            contextBuilder.append("Can scroll UP: ${screenState.scrollUp}px\n")
        }
        if (screenState.scrollDown > 0) {
            contextBuilder.append("Can scroll DOWN: ${screenState.scrollDown}px\n")
        }
        
        contextBuilder.append("\n═══ UI ELEMENTS ═══\n")
        contextBuilder.append("${screenState.uiRepresentation}\n")
        
        if (modelOutput != null && result != null) {
            contextBuilder.append("\n═══ PREVIOUS RESULT ═══\n")
            result.forEach { actionResult ->
                if (actionResult.error != null) {
                    contextBuilder.append("ERROR: ${actionResult.error}\n")
                } else if (actionResult.longTermMemory != null) {
                    contextBuilder.append("RESULT: ${actionResult.longTermMemory}\n")
                }
            }
        }
        
        contextBuilder.append("\n═══ STEP ${stepInfo.currentStep}/${stepInfo.maxSteps} ═══")
        
        messages.add(LlmMessage(MessageRole.USER, contextBuilder.toString()))
    }

    fun addContextMessage(message: LlmMessage) {
        messages.add(message)
    }

    fun getMessages(): List<LlmMessage> {
        return messages.toList()
    }

    fun clearMessages() {
        messages.clear()
    }
}