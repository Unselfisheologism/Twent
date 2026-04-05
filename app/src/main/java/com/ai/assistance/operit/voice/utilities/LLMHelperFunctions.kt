package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.runBlocking

fun addResponse(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBitmap: Bitmap? = null
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()
    val messageParts = mutableListOf<Any>()
    messageParts.add(TextPart(prompt))
    if (imageBitmap != null) {
        messageParts.add(ImagePart(imageBitmap))
    }
    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

fun addResponsePrePost(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBefore: Bitmap? = null,
    imageAfter: Bitmap? = null
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()
    val messageParts = mutableListOf<Any>()
    messageParts.add(TextPart(prompt))
    imageBefore?.let { messageParts.add(ImagePart(it)) }
    imageAfter?.let { messageParts.add(ImagePart(it)) }
    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

suspend fun getReasoningModelApiResponse(
    chat: List<Pair<String, List<Any>>>,
    context: Context? = null,
): String? {
    if (context == null) {
        Log.e("ConvAgent_LLM", "Context is null, cannot call LLM")
        return null
    }

    try {
        val modelConfigManager = ModelConfigManager(context)
        modelConfigManager.initializeIfNeeded()

        val config = modelConfigManager.getModelConfig(ModelConfigManager.DEFAULT_CONFIG_ID)
            ?: run {
                Log.e("ConvAgent_LLM", "Failed to load model config")
                return null
            }

        val apiPrefs = ApiPreferences.getInstance(context)
        val customHeadersJson = runBlocking { apiPrefs.getCustomHeaders() }

        val aiService = AIServiceFactory.createService(
            config = config,
            customHeadersJson = customHeadersJson,
            modelConfigManager = modelConfigManager,
            context = context
        )

        // Convert the internal chat format to the chat API format
        // The first "user" message is the system prompt
        // The last "user" message is the current user input
        // Everything in between is conversation history
        val textMessages = chat.mapNotNull { (role, parts) ->
            val text = parts.filterIsInstance<TextPart>().joinToString(separator = "\n") { it.text }
            if (text.isBlank()) return@mapNotNull null
            val convertedRole = when (role.lowercase()) {
                "user" -> "user"
                "model", "assistant" -> "assistant"
                else -> "user"
            }
            convertedRole to text
        }

        if (textMessages.isEmpty()) {
            Log.e("ConvAgent_LLM", "No messages in chat history")
            return null
        }

        // Separate system prompt (first user message), conversation history, and latest user message
        val systemPrompt = textMessages.firstOrNull { it.first == "user" }?.second ?: ""
        
        // Find the latest user message (last user message in the list)
        val lastUserIndex = textMessages.indexOfLast { it.first == "user" }
        val latestUserMessage = textMessages[lastUserIndex].second
        
        // Build conversation history: system prompt + all messages except the latest user message
        val conversationHistory = mutableListOf<Pair<String, String>>()
        
        // Add system prompt as the first user message
        if (systemPrompt.isNotBlank()) {
            conversationHistory.add("user" to systemPrompt)
        }
        
        // Add all intermediate messages (between system prompt and latest user message)
        for (i in 1 until lastUserIndex) {
            conversationHistory.add(textMessages[i])
        }

        Log.d("ConvAgent_LLM", "Calling AI service: ${config.apiProviderType} / ${config.modelName}")
        Log.d("ConvAgent_LLM", "History: ${conversationHistory.size} messages, user message: ${latestUserMessage.length} chars")

        val stream = aiService.sendMessage(
            context = context,
            message = latestUserMessage,
            chatHistory = conversationHistory,
            stream = false,
            onTokensUpdated = { _, _, _ -> },
            onNonFatalError = { error -> Log.e("ConvAgent_LLM", "Non-fatal error: $error") }
        )

        val fullResponse = StringBuilder()
        stream.collect { chunk ->
            fullResponse.append(chunk)
        }

        val result = fullResponse.toString()
        if (result.isBlank()) {
            Log.e("ConvAgent_LLM", "AI service returned empty response")
            return null
        }

        Log.d("ConvAgent_LLM", "AI service responded successfully (${result.length} chars)")
        return result

    } catch (e: Exception) {
        Log.e("ConvAgent_LLM", "LLM call failed: ${e.message}", e)
        return null
    }
}
