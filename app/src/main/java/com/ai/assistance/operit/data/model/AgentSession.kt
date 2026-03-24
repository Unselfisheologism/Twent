package com.ai.assistance.operit.data.model

/**
 * Represents a running session of an AI agent.
 * Each session maps to a terminal session managed by TerminalManager.
 */
data class AgentSession(
    val id: String, // terminal session ID
    val agentId: String, // which agent type (e.g., "hermes-agent")
    val title: String, // user-visible name (e.g., "Hermes - Session 1")
    val createdAt: Long, // timestamp when session started
    val lastActivityAt: Long // timestamp of last message
)

/**
 * State of an agent session for UI display.
 */
data class AgentSessionState(
    val session: AgentSession,
    val isRunning: Boolean = true,
    val lastMessagePreview: String = ""
)