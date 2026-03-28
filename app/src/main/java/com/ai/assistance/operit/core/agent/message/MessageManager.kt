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

    init {
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
    }

    fun addNewTask(task: String) {
        // Clear previous task messages (keep system prompt)
        messages.clear()
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
        messages.add(LlmMessage(MessageRole.USER, task))
    }

    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo,
        screenState: ScreenAnalysis
    ) {
        // Build context about the previous action result
        val contextBuilder = StringBuilder()
        contextBuilder.append("Current Screen:\n${screenState.uiRepresentation}\n\n")
        
        contextBuilder.append("Current Activity: ${screenState.activityName}\n")
        
        if (screenState.isKeyboardOpen) {
            contextBuilder.append("Keyboard is open\n")
        }
        
        if (screenState.scrollUp > 0) {
            contextBuilder.append("Can scroll up ${screenState.scrollUp} pixels\n")
        }
        if (screenState.scrollDown > 0) {
            contextBuilder.append("Can scroll down ${screenState.scrollDown} pixels\n")
        }

        // Add previous action result
        if (modelOutput != null && result != null) {
            contextBuilder.append("\nPrevious Action Results:\n")
            result.forEach { actionResult ->
                if (actionResult.error != null) {
                    contextBuilder.append("- Error: ${actionResult.error}\n")
                } else if (actionResult.longTermMemory != null) {
                    contextBuilder.append("- ${actionResult.longTermMemory}\n")
                }
            }
        }

        contextBuilder.append("\nStep ${stepInfo.currentStep}/${stepInfo.maxSteps}")

        // Add as user message
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