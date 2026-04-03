package com.ai.assistance.operit.voice.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.voice.utilities.ApiKeyManager
import com.ai.assistance.operit.voice.v2.AgentOutput
import com.ai.assistance.operit.voice.v2.llm.TextPart
import com.ai.assistance.operit.voice.v2.llm.MessageRole
import com.ai.assistance.operit.voice.v2.llm.GeminiMessage
import com.ai.assistance.operit.voice.v2.llm.TextPart
import com.ai.assistance.operit.voice.v2.llm.MessageRole
import com.ai.assistance.operit.voice.v2.llm.GeminiMessage
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.voice.v2.logging.TaskLogger
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A lightweight Gemini API client using direct OkHttp calls.
 * No proxy, no SDK - just direct API calls with key rotation.
 */
class GeminiApi(
    private val modelName: String,
    private val apiKeyManager: ApiKeyManager,
    private val context: Context,
    private val maxRetry: Int = 3
) {

    companion object {
        private const val TAG = "GeminiV2Api"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        val jsonString = retryWithBackoff(times = maxRetry) {
            performApiCall(messages)
        } ?: return null

        try {
            val input = jsonParser.encodeToString(messages)
            TaskLogger.log(context, input, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log task: ${e.message}")
        }

        return try {
            Log.d(TAG, "Parsing response: $jsonString")
            jsonParser.decodeFromString<AgentOutput>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AgentOutput: ${e.message}", e)
            null
        }
    }

    private suspend fun performApiCall(messages: List<GeminiMessage>): String {
        val apiKey = apiKeyManager.getNextKey()
        val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"

        val contents = messages.map { message ->
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.MODEL -> "model"
                MessageRole.TOOL -> "tool"
            }
            val parts = message.parts.filterIsInstance<TextPart>().map { part ->
                mapOf("text" to part.text)
            }
            mapOf("role" to role, "parts" to parts)
        }

        val requestBody = mapOf(
            "contents" to contents,
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json"
            )
        )

        val jsonBody = jsonParser.encodeToString(requestBody)

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string()
            if (!response.isSuccessful || responseBodyString.isNullOrBlank()) {
                throw IOException("API call failed with code: ${response.code}, body: $responseBodyString")
            }

            val jsonResponse = JSONObject(responseBodyString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                if (content != null) {
                    val parts = content.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text")
                    }
                }
            }
            throw IOException("Empty or invalid response from API")
        }
    }

    suspend fun generateGroundedContent(prompt: String): String? {
        val apiKey = apiKeyManager.getNextKey()
        val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"

        val jsonBody = """
        {
          "contents": [
            {
              "parts": [
                {"text": "$prompt"}
              ]
            }
          ],
          "tools": [
            {
              "google_search": {}
            }
          ]
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "Grounded API call failed: ${response.code}")
                    return null
                }
                JSONObject(responseBody)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Grounded API call exception", e)
            null
        }
    }
}

@Serializable
private data class ProxyRequestPart(val text: String)

@Serializable
private data class ProxyRequestMessage(val role: String, val parts: List<ProxyRequestPart>)

@Serializable
private data class ProxyRequestBody(val modelName: String, val messages: List<ProxyRequestMessage>)

class ContentBlockedException(message: String) : Exception(message)

private suspend fun <T> retryWithBackoff(
    times: Int,
    initialDelay: Long = 1000L,
    maxDelay: Long = 16000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T? {
    var currentDelay = initialDelay
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            Log.e("RetryUtil", "Attempt ${attempt + 1}/$times failed: ${e.message}", e)
            if (attempt == times - 1) {
                return null
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return null
}
