# Webhook & Integration System Design

**Date:** 2026-03-19  
**Status:** Pending User Approval  
**Author:** Operit AI Development

## 1. Overview

This document describes the design for adding Zapier-like integration capabilities to the Operit AI mobile app. The system enables workflow nodes to connect to external services (GitHub, Linear, Notion, Slack, etc.) via Composio, and also supports custom webhook endpoints for services not available on Composio.

## 2. Goals

- Add a new "Integration" node type to the workflow builder
- Support Composio-powered tools (1000+ external apps)
- Support custom webhook/HTTP endpoints with authentication
- Unified "Webhooks & Integrations" settings area
- Per-user OAuth connections (single-user app, tokens stored locally)

## 3. Non-Goals

- Real-time inbound webhooks (receiving events from external services)
- Multi-user collaboration
- Server-side processing

## 4. Architecture

### 4.1 Components

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
│  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │ Workflow Editor │  │ Settings - Webhooks & Integrations│  │
│  │ - Integration   │  │ - Connected Accounts Tab        │  │
│  │   Node          │  │ - Custom Webhooks Tab           │  │
│  └─────────────────┘  │ - Outbound Events Tab           │  │
│                       └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Domain Layer                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ IntegrationNodeExecutor (in core/workflow)           │    │
│  │ - Orchestrates execution                             │    │
│  │ - Handles node configuration                         │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  ┌──────────────────┐  ┌────────────────────────────────┐  │
│  │ComposioApiService │  │ IntegrationRepository          │  │
│  │ - REST API client │  │ - ConnectedAccount CRUD        │  │
│  │ - Tool execution  │  │ - CustomWebhook CRUD          │  │
│  └──────────────────┘  └────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │ EncryptedStorage       │  │ HttpRequestExecutor       │  │
│  │ - OAuth tokens         │  │ - Custom HTTP requests   │  │
│  │ - API keys             │  │ - Auth headers           │  │
│  └────────────────────────┘  └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Execution Logic Location:** 
The `IntegrationNodeExecutor` class will be in `core/workflow/` package (alongside existing `WorkflowExecutor`). It orchestrates the execution by calling `ComposioApiService` or `HttpRequestExecutor` based on node configuration.

### 4.2 Package Structure

```
app/src/main/java/com/ai/assistance/operit/
├── data/
│   ├── integration/
│   │   ├── ComposioApiService.kt      # Composio REST API client
│   │   ├── IntegrationRepository.kt    # Data operations
│   │   ├── HttpRequestExecutor.kt      # Custom HTTP execution
│   │   ├── model/
│   │   │   ├── ConnectedAccount.kt     # OAuth account model
│   │   │   ├── CustomWebhook.kt        # Custom webhook config
│   │   │   ├── ToolDefinition.kt       # Tool schema from Composio
│   │   │   └── ToolAction.kt          # Available actions
│   │   └── preferences/
│   │       └── IntegrationPreferences.kt
│   └── webhook/
│       ├── WebhookService.kt           # Existing - keep as-is
│       └── WebhookPreferences.kt       # Existing - keep as-is
├── domain/
│   └── usecase/
│       ├── ExecuteIntegrationNode.kt   # Business logic
│       └── ManageConnections.kt        # OAuth management
└── ui/
    └── features/
        ├── workflow/
        │   └── components/
        │       └── nodes/
        │           └── IntegrationNode.kt
        └── settings/
            └── screens/
                └── WebhooksIntegrationsScreen.kt
```

## 5. Data Models

### 5.1 ConnectedAccount

```kotlin
data class ConnectedAccount(
    val id: String,
    val toolkit: String,          // e.g., "github", "linear", "notion"
    val accountName: String,       // User's account name/email
    val accountId: String,         // Composio's account ID
    val connectedAt: Long,         // Timestamp
    val status: ConnectionStatus  // ACTIVE, EXPIRED, ERROR
)
```

### 5.2 CustomWebhook

```kotlin
data class CustomWebhook(
    val id: String,
    val name: String,
    val url: String,
    val method: HttpMethod,       // GET, POST, PUT, DELETE, PATCH
    val headers: Map<String, String>,
    val body: String?,             // For POST/PUT/PATCH
    val description: String?
)
```

### 5.3 IntegrationNodeConfig

```kotlin
data class IntegrationNodeConfig(
    val nodeId: String,
    val integrationType: IntegrationType,  // COMPOSIO, CUSTOM_WEBHOOK
    
    // For Composio
    val toolkit: String?,         // e.g., "github"
    val toolName: String?,        // e.g., "CREATE_ISSUE"
    val action: String?,          // create, read, update, delete
    val parameters: Map<String, Any>,
    val connectedAccountId: String?,
    
    // For Custom Webhook
    val customWebhookId: String?   // Reference to saved custom webhook
)
```

## 6. Composio Integration

### 6.1 API Authentication

- Use Composio REST API with API key stored in `local.properties`
- API key format: `COMPOSIO_API_KEY`
- All API calls include `x-api-key` header

