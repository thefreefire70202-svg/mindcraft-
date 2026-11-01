package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ServerStatusResult
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

@Composable
fun ServerQueryScreen(
    queryResult: ServerStatusResult?,
    isLoading: Boolean,
    onQueryServer: (String, Int) -> Unit,
    onConnectToThisServer: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var hostInput by remember { mutableStateOf("donutsmp.net") }
    var portInput by remember { mutableStateOf("25565") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Server Status & Ping",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Test and ping any public or private Minecraft Java server over TCP.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ping_input_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { input ->
                            if (input.contains(":")) {
                                val (h, p) = com.example.engine.MinecraftProtocolClient.parseHostAndPort(
                                    input,
                                    portInput.toIntOrNull() ?: 25565
                                )
                                hostInput = h
                                portInput = p.toString()
                            } else {
                                hostInput = input
                            }
                        },
                        label = { Text("Server Address") },
                        placeholder = { Text("e.g. PRIVATESMPGO.aternos.me:53750 or donutsmp.net") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DiamondBlue,
                            unfocusedBorderColor = SurfaceBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DiamondBlue,
                            unfocusedBorderColor = SurfaceBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val port = portInput.toIntOrNull() ?: 25565
                            onQueryServer(hostInput, port)
                        },
                        enabled = !isLoading && hostInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("ping_server_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DiamondBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pinging server...")
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ping Server")
                        }
                    }
                }
            }
        }

        // Preset Servers
        item {
            Text(
                text = "Preset Servers to Test",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("Aternos SMP") {
                    hostInput = "PRIVATESMPGO.aternos.me"
                    portInput = "53750"
                    onQueryServer("PRIVATESMPGO.aternos.me", 53750)
                }
                PresetChip("DonutSMP") {
                    hostInput = "donutsmp.net"
                    portInput = "25565"
                    onQueryServer("donutsmp.net", 25565)
                }
                PresetChip("CubeCraft") {
                    hostInput = "play.cubecraft.net"
                    portInput = "25565"
                    onQueryServer("play.cubecraft.net", 25565)
                }
                PresetChip("Localhost") {
                    hostInput = "10.0.2.2"
                    portInput = "25565"
                    onQueryServer("10.0.2.2", 25565)
                }
            }
        }

        // Result Card
        if (queryResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (queryResult.isOnline) EmeraldGreen else RedstoneRed
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (queryResult.isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (queryResult.isOnline) EmeraldGreen else RedstoneRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (queryResult.isOnline) "Server is Online" else "Server Offline / Unreachable",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (queryResult.isOnline) EmeraldGreen else RedstoneRed
                                )
                            }

                            if (queryResult.isOnline) {
                                Text(
                                    text = "${queryResult.pingMs} ms",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DiamondBlue
                                )
                            }
                        }

                        if (queryResult.isOnline) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (queryResult.motd.isNotBlank()) {
                                Text(
                                    text = queryResult.motd,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Version: ${queryResult.versionName}${if (queryResult.isSnapshot) " [Snapshot]" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (queryResult.isSnapshot) GoldYellow else TextSecondary
                                )
                                Text(
                                    text = "Players: ${queryResult.onlinePlayers} / ${queryResult.maxPlayers}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val port = portInput.toIntOrNull() ?: 25565
                                    onConnectToThisServer(hostInput, port)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect Bot to this Server")
                            }
                        } else if (queryResult.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reason: ${queryResult.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetChip(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}
