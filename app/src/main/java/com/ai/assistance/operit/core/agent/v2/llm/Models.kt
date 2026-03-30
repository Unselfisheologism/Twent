package com.ai.assistance.operit.core.agent.v2.llm

enum class MessageRole {
    USER,
    MODEL,
    TOOL
}

data class TextPart(val text: String)

data class GeminiMessage(
    val role: MessageRole,
    val parts: List<Any>,
    val toolCode: String? = null
)
