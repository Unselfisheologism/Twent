package com.ai.assistance.operit.data.integration.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Tool definition schema from Composio API
 * Represents a available tool that can be used through integrations
 */
@Serializable
data class ToolDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,  // Tool name (e.g., "github_create_issue")
    val description: String = "",  // Tool description
    val toolkit: String,  // The toolkit this tool belongs to (e.g., "github", "slack")
    val category: String = "",  // Tool category (e.g., "repository", "workflow")
    val version: String = "1.0",  // Tool version
    val inputSchema: ToolInputSchema = ToolInputSchema(),  // JSON schema for tool inputs
    val outputSchema: ToolOutputSchema = ToolOutputSchema(),  // JSON schema for tool outputs
    val isEnabled: Boolean = true,  // Whether the tool is enabled
    val requiresAuth: Boolean = true,  // Whether the tool requires authentication
    val rateLimit: RateLimitInfo? = null,  // Rate limit information
    val metadata: Map<String, String> = emptyMap()  // Additional tool-specific metadata
)

/**
 * Input schema for tool parameters
 */
@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, ToolParameter> = emptyMap(),
    val required: List<String> = emptyList(),
    val description: String = ""
)

/**
 * Individual tool parameter definition
 */
@Serializable
data class ToolParameter(
    val type: String,  // Parameter type (string, integer, boolean, array, object)
    val description: String = "",  // Parameter description
    val default: String? = null,  // Default value
    val enum: List<String>? = null,  // Allowed values for enum parameters
    val format: String? = null,  // Format specification (e.g., "email", "uri")
    val items: ToolParameterItems? = null,  // For array types
    val properties: Map<String, ToolParameter>? = null  // For object types
)

/**
 * Items definition for array parameters
 */
@Serializable
data class ToolParameterItems(
    val type: String = "string",
    val description: String = "",
    val enum: List<String>? = null
)

/**
 * Output schema for tool results
 */
@Serializable
data class ToolOutputSchema(
    val type: String = "object",
    val properties: Map<String, ToolOutputProperty> = emptyMap(),
    val description: String = ""
)

/**
 * Output property definition
 */
@Serializable
data class ToolOutputProperty(
    val type: String,
    val description: String = "",
    val format: String? = null
)

/**
 * Rate limit information for tools
 */
@Serializable
data class RateLimitInfo(
    val limit: Int,  // Maximum requests allowed
    val remaining: Int = limit,  // Remaining requests
    val resetAt: Long = 0L  // Timestamp when rate limit resets
)

/**
 * Collection of tool definitions for a toolkit
 */
@Serializable
data class ToolkitDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,  // Toolkit name (e.g., "github")
    val displayName: String,  // Display name (e.g., "GitHub")
    val description: String = "",  // Toolkit description
    val icon: String = "",  // Icon identifier
    val version: String = "1.0",  // Toolkit version
    val tools: List<ToolDefinition> = emptyList(),  // Available tools
    val authType: AuthType = AuthType.NONE,  // Authentication type required
    val isEnabled: Boolean = true  // Whether the toolkit is enabled
)

/**
 * Authentication types supported by toolkits
 */
@Serializable
enum class AuthType {
    NONE,           // No authentication required
    OAUTH2,        // OAuth 2.0 authentication
    API_KEY,       // API key authentication
    BASIC,         // Basic authentication
    BEARER_TOKEN   // Bearer token authentication
}
