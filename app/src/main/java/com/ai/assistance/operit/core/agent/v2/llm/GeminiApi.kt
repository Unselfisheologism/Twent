package com.ai.assistance.operit.core.agent.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.v2.AgentOutput
import com.ai.assistance.operit.core.agent.v2.actions.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OperitLlmApi(
    private val modelName: String,
    private val context: Context,
    private val maxRetry: Int = 3
) : V2LlmApi {

    companion object {
        private const val TAG = "OperitLlmApi"
    }

    override suspend fun generateAgentOutput(messages: List<LlmMessage>): AgentOutput? {
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

    private fun parseAgentOutput(response: String): AgentOutput? {
        return try {
            val json = JSONObject(response)

            val thinking = json.optString("thinking", null).takeIf { it.isNotEmpty() }
            val memory = json.optString("memory", null).takeIf { it.isNotEmpty() }
            val nextGoal = json.optString("nextGoal", null).takeIf { it.isNotEmpty() }
            val evaluationPreviousGoal = json.optString("evaluationPreviousGoal", null).takeIf { it.isNotEmpty() }

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
                evaluationPreviousGoal = evaluationPreviousGoal,
                memory = memory,
                nextGoal = nextGoal,
                action = actions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse agent output", e)
            null
        }
    }

    private fun parseAction(name: String, params: JSONObject?): Action? {
        return try {
            when (name) {
                "tap", "click", "click_element" -> {
                    val x = params?.optInt("x") ?: 0
                    val y = params?.optInt("y") ?: 0
                    if (x > 0 && y > 0) {
                        Action.TapAt(x, y)
                    } else {
                        val index = params?.optInt("index") ?: 0
                        if (index > 0) {
                            Action.TapElement(index)
                        } else {
                            val text = params?.optString("text")
                            if (!text.isNullOrBlank()) {
                                Action.TapElement(text.hashCode().and(0x7FFFFFFF) % 10000)
                            } else {
                                Action.TapElement(0)
                            }
                        }
                    }
                }
                "type_text", "type" -> {
                    val text = params?.optString("text") ?: ""
                    Action.InputText(text)
                }
                "swipe_up" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeUp(pixels)
                }
                "swipe_down" -> {
                    val pixels = params?.optInt("pixels") ?: 500
                    Action.SwipeDown(pixels)
                }
                "open_app" -> {
                    val appName = params?.optString("app_name") ?: params?.optString("package_name") ?: ""
                    Action.OpenApp(appName)
                }
                "back" -> Action.Back
                "home" -> Action.Home
                "wait" -> Action.Wait
                "done" -> {
                    val success = params?.optBoolean("success") ?: true
                    val message = params?.optString("message") ?: "Task completed"
                    Action.Done(success, message, null)
                }
                "speak" -> {
                    val text = params?.optString("text") ?: ""
                    Action.Speak(text)
                }
                "swipe_left" -> Action.SwipeLeft(params?.optInt("pixels") ?: 500)
                "swipe_right" -> Action.SwipeRight(params?.optInt("pixels") ?: 500)
                "double_tap" -> Action.DoubleTapAt(params?.optInt("x") ?: 0, params?.optInt("y") ?: 0)
                "long_press" -> Action.LongPressAt(
                    params?.optInt("x") ?: 0,
                    params?.optInt("y") ?: 0,
                    params?.optLong("duration_ms")?.toLong() ?: 1500
                )
                "press_key" -> Action.PressKey(params?.optString("key") ?: "enter")
                "scroll_up" -> Action.ScrollUp(params?.optInt("pixels") ?: 500)
                "scroll_down" -> Action.ScrollDown(params?.optInt("pixels") ?: 500)
                "switch_app" -> Action.SwitchApp
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
