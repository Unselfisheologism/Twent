package com.ai.assistance.operit.voice.utilities

import android.graphics.Bitmap
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

sealed class MessagePart {
    abstract val text: String?
    abstract val bitmap: Bitmap?
}

data class TextPart(override val text: String, override val bitmap: Bitmap? = null) : MessagePart() {
    companion object {
        operator fun invoke(text: String) = TextPart(text)
    }
}

data class ImagePart(override val bitmap: Bitmap, override val text: String? = null) : MessagePart() {
    companion object {
        operator fun invoke(bitmap: Bitmap) = ImagePart(bitmap)
    }
}

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

    imageBefore?.let {
        messageParts.add(ImagePart(it))
    }

    imageAfter?.let {
        messageParts.add(ImagePart(it))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

suspend fun getReasoningModelApiResponse(
    chat: List<Pair<String, List<Any>>>,
): String? {
    return null
}
