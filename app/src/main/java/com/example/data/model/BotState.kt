package com.example.data.model

enum class BotStatus {
    DISCONNECTED,
    CONNECTING,
    ONLINE,
    PAUSED,
    ERROR,
    RECOVERING
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR,
    RECOVERY,
    AI_THOUGHT
}

enum class LogCategory {
    SYSTEM,
    NETWORK,
    PERCEPTION,
    AI_REASONING,
    ACTION,
    CHAT,
    COMBAT,
    ERROR_WATCHDOG
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val message: String,
    val isBot: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class InventoryItem(
    val slot: Int,
    val itemId: String,
    val displayName: String,
    val count: Int
)

data class Position3D(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
)

data class BotTelemetry(
    val pingMs: Int = 0,
    val tps: Double = 0.0,
    val cpuPercent: Int = 0,
    val memoryMb: Int = 0,
    val uptimeSeconds: Long = 0,
    val actionsExecuted: Int = 0,
    val aiQueriesMade: Int = 0
)

data class BotState(
    val status: BotStatus = BotStatus.DISCONNECTED,
    val botName: String = "MindcraftBot",
    val serverHost: String = "localhost",
    val serverPort: Int = 25565,
    val serverMotd: String = "",
    val serverVersion: String = "Auto-Detect",
    val detectedProtocolVersion: Int = 0,
    val isSnapshot: Boolean = false,
    val autoDetectVersion: Boolean = true,
    val manualProtocolOverride: Int = 0,
    val onlinePlayersCount: Int = 0,
    val maxPlayersCount: Int = 0,
    val isRealTcpConnected: Boolean = false,
    val health: Int = -1,
    val maxHealth: Int = 20,
    val food: Int = -1,
    val position: Position3D = Position3D(),
    val hasPositionUpdate: Boolean = false,
    val dimension: String = "",
    val biome: String = "",
    val currentGoal: String = "Ready for instructions",
    val activeTask: String = "Idle",
    val inventory: List<InventoryItem> = emptyList(),
    val telemetry: BotTelemetry = BotTelemetry(),
    val chatHistory: List<ChatMessage> = emptyList(),
    val isAutoRecoveryEnabled: Boolean = true,
    val isAiAutopilotEnabled: Boolean = true
)
