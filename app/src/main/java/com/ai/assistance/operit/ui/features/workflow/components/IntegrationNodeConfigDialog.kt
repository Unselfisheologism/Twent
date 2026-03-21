package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.integration.model.ToolDefinition
import com.ai.assistance.operit.data.integration.model.ToolParameter
import com.ai.assistance.operit.data.integration.model.ToolkitDefinition
import com.ai.assistance.operit.data.model.IntegrationNode
import com.ai.assistance.operit.data.model.IntegrationNodeConstants
import com.ai.assistance.operit.data.model.IntegrationWebhookConfig
import com.ai.assistance.operit.data.model.IntegrationMcpServerConfig
import com.ai.assistance.operit.data.model.ParameterValue
import java.util.UUID

/**
 * Integration Node Configuration Dialog
 * 
 * Provides a comprehensive interface for configuring integration nodes in the workflow builder.
 * 
 * Features:
 * - Integration type selector (Tool/Webhook/MCP/OAuth)
 * - Toolkit/action picker for Composio tools
 * - Webhook configuration form
 * - MCP server configuration
 * - OAuth account selection
 * - Parameter input with static values or node reference binding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationNodeConfigDialog(
    node: IntegrationNode?,
    availableToolkits: List<ToolkitDefinition> = emptyList(),
    availableActions: List<ToolDefinition> = emptyList(),
    availableAccounts: List<ConnectedAccountInfo> = emptyList(),
    availableMcpServers: List<McpServerInfo> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (IntegrationNode) -> Unit
) {
    // Create a new node or use existing
    var currentNode by remember {
        mutableStateOf(node ?: createDefaultIntegrationNode())
    }

    // UI State
    var nodeName by remember { mutableStateOf(currentNode.name) }
    var nodeDescription by remember { mutableStateOf(currentNode.description) }
    var integrationType by remember { mutableStateOf(currentNode.integrationType) }
    var selectedToolkit by remember { mutableStateOf(currentNode.toolkit) }
    var selectedAction by remember { mutableStateOf(currentNode.actionId) }
    var parameters by remember { mutableStateOf(currentNode.parameters) }
    var selectedAccountId by remember { mutableStateOf(currentNode.accountId) }
    var enabled by remember { mutableStateOf(currentNode.enabled) }
    var timeout by remember { mutableStateOf(currentNode.timeout.toString()) }

    // Webhook config state
    var webhookUrl by remember { mutableStateOf(currentNode.webhookConfig?.url ?: "") }
    var webhookMethod by remember { mutableStateOf(currentNode.webhookConfig?.method ?: "GET") }
    var webhookHeaders by remember { 
        mutableStateOf(currentNode.webhookConfig?.headers?.toMutableMap() ?: mutableMapOf<String, String>())
    }
    var webhookAuthType by remember { mutableStateOf(getAuthTypeFromWebhook(currentNode.webhookConfig)) }
    var webhookApiKey by remember { mutableStateOf("") }
    var webhookBearerToken by remember { mutableStateOf("") }
    var webhookBasicUsername by remember { mutableStateOf("") }
    var webhookBasicPassword by remember { mutableStateOf("") }
    var webhookBody by remember { mutableStateOf("") }

    // MCP config state
    var mcpServerName by remember { mutableStateOf(currentNode.mcpServerConfig?.serverName ?: "") }
    var mcpServerId by remember { mutableStateOf(currentNode.mcpServerConfig?.serverId ?: "") }
    var mcpToolName by remember { mutableStateOf(currentNode.mcpServerConfig?.toolName ?: "") }
    var mcpParameters by remember { 
        mutableStateOf(currentNode.mcpServerConfig?.parameters?.toMutableMap() ?: mutableMapOf<String, String>())
    }

    // Dropdown states
    var toolkitExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var mcpServerExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var authTypeExpanded by remember { mutableStateOf(false) }

    // Get available actions for selected toolkit
    val filteredActions = remember(selectedToolkit, availableActions) {
        if (selectedToolkit.isNotEmpty()) {
            availableActions.filter { it.toolkit == selectedToolkit }
        } else {
            availableActions
        }
    }

    // Get selected action definition for parameter inputs
    val selectedActionDefinition = remember(selectedAction, filteredActions) {
        filteredActions.find { it.name == selectedAction }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getIntegrationIcon(integrationType),
                    contentDescription = null,
                    tint = getIntegrationColor(integrationType)
                )
                Text(
                    text = if (node != null) stringResource(R.string.integration_dialog_edit_title)
                           else stringResource(R.string.integration_dialog_create_title)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Node name
                OutlinedTextField(
                    value = nodeName,
                    onValueChange = { nodeName = it },
                    label = { Text(stringResource(R.string.workflow_node_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Node description
                OutlinedTextField(
                    value = nodeDescription,
                    onValueChange = { nodeDescription = it },
                    label = { Text(stringResource(R.string.workflow_node_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                HorizontalDivider()

                // T-025: Integration Type Selector
                Text(
                    text = stringResource(R.string.integration_type_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                IntegrationTypeSelector(
                    selectedType = integrationType,
                    onTypeSelected = { 
                        integrationType = it
                        // Reset relevant fields when type changes
                        when (it) {
                            IntegrationNodeConstants.TYPE_TOOL -> {
                                selectedToolkit = ""
                                selectedAction = ""
                                parameters = emptyMap()
                            }
                            IntegrationNodeConstants.TYPE_WEBHOOK -> {
                                webhookUrl = ""
                                webhookMethod = "GET"
                                // Note: webhookHeaders is already initialized with remember and mutableStateOf
                            }
                            IntegrationNodeConstants.TYPE_MCP -> {
                                mcpServerName = ""
                                mcpServerId = ""
                                mcpToolName = ""
                                // Note: mcpParameters is already initialized with remember and mutableStateOf
                            }
                            IntegrationNodeConstants.TYPE_OAUTH -> {
                                selectedAccountId = null
                            }
                        }
                    }
                )

                HorizontalDivider()

                // T-026: Toolkit/Action Picker (for Tool type)
                if (integrationType == IntegrationNodeConstants.TYPE_TOOL) {
                    ToolConfigSection(
                        selectedToolkit = selectedToolkit,
                        selectedAction = selectedAction,
                        availableToolkits = availableToolkits,
                        filteredActions = filteredActions,
                        parameters = parameters,
                        selectedActionDefinition = selectedActionDefinition,
                        toolkitExpanded = toolkitExpanded,
                        actionExpanded = actionExpanded,
                        onToolkitExpandedChange = { toolkitExpanded = it },
                        onActionExpandedChange = { actionExpanded = it },
                        onToolkitSelected = { 
                            selectedToolkit = it
                            selectedAction = ""
                            parameters = emptyMap()
                        },
                        onActionSelected = { 
                            selectedAction = it
                            parameters = emptyMap() // Reset parameters when action changes
                        },
                        onParameterChange = { paramName, value ->
                            parameters = parameters + (paramName to value)
                        }
                    )
                }

                // T-027: Webhook Configuration (for Webhook type)
                if (integrationType == IntegrationNodeConstants.TYPE_WEBHOOK) {
                    WebhookConfigSection(
                        url = webhookUrl,
                        method = webhookMethod,
                        headers = webhookHeaders,
                        authType = webhookAuthType,
                        apiKey = webhookApiKey,
                        bearerToken = webhookBearerToken,
                        basicUsername = webhookBasicUsername,
                        basicPassword = webhookBasicPassword,
                        body = webhookBody,
                        methodExpanded = methodExpanded,
                        authTypeExpanded = authTypeExpanded,
                        onUrlChange = { webhookUrl = it },
                        onMethodChange = { webhookMethod = it },
                        onHeadersChange = { webhookHeaders = it }
                        onAuthTypeChange = { webhookAuthType = it },
                        onApiKeyChange = { webhookApiKey = it },
                        onBearerTokenChange = { webhookBearerToken = it },
                        onBasicUsernameChange = { webhookBasicUsername = it },
                        onBasicPasswordChange = { webhookBasicPassword = it },
                        onBodyChange = { webhookBody = it },
                        onMethodExpandedChange = { methodExpanded = it },
                        onAuthTypeExpandedChange = { authTypeExpanded = it }
                    )
                }

                // MCP Configuration (for MCP type)
                if (integrationType == IntegrationNodeConstants.TYPE_MCP) {
                    McpConfigSection(
                        serverName = mcpServerName,
                        serverId = mcpServerId,
                        toolName = mcpToolName,
                        parameters = mcpParameters,
                        availableMcpServers = availableMcpServers,
                        serverExpanded = mcpServerExpanded,
                        onServerExpandedChange = { mcpServerExpanded = it },
                        onServerSelected = { name, id ->
                            mcpServerName = name
                            mcpServerId = id ?: ""
                        },
                        onToolNameChange = { mcpToolName = it },
                        onParametersChange = { mcpParameters = it.toMutableMap() }
                    )
                }

                // OAuth Configuration (for OAuth type)
                if (integrationType == IntegrationNodeConstants.TYPE_OAUTH) {
                    OAuthConfigSection(
                        selectedAccountId = selectedAccountId,
                        availableAccounts = availableAccounts,
                        accountExpanded = accountExpanded,
                        onAccountExpandedChange = { accountExpanded = it },
                        onAccountSelected = { selectedAccountId = it }
                    )
                }

                HorizontalDivider()

                // Common settings
                Text(
                    text = stringResource(R.string.workflow_common_settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Timeout
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text(stringResource(R.string.integration_timeout_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("ms") }
                )

                // Enable/Disable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.workflow_node_enabled_label))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedNode = currentNode.copy(
                        name = nodeName,
                        description = nodeDescription,
                        integrationType = integrationType,
                        toolkit = selectedToolkit,
                        actionId = selectedAction,
                        parameters = parameters,
                        accountId = selectedAccountId,
                        enabled = enabled,
                        timeout = timeout.toLongOrNull() ?: 30000L,
                        webhookConfig = if (integrationType == IntegrationNodeConstants.TYPE_WEBHOOK) {
                            IntegrationWebhookConfig(
                                id = currentNode.webhookConfig?.id ?: UUID.randomUUID().toString(),
                                url = webhookUrl,
                                method = webhookMethod,
                                headers = webhookHeaders,
                                apiKeyRequired = webhookAuthType != "none"
                            )
                        } else null,
                        mcpServerConfig = if (integrationType == IntegrationNodeConstants.TYPE_MCP) {
                            IntegrationMcpServerConfig(
                                serverName = mcpServerName,
                                serverId = mcpServerId,
                                toolName = mcpToolName,
                                parameters = mcpParameters
                            )
                        } else null
                    )
                    onConfirm(updatedNode)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

/**
 * T-025: Integration Type Selector
 * Radio buttons or segmented control for selecting integration type
 */
