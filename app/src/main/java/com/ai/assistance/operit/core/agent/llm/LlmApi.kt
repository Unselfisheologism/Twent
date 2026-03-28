package com.ai.assistance.operit.core.agent.llm

import com.ai.assistance.operit.core.agent.model.AgentOutput

interface LlmApi {
    suspend fun generateAgentOutput(messages: List<LlmMessage>): AgentOutput?
}

data class LlmMessage(
    val role: MessageRole,
    val content: String
)

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}