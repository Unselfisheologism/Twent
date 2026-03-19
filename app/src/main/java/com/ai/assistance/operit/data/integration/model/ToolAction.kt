package com.ai.assistance.operit.data.integration.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Available actions for each tool
 * Represents specific operations that can be performed with a tool
 */
@Serializable
data class ToolAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,  // Action name (e.g., "create_issue", "list_repos")
    val displayName: String,  // Display-friendly name
    val description: String = "",  // Action description
    val toolDefinitionId: String,  // Reference to the parent tool
    val toolkit: String,  // The toolkit this action belongs to
    val category: String = "",  // Action category for grouping
    val parameters: List<ActionParameter> = emptyList(),  // Action parameters
    val requiredPermissions: List<String> = emptyList(),  // Required OAuth scopes
    val isEnabled: Boolean = true,  // Whether the action is available
    val isExperimental: Boolean = false,  // Whether the action is experimental
    val metadata: Map<String, String> = emptyMap()  // Additional metadata
)

/**
 * Parameter definition for tool actions
 */
@Serializable
data class ActionParameter(
    val name: String,  // Parameter name
    val displayName: String,  // Display-friendly name
    val type: ParameterType,  // Parameter type
    val description: String = "",  // Parameter description
    val required: Boolean = false,  // Whether the parameter is required
    val defaultValue: String? = null,  // Default value
    val allowedValues: List<String>? = null,  // Allowed values for enum parameters
    val isSensitive: Boolean = false,  // Whether the parameter contains sensitive data
    val validationRules: List<ValidationRule> = emptyList()  // Validation rules
)

/**
 * Supported parameter types for actions
 */
@Serializable
enum class ParameterType {
    STRING,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    FILE,
    DATE,
    EMAIL,
    URL,
    ENUM
}

/**
 * Validation rules for parameters
 */
@Serializable
data class ValidationRule(
    val type: ValidationType,
    val value: String? = null,
    val message: String = ""
)

/**
 * Types of validation that can be applied
 */
@Serializable
enum class ValidationType {
    REQUIRED,
    MIN_LENGTH,
    MAX_LENGTH,
    MIN_VALUE,
    MAX_VALUE,
    PATTERN,
    EMAIL,
    URL,
    ENUM
}

/**
 * Result of executing a tool action
 */
@Serializable
data class ToolActionResult(
    val actionId: String,
    val success: Boolean,
    val result: String = "",  // Result data as JSON string
    val errorMessage: String? = null,
    val executionTime: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Action execution status
 */
@Serializable
enum class ActionExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT
}
