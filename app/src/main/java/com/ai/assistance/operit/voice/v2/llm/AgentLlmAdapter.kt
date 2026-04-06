package com.ai.assistance.operit.voice.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.voice.v2.AgentOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Adapter that allows the AI Agent to use the same AI provider configuration as the Chat page.
 * 
 * This replaces the hardcoded GeminiApi + ApiKeyManager approach with the proper
 * ModelConfigManager + AIServiceFactory system, ensuring the agent works with ANY
 * provider the user has configured (Gemini, OpenAI, Claude, DeepSeek, etc.).
 */
class AgentLlmAdapter(
    private val context: Context,
    private val maxRetry: Int = 10
) {
    companion object {
        private const val TAG = "AgentLlmAdapter"
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Generates structured agent output from the LLM.
     * Converts internal GeminiMessage format to chat API format and calls the user's configured provider.
     */
    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        val jsonString = retryWithBackoff(times = maxRetry) {
            performApiCall(messages)
        } ?: return null

        return try {
            Log.d(TAG, "Parsing response: ${jsonString.take(200)}...")
            jsonParser.decodeFromString<AgentOutput>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AgentOutput: ${e.message}", e)
            null
        }
    }

    private suspend fun performApiCall(messages: List<GeminiMessage>): String {
        val modelConfigManager = ModelConfigManager(context)
        modelConfigManager.initializeIfNeeded()

        val config = modelConfigManager.getModelConfig(ModelConfigManager.DEFAULT_CONFIG_ID)
            ?: throw IllegalStateException("No AI provider configured. Please go to Models & Parameters settings and configure your provider.")

        val apiPrefs = ApiPreferences.getInstance(context)
        val customHeadersJson = runBlocking { apiPrefs.getCustomHeaders() }

        val aiService = AIServiceFactory.createService(
            config = config,
            customHeadersJson = customHeadersJson,
            modelConfigManager = modelConfigManager,
            context = context
        )

        // Convert GeminiMessage format to chat API format
        // The first message is the system prompt, the rest are conversation history
        val textMessages = messages.mapNotNull { message ->
            val text = message.parts.filterIsInstance<TextPart>().joinToString(separator = "\n") { it.text }
            if (text.isBlank()) return@mapNotNull null
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.MODEL -> "assistant"
                MessageRole.TOOL -> "user" // Treat tool messages as user for compatibility
            }
            role to text
        }

        if (textMessages.isEmpty()) {
            throw IllegalArgumentException("No messages to send to LLM")
        }

        // The first message is the system prompt, the last is the current request
        val systemPrompt = textMessages.firstOrNull { it.first == "user" }?.second ?: ""
        val lastUserIndex = textMessages.indexOfLast { it.first == "user" }
        val latestUserMessage = textMessages[lastUserIndex].second

        // Build conversation history (everything between system prompt and latest user message)
        val conversationHistory = mutableListOf<Pair<String, String>>()
        if (systemPrompt.isNotBlank()) {
            conversationHistory.add("user" to systemPrompt)
        }
        for (i in 1 until lastUserIndex) {
            conversationHistory.add(textMessages[i])
        }

        Log.d(TAG, "Calling AI service: ${config.apiProviderType} / ${config.modelName}")
        Log.d(TAG, "History: ${conversationHistory.size} messages, current message: ${latestUserMessage.length} chars")

        val stream = aiService.sendMessage(
            context = context,
            message = latestUserMessage,
            chatHistory = conversationHistory,
            stream = false,
            onTokensUpdated = { _, _, _ -> },
            onNonFatalError = { error -> Log.e(TAG, "Non-fatal error: $error") }
        )

        val fullResponse = StringBuilder()
        stream.collect { chunk ->
            fullResponse.append(chunk)
        }

        val result = fullResponse.toString()
        if (result.isBlank()) {
            throw IllegalStateException("AI service returned empty response")
        }

        Log.d(TAG, "AI service responded successfully (${result.length} chars)")
        return result
    }

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
                Log.e(TAG, "Attempt ${attempt + 1}/$times failed: ${e.message}", e)
                if (attempt == times - 1) {
                    return null
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return null
    }
}
