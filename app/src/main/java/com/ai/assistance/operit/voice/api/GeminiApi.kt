package com.ai.assistance.operit.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ai.assistance.operit.voice.utilities.ApiKeyManager
import com.ai.assistance.operit.voice.utilities.TextPart
import com.ai.assistance.operit.voice.utilities.ImagePart
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.ai.assistance.operit.voice.utilities.NetworkConnectivityManager
import com.ai.assistance.operit.voice.utilities.NetworkNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object GeminiApi {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-2.5-flash",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        try {
            val appCtx = context ?: run {
                Log.e("GeminiApi", "Context is null, cannot proceed")
                return null
            }
            val isOnline = NetworkConnectivityManager(appCtx).isNetworkAvailable()
            if (!isOnline) {
                Log.e("GeminiApi", "No internet connection detected.")
                NetworkNotifier.notifyOffline()
                return null
            }
            Log.d("GeminiApi", "Network check passed")
        } catch (e: Exception) {
            Log.e("GeminiApi", "Network check failed: ${e.message}", e)
            return null
        }

        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString(separator = "\n") { it.text } ?: "No text prompt found"

        Log.d("GeminiApi", "Starting API calls with model: $modelName, retries: $maxRetry")
        Log.d("GeminiApi", "Chat history has ${chat.size} messages")

        var attempts = 0
        while (attempts < maxRetry) {
            val currentApiKey = try {
                ApiKeyManager.getNextKey()
            } catch (e: IllegalStateException) {
                Log.e("GeminiApi", "No API keys configured: ${e.message}")
                return null
            }
            Log.d("GeminiApi", "API Request attempt ${attempts + 1}/$maxRetry, model: $modelName, key ends with: ...${currentApiKey.takeLast(4)}")

            val attemptStartTime = System.currentTimeMillis()
            val url = "$BASE_URL/$modelName:generateContent?key=$currentApiKey"

            try {
                val payload = buildDirectPayload(chat, modelName)
                Log.d("GeminiApi", "Payload built, size: ${payload.toString().length} chars")

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                Log.d("GeminiApi", "Sending HTTP request to: $BASE_URL/$modelName:generateContent?key=...${currentApiKey.takeLast(4)}")
                val requestStartTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val responseEndTime = System.currentTimeMillis()
                    val requestTime = responseEndTime - requestStartTime
                    val totalAttemptTime = responseEndTime - attemptStartTime
                    val responseBody = response.body?.string()

                    Log.d("GeminiApi", "HTTP ${response.code} in ${requestTime}ms")

                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        Log.e("GeminiApi", "API call failed with HTTP ${response.code}. Body: ${responseBody?.take(500)}")
                        throw Exception("API Error ${response.code}: $responseBody")
                    }

                    val parsedResponse = parseSuccessResponse(responseBody)
                    Log.d("GeminiApi", "Response parsed successfully, length: ${parsedResponse?.length ?: 0}")

                    val logEntry = createLogEntry(
                        attempt = attempts + 1,
                        modelName = modelName,
                        prompt = lastUserPrompt,
                        imagesCount = images.size,
                        payload = payload.toString(),
                        responseCode = response.code,
                        responseBody = responseBody,
                        responseTime = requestTime,
                        totalTime = totalAttemptTime
                    )
                    saveLogToFile(context ?: return parsedResponse, logEntry)

                    return parsedResponse
                }
            } catch (e: Exception) {
                val attemptEndTime = System.currentTimeMillis()
                val totalAttemptTime = attemptEndTime - attemptStartTime

                Log.e("GeminiApi", "API Error attempt ${attempts + 1}: ${e.message}", e)

                val logEntry = createLogEntry(
                    attempt = attempts + 1,
                    modelName = modelName,
                    prompt = lastUserPrompt,
                    imagesCount = images.size,
                    payload = "",
                    responseCode = null,
                    responseBody = null,
                    responseTime = 0,
                    totalTime = totalAttemptTime,
                    error = e.message
                )
                context?.let { saveLogToFile(it, logEntry) }

                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all $maxRetry retries.")
                    return null
                }
            }
        }
        return null
    }

    private fun buildDirectPayload(chat: List<Pair<String, List<Any>>>, modelName: String): JSONObject {
        val rootObject = JSONObject()
        val contentsArray = JSONArray()

        chat.forEach { (role, parts) ->
            val contentObject = JSONObject()
            contentObject.put("role", role.lowercase())

            val jsonParts = JSONArray()
            parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        val partObject = JSONObject().put("text", part.text)
                        jsonParts.put(partObject)
                    }
                    is ImagePart -> {
                        Log.w("GeminiApi", "ImagePart skipped in direct API call")
                    }
                }
            }

            if (jsonParts.length() > 0) {
                contentObject.put("parts", jsonParts)
                contentsArray.put(contentObject)
            }
        }

        rootObject.put("contents", contentsArray)
        rootObject.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        return rootObject
    }

    private fun parseSuccessResponse(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            if (!json.has("candidates")) {
                Log.w("GeminiApi", "Response has no candidates: $responseBody")
                return null
            }
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            if (!firstCandidate.has("content")) return null
            val content = firstCandidate.getJSONObject("content")
            if (!content.has("parts")) return null
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) return null
            parts.getJSONObject(0).getString("text")
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to parse response: $responseBody", e)
            responseBody
        }
    }

    private fun saveLogToFile(context: Context, logEntry: String) {
        try {
            val logDir = File(context.filesDir, "gemini_logs")
            if (!logDir.exists()) logDir.mkdirs()
            val logFile = File(logDir, "gemini_api_log.txt")
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to save log", e)
        }
    }

    private fun createLogEntry(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        payload: String,
        responseCode: Int?,
        responseBody: String?,
        responseTime: Long,
        totalTime: Long,
        error: String? = null
    ): String {
        return buildString {
            appendLine("=== GEMINI API DEBUG LOG ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}")
            appendLine("Attempt: $attempt")
            appendLine("Model: $modelName")
            appendLine("Images count: $imagesCount")
            appendLine("Prompt length: ${prompt.length}")
            appendLine("Prompt: $prompt")
            appendLine("Payload: $payload")
            appendLine("Response code: $responseCode")
            appendLine("Response time: ${responseTime}ms")
            appendLine("Total time: ${totalTime}ms")
            if (error != null) {
                appendLine("Error: $error")
            } else {
                appendLine("Response body: $responseBody")
            }
            appendLine("=== END LOG ===")
        }
    }
}
