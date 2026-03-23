# Feature Specification: AI Agent Terminal Integration

## Project: Operit AI (AESOP-Lab/aesop-android)

## Feature Summary

Enable users to run autonomous AI agent CLIs (like `hermes-agent`, `claude-code`, `codex`) inside the Operit terminal with a chat-like GUI interface for interaction, rather than manual terminal command typing.

---

## 1. Overview

**Goal**: Allow users to browse, install, and run AI agent CLIs from a GUI, with persistent chat sessions.

**Core Value**:
- Users don't need to remember CLI commands
- Chat-like interface for interacting with autonomous agents
- Persistent sessions (agent remembers conversation)
- Easy installation of agent CLIs via templates

---

## 2. Architecture

### 2.1 Data Model

```kotlin
// New file: data/model/AgentDefinition.kt
data class AgentDefinition(
    val id: String,                    // unique identifier, e.g., "hermes-agent"
    val name: String,                  // display name, e.g., "Hermes Agent"
    val description: String,          // what the agent does
    val icon: String,                  // icon resource name
    val installCommand: String,        // shell command to install (e.g., "pip install hermes-agent-cli")
    val installCheckCommand: String,   // command to check if installed (e.g., "which hermes")
    val startCommand: String,          // command to start agent (e.g., "hermes")
    val requiredDeps: List<String>,    // e.g., ["python3", "pip"]
    val isInstalled: Boolean = false, // runtime state
    val isRunning: Boolean = false    // runtime state
)

// New file: data/model/AgentSession.kt
data class AgentSession(
    val id: String,                    // terminal session ID
    val agentId: String,               // which agent type
    val title: String,                 // user-visible name
    val createdAt: Long,              // timestamp
    val lastActivityAt: Long         // for sorting
)
```

### 2.2 Repository

```kotlin
// New file: data/repository/AgentRepository.kt
class AgentRepository(context: Context) {
    // Get all available agent definitions (hardcoded + from configs)
    fun getAvailableAgents(): List<AgentDefinition>
    
    // Check if agent is installed
    suspend fun isAgentInstalled(agentId: String): Boolean
    
    // Install agent (run install command in terminal)
    suspend fun installAgent(agentId: String): Result<Unit>
    
    // Start agent session (creates terminal session, runs start command)
    suspend fun startAgentSession(agentId: String, title: String): Result<String> // returns session ID
    
    // Send input to running agent (NOT restart - send to stdin)
    suspend fun sendToAgent(sessionId: String, input: String): Result<Unit>
    
    // Get stream of output from agent session
    fun getAgentOutputStream(sessionId: String): Flow<String>
    
    // Close agent session
    suspend fun closeAgentSession(sessionId: String): Result<Unit>
}
```

### 2.3 Integration with Existing Terminal Module

The existing `TerminalManager` already handles:
- `createNewSession(title)` - creates terminal session with running process
- `sendInput(sessionId, input)` - sends to stdin (keeps process alive!)
- `commandExecutionEvents` - flow of stdout/stderr
- Multiple sessions support

**Key insight**: Use `sendInput()` NOT `sendCommand()` - this keeps the hermes process alive!

---

## 3. UI/UX Design

### 3.1 New Screen: AI Agents

Location: New screen in Toolbox or a dedicated section

**Screen 1: Agent List (AgentMarketScreen)**
```
┌─────────────────────────────────────────┐
│ ← AI Agents                        [+] │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ 🤖 Hermes Agent              [Start]│ │
│ │ NousResearch's autonomous agent    │ │
│ │ Status: Installed ✓                │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ 🦋 Claude Code              [Install]│ │
│ │ Anthropic's AI coding assistant    │ │
│ │ Status: Not installed              │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ ⚡ Codex                    [Install]│ │
│ │ OpenAI's coding agent              │ │
│ │ Status: Not installed              │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

**Screen 2: Agent Chat (AgentSessionScreen)**
```
┌─────────────────────────────────────────┐
│ ← Hermes Agent - Session 1        [⋮] │
├─────────────────────────────────────────┤
│                                         │
│ 🤖 Agent: Let me help you build that    │
│ calculator app. I'll start by...        │
│                                         │
│ 🔧 Writing main.py...                   │
│ 🔧 Writing ui.py...                     │
│                                         │
│ 🤖 Agent: Done! Here's what I created: │
│ • main.py - Calculator logic            │
│ • ui.py - Simple GUI                   │
│                                         │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ Type your message...          [➤] │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

