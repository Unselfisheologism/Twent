package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.data.preferences.WebhookPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook service for sending event notifications to configured endpoints
 */
class WebhookService(private val context: Context) {

    companion object {
        private const val TAG = "WebhookService"
        
        @Volatile
        private var INSTANCE: WebhookService? = null
        
        fun getInstance(context: Context): WebhookService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebhookService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferences = WebhookPreferences.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Webhook event types
     */
    enum class WebhookEvent {
        // MCP Server events
        MCP_SERVER_START,
        MCP_SERVER_STOP,
        MCP_SERVER_INSTALL,
        MCP_SERVER_UNINSTALL,
        
        // Workflow events
        WORKFLOW_TRIGGER,
        WORKFLOW_NODE_START,
        WORKFLOW_NODE_COMPLETE,
        WORKFLOW_COMPLETE,
        
        // Package events
        PACKAGE_INSTALL,
        PACKAGE_UNINSTALL,
        
        // Terminal events
        TERMINAL_COMMAND
    }
    
    /**
     * Send a webhook notification for the specified event
     */
    fun sendWebhook(event: WebhookEvent, data: Map<String, Any>) {
        scope.launch {
            try {
                // Check if webhook is globally enabled
                val isEnabled = preferences.webhookEnabled.first()
                if (!isEnabled) {
                    return@launch
                }
                
                // Check if this specific event type is enabled
                if (!isEventEnabled(event)) {
                    return@launch
                }
                
                // Get global webhook URL
                val webhookUrl = preferences.globalWebhookUrl.first()
                if (webhookUrl.isBlank()) {
                    AppLogger.d(TAG, "Global webhook URL is not configured")
                    return@launch
                }
                
                // Build the payload
                val payload = buildPayload(event, data)
                
                // Get secret for HMAC signature
                val secret = preferences.webhookSecret.first()
                
                // Send the webhook
                sendRequest(webhookUrl, payload, secret)
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error sending webhook for event: ${event.name}", e)
            }
        }
    }
    
    /**
     * Check if the specific event type is enabled
     */
    private suspend fun isEventEnabled(event: WebhookEvent): Boolean {
        return when (event) {
            WebhookEvent.MCP_SERVER_START -> preferences.mcpServerStartEnabled.first()
            WebhookEvent.MCP_SERVER_STOP -> preferences.mcpServerStopEnabled.first()
            WebhookEvent.MCP_SERVER_INSTALL -> preferences.mcpServerInstallEnabled.first()
            WebhookEvent.MCP_SERVER_UNINSTALL -> preferences.mcpServerUninstallEnabled.first()
            WebhookEvent.WORKFLOW_TRIGGER -> preferences.workflowTriggerEnabled.first()
            WebhookEvent.WORKFLOW_NODE_START -> preferences.workflowNodeStartEnabled.first()
            WebhookEvent.WORKFLOW_NODE_COMPLETE -> preferences.workflowNodeCompleteEnabled.first()
            WebhookEvent.WORKFLOW_COMPLETE -> preferences.workflowCompleteEnabled.first()
            WebhookEvent.PACKAGE_INSTALL -> preferences.packageInstallEnabled.first()
            WebhookEvent.PACKAGE_UNINSTALL -> preferences.packageUninstallEnabled.first()
            WebhookEvent.TERMINAL_COMMAND -> preferences.terminalCommandEnabled.first()
        }
    }
    
    /**
     * Build the webhook payload
     */
    private fun buildPayload(event: WebhookEvent, data: Map<String, Any>): String {
        val timestamp = System.currentTimeMillis()
        return """
            {
                "event": "${event.name}",
                "timestamp": $timestamp,
                "data": ${mapToJson(data)}
            }
        """.trimIndent()
    }
    
    /**
     * Convert map to JSON string
     */
    private fun mapToJson(map: Map<String, Any>): String {
        val builder = StringBuilder()
        builder.append("{")
        map.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) builder.append(",")
            builder.append("\"$key\":")
            when (value) {
                is String -> builder.append("\"${value.replace("\"", "\\\"")}\"")
                is Number -> builder.append(value)
                is Boolean -> builder.append(value)
                is Map<*, *> -> builder.append(mapToJson(value as Map<String, Any>))
                else -> builder.append("\"${value.toString().replace("\"", "\\\"")}\"")
            }
        }
        builder.append("}")
        return builder.toString()
    }
    
    /**
     * Send HTTP request to webhook URL
     */
    private suspend fun sendRequest(url: String, payload: String, secret: String) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val parsedUrl = URL(url)
            connection = parsedUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            
            // Add HMAC signature if secret is provided
            if (secret.isNotBlank()) {
                val signature = generateHmacSignature(payload, secret)
                connection.setRequestProperty("X-Webhook-Signature", signature)
            }
            
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Write payload
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(payload.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            
            // Get response
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                AppLogger.d(TAG, "Webhook sent successfully for event. Response code: $responseCode")
            } else {
                AppLogger.w(TAG, "Webhook request failed with response code: $responseCode")
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sending webhook request", e)
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Generate HMAC-SHA256 signature
     */
    private fun generateHmacSignature(data: String, secret: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error generating HMAC signature", e)
            ""
        }
    }
    
    /**
     * Cleanup when service is no longer needed
     */
    fun destroy() {
        scope.cancel()
    }
    
    // ========== Convenience methods for specific events ==========
    
    /**
     * Send MCP server started webhook
     */
    fun sendMcpServerStart(serverId: String, serverName: String) {
        sendWebhook(WebhookEvent.MCP_SERVER_START, mapOf(
            "server_id" to serverId,
            "server_name" to serverName,
            "action" to "started"
        ))
    }
    
    /**
     * Send MCP server stopped webhook
     */
    fun sendMcpServerStop(serverId: String, serverName: String) {
        sendWebhook(WebhookEvent.MCP_SERVER_STOP, mapOf(
            "server_id" to serverId,
            "server_name" to serverName,
            "action" to "stopped"
        ))
    }
    
    /**
     * Send MCP server installed webhook
     */
    fun sendMcpServerInstall(serverId: String, serverName: String, version: String? = null) {
        val data = mutableMapOf<String, Any>(
            "server_id" to serverId,
            "server_name" to serverName,
            "action" to "installed"
        )
        version?.let { data["version"] = it }
        sendWebhook(WebhookEvent.MCP_SERVER_INSTALL, data)
    }
    
    /**
     * Send MCP server uninstalled webhook
     */
    fun sendMcpServerUninstall(serverId: String, serverName: String? = null) {
        val data = mutableMapOf<String, Any>(
            "server_id" to serverId,
            "action" to "uninstalled"
        )
        serverName?.let { data["server_name"] = it }
        sendWebhook(WebhookEvent.MCP_SERVER_UNINSTALL, data)
    }
    
    /**
     * Send workflow triggered webhook
     */
    fun sendWorkflowTrigger(workflowId: String, workflowName: String, triggerType: String) {
        sendWebhook(WebhookEvent.WORKFLOW_TRIGGER, mapOf(
            "workflow_id" to workflowId,
            "workflow_name" to workflowName,
            "trigger_type" to triggerType
        ))
    }
    
    /**
     * Send workflow node started webhook
     */
    fun sendWorkflowNodeStart(workflowId: String, workflowName: String, nodeId: String, nodeName: String, nodeType: String) {
        sendWebhook(WebhookEvent.WORKFLOW_NODE_START, mapOf(
            "workflow_id" to workflowId,
            "workflow_name" to workflowName,
            "node_id" to nodeId,
            "node_name" to nodeName,
            "node_type" to nodeType,
            "status" to "started"
        ))
    }
    
    /**
     * Send workflow node completed webhook
     */
    fun sendWorkflowNodeComplete(workflowId: String, workflowName: String, nodeId: String, nodeName: String, nodeType: String, success: Boolean, result: String? = null) {
        val data = mutableMapOf<String, Any>(
            "workflow_id" to workflowId,
            "workflow_name" to workflowName,
            "node_id" to nodeId,
            "node_name" to nodeName,
            "node_type" to nodeType,
            "status" to if (success) "success" else "failed"
        )
        result?.let { data["result"] = it }
        sendWebhook(WebhookEvent.WORKFLOW_NODE_COMPLETE, data)
    }
    
    /**
     * Send workflow completed webhook
     */
    fun sendWorkflowComplete(workflowId: String, workflowName: String, success: Boolean, durationMs: Long) {
        sendWebhook(WebhookEvent.WORKFLOW_COMPLETE, mapOf(
            "workflow_id" to workflowId,
            "workflow_name" to workflowName,
            "status" to if (success) "success" else "failed",
            "duration_ms" to durationMs
        ))
    }
    
    /**
     * Send package installed webhook
     */
    fun sendPackageInstall(packageId: String, packageName: String, packageType: String) {
        sendWebhook(WebhookEvent.PACKAGE_INSTALL, mapOf(
            "package_id" to packageId,
            "package_name" to packageName,
            "package_type" to packageType,
            "action" to "installed"
        ))
    }
    
    /**
     * Send package uninstalled webhook
     */
    fun sendPackageUninstall(packageId: String, packageName: String, packageType: String) {
        sendWebhook(WebhookEvent.PACKAGE_UNINSTALL, mapOf(
            "package_id" to packageId,
            "package_name" to packageName,
            "package_type" to packageType,
            "action" to "uninstalled"
        ))
    }
    
    /**
     * Send terminal command executed webhook
     */
    fun sendTerminalCommand(sessionId: String, command: String, exitCode: Int?) {
        val data = mutableMapOf<String, Any>(
            "session_id" to sessionId,
            "command" to command
        )
        exitCode?.let { data["exit_code"] = it }
        sendWebhook(WebhookEvent.TERMINAL_COMMAND, data)
    }
}
