package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiSettings
import com.example.data.model.BotState
import com.example.data.model.BotStatus
import com.example.data.model.ChatMessage
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedstoneRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    botState: BotState,
    aiSettings: AiSettings,
    onStartBot: (String, Int, String, Boolean) -> Unit,
    onStopBot: () -> Unit,
    onPauseBot: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onToggleAiAutopilot: () -> Unit,
    onSetVersionDetection: (Boolean, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverHostInput by remember { mutableStateOf(botState.serverHost) }
    var serverPortInput by remember { mutableStateOf(botState.serverPort.toString()) }
    var botNameInput by remember { mutableStateOf(botState.botName) }
    var chatInput by remember { mutableStateOf("") }
    var directConnect by remember { mutableStateOf(false) }

    val isOnline = botState.status == BotStatus.ONLINE
    val isConnecting = botState.status == BotStatus.CONNECTING
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Server Connection Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_connection_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minecraft Server Connection",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isOnline -> EmeraldGreen.copy(alpha = 0.15f)
                                isConnecting -> GoldYellow.copy(alpha = 0.15f)
                                else -> SurfaceElevated
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isOnline -> EmeraldGreen
                                                isConnecting -> GoldYellow
                                                botState.status == BotStatus.ERROR -> RedstoneRed
                                                else -> TextMuted
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isOnline -> "Connected"
                                        isConnecting -> "Connecting..."
                                        botState.status == BotStatus.ERROR -> "Error"
                                        else -> "Disconnected"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isOnline -> EmeraldGreen
                                        isConnecting -> GoldYellow
                                        botState.status == BotStatus.ERROR -> RedstoneRed
                                        else -> TextMuted
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isOnline && !isConnecting) {
                        if (botState.status == BotStatus.ERROR) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RedstoneRed.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RedstoneRed.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = null,
                                            tint = RedstoneRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Connection Notice",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = RedstoneRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = botState.currentGoal.removePrefix("Failed: ").ifBlank { "Could not connect to server. Check server status and retry." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        // Inputs when offline
                        OutlinedTextField(
                            value = serverHostInput,
                            onValueChange = { input ->
                                if (input.contains(":")) {
                                    val (h, p) = com.example.engine.MinecraftProtocolClient.parseHostAndPort(
                                        input,
                                        serverPortInput.toIntOrNull() ?: 25565
                                    )
                                    serverHostInput = h
                                    serverPortInput = p.toString()
                                } else {
                                    serverHostInput = input
                                }
                            },
                            label = { Text("Server Address (IP or Domain)") },
                            placeholder = { Text("e.g. PRIVATESMPGO.aternos.me:53750") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_host_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DiamondBlue,
                                unfocusedBorderColor = SurfaceBorder
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = serverPortInput,
                                onValueChange = { serverPortInput = it },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DiamondBlue,
                                    unfocusedBorderColor = SurfaceBorder
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = botNameInput,
                                onValueChange = { botNameInput = it },
                                label = { Text("Bot Name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.weight(2f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DiamondBlue,
                                    unfocusedBorderColor = SurfaceBorder
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Auto-detect server version row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Detect Version & Snapshot",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Detects release, legacy, or snapshot protocol before joining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = botState.autoDetectVersion,
                                onCheckedChange = { onSetVersionDetection(it, botState.manualProtocolOverride) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldGreen,
                                    checkedTrackColor = SurfaceElevated
                                ),
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }

                        if (!botState.autoDetectVersion) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickChatPill(
                                    text = if (botState.manualProtocolOverride == 769) "✓ 1.21.4" else "1.21.4"
                                ) { onSetVersionDetection(false, 769) }
                                QuickChatPill(
                                    text = if (botState.manualProtocolOverride == 765) "✓ 1.20.4" else "1.20.4"
                                ) { onSetVersionDetection(false, 765) }
                                QuickChatPill(
                                    text = if (botState.manualProtocolOverride == 754) "✓ 1.16.5" else "1.16.5"
                                ) { onSetVersionDetection(false, 754) }
                                QuickChatPill(
                                    text = if (botState.manualProtocolOverride == 47) "✓ 1.8.9" else "1.8.9"
                                ) { onSetVersionDetection(false, 47) }
                            }
                        }

                        if (isConnecting && botState.activeTask.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldYellow.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = GoldYellow
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = botState.activeTask,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoldYellow
                                    )
                                }
                            }
                        }

                        // Direct TCP Connect switch to prevent server rate limiting
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Direct Connect (Skip Ping)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Connects directly without status ping to avoid server throttle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = directConnect,
                                onCheckedChange = { directConnect = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldGreen,
                                    checkedTrackColor = SurfaceElevated
                                ),
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val port = serverPortInput.toIntOrNull() ?: 25565
                                onStartBot(serverHostInput, port, botNameInput, directConnect)
                            },
                            enabled = !isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("join_server_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                disabledContainerColor = SurfaceElevated
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting...", style = MaterialTheme.typography.titleSmall, color = TextMuted)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Join Server", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    } else {
                        // Connected Server Info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${botState.botName} on ${botState.serverHost}:${botState.serverPort}",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            if (botState.serverMotd.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = botState.serverMotd,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Players: ${botState.onlinePlayersCount}/${botState.maxPlayersCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen
                                )
                                Text(
                                    text = "Ping: ${botState.telemetry.pingMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DiamondBlue
                                )
                                Text(
                                    text = "Position: (${botState.position.x.toInt()}, ${botState.position.y.toInt()}, ${botState.position.z.toInt()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onStopBot()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("disconnect_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = RedstoneRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect")
                            }

                            OutlinedButton(
                                onClick = onPauseBot,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                            ) {
                                Icon(
                                    if (botState.status == BotStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (botState.status == BotStatus.PAUSED) "Resume" else "Pause")
                            }
                        }
                    }
                }
            }
        }

        // Live Minecraft In-Game Chat Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("in_game_chat_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In-Game Chat & Commands",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        // AI Autopilot switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI Auto-Reply",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = botState.isAiAutopilotEnabled,
                                onCheckedChange = { onToggleAiAutopilot() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DiamondBlue,
                                    checkedTrackColor = SurfaceElevated
                                ),
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat message feed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(SurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        if (botState.chatHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isOnline) "Chat connected. Type a message below." else "Connect to a server to chat with players.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                reverseLayout = true
                            ) {
                                items(botState.chatHistory.reversed()) { chat ->
                                    val time = timeFormatter.format(Date(chat.timestamp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "[$time] ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = "<${chat.sender}> ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (chat.isBot) EmeraldGreen else DiamondBlue
                                        )
                                        Text(
                                            text = chat.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat Input
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        placeholder = { Text("Send chat message or command...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (chatInput.isNotBlank()) {
                                onSendChatMessage(chatInput)
                                chatInput = ""
                                focusManager.clearFocus()
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_message_input"),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (chatInput.isNotBlank()) {
                                        onSendChatMessage(chatInput)
                                        chatInput = ""
                                        focusManager.clearFocus()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = DiamondBlue)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DiamondBlue,
                            unfocusedBorderColor = SurfaceBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Chat Pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickChatPill("Hello everyone!") { onSendChatMessage("Hello everyone!") }
                        QuickChatPill("Come to me") { onSendChatMessage("Come to my coordinates") }
                        QuickChatPill("Gather wood") { onSendChatMessage("Chop some trees and gather oak wood") }
                        QuickChatPill("Defend area") { onSendChatMessage("Defend against nearby monsters") }
                    }
                }
            }
        }

        // Clean Health & Status strip
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bot Health", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = "${botState.health} / 20",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (botState.health > 10) EmeraldGreen else RedstoneRed
                        )
                    }

                    Column {
                        Text("Food Level", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = "${botState.food} / 20",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldYellow
                        )
                    }

                    Column {
                        Text("AI Provider", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = aiSettings.activeProvider.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = DiamondBlue
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun QuickChatPill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
