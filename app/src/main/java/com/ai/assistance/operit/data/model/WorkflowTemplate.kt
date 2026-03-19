package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * Workflow Template Model
 * Represents a reusable workflow template stored as JSON
 */
@Serializable
data class WorkflowTemplate(
    val templateId: String,
    val name: String,
    val description: String,
    val category: String,
    val icon: String,
    val workflow: Workflow
)

/**
 * Template category enum
 */
object TemplateCategory {
    const val AUTOMATION = "automation"
    const val INTEGRATION = "integration"
    const val LOGIC = "logic"
    const val DATA = "data"
    const val NOTIFICATION = "notification"
    
    fun getDisplayName(category: String): String {
        return when (category) {
            AUTOMATION -> "Automation"
            INTEGRATION -> "Integration"
            LOGIC -> "Logic"
            DATA -> "Data"
            NOTIFICATION -> "Notification"
            else -> "Other"
        }
    }
}
