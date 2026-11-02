package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.db.BotLogEntity
import com.example.data.model.BotState
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotBrainScreen(
    botState: BotState,
    logs: List<BotLogEntity>,
    modifier: Modifier = Modifier
) {
    val events = logs
        .filter { it.category in setOf("NETWORK", "PERCEPTION", "AI_REASONING", "ACTION", "CHAT", "ERROR_WATCHDOG") }
        .takeLast(100)
        .reversed()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "MINDCRAFT LIVE ACTIVITY",
                style = MaterialTheme.typography.titleLarge,
                color = DiamondBlue
            )
            Text(
                text = "This view shows recorded application and protocol events. It does not simulate reasoning or gameplay.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("LIVE SESSION", color = DiamondBlue, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    StateLine("Bot status", botState.status.name)
                    StateLine("Protocol phase", botState.protocolState)
                    StateLine("TCP socket", if (botState.isRealTcpConnected) "Connected" else "Not connected")
                    StateLine("Server", if (botState.serverHost.isBlank()) "Unavailable" else "${botState.serverHost}:${botState.serverPort}")
                    StateLine("Version", botState.serverVersion.ifBlank { "Unknown" })
                    StateLine("Position", if (botState.hasPositionUpdate) "${botState.position.x}, ${botState.position.y}, ${botState.position.z}" else "Unavailable")
                    StateLine("Inventory", if (botState.inventory.isEmpty()) "Not received / unavailable" else "${botState.inventory.size} decoded entries")
                    StateLine("Health / food", if (botState.health >= 0 || botState.food >= 0) "${botState.health} / ${botState.food}" else "Unavailable")
                }
            }
        }

        item {
            Text("RECORDED EVENTS", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }

        if (events.isEmpty()) {
            item {
                Text("No live events have been recorded yet.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(events, key = { it.id }) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${event.category} · ${event.level}", color = DiamondBlue, style = MaterialTheme.typography.labelSmall)
                            Text(
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp)),
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(event.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        if (!event.details.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(event.details, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
    }
}
