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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BotLogEntity
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
import com.example.ui.theme.TerminalText
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleScreen(
    logs: List<BotLogEntity>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("ALL", "AI_REASONING", "ACTION", "CHAT", "PERCEPTION", "NETWORK", "ERROR_WATCHDOG")

    val filteredLogs = logs.filter { log ->
        val matchesCategory = (selectedCategory == "ALL" || log.category.equals(selectedCategory, ignoreCase = true))
        val matchesSearch = searchQuery.isBlank() || log.message.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        // Console Header & Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity Logs",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "${filteredLogs.size} events logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            IconButton(
                onClick = onClearLogs,
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Logs",
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter console output...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("console_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DiamondBlue,
                unfocusedBorderColor = SurfaceBorder
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = {
                        Text(
                            text = when (cat) {
                                "ALL" -> "All"
                                "AI_REASONING" -> "AI"
                                "ACTION" -> "Actions"
                                "CHAT" -> "Chat"
                                "PERCEPTION" -> "World"
                                "NETWORK" -> "Network"
                                "ERROR_WATCHDOG" -> "Errors"
                                else -> cat
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DiamondBlue.copy(alpha = 0.2f),
                        selectedLabelColor = DiamondBlue,
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) DiamondBlue else SurfaceBorder,
                        selectedBorderColor = DiamondBlue,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Terminal Window
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("console_terminal_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No console activity matching filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { log ->
                        val timeStr = timeFormatter.format(Date(log.timestamp))
                        val levelColor = when (log.level) {
                            "SUCCESS" -> EmeraldGreen
                            "AI_THOUGHT" -> DiamondBlue
                            "WARN" -> GoldYellow
                            "ERROR" -> RedstoneRed
                            "RECOVERY" -> Color(0xFF67E8F9)
                            else -> TextSecondary
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[$timeStr] ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoMonoFamily,
                                    fontSize = 11.sp
                                ),
                                color = TextMuted
                            )
                            Text(
                                text = "[${log.category}] ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoMonoFamily,
                                    fontSize = 11.sp
                                ),
                                color = levelColor
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoMonoFamily,
                                    fontSize = 11.sp
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
