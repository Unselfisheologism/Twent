package com.ai.assistance.operit.voice.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.voice.v2.AgentOutput
import com.ai.assistance.operit.voice.v2.logging.TaskLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class V2LlmApi(
    private val modelName: String,
    private val apiKeyManager: ApiKeyManager,
    private val context: Context,
    private val maxRetry: Int = 3
) {
    companion object {
        private const val TAG = "V2LlmApi"
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var aiService: EnhancedAIService? = null

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            aiService = EnhancedAIService.getInstance(context)
        }
    }

    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        return try {
            val service = aiService ?: run {
                initialize()
                aiService
            }
            
            if (service == null) {
                Log.e(TAG, "AI Service not initialized")
                return null
            }

            val prompt = messagesToPrompt(messages)
            Log.d(TAG, "Sending prompt to LLM: ${prompt.take(200)}...")

            val stream = service.sendMessage(
                context = context,
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
            Log.d(TAG, "LLM response: ${responseText.take(200)}...")

            TaskLogger.log(context, prompt, responseText)

            try {
                jsonParser.decodeFromString<AgentOutput>(responseText)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON into AgentOutput. Error: ${e.message}", e)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating agent output", e)
            null
        }
    }

    private fun messagesToPrompt(messages: List<GeminiMessage>): String {
        return messages.joinToString("\n\n") { message ->
            val role = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.MODEL -> "Assistant"
                MessageRole.TOOL -> "Tool"
            }
            val content = message.parts.filterIsInstance<com.ai.assistance.operit.voice.utilities.TextPart>()
                .joinToString("\n") { it.text }
            "$role: $content"
        }
    }

    suspend fun generateGroundedContent(prompt: String): String? {
        return try {
            val service = aiService ?: run {
                initialize()
                aiService
            }
            
            if (service == null) {
                Log.e(TAG, "AI Service not initialized")
                return null
            }

            val stream = service.sendMessage(
                context = context,
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

            response.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating grounded content", e)
            null
        }
    }
}
