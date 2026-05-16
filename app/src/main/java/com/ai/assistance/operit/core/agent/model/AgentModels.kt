package com.ai.assistance.operit.core.agent.model

import com.ai.assistance.operit.core.agent.perception.ScreenAnalysis

data class AgentSettings(
    val maxSteps: Int = 150,
    val maxFailures: Int = 3,
    val showThoughts: Boolean = false
)

data class AgentState(
    var stopped: Boolean = false,
    var nSteps: Int = 0,
    var consecutiveFailures: Int = 0,
    var lastModelOutput: AgentOutput? = null,
    var lastResult: List<com.ai.assistance.operit.core.agent.actions.ActionResult>? = null,
    /** Cross-session memory context injected from TwGlobalBrain. */
    var memoryContext: String = ""
)

data class AgentOutput(
    val thinking: String? = null,
    val memory: String? = null,
    val nextGoal: String? = null,
    val action: List<com.ai.assistance.operit.core.agent.actions.Action> = emptyList()
)

data class AgentStepInfo(
    val currentStep: Int,
    val maxSteps: Int
)

data class AgentHistory(
    val modelOutput: AgentOutput,
    val result: List<com.ai.assistance.operit.core.agent.actions.ActionResult>,
    val state: ScreenAnalysis,
    val metadata: Any? = null
)

class AgentHistoryList<T> {
    private val items = mutableListOf<AgentHistory>()

    fun addItem(item: AgentHistory) {
        items.add(item)
    }

    fun getItems(): List<AgentHistory> = items.toList()

    fun clear() {
        items.clear()
    }

    fun size(): Int = items.size
}