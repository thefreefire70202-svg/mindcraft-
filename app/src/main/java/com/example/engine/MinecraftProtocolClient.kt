package com.example.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.Inflater

data class ServerStatusResult(
    val isOnline: Boolean,
    val motd: String = "",
    val versionName: String = "",
    val protocolVersion: Int = 0,
    val onlinePlayers: Int = 0,
    val maxPlayers: Int = 0,
    val pingMs: Long = 0,
    val isSnapshot: Boolean = false,
    val rawJson: String = "",
    val error: String? = null
)

data class CachedServerProtocol(
    val protocol: Int,
    val versionName: String,
    val isSnapshot: Boolean,
    val timestamp: Long
)

data class ConnectResult(
    val isSuccess: Boolean,
    val message: String,
    val protocolVersionUsed: Int,
    val versionName: String,
    val isSnapshot: Boolean,
    val isOnlineModeRestricted: Boolean = false,
    val exception: Throwable? = null
)

enum class ProtocolState {
    TCP_CONNECTED,
    HANDSHAKE_SENT,
    LOGIN_STARTED,
    LOGIN_SUCCESS,
    CONFIGURATION,
    PLAY_READY,
    DISCONNECTED,
    ERROR
}

/**
 * Packet IDs sourced from the versioned PrismarineJS minecraft-data schema.
 * This client intentionally enables only protocol 767 (Java 1.21/1.21.1)
 * until additional versions have complete packet definitions and test coverage.
 */
private data class ProtocolPacketIds(
    val loginStart: Int,
    val loginSuccess: Int,
    val loginDisconnect: Int,
    val loginEncryptionRequest: Int,
    val loginCompression: Int,
    val loginPluginRequest: Int,
    val loginCookieRequest: Int,
    val loginAcknowledged: Int,
    val loginPluginResponse: Int,
    val loginCookieResponse: Int,
    val configCookieRequest: Int,
    val configPluginMessage: Int,
    val configDisconnect: Int,
    val configFinish: Int,
    val configKeepAlive: Int,
    val configPing: Int,
    val configKnownPacks: Int,
    val configClientInformation: Int,
    val configCookieResponse: Int,
    val configPluginResponse: Int,
    val configFinishAck: Int,
    val configKeepAliveResponse: Int,
    val configPong: Int,
    val configSelectKnownPacks: Int,
    val playKeepAlive: Int,
    val playPing: Int,
    val playPlayerChat: Int,
    val playPosition: Int,
    val playSystemChat: Int,
    val playDisconnect: Int,
    val playStartConfiguration: Int,
    val playConfigurationAcknowledged: Int,
    val playTeleportConfirm: Int,
    val playChatMessage: Int,
    val playKeepAliveResponse: Int,
    val playPositionRotation: Int,
    val playBlockDig: Int,
    val playBlockPlace: Int,
    val playPong: Int,
    val playClientInformation: Int
)

private object ProtocolPacketRegistry {
    fun forProtocol(protocol: Int): ProtocolPacketIds? = when (protocol) {
        767 -> ProtocolPacketIds(
            loginStart = 0x00,
            loginSuccess = 0x02,
            loginDisconnect = 0x00,
            loginEncryptionRequest = 0x01,
            loginCompression = 0x03,
            loginPluginRequest = 0x04,
            loginCookieRequest = 0x05,
            loginAcknowledged = 0x03,
            loginPluginResponse = 0x02,
            loginCookieResponse = 0x04,
            configCookieRequest = 0x00,
            configPluginMessage = 0x01,
            configDisconnect = 0x02,
            configFinish = 0x03,
            configKeepAlive = 0x04,
            configPing = 0x05,
            configKnownPacks = 0x0E,
            configClientInformation = 0x00,
            configCookieResponse = 0x01,
            configPluginResponse = 0x02,
            configFinishAck = 0x03,
            configKeepAliveResponse = 0x04,
            configPong = 0x05,
            configSelectKnownPacks = 0x07,
            playKeepAlive = 0x26,
            playPing = 0x35,
            playPlayerChat = 0x39,
            playPosition = 0x40,
            playSystemChat = 0x6C,
            playDisconnect = 0x1D,
            playStartConfiguration = 0x69,
            playConfigurationAcknowledged = 0x0C,
            playTeleportConfirm = 0x00,
            playChatMessage = 0x06,
            playKeepAliveResponse = 0x18,
            playPositionRotation = 0x1B,
            playBlockDig = 0x24,
            playBlockPlace = 0x38,
            playPong = 0x2D,
            playClientInformation = 0x0E
        )
        else -> null
    }
}

class MinecraftProtocolClient {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var compressionThreshold = -1
    private var currentState = ProtocolState.DISCONNECTED
    private val _connectionState = kotlinx.coroutines.flow.MutableStateFlow(ProtocolState.DISCONNECTED)
    val connectionState: kotlinx.coroutines.flow.StateFlow<ProtocolState> = _connectionState.asStateFlow()
    private var packetListenerJob: Job? = null
    private var lastPlayerPosition: Triple<Double, Double, Double>? = null
    private var blockChangeSequence: Int = 0
    @Volatile private var lastPlayerYaw: Float = 0f
    @Volatile private var lastPlayerPitch: Float = 0f

    private fun setConnectionState(state: ProtocolState) {
        currentState = state
        _connectionState.value = state
    }

    private val protocolCache = ConcurrentHashMap<String, CachedServerProtocol>()
    private val lastContactTimestamps = ConcurrentHashMap<String, Long>()

    var currentProtocolVersion: Int = 0
        private set
    var currentVersionName: String = "Auto-Detect"
        private set

    private val _chatMessages = MutableSharedFlow<Pair<String, String>>(replay = 50)
    val chatMessages: SharedFlow<Pair<String, String>> = _chatMessages.asSharedFlow()

    private val _spawnPosition = MutableSharedFlow<Triple<Double, Double, Double>>(replay = 1)
    val spawnPosition: SharedFlow<Triple<Double, Double, Double>> = _spawnPosition.asSharedFlow()

    private val _disconnectEvent = MutableSharedFlow<String>(replay = 1)
    val disconnectEvent: SharedFlow<String> = _disconnectEvent.asSharedFlow()

    private val _protocolEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val protocolEvents: SharedFlow<String> = _protocolEvents.asSharedFlow()

