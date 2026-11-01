package com.example.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY
}

class MinecraftProtocolClient {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var compressionThreshold = -1
    private var currentState = ProtocolState.HANDSHAKE
    private var packetListenerJob: Job? = null

    private val protocolCache = ConcurrentHashMap<String, CachedServerProtocol>()
    private val lastContactTimestamps = ConcurrentHashMap<String, Long>()

    var currentProtocolVersion: Int = 765
        private set
    var currentVersionName: String = "Auto-Detect"
        private set

    private val _chatMessages = MutableSharedFlow<Pair<String, String>>(replay = 20)
    val chatMessages: SharedFlow<Pair<String, String>> = _chatMessages.asSharedFlow()

    companion object {
        fun parseHostAndPort(rawHost: String, defaultPort: Int = 25565): Pair<String, Int> {
            val clean = rawHost.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("minecraft://")
                .trimEnd('/')
            return if (clean.contains(":")) {
                val parts = clean.split(":")
                val h = parts[0].trim()
                val p = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: defaultPort
                Pair(h, p)
            } else {
                Pair(clean, defaultPort)
            }
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
                // Use protocol 767 (Minecraft 1.21.1) which modern servers (Purpur/Paper/Aternos/Velocity) accept without rejecting
                val handshakeBytes = ByteArrayOutputStream()
                writeVarInt(handshakeBytes, 0x00) // Packet ID
                writeVarInt(handshakeBytes, 767)  // Protocol 767 (1.21.1 standard for status ping)
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
                val jsonResponse = readString(inStream)

                val ping = System.currentTimeMillis() - startTime

                // 4. Send Ping Request Packet (ID: 0x01) with timestamp to cleanly complete SLP exchange
                try {
                    val pingReq = ByteArrayOutputStream()
                    writeVarInt(pingReq, 0x01)
                    val pbb = ByteBuffer.allocate(8).putLong(System.currentTimeMillis())
                    pingReq.write(pbb.array())
                    sendRawPacket(outStream, pingReq.toByteArray())
                } catch (_: Exception) {}

                // Parse Server JSON response
                val json = JSONObject(jsonResponse)
                val versionObj = json.optJSONObject("version")
                val versionName = versionObj?.optString("name", "Minecraft Server") ?: "Minecraft Server"
                val protocolVer = versionObj?.optInt("protocol", 767) ?: 767

                val playersObj = json.optJSONObject("players")
                val onlinePlayers = playersObj?.optInt("online", 0) ?: 0
                val maxPlayers = playersObj?.optInt("max", 0) ?: 0

                val descObj = json.opt("description")
                val motd = parseDescription(descObj)

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
                } catch (_: Exception) {}
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

        // 2. If protocol is still unknown, default directly to 767 (1.21.1)
        // DO NOT open a second status socket here, as that triggers connection-throttle!
        if (detectedProtocol <= 0) {
            detectedProtocol = 767
            detectedName = "1.21.x"
        }

        currentProtocolVersion = detectedProtocol
        currentVersionName = detectedName

        // 3. Perform connection with the auto-detected protocol
        val attemptResult = performLoginHandshake(host, port, username, detectedProtocol, detectedName, isSnapshot, timeoutMs, scope)

        // 4. If server disconnected with an "Outdated" message, extract the required version and auto-retry!
        if (!attemptResult.isSuccess && attemptResult.message.contains("Outdated", ignoreCase = true)) {
            val retryProtocol = extractProtocolFromDisconnectReason(attemptResult.message)
            if (retryProtocol != null && retryProtocol != detectedProtocol) {
                disconnect()
                currentProtocolVersion = retryProtocol
                currentVersionName = "Auto-corrected ($retryProtocol)"
                setCachedProtocol(host, port, retryProtocol, currentVersionName, isSnapshot)
                return@withContext performLoginHandshake(host, port, username, retryProtocol, currentVersionName, isSnapshot, timeoutMs, scope)
            }
        }

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
            s.soTimeout = timeoutMs
            s.connect(InetSocketAddress(targetHost, port), timeoutMs)
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()
            isConnected = true
            compressionThreshold = -1
            currentState = ProtocolState.HANDSHAKE
            lastContactTimestamps["$host:$port"] = System.currentTimeMillis()

            // 1. Send Handshake Packet with NextState = 2 (Login)
            val handshakeBytes = ByteArrayOutputStream()
            writeVarInt(handshakeBytes, 0x00) // Packet ID
            writeVarInt(handshakeBytes, protocol) // Auto-detected protocol (e.g. 767 for 1.21.1)
            writeString(handshakeBytes, host)
            handshakeBytes.write((port shr 8) and 0xFF)
            handshakeBytes.write(port and 0xFF)
            writeVarInt(handshakeBytes, 2) // Next State = 2 (Login)

            sendRawPacket(outputStream!!, handshakeBytes.toByteArray())
            currentState = ProtocolState.LOGIN

            // 2. Send Login Start Packet (Packet ID 0x00 in Login State)
            val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
            val loginBytes = ByteArrayOutputStream()
            writeVarInt(loginBytes, 0x00)
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

            // 3. Read Login Response packet(s)
            var (packetId, payload) = readNextPacket()

            // Handle Packet 0x03: Set Compression (used by Paper, Purpur, Spigot, Aternos)
            if (packetId == 0x03) {
                val thresholdStream = ByteArrayInputStream(payload)
                compressionThreshold = readVarInt(thresholdStream)

                // Read next packet under compression envelope
                val nextPacket = readNextPacket()
                packetId = nextPacket.first
                payload = nextPacket.second
            }

            when (packetId) {
                0x00 -> {
                    // Disconnected by server
                    val reasonStream = ByteArrayInputStream(payload)
                    val rawReason = readString(reasonStream)
                    val cleanReason = parseDisconnectReason(rawReason)
                    disconnect()
                    ConnectResult(
                        isSuccess = false,
                        message = cleanReason,
                        protocolVersionUsed = protocol,
                        versionName = versionName,
                        isSnapshot = isSnapshot,
                        exception = Exception(cleanReason)
                    )
                }
                0x01 -> {
                    // Encryption Request (Server requires premium Mojang auth / online-mode=true)
                    disconnect()
                    ConnectResult(
                        isSuccess = false,
                        isOnlineModeRestricted = true,
                        message = "Server is in Online Mode (Mojang Auth required). To allow Mindcraft Bot to join your Aternos server: Open Aternos -> Options -> Set 'Cracked: ON' -> Restart server.",
                        protocolVersionUsed = protocol,
                        versionName = versionName,
                        isSnapshot = isSnapshot
                    )
                }
                0x02 -> {
                    // Login Success!
                    if (protocol >= 764) {
                        // In 1.20.2+, acknowledge login to transition to Configuration phase
                        try {
                            val ackBytes = ByteArrayOutputStream()
                            writeVarInt(ackBytes, 0x03) // Login Acknowledged
                            sendProtocolPacket(ackBytes.toByteArray())
                            currentState = ProtocolState.CONFIGURATION
                        } catch (_: Exception) {}
                    } else {
                        currentState = ProtocolState.PLAY
                    }

                    // Remove socket read timeout so packet listener runs indefinitely
                    socket?.soTimeout = 0

                    // Start background packet handler to answer Keep-Alive & read Chat
                    if (scope != null) {
                        startPacketListenerLoop(scope, protocol)
                    }

                    ConnectResult(
                        isSuccess = true,
                        message = "Joined $versionName server (Protocol: $protocol${if (isSnapshot) " [Snapshot]" else ""}) as '$username'.",
                        protocolVersionUsed = protocol,
                        versionName = versionName,
                        isSnapshot = isSnapshot
                    )
                }
                else -> {
                    ConnectResult(
                        isSuccess = true,
                        message = "Connected to $versionName (Protocol: $protocol). Handshake accepted.",
                        protocolVersionUsed = protocol,
                        versionName = versionName,
                        isSnapshot = isSnapshot
                    )
                }
            }
        } catch (e: Exception) {
            disconnect()
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

    private fun startPacketListenerLoop(scope: CoroutineScope, protocol: Int) {
        packetListenerJob?.cancel()
        packetListenerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isCurrentlyConnected()) {
                    val (packetId, payload) = try {
                        readNextPacket()
                    } catch (e: Exception) {
                        break
                    }

                    when (currentState) {
                        ProtocolState.CONFIGURATION -> {
                            handleConfigurationPacket(packetId, payload, protocol)
                        }
                        ProtocolState.PLAY -> {
                            handlePlayPacket(packetId, payload, protocol)
                        }
                        else -> {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                isConnected = false
            }
        }
    }

    private fun handleConfigurationPacket(packetId: Int, payload: ByteArray, protocol: Int) {
        val stream = ByteArrayInputStream(payload)
        when (packetId) {
            0x04 -> {
                // Clientbound Keep Alive (Configuration)
                val id = readLong(stream)
                val reply = ByteArrayOutputStream()
                writeVarInt(reply, 0x03) // Serverbound Keep Alive (Config)
                writeLong(reply, id)
                sendProtocolPacket(reply.toByteArray())
            }
            0x05 -> {
                // Clientbound Ping (Configuration)
                val pingId = stream.read() or (stream.read() shl 8) or (stream.read() shl 16) or (stream.read() shl 24)
                val pong = ByteArrayOutputStream()
                writeVarInt(pong, 0x04) // Pong (Config)
                pong.write(pingId and 0xFF)
                pong.write((pingId shr 8) and 0xFF)
                pong.write((pingId shr 16) and 0xFF)
                pong.write((pingId shr 24) and 0xFF)
                sendProtocolPacket(pong.toByteArray())
            }
            0x02, 0x03 -> {
                // Finish Configuration (Serverbound Finish Configuration = 0x02)
                try {
                    val finishAck = ByteArrayOutputStream()
                    writeVarInt(finishAck, 0x02)
                    sendProtocolPacket(finishAck.toByteArray())
                    currentState = ProtocolState.PLAY
                } catch (_: Exception) {}
            }
        }
    }

    private fun handlePlayPacket(packetId: Int, payload: ByteArray, protocol: Int) {
        val stream = ByteArrayInputStream(payload)
        // Keep Alive detection across modern versions (usually 0x24, 0x26, or payload of 8 bytes)
        if (payload.size == 8 || (packetId in listOf(0x21, 0x24, 0x26, 0x27))) {
            try {
                val keepAliveId = readLong(stream)
                val keepAliveReply = ByteArrayOutputStream()
                // Serverbound Keep Alive packet ID is typically 0x15 or 0x18
                writeVarInt(keepAliveReply, if (protocol >= 767) 0x18 else 0x15)
                writeLong(keepAliveReply, keepAliveId)
                sendProtocolPacket(keepAliveReply.toByteArray())
            } catch (_: Exception) {}
        }

        // Try extracting chat messages from text packets
        try {
            val textCandidate = String(payload, StandardCharsets.UTF_8)
            if (textCandidate.contains("\"text\"") || textCandidate.contains("translate")) {
                val parsed = parseDisconnectReason(textCandidate)
                if (parsed.isNotBlank() && !parsed.startsWith("{")) {
                    _chatMessages.tryEmit(Pair("Server", parsed))
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun sendChatMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        if (!isCurrentlyConnected()) return@withContext false
        try {
            val clean = message.trim().take(256)
            val bytes = ByteArrayOutputStream()
            if (clean.startsWith("/")) {
                // Chat command
                writeVarInt(bytes, if (currentProtocolVersion >= 767) 0x04 else 0x03)
                writeString(bytes, clean.removePrefix("/"))
                writeLong(bytes, System.currentTimeMillis())
                writeLong(bytes, 0L) // salt
                writeVarInt(bytes, 0)
                writeVarInt(bytes, 0)
            } else {
                // Chat message
                writeVarInt(bytes, if (currentProtocolVersion >= 767) 0x06 else 0x05)
                writeString(bytes, clean)
                writeLong(bytes, System.currentTimeMillis())
                writeLong(bytes, 0L)
                writeVarInt(bytes, 0)
                writeVarInt(bytes, 0)
            }
            sendProtocolPacket(bytes.toByteArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect() {
        isConnected = false
        packetListenerJob?.cancel()
        packetListenerJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        compressionThreshold = -1
        currentState = ProtocolState.HANDSHAKE
    }

    fun isCurrentlyConnected(): Boolean = isConnected && socket?.isConnected == true && socket?.isClosed == false

    private fun readNextPacket(): Pair<Int, ByteArray> {
        val inStream = inputStream ?: throw EOFException("Socket not connected")
        val packetLength = readVarInt(inStream)

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

            if (dataLength == 0) {
                // Uncompressed data inside envelope
                val packetId = readVarInt(envelopeStream)
                val remaining = ByteArray(envelopeStream.available())
                envelopeStream.read(remaining)
                return Pair(packetId, remaining)
            } else {
                // Compressed data inside envelope
                val compressedPayload = ByteArray(envelopeStream.available())
                envelopeStream.read(compressedPayload)

                val inflater = Inflater()
                inflater.setInput(compressedPayload)
                val decompressed = ByteArray(dataLength)
                inflater.inflate(decompressed)
                inflater.end()

                val decompStream = ByteArrayInputStream(decompressed)
                val packetId = readVarInt(decompStream)
                val remaining = ByteArray(decompStream.available())
                decompStream.read(remaining)
                return Pair(packetId, remaining)
            }
        }
    }

    private fun sendProtocolPacket(payload: ByteArray) {
        val out = outputStream ?: return
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
                deflater.setInput(payload)
                deflater.finish()
                val compressed = ByteArray(payload.size + 1024)
                val compressedLen = deflater.deflate(compressed)
                deflater.end()

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
        var b: Int
        while (true) {
            b = input.read()
            if (b == -1) throw EOFException("End of stream while reading VarInt")
            value = value or ((b and 127) shl (size++ * 7))
            if (size > 5) throw RuntimeException("VarInt is too big")
            if ((b and 128) != 128) break
        }
        return value
    }

    private fun writeString(out: OutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(input: InputStream): String {
        val length = readVarInt(input)
        val buffer = ByteArray(length)
        readFully(input, buffer)
        return String(buffer, StandardCharsets.UTF_8)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        for (i in 7 downTo 0) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
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

    private fun isSnapshotVersion(versionName: String, protocol: Int): Boolean {
        if (protocol >= 1073741824) return true
        val lower = versionName.lowercase()
        return lower.matches(Regex(".*\\d{2}w\\d{2}[a-z].*")) ||
                lower.contains("snapshot") ||
                lower.contains("pre-release") ||
                lower.contains("pre") ||
                lower.contains("-rc")
    }

    private fun extractProtocolFromDisconnectReason(reason: String): Int? {
        val lower = reason.lowercase()
        return when {
            "1.21.4" in lower -> 769
            "1.21.3" in lower || "1.21.2" in lower -> 768
            "1.21.1" in lower || "1.21" in lower -> 767
            "1.20.6" in lower || "1.20.5" in lower -> 766
            "1.20.4" in lower || "1.20.3" in lower -> 765
            "1.20.2" in lower -> 764
            "1.20.1" in lower || "1.20" in lower -> 763
            "1.19.4" in lower -> 762
            "1.19.2" in lower -> 760
            "1.16.5" in lower -> 754
            "1.12.2" in lower -> 340
            "1.8" in lower -> 47
            else -> null
        }
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
            } catch (_: Exception) {}
        }
        return cleanFormattingCodes(trimmed)
    }

    private fun cleanFormattingCodes(text: String): String {
        return text.replace(Regex("§[0-9a-fk-or]"), "").trim()
    }
}
