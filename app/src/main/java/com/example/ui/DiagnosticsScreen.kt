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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ErrorReportEntity
import com.example.data.model.BotState
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedstoneRed
import com.example.ui.theme.RobotoMonoFamily
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    botState: BotState,
    errorReports: List<ErrorReportEntity>,
    onToggleAutoRecovery: () -> Unit,
    onTriggerTestHazard: () -> Unit,
    onClearReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUTOMATED ERROR REPORTING & WATCHDOG",
                    style = MaterialTheme.typography.titleLarge,
                    color = DiamondBlue
                )
                Text(
                    text = "Self-healing telemetry system ensuring 24/7 continuous bot stability without manual restarts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (errorReports.isNotEmpty()) {
                IconButton(
                    onClick = onClearReports,
                    modifier = Modifier.testTag("clear_reports_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Reports", tint = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Watchdog Control Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watchdog_banner_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (botState.isAutoRecoveryEnabled) EmeraldGreen else SurfaceBorder
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (botState.isAutoRecoveryEnabled) EmeraldGreen else TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (botState.isAutoRecoveryEnabled) "SELF-HEALING WATCHDOG ACTIVE" else "SELF-HEALING WATCHDOG PAUSED",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (botState.isAutoRecoveryEnabled) EmeraldGreen else TextMuted
                            )
                            Text(
                                text = "Auto-reconnects, fixes stuck pathfinding, and manages hazard retreats",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = botState.isAutoRecoveryEnabled,
                        onCheckedChange = { onToggleAutoRecovery() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldGreen,
                            checkedTrackColor = SurfaceElevated
                        ),
                        modifier = Modifier.testTag("toggle_watchdog_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test hazard button
                Button(
                    onClick = onTriggerTestHazard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("trigger_test_hazard_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TRIGGER TEST HAZARD & WATCH AUTO-RECOVERY", style = MaterialTheme.typography.labelMedium, color = GoldYellow)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "ERROR INCIDENT LOG (${errorReports.size} RECORDED)",
            style = MaterialTheme.typography.titleMedium,
            color = DiamondBlue
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (errorReports.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No active error incidents", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Bot runtime is operating smoothly without unhandled exceptions.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(errorReports) { report ->
                    ErrorReportItemCard(report = report, formattedTime = timeFormatter.format(Date(report.timestamp)))
                }
            }
        }
    }
}

@Composable
fun ErrorReportItemCard(report: ErrorReportEntity, formattedTime: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, RedstoneRed.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedstoneRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = report.errorType,
                        style = MaterialTheme.typography.titleSmall,
                        color = RedstoneRed
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = EmeraldGreen.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resolved", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$formattedTime • Snap: ${report.botCoordinates}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFamily),
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = report.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Recovery action applied
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "AUTONOMOUS RECOVERY REMEDIATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = EmeraldGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = report.recoveryStrategyApplied,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFamily),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
