package com.ai.assistance.operit.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.voice.MyApplication
import com.ai.assistance.operit.voice.utilities.TextPart
import com.ai.assistance.operit.voice.utilities.ImagePart
import com.ai.assistance.operit.voice.utilities.NetworkConnectivityManager
import com.ai.assistance.operit.voice.utilities.NetworkNotifier
import kotlinx.coroutines.delay

object LlmProvider {
    private var aiService: EnhancedAIService? = null

    private suspend fun getAiService(): EnhancedAIService? {
        if (aiService == null) {
            try {
                aiService = EnhancedAIService.getInstance(MyApplication.appContext)
            } catch (e: Exception) {
                Log.e("LlmProvider", "Failed to get AI service", e)
            }
        }
        return aiService
    }

    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "default",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        try {
            val appCtx = context ?: MyApplication.appContext
            val isOnline = NetworkConnectivityManager(appCtx).isNetworkAvailable()
            if (!isOnline) {
                Log.e("LlmProvider", "No internet connection. Skipping generateContent call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e("LlmProvider", "Network check failed, assuming offline. ${e.message}")
            return null
        }

        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString("\n") { it.text } ?: "No text prompt found"

        var attempts = 0
        while (attempts < maxRetry) {
            Log.d("LlmProvider", "=== LLM API REQUEST (Attempt ${attempts + 1}) ===")

            try {
                val service = getAiService()
                if (service == null) {
                    Log.e("LlmProvider", "AI Service not available")
                    return null
                }

                val prompt = buildPrompt(chat)
                Log.d("LlmProvider", "Prompt: ${prompt.take(200)}...")

                val stream = service.sendMessage(
                    context = MyApplication.appContext,
                    message = prompt,
                    chatHistory = emptyList(),
                    modelParameters = emptyList(),
                    enableThinking = false,
                    stream = false
                )

                val response = StringBuilder()
                stream.collect { chunk ->
                    response.append(chunk)
                }

                val responseText = response.toString()
                if (responseText.isNotBlank()) {
                    Log.d("LlmProvider", "=== LLM API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("LlmProvider", "Response: ${responseText.take(200)}...")
                    return responseText
                }

                throw Exception("Empty response from LLM")
            } catch (e: Exception) {
                Log.e("LlmProvider", "=== LLM API ERROR (Attempt ${attempts + 1}) ===", e)
                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("LlmProvider", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("LlmProvider", "Request failed after all ${maxRetry} retries.")
                    return null
                }
            }
        }
        return null
    }

    private fun buildPrompt(chat: List<Pair<String, List<Any>>>): String {
        return chat.joinToString("\n\n") { (role, parts) ->
            val content = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
            "$role: $content"
        }
    }
}
