package com.ai.assistance.operit.core.agent.v2.llm

import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.core.agent.v2.AgentOutput

interface V2LlmApi {
    suspend fun generateAgentOutput(messages: List<LlmMessage>): AgentOutput?
}
