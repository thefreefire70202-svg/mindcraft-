# Mindcraft Bot Host - Real Minecraft 1.21 Server Join & AI Assistant Integration

An end-to-end fix and enhancement plan for Mindcraft Bot Host on Android. This plan resolves server connection throttling on Bukkit/Purpur/Paper (including Aternos), updates the Minecraft protocol pipeline to support full 1.20.2–1.21.x Configuration Phase negotiation, and implements an interactive in-game AI assistant that responds to player chat commands in real time.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions were established during clarification and guide the technical implementation:

- **Server Authentication Mode**: Offline / Cracked mode compatibility (`online-mode=false`). Generates proper offline player UUIDs and skips encryption handshakes, enabling the bot to join Aternos and custom SMPs with Cracked enabled.
- **Connection Throttle Management**: Implements an automatic server throttle cooldown (enforcing a 5-second buffer between socket operations), deduplicates ping requests, and provides a direct 1-tap "Direct Join" flow that completely bypasses status pinging to prevent Bukkit `connection-throttle` rejections.
- **AI Persona & Task Execution**: The bot acts as an interactive in-game AI assistant. When players message the bot in chat (e.g., `!help`, `follow me`, `what are you doing`, `status`, `protect me`), the AI engine parses the message and broadcasts natural in-game responses and status updates.

---

## 1. Overview & Core Concept

- **What It Does**: Resolves the root cause of the `Connection throttled! Please wait before reconnecting` disconnect by intelligently staggering network requests, caching protocol detection, and implementing the 1.21.x Configuration Phase packet exchange. Once connected, the AI agent enters the world and listens for player chat, replying and acting autonomously.
- **Target Audience**: Minecraft Java server administrators, SMP players, and developers wanting a dedicated Android console to run autonomous AI agents in their private worlds or servers (such as Aternos, Paper, and Purpur).
- **Key Value**: Guarantees reliable, un-throttled server joining on modern Minecraft 1.21+ servers while delivering an engaging companion that converses and coordinates with human players in-game.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Direct Join Flow (Bypass Throttle)**:
   - User inputs server address (e.g. `PRIVATESMPGO.aternos.me:53750`) and bot name.
   - User taps **Join Server Directly** (or **Deploy Bot**).
   - If a status query was recently performed within 5 seconds, an animated countdown badge appears (`Cooldown active: joining in 3s...`) preventing Bukkit IP rate-limits.
   - The bot connects, completes the Handshake $\rightarrow$ Login Success $\rightarrow$ Configuration Phase $\rightarrow$ Play Phase, and logs `Joined Purpur 1.21.1 server as MindcraftBot`.

2. **In-Game Chat & AI Interaction Flow**:
   - Once spawned, the bot sends an introductory greeting to the server chat: `Mindcraft AI Bot online! Type "!help" or chat with me.`
   - In-game players send chat messages.
   - The bot's packet listener captures the chat packets, forwards them to the AI Client (Gemini or Local Edge Model), generates an intelligent reply, and transmits the response back through the Minecraft chat protocol.
   - The Android app's **Dashboard** and **Console** tabs display the live two-way dialogue with player name tags and timestamps.

3. **Auto-Recovery & Reconnect Flow**:
   - If the server disconnects the bot (e.g., server restart or temporary throttle), the Self-Healing Watchdog captures the disconnect reason.
   - The UI displays a live cooldown progress indicator and automatically reconnects without manual user intervention once the cooldown window expires.

### Visual Styling & Theme

- **Theme**: Obsidian Netherite HUD (`#0D1117` void background, `#161B22` surface tiles, `#30363D` borders).
- **Status Indicators**:
  - Emerald Green (`#2EA043`): Bot joined in-game, responding to chat.
  - Gold Yellow (`#D29922`): Server cooldown active / connecting.
  - Diamond Blue (`#58A6FF`): AI reasoning / chat command execution.
  - Redstone Red (`#DA3633`): Disconnected / server offline.
