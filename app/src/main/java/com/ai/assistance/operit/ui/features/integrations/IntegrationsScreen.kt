package com.ai.assistance.operit.ui.features.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.integration.ComposioApiService
import com.ai.assistance.operit.data.integration.model.ToolkitDefinition
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.voice.utilities.UserIdManager
import kotlinx.coroutines.launch

/**
 * Integrations Screen
 * 
 * Shows available external service integrations (toolkits) from Composio.
 * Users can see which services are connected and connect/disconnect them.
 * 
 * This page uses the Composio REST API v3.1:
 * - GET /toolkits - List available toolkits
 * - GET /connected_accounts - List user's connected accounts
 * - POST /connected_accounts/link - Create auth link for user to connect
 * - DELETE /connected_accounts/{id} - Disconnect an account
 */
@Composable
fun IntegrationsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val composioApi = remember { ComposioApiService.getInstance(context) }

    // State
    var toolkits by remember { mutableStateOf<List<ToolkitItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectingToolkit by remember { mutableStateOf<String?>(null) }

    // Refresh function
    fun refresh() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = loadIntegrations(composioApi)
            result.fold(
                onSuccess = { toolkits = it },
                onFailure = { errorMessage = it.message ?: "Unknown error" }
            )
            isLoading = false
        }
    }

    // Load on first composition
    LaunchedEffect(Unit) {
        val result = loadIntegrations(composioApi)
        result.fold(
            onSuccess = { toolkits = it },
            onFailure = { errorMessage = it.message ?: "Unknown error" }
        )
        isLoading = false
    }

    // ── Single scrollable column: header IS the first LazyColumn item ──
    // This ensures the header scrolls WITH the content and the first toolkit card
    // always appears BELOW the header — no overlap possible.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Header Row ──────────────────────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.nav_integrations),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refresh() }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 2. Description ─────────────────────────────────────────────
        item(key = "description") {
            Text(
                text = stringResource(R.string.integrations_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── 3. Loading state ───────────────────────────────────────────
        if (isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // ── 4. Error state ─────────────────────────────────────────────
        errorMessage?.let { error ->
            item(key = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { refresh() }) {
                        Text(stringResource(R.string.integrations_retry))
                    }
                }
            }
        }

        // ── 5. Empty state ─────────────────────────────────────────────
        if (!isLoading && errorMessage == null && toolkits.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.integrations_no_toolkits),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 6. Toolkit cards ───────────────────────────────────────────
        items(
            items = toolkits,
            key = { it.slug }
        ) { toolkit ->
            ToolkitCard(
                toolkit = toolkit,
                onConnect = {
                    connectingToolkit = toolkit.slug
                    scope.launch {
                        connectToolkit(context, composioApi, toolkit.slug) { success, error ->
                            connectingToolkit = null
                            if (success) {
                                refresh()
                            } else {
                                errorMessage = error
                            }
                        }
                    }
                },
                onDisconnect = { accountId ->
                    scope.launch {
                        disconnectToolkit(composioApi, accountId) { success, error ->
                            if (success) {
                                refresh()
                            } else {
                                errorMessage = error
                            }
                        }
                    }
                },
                isConnecting = connectingToolkit == toolkit.slug
            )
        }
    }
}

/**
 * Data class for toolkit items displayed in the UI
 */
data class ToolkitItem(
    val slug: String,
    val name: String,
    val description: String,
    val isConnected: Boolean,
    val isStale: Boolean,      // true = connected but auth token is stale/expired/revoked
    val connectedAccountId: String?,
    val logoUrl: String?
)

/**
 * Load all toolkits from Composio with full pagination.
 * Also fetch connections to mark which toolkits are already linked.
 * Filters out internal Composio toolkits (composio, composio_search).
 * Uses suspendCoroutine so the coroutine always completes (no callback ambiguity).
 */
