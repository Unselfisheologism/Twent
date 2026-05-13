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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Load toolkits and connections with full pagination
    LaunchedEffect(Unit) {
        loadIntegrations(composioApi, { items ->
            toolkits = items
            isLoading = false
        }, { error ->
            errorMessage = error
            isLoading = false
        })
    }

    // Refresh function
    fun refresh() {
        isLoading = true
        errorMessage = null
        scope.launch {
            loadIntegrations(composioApi, { items ->
                toolkits = items
                isLoading = false
            }, { error ->
                errorMessage = error
                isLoading = false
            })
        }
    }

// Filter out internal/non-user-facing Composio toolkits
    // These are infrastructure/tooling toolkits, not user integrations

Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(onClick = { refresh() }) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }
    }

    // Description
    Text(
        text = stringResource(R.string.integrations_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Content
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { refresh() }) {
                    Text(stringResource(R.string.integrations_retry))
                }
            }
        }
        toolkits.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.integrations_no_toolkits),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(toolkits) { toolkit ->
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
    val connectedAccountId: String?,
    val logoUrl: String?
)

/**
 * Load ALL toolkits from Composio using cursor-based pagination.
 * Composio returns max 50 per page — loop until no next_cursor.
 * Also fetch connections to mark which toolkits are already linked.
 * Filters out internal Composio toolkits (composio, composio_search).
 */
private suspend fun loadIntegrations(
    composioApi: ComposioApiService,
    onSuccess: (List<ToolkitItem>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        if (!composioApi.isConfigured()) {
            onError("Composio API key not configured. Add COMPOSIO_API_KEY to local.properties.")
            return
        }

        // Fetch all toolkits via offset-based pagination (Composio max 50/page)
        val allToolkits = mutableListOf<com.ai.assistance.operit.data.integration.model.ToolkitDefinition>()
        var offset = 0
        var hasMore = true

        while (hasMore) {
            val result = composioApi.listToolkits(limit = 50, offset = offset)
            result.fold(
                onSuccess = { toolkits ->
                    allToolkits.addAll(toolkits)
                    // If we got fewer than 50, we've reached the end
                    hasMore = toolkits.size >= 50
                    offset += 50
                },
                onFailure = { error ->
                    onError("Failed to load toolkits: ${error.message}")
                    return
                }
            )
        }

        // Fetch all connected accounts
        val connectionsResult = composioApi.listConnections()
        val connectedToolkits = mutableMapOf<String, String>()
        connectionsResult.getOrNull()?.forEach { conn ->
            if (conn.status == "ACTIVE") {
                connectedToolkits[conn.toolkit] = conn.id
            }
        }

        // Internal toolkits to filter out
        val internalSlugs = setOf("composio", "composio_search")

        // Map to ToolkitItems, filtering internal toolkits
        val items = allToolkits
            .filter { it.name !in internalSlugs }
            .map { toolkit ->
                ToolkitItem(
                    slug = toolkit.name,
                    name = toolkit.displayName,
                    description = toolkit.description,
                    isConnected = connectedToolkits.containsKey(toolkit.name),
                    connectedAccountId = connectedToolkits[toolkit.name],
                    logoUrl = null
                )
            }
        onSuccess(items)
    } catch (e: Exception) {
        onError("Error loading integrations: ${e.message}")
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
                if (toolkit.isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.integrations_connected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
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
                if (toolkit.isConnected) {
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
