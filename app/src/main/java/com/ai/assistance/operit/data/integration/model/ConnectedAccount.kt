package com.ai.assistance.operit.data.integration.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Connected account model for OAuth-based integrations
 * Stores information about connected third-party accounts (e.g., GitHub, Slack, etc.)
 */
@Serializable
data class ConnectedAccount(
    val id: String = UUID.randomUUID().toString(),
    val toolkit: String,  // The integration toolkit (e.g., "github", "slack", "composio")
    val accountName: String,  // Display name for the account
    val accountId: String = "",  // Remote account ID from the provider
    val connectedAt: Long = System.currentTimeMillis(),  // Timestamp when connected
    val lastSyncAt: Long? = null,  // Last successful sync timestamp
    val status: AccountStatus = AccountStatus.ACTIVE,  // Current connection status
    val accessToken: String = "",  // Encrypted access token (should be stored securely)
    val refreshToken: String = "",  // Encrypted refresh token for token renewal
    val tokenExpiresAt: Long = 0L,  // Token expiration timestamp
    val metadata: Map<String, String> = emptyMap()  // Additional provider-specific data
)

/**
 * Account connection status
 */
@Serializable
enum class AccountStatus {
    PENDING,   // OAuth flow initiated, waiting for callback
    ACTIVE,    // Account is connected and working
    EXPIRED,   // Token has expired and needs refresh
    REVOKED,   // User has revoked access
    ERROR      // Connection encountered an error
}
