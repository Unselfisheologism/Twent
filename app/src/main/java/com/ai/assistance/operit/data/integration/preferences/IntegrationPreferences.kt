package com.ai.assistance.operit.data.integration.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration Preferences - Secure Token Storage
 * 
 * Uses Android's EncryptedSharedPreferences to securely store:
 * - OAuth tokens (access token, refresh token, expiration)
 * - API keys for custom webhooks
 * 
 * This provides encrypted storage for sensitive credentials,
 * ensuring tokens are not stored in plaintext.
 */
class IntegrationPreferences(private val context: Context) {

  companion object {
    private const val TAG = "IntegrationPreferences"
    private const val PREFS_NAME = "integration_secure_prefs"
    
    // Token key suffixes
    private const val KEY_ACCESS_TOKEN = "_access_token"
    private const val KEY_REFRESH_TOKEN = "_refresh_token"
    private const val KEY_TOKEN_EXPIRES_AT = "_token_expires_at"
    private const val KEY_API_KEY = "_api_key"
    
    // Default token refresh buffer (5 minutes before expiration)
    const val DEFAULT_REFRESH_BUFFER_MS = 5 * 60 * 1000L
    
    @Volatile
    private var INSTANCE: IntegrationPreferences? = null
    
    fun getInstance(context: Context): IntegrationPreferences {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: IntegrationPreferences(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
  
  private val masterKey: MasterKey by lazy {
    MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
  }
  
  private val encryptedPrefs: SharedPreferences by lazy {
    EncryptedSharedPreferences.create(
      context,
      PREFS_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  }
  
  // ==================== OAuth Token Operations ====================
  
  /**
   * Save access token for an account
   */
  suspend fun saveAccessToken(accountId: String, accessToken: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_ACCESS_TOKEN
        encryptedPrefs.edit().putString(key, accessToken).apply()
        AppLogger.d(TAG, "Saved access token for account: $accountId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save access token: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  /**
   * Get access token for an account
   */
  suspend fun getAccessToken(accountId: String): String? {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_ACCESS_TOKEN
        encryptedPrefs.getString(key, null)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get access token: ${e.message}", e)
        null
      }
    }
  }
  
  /**
   * Save refresh token for an account
   */
  suspend fun saveRefreshToken(accountId: String, refreshToken: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_REFRESH_TOKEN
        encryptedPrefs.edit().putString(key, refreshToken).apply()
        AppLogger.d(TAG, "Saved refresh token for account: $accountId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save refresh token: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  /**
   * Get refresh token for an account
   */
  suspend fun getRefreshToken(accountId: String): String? {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_REFRESH_TOKEN
        encryptedPrefs.getString(key, null)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get refresh token: ${e.message}", e)
        null
      }
    }
  }
  
  /**
   * Save token expiration timestamp for an account
   */
  suspend fun saveTokenExpiresAt(accountId: String, expiresAt: Long): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_TOKEN_EXPIRES_AT
        encryptedPrefs.edit().putLong(key, expiresAt).apply()
        AppLogger.d(TAG, "Saved token expiration for account: $accountId, expires at: $expiresAt")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save token expiration: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  /**
   * Get token expiration timestamp for an account
   */
  suspend fun getTokenExpiresAt(accountId: String): Long {
    return withContext(Dispatchers.IO) {
      try {
        val key = accountId + KEY_TOKEN_EXPIRES_AT
        encryptedPrefs.getLong(key, 0L)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get token expiration: ${e.message}", e)
        0L
      }
    }
  }
  
  /**
   * Save all token data at once (convenience method)
   */
  suspend fun saveTokens(
    accountId: String,
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long
  ): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        
        saveAccessToken(accountId, accessToken)
        saveRefreshToken(accountId, refreshToken)
        saveTokenExpiresAt(accountId, expiresAt)
        
        AppLogger.d(TAG, "Saved all tokens for account: $accountId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save tokens: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  /**
   * Get all token data for an account
   */
  suspend fun getTokens(accountId: String): TokenData? {
    return withContext(Dispatchers.IO) {
      try {
        val accessToken = getAccessToken(accountId)
        val refreshToken = getRefreshToken(accountId)
        val expiresAt = getTokenExpiresAt(accountId)
        
        if (accessToken != null) {
          TokenData(
            accessToken = accessToken,
            refreshToken = refreshToken ?: "",
            expiresAt = expiresAt
          )
        } else {
          null
        }
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get tokens: ${e.message}", e)
        null
      }
    }
  }
  
  /**
   * Check if token needs refresh
   * Returns true if token is expired or will expire within the buffer time
   */
  suspend fun isTokenNeedsRefresh(accountId: String, bufferMs: Long = DEFAULT_REFRESH_BUFFER_MS): Boolean {
    return withContext(Dispatchers.IO) {
      val expiresAt = getTokenExpiresAt(accountId)
      val currentTime = System.currentTimeMillis()
      currentTime >= (expiresAt - bufferMs)
    }
  }
  
  /**
   * Check if token is expired
   */
  suspend fun isTokenExpired(accountId: String): Boolean {
    return withContext(Dispatchers.IO) {
      val expiresAt = getTokenExpiresAt(accountId)
      System.currentTimeMillis() >= expiresAt
    }
  }
  
  /**
   * Delete all tokens for an account
   */
  suspend fun deleteTokens(accountId: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        encryptedPrefs.edit()
          .remove(accountId + KEY_ACCESS_TOKEN)
          .remove(accountId + KEY_REFRESH_TOKEN)
          .remove(accountId + KEY_TOKEN_EXPIRES_AT)
          .apply()
        AppLogger.d(TAG, "Deleted tokens for account: $accountId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to delete tokens: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  // ==================== API Key Operations ====================
  
  /**
   * Save API key for a custom webhook
   */
  suspend fun saveApiKey(webhookId: String, apiKey: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val key = webhookId + KEY_API_KEY
        encryptedPrefs.edit().putString(key, apiKey).apply()
        AppLogger.d(TAG, "Saved API key for webhook: $webhookId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save API key: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  /**
   * Get API key for a custom webhook
   */
  suspend fun getApiKey(webhookId: String): String? {
    return withContext(Dispatchers.IO) {
      try {
        val key = webhookId + KEY_API_KEY
        encryptedPrefs.getString(key, null)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get API key: ${e.message}", e)
        null
      }
    }
  }
  
  /**
   * Delete API key for a custom webhook
   */
  suspend fun deleteApiKey(webhookId: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        val key = webhookId + KEY_API_KEY
        encryptedPrefs.edit().remove(key).apply()
        AppLogger.d(TAG, "Deleted API key for webhook: $webhookId")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to delete API key: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  // ==================== Bulk Operations ====================
  
  /**
   * Get all account IDs that have stored tokens
   */
  suspend fun getAllAccountIds(): List<String> {
    return withContext(Dispatchers.IO) {
      try {
        encryptedPrefs.all.keys
          .filter { it.endsWith(KEY_ACCESS_TOKEN) }
          .map { it.removeSuffix(KEY_ACCESS_TOKEN) }
          .distinct()
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get all account IDs: ${e.message}", e)
        emptyList()
      }
    }
  }
  
  /**
   * Get all webhook IDs that have stored API keys
   */
  suspend fun getAllWebhookIds(): List<String> {
    return withContext(Dispatchers.IO) {
      try {
        encryptedPrefs.all.keys
          .filter { it.endsWith(KEY_API_KEY) }
          .map { it.removeSuffix(KEY_API_KEY) }
          .distinct()
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get all webhook IDs: ${e.message}", e)
        emptyList()
      }
    }
  }
  
  /**
   * Check if an account has stored tokens
   */
  suspend fun hasTokens(accountId: String): Boolean {
    return getAccessToken(accountId) != null
  }
  
  /**
   * Check if a webhook has stored API key
   */
  suspend fun hasApiKey(webhookId: String): Boolean {
    return getApiKey(webhookId) != null
  }
  
  /**
   * Clear all stored credentials (for testing/reset purposes)
   */
  suspend fun clearAll(): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        encryptedPrefs.edit().clear().apply()
        AppLogger.d(TAG, "Cleared all integration preferences")
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to clear preferences: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
  
  // ==================== Token Refresh Helper ====================
  
  /**
   * Check if token refresh is needed and return refresh info
   */
  suspend fun getTokenRefreshInfo(
    accountId: String,
    bufferMs: Long = DEFAULT_REFRESH_BUFFER_MS
  ): TokenRefreshInfo {
    return withContext(Dispatchers.IO) {
      val accessToken = getAccessToken(accountId)
      val refreshToken = getRefreshToken(accountId)
      val expiresAt = getTokenExpiresAt(accountId)
      val currentTime = System.currentTimeMillis()
      val timeUntilExpiry = expiresAt - currentTime
      val needsRefresh = currentTime >= (expiresAt - bufferMs)
      
      TokenRefreshInfo(
        accountId = accountId,
        hasAccessToken = !accessToken.isNullOrEmpty(),
        hasRefreshToken = !refreshToken.isNullOrEmpty(),
        expiresAt = expiresAt,
        timeUntilExpiryMs = timeUntilExpiry,
        needsRefresh = needsRefresh,
        refreshToken = refreshToken
      )
    }
  }
}

/**
 * Data class to hold token information
 */
data class TokenData(
  val accessToken: String,
  val refreshToken: String,
  val expiresAt: Long
)

/**
 * Data class for token refresh information
 */
data class TokenRefreshInfo(
  val accountId: String,
  val hasAccessToken: Boolean,
  val hasRefreshToken: Boolean,
  val expiresAt: Long,
  val timeUntilExpiryMs: Long,
  val needsRefresh: Boolean,
  val refreshToken: String?
)