- **Typography & Layout**: Monospace terminal logs with color-coded category tags (`[NETWORK]`, `[CHAT]`, `[AI]`, `[THROTTLE_GUARD]`).

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Alternatives Considered |
|---|---|---|---|
| **Throttle Mitigation** | Proactive 5s socket delay + direct join option | Aternos and Paper default to `connection-throttle: 4000`. Querying and then joining within 400ms triggers immediate IP rejection. | Allowing unthrottled rapid reconnects (leads to infinite loop of "Connection throttled" kicks). |
| **Protocol 1.21 Support** | Native Kotlin 1.20.2–1.21.x Configuration Phase packet engine | Modern Minecraft servers disconnect clients that fail to negotiate the Configuration Phase (Select Known Packs, Client Info, Finish Configuration). | Using a headless Node.js/Mineflayer wrapper (heavy, requires 100MB+ node runtime, crashes on Android sandbox). |
| **AI Chat Responses** | Asynchronous Gemini/Local LLM prompt pipeline with in-game rate limit | Allows responsive conversational interaction while preventing spam-kicks by the Minecraft server. | Static hardcoded command responses (inflexible and unnatural). |

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Mindcraft Android App                           │
├────────────────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose - Obsidian HUD)                             │
│  ├── Dashboard Screen: Server connection, cooldown timer, live stats    │
│  ├── In-Game Chat Box: Bidirectional chat stream & command sender      │
│  └── Console Screen: Real-time packet, network, & AI reasoning logs    │
├────────────────────────────────────────────────────────────────────────┤
│  Network & Protocol Layer (MinecraftProtocolClient)                    │
│  ├── Throttle Guard: Enforces 5000ms delay between socket openings    │
│  ├── Handshake & Login: Offline UUID calculation (Packet 0x00)         │
│  ├── 1.21 Configuration Phase:                                         │
│  │    ├── Client Information (0x00)                                   │
│  │    ├── Select Known Packs (0x07)                                   │
│  │    ├── Keep-Alive & Pong (0x03, 0x04)                               │
│  │    └── Finish Configuration Acknowledgment (0x02)                   │
│  └── Play Phase: Keep-Alive responder, position sync, chat transmitter  │
├────────────────────────────────────────────────────────────────────────┤
│  AI Engine & Chat Pipeline (MindcraftBotEngine & AiClient)             │
│  ├── In-Game Chat Listener: Captures incoming player messages          │
│  ├── Reasoning Router: Gemini 2.5 Flash / Local Edge LLM               │
│  └── Action Dispatcher: Dispatches chat replies and in-game movements   │
└────────────────────────────────────────────────────────────────────────┘
```

### Component & State Mapping

1. **`MinecraftProtocolClient`**:
   - Update `queryServerStatus` and `connect` with unified IP:Port last-contact tracking.
   - Refactor `performLoginHandshake` to support the full 1.20.2+ / 1.21.x configuration loop:
     - Send `Login Start` with 128-bit UUID.
     - Handle `Set Compression` (0x03) gracefully.
     - On `Login Success` (0x02), send `Login Acknowledged` (0x03).
     - In `CONFIGURATION` state, automatically respond to `Known Packs`, `Client Info`, and ack `Finish Configuration` (0x02).
     - In `PLAY` state, continuously respond to `Keep Alive` and send `Confirm Teleportation` when position sync packets arrive.
2. **`MindcraftBotEngine`**:
   - Implement automatic throttle cooldown loop: when server responds with `Connection throttled!`, initiate a 5-second countdown with UI progress before cleanly retrying.
   - Implement `onChatMessageReceived`: trigger AI response generation with context awareness (bot position, server version, sender name) and send response to server.
3. **`DashboardScreen` & `ServerQueryScreen`**:
   - Display prominent "Throttle Cooldown" banner when timer is active.
   - Ensure the "Direct Connect" toggle is easily accessible and enabled by default when connecting to a previously queried server.

---

## Verification Plan

1. **Compilation Check**: Run `compile_applet` to ensure zero compilation or syntax errors.
2. **Local JVM Testing**: Run `gradle :app:testDebugUnitTest` to verify protocol parser, UUID generation, and throttle cooldown logic.
3. **Connection Verification**: Validate protocol handshake transitions across mock/real socket streams and confirm the throttled reconnect backoff behaves predictably.