**Screen 3: Session Manager (AgentSessionsScreen)**
```
┌─────────────────────────────────────────┐
│ ← My Agent Sessions                    │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ 🤖 Hermes - Session 1     Running 🟢│ │
│ │ Started 5 min ago                   │ │
│ │ [Switch] [Close]                    │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ 🤖 Hermes - Session 2     Running 🟢│ │
│ │ Started 1 hour ago                  │ │
│ │ [Switch] [Close]                    │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ + Start New Session                │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 3.2 Navigation Structure

```
Toolbox
├── Agent Sessions (new NavItem)
│   ├── AgentSessionsScreen (list of running sessions)
│   │   ├── → AgentSessionScreen (chat with specific session)
│   │   └── → AgentMarketScreen (browse/install agents)
│   └── AgentMarketScreen (if no sessions running)
```

Or integrate into existing Terminal section:

```
Toolbox
├── Terminal
│   ├── TerminalScreen (existing)
│   ├── Agent Sessions (new) ← switches to AgentSessionsScreen
│   └── Agent Market (new) ← switches to AgentMarketScreen
```

---

## 4. Implementation Steps

### Step 1: Create Agent Definitions

Create `app/src/main/java/com/ai/assistance/operit/data/model/AgentDefinition.kt`:

```kotlin
package com.ai.assistance.operit.data.model

data class AgentDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,  // drawable resource name
    val installCommand: String,
    val installCheckCommand: String,
    val startCommand: String,
    val requiredDeps: List<String> = emptyList()
)

object AgentRegistry {
    // Hardcoded initial agents
    val agents = listOf(
        AgentDefinition(
            id = "hermes-agent",
            name = "Hermes Agent",
            description = "Autonomous AI agent from NousResearch. Great for coding, debugging, and complex tasks.",
            icon = "ic_hermes",
            installCommand = "pip install nous-hermes-agent-cli",
            installCheckCommand = "which hermes",
            startCommand = "hermes",
            requiredDeps = listOf("python3", "pip")
        ),
        AgentDefinition(
            id = "claude-code",
            name = "Claude Code",
            description = "Anthropic's CLI for AI-assisted coding. Full code editing capabilities.",
            icon = "ic_claude",
            installCommand = "npm install -g @anthropic/claude-code-cli",
            installCheckCommand = "which claude",
            startCommand = "claude",
            requiredDeps = listOf("node", "npm")
        ),
        AgentDefinition(
            id = "codex",
            name = "OpenAI Codex",
            description = "OpenAI's command-line coding agent.",
            icon = "ic_codex",
            installCommand = "pip install codex-cli",
            installCheckCommand = "which codex",
            startCommand = "codex",
            requiredDeps = listOf("python3", "pip")
        )
    )
    
