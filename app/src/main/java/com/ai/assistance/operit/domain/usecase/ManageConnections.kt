package com.ai.assistance.operit.domain.usecase

import android.content.Context
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.IntegrationRepository
import com.ai.assistance.operit.data.integration.model.AccountStatus
import com.ai.assistance.operit.data.integration.model.ConnectedAccount
import com.ai.assistance.operit.data.integration.preferences.IntegrationPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manage Connections Use Case
 * 
 * Handles OAuth connection lifecycle management:
 * - OAuth flow initiation (getting authorization URL)
 * - Token exchange (exchanging code for tokens)
 * - Connection status management (active, expired, error)
 * - Disconnection (removing connections)
 * 
 * This use case orchestrates between:
 * - IntegrationRepository (for storing connected accounts)
 * - ComposioApiService (for OAuth API calls)
 * - IntegrationPreferences (for secure token storage)
 * 
 * @param context Application context
 */
class ManageConnections(context: Context) {

  companion object {
    private const val TAG = "ManageConnections"
    
    @Volatile
    private var INSTANCE: ManageConnections? = null
    
    fun getInstance(context: Context): ManageConnections {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: ManageConnections(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
  
  private val integrationRepository = IntegrationRepository.getInstance(context)
  private val composioApiService = ComposioApiService.getInstance(context)
  private val integrationPreferences = IntegrationPreferences.getInstance(context)
  
  // State flow for connection events
  private val _connectionEvents = MutableStateFlow<ConnectionEvent?>(null)
  val connectionEvents: StateFlow<ConnectionEvent?> = _connectionEvents
  
  // ==================== OAuth Flow ====================
  
  /**
   * Initiate OAuth flow for a toolkit
   * 
   * @param toolkit The toolkit name (e.g., "github", "slack", "google_calendar")
   * @param redirectUri The OAuth callback URI
   * @return Result containing OAuth URL and pending connection info
   */
  suspend fun initiateOAuthFlow(
    toolkit: String,
    redirectUri: String
  ): Result<OAuthFlowResult> = withContext(Dispatchers.IO) {
    try {
      AppLogger.d(TAG, "Initiating OAuth flow for toolkit: $toolkit")
      
      // Check if Composio API is configured
      if (!composioApiService.isConfigured()) {
        val error = Exception("COMPOSIO_API_KEY not configured")
        AppLogger.e(TAG, error.message ?: "API not configured")
        return@withContext Result.failure(error)
      }
      
      // Get OAuth URL from Composio
      val oauthResult = composioApiService.getOAuthUrl(toolkit, redirectUri)
      
      oauthResult.fold(
        onSuccess = { response ->
          AppLogger.d(TAG, "Got OAuth URL for $toolkit: ${response.authUrl}")
          
          // Create a pending connection account
          val accountId = response.connectionId ?: UUID.randomUUID().toString()
          val pendingAccount = ConnectedAccount(
            id = accountId,
            toolkit = toolkit,
            accountName = "Pending $toolkit connection",
            status = AccountStatus.PENDING,
            connectedAt = System.currentTimeMillis(),
            lastSyncAt = null,
            accessToken = "",
            refreshToken = "",
            tokenExpiresAt = 0L,
            metadata = mapOf("authUrl" to response.authUrl)
          )
          
          // Save pending account
          val saveResult = integrationRepository.saveConnectedAccount(pendingAccount)
          
          saveResult.fold(
            onSuccess = {
              val result = OAuthFlowResult(
                accountId = accountId,
                authUrl = response.authUrl,
                connectionId = response.connectionId ?: accountId
              )
              _connectionEvents.value = ConnectionEvent.OAuthInitiated(toolkit, accountId)
              Result.success(result)
            },
            onFailure = { error ->
              AppLogger.e(TAG, "Failed to save pending account: ${error.message}")
              Result.failure(error)
            }
          )
        },
        onFailure = { error ->
          AppLogger.e(TAG, "Failed to get OAuth URL: ${error.message}")
          Result.failure(error)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error initiating OAuth flow", e)
      Result.failure(e)
    }
  }
  
  /**
   * Complete OAuth flow by exchanging authorization code for tokens
   * 
   * @param accountId The pending account ID
   * @param authCode The authorization code from OAuth callback
   * @return Result containing the completed connected account
   */
  suspend fun completeOAuthFlow(
    accountId: String,
    authCode: String
  ): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
    try {
      AppLogger.d(TAG, "Completing OAuth flow for account: $accountId")
      
      // Get the pending account
      val accountResult = integrationRepository.getConnectedAccount(accountId)
      
      accountResult.fold(
        onSuccess = { account ->
          if (account == null) {
            return@withContext Result.failure(Exception("Account not found: $accountId"))
          }
          
          // Exchange code for tokens
          val exchangeResult = composioApiService.exchangeOAuthCode(
            code = authCode,
            connectionId = accountId
          )
          
          exchangeResult.fold(
            onSuccess = { response ->
              val currentTime = System.currentTimeMillis()
              val expiresAt = currentTime + (response.expiresIn * 1000)
              
              // Save tokens securely
              integrationPreferences.saveTokens(
                accountId = accountId,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: "",
                expiresInSeconds = response.expiresIn
              )
              
              // Update account status
              val updatedAccount = account.copy(
                status = AccountStatus.ACTIVE,
                accountName = response.accountName ?: account.accountName,
                connectedAt = currentTime,
                lastSyncAt = currentTime,
                accessToken = response.accessToken, // Store reference (actual token in secure prefs)
                refreshToken = response.refreshToken ?: "",
                tokenExpiresAt = expiresAt,
                metadata = account.metadata + mapOf(
                  "entityId" to (response.entityId ?: ""),
                  "connectionStatus" to "connected"
                )
              )
              
              // Save updated account
              val saveResult = integrationRepository.saveConnectedAccount(updatedAccount)
              
              saveResult.fold(
                onSuccess = {
                  _connectionEvents.value = ConnectionEvent.ConnectionCompleted(accountId)
                  AppLogger.d(TAG, "OAuth flow completed successfully for: $accountId")
                  Result.success(updatedAccount)
                },
                onFailure = { error ->
                  AppLogger.e(TAG, "Failed to save completed account: ${error.message}")
                  Result.failure(error)
                }
              )
            },
            onFailure = { error ->
              // Mark account as error
              integrationRepository.updateConnectedAccountStatus(
                accountId, 
                AccountStatus.ERROR
              )
              _connectionEvents.value = ConnectionEvent.ConnectionFailed(accountId, error.message ?: "Token exchange failed")
              AppLogger.e(TAG, "Failed to exchange OAuth code: ${error.message}")
              Result.failure(error)
            }
          )
        },
        onFailure = { error ->
          AppLogger.e(TAG, "Failed to get account: ${error.message}")
          Result.failure(error)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error completing OAuth flow", e)
      Result.failure(e)
    }
  }
  
  // ==================== Connection Management ====================
  
  /**
   * Get all connected accounts
   */
  suspend fun getAllConnections(): Result<List<ConnectedAccount>> {
    return integrationRepository.listConnectedAccounts()
  }
  
  /**
   * Get connections by toolkit type
   */
  suspend fun getConnectionsByToolkit(toolkit: String): Result<List<ConnectedAccount>> {
    return integrationRepository.listConnectedAccountsByToolkit(toolkit)
  }
  
  /**
   * Get connections by status
   */
  suspend fun getConnectionsByStatus(status: AccountStatus): Result<List<ConnectedAccount>> {
    return integrationRepository.listConnectedAccountsByStatus(status)
  }
  
  /**
   * Get a specific connection by ID
   */
  suspend fun getConnection(accountId: String): Result<ConnectedAccount?> {
    return integrationRepository.getConnectedAccount(accountId)
  }
  
  /**
   * Refresh connection tokens
   * 
   * @param accountId The account ID to refresh
   * @return Result containing updated account
   */
  suspend fun refreshConnection(accountId: String): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
    try {
      AppLogger.d(TAG, "Refreshing connection: $accountId")
      
      // Check if token needs refresh
      val needsRefresh = integrationPreferences.isTokenNeedsRefresh(accountId)
      
      if (!needsRefresh) {
        // Token still valid, just return current account
        val accountResult = integrationRepository.getConnectedAccount(accountId)
        return@withContext accountResult.map { it ?: throw Exception("Account not found") }
      }
      
      // Get current account
      val accountResult = integrationRepository.getConnectedAccount(accountId)
      
      accountResult.fold(
        onSuccess = { account ->
          if (account == null) {
            return@withContext Result.failure(Exception("Account not found"))
          }
          
          // Get refresh token
          val refreshToken = integrationPreferences.getRefreshToken(accountId)
          
          if (refreshToken.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("No refresh token available"))
          }
          
          // Note: In a real implementation, you would call Composio's token refresh endpoint
          // For now, we'll mark the token as needs re-authentication
          AppLogger.w(TAG, "Token refresh not implemented - needs re-authentication")
          
          // Update status to indicate token expired
          integrationRepository.updateConnectedAccountStatus(
            accountId,
            AccountStatus.EXPIRED
          )
          
          _connectionEvents.value = ConnectionEvent.TokenExpired(accountId)
          Result.failure(Exception("Token expired - please reconnect"))
        },
        onFailure = { error ->
          Result.failure(error)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error refreshing connection", e)
      Result.failure(e)
    }
  }
  
  /**
   * Disconnect and remove a connection
   * 
   * @param accountId The account ID to disconnect
   * @return Result indicating success
   */
  suspend fun disconnectConnection(accountId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      AppLogger.d(TAG, "Disconnecting connection: $accountId")
      
      // Delete stored tokens
      integrationPreferences.deleteTokens(accountId)
      
      // Delete the connected account
      val deleteResult = integrationRepository.deleteConnectedAccount(accountId)
      
      deleteResult.fold(
        onSuccess = { deleted ->
          if (deleted) {
            _connectionEvents.value = ConnectionEvent.Disconnected(accountId)
            AppLogger.d(TAG, "Disconnected: $accountId")
          }
          Result.success(deleted)
        },
        onFailure = { error ->
          AppLogger.e(TAG, "Failed to disconnect: ${error.message}")
          Result.failure(error)
        }
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Error disconnecting", e)
      Result.failure(e)
    }
  }
  
  /**
   * Update connection status
   * 
   * @param accountId The account ID
   * @param status New status
   * @return Result containing updated account
   */
  suspend fun updateConnectionStatus(
    accountId: String,
    status: AccountStatus
  ): Result<ConnectedAccount?> {
    return integrationRepository.updateConnectedAccountStatus(accountId, status)
  }
  
  /**
   * Check if any connections exist
   */
  suspend fun hasConnections(): Boolean {
    return integrationRepository.hasConnectedAccounts()
  }
  
  /**
   * Get connection count
   */
  suspend fun getConnectionCount(): Int {
    return integrationRepository.getConnectedAccountCount()
  }
  
  /**
   * Clear connection events
   */
  fun clearConnectionEvent() {
    _connectionEvents.value = null
  }
}

/**
 * Result from OAuth flow initiation
 */
data class OAuthFlowResult(
  val accountId: String,
  val authUrl: String,
  val connectionId: String
)

/**
 * Connection lifecycle events
 */
sealed class ConnectionEvent {
  data class OAuthInitiated(val toolkit: String, val accountId: String) : ConnectionEvent()
  data class ConnectionCompleted(val accountId: String) : ConnectionEvent()
  data class ConnectionFailed(val accountId: String, val error: String) : ConnectionEvent()
  data class TokenExpired(val accountId: String) : ConnectionEvent()
  data class Disconnected(val accountId: String) : ConnectionEvent()
}
