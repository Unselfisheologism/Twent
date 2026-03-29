package com.ai.assistance.operit.core.agent.message

import android.graphics.Bitmap
import android.util.Base64
import com.ai.assistance.operit.core.agent.actions.ActionResult
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.model.AgentOutput
import com.ai.assistance.operit.core.agent.model.AgentStepInfo
import com.ai.assistance.operit.core.agent.perception.ScreenAnalysis
import java.io.ByteArrayOutputStream

class MessageManager(
    private val systemPrompt: String
) {
    private val messages = mutableListOf<LlmMessage>()
    private var lastScreenshotBase64: String? = null

    init {
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
    }

    fun addNewTask(task: String) {
        messages.clear()
        messages.add(LlmMessage(MessageRole.SYSTEM, systemPrompt))
        messages.add(LlmMessage(MessageRole.USER, task))
    }

    private fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 80): String? {
        if (bitmap == null) return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val byteArray = stream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo,
        screenState: ScreenAnalysis
    ) {
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

        val screenshotBase64 = screenState.screenshot?.let { bitmapToBase64(it) }
        if (screenshotBase64 != null) {
            lastScreenshotBase64 = screenshotBase64
            contextBuilder.insert(0, "[SCREENSHOT: data:image/jpeg;base64,$screenshotBase64]\n\n")
        }

        messages.add(LlmMessage(MessageRole.USER, contextBuilder.toString()))
    }

    fun addContextMessage(message: LlmMessage) {
        messages.add(message)
    }

    fun getMessages(): List<LlmMessage> {
        return messages.toList()
    }

    fun getLastScreenshotBase64(): String? = lastScreenshotBase64

    fun clearMessages() {
        messages.clear()
    }
}