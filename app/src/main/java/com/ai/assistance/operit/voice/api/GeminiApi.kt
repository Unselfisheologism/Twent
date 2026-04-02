package com.ai.assistance.operit.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.voice.MyApplication
import com.ai.assistance.operit.voice.utilities.ApiKeyManager
import com.ai.assistance.operit.voice.utilities.TextPart
import com.ai.assistance.operit.voice.utilities.ImagePart
import com.ai.assistance.operit.voice.utilities.NetworkConnectivityManager
import com.ai.assistance.operit.voice.utilities.NetworkNotifier
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object GeminiApi {
    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var aiService: EnhancedAIService? = null

    private suspend fun getAiService(): EnhancedAIService? {
        if (aiService == null) {
            try {
                aiService = EnhancedAIService.getInstance(MyApplication.appContext)
            } catch (e: Exception) {
                Log.e("GeminiApi", "Failed to get AI service", e)
            }
        }
        return aiService
    }

    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-2.5-flash",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        try {
            val appCtx = context ?: MyApplication.appContext
            val isOnline = NetworkConnectivityManager(appCtx).isNetworkAvailable()
            if (!isOnline) {
                Log.e("GeminiApi", "No internet connection. Skipping generateContent call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e("GeminiApi", "Network check failed, assuming offline. ${e.message}")
            return null
        }

        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString("\n") { it.text } ?: "No text prompt found"

        var attempts = 0
        while (attempts < maxRetry) {
            Log.d("GeminiApi", "=== LLM API REQUEST (Attempt ${attempts + 1}) ===")

            try {
                val service = getAiService()
                if (service == null) {
                    Log.e("GeminiApi", "AI Service not available")
                    return null
                }

                val prompt = buildPrompt(chat)
                Log.d("GeminiApi", "Prompt: ${prompt.take(200)}...")

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
                    Log.d("GeminiApi", "=== LLM API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("GeminiApi", "Response: ${responseText.take(200)}...")
                    return responseText
                }

                throw Exception("Empty response from LLM")
            } catch (e: Exception) {
                Log.e("GeminiApi", "=== LLM API ERROR (Attempt ${attempts + 1}) ===", e)
                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all ${maxRetry} retries.")
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

    private fun buildPayload(chat: List<Pair<String, List<Any>>>, modelName: String): JSONObject {
        val rootObject = JSONObject()
        rootObject.put("modelName", modelName)

        val messagesArray = JSONArray()
        chat.forEach { (role, parts) ->
            val messageObject = JSONObject()
            messageObject.put("role", role.lowercase())

            val jsonParts = JSONArray()
            parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        val partObject = JSONObject().put("text", part.text)
                        jsonParts.put(partObject)
                    }
                    is ImagePart -> {
                        Log.w("GeminiApi", "ImagePart found but skipped.")
                    }
                }
            }

            if (jsonParts.length() > 0) {
                messageObject.put("parts", jsonParts)
                messagesArray.put(messageObject)
            }
        }

        rootObject.put("messages", messagesArray)
        return rootObject
    }
}