    companion object {
        private const val MAX_PACKET_SIZE = 2 * 1024 * 1024

        fun parseHostAndPort(rawHost: String, defaultPort: Int = 25565): Pair<String, Int> {
            val clean = rawHost.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("minecraft://")
            .trimEnd('/')
        require(defaultPort in 1..65535) { "Port must be between 1 and 65535." }

        if (clean.startsWith("[")) {
            val closingBracket = clean.indexOf(']')
            require(closingBracket > 1) { "Invalid bracketed IPv6 address." }
            val host = clean.substring(1, closingBracket)
            val suffix = clean.substring(closingBracket + 1)
            val port = if (suffix.isBlank()) defaultPort else {
                require(suffix.startsWith(":")) { "Expected :port after bracketed IPv6 address." }
                suffix.drop(1).toIntOrNull()?.also { require(it in 1..65535) }
                    ?: throw IllegalArgumentException("Invalid server port.")
            }
            return Pair(host, port)
        }

        val colonCount = clean.count { it == ':' }
        if (colonCount > 1) return Pair(clean, defaultPort)
        if (colonCount == 1) {
            val separator = clean.lastIndexOf(':')
            val host = clean.substring(0, separator).trim()
            val port = clean.substring(separator + 1).trim().toIntOrNull()
                ?: throw IllegalArgumentException("Invalid server port.")
            require(host.isNotBlank() && port in 1..65535) { "Invalid server host or port." }
            return Pair(host, port)
        }
        return Pair(clean, defaultPort)
        }
    }

    fun getCachedProtocol(host: String, port: Int): CachedServerProtocol? {
        val (h, p) = parseHostAndPort(host, port)
        return protocolCache["$h:$p"]
    }

    fun setCachedProtocol(host: String, port: Int, protocol: Int, versionName: String, isSnapshot: Boolean) {
        val (h, p) = parseHostAndPort(host, port)
        val now = System.currentTimeMillis()
        protocolCache["$h:$p"] = CachedServerProtocol(protocol, versionName, isSnapshot, now)
        lastContactTimestamps["$h:$p"] = now
    }

    fun getLastContactTimestamp(host: String, port: Int): Long {
        val (h, p) = parseHostAndPort(host, port)
        return lastContactTimestamps["$h:$p"] ?: 0L
    }

    fun updateLastContactTimestamp(host: String, port: Int) {
        val (h, p) = parseHostAndPort(host, port)
        lastContactTimestamps["$h:$p"] = System.currentTimeMillis()
    }

