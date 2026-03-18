package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.webhookDataStore: DataStore<Preferences> by preferencesDataStore(name = "webhook_config")

/**
 * Webhook configuration preferences
 * Stores webhook URLs and enabled/disabled states for different event types
 */
class WebhookPreferences(private val context: Context) {

    companion object {
        // General webhook settings
        private val WEBHOOK_ENABLED = booleanPreferencesKey("webhook_enabled")
        private val GLOBAL_WEBHOOK_URL = stringPreferencesKey("global_webhook_url")
        private val WEBHOOK_SECRET = stringPreferencesKey("webhook_secret")
        
        // MCP Server webhooks
        private val MCP_SERVER_START_ENABLED = booleanPreferencesKey("mcp_server_start_enabled")
        private val MCP_SERVER_STOP_ENABLED = booleanPreferencesKey("mcp_server_stop_enabled")
        private val MCP_SERVER_INSTALL_ENABLED = booleanPreferencesKey("mcp_server_install_enabled")
        private val MCP_SERVER_UNINSTALL_ENABLED = booleanPreferencesKey("mcp_server_uninstall_enabled")
        
        // Workflow webhooks
        private val WORKFLOW_TRIGGER_ENABLED = booleanPreferencesKey("workflow_trigger_enabled")
        private val WORKFLOW_NODE_START_ENABLED = booleanPreferencesKey("workflow_node_start_enabled")
        private val WORKFLOW_NODE_COMPLETE_ENABLED = booleanPreferencesKey("workflow_node_complete_enabled")
        private val WORKFLOW_COMPLETE_ENABLED = booleanPreferencesKey("workflow_complete_enabled")
        
        // Package webhooks
        private val PACKAGE_INSTALL_ENABLED = booleanPreferencesKey("package_install_enabled")
        private val PACKAGE_UNINSTALL_ENABLED = booleanPreferencesKey("package_uninstall_enabled")
        
        // Terminal webhooks
        private val TERMINAL_COMMAND_ENABLED = booleanPreferencesKey("terminal_command_enabled")
        
        // Instance
        @Volatile
        private var INSTANCE: WebhookPreferences? = null
        
        fun getInstance(context: Context): WebhookPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebhookPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // General webhook settings
    val webhookEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[WEBHOOK_ENABLED] ?: false
    }
    
    val globalWebhookUrl: Flow<String> = context.webhookDataStore.data.map { preferences ->
        preferences[GLOBAL_WEBHOOK_URL] ?: ""
    }
    
    val webhookSecret: Flow<String> = context.webhookDataStore.data.map { preferences ->
        preferences[WEBHOOK_SECRET] ?: ""
    }
    
    // MCP Server webhooks
    val mcpServerStartEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[MCP_SERVER_START_ENABLED] ?: true
    }
    
    val mcpServerStopEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[MCP_SERVER_STOP_ENABLED] ?: true
    }
    
    val mcpServerInstallEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[MCP_SERVER_INSTALL_ENABLED] ?: true
    }
    
    val mcpServerUninstallEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[MCP_SERVER_UNINSTALL_ENABLED] ?: true
    }
    
    // Workflow webhooks
    val workflowTriggerEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[WORKFLOW_TRIGGER_ENABLED] ?: true
    }
    
    val workflowNodeStartEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[WORKFLOW_NODE_START_ENABLED] ?: true
    }
    
    val workflowNodeCompleteEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[WORKFLOW_NODE_COMPLETE_ENABLED] ?: true
    }
    
    val workflowCompleteEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[WORKFLOW_COMPLETE_ENABLED] ?: true
    }
    
    // Package webhooks
    val packageInstallEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[PACKAGE_INSTALL_ENABLED] ?: true
    }
    
    val packageUninstallEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[PACKAGE_UNINSTALL_ENABLED] ?: true
    }
    
    // Terminal webhooks
    val terminalCommandEnabled: Flow<Boolean> = context.webhookDataStore.data.map { preferences ->
        preferences[TERMINAL_COMMAND_ENABLED] ?: false
    }
    
    // Setters
    suspend fun setWebhookEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[WEBHOOK_ENABLED] = enabled
        }
    }
    
    suspend fun setGlobalWebhookUrl(url: String) {
        context.webhookDataStore.edit { preferences ->
            preferences[GLOBAL_WEBHOOK_URL] = url
        }
    }
    
    suspend fun setWebhookSecret(secret: String) {
        context.webhookDataStore.edit { preferences ->
            preferences[WEBHOOK_SECRET] = secret
        }
    }
    
    suspend fun setMcpServerStartEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[MCP_SERVER_START_ENABLED] = enabled
        }
    }
    
    suspend fun setMcpServerStopEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[MCP_SERVER_STOP_ENABLED] = enabled
        }
    }
    
    suspend fun setMcpServerInstallEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[MCP_SERVER_INSTALL_ENABLED] = enabled
        }
    }
    
    suspend fun setMcpServerUninstallEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[MCP_SERVER_UNINSTALL_ENABLED] = enabled
        }
    }
    
    suspend fun setWorkflowTriggerEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[WORKFLOW_TRIGGER_ENABLED] = enabled
        }
    }
    
    suspend fun setWorkflowNodeStartEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[WORKFLOW_NODE_START_ENABLED] = enabled
        }
    }
    
    suspend fun setWorkflowNodeCompleteEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[WORKFLOW_NODE_COMPLETE_ENABLED] = enabled
        }
    }
    
    suspend fun setWorkflowCompleteEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[WORKFLOW_COMPLETE_ENABLED] = enabled
        }
    }
    
    suspend fun setPackageInstallEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[PACKAGE_INSTALL_ENABLED] = enabled
        }
    }
    
    suspend fun setPackageUninstallEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[PACKAGE_UNINSTALL_ENABLED] = enabled
        }
    }
    
    suspend fun setTerminalCommandEnabled(enabled: Boolean) {
        context.webhookDataStore.edit { preferences ->
            preferences[TERMINAL_COMMAND_ENABLED] = enabled
        }
    }
}
