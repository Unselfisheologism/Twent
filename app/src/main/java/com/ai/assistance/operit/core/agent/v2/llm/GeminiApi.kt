package com.ai.assistance.operit.core.agent.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.core.agent.llm.LlmApi
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import kotlinx.coroutines.Dispatchers
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

    override suspend fun generateAgentOutput(messages: List<LlmMessage>): com.ai.assistance.operit.core.agent.model.AgentOutput? {
        return withContext(Dispatchers.IO) {
            try {
                val enhanced = EnhancedAIService.getInstance(context)
                val aiService = enhanced.getAIServiceForFunction(FunctionType.CHAT)

                val systemMsg = messages.find { it.role == MessageRole.SYSTEM }?.content ?: ""
                val userMsg = messages.findLast { it.role == MessageRole.USER }?.content ?: ""

                val prompt = buildString {
                    append(systemMsg)
                    if (userMsg.isNotEmpty()) {
                        append("\n\nCurrent request: ")
                        append(userMsg)
                    }
                }

                Log.d(TAG, "Sending to LLM: ${prompt.take(200)}...")

                val responseStream = aiService.sendMessage(
                    context = context,
                    message = userMsg,
                    chatHistory = listOf("system" to systemMsg),
                    stream = false
                )

                val responseBuilder = StringBuilder()
                responseStream.collect { chunk: String ->
                    responseBuilder.append(chunk)
                }
                val response = responseBuilder.toString()

                Log.d(TAG, "LLM response: ${response.take(300)}...")
                parseAgentOutput(response)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate output", e)
                null
            }
        }
    }

    private fun parseAgentOutput(response: String): com.ai.assistance.operit.core.agent.model.AgentOutput? {
        return try {
            val json = JSONObject(response)

            val thinking = json.optString("thinking", null).takeIf { it.isNotEmpty() }
            val memory = json.optString("memory", null).takeIf { it.isNotEmpty() }
            val nextGoal = json.optString("nextGoal", null).takeIf { it.isNotEmpty() }

            val actionArray = json.optJSONArray("action")
            val actions = mutableListOf<com.ai.assistance.operit.core.agent.actions.Action>()

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

            com.ai.assistance.operit.core.agent.model.AgentOutput(
                thinking = thinking,
                evaluationPreviousGoal = null,
                memory = memory,
                nextGoal = nextGoal,
                action = actions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse agent output", e)
            null
        }
    }

    private fun parseAction(name: String, params: JSONObject?): com.ai.assistance.operit.core.agent.actions.Action? {
        return try {
            when (name) {
                "tap", "click" -> {
                    val x = params?.optInt("x") ?: 0
                    val y = params?.optInt("y") ?: 0
                    if (x > 0 && y > 0) {
                        com.ai.assistance.operit.core.agent.actions.Action.TapAt(x, y)
                    } else {
                        val index = params?.optInt("index") ?: 0
                        com.ai.assistance.operit.core.agent.actions.Action.TapElement(index)
                    }
                }
                "type_text", "type" -> {
                    val text = params?.optString("text") ?: ""
                    com.ai.assistance.operit.core.agent.actions.Action.InputText(text)
                }
                "swipe_up" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    com.ai.assistance.operit.core.agent.actions.Action.SwipeUp(pixels)
                }
                "swipe_down" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    com.ai.assistance.operit.core.agent.actions.Action.SwipeDown(pixels)
                }
                "open_app" -> {
                    val packageName = params?.optString("package_name") ?: params?.optString("app_name") ?: ""
                    com.ai.assistance.operit.core.agent.actions.Action.OpenApp(packageName)
                }
                "back" -> com.ai.assistance.operit.core.agent.actions.Action.Back
                "home" -> com.ai.assistance.operit.core.agent.actions.Action.Home
                "wait" -> com.ai.assistance.operit.core.agent.actions.Action.Wait
                "done" -> {
                    val success = params?.optBoolean("success") ?: true
                    val message = params?.optString("message") ?: "Task completed"
                    com.ai.assistance.operit.core.agent.actions.Action.Done(message, success)
                }
                "speak" -> {
                    val text = params?.optString("text") ?: ""
                    com.ai.assistance.operit.core.agent.actions.Action.Speak(text)
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
