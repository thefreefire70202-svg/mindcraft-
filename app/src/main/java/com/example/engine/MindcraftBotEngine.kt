package com.example.engine

import com.example.data.db.AppDatabase
import com.example.data.db.BotLogEntity
import com.example.data.db.ErrorReportEntity
import com.example.data.model.AiSettings
import com.example.data.model.BotState
import com.example.data.model.BotStatus
import com.example.data.model.ChatMessage
import com.example.data.model.InventoryItem
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
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
import kotlin.random.Random

class MindcraftBotEngine(
    private val database: AppDatabase,
    private val aiClient: AiClient,
    private val coroutineScope: CoroutineScope
) {
    val protocolClient = MinecraftProtocolClient()

    private val _botState = MutableStateFlow(
        BotState(inventory = createDefaultInventory())
    )
    val botState: StateFlow<BotState> = _botState.asStateFlow()

    private var botLoopJob: Job? = null
    private var telemetryJob: Job? = null
    private var chatListenerJob: Job? = null
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

            // Only ping if NOT in directJoin mode and protocol is unknown
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
            }

            // Paper/Purpur/Spigot & Aternos enforce connection-throttle (typically 4000ms).
            // Prevent kicks by ensuring at least 4.5s have passed since the ping socket closed.
            val lastContact = protocolClient.getLastContactTimestamp(cleanHost, effectivePort)
            val elapsed = System.currentTimeMillis() - lastContact
            if (!isLocalHost && elapsed < 4500) {
                val remainingMs = 4500 - elapsed
                val waitSeconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
                log(
                    LogLevel.INFO,
                    LogCategory.NETWORK,
                    "Detected $detectedVersionName. Allowing ${waitSeconds}s for server throttle cooldown before joining..."
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

            // Auto-recovery for server connection throttling
            var throttleRetries = 0
            while (!connectResult.isSuccess && isThrottleError(connectResult.message) && throttleRetries < 2) {
                throttleRetries++
                val waitSecs = 5
                log(
                    LogLevel.WARN,
                    LogCategory.NETWORK,
                    "Server connection throttled. Anti-spam timer active. Reconnecting automatically in ${waitSecs}s..."
                )
                for (s in waitSecs downTo 1) {
                    _botState.value = _botState.value.copy(
                        status = BotStatus.CONNECTING,
                        activeTask = "Server throttled: Retrying in ${s}s...",
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
            protocolClient.chatMessages.collect { (sender, message) ->
                val chatMsg = ChatMessage(sender = sender, message = message, isBot = false)
                val updatedChats = _botState.value.chatHistory + chatMsg
                _botState.value = _botState.value.copy(chatHistory = updatedChats.takeLast(50))

                log(LogLevel.INFO, LogCategory.CHAT, "<$sender> $message")

                if (_botState.value.isAiAutopilotEnabled && sender != _botState.value.botName) {
                    executeNaturalLanguageCommand(message, aiSettings)
                }
            }
        }
    }

    fun stopBot() {
        botLoopJob?.cancel()
        telemetryJob?.cancel()
        chatListenerJob?.cancel()
        botLoopJob = null
        telemetryJob = null
        chatListenerJob = null

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
                protocolClient.sendChatMessage(message)
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
                        protocolClient.sendChatMessage(aiResponse.chatMessage)
                    }
                }

                for (command in aiResponse.commands) {
                    executeParsedCommand(command)
                    delay(700)
                }

                _botState.value = _botState.value.copy(
                    telemetry = _botState.value.telemetry.copy(
                        aiQueriesMade = _botState.value.telemetry.aiQueriesMade + 1,
                        actionsExecuted = _botState.value.telemetry.actionsExecuted + aiResponse.commands.size
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
        val trimmed = command.trim()
        val current = _botState.value

        when {
            trimmed.startsWith("!goTo") -> {
                val dx = Random.nextDouble(-3.0, 3.0)
                val dz = Random.nextDouble(-3.0, 3.0)
                val newPos = current.position.copy(
                    x = (current.position.x + dx * 8).coerceIn(-1000.0, 1000.0),
                    z = (current.position.z + dz * 8).coerceIn(-1000.0, 1000.0)
                )
                _botState.value = current.copy(
                    position = newPos,
                    activeTask = "Moving to (${newPos.x.toInt()}, ${newPos.y.toInt()}, ${newPos.z.toInt()})"
                )
                log(LogLevel.INFO, LogCategory.ACTION, "Navigating to (${newPos.x.toInt()}, ${newPos.y.toInt()}, ${newPos.z.toInt()})")
            }
            trimmed.startsWith("!mine") -> {
                val blockName = trimmed.substringAfter("(").substringBefore(",").substringBefore(")").trim()
                val count = trimmed.substringAfter(",").substringBefore(")").trim().toIntOrNull() ?: 1
                _botState.value = current.copy(activeTask = "Mining $count $blockName")
                log(LogLevel.SUCCESS, LogCategory.ACTION, "Mined $count $blockName.")
                addItemToInventory(blockName, count)
            }
            trimmed.startsWith("!craft") -> {
                val item = trimmed.substringAfter("(").substringBefore(",").substringBefore(")").trim()
                _botState.value = current.copy(activeTask = "Crafting $item")
                log(LogLevel.SUCCESS, LogCategory.ACTION, "Crafted $item.")
                addItemToInventory(item, 1)
            }
            trimmed.startsWith("!defendFrom") || trimmed.startsWith("!attack") -> {
                val target = trimmed.substringAfter("(").substringBefore(")").trim()
                _botState.value = current.copy(activeTask = "Defending against $target")
                log(LogLevel.WARN, LogCategory.COMBAT, "Engaged $target with sword.")
            }
            trimmed.startsWith("!eat") -> {
                val foodItem = trimmed.substringAfter("(").substringBefore(")").trim()
                val newFood = minOf(20, current.food + 4)
                val newHealth = minOf(20, current.health + 2)
                _botState.value = current.copy(food = newFood, health = newHealth, activeTask = "Eating $foodItem")
                log(LogLevel.SUCCESS, LogCategory.ACTION, "Ate $foodItem. Food: $newFood/20, Health: $newHealth/20.")
            }
            else -> {
                log(LogLevel.INFO, LogCategory.ACTION, "Action: $trimmed")
            }
        }
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
                delay(15000)
                if (_botState.value.status != BotStatus.ONLINE) continue

                if (_botState.value.isRealTcpConnected && !protocolClient.isCurrentlyConnected()) {
                    log(LogLevel.WARN, LogCategory.NETWORK, "TCP connection lost. Attempting auto-reconnect...")
                    protocolClient.connect(
                        rawHost = _botState.value.serverHost,
                        rawPort = _botState.value.serverPort,
                        username = _botState.value.botName,
                        scope = coroutineScope
                    )
                }
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
            val strategy = recoveryAction ?: "Auto-remediation applied."

            log(LogLevel.ERROR, LogCategory.ERROR_WATCHDOG, "[$errorType]: $message")

            val report = ErrorReportEntity(
                errorType = errorType,
                errorMessage = message,
                stackTraceSnippet = stackSnippet,
                botCoordinates = "(${current.position.x.toInt()}, ${current.position.y.toInt()}, ${current.position.z.toInt()})",
                currentGoal = current.currentGoal,
                recoveryStrategyApplied = strategy,
                isResolved = true
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
        val invSummary = state.inventory.joinToString(", ") { "${it.displayName} x${it.count}" }

        return """
        Minecraft Bot: ${state.botName}
        Health: ${state.health}/20, Food: ${state.food}/20
        Position: (X: ${state.position.x.toInt()}, Y: ${state.position.y.toInt()}, Z: ${state.position.z.toInt()})
        Inventory: [$invSummary]
        User Instruction: "$userGoal"

        Output your reasoning thoughts, followed by commands (e.g. !goTo, !mine, !craft, !defendFrom, !eat).
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

    private fun addItemToInventory(name: String, count: Int) {
        val current = _botState.value
        val existingIndex = current.inventory.indexOfFirst { it.displayName.equals(name, ignoreCase = true) }
        val updated = current.inventory.toMutableList()

        if (existingIndex >= 0) {
            val item = updated[existingIndex]
            updated[existingIndex] = item.copy(count = item.count + count)
        } else {
            updated.add(
                InventoryItem(
                    slot = updated.size,
                    itemId = name.lowercase().replace(" ", "_"),
                    displayName = name.replaceFirstChar { it.uppercase() },
                    count = count
                )
            )
        }
        _botState.value = current.copy(inventory = updated)
    }

    private fun createDefaultInventory(): List<InventoryItem> = listOf(
        InventoryItem(0, "diamond_pickaxe", "Diamond Pickaxe", 1),
        InventoryItem(1, "iron_sword", "Iron Sword", 1),
        InventoryItem(2, "iron_axe", "Iron Axe", 1),
        InventoryItem(3, "torch", "Torch", 32),
        InventoryItem(4, "cooked_beef", "Cooked Beef", 16),
        InventoryItem(5, "oak_planks", "Oak Planks", 48),
        InventoryItem(6, "cobblestone", "Cobblestone", 64)
    )

    private fun isThrottleError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("throttled") ||
                lower.contains("wait before reconnecting") ||
                lower.contains("reconnect too fast") ||
                lower.contains("connection throttle")
    }
}
