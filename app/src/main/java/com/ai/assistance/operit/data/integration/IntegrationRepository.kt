package com.ai.assistance.operit.data.integration

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.data.integration.model.AccountStatus
import com.ai.assistance.operit.data.integration.model.ConnectedAccount
import com.ai.assistance.operit.data.integration.model.CustomWebhook
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Integration Repository
 * Handles CRUD operations for ConnectedAccount and CustomWebhook
 * Uses file-based storage in the Downloads directory
 */
class IntegrationRepository(private val context: Context) {

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    classDiscriminator = "__type"
  }

  companion object {
    private const val TAG = "IntegrationRepository"
    private const val INTEGRATION_DIR = "Operit/integration"
    private const val ACCOUNTS_DIR = "accounts"
    private const val WEBHOOKS_DIR = "webhooks"

    @Volatile
    private var INSTANCE: IntegrationRepository? = null

    // Flow to notify about integration changes
    val integrationUpdateEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun getInstance(context: Context): IntegrationRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: IntegrationRepository(context.applicationContext).also { INSTANCE = it }
      }
    }

    fun notifyIntegrationsChanged() {
      integrationUpdateEvents.tryEmit(Unit)
    }
  }

  // ==================== ConnectedAccount Operations ====================

  /**
   * Get the directory for storing connected accounts
   */
  private fun getAccountsDirectory(): File {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val accountsDir = File(downloadDir, "$INTEGRATION_DIR/$ACCOUNTS_DIR")
    if (!accountsDir.exists()) {
      accountsDir.mkdirs()
    }
    return accountsDir
  }

  /**
   * Get the file for a specific connected account
   */
  private fun getAccountFile(accountId: String): File {
    return File(getAccountsDirectory(), "$accountId.json")
  }

  /**
   * Save a connected account (create or update)
   */
  suspend fun saveConnectedAccount(account: ConnectedAccount): Result<ConnectedAccount> {
    return withContext(Dispatchers.IO) {
      try {
        val file = getAccountFile(account.id)
        val jsonString = json.encodeToString(account)
        file.writeText(jsonString)
        AppLogger.d(TAG, "Saved connected account: ${account.id}")
        notifyIntegrationsChanged()
        Result.success(account)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save connected account: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Get a connected account by ID
   */
  suspend fun getConnectedAccount(accountId: String): Result<ConnectedAccount?> {
    return withContext(Dispatchers.IO) {
      try {
        val file = getAccountFile(accountId)
        if (!file.exists()) {
          return@withContext Result.success(null)
        }
        val jsonString = file.readText()
        val account = json.decodeFromString<ConnectedAccount>(jsonString)
        Result.success(account)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get connected account: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * List all connected accounts
   */
  suspend fun listConnectedAccounts(): Result<List<ConnectedAccount>> {
    return withContext(Dispatchers.IO) {
      try {
        val accountsDir = getAccountsDirectory()
        val files = accountsDir.listFiles { file -> file.extension == "json" } ?: emptyArray()
        
        val accounts = files.mapNotNull { file ->
          try {
            val jsonString = file.readText()
            json.decodeFromString<ConnectedAccount>(jsonString)
          } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse account file ${file.name}: ${e.message}")
            null
          }
        }.sortedByDescending { it.connectedAt }
        
        Result.success(accounts)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to list connected accounts: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * List connected accounts by toolkit type
   */
  suspend fun listConnectedAccountsByToolkit(toolkit: String): Result<List<ConnectedAccount>> {
    return withContext(Dispatchers.IO) {
      listConnectedAccounts().map { accounts ->
        accounts.filter { it.toolkit.equals(toolkit, ignoreCase = true) }
      }
    }
  }

  /**
   * List connected accounts by status
   */
  suspend fun listConnectedAccountsByStatus(status: AccountStatus): Result<List<ConnectedAccount>> {
    return withContext(Dispatchers.IO) {
      listConnectedAccounts().map { accounts ->
        accounts.filter { it.status == status }
      }
    }
  }

  /**
   * Delete a connected account by ID
   */
  suspend fun deleteConnectedAccount(accountId: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      try {
        val file = getAccountFile(accountId)
        if (file.exists()) {
          val deleted = file.delete()
          if (deleted) {
            AppLogger.d(TAG, "Deleted connected account: $accountId")
            notifyIntegrationsChanged()
          }
          Result.success(deleted)
        } else {
          Result.success(false)
        }
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to delete connected account: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Update connected account status
   */
  suspend fun updateConnectedAccountStatus(
    accountId: String,
    status: AccountStatus,
    accessToken: String? = null,
    refreshToken: String? = null,
    tokenExpiresAt: Long? = null
  ): Result<ConnectedAccount?> {
    return withContext(Dispatchers.IO) {
      val accountResult = getConnectedAccount(accountId)
      accountResult.fold(
        onSuccess = { account ->
          if (account == null) {
            return@withContext Result.success(null)
          }
          val updatedAccount = account.copy(
            status = status,
            accessToken = accessToken ?: account.accessToken,
            refreshToken = refreshToken ?: account.refreshToken,
            tokenExpiresAt = tokenExpiresAt ?: account.tokenExpiresAt
          )
          saveConnectedAccount(updatedAccount)
        },
        onFailure = { error ->
          Result.failure(error)
        }
      )
    }
  }

  // ==================== CustomWebhook Operations ====================

  /**
   * Get the directory for storing custom webhooks
   */
  private fun getWebhooksDirectory(): File {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val webhooksDir = File(downloadDir, "$INTEGRATION_DIR/$WEBHOOKS_DIR")
    if (!webhooksDir.exists()) {
      webhooksDir.mkdirs()
    }
    return webhooksDir
  }

  /**
   * Get the file for a specific custom webhook
   */
  private fun getWebhookFile(webhookId: String): File {
    return File(getWebhooksDirectory(), "$webhookId.json")
  }

  /**
   * Save a custom webhook (create or update)
   */
  suspend fun saveCustomWebhook(webhook: CustomWebhook): Result<CustomWebhook> {
    return withContext(Dispatchers.IO) {
      try {
        val updatedWebhook = webhook.copy(updatedAt = System.currentTimeMillis())
        val file = getWebhookFile(updatedWebhook.id)
        val jsonString = json.encodeToString(updatedWebhook)
        file.writeText(jsonString)
        AppLogger.d(TAG, "Saved custom webhook: ${updatedWebhook.id}")
        notifyIntegrationsChanged()
        Result.success(updatedWebhook)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to save custom webhook: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Get a custom webhook by ID
   */
  suspend fun getCustomWebhook(webhookId: String): Result<CustomWebhook?> {
    return withContext(Dispatchers.IO) {
      try {
        val file = getWebhookFile(webhookId)
        if (!file.exists()) {
          return@withContext Result.success(null)
        }
        val jsonString = file.readText()
        val webhook = json.decodeFromString<CustomWebhook>(jsonString)
        Result.success(webhook)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to get custom webhook: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * List all custom webhooks
   */
  suspend fun listCustomWebhooks(): Result<List<CustomWebhook>> {
    return withContext(Dispatchers.IO) {
      try {
        val webhooksDir = getWebhooksDirectory()
        val files = webhooksDir.listFiles { file -> file.extension == "json" } ?: emptyArray()
        
        val webhooks = files.mapNotNull { file ->
          try {
            val jsonString = file.readText()
            json.decodeFromString<CustomWebhook>(jsonString)
          } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse webhook file ${file.name}: ${e.message}")
            null
          }
        }.sortedByDescending { it.updatedAt }
        
        Result.success(webhooks)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to list custom webhooks: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * List enabled custom webhooks
   */
  suspend fun listEnabledCustomWebhooks(): Result<List<CustomWebhook>> {
    return withContext(Dispatchers.IO) {
      listCustomWebhooks().map { webhooks ->
        webhooks.filter { it.enabled }
      }
    }
  }

  /**
   * List custom webhooks by event type
   */
  suspend fun listCustomWebhooksByEventType(eventType: com.ai.assistance.operit.data.integration.model.WebhookEventType): Result<List<CustomWebhook>> {
    return withContext(Dispatchers.IO) {
      listCustomWebhooks().map { webhooks ->
        webhooks.filter { it.eventType == eventType }
      }
    }
  }

  /**
   * Delete a custom webhook by ID
   */
  suspend fun deleteCustomWebhook(webhookId: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      try {
        val file = getWebhookFile(webhookId)
        if (file.exists()) {
          val deleted = file.delete()
          if (deleted) {
            AppLogger.d(TAG, "Deleted custom webhook: $webhookId")
            notifyIntegrationsChanged()
          }
          Result.success(deleted)
        } else {
          Result.success(false)
        }
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to delete custom webhook: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Toggle custom webhook enabled status
   */
  suspend fun toggleCustomWebhookEnabled(webhookId: String, enabled: Boolean): Result<CustomWebhook?> {
    return withContext(Dispatchers.IO) {
      val webhookResult = getCustomWebhook(webhookId)
      webhookResult.fold(
        onSuccess = { webhook ->
          if (webhook == null) {
            return@withContext Result.success(null)
          }
          val updatedWebhook = webhook.copy(enabled = enabled)
          saveCustomWebhook(updatedWebhook)
        },
        onFailure = { error ->
          Result.failure(error)
        }
      )
    }
  }

  // ==================== Utility Operations ====================

  /**
   * Check if any connected accounts exist
   */
  suspend fun hasConnectedAccounts(): Boolean {
    return listConnectedAccounts().getOrNull()?.isNotEmpty() ?: false
  }

  /**
   * Check if any custom webhooks exist
   */
  suspend fun hasCustomWebhooks(): Boolean {
    return listCustomWebhooks().getOrNull()?.isNotEmpty() ?: false
  }

  /**
   * Get count of connected accounts
   */
  suspend fun getConnectedAccountCount(): Int {
    return listConnectedAccounts().getOrNull()?.size ?: 0
  }

  /**
   * Get count of custom webhooks
   */
  suspend fun getCustomWebhookCount(): Int {
    return listCustomWebhooks().getOrNull()?.size ?: 0
  }

  /**
   * Clear all integration data (for testing/reset purposes)
   */
  suspend fun clearAllData(): Result<Unit> {
    return withContext(Dispatchers.IO) {
      try {
        // Clear accounts
        val accountsDir = getAccountsDirectory()
        accountsDir.listFiles()?.forEach { it.delete() }
        
        // Clear webhooks
        val webhooksDir = getWebhooksDirectory()
        webhooksDir.listFiles()?.forEach { it.delete() }
        
        AppLogger.d(TAG, "Cleared all integration data")
        notifyIntegrationsChanged()
        Result.success(Unit)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to clear integration data: ${e.message}", e)
        Result.failure(e)
      }
    }
  }
}