@Composable
private fun IntegrationTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val types = listOf(
        IntegrationNodeConstants.TYPE_TOOL to Pair(Icons.Default.Build, R.string.integration_type_tool),
        IntegrationNodeConstants.TYPE_WEBHOOK to Pair(Icons.Outlined.Link, R.string.integration_type_webhook),
        IntegrationNodeConstants.TYPE_MCP to Pair(Icons.Outlined.Extension, R.string.integration_type_mcp),
        IntegrationNodeConstants.TYPE_OAUTH to Pair(Icons.Outlined.Security, R.string.integration_type_oauth)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { (type, iconAndLabel) ->
            val (icon, labelRes) = iconAndLabel
            val isSelected = selectedType == type
            val typeColor = getIntegrationColor(type)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTypeSelected(type) }
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp,
                            typeColor,
                            RoundedCornerShape(8.dp)
                        ) else Modifier
                    ),
                color = if (isSelected) typeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onTypeSelected(type) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = typeColor
                        )
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) typeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) typeColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * T-026: Toolkit/Action Picker
 * Dropdown for selecting Composio toolkit and action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConfigSection(
    selectedToolkit: String,
    selectedAction: String,
    availableToolkits: List<ToolkitDefinition>,
    filteredActions: List<ToolDefinition>,
    parameters: Map<String, ParameterValue>,
    selectedActionDefinition: ToolDefinition?,
    toolkitExpanded: Boolean,
    actionExpanded: Boolean,
    onToolkitExpandedChange: (Boolean) -> Unit,
    onActionExpandedChange: (Boolean) -> Unit,
    onToolkitSelected: (String) -> Unit,
    onActionSelected: (String) -> Unit,
    onParameterChange: (String, ParameterValue) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.integration_tool_config_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // Toolkit selector
        ExposedDropdownMenuBox(
            expanded = toolkitExpanded,
            onExpandedChange = onToolkitExpandedChange
        ) {
            OutlinedTextField(
                value = availableToolkits.find { it.name == selectedToolkit }?.displayName 
                        ?: selectedToolkit 
                        ?: stringResource(R.string.integration_select_toolkit),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.integration_toolkit_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toolkitExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = toolkitExpanded,
                onDismissRequest = { onToolkitExpandedChange(false) }
            ) {
                if (availableToolkits.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.integration_no_toolkits)) },
                        onClick = { onToolkitExpandedChange(false) },
                        enabled = false
                    )
                } else {
                    availableToolkits.forEach { toolkit ->
                        DropdownMenuItem(
                            text = { Text(toolkit.displayName) },
                            onClick = {
                                onToolkitSelected(toolkit.name)
                                onToolkitExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        // Action selector
        if (selectedToolkit.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = actionExpanded,
                onExpandedChange = onActionExpandedChange
            ) {
                OutlinedTextField(
                    value = filteredActions.find { it.name == selectedAction }?.description?.take(50)
                            ?: selectedAction
                            ?: stringResource(R.string.integration_select_action),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.integration_action_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { onActionExpandedChange(false) }
                ) {
                    if (filteredActions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.integration_no_actions)) },
                            onClick = { onActionExpandedChange(false) },
                            enabled = false
                        )
                    } else {
                        filteredActions.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = action.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (action.description.isNotEmpty()) {
                                            Text(
                                                text = action.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onActionSelected(action.name)
                                    onActionExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Parameter inputs
        if (selectedActionDefinition != null && selectedActionDefinition.inputSchema.properties.isNotEmpty()) {
            Text(
                text = stringResource(R.string.integration_parameters_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            selectedActionDefinition.inputSchema.properties.forEach { (paramName, paramDef) ->
                val currentValue = parameters[paramName]
                val isNodeReference = currentValue is ParameterValue.NodeReference

                ParameterInputField(
                    paramName = paramName,
                    paramDefinition = paramDef,
                    value = (currentValue as? ParameterValue.StaticValue)?.value ?: "",
                    isNodeReference = isNodeReference,
                    referenceNodeId = (currentValue as? ParameterValue.NodeReference)?.nodeId ?: "",
                    onValueChange = { newValue ->
                        if (isNodeReference) {
                            onParameterChange(paramName, ParameterValue.NodeReference(newValue))
                        } else {
                            onParameterChange(paramName, ParameterValue.StaticValue(newValue))
                        }
                    },
                    onTypeChange = { useReference ->
                        if (useReference) {
                            onParameterChange(paramName, ParameterValue.NodeReference(""))
                        } else {
                            onParameterChange(paramName, ParameterValue.StaticValue(""))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Parameter input field with static value or node reference support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterInputField(
    paramName: String,
    paramDefinition: ToolParameter,
    value: String,
    isNodeReference: Boolean,
    referenceNodeId: String,
    onValueChange: (String) -> Unit,
    onTypeChange: (Boolean) -> Unit
) {
    var showReferenceToggle by remember { mutableStateOf(isNodeReference) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = paramName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.integration_param_static),
                    fontSize = 12.sp,
                    color = if (!showReferenceToggle) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = showReferenceToggle,
                    onCheckedChange = { 
                        showReferenceToggle = it
                        onTypeChange(it)
                    },
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = stringResource(R.string.integration_param_reference),
                    fontSize = 12.sp,
                    color = if (showReferenceToggle) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (paramDefinition.description.isNotEmpty()) {
            Text(
                text = paramDefinition.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(paramDefinition.default ?: "") },
            leadingIcon = if (showReferenceToggle) {
                { Icon(Icons.Default.Link, contentDescription = null) }
            } else null
        )

        // Enum values if available
        if (!paramDefinition.enum.isNullOrEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                paramDefinition.enum.take(5).forEach { enumValue ->
                    FilterChip(
                        selected = value == enumValue,
                        onClick = { onValueChange(enumValue) },
                        label = { Text(enumValue, fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}

/**
 * T-027: Webhook Configuration Form
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookConfigSection(
    url: String,
    method: String,
    headers: Map<String, String>,
    authType: String,
    apiKey: String,
    bearerToken: String,
    basicUsername: String,
    basicPassword: String,
    body: String,
    methodExpanded: Boolean,
    authTypeExpanded: Boolean,
    onUrlChange: (String) -> Unit,
    onMethodChange: (String) -> Unit,
    onHeadersChange: (MutableMap<String, String>) -> Unit,
    onAuthTypeChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBearerTokenChange: (String) -> Unit,
    onBasicUsernameChange: (String) -> Unit,
    onBasicPasswordChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onMethodExpandedChange: (Boolean) -> Unit,
    onAuthTypeExpandedChange: (Boolean) -> Unit
) {
    val httpMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    val authTypes = listOf(
        "none" to R.string.integration_auth_none,
        "api_key" to R.string.integration_auth_api_key,
        "bearer" to R.string.integration_auth_bearer,
        "basic" to R.string.integration_auth_basic
    )
    val showBody = method in listOf("POST", "PUT", "PATCH")

    var newHeaderKey by remember { mutableStateOf("") }
    var newHeaderValue by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.integration_webhook_config_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // URL input
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.integration_webhook_url_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://api.example.com/endpoint") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            isError = url.isNotEmpty() && !url.startsWith("http")
        )

        // HTTP Method selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = methodExpanded,
                onExpandedChange = onMethodExpandedChange,
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = method,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.integration_webhook_method_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { onMethodExpandedChange(false) }
                ) {
                    httpMethods.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                onMethodChange(m)
                                onMethodExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        // Headers section
        Text(
            text = stringResource(R.string.integration_webhook_headers_label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        headers.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        val newHeaders = headers.toMutableMap()
                        newHeaders.remove(key)
                        onHeadersChange(newHeaders)
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                }
            }
        }

        // Add new header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newHeaderKey,
                onValueChange = { newHeaderKey = it },
                label = { Text(stringResource(R.string.integration_header_key)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = newHeaderValue,
                onValueChange = { newHeaderValue = it },
                label = { Text(stringResource(R.string.integration_header_value)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    if (newHeaderKey.isNotBlank()) {
                        val newHeaders = headers.toMutableMap()
                        newHeaders[newHeaderKey] = newHeaderValue
                        onHeadersChange(newHeaders)
                        newHeaderKey = ""
                        newHeaderValue = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        HorizontalDivider()

        // Authentication type
        Text(
            text = stringResource(R.string.integration_webhook_auth_label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        ExposedDropdownMenuBox(
            expanded = authTypeExpanded,
            onExpandedChange = onAuthTypeExpandedChange
        ) {
            OutlinedTextField(
                value = stringResource(authTypes.find { it.first == authType }?.second ?: R.string.integration_auth_none),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.integration_auth_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = authTypeExpanded,
                onDismissRequest = { onAuthTypeExpandedChange(false) }
            ) {
                authTypes.forEach { (type, labelRes) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        onClick = {
                            onAuthTypeChange(type)
                            onAuthTypeExpandedChange(false)
                        }
                    )
                }
            }
        }

        // Auth fields based on type
        when (authType) {
            "api_key" -> {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.integration_api_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            "bearer" -> {
                OutlinedTextField(
                    value = bearerToken,
                    onValueChange = onBearerTokenChange,
                    label = { Text(stringResource(R.string.integration_bearer_token_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            "basic" -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = basicUsername,
                        onValueChange = onBasicUsernameChange,
                        label = { Text(stringResource(R.string.integration_username_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = basicPassword,
                        onValueChange = onBasicPasswordChange,
                        label = { Text(stringResource(R.string.integration_password_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }

        // Body template editor
        if (showBody) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.integration_webhook_body_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text(stringResource(R.string.integration_body_placeholder)) }
            )
            Text(
                text = stringResource(R.string.integration_body_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * MCP Server Configuration Section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpConfigSection(
    serverName: String,
    serverId: String?,
    toolName: String,
    parameters: Map<String, String>,
    availableMcpServers: List<McpServerInfo>,
    serverExpanded: Boolean,
    onServerExpandedChange: (Boolean) -> Unit,
    onServerSelected: (String, String?) -> Unit,
    onToolNameChange: (String) -> Unit,
    onParametersChange: (Map<String, String>) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.integration_mcp_config_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // MCP Server selector
        ExposedDropdownMenuBox(
            expanded = serverExpanded,
            onExpandedChange = onServerExpandedChange
        ) {
            OutlinedTextField(
                value = serverName.ifEmpty { stringResource(R.string.integration_select_mcp_server) },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.integration_mcp_server_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = serverExpanded,
                onDismissRequest = { onServerExpandedChange(false) }
            ) {
                if (availableMcpServers.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.integration_no_mcp_servers)) },
                        onClick = { onServerExpandedChange(false) },
                        enabled = false
                    )
                } else {
                    availableMcpServers.forEach { server ->
                        DropdownMenuItem(
                            text = { Text(server.name) },
                            onClick = {
                                onServerSelected(server.name, server.id)
                                onServerExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        // Tool name input
        OutlinedTextField(
            value = toolName,
            onValueChange = onToolNameChange,
            label = { Text(stringResource(R.string.integration_mcp_tool_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("tool_name") }
        )
    }
}

/**
 * OAuth Account Configuration Section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OAuthConfigSection(
    selectedAccountId: String?,
    availableAccounts: List<ConnectedAccountInfo>,
    accountExpanded: Boolean,
    onAccountExpandedChange: (Boolean) -> Unit,
    onAccountSelected: (String?) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.integration_oauth_config_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.integration_oauth_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Account selector
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = onAccountExpandedChange
        ) {
            OutlinedTextField(
                value = availableAccounts.find { it.id == selectedAccountId }?.displayName
                        ?: stringResource(R.string.integration_select_account),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.integration_account_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = accountExpanded,
                onDismissRequest = { onAccountExpandedChange(false) }
            ) {
                if (availableAccounts.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.integration_no_accounts)) },
                        onClick = { onAccountExpandedChange(false) },
                        enabled = false
                    )
                } else {
                    availableAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(account.displayName)
                                    Text(
                                        text = account.accountType,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onAccountSelected(account.id)
                                onAccountExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to create a default integration node
 */
private fun createDefaultIntegrationNode(): IntegrationNode {
    return IntegrationNode(
        id = UUID.randomUUID().toString(),
        name = "",
        integrationType = IntegrationNodeConstants.TYPE_TOOL
    )
}

/**
 * Get integration color based on type
 */
private fun getIntegrationColor(type: String): Color {
    return when (type) {
        IntegrationNodeConstants.TYPE_TOOL -> Color(0xFF2196F3)
        IntegrationNodeConstants.TYPE_WEBHOOK -> Color(0xFF00BCD4)
        IntegrationNodeConstants.TYPE_MCP -> Color(0xFF9C27B0)
        IntegrationNodeConstants.TYPE_OAUTH -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }
}

/**
 * Get integration icon based on type
 */
private fun getIntegrationIcon(type: String): ImageVector {
    return when (type) {
        IntegrationNodeConstants.TYPE_TOOL -> Icons.Default.Build
        IntegrationNodeConstants.TYPE_WEBHOOK -> Icons.Outlined.Link
        IntegrationNodeConstants.TYPE_MCP -> Icons.Outlined.Extension
        IntegrationNodeConstants.TYPE_OAUTH -> Icons.Outlined.Security
        else -> Icons.Default.Help
    }
}

/**
 * Get auth type from webhook config
 */
private fun getAuthTypeFromWebhook(config: IntegrationWebhookConfig?): String {
    if (config == null) return "none"
    return when {
        config.apiKeyRequired -> "api_key"
        else -> "none"
    }
}

/**
 * Connected account info for display
 */
data class ConnectedAccountInfo(
    val id: String,
    val displayName: String,
    val accountType: String,
    val connectedAt: Long = 0L
)

/**
 * MCP Server info for display
 */
data class McpServerInfo(
    val id: String?,
    val name: String,
    val description: String = ""
)
