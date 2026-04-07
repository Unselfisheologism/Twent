package com.ai.assistance.operit.voice.v2.llm

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.voice.v2.AgentOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Adapter that allows the AI Agent to use the same AI provider configuration as the Chat page.
 *
 * This replaces the hardcoded GeminiApi + ApiKeyManager approach with the proper
 * ModelConfigManager + AIServiceFactory system, ensuring the agent works with ANY
 * provider the user has configured (Gemini, OpenAI, Claude, DeepSeek, etc.).
 *
 * CRITICAL: The first message in the agent's message list is the SYSTEM PROMPT
 * (stored as MODEL role). We MUST send this as "user" role to OpenAI-compatible APIs,
 * because they expect the conversation to start with user/system messages, not assistant.
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
        } ?: run {
            Log.e(TAG, "All $maxRetry retry attempts failed. Returning null.")
            return null
        }

        Log.d(TAG, "Raw LLM response (${jsonString.length} chars): ${jsonString.take(500)}...")

        // Try to extract JSON from the response (handle markdown code blocks, extra text, etc.)
        val cleanedJson = extractJsonFromResponse(jsonString)
        Log.d(TAG, "Cleaned JSON for parsing (${cleanedJson.length} chars): ${cleanedJson.take(300)}...")

        return try {
            val result = jsonParser.decodeFromString<AgentOutput>(cleanedJson)
            Log.d(TAG, "Successfully parsed AgentOutput: nextGoal=${result.nextGoal}, actions=${result.action.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AgentOutput from response. Error: ${e.message}", e)
            Log.e(TAG, "Full response: $jsonString")
            Log.e(TAG, "Cleaned JSON attempt: $cleanedJson")
            null
        }
    }

    /**
     * Extracts JSON from LLM response, handling common formats like:
     * - Raw JSON
     * - JSON wrapped in markdown code blocks (```json ... ```)
     * - JSON with surrounding text/explanations
     */
    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()

        // Handle markdown code blocks
        val jsonBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Find the first { and last } to extract JSON object
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        // Return as-is if no JSON found
        return trimmed
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

        // CRITICAL FIX: Convert ALL messages to the chat API format.
        // The first message is the SYSTEM PROMPT (stored as MODEL role).
        // We MUST send it as "user" role for OpenAI-compatible APIs.
        // Subsequent MODEL messages become "assistant" (model's previous responses).
        val textMessages = messages.mapIndexed { index, message ->
            val text = message.parts.filterIsInstance<TextPart>().joinToString(separator = "\n") { it.text }
            if (text.isBlank()) return@mapIndexed null

            val role = if (index == 0 && message.role == MessageRole.MODEL) {
                "user" // First message is the system prompt - send as "user" for OpenAI compatibility
            } else {
                when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.MODEL -> "assistant"
                    MessageRole.TOOL -> "user"
                }
            }
            role to text
        }.filterNotNull()

        if (textMessages.isEmpty()) {
            throw IllegalArgumentException("No messages to send to LLM")
        }

        // Find the system prompt (first "user" message)
        val systemPrompt = textMessages.firstOrNull { it.first == "user" }?.second ?: ""

        // Find the last "user" message (this is the current user input / state message)
        val lastUserIndex = textMessages.indexOfLast { it.first == "user" }
        val latestUserMessage = textMessages[lastUserIndex].second

        // Build conversation history: ALL messages before the last "user" message
        // PLUS all messages after the last "user" message (context messages like corrective notes)
        val conversationHistory = mutableListOf<Pair<String, String>>()

        // Add all messages before the last user message (system prompt + any intermediate messages)
        for (i in 0 until lastUserIndex) {
            conversationHistory.add(textMessages[i])
        }

        // Add all messages after the last user message (context messages)
        for (i in lastUserIndex + 1 until textMessages.size) {
            conversationHistory.add(textMessages[i])
        }

        Log.d(TAG, "=== LLM API CALL ===")
        Log.d(TAG, "Provider: ${config.apiProviderType} / Model: ${config.modelName}")
        Log.d(TAG, "Total messages: ${messages.size} → ${textMessages.size} after filtering blanks")
        Log.d(TAG, "Conversation history: ${conversationHistory.size} messages")
        Log.d(TAG, "Latest user message length: ${latestUserMessage.length} chars")
        Log.d(TAG, "System prompt length: ${systemPrompt.length} chars (first 200: ${systemPrompt.take(200)})")

        val stream = aiService.sendMessage(
            context = context,
            message = latestUserMessage,
            chatHistory = conversationHistory,
            stream = false,
            onTokensUpdated = { _, _, _ -> },
            onNonFatalError = { error -> Log.e(TAG, "Non-fatal error from stream: $error") }
        )

        val fullResponse = StringBuilder()
        stream.collect { chunk ->
            fullResponse.append(chunk)
        }

        val result = fullResponse.toString()
        if (result.isBlank()) {
            throw IllegalStateException("AI service returned empty response")
        }

        Log.d(TAG, "=== LLM RESPONSE RECEIVED === (${result.length} chars)")
        Log.d(TAG, "Response preview: ${result.take(300)}...")
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
                Log.e(TAG, "API call attempt ${attempt + 1}/$times failed: ${e.message}", e)
                if (attempt == times - 1) {
                    Log.e(TAG, "All retry attempts exhausted. Last error: ${e.message}")
                    return null
                }
                Log.d(TAG, "Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return null
    }
}
