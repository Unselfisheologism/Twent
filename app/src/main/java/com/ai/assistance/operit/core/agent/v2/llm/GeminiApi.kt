package com.ai.assistance.operit.core.agent.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.FunctionType
import com.ai.assistance.operit.core.agent.llm.LlmApi
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.v2.AgentModels.AgentOutput
import com.ai.assistance.operit.core.agent.v2.AgentModels.AgentSettings
import com.ai.assistance.operit.core.agent.v2.AgentModels.ActionResult
import com.ai.assistance.operit.core.agent.v2.actions.Action
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OperitLlmApi(
    private val modelName: String,
    private val context: Context,
    private val maxRetry: Int = 3
) : LlmApi {

    companion object {
        private const val TAG = "OperitLlmApi"
    }

    private var cachedService: AIService? = null

    override suspend fun generateAgentOutput(messages: List<LlmMessage>): AgentOutput? {
        return withContext(Dispatchers.IO) {
            try {
                val aiService = getAiService() ?: run {
                    Log.e(TAG, "No AI service available")
                    return@withContext null
                }

                // Extract system message and user messages
                val systemMessage = messages.find { 
                    it.role == com.ai.assistance.operit.core.agent.llm.MessageRole.SYSTEM 
                }?.content ?: ""

                val userMessage = messages.findLast { 
                    it.role == com.ai.assistance.operit.core.agent.llm.MessageRole.USER 
                }?.content ?: ""

                // Build full prompt with system context and current task
                val fullPrompt = buildString {
                    append(systemMessage)
                    if (userMessage.isNotEmpty()) {
                        append("\n\n")
                        append("Current request: ")
                        append(userMessage)
                    }
                }

                Log.d(TAG, "Sending to LLM: ${fullPrompt.take(200)}...")

                // Call the AI service (non-streaming for agent)
                val responseStream = aiService.sendMessage(
                    context = context,
                    message = userMessage,
                    chatHistory = listOf(
                        "system" to systemMessage
                    ),
                    stream = false
                )

                // Collect the response
                val responseBuilder = StringBuilder()
                responseStream.collect { chunk ->
                    responseBuilder.append(chunk)
                }
                val response = responseBuilder.toString()

                Log.d(TAG, "LLM response: ${response.take(300)}...")

                // Parse JSON response to AgentOutput
                parseAgentOutput(response)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate output", e)
                null
            }
        }
    }

    private fun getAiService(): AIService? {
        // Try to get from FloatingChatService first
        try {
            val chatService = FloatingChatService.getInstance()
            val enhancedService = chatService?.getAiService()
            if (enhancedService != null) {
                // Get the CHAT function service synchronously
                // Note: In a real implementation, you'd want to cache this properly
                return null // We'll use EnhancedAIService directly instead
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get service from FloatingChatService: ${e.message}")
        }

        // Fallback: use EnhancedAIService directly
        return try {
            val enhanced = EnhancedAIService.getInstance(context)
            // Return a wrapper that delegates to EnhancedAIService
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get EnhancedAIService", e)
            null
        }
    }

    private fun parseAgentOutput(response: String): AgentOutput? {
        return try {
            // Try to parse as JSON
            val json = JSONObject(response)

            val thinking = json.optString("thinking", null).takeIf { it.isNotEmpty() }
            val evaluationPreviousGoal = json.optString("evaluationPreviousGoal", null).takeIf { it.isNotEmpty() }
            val memory = json.optString("memory", null).takeIf { it.isNotEmpty() }
            val nextGoal = json.optString("nextGoal", null).takeIf { it.isNotEmpty() }

            val actionArray = json.optJSONArray("action")
            val actions = mutableListOf<Action>()

            actionArray?.let { arr ->
                for (i in 0 until arr.length()) {
                    val actionObj = arr.getJSONObject(i)
                    val actionName = actionObj.keys().next()
                    val actionParams = actionObj.optJSONObject(actionName)

                    val action = parseAction(actionName, actionParams)
                    if (action != null) {
                        actions.add(action)
                    }
                }
            }

            AgentOutput(
                thinking = thinking,
                memory = memory,
                nextGoal = nextGoal,
                action = actions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse agent output", e)
            // Return a default output that will cause retry
            null
        }
    }

    private fun parseAction(name: String, params: JSONObject?): Action? {
        return try {
            when (name) {
                "tap", "click" -> {
                    val x = params?.optInt("x") ?: 0
                    val y = params?.optInt("y") ?: 0
                    if (x > 0 && y > 0) {
                        Action.TapAt(x, y)
                    } else {
                        val index = params?.optInt("index") ?: 0
                        Action.TapElement(index)
                    }
                }
                "long_press" -> {
                    val x = params?.optInt("x") ?: 0
                    val y = params?.optInt("y") ?: 0
                    val duration = params?.optInt("duration_ms") ?: 1500
                    if (x > 0 && y > 0) {
                        Action.LongPressAt(x, y, duration.toLong())
                    } else {
                        val index = params?.optInt("index") ?: 0
                        Action.LongPressElement(index)
                    }
                }
                "type_text", "type" -> {
                    val text = params?.optString("text") ?: ""
                    Action.InputText(text)
                }
                "swipe" -> {
                    val startX = params?.optInt("start_x") ?: 0
                    val startY = params?.optInt("start_y") ?: 0
                    val endX = params?.optInt("end_x") ?: 0
                    val endY = params?.optInt("end_y") ?: 0
                    val duration = params?.optInt("duration_ms") ?: 500
                    Action.Swipe(startX, startY, endX, endY, duration)
                }
                "swipe_up" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeUp(pixels)
                }
                "swipe_down" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeDown(pixels)
                }
                "swipe_left" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeLeft(pixels)
                }
                "swipe_right" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeRight(pixels)
                }
                "open_app" -> {
                    val packageName = params?.optString("package_name") 
                        ?: params?.optString("app_name") ?: ""
                    Action.OpenApp(packageName)
                }
                "back" -> Action.Back
                "home" -> Action.Home
                "switch_app" -> Action.SwitchApp
                "wait" -> Action.Wait
                "done" -> {
                    val success = params?.optBoolean("success") ?: true
                    val message = params?.optString("message") ?: "Task completed"
                    Action.Done(success, message)
                }
                "speak" -> {
                    val text = params?.optString("text") ?: ""
                    Action.Speak(text)
                }
                else -> {
                    Log.w(TAG, "Unknown action: $name")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse action: $name", e)
            null
        }
    }
}
