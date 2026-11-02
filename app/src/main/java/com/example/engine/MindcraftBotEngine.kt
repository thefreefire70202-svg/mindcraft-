package com.example.engine

import com.example.data.db.AppDatabase
import com.example.data.db.BotLogEntity
import com.example.data.db.ErrorReportEntity
import com.example.data.model.AiSettings
import com.example.data.model.BotState
import com.example.data.model.BotStatus
import com.example.data.model.ChatMessage
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.Position3D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

class MindcraftBotEngine(
    private val database: AppDatabase,
    private val aiClient: AiClient,
    private val coroutineScope: CoroutineScope
) {
    val protocolClient = MinecraftProtocolClient()

    private val _botState = MutableStateFlow(
        BotState(inventory = emptyList())
    )
    val botState: StateFlow<BotState> = _botState.asStateFlow()

    private var botLoopJob: Job? = null
    private var telemetryJob: Job? = null
    private var chatListenerJob: Job? = null
    private var reconnectJob: Job? = null
    private var uptimeSeconds = 0L

    fun startBot(
        serverHost: String,
        serverPort: Int,
        botName: String,
        aiSettings: AiSettings,
        directJoin: Boolean = false
    ) {
        if (_botState.value.status == BotStatus.ONLINE) return

        val (cleanHost, effectivePort) = MinecraftProtocolClient.parseHostAndPort(serverHost, serverPort)
        val cleanBotName = botName.trim().ifBlank { "MindcraftBot" }

        _botState.value = _botState.value.copy(
            status = BotStatus.CONNECTING,
            serverHost = cleanHost,
            serverPort = effectivePort,
            botName = cleanBotName,
            currentGoal = "Connecting to $cleanHost:$effectivePort...",
            activeTask = if (directJoin) "Direct TCP login..." else "Checking server status..."
        )

        coroutineScope.launch(Dispatchers.IO) {
            val overrideProtocol = if (_botState.value.autoDetectVersion) null else _botState.value.manualProtocolOverride
            val cached = if (overrideProtocol == null) protocolClient.getCachedProtocol(cleanHost, effectivePort) else null

            var detectedProtocol = overrideProtocol ?: cached?.protocol ?: 0
            var detectedVersionName = cached?.versionName ?: "Auto-Detect"
            var detectedIsSnapshot = cached?.isSnapshot ?: false
            val isLocalHost = cleanHost in listOf("localhost", "127.0.0.1", "10.0.2.2")

            // Bypass ping if in directJoin mode OR if protocol is already known/cached
            if (!directJoin && detectedProtocol <= 0) {
                log(LogLevel.INFO, LogCategory.NETWORK, "Pinging server at $cleanHost:$effectivePort...")
                val statusResult = protocolClient.queryServerStatus(cleanHost, effectivePort)

                if (statusResult.isOnline) {
                    detectedProtocol = statusResult.protocolVersion
                    detectedVersionName = statusResult.versionName
                    detectedIsSnapshot = statusResult.isSnapshot
                    protocolClient.setCachedProtocol(cleanHost, effectivePort, detectedProtocol, detectedVersionName, detectedIsSnapshot)

                    log(
                        LogLevel.SUCCESS,
                        LogCategory.NETWORK,
                        "Server online! Version: ${statusResult.versionName}, Players: ${statusResult.onlinePlayers}/${statusResult.maxPlayers}, Ping: ${statusResult.pingMs}ms"
                    )
                    if (statusResult.motd.isNotBlank()) {
                        log(LogLevel.INFO, LogCategory.NETWORK, "MOTD: ${statusResult.motd}")
                    }

                    _botState.value = _botState.value.copy(
                        serverMotd = statusResult.motd,
                        serverVersion = statusResult.versionName,
                        onlinePlayersCount = statusResult.onlinePlayers,
                        maxPlayersCount = statusResult.maxPlayers,
                        telemetry = _botState.value.telemetry.copy(pingMs = statusResult.pingMs.toInt())
                    )
                } else {
                    log(
                        LogLevel.WARN,
                        LogCategory.NETWORK,
                        "Server ping check: ${statusResult.error ?: "Server did not respond to ping"}. Attempting direct connection..."
                    )
                }
            } else if (detectedProtocol > 0) {
                log(LogLevel.INFO, LogCategory.NETWORK, "Using cached protocol $detectedProtocol ($detectedVersionName) - skipping status ping to avoid server throttle.")
            }

            // Bukkit/Spigot/Paper/Purpur & Aternos enforce connection-throttle (typically 4000-5000ms).
            // Prevent kicks by ensuring at least 5s have passed since the last socket contact.
            val lastContact = protocolClient.getLastContactTimestamp(cleanHost, effectivePort)
            val elapsed = System.currentTimeMillis() - lastContact
            if (!isLocalHost && lastContact > 0 && elapsed < 5000) {
                val remainingMs = 5000 - elapsed
                val waitSeconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
                log(
                    LogLevel.INFO,
                    LogCategory.NETWORK,
                    "Cooldown guard: Waiting ${waitSeconds}s for server throttle cooldown before joining..."
                )
                for (s in waitSeconds downTo 1) {
                    _botState.value = _botState.value.copy(
                        activeTask = "Throttle cooldown: Joining in ${s}s...",
                        currentGoal = "Waiting for server connection cooldown ($s s)..."
                    )
                    delay(1000L)
                }
            }

            val snapshotLabel = if (detectedIsSnapshot) " [Snapshot]" else ""
            log(
                LogLevel.INFO,
                LogCategory.NETWORK,
                "Connecting to $cleanHost:$effectivePort as '$cleanBotName' (Protocol: ${if (detectedProtocol > 0) detectedProtocol.toString() else "Auto-Detect"}$snapshotLabel)..."
            )

            var connectResult = protocolClient.connect(
                rawHost = cleanHost,
                rawPort = effectivePort,
                username = cleanBotName,
                protocolVersionOverride = if (detectedProtocol > 0) detectedProtocol else null,
                knownVersionName = detectedVersionName,
                knownIsSnapshot = detectedIsSnapshot,
                scope = coroutineScope
            )

            // Auto-recovery for server connection throttling (Aternos, Spigot, Paper)
            var throttleRetries = 0
            while (!connectResult.isSuccess && isThrottleError(connectResult.message) && throttleRetries < 4) {
                throttleRetries++
                val waitSecs = 6
                log(
                    LogLevel.WARN,
                    LogCategory.NETWORK,
                    "Server connection throttled (attempt $throttleRetries/4). Anti-spam timer active. Reconnecting in ${waitSecs}s..."
                )
                for (s in waitSecs downTo 1) {
                    _botState.value = _botState.value.copy(
                        status = BotStatus.CONNECTING,
                        activeTask = "Server throttled: Retrying in ${s}s ($throttleRetries/4)...",
                        currentGoal = "Connection throttled - Cooling down ($s s)..."
                    )
                    delay(1000L)
                }
                log(LogLevel.INFO, LogCategory.NETWORK, "Throttle cooldown finished. Reconnecting as '$cleanBotName'...")
                connectResult = protocolClient.connect(
                    rawHost = cleanHost,
                    rawPort = effectivePort,
                    username = cleanBotName,
                    protocolVersionOverride = if (detectedProtocol > 0) detectedProtocol else null,
                    knownVersionName = detectedVersionName,
                    knownIsSnapshot = detectedIsSnapshot,
                    scope = coroutineScope
                )
            }

            if (connectResult.isSuccess) {
                val finalSnapshotLabel = if (connectResult.isSnapshot) " [Snapshot]" else ""
                log(
                    LogLevel.SUCCESS,
                    LogCategory.NETWORK,
                    "Joined ${connectResult.versionName}$finalSnapshotLabel (Protocol: ${connectResult.protocolVersionUsed}). Handshake accepted."
                )

                _botState.value = _botState.value.copy(
                    status = BotStatus.ONLINE,
                    isRealTcpConnected = true,
                    serverVersion = connectResult.versionName,
                    detectedProtocolVersion = connectResult.protocolVersionUsed,
                    isSnapshot = connectResult.isSnapshot,
                    currentGoal = "Connected to ${connectResult.versionName}$finalSnapshotLabel server. Ready for tasks.",
                    activeTask = "In-game session active",
                    chatHistory = listOf(
                        ChatMessage(sender = "System", message = "Connected to $cleanHost:$effectivePort as $cleanBotName (${connectResult.versionName}$finalSnapshotLabel)")
                    )
                )

                // Greet players in server chat
                coroutineScope.launch(Dispatchers.IO) {
                    delay(1200)
                    val wrote = protocolClient.sendChatMessage("Mindcraft bot session is ready. Type !help for supported commands.")
                    log(
                        if (wrote) LogLevel.INFO else LogLevel.WARN,
                        LogCategory.CHAT,
                        if (wrote) "Greeting packet written to the local TCP socket; server-side delivery is not confirmed."
                        else "Greeting was not written; chat transmission is unavailable."
                    )
                }

                startChatListener(aiSettings)
                startLoops(aiSettings)
            } else {
                val errorMsg = connectResult.message
                log(LogLevel.ERROR, LogCategory.NETWORK, "Connection failed: $errorMsg")

                val helpfulHint = when {
                    connectResult.isOnlineModeRestricted -> {
                        "Tip: Go to your Aternos options and enable 'Cracked: ON', then restart your server to allow your bot to join."
                    }
                    isLocalHost -> {
                        "Tip: When running a local Minecraft server on your PC, use host '10.0.2.2' if on the Android emulator, or your PC's local LAN IP (e.g. 192.168.1.X)."
                    }
                    else -> {
                        "Tip: Check if your server is running in the Aternos dashboard and the port is correct."
                    }
                }
                log(LogLevel.INFO, LogCategory.NETWORK, helpfulHint)

                handleEngineError(
                    errorType = "ServerConnectionException",
                    message = errorMsg,
                    exception = connectResult.exception ?: Exception(errorMsg),
                    recoveryAction = "Connection failed. Please check server settings and retry."
                )

                _botState.value = _botState.value.copy(
                    status = BotStatus.ERROR,
                    isRealTcpConnected = false,
                    currentGoal = "Failed: $errorMsg",
                    activeTask = "Connection failed"
                )
            }
        }
    }

    private fun startChatListener(aiSettings: AiSettings) {
        chatListenerJob?.cancel()
        chatListenerJob = coroutineScope.launch(Dispatchers.IO) {
            // Collect spawn position updates from server
            launch {
                protocolClient.spawnPosition.collect { (x, y, z) ->
                    val pos = Position3D(x = x, y = y, z = z)
                    _botState.value = _botState.value.copy(
                        position = pos,
                        hasPositionUpdate = true,
                        activeTask = "In-game at (${x.toInt()}, ${y.toInt()}, ${z.toInt()})"
                    )
                    log(LogLevel.SUCCESS, LogCategory.PERCEPTION, "Spawned in world at (${x.toInt()}, ${y.toInt()}, ${z.toInt()})")
                }
            }

            // Collect TCP disconnect events for instantaneous self-healing
            launch {
                protocolClient.disconnectEvent.collect { reason ->
                    if (_botState.value.status == BotStatus.ONLINE) {
                        _botState.value = _botState.value.copy(
                            isRealTcpConnected = false,
                            activeTask = "TCP connection lost"
                        )
                        log(LogLevel.WARN, LogCategory.NETWORK, "TCP connection dropped by server: $reason")
                        if (_botState.value.isAutoRecoveryEnabled) {
                            triggerAutoReconnect(reason, aiSettings)
                        }
                    }
                }
            }

            launch {
                protocolClient.protocolEvents.collect { event ->
                    log(LogLevel.WARN, LogCategory.NETWORK, event)
                }
            }

            protocolClient.connectionState.collect { phase ->
                _botState.value = _botState.value.copy(protocolState = phase.name)
                log(LogLevel.INFO, LogCategory.NETWORK, "Minecraft protocol state: $phase")
            }

            protocolClient.chatMessages.collect { (sender, message) ->
                val isSelf = sender.equals(_botState.value.botName, ignoreCase = true) || sender.equals("MindcraftBot", ignoreCase = true)
                val chatMsg = ChatMessage(sender = sender, message = message, isBot = isSelf)
                val updatedChats = _botState.value.chatHistory + chatMsg
                _botState.value = _botState.value.copy(chatHistory = updatedChats.takeLast(50))

                log(LogLevel.INFO, LogCategory.CHAT, "<$sender> $message")

                if (isSelf) return@collect

                val cleanMsg = message.trim()
                when {
                    cleanMsg.equals("!help", ignoreCase = true) -> {
                        sendChatReply("[Mindcraft] Available: !help, !coords, !status, !stop, or speak naturally. Mining and crafting are unsupported.", "Help reply")
                    }
                    cleanMsg.equals("!coords", ignoreCase = true) || cleanMsg.equals("!pos", ignoreCase = true) -> {
                        val state = _botState.value
                        if (state.hasPositionUpdate) {
                            val pos = state.position
                            sendChatReply("[Mindcraft] Position: X=${pos.x.toInt()}, Y=${pos.y.toInt()}, Z=${pos.z.toInt()}", "Position reply")
                        } else {
                            sendChatReply("[Mindcraft] Position is unavailable; the server has not supplied a position packet.", "Unavailable position reply")
                        }
                    }
                    cleanMsg.equals("!status", ignoreCase = true) -> {
                        val s = _botState.value
                        sendChatReply("[Mindcraft] Connection: ${s.status}. Health, food and inventory are unavailable because those server packets are not decoded yet.", "Status reply")
                    }
                    cleanMsg.startsWith("!mine", ignoreCase = true) -> {
                        sendChatReply("[Mindcraft] Mining is unsupported; no mining action was performed.", "Unsupported mining reply")
                        log(LogLevel.WARN, LogCategory.ACTION, "$sender requested mining, but no real mining action is available.")
                    }
                    cleanMsg.startsWith("!craft", ignoreCase = true) -> {
                        sendChatReply("[Mindcraft] Crafting is unsupported; no crafting action was performed.", "Unsupported crafting reply")
                        log(LogLevel.WARN, LogCategory.ACTION, "$sender requested crafting, but no real crafting action is available.")
                    }
                    cleanMsg.equals("!stop", ignoreCase = true) -> {
                        _botState.value = _botState.value.copy(activeTask = "Standing by")
                        sendChatReply("[Mindcraft] Automation paused. No gameplay action was performed.", "Stop reply")
                        log(LogLevel.INFO, LogCategory.ACTION, "$sender requested stop")
                    }
                    else -> {
                        // Natural language AI conversation
                        if (_botState.value.isAiAutopilotEnabled) {
                            executeNaturalLanguageCommand(message, aiSettings)
                        }
                    }
                }
            }
        }
    }

    fun stopBot() {
        botLoopJob?.cancel()
        telemetryJob?.cancel()
        chatListenerJob?.cancel()
        reconnectJob?.cancel()
        botLoopJob = null
        telemetryJob = null
        chatListenerJob = null
        reconnectJob = null

        protocolClient.disconnect()

        coroutineScope.launch(Dispatchers.IO) {
            log(LogLevel.INFO, LogCategory.NETWORK, "Disconnected from server.")
            _botState.value = _botState.value.copy(
                status = BotStatus.DISCONNECTED,
                isRealTcpConnected = false,
                activeTask = "Offline",
                currentGoal = "Disconnected"
            )
        }
    }

    fun pauseBot() {
        if (_botState.value.status == BotStatus.ONLINE) {
            _botState.value = _botState.value.copy(status = BotStatus.PAUSED, activeTask = "Paused")
            coroutineScope.launch(Dispatchers.IO) {
                log(LogLevel.INFO, LogCategory.SYSTEM, "Bot paused.")
            }
        } else if (_botState.value.status == BotStatus.PAUSED) {
            _botState.value = _botState.value.copy(status = BotStatus.ONLINE, activeTask = "Active")
            coroutineScope.launch(Dispatchers.IO) {
                log(LogLevel.INFO, LogCategory.SYSTEM, "Bot resumed.")
            }
        }
    }

    fun sendInGameChatMessage(message: String, aiSettings: AiSettings) {
        if (message.isBlank()) return

        coroutineScope.launch(Dispatchers.IO) {
            val playerChat = ChatMessage(sender = "Player", message = message, isBot = false)
            val updatedChats = _botState.value.chatHistory + playerChat
            _botState.value = _botState.value.copy(chatHistory = updatedChats.takeLast(50))

            log(LogLevel.INFO, LogCategory.CHAT, "<Player> $message")

            // Send to real server over TCP if connected
            if (_botState.value.isRealTcpConnected) {
                val wrote = protocolClient.sendChatMessage(message)
                log(
                    if (wrote) LogLevel.INFO else LogLevel.WARN,
                    LogCategory.CHAT,
                    if (wrote) "Chat packet written to the local TCP socket; server-side delivery is not confirmed."
                    else "Chat packet was not transmitted."
                )
            } else {
                log(LogLevel.WARN, LogCategory.CHAT, "Chat was kept locally; bot is not connected to a Minecraft server.")
            }

            // If AI is enabled, process instruction and have the bot reply & execute commands
            if (_botState.value.isAiAutopilotEnabled) {
                executeNaturalLanguageCommand(message, aiSettings)
            }
        }
    }

    fun executeNaturalLanguageCommand(instruction: String, aiSettings: AiSettings) {
        coroutineScope.launch(Dispatchers.IO) {
            _botState.value = _botState.value.copy(
                currentGoal = instruction,
                activeTask = "Planning actions..."
            )

            val prompt = buildMindcraftPrompt(instruction)
            log(LogLevel.AI_THOUGHT, LogCategory.AI_REASONING, "Sending request to ${aiSettings.activeProvider.displayName}...")

            val aiResponse = aiClient.queryBotBrain(prompt, aiSettings.systemPromptPersona, aiSettings)

            if (aiResponse.isSuccess) {
                log(LogLevel.AI_THOUGHT, LogCategory.AI_REASONING, "[AI Plan]: ${aiResponse.thoughtProcess}")

                if (aiResponse.chatMessage.isNotBlank()) {
                    val botChat = ChatMessage(
                        sender = _botState.value.botName,
                        message = aiResponse.chatMessage,
                        isBot = true
                    )
                    val updatedChats = _botState.value.chatHistory + botChat
                    _botState.value = _botState.value.copy(chatHistory = updatedChats.takeLast(50))

                    log(LogLevel.INFO, LogCategory.CHAT, "<${_botState.value.botName}> ${aiResponse.chatMessage}")

                    if (_botState.value.isRealTcpConnected) {
                        val wrote = protocolClient.sendChatMessage(aiResponse.chatMessage)
                        log(
                            if (wrote) LogLevel.INFO else LogLevel.WARN,
                            LogCategory.CHAT,
                            if (wrote) "AI reply packet written to the local TCP socket; server-side delivery is not confirmed."
                            else "AI reply was not transmitted to the local TCP socket."
                        )
                    }
                }

                if (aiResponse.commands.isNotEmpty()) {
                    log(LogLevel.WARN, LogCategory.ACTION, "AI proposed " + aiResponse.commands.size + " game command(s), but command execution is disabled until real protocol actions are implemented.")
                }

                _botState.value = _botState.value.copy(
                    telemetry = _botState.value.telemetry.copy(
                        aiQueriesMade = _botState.value.telemetry.aiQueriesMade + 1,
                        actionsExecuted = _botState.value.telemetry.actionsExecuted
                    )
                )
            } else {
                handleEngineError(
                    errorType = "AiReasoningException",
                    message = aiResponse.errorMessage ?: "AI reasoning failed to generate valid actions.",
                    exception = Exception(aiResponse.errorMessage)
                )
            }
        }
    }

    private suspend fun executeParsedCommand(command: String) {
        log(
            LogLevel.WARN,
            LogCategory.ACTION,
            "Ignored unsupported game command '$command'. No Minecraft action was executed."
        )
    }

    private suspend fun sendChatReply(message: String, context: String) {
        val written = protocolClient.sendChatMessage(message)
        log(
            if (written) LogLevel.INFO else LogLevel.WARN,
            LogCategory.CHAT,
            if (written) "$context packet written to the local TCP socket; server-side delivery is not confirmed."
            else "$context packet was not written to the local TCP socket."
        )
    }

    private fun startLoops(aiSettings: AiSettings) {
        uptimeSeconds = 0L

        telemetryJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                if (_botState.value.status != BotStatus.ONLINE) continue

                uptimeSeconds++
                _botState.value = _botState.value.copy(
                    telemetry = _botState.value.telemetry.copy(
                        uptimeSeconds = uptimeSeconds
                    )
                )
            }
        }

        botLoopJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10000)
                if (_botState.value.status != BotStatus.ONLINE) continue

                if (_botState.value.isRealTcpConnected && !protocolClient.isCurrentlyConnected()) {
                    log(LogLevel.WARN, LogCategory.NETWORK, "Watchdog detected disconnected TCP socket. Initiating auto-reconnect...")
                    if (_botState.value.isAutoRecoveryEnabled) {
                        triggerAutoReconnect("TCP socket disconnected", aiSettings)
                    }
                }
            }
        }
    }

    private fun triggerAutoReconnect(reason: String, aiSettings: AiSettings) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = coroutineScope.launch(Dispatchers.IO) {
            val state = _botState.value
            val host = state.serverHost
            val port = state.serverPort
            val name = state.botName
            if (host.isBlank() || port <= 0) return@launch

            _botState.value = _botState.value.copy(
                status = BotStatus.CONNECTING,
                isRealTcpConnected = false
            )

            val waitSecs = 6
            log(LogLevel.WARN, LogCategory.ERROR_WATCHDOG, "Watchdog: TCP connection lost ($reason). Waiting ${waitSecs}s throttle cooldown...")
            for (s in waitSecs downTo 1) {
                _botState.value = _botState.value.copy(
                    activeTask = "Cooldown: Reconnecting in ${s}s...",
                    currentGoal = "TCP disconnected ($reason) - Retrying in ${s}s..."
                )
                delay(1000L)
            }

            log(LogLevel.INFO, LogCategory.NETWORK, "Watchdog: Reconnecting TCP socket to $host:$port as '$name'...")
            val result = protocolClient.connect(
                rawHost = host,
                rawPort = port,
                username = name,
                protocolVersionOverride = if (state.autoDetectVersion) null else state.manualProtocolOverride,
                knownVersionName = state.serverVersion,
                knownIsSnapshot = state.isSnapshot,
                scope = coroutineScope
            )

            if (result.isSuccess) {
                log(LogLevel.SUCCESS, LogCategory.NETWORK, "Watchdog: Reconnected successfully to ${result.versionName}!")
                _botState.value = _botState.value.copy(
                    status = BotStatus.ONLINE,
                    isRealTcpConnected = true,
                    currentGoal = "Reconnected to server.",
                    activeTask = "In-game session restored"
                )
                delay(1000L)
                sendChatReply("[Mindcraft] Bot connection restored.", "Reconnect reply")
                startChatListener(aiSettings)
                startLoops(aiSettings)
            } else {
                log(LogLevel.ERROR, LogCategory.NETWORK, "Watchdog: Auto-reconnect failed (${result.message}).")
                handleEngineError(
                    errorType = "TcpConnectionLostException",
                    message = "TCP connection lost: ${result.message}",
                    exception = result.exception ?: Exception(result.message),
                    recoveryAction = "TCP dropped. Verify 'connection-throttle: 0' in bukkit.yml and 'timeout-time: 120' in spigot.yml."
                )
                _botState.value = _botState.value.copy(
                    status = BotStatus.ERROR,
                    isRealTcpConnected = false,
                    currentGoal = "Auto-reconnect failed: ${result.message}",
                    activeTask = "Connection failed"
                )
            }
        }
    }

    fun handleEngineError(
        errorType: String,
        message: String,
        exception: Throwable,
        recoveryAction: String? = null
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val stackSnippet = sw.toString().take(250)

            val current = _botState.value
            val strategy = recoveryAction ?: "No automatic remediation was performed."

            log(LogLevel.ERROR, LogCategory.ERROR_WATCHDOG, "[$errorType]: $message")

            val report = ErrorReportEntity(
                errorType = errorType,
                errorMessage = message,
                stackTraceSnippet = stackSnippet,
                botCoordinates = "(${current.position.x.toInt()}, ${current.position.y.toInt()}, ${current.position.z.toInt()})",
                currentGoal = current.currentGoal,
                recoveryStrategyApplied = strategy,
                isResolved = false
            )
            database.errorReportDao().insertErrorReport(report)

            log(LogLevel.RECOVERY, LogCategory.ERROR_WATCHDOG, strategy)
        }
    }

    fun toggleAutoRecovery() {
        val newVal = !_botState.value.isAutoRecoveryEnabled
        _botState.value = _botState.value.copy(isAutoRecoveryEnabled = newVal)
    }

    fun toggleAiAutopilot() {
        val newVal = !_botState.value.isAiAutopilotEnabled
        _botState.value = _botState.value.copy(isAiAutopilotEnabled = newVal)
    }

    fun setVersionDetection(autoDetect: Boolean, manualProtocol: Int) {
        _botState.value = _botState.value.copy(
            autoDetectVersion = autoDetect,
            manualProtocolOverride = manualProtocol
        )
    }

    private fun buildMindcraftPrompt(userGoal: String): String {
        val state = _botState.value

        return """
        Minecraft Bot: ${state.botName}
        Health and food: unavailable (the protocol client does not decode these game-state fields).
        Position: unavailable until a position packet is decoded.
        Inventory: unavailable until inventory packets are decoded.
        User Instruction: "$userGoal"

        Return a concise response for the user. Do not claim to perform Minecraft actions; game-action execution is currently disabled.
        """.trimIndent()
    }

    private suspend fun log(level: LogLevel, category: LogCategory, message: String, details: String? = null) {
        database.botLogDao().insertLog(
            BotLogEntity(
                level = level.name,
                category = category.name,
                message = message,
                details = details
            )
        )
    }

    private fun isThrottleError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("throttled") ||
                lower.contains("throttle") ||
                lower.contains("wait before reconnecting") ||
                lower.contains("reconnect too fast") ||
                lower.contains("connection throttle") ||
                lower.contains("slow down") ||
                lower.contains("too fast") ||
                lower.contains("rate limit")
    }
}