### 6.2 Available Toolkits

Composio supports 1000+ toolkits including:
- Developer: GitHub, GitLab, Jira, Linear
- Productivity: Notion, Slack, Discord, Microsoft Teams
- CRM: Salesforce, HubSpot
- Marketing: Mailchimp, SendGrid
- Communications: Twilio, Zoom

### 6.3 OAuth Flow

1. User selects tool to connect
2. App calls Composio API to get OAuth redirect URL
3. Open Chrome Custom Tab with OAuth URL
4. User authenticates with external service
5. Callback to app via deep link: `operit://oauth-callback/{toolkit}`
6. Exchange code for access token
7. Store token in EncryptedSharedPreferences

### 6.4 Tool Execution

```kotlin
// Execute Composio tool
POST https://api.composio.com/v1/tools/{tool_name}/execute
Headers:
  x-api-key: {COMPOSIO_API_KEY}
  Content-Type: application/json

Body:
{
  "connectedAccountId": "acct_xxx",
  "parameters": {
    "owner": "user",
    "repo": "my-repo",
    "title": "Bug fix"
  }
}
```

## 7. Custom Webhook Support

### 7.1 HTTP Request Execution

For non-Composio endpoints, support:

- **HTTP Methods**: GET, POST, PUT, DELETE, PATCH
- **Authentication**:
  - API Key in header
  - Bearer token
  - Basic auth
  - Custom headers
- **Body**: JSON, form-data, raw text

### 7.2 Variable Substitution

Workflow variables are referenced using `{{variable_name}}` syntax:

- **Variable Sources**: 
  - Workflow context variables (user-defined)
  - Previous node outputs (referenced as `{{node_id.output_field}}`)
- **Substitution Behavior**:
  - Variables are substituted in URL, headers, and body
  - If a variable doesn't exist: **fail the node with an error**
  - No empty string fallback - explicit failure to catch configuration errors

### 7.3 Request Builder

### 7.2 Request Builder

```kotlin
fun buildRequest(
    url: String,
    method: HttpMethod,
    headers: Map<String, String>,
    body: String?,
    variables: Map<String, String>
): Request {
    // Substitute variables in URL, headers, body
    // Build OkHttp request
}
```

## 8. UI Design

### 8.1 Workflow Node - Integration Type Selection

When user adds an "Integration" node:

1. **Select Integration Type**:
   - "Composio Tool" - for services on Composio
   - "Custom Webhook" - for arbitrary HTTP endpoints

2. **If Composio Tool**:
   - Select Tool (dropdown populated from API)
   - Select Action (Create/Read/Update/Delete based on tool)
   - Configure Parameters (dynamic form)
   - Select Connection (connected account)

3. **If Custom Webhook**:
   - Select saved webhook OR create new
   - Configure URL, method, headers, body

### 8.2 Settings - Webhooks & Integrations

Unified settings with 3 tabs:

1. **Connected Accounts**:
   - List of connected services
   - Connect/Disconnect buttons
   - Status indicators

2. **Custom Webhooks**:
   - List of saved webhook configurations
   - Add/Edit/Delete buttons
   - Test button

3. **Outbound Events** (existing):
   - Keep current webhook notification settings
   - MCP events, workflow events

## 9. Error Handling

### 9.1 Connection Errors

- **Expired Token**: Prompt user to reconnect
- **Invalid Credentials**: Show error, offer reconnect
- **Rate Limiting**: Implement backoff, show user message

### 9.2 Request Errors

- **Network Error**: Retry with exponential backoff (max 3)
- **API Error**: Parse error response, show user-friendly message
- **Timeout**: 30-second timeout, show error

### 9.3 Node Execution Errors

- Log error details
- Mark node as failed
- Continue or stop workflow based on configuration

## 10. Security

- Store OAuth tokens in EncryptedSharedPreferences
- Never log sensitive data (tokens, API keys)
- Validate all URLs before making requests
- Use HTTPS only for webhook endpoints

## 11. Dependencies

- **OkHttp**: HTTP client for API calls
- **Retrofit** (optional): For structured API clients
- **Kotlinx Serialization**: JSON parsing
- **DataStore**: Preferences storage

## 12. Implementation Phases

### Phase 1: Foundation
- Create data layer (models, repository)
- ComposioApiService implementation
- Basic OAuth flow

### Phase 2: Workflow Integration
- Integration node in workflow builder
- Node configuration UI
- Execution logic

### Phase 3: Custom Webhooks
- CustomWebhook CRUD
- HTTP request executor
- Variable substitution

### Phase 4: Settings UI
- Connected Accounts management
- Custom Webhooks management
- Integrate with existing webhook settings

## 13. Open Questions

1. Should we cache tool schemas locally? (Faster but may be stale)
2. How to handle Composio API rate limits?
3. Support for webhook retry on failure?

---

**Review Status**: Approved (with fixes applied)