private suspend fun loadIntegrations(
    composioApi: ComposioApiService
): Result<List<ToolkitItem>> {
    if (!composioApi.isConfigured()) {
        val msg = "Composio API key not configured. Add COMPOSIO_API_KEY to local.properties."
        AppLogger.e("Integrations", msg)
        return Result.failure(Exception(msg))
    }

    return try {
        // Fetch all toolkits via cursor-based pagination
        // Composio REST API uses next_cursor (NOT offset) for pagination
        val allToolkits = mutableListOf<com.ai.assistance.operit.data.integration.model.ToolkitDefinition>()
        var nextCursor: String? = null

        // Safety: limit max pages to 50 (max 2500 toolkits — well beyond Composio's actual count)
        var pageCount = 0
        val maxPages = 50
        var lastCursor: String? = null
        var stuckCount = 0

        do {
            val rawResponse = composioApi.fetchToolkitsRaw(limit = 50, nextCursor = nextCursor)
            if (rawResponse == null) {
                AppLogger.e("Integrations", "Failed to fetch toolkits page (null response)")
                // Fall back: if we got some toolkits, return them instead of failing
                if (allToolkits.isNotEmpty()) {
                    AppLogger.w("Integrations", "Returning ${allToolkits.size} toolkits fetched before failure")
                    break
                }
                return Result.failure(Exception("Failed to fetch toolkits"))
            }

            // Log raw response for debugging (first 500 chars to avoid log spam)
            AppLogger.d("Integrations", "Raw response (${rawResponse.length} chars): ${rawResponse.take(500)}")

            val toolkits = composioApi.parseToolkits(rawResponse)
            allToolkits.addAll(toolkits)
            nextCursor = composioApi.getToolkitsNextCursor(rawResponse)
            
            AppLogger.d("Integrations", "Parsed ${toolkits.size} toolkits, next_cursor=$nextCursor, total=${allToolkits.size}")

            // Guard against infinite loop: if cursor keeps repeating, stop
            if (nextCursor == lastCursor && nextCursor != null) {
                stuckCount++
                if (stuckCount >= 2) {
                    AppLogger.w("Integrations", "Cursor stuck on '$nextCursor', stopping pagination after ${pageCount + 1} pages")
                    nextCursor = null
                }
            } else {
                stuckCount = 0
            }
            lastCursor = nextCursor

            pageCount++

            if (pageCount >= maxPages) {
                AppLogger.w("Integrations", "Max pages ($maxPages) reached, stopping pagination")
                nextCursor = null
            }
        } while (nextCursor != null)

        // Fetch all connected accounts
        val connectionsResult = composioApi.listConnections()
        val connectedToolkits = mutableMapOf<String, String>()   // normalizedKey -> accountId
        val staleToolkits    = mutableMapOf<String, String>()   // normalizedKey -> accountId (non-ACTIVE)

        connectionsResult.getOrNull()?.forEach { conn ->
            val normalizedKey = conn.toolkit.lowercase()
                .removePrefix("composio_")
                .removePrefix("composio-")
                .trim()
            when {
                conn.status == "ACTIVE" -> connectedToolkits[normalizedKey] = conn.id
                // Treat EXPIRED / REVOKED / ERROR / PENDING as stale (auth needs reconnection)
                else -> staleToolkits[normalizedKey] = conn.id
            }
        }

        // Internal toolkits to filter out
        val internalSlugs = setOf("composio", "composio_search", "composio-search")

        // Map to ToolkitItems, filtering internal toolkits.
        // Also do case-insensitive matching since Composio API may return "composio_gmail"
        // while the toolkit name is just "gmail".
        val items = allToolkits
            .filter { it.name.lowercase() !in internalSlugs.map { s -> s.lowercase() } }
            .map { toolkit ->
                val normalizedName = toolkit.name.lowercase()
                val isStale = staleToolkits.containsKey(normalizedName)
                ToolkitItem(
                    slug = toolkit.name,
                    name = toolkit.displayName,
                    description = toolkit.description,
                    isConnected = connectedToolkits.containsKey(normalizedName) || isStale,
                    isStale = isStale,
                    connectedAccountId = connectedToolkits[normalizedName] ?: staleToolkits[normalizedName],
                    logoUrl = null
                )
            }
        AppLogger.d("Integrations", "Loaded ${items.size} toolkits (filtered from ${allToolkits.size} total)")
        Result.success(items)
    } catch (e: Exception) {
        AppLogger.e("Integrations", "Error loading integrations: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Connect to a toolkit via Composio
 * 
 * Uses the Composio "Connect Links" flow:
 * 1. Get or create an auth config for the toolkit (uses Composio managed auth)
 * 2. POST to /connected_accounts/link with auth_config_id and user_id
 * 3. Response gives redirect_url (Connect Link - e.g. connect.composio.dev/link/ln_abc123)
 * 4. User opens URL which redirects to the actual OAuth provider (GitHub, Google, etc.)
 * 5. Composio handles the full OAuth flow automatically
 */
private suspend fun connectToolkit(
    context: Context,
    composioApi: ComposioApiService,
    toolkitSlug: String,
    callback: (Boolean, String?) -> Unit
) {
    try {
        val userId = UserIdManager(context).getOrCreateUserId()
        AppLogger.d("IntegrationsScreen", "Connecting toolkit '$toolkitSlug' for user '$userId'")
        
        // Step 1: Get or create an auth config, then create a connect link
        val authConfigResult = composioApi.getOrCreateAuthConfig(toolkitSlug)
        val authConfigId = authConfigResult.getOrNull()
        
        if (authConfigId == null) {
            callback(false, "Failed to get auth config for $toolkitSlug: ${authConfigResult.exceptionOrNull()?.message}")
            return
        }
        
        AppLogger.d("IntegrationsScreen", "Got auth config '$authConfigId' for toolkit '$toolkitSlug'")
        
        // Step 2: Create a Connect Link
        val authLinkResult = composioApi.createAuthLink(
            authConfigId = authConfigId,
            userId = userId
        )
        
        authLinkResult.fold(
            onSuccess = { authLink ->
                val redirectUrl = authLink.redirectUrl
                if (redirectUrl.isNotBlank()) {
                    AppLogger.d("IntegrationsScreen", "Opening Connect Link: $redirectUrl")
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(redirectUrl)
                    }
                    context.startActivity(intent)
                    callback(true, null)
                } else {
                    callback(false, "Received empty redirect URL from Composio")
                }
            },
            onFailure = { error ->
                AppLogger.e("IntegrationsScreen", "Failed to create auth link: ${error.message}")
                callback(false, "Failed to create auth link: ${error.message}")
            }
        )
    } catch (e: Exception) {
        AppLogger.e("IntegrationsScreen", "Failed to connect toolkit", e)
        callback(false, "Failed to connect: ${e.message}")
    }
}

/**
 * Disconnect a toolkit
 */
private suspend fun disconnectToolkit(
    composioApi: ComposioApiService,
    accountId: String,
    callback: (Boolean, String?) -> Unit
) {
    try {
        val result = composioApi.disconnectConnection(accountId)
        result.fold(
            onSuccess = {
                AppLogger.d("IntegrationsScreen", "Disconnected account: $accountId")
                callback(true, null)
            },
            onFailure = { error ->
                AppLogger.e("IntegrationsScreen", "Failed to disconnect: ${error.message}")
                callback(false, "Failed to disconnect: ${error.message}")
            }
        )
    } catch (e: Exception) {
        callback(false, "Failed to disconnect: ${e.message}")
    }
}

/**
 * Card displaying a toolkit/integration
 */
@Composable
private fun ToolkitCard(
    toolkit: ToolkitItem,
    onConnect: () -> Unit,
    onDisconnect: (String) -> Unit,
    isConnecting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolkit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (toolkit.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = toolkit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
// Connection status
                val isStale = toolkit.isStale
                val (statusIcon, statusText, statusColor) = when {
                    isStale -> Triple(
                        Icons.Filled.Warning,
                        stringResource(R.string.integrations_stale),
                        MaterialTheme.colorScheme.error
                    )
                    toolkit.isConnected -> Triple(
                        Icons.Filled.CheckCircle,
                        stringResource(R.string.integrations_connected),
                        MaterialTheme.colorScheme.primary
                    )
                    else -> Triple(null, "", Color.Transparent)
                }
                if (statusIcon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = if (isStale) "Reconnect needed" else "Connected",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
if (toolkit.isConnected || isStale) {
                    if (isStale) {
                        Button(
                            onClick = {
                                toolkit.connectedAccountId?.let { onDisconnect(it) }
                                onConnect()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.integrations_reconnect))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                toolkit.connectedAccountId?.let { onDisconnect(it) }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.integrations_disconnect))
                        }
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !isConnecting
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.integrations_connect))
                    }
                }
            }
        }
    }
}
