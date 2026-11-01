# Mindcraft AI Bot Host for Android

A dedicated Android hosting console and autonomous runtime for AI-powered Minecraft bots based on the **Mindcraft** framework. The app enables users to configure, launch, and host autonomous Minecraft bots directly from Android, leveraging either cloud AI APIs (Gemini, OpenAI, Claude, Groq) or downloadable lightweight local AI models with real-time MB download tracking, backed by an obsidian dark-mode dashboard, live activity log streams, and self-healing automated error recovery.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The app is designed around the Mindcraft architecture (Perception $\rightarrow$ LLM Reasoning $\rightarrow$ Action Dispatch $\rightarrow$ Self-Correction). Review the following core configurations:

- **Minecraft Connection Protocol**: Primary support for standard Minecraft Java Edition servers (IP/Domain + Port, offline/custom/Microsoft auth profiles), with simulated local testing sandbox and direct network packet bridge.
- **Local AI Engine**: Built-in High-Speed Local AI Model Manager featuring downloadable quantized models (SmolLM2 360M Instruct at ~185 MB and TinyLlama 1.1B at ~630 MB) with real-time byte/MB progress tracking, download resume, and on-device inference simulator.
- **Third-Party Cloud API Support**: Pre-configured instant setup for Google Gemini (including in-app BuildConfig / AI Studio secret integration), OpenAI (GPT-4o / GPT-4o-mini), Anthropic Claude 3.5 Sonnet, and Groq (Llama-3-70b ultra-low latency), with custom endpoint support.
- **Dark Mode Visual Direction**: "Obsidian & Redstone" gaming HUD aesthetic with deep slate/charcoal surfaces, glowing emerald/redstone telemetry indicators, and high-legibility monospace log consoles.

---

## 1. Overview & Core Concept

**Mindcraft Bot Host** brings the power of autonomous Minecraft agents to Android devices. Based on the open-source Mindcraft framework (which bridges LLM cognitive planning with the Mineflayer Minecraft protocol engine), the app allows users to:
1. **Host & Run Bots in-App**: Run continuous bot lifecycle sessions with connection management, inventory monitoring, position coordinates, and task execution directly on-device.
2. **Hybrid Brain (Cloud API & Local AI)**: Seamlessly switch between lightning-fast cloud APIs (Gemini 2.5 Flash, GPT-4o-mini, Groq) and fully offline local models.
3. **Local AI Model Downloader**: Download ultra-fast edge LLMs directly inside the app with a sleek progress bar showing downloaded MB, total MB, download speed (MB/s), and estimated time remaining.
4. **Performance & Activity Dashboard**: Live monitoring of Bot TPS (ticks per second), ping latency, CPU/memory usage, action frequency, inventory slots, health/hunger bars, and filtered activity logs.
5. **Interactive "How It Works" Bot Brain**: Educational and operational explorer showing how Mindcraft translates Minecraft sensory inputs into LLM prompts, generates code/commands, executes them, and self-corrects.
6. **Automated Error Reporting & Self-Healing**: Automated crash and exception tracker with instant root-cause analysis, auto-retry policies (reconnect on kick, retry pathfinding on obstacle, re-prompt on JSON malformation), and one-tap diagnostic reports.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Quick Launch Flow**: User opens the app $\rightarrow$ Selects bot profile ("Andy - Builder & Defender") $\rightarrow$ Enters server host `mc.hypixel.net` or local LAN IP $\rightarrow$ Chooses AI backend (Cloud API key or downloaded Local Model) $\rightarrow$ Taps "Deploy Bot".
2. **Local AI Download Flow**: User navigates to "AI Models" tab $\rightarrow$ Views available edge models (SmolLM2 360M, TinyLlama 1.1B) $\rightarrow$ Clicks "Download Model" $\rightarrow$ Watches real-time download bar with MB counter and speed gauge $\rightarrow$ Activates local engine once download finishes.
3. **Live Dashboard & Command Feed**: Real-time HUD showing bot coordinates `(X, Y, Z)`, health hearts, hunger drumsticks, inventory grid, and active AI tasks $\rightarrow$ Live color-coded terminal log stream (`[PERCEPTION]`, `[AI REASON]`, `[ACTION]`, `[CHAT]`, `[ERROR]`, `[RECOVERY]`) $\rightarrow$ Chat & command prompt to send natural language instructions to the bot in real time.
4. **Bot Brain Explorer**: Interactive schematic allowing the user to inspect what the bot "sees" in Minecraft, the raw prompt sent to the LLM, the model's generated plan, and the action sent to the server.
5. **Error & Health Watchdog**: If a connection drops, server kicks, or task fails, the automated watchdog triggers self-healing, posts a notification in the diagnostic console, and archives the error log for review.

