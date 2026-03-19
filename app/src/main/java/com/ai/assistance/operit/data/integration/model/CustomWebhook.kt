package com.ai.assistance.operit.data.integration.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Custom webhook configuration for external integrations
 * Allows users to define custom HTTP endpoints to send data to
 */
@Serializable
data class CustomWebhook(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",  // Display name for the webhook
    val url: String,  // Target webhook URL
    val method: HttpMethod = HttpMethod.POST,  // HTTP method
    val headers: Map<String, String> = emptyMap(),  // Custom headers
    val body: String = "",  // Request body template
    val enabled: Boolean = true,  // Whether the webhook is active
    val eventType: WebhookEventType = WebhookEventType.MANUAL,  // Trigger event type
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * Supported HTTP methods for webhooks
 */
@Serializable
enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE
}

/**
 * Types of events that can trigger webhooks
 */
@Serializable
enum class WebhookEventType {
    MANUAL,           // Manually triggered by user
    WORKFLOW_START,   // Triggered when a workflow starts
    WORKFLOW_COMPLETE, // Triggered when a workflow completes
    WORKFLOW_ERROR,   // Triggered when a workflow fails
    TOOL_EXECUTE,     // Triggered when a tool is executed
    MCP_SERVER_START, // Triggered when an MCP server starts
    MCP_SERVER_STOP,  // Triggered when an MCP server stops
    PACKAGE_INSTALL,  // Triggered when a package is installed
    PACKAGE_UNINSTALL // Triggered when a package is uninstalled
}

/**
 * Webhook payload template for dynamic content
 */
@Serializable
data class WebhookPayload(
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, String> = emptyMap(),
    val source: String = "operit",
    val version: String = "1.0"
)
