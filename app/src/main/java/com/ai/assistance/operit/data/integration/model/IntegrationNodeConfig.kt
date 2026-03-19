package com.ai.assistance.operit.data.integration.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Node configuration for workflow integration
 * Defines how integrations (toolkits, webhooks, MCP) are used within workflows
 */
@Serializable
data class IntegrationNodeConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: IntegrationNodeType,  // Type of integration node
    val name: String = "",  // Node display name
    val description: String = "",  // Node description
    val toolkit: String = "",  // Toolkit name (for tool-based integrations)
    val actionId: String = "",  // Action to execute
    val parameters: Map<String, IntegrationParameter> = emptyMap(),  // Action parameters
    val accountId: String? = null,  // Connected account to use (if required)
    val webhookConfig: CustomWebhook? = null,  // Custom webhook configuration (if type is WEBHOOK)
    val mcpServerConfig: MCPServerConfig? = null,  // MCP server configuration (if type is MCP)
    val errorHandling: ErrorHandlingConfig = ErrorHandlingConfig(),  // Error handling settings
    val retryConfig: RetryConfig = RetryConfig(),  // Retry settings
    val timeout: Long = 30000L,  // Execution timeout in milliseconds
    val enabled: Boolean = true  // Whether the node is enabled
)

/**
 * Types of integration nodes
 */
@Serializable
enum class IntegrationNodeType {
    TOOL,          // Direct tool execution from a toolkit (Composio)
    WEBHOOK,       // Custom webhook execution
    MCP,           // MCP server tool execution
    OAUTH          // OAuth connection management
}

/**
 * Integration parameter that supports both static values and node references
 */
@Serializable
sealed class IntegrationParameter {
    /**
     * Static value parameter
     */
    @Serializable
    data class StaticValue(
        val value: String
    ) : IntegrationParameter()

    /**
     * Reference to another node's output
     */
    @Serializable
    data class NodeReference(
        val nodeId: String,
        val outputKey: String = "result"
    ) : IntegrationParameter()

    /**
     * Expression-based parameter (supports templates)
     */
    @Serializable
    data class Expression(
        val expression: String
    ) : IntegrationParameter()
}

/**
 * MCP server configuration for integration nodes
 */
@Serializable
data class MCPServerConfig(
    val serverName: String,  // MCP server name
    val serverId: String? = null,  // MCP server ID (if already configured)
    val toolName: String,  // Tool to execute on the MCP server
    val parameters: Map<String, String> = emptyMap()  // Tool parameters
)

/**
 * Error handling configuration for integration nodes
 */
@Serializable
data class ErrorHandlingConfig(
    val onError: ErrorAction = ErrorAction.STOP,  // Action on error
    val errorMessage: String = "",  // Custom error message
    val fallbackValue: String? = null,  // Fallback value on error
    val continueOnError: Boolean = false  // Whether to continue execution on error
)

/**
 * Actions to take when an error occurs
 */
@Serializable
enum class ErrorAction {
    STOP,          // Stop workflow execution
    CONTINUE,      // Continue to next node
    RETRY,         // Retry the node
    FALLBACK       // Use fallback value and continue
}

/**
 * Retry configuration for integration nodes
 */
@Serializable
data class RetryConfig(
    val enabled: Boolean = false,  // Whether retries are enabled
    val maxRetries: Int = 3,  // Maximum number of retries
    val retryDelay: Long = 1000L,  // Delay between retries in milliseconds
    val exponentialBackoff: Boolean = true,  // Whether to use exponential backoff
    val retryableErrors: List<String> = emptyList()  // Errors that should trigger retry
)

/**
 * Integration execution result
 */
@Serializable
data class IntegrationResult(
    val nodeId: String,
    val success: Boolean,
    val output: String = "",  // Output as JSON string
    val errorMessage: String? = null,
    val executionTime: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Integration status for tracking
 */
@Serializable
enum class IntegrationStatus {
    IDLE,
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED
}
