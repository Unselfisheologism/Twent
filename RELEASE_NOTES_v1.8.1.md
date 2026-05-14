# Twent v1.8.1 — Release Notes

> Your personal agentic OS for Android. Free forever. No credit card.

---

## What's New in v1.8.1

### 🤖 Hermes Brain — Smarter AI, Always On

The AI agent now has a proper brain. A persistent overlay layer that wraps every conversation with:
- **Memory injection** — the agent remembers who you are and what you've been working on, across sessions
- **Iteration budgets** — smarter tool-calling loops, no more getting stuck in infinite retry spirals
- **Tool interception** — mid-session notes and snapshots so you can pause, branch, and resume conversations
- **Mid-session awareness** — the agent knows where it is in a conversation, not just the current message

Basically, Hermes Brain means the AI doesn't forget what it was doing 10 minutes ago. Revolutionary concept, right? (▀̿Ĺ̯▀̿ ̿)

### 🔌 Composio Integration — 1000+ App Connections

This is the big one. Twent now speaks directly to Composio's entire integration catalogue:
- Browse and connect **1000+ services** — Gmail, Notion, Slack, GitHub, Linear, HubSpot, whatever your workflow needs
- Full **tool-calling support** — the agent can actually execute actions on those services, not just talk about them
- Entity ID passed as proper string format — no more silent failures when calling tools with IDs
- Forever spinner fixed — integrations that were stuck loading are now snappy

If you've been waiting to automate your entire digital life from your phone, the wait is over.

### 🛠️ MCP (Model Context Protocol) — Fixed and Faster

A bunch of MCP server registration and execution issues got squashed:
- **Tool registration** properly handles the MCP SDK, no more missing tools silently failing
- **DuckDuckGo MCP server** fully reimplemented and working — your default web search actually searches the web now
- **MCP local server** setup cleaned up — no more weird terminal setup screen glitches
- Tool registration error that was causing crashes? Gone.
- Secondary web search provider fallback — if your primary is down, it auto-switches

### 📁 Mini-Apps — Create Apps Inside Twent

- Fixed chat vanishing when creating mini-apps — your conversation no longer disappears mid-build
- File generation pipeline improved — export your mini-apps as standalone files
- Character cards / persona system — give your AI a personality, backstory, and tone

### 🐛 Bug Fixes (The Boring But Essential Stuff)

- `MessageProcessingDelegate` — cleaned up and stabilized (was causing phantom chat vanish bugs)
- Terminal setup screen — PyJWT re-installation, PNPM install command, and ddg-mcp-server installation commands all fixed
- Chat response tool calls no longer vanish after completion — you can actually read what happened now
- Fixed "Integrations" folder navigation — goes to the right Twent folder, not Operit
- Various MCP running issues resolved — ddg-mcp-server actually starts properly
- Error handling across the board — less crashing, more doing

---

## What's Already in Twent (The Full Feature List)

### AI Agent
- Screen reading & UI automation (tap, swipe, scroll, type on any app)
- Floating chat bubble overlay — AI always accessible, no matter what app you're in
- Voice wake word activation — just say "hey" and the agent wakes up
- Persistent memory system — remembers conversations across sessions
- MCP plugins — extend the agent with custom tools and protocols
- Composio integration — 1000+ app connections
- Hermes Brain — smarter context, iteration control, mid-session notes

### Ubuntu 24.04 Terminal
- Full Linux environment on Android, **no root required**
- bash, zsh, fish shells
- apt, npm, pip, cargo package managers
- Git, SSH, SCP — full development toolchain
- Python, Node.js, Go, Rust, C/C++ compilers
- VS Code Server — code on your phone like it's 2019

### Local AI & Privacy
- GGUF model inference — run models locally on your device
- MNN (Mobile Neural Networks) for efficient on-device AI
- BYOK — bring your own API keys, encrypted locally
- Zero telemetry — nothing leaves your device without your permission
- On-device embedding generation with ONNX, HNSW vector search

### Developer Tools
- Claude Code integration
- OpenAI Codex support
- GitHub CLI with OAuth
- SSH client and server
- NanoHTTPD local web server

### Automation & Workflows
- Visual workflow builder
- Tasker plugin integration
- Scheduled triggers
- Conditional logic (if/then/else)
- ADB integration
- Document processing — DOC, DOCX, PDF, EPUB, PPTX

### UI / UX
- Dark/light theme with color customization
- Reorderable screens
- Swipe-to-reveal actions
- Glance app widgets
- Multi-language OCR via ML Kit (Chinese, Japanese, Korean, Devanagari)
- Video backgrounds in onboarding
- LaTeX rendering

---

## Technical Details

| | |
|---|---|
| **Version** | 1.8.1 (versionCode 39) |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 14 (API 34) |
| **Architecture** | arm64-v8a |
| **Size** | ~500MB |
| **RAM** | 3GB+ recommended |
| **Root Required** | No |

---

## Get It

📦 **Download APK**: [https://twent.xyz](https://twent.xyz)

💬 **Support**: support@twent.xyz

🌐 **Website**: [https://twent.xyz](https://twent.xyz)

📱 **Community**: Join the X/Twitter conversation at [@twentai](https://x.com/twentai)

---

*Everything is free. No credit card. No catch. Your agentic OS is waiting.*