    fun getById(id: String) = agents.find { it.id == id }
}
```

### Step 2: Create AgentRepository

Create `app/src/main/java/com/ai/assistance/operit/data/repository/AgentRepository.kt`:

```kotlin
package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.AgentDefinition
import com.ai.assistance.operit.data.model.AgentRegistry
import com.ai.assistance.operit.terminal.TerminalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AgentRepository(private val context: Context) {
    private val terminalManager = TerminalManager.getInstance(context)
    
    fun getAvailableAgents(): List<AgentDefinition> = AgentRegistry.agents
    
    suspend fun isAgentInstalled(agentId: String): Boolean = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext false
        // Run install check command, check exit code
        terminalManager.createNewSession("install-check-${agentId}").use { sessionId ->
            terminalManager.sendCommandToSession(sessionId, agent.installCheckCommand)
            // Wait and check result - simplified for now
            // In real implementation: parse output for success
            true // Placeholder
        }
    }
    
    suspend fun installAgent(agentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        // Run install command in a terminal session
        // Show progress to user
        Result.success(Unit) // Placeholder
    }
    
    suspend fun startAgentSession(agentId: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        val agent = AgentRegistry.getById(agentId) ?: return@withContext Result.failure(
            Exception("Unknown agent: $agentId")
        )
        
        // Create new terminal session
        val sessionId = terminalManager.createNewSession(title)
        
        // Start the agent process (keeps running!)
        // IMPORTANT: Use sendCommand that starts REPL, not exits
        terminalManager.sendCommandToSession(sessionId, agent.startCommand)
        
        Result.success(sessionId)
    }
    
    suspend fun sendToAgent(sessionId: String, input: String): Result<Unit> = withContext(Dispatchers.IO) {
        // KEY: Use sendInput, NOT sendCommand - keeps process alive!
        terminalManager.sendInput(sessionId, input)
        Result.success(Unit)
    }
    
    fun getAgentOutputFlow(sessionId: String): Flow<String> {
        // Subscribe to terminalManager.commandExecutionEvents
        // Filter for this session, emit output
        return terminalManager.commandExecutionEvents
    }
    
    suspend fun closeAgentSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        terminalManager.closeSession(sessionId)
        Result.success(Unit)
    }
}
```

### Step 3: Create UI Screens

Create ViewModels and Compose screens:

**AgentMarketViewModel + AgentMarketScreen**
- List available agents
- Show install/start buttons
- Handle install flow

**AgentSessionsViewModel + AgentSessionsScreen**
- List running agent sessions
- Allow switching/closing sessions

**AgentChatViewModel + AgentSessionScreen**
- Display chat interface
- Handle send input
- Stream output in real-time

### Step 4: Add Navigation

In `OperitScreens.kt`, add:

```kotlin
data object AgentSessions : Screen(...)
data object AgentMarket : Screen(...)
data object AgentChat : Screen(...)
```

Add navigation routes and remember navigation state.

### Step 5: Add Resources

Add icons for agents (or use generic robot icons).
Add string resources for UI labels.

---

## 5. Key Implementation Notes

### 5.1 Terminal Integration (CRITICAL)

**DO**: Use `terminalManager.sendInput(sessionId, text)` to send messages to running agent
**DON'T**: Use `terminalManager.sendCommandToSession(sessionId, "hermes $message")` - this restarts the agent every time!

The difference:
- `sendCommand` = runs command, waits for exit
- `sendInput` = sends to stdin of already-running process

### 5.2 Output Streaming

The existing `TerminalManager.commandExecutionEvents` is a `Flow<CommandExecutionEvent>`.
- Subscribe to it in your ViewModel
- Filter by sessionId
- Append to chat message

### 5.3 Installation

For simplicity, first version can require manual installation:
- User opens terminal, runs `pip install nous-hermes-agent-cli` themselves
- App just checks if command exists via `which hermes`

Later: Add in-app installation that runs the install command and shows progress.

### 5.4 Session Persistence

Each `AgentSession` maps to a Terminal session ID.
- User can have multiple sessions running
- Closing session terminates the agent process
- App doesn't need to save/restore - terminal sessions persist while app is alive

---

## 6. File Changes Summary

### New Files (create):
- `app/src/main/java/com/ai/assistance/operit/data/model/AgentDefinition.kt`
- `app/src/main/java/com/ai/assistance/operit/data/repository/AgentRepository.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentMarketViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentMarketScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentSessionsViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentSessionsScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentChatViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agents/AgentChatScreen.kt`

### Modified Files:
- `app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt` - add navigation
- Add string resources
- Add icons (optional)

---

## 7. Testing Checklist

- [ ] Can view list of available agents
- [ ] Can start hermes-agent session
- [ ] Can send message to running agent (agent responds!)
- [ ] Can have multiple sessions running
- [ ] Can switch between sessions
- [ ] Can close session (agent process terminates)
- [ ] Output streams in real-time (not just after completion)
- [ ] Terminal module integration works (no process restart on each message)

---

## 8. Future Enhancements (Out of Scope for V1)

- In-app agent installation with progress UI
- Agent output parsing (extract code blocks, tool calls)
- Save/restore sessions across app restarts
- Agent configuration (API keys, preferences)
- More agent templates
- Agent marketplace (fetch from remote config)