### Visual Identity & Theme
- **Theme Name**: Obsidian Netherite (Clean Premium Dark Mode)
- **Palette**:
  - Background: `#0D1117` (Deep Obsidian Void)
  - Surface & Card: `#161B22` with border `#30363D`
  - Accent Emerald: `#2EA043` / `#238636` (Bot Online, Healthy State, Success)
  - Accent Redstone: `#DA3633` / `#F85149` (Alerts, Combat, Critical Errors)
  - Accent Diamond Blue: `#58A6FF` (AI Reasoning, Network Telemetry, Cloud APIs)
  - Accent Gold/Amethyst: `#D29922` / `#BC8CFF` (Tasks, Inventory, Local Models)
- **Typography**: Clean modern sans-serif for UI elements (`Inter`/`Roboto`) paired with crisp Monospace typography for terminal logs, coordinates, and diagnostic payloads.
- **Component Styling**: Minimalist floating HUD cards, glowing status badges, segmented provider pickers, and smooth animated progress meters.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Alternatives Considered |
|---|---|---|---|
| **Bot Host Architecture** | Native Kotlin Mindcraft engine with full Minecraft Protocol & Mindcraft Action dispatcher + simulated sandbox | Runs natively on Android without requiring external Node.js/Docker server, maintaining 100% responsiveness and offline resilience. | Bundling headless Node.js via Termux/Chroot (unstable, heavy APK, crashes on modern Android 14/15 restrictions). |
| **Local Model Management** | On-device model manager with real-time Chunked HTTP/MB downloader and fast local edge LLM pipeline | Gives users exact MB progress and speeds while keeping the base APK size compact (<15 MB) without bundling huge multi-gigabyte binaries. | Embedding 1 GB model directly in APK (exceeds Google Play limits, wastes user bandwidth). |
| **API Provider Key Handling** | Multi-provider selector (Gemini, OpenAI, Claude, Groq, Custom) with secure local persistence & BuildConfig fallback | Allows effortless copy-paste of keys with auto-validation and instant provider switching. | Hardcoded single provider (restricts user freedom). |
| **Error Reporting & Resilience** | Autonomous Self-Healing Watchdog with auto-reconnect, exponential backoff, prompt auto-correction, and Room-backed error repository | Mindcraft bots frequently face Minecraft server disconnects, pathfinding snags, or LLM hallucinations; automatic recovery is essential for continuous unattended hosting. | Simple toast error messages (leaves the bot stranded or disconnected). |

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Mindcraft Android App                           │
├────────────────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose - Clean Dark Mode HUD)                     │
│  ├── Bot Dashboard (Health, Hunger, Coordinates, Inventory, TPS)       │
│  ├── Activity Terminal (Real-time color-coded log stream)              │
│  ├── AI Configuration (Cloud API selector + Local Model Downloader)   │
│  ├── Bot Brain Explorer (Perception -> Prompt -> Action Pipeline)      │
│  └── Automated Diagnostic Center & Error Reporter                      │
├────────────────────────────────────────────────────────────────────────┤
│  ViewModel & State Management (StateFlow / Coroutines)                 │
│  ├── BotSessionViewModel (Lifecycle, live stats, command dispatcher)   │
│  ├── AiConfigViewModel (Provider keys, local model download manager)   │
│  └── DiagnosticsViewModel (Error recovery policies, export logs)       │
├────────────────────────────────────────────────────────────────────────┤
│  Core Bot & Engine Layer                                               │
│  ├── MindcraftAgentEngine (Perception, Action Loop, Memory, Inventory) │
│  ├── LLM Reasoning Router:                                             │
│  │    ├── Cloud API Client (Gemini, OpenAI, Claude, Groq via Retrofit) │
│  │    └── Local AI Engine (Downloaded GGUF/SmolLM2 fast inference)    │
│  ├── Minecraft Protocol Bridge (Packet handler, Auth, Server Ping)     │
│  └── Self-Healing Watchdog (Heartbeat monitor, auto-retry, self-repair) │
├────────────────────────────────────────────────────────────────────────┤
│  Data & Persistence Layer (Room Database + Encrypted Preferences)      │
│  ├── BotLogDao & BotLogEntity (Persistent searchable activity logs)    │
│  ├── ErrorReportDao & ErrorReportEntity (Automated error dumps)        │
│  └── ServerConfig & ApiKeys (Local persistent configuration)          │
└────────────────────────────────────────────────────────────────────────┘
```

### Core Entities & Persistence
- `BotLogEntry`: timestamp, level (INFO, WARN, ERROR, RECOVERY, AI_THOUGHT), category (NETWORK, COMBAT, MINING, CHAT, PERCEPTION), message, rawJsonPayload.
- `ErrorReport`: id, timestamp, errorType, stackTrace, botStateSnapshot (coordinates, currentGoal, lastPrompt), recoveryActionTaken, resolvedStatus.
- `AiModelPackage`: modelId, name, downloadUrl, sizeBytes, downloadedBytes, isDownloaded, isActive, parameterCount, quantization.
- `ServerProfile`: host, port, botUsername, botPersona (Builder, Guard, Miner, Explorer), authMode.

---

## Implementation Steps & Verification Plan

1. **Project Setup & Dependencies**:
   - Update `app/build.gradle.kts` and Version Catalog with necessary JSON/Retrofit and Coroutines tools.
   - Synchronize app name in `metadata.json` and `res/values/strings.xml` ("Mindcraft Bot Host").
2. **Room Database & Persistence**:
   - Create Room Database for Bot Logs, Error Reports, and Server Profiles.
3. **Mindcraft AI Engine & Bot Brain**:
   - Implement `MindcraftAgentEngine`: Perception generator, memory buffer, prompt constructor matching Mindcraft's prompt structure, and action executor (`!goTo`, `!mine`, `!craft`, `!defend`, `!chat`, `!inspectInventory`).
   - Implement `AiReasoningRouter` supporting both Cloud APIs (Gemini, OpenAI, Claude, Groq) and Local Model Engine.
4. **Local Model Downloader**:
   - Implement real-time HTTP chunk downloader with bytes-transferred calculation, MB formatting, download speed calculation, and storage in app-private directory.
5. **Dashboard, Activity Terminal & Explorer UI**:
   - Build high-polish Obsidian Dark Mode Compose UI:
     - **Dashboard Tab**: Minecraft HUD (Heart/Hunger gauges, coords, biome, active task card, quick command buttons).
     - **Console/Logs Tab**: High-speed monospace scrolling log stream with filters, search, and pause/resume.
     - **Bot Brain Tab**: Visual step-by-step diagram of how Mindcraft's perception-reason-action loop functions with interactive simulator.
     - **AI & Models Tab**: Cloud API key manager with test connection button + Local Model download manager with MB progress.
     - **Diagnostics Tab**: Error logs, self-healing status, retry count, and crash report export.
6. **Self-Healing Watchdog & Automated Error Reporting**:
   - Build autonomous supervisor that captures network timeouts, invalid responses, and path blocked events, automatically triggering remediation prompts and recovery steps.
7. **Compilation & Verification**:
   - Run `compile_applet` to ensure pristine compilation and error-free execution.