    /**
     * Real Minecraft Server List Ping (SLP) over raw TCP socket.
     * Works on release servers, paper/purpur, snapshot servers, and Aternos.
     */
    suspend fun queryServerStatus(rawHost: String, rawPort: Int = 25565, timeoutMs: Int = 7500): ServerStatusResult =
        withContext(Dispatchers.IO) {
            val (host, port) = parseHostAndPort(rawHost, rawPort)
            val targetHost = if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") "10.0.2.2" else host
            val startTime = System.currentTimeMillis()
            var s: Socket? = null
            try {
                s = Socket()
                s.tcpNoDelay = true
                s.soTimeout = timeoutMs
                s.connect(InetSocketAddress(targetHost, port), timeoutMs)

                val inStream = s.getInputStream()
                val outStream = s.getOutputStream()

                // 1. Send Handshake Packet (ID: 0x00, NextState: 1 = Status)
                // Status handshake protocol -1 is version-independent; the server reports its actual protocol in the JSON response.
                val handshakeBytes = ByteArrayOutputStream()
                writeVarInt(handshakeBytes, 0x00) // Packet ID
                writeVarInt(handshakeBytes, -1)  // Unknown protocol for version-independent status ping
                writeString(handshakeBytes, host) // Server hostname
                handshakeBytes.write((port shr 8) and 0xFF)
                handshakeBytes.write(port and 0xFF)
                writeVarInt(handshakeBytes, 1)    // Next State = 1 (Status)

                sendRawPacket(outStream, handshakeBytes.toByteArray())

                // 2. Send Status Request Packet (ID: 0x00, empty body)
                val statusReq = ByteArrayOutputStream()
                writeVarInt(statusReq, 0x00)
                sendRawPacket(outStream, statusReq.toByteArray())

                // 3. Read Status Response Packet
                val packetLength = readVarInt(inStream)
                val packetId = readVarInt(inStream)
                if (packetId != 0x00) {
                    throw IllegalStateException("Unexpected server status packet ID: $packetId")
                }
                val jsonResponse = readString(inStream)

                val ping = System.currentTimeMillis() - startTime

                // Parse Server JSON response
                val json = JSONObject(jsonResponse)
                val versionObj = json.optJSONObject("version")
                val versionName = versionObj?.optString("name", "Minecraft Server") ?: "Minecraft Server"
                val protocolVer = versionObj?.optInt("protocol", 0) ?: 0

                val playersObj = json.optJSONObject("players")
                val onlinePlayers = playersObj?.optInt("online", 0) ?: 0
                val maxPlayers = playersObj?.optInt("max", 0) ?: 0

                val descObj = json.opt("description")
                val motd = parseDescription(descObj)

                if (protocolVer <= 0) {
                    return@withContext ServerStatusResult(
                        isOnline = false,
                        error = "Server status response did not include a valid protocol version."
                    )
                }
                val snapshot = isSnapshotVersion(versionName, protocolVer)

                setCachedProtocol(host, port, protocolVer, versionName, snapshot)

                ServerStatusResult(
                    isOnline = true,
                    motd = cleanFormattingCodes(motd),
                    versionName = versionName,
                    protocolVersion = protocolVer,
                    onlinePlayers = onlinePlayers,
                    maxPlayers = maxPlayers,
                    pingMs = ping,
                    isSnapshot = snapshot,
                    rawJson = jsonResponse
                )
            } catch (e: Exception) {
                lastContactTimestamps["$host:$port"] = System.currentTimeMillis()
                val friendlyError = when {
                    e is java.net.UnknownHostException -> "Could not resolve hostname '$host'. Check server address."
                    e is java.net.ConnectException -> "Connection refused. Server may be offline (start it in your Aternos panel) or port $port is closed."
                    e is java.net.SocketTimeoutException -> "Connection timed out after ${timeoutMs}ms. Server may be offline or starting up."
                    e is EOFException -> "Server closed connection during status check (check online-mode or firewall)."
                    else -> e.localizedMessage ?: "Failed to reach server"
                }
                ServerStatusResult(
                    isOnline = false,
                    error = friendlyError
                )
            } finally {
                try {
                    s?.close()
                } catch (e: Exception) {
                    _protocolEvents.tryEmit("Status socket cleanup failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

    /**
     * Connect to real Minecraft server with auto-detection of server version & protocol.
     * Uses cached status when available to eliminate redundant TCP connections that trigger server throttles.
     */
    suspend fun connect(
        rawHost: String,
        rawPort: Int = 25565,
        username: String = "MindcraftBot",
        protocolVersionOverride: Int? = null,
        knownVersionName: String? = null,
        knownIsSnapshot: Boolean? = null,
        timeoutMs: Int = 9000,
        scope: CoroutineScope? = null
    ): ConnectResult = withContext(Dispatchers.IO) {
        disconnect()

        val (host, port) = parseHostAndPort(rawHost, rawPort)

        // Throttle Guard: Paper, Purpur, Spigot, and Aternos enforce a connection throttle (typically 4000-5000ms).
        // Ensure at least 5000ms have passed since the last socket contact on this host:port to prevent Bukkit kicks.
        val lastContact = getLastContactTimestamp(host, port)
        val elapsed = System.currentTimeMillis() - lastContact
        if (lastContact > 0 && elapsed < 5000L && host !in listOf("localhost", "127.0.0.1", "10.0.2.2")) {
            val waitMs = (5000L - elapsed).coerceIn(0L, 5000L)
            kotlinx.coroutines.delay(waitMs)
        }

        var detectedProtocol = protocolVersionOverride ?: 0
        var detectedName = knownVersionName ?: "Auto-Detect"
        var isSnapshot = knownIsSnapshot ?: false

        // 1. Check cache first to avoid ANY redundant network socket
        if (detectedProtocol <= 0) {
            val cached = getCachedProtocol(host, port)
            if (cached != null) {
                detectedProtocol = cached.protocol
                detectedName = cached.versionName
                isSnapshot = cached.isSnapshot
            }
        }

        // A direct join still needs real version detection. Never guess a protocol.
        if (detectedProtocol <= 0) {
            val status = queryServerStatus(host, port, timeoutMs.coerceAtLeast(7500))
            if (!status.isOnline || status.protocolVersion <= 0) {
                setConnectionState(ProtocolState.ERROR)
                return@withContext ConnectResult(
                    isSuccess = false,
                    message = "Server protocol could not be detected. Select a Minecraft version manually or make the server status endpoint reachable. No login packet was sent.",
                    protocolVersionUsed = 0,
                    versionName = "Unknown",
                    isSnapshot = false,
                    exception = IllegalStateException(status.error ?: "Server did not provide a valid protocol version.")
                )
            }
            detectedProtocol = status.protocolVersion
            detectedName = status.versionName
            isSnapshot = status.isSnapshot
            setCachedProtocol(host, port, detectedProtocol, detectedName, isSnapshot)
        }

        val packetIds = ProtocolPacketRegistry.forProtocol(detectedProtocol)
        if (packetIds == null) {
            setConnectionState(ProtocolState.ERROR)
            return@withContext ConnectResult(
                isSuccess = false,
                message = "Minecraft protocol $detectedProtocol ($detectedName) is not supported by this build. Only protocol 767 has a packet registry; no login packet was sent.",
                protocolVersionUsed = detectedProtocol,
                versionName = detectedName,
                isSnapshot = isSnapshot,
                exception = UnsupportedOperationException("No packet registry for protocol $detectedProtocol")
            )
        }

        currentProtocolVersion = detectedProtocol
        currentVersionName = detectedName

        // 3. Perform connection with the version-specific packet registry
        val attemptResult = performLoginHandshake(host, port, username, detectedProtocol, detectedName, isSnapshot, timeoutMs, scope)

        attemptResult
    }

    private fun performLoginHandshake(
        host: String,
        port: Int,
        username: String,
        protocol: Int,
        versionName: String,
        isSnapshot: Boolean,
        timeoutMs: Int,
        scope: CoroutineScope?
    ): ConnectResult {
        return try {
            val targetHost = if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") "10.0.2.2" else host
            val s = Socket()
            s.tcpNoDelay = true
            s.keepAlive = true
            s.soTimeout = timeoutMs
            s.connect(InetSocketAddress(targetHost, port), timeoutMs)
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()
            isConnected = true
            compressionThreshold = -1
            setConnectionState(ProtocolState.TCP_CONNECTED)
            lastContactTimestamps["$host:$port"] = System.currentTimeMillis()

            // 1. Send Handshake Packet with NextState = 2 (Login)
            val handshakeBytes = ByteArrayOutputStream()
            writeVarInt(handshakeBytes, 0x00) // Packet ID
            writeVarInt(handshakeBytes, protocol) // Auto-detected protocol from the detected server version
            writeString(handshakeBytes, host)
            handshakeBytes.write((port shr 8) and 0xFF)
            handshakeBytes.write(port and 0xFF)
            writeVarInt(handshakeBytes, 2) // Next State = 2 (Login)

            sendRawPacket(outputStream!!, handshakeBytes.toByteArray())
            setConnectionState(ProtocolState.HANDSHAKE_SENT)

            // 2. Send Login Start Packet (Packet ID 0x00 in Login State)
            val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
            val loginBytes = ByteArrayOutputStream()
            val packetIds = ProtocolPacketRegistry.forProtocol(protocol)
                ?: throw UnsupportedOperationException("Protocol $protocol has no packet registry")
            writeVarInt(loginBytes, packetIds.loginStart)
            writeString(loginBytes, username)

            if (protocol >= 764) {
                // 1.20.2+ and 1.21.x sends full 128-bit UUID (16 bytes)
                val bb = ByteBuffer.allocate(16)
                bb.putLong(offlineUuid.mostSignificantBits)
                bb.putLong(offlineUuid.leastSignificantBits)
                loginBytes.write(bb.array())
            } else if (protocol >= 761) {
                // 1.19.3 - 1.20.1
                loginBytes.write(0) // Has sig data = false
                loginBytes.write(1) // Has UUID = true
                val bb = ByteBuffer.allocate(16)
                bb.putLong(offlineUuid.mostSignificantBits)
                bb.putLong(offlineUuid.leastSignificantBits)
                loginBytes.write(bb.array())
            }

            sendProtocolPacket(loginBytes.toByteArray())
            setConnectionState(ProtocolState.LOGIN_STARTED)

            // 3. Read Login Response packets until Login Success (0x02) or error
            var loginSuccess = false
            var loginResult: ConnectResult? = null

            while ((!loginSuccess || currentState != ProtocolState.PLAY_READY) && isCurrentlyConnected()) {
                val (packetId, payload) = readNextPacket()

                // Login Success is not a completed session on 1.20.2+.
                // Keep consuming Configuration packets until the server sends Finish Configuration.
                if (loginSuccess && protocol >= 764) {
                    handleConfigurationPacket(packetId, payload, protocol)
                    if (currentState == ProtocolState.PLAY_READY) {
                        loginResult = ConnectResult(
                            isSuccess = true,
                            message = "Joined $versionName server (Protocol: $protocol) as '$username'. Configuration completed.",
                            protocolVersionUsed = protocol,
                            versionName = versionName,
                            isSnapshot = isSnapshot
                        )
                        break
                    }
                    continue
                }

                when (packetId) {
                    packetIds.loginDisconnect -> {
                        // Disconnected by server
                        val reasonStream = ByteArrayInputStream(payload)
                        val rawReason = readString(reasonStream)
                        val cleanReason = parseDisconnectReason(rawReason)
                        disconnect()
                        setConnectionState(ProtocolState.ERROR)
                        loginResult = ConnectResult(
                            isSuccess = false,
                            message = cleanReason,
                            protocolVersionUsed = protocol,
                            versionName = versionName,
                            isSnapshot = isSnapshot,
                            exception = Exception(cleanReason)
                        )
                        break
                    }
                    packetIds.loginEncryptionRequest -> {
                        // Encryption Request (Server requires premium Mojang auth / online-mode=true)
                        disconnect()
                        setConnectionState(ProtocolState.ERROR)
                        loginResult = ConnectResult(
                            isSuccess = false,
                            isOnlineModeRestricted = true,
                            message = "Server is in Online Mode (Mojang Auth required). To allow Mindcraft Bot to join your Aternos server: Open Aternos -> Options -> Set 'Cracked: ON' -> Restart server.",
                            protocolVersionUsed = protocol,
                            versionName = versionName,
                            isSnapshot = isSnapshot
                        )
                        break
                    }
                    packetIds.loginSuccess -> {
                        // Login Success. Modern protocol versions must finish Configuration before success.
                        loginSuccess = true
                        setConnectionState(ProtocolState.LOGIN_SUCCESS)
                        if (protocol >= 764) {
                            val ackBytes = ByteArrayOutputStream()
                            writeVarInt(ackBytes, packetIds.loginAcknowledged) // Serverbound Login Acknowledged
                            sendProtocolPacket(ackBytes.toByteArray())
                            setConnectionState(ProtocolState.CONFIGURATION)
                            sendClientInformation(isConfigurationState = true)
                            socket?.soTimeout = 45000
                        } else {
                            setConnectionState(ProtocolState.PLAY_READY)
                            socket?.soTimeout = 45000
                            loginResult = ConnectResult(
                                isSuccess = true,
                                message = "Joined $versionName server (Protocol: $protocol) as '$username'.",
                                protocolVersionUsed = protocol,
                                versionName = versionName,
                                isSnapshot = isSnapshot
                            )
                            break
                        }
                    }
                    packetIds.loginCompression -> {
                        // Set Compression
                        val thresholdStream = ByteArrayInputStream(payload)
                        compressionThreshold = readVarInt(thresholdStream)
                    }
                    packetIds.loginPluginRequest -> {
                        // Login Plugin Request (Custom Query) -> Answer Login Plugin Response (Packet 0x02, messageId, successful = false)
                        val queryStream = ByteArrayInputStream(payload)
                        val messageId = readVarInt(queryStream)
                        val channel = readString(queryStream)
                        _protocolEvents.tryEmit("Login plugin request '$channel' was declined because plugin authentication is unsupported.")
                        val reply = ByteArrayOutputStream()
                        writeVarInt(reply, packetIds.loginPluginResponse)
                        writeVarInt(reply, messageId)
                        reply.write(0)
                        sendProtocolPacket(reply.toByteArray())
                    }
                    packetIds.loginCookieRequest -> {
                        // Cookie Request (1.20.5+) -> Answer Cookie Response (Packet 0x04, key, hasPayload = false)
                        val cookieStream = ByteArrayInputStream(payload)
                        val key = readString(cookieStream)
                        val reply = ByteArrayOutputStream()
                        writeVarInt(reply, packetIds.loginCookieResponse)
                        writeString(reply, key)
                        reply.write(0)
                        sendProtocolPacket(reply.toByteArray())
                    }
                    else -> _protocolEvents.tryEmit("Received unhandled protocol 767 login packet id 0x${packetId.toString(16)}; payload was skipped.")
                }
            }

            val completedResult = loginResult ?: ConnectResult(
                isSuccess = false,
                message = if (loginSuccess && protocol >= 764) {
                    "Login succeeded, but Minecraft Configuration did not complete."
                } else {
                    "Login timed out or connection closed before Login Success."
                },
                protocolVersionUsed = protocol,
                versionName = versionName,
                isSnapshot = isSnapshot
            )

            if (completedResult.isSuccess && scope != null) {
                startPacketListenerLoop(scope, protocol)
            } else if (!completedResult.isSuccess) {
                disconnect()
                setConnectionState(ProtocolState.ERROR)
            }
            completedResult
        } catch (e: Exception) {
            disconnect()
            setConnectionState(ProtocolState.ERROR)
            ConnectResult(
                isSuccess = false,
                message = e.localizedMessage ?: "Failed to connect to server",
                protocolVersionUsed = protocol,
                versionName = versionName,
                isSnapshot = isSnapshot,
                exception = e
            )
        }
    }

    fun sendClientInformation(isConfigurationState: Boolean) {
            val bytes = ByteArrayOutputStream()
            // In CONFIGURATION state, Client Information is packet 0x00
            // Packet IDs are selected from the protocol-specific registry.
            val packetIds = ProtocolPacketRegistry.forProtocol(currentProtocolVersion)
                ?: throw UnsupportedOperationException("No client-information packet registry for protocol $currentProtocolVersion")
            writeVarInt(bytes, if (isConfigurationState) packetIds.configClientInformation else packetIds.playClientInformation)
            writeString(bytes, "en_us")
            bytes.write(8) // view distance = 8 chunks
            writeVarInt(bytes, 0) // chat mode: 0 (enabled)
            bytes.write(1) // chat colors: true
            bytes.write(0x7F) // displayed skin parts: 127 (all enabled)
            writeVarInt(bytes, 1) // main hand: right (1)
            bytes.write(0) // text filtering: false
            bytes.write(1) // allow server listings: true
            sendProtocolPacket(bytes.toByteArray())
    }

    private fun startPacketListenerLoop(scope: CoroutineScope, protocol: Int) {
        packetListenerJob?.cancel()
        packetListenerJob = scope.launch(Dispatchers.IO) {
            var disconnectReason = "Connection closed normally"
            try {
                while (isActive && isCurrentlyConnected()) {
                    val (packetId, payload) = try {
                        readNextPacket()
                    } catch (e: java.net.SocketTimeoutException) {
                        disconnectReason = "TCP connection timed out (no packets received from server for 45s)"
                        break
                    } catch (e: EOFException) {
                        disconnectReason = "TCP connection lost: server closed socket (EOF)"
                        break
                    } catch (e: java.net.SocketException) {
                        disconnectReason = "TCP connection reset: ${e.message}"
                        break
                    } catch (e: Exception) {
                        disconnectReason = "TCP stream error: ${e.localizedMessage ?: "Unknown network error"}"
                        break
                    }

                    when (currentState) {
                        ProtocolState.CONFIGURATION -> {
                            handleConfigurationPacket(packetId, payload, protocol)
                        }
                        ProtocolState.PLAY_READY -> {
                            handlePlayPacket(packetId, payload, protocol)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                disconnectReason = "TCP error: ${e.localizedMessage}"
            } finally {
                isConnected = false
                setConnectionState(
                    if (disconnectReason == "Connection closed normally") ProtocolState.DISCONNECTED
                    else ProtocolState.ERROR
                )
                try {
                    socket?.close()
                } catch (e: Exception) {
                    _protocolEvents.tryEmit("Listener socket cleanup failed: ${e.message ?: e.javaClass.simpleName}")
                }
                socket = null
                inputStream = null
                outputStream = null
                compressionThreshold = -1
                _disconnectEvent.tryEmit(disconnectReason)
            }
        }
    }

    private fun handleConfigurationPacket(packetId: Int, payload: ByteArray, protocol: Int) {
        val ids = ProtocolPacketRegistry.forProtocol(protocol)
            ?: throw UnsupportedOperationException("No configuration registry for protocol $protocol")
        val stream = ByteArrayInputStream(payload)
        when (packetId) {
            ids.configCookieRequest -> {
                val key = readString(stream)
                val reply = ByteArrayOutputStream()
                writeVarInt(reply, ids.configCookieResponse)
                writeString(reply, key)
                reply.write(0)
                sendProtocolPacket(reply.toByteArray())
            }
            ids.configPluginMessage -> handleConfigPluginMessage(stream, protocol)
            ids.configDisconnect -> {
                _protocolEvents.tryEmit("Server sent a configuration disconnect packet. Its NBT reason component is not decoded by this client.")
                throw EOFException("Server disconnected during configuration; NBT reason decoding is unsupported.")
            }
            ids.configFinish -> finishConfigurationAck(protocol)
            ids.configKeepAlive -> {
                val id = readLong(stream)
                val reply = ByteArrayOutputStream()
                writeVarInt(reply, ids.configKeepAliveResponse)
                writeLong(reply, id)
                sendProtocolPacket(reply.toByteArray())
            }
            ids.configPing -> {
                val pingId = readInt(stream)
                val pong = ByteArrayOutputStream()
                writeVarInt(pong, ids.configPong)
                writeInt(pong, pingId)
                sendProtocolPacket(pong.toByteArray())
            }
            ids.configKnownPacks -> {
                // Select no optional packs. This is a valid explicit response.
                val reply = ByteArrayOutputStream()
                writeVarInt(reply, ids.configSelectKnownPacks)
                writeVarInt(reply, 0)
                sendProtocolPacket(reply.toByteArray())
            }
            else -> _protocolEvents.tryEmit("Received unhandled protocol 767 configuration packet id 0x${packetId.toString(16)}; payload was skipped.")
        }
    }

    private fun finishConfigurationAck(protocol: Int) {
        val ids = ProtocolPacketRegistry.forProtocol(protocol)
            ?: throw UnsupportedOperationException("No configuration registry for protocol $protocol")
        val finishAck = ByteArrayOutputStream()
        writeVarInt(finishAck, ids.configFinishAck)
        sendProtocolPacket(finishAck.toByteArray())
        sendClientInformation(isConfigurationState = false)
        setConnectionState(ProtocolState.PLAY_READY)
    }

    private fun handleConfigPluginMessage(stream: InputStream, protocol: Int) {
        val channel = readString(stream)
        if (channel == "minecraft:brand") {
            val ids = ProtocolPacketRegistry.forProtocol(protocol)
                ?: throw UnsupportedOperationException("No configuration registry for protocol $protocol")
            val reply = ByteArrayOutputStream()
            writeVarInt(reply, ids.configPluginResponse)
            writeString(reply, "minecraft:brand")
            writeString(reply, "mindcraft")
            sendProtocolPacket(reply.toByteArray())
        }
    }

    private fun handlePlayPacket(packetId: Int, payload: ByteArray, protocol: Int) {
        val ids = ProtocolPacketRegistry.forProtocol(protocol)
            ?: throw UnsupportedOperationException("No play registry for protocol $protocol")
        val stream = ByteArrayInputStream(payload)
        when (packetId) {
            ids.playPosition -> {
                val x = readDouble(stream)
                val y = readDouble(stream)
                val z = readDouble(stream)
                val yaw = readFloat(stream)
                val pitch = readFloat(stream)
                val flags = stream.read()
                if (flags !in 0..255) throw EOFException("Truncated player-position flags.")
                val teleportId = readVarInt(stream)

                val confirm = ByteArrayOutputStream()
                writeVarInt(confirm, ids.playTeleportConfirm)
                writeVarInt(confirm, teleportId)
                sendProtocolPacket(confirm.toByteArray())

                val previous = lastPlayerPosition
                if (flags and 0x07 != 0 && previous == null) {
                    _protocolEvents.tryEmit("Relative teleport received before an absolute position; coordinates were not published.")
                    return
                }
                val absoluteX = if (flags and 0x01 != 0) previous!!.first + x else x
                val absoluteY = if (flags and 0x02 != 0) previous!!.second + y else y
                val absoluteZ = if (flags and 0x04 != 0) previous!!.third + z else z
                val absolutePosition = Triple(absoluteX, absoluteY, absoluteZ)
                val absoluteYaw = if (flags and 0x08 != 0) lastPlayerYaw + yaw else yaw
                val absolutePitch = if (flags and 0x10 != 0) lastPlayerPitch + pitch else pitch
                lastPlayerPosition = absolutePosition
                lastPlayerYaw = absoluteYaw
                lastPlayerPitch = absolutePitch
                _spawnPosition.tryEmit(absolutePosition)
            }
            ids.playKeepAlive -> {
                val id = readLong(stream)
                val reply = ByteArrayOutputStream()
                writeVarInt(reply, ids.playKeepAliveResponse)
                writeLong(reply, id)
                sendProtocolPacket(reply.toByteArray())
            }
            ids.playPing -> {
                val id = readInt(stream)
                val pong = ByteArrayOutputStream()
                writeVarInt(pong, ids.playPong)
                writeInt(pong, id)
                sendProtocolPacket(pong.toByteArray())
            }
            ids.playDisconnect -> {
                _protocolEvents.tryEmit("Server sent a Play disconnect packet. Its NBT reason component is not decoded by this client.")
                throw EOFException("Server disconnected during Play; NBT reason decoding is unsupported.")
            }
            ids.playStartConfiguration -> {
                val ack = ByteArrayOutputStream()
                writeVarInt(ack, ids.playConfigurationAcknowledged)
                sendProtocolPacket(ack.toByteArray())
                setConnectionState(ProtocolState.CONFIGURATION)
                sendClientInformation(isConfigurationState = true)
            }
            ids.playPlayerChat -> {
                val senderUuid = UUID(readLong(stream), readLong(stream))
                readVarInt(stream) // message index
                val hasSignature = stream.read()
                if (hasSignature == 1) {
                    val signature = ByteArray(256)
                    readFully(stream, signature)
                } else if (hasSignature != 0) {
                    throw IllegalStateException("Invalid optional signature flag in player chat packet")
                }
                val plainMessage = readString(stream)
                _chatMessages.tryEmit(Pair(senderUuid.toString(), plainMessage))
            }
            ids.playSystemChat -> {
                _protocolEvents.tryEmit("Received system-chat packet; NBT text-component decoding is unsupported.")
            }
            else -> _protocolEvents.tryEmit("Received unhandled protocol 767 play packet id 0x${packetId.toString(16)}.")
        }
    }

    /**
     * Send an absolute player position/rotation update using the protocol-767
     * serverbound Player Position And Rotation packet. This reports a requested
     * position; it is not proof that the server accepted the movement.
     */
    suspend fun sendPlayerPosition(
        x: Double,
        y: Double,
        z: Double,
        yaw: Float = lastPlayerYaw,
        pitch: Float = lastPlayerPitch,
        onGround: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected() || currentState != ProtocolState.PLAY_READY) return@withContext false
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !yaw.isFinite() || !pitch.isFinite()) {
            return@withContext false
        }
        try {
            sendProtocolPacket(MinecraftPlayPacketEncoder.positionRotation(x, y, z, yaw, pitch, onGround))
            true
        } catch (e: Exception) {
            _protocolEvents.tryEmit("Movement packet write failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Walk in short position increments toward a coordinate. This is straight-line
     * movement only: without decoded chunk collision data it is not obstacle-aware
     * pathfinding. Success means packets were written locally, not that the server
     * accepted the final position.
     */
    suspend fun walkToCoordinates(
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        maxDistance: Double = 64.0,
        stepLength: Double = 0.25,
        stepDelayMs: Long = 50L
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected() || currentState != ProtocolState.PLAY_READY) return@withContext false
        val start = lastPlayerPosition ?: return@withContext false
        if (!targetX.isFinite() || !targetY.isFinite() || !targetZ.isFinite() ||
            !maxDistance.isFinite() || maxDistance <= 0.0 ||
            !stepLength.isFinite() || stepLength <= 0.0 || stepDelayMs < 0L
        ) return@withContext false

        val dx = targetX - start.first
        val dy = targetY - start.second
        val dz = targetZ - start.third
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (distance > maxDistance) {
            _protocolEvents.tryEmit("Walk request rejected: target is " + distance + " blocks away; limit is " + maxDistance + ".")
            return@withContext false
        }
        if (distance < 0.01) return@withContext true

        val steps = kotlin.math.ceil(distance / stepLength).toInt().coerceAtLeast(1)
        val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        for (step in 1..steps) {
            if (!isCurrentlyConnected() || currentState != ProtocolState.PLAY_READY) return@withContext false
            val fraction = step.toDouble() / steps
            val wrote = sendPlayerPosition(
                start.first + dx * fraction,
                start.second + dy * fraction,
                start.third + dz * fraction,
                yaw = yaw,
                pitch = lastPlayerPitch,
                onGround = true
            )
            if (!wrote) return@withContext false
            if (step < steps && stepDelayMs > 0L) delay(stepDelayMs)
        }
        true
    }

    /**
     * Send the two serverbound Player Action packets for a normal dig attempt.
     * The server remains authoritative; callers must wait for a block update
     * before treating the block as mined.
     */
    suspend fun mineBlock(x: Int, y: Int, z: Int, face: Int = 1): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected() || currentState != ProtocolState.PLAY_READY || face !in 0..5) {
            return@withContext false
        }
        val player = lastPlayerPosition ?: return@withContext false
        val dx = player.first - (x + 0.5)
        val dy = (player.second + 1.62) - (y + 0.5)
        val dz = player.third - (z + 0.5)
        if (dx * dx + dy * dy + dz * dz > 36.0) {
            _protocolEvents.tryEmit("Mining request rejected locally: target is outside the 6-block reach limit.")
            return@withContext false
        }
        try {
            val startSequence = nextBlockChangeSequence()
            sendProtocolPacket(MinecraftPlayPacketEncoder.dig(0, x, y, z, face, startSequence))
            delay(1000L) // conservative minimum attempt; exact break time requires block/tool state
            val finishSequence = nextBlockChangeSequence()
            sendProtocolPacket(MinecraftPlayPacketEncoder.dig(status = 2, x, y, z, face, finishSequence))
            true
        } catch (e: Exception) {
            _protocolEvents.tryEmit("Mining packet write failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Request placement against a known adjacent block. This only transmits the
     * interaction; a server block-update packet is required to confirm placement.
     */
    suspend fun placeBlock(
        x: Int,
        y: Int,
        z: Int,
        face: Int,
        cursorX: Float = 0.5f,
        cursorY: Float = 0.5f,
        cursorZ: Float = 0.5f,
        hand: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected() || currentState != ProtocolState.PLAY_READY ||
            face !in 0..5 || hand !in 0..1 ||
            cursorX !in 0f..1f || cursorY !in 0f..1f || cursorZ !in 0f..1f
        ) return@withContext false
        val player = lastPlayerPosition ?: return@withContext false
        val dx = player.first - (x + 0.5)
        val dy = (player.second + 1.62) - (y + 0.5)
        val dz = player.third - (z + 0.5)
        if (dx * dx + dy * dy + dz * dz > 36.0) {
            _protocolEvents.tryEmit("Placement request rejected locally: target is outside the 6-block reach limit.")
            return@withContext false
        }
        try {
            val sequence = nextBlockChangeSequence()
            sendProtocolPacket(MinecraftPlayPacketEncoder.placeBlock(
                x, y, z, face, cursorX, cursorY, cursorZ, hand, sequence
            ))
            true
        } catch (e: Exception) {
            _protocolEvents.tryEmit("Block placement packet write failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    @Synchronized
    private fun nextBlockChangeSequence(): Int {
        blockChangeSequence = if (blockChangeSequence == Int.MAX_VALUE) 0 else blockChangeSequence + 1
        return blockChangeSequence
    }

    suspend fun sendChatMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected()) return@withContext false
        try {
            val clean = message.trim().take(256)
            val bytes = ByteArrayOutputStream()
            if (clean.startsWith("/")) {
                // Signed command packet support is not implemented for this protocol.
                return@withContext false
            } else {
                // Chat message
                val ids = ProtocolPacketRegistry.forProtocol(currentProtocolVersion)
                    ?: return@withContext false
                writeVarInt(bytes, ids.playChatMessage)
                writeString(bytes, clean)
                writeLong(bytes, System.currentTimeMillis())
                writeLong(bytes, 0L)
                bytes.write(0) // hasSignature = false
                writeVarInt(bytes, 0) // message count
                bytes.write(byteArrayOf(0, 0, 0)) // 20-bit message acknowledgement bitset
            }
            sendProtocolPacket(bytes.toByteArray())
            true
        } catch (e: Exception) {
            _protocolEvents.tryEmit("Chat write failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    fun disconnect() {
        isConnected = false
        packetListenerJob?.cancel()
        packetListenerJob = null
        try {
            socket?.close()
        } catch (e: Exception) {
            _protocolEvents.tryEmit("Socket cleanup failed: ${e.message ?: e.javaClass.simpleName}")
        }
        socket = null
        inputStream = null
        outputStream = null
        compressionThreshold = -1
        setConnectionState(ProtocolState.DISCONNECTED)
    }

    fun isCurrentlyConnected(): Boolean = isConnected && socket?.isConnected == true && socket?.isClosed == false

    private fun readNextPacket(): Pair<Int, ByteArray> {
        val inStream = inputStream ?: throw EOFException("Socket not connected")
        val packetLength = readVarInt(inStream)
        if (packetLength <= 0 || packetLength > MAX_PACKET_SIZE) {
            throw IllegalStateException("Invalid Minecraft packet length: $packetLength")
        }

        if (compressionThreshold < 0) {
            // Uncompressed packet
            val packetBytes = ByteArray(packetLength)
            readFully(inStream, packetBytes)
            val stream = ByteArrayInputStream(packetBytes)
            val packetId = readVarInt(stream)
            val remaining = ByteArray(stream.available())
            stream.read(remaining)
            return Pair(packetId, remaining)
        } else {
            // Compression envelope
            val envelopeBytes = ByteArray(packetLength)
            readFully(inStream, envelopeBytes)
            val envelopeStream = ByteArrayInputStream(envelopeBytes)
            val dataLength = readVarInt(envelopeStream)
            if (dataLength < 0 || dataLength > MAX_PACKET_SIZE) {
                throw IllegalStateException("Invalid uncompressed packet length: $dataLength")
            }

            if (dataLength == 0) {
                // Uncompressed data inside the compression envelope.
                if (envelopeStream.available() <= 0 || envelopeStream.available() >= compressionThreshold) {
                    throw IllegalStateException("Invalid uncompressed packet for compression threshold $compressionThreshold.")
                }
                val packetId = readVarInt(envelopeStream)
                val remaining = ByteArray(envelopeStream.available())
                readFully(envelopeStream, remaining)
                return Pair(packetId, remaining)
            }

            if (dataLength < compressionThreshold) {
                throw IllegalStateException("Compressed packet is smaller than the negotiated compression threshold.")
            }

            val compressedPayload = ByteArray(envelopeStream.available())
            readFully(envelopeStream, compressedPayload)
            val inflater = Inflater()
            val decompressed = ByteArray(dataLength)
            try {
                inflater.setInput(compressedPayload)
                var total = 0
                while (!inflater.finished() && total < decompressed.size) {
                    val count = inflater.inflate(decompressed, total, decompressed.size - total)
                    if (count == 0) {
                        if (inflater.needsDictionary()) throw IllegalStateException("Compressed packet requires an unsupported dictionary.")
                        if (inflater.needsInput()) break
                        throw IllegalStateException("Compressed packet decompressor made no progress.")
                    }
                    total += count
                }
                if (!inflater.finished() || total != dataLength || inflater.remaining != 0) {
                    throw IllegalStateException("Compressed packet length does not match its declared size.")
                }
            } finally {
                inflater.end()
            }

            val decompStream = ByteArrayInputStream(decompressed)
            val packetId = readVarInt(decompStream)
            val remaining = ByteArray(decompStream.available())
            readFully(decompStream, remaining)
            return Pair(packetId, remaining)
        }
    }

    @Synchronized
    private fun sendProtocolPacket(payload: ByteArray) {
        require(payload.isNotEmpty() && payload.size <= MAX_PACKET_SIZE) { "Invalid outgoing Minecraft packet size: ${payload.size}" }
        val out = outputStream ?: throw EOFException("Socket output stream is unavailable")
        if (compressionThreshold < 0) {
            sendRawPacket(out, payload)
        } else {
            val packet = ByteArrayOutputStream()
            if (payload.size < compressionThreshold) {
                // Uncompressed envelope: Data Length = 0
                val dataLenVarInt = ByteArrayOutputStream()
                writeVarInt(dataLenVarInt, 0)
                val totalLength = dataLenVarInt.size() + payload.size
                writeVarInt(packet, totalLength)
                writeVarInt(packet, 0)
                packet.write(payload)
            } else {
                // Deflated payload
                val deflater = Deflater()
                val compressed = ByteArray(payload.size + 1024)
                val compressedLen = try {
                    deflater.setInput(payload)
                    deflater.finish()
                    deflater.deflate(compressed)
                } finally {
                    deflater.end()
                }

                val dataLenBytes = ByteArrayOutputStream()
                writeVarInt(dataLenBytes, payload.size)
                val totalLength = dataLenBytes.size() + compressedLen

                writeVarInt(packet, totalLength)
                writeVarInt(packet, payload.size)
                packet.write(compressed, 0, compressedLen)
            }
            out.write(packet.toByteArray())
            out.flush()
        }
    }

    private fun sendRawPacket(out: OutputStream, payload: ByteArray) {
        require(payload.isNotEmpty() && payload.size <= MAX_PACKET_SIZE) { "Invalid outgoing Minecraft packet size: ${payload.size}" }
        val packet = ByteArrayOutputStream()
        writeVarInt(packet, payload.size)
        packet.write(payload)
        out.write(packet.toByteArray())
        out.flush()
    }

    private fun writeVarInt(out: OutputStream, value: Int) {
        var v = value
        while ((v and -128) != 0) {
            out.write((v and 127) or 128)
            v = v ushr 7
        }
        out.write(v and 127)
    }

    private fun readVarInt(input: InputStream): Int {
        var value = 0
        var size = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("End of stream while reading VarInt")
            if (size == 4 && (b and 0xF0) != 0) throw IllegalStateException("VarInt exceeds 32 bits.")
            value = value or ((b and 127) shl (size * 7))
            size++
            if (size > 5) throw IllegalStateException("VarInt is too big.")
            if ((b and 128) == 0) return value
        }
    }

    private fun writeString(out: OutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 32767 * 4) { "Minecraft string exceeds the supported byte length." }
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(input: InputStream): String {
        val length = readVarInt(input)
        if (length < 0 || length > 32767 * 4) throw IllegalStateException("Invalid Minecraft string length: $length")
        val buffer = ByteArray(length)
        readFully(input, buffer)
        return String(buffer, StandardCharsets.UTF_8)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        for (i in 7 downTo 0) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }

    private fun readInt(input: InputStream): Int {
        val bytes = ByteArray(4)
        readFully(input, bytes)
        return ByteBuffer.wrap(bytes).int
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write(ByteBuffer.allocate(4).putInt(value).array())
    }

    private fun readLong(input: InputStream): Long {
        var value = 0L
        for (i in 0 until 8) {
            val b = input.read()
            if (b == -1) throw EOFException("Unexpected EOF reading Long")
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        return value
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var total = 0
        while (total < buffer.size) {
            val r = input.read(buffer, total, buffer.size - total)
            if (r == -1) throw EOFException("Unexpected EOF")
            total += r
        }
    }

    private fun readDouble(input: InputStream): Double {
        val buf = ByteArray(8)
        readFully(input, buf)
        return ByteBuffer.wrap(buf).double
    }

    private fun writeDouble(out: OutputStream, value: Double) {
        val buf = ByteBuffer.allocate(8).putDouble(value)
        out.write(buf.array())
    }

    private fun readFloat(input: InputStream): Float {
        val buf = ByteArray(4)
        readFully(input, buf)
        return ByteBuffer.wrap(buf).float
    }

    private fun writeFloat(out: OutputStream, value: Float) {
        val buf = ByteBuffer.allocate(4).putFloat(value)
        out.write(buf.array())
    }

    private fun isSnapshotVersion(versionName: String, protocol: Int): Boolean {
        if (protocol >= 1073741824) return true
        val lower = versionName.lowercase()
        return lower.matches(Regex(".*\\d{2}w\\d{2}[a-z].*")) ||
                lower.contains("snapshot") ||
                lower.contains("pre-release") ||
                lower.contains("pre") ||
                lower.contains("-rc")
    }



    private fun parseDescription(descObj: Any?): String {
        return when (descObj) {
            is JSONObject -> {
                val sb = StringBuilder()
                sb.append(descObj.optString("text", ""))
                val extra = descObj.optJSONArray("extra")
                if (extra != null) {
                    for (i in 0 until extra.length()) {
                        val item = extra.opt(i)
                        when (item) {
                            is JSONObject -> sb.append(item.optString("text", ""))
                            is String -> sb.append(item)
                        }
                    }
                }
                sb.toString().ifBlank { "Minecraft Server" }
            }
            is String -> descObj
            else -> "Minecraft Server"
        }
    }

    private fun parseDisconnectReason(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val text = json.optString("text", "")
                val extraArray = json.optJSONArray("extra")
                val extraBuilder = StringBuilder()
                if (extraArray != null) {
                    for (i in 0 until extraArray.length()) {
                        val item = extraArray.opt(i)
                        when (item) {
                            is JSONObject -> extraBuilder.append(item.optString("text", ""))
                            is String -> extraBuilder.append(item)
                        }
                    }
                }
                val combined = (text + extraBuilder.toString()).trim()
                if (combined.isNotBlank()) {
                    return cleanFormattingCodes(combined)
                }
            } catch (e: Exception) {
                _protocolEvents.tryEmit("Text component parsing failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return cleanFormattingCodes(trimmed)
    }

    private fun cleanFormattingCodes(text: String): String {
        return text.replace(Regex("§[0-9a-fk-or]"), "").trim()
    }
}
