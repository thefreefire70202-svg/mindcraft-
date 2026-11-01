package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BotStatus
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

sealed class NavTab(val title: String, val icon: ImageVector, val tag: String) {
    object Bot : NavTab("Bot", Icons.Default.Dns, "nav_bot")
    object AiSetup : NavTab("AI Setup", Icons.Default.SmartToy, "nav_ai_setup")
    object PingServer : NavTab("Ping Server", Icons.Default.Wifi, "nav_ping_server")
    object Logs : NavTab("Logs", Icons.AutoMirrored.Filled.ListAlt, "nav_logs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    BackHandler(enabled = currentTab != 0) {
        focusManager.clearFocus()
        currentTab = 0
    }

    val botState by viewModel.botState.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val localModels by viewModel.localModels.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val serverQueryResult by viewModel.serverQueryResult.collectAsStateWithLifecycle()
    val isQueryingServer by viewModel.isQueryingServer.collectAsStateWithLifecycle()

    val tabs = listOf(
        NavTab.Bot,
        NavTab.AiSetup,
        NavTab.PingServer,
        NavTab.Logs
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Minecraft Bot",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (botState.status == BotStatus.ONLINE) "${botState.serverHost}:${botState.serverPort}" else "Not connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (botState.status) {
                                            BotStatus.ONLINE -> EmeraldGreen
                                            BotStatus.CONNECTING -> GoldYellow
                                            BotStatus.PAUSED -> DiamondBlue
                                            BotStatus.ERROR -> RedstoneRed
                                            else -> TextMuted
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (botState.status) {
                                    BotStatus.ONLINE -> "Online"
                                    BotStatus.CONNECTING -> "Connecting"
                                    BotStatus.PAUSED -> "Paused"
                                    BotStatus.ERROR -> "Error"
                                    else -> "Offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (botState.status) {
                                    BotStatus.ONLINE -> EmeraldGreen
                                    BotStatus.CONNECTING -> GoldYellow
                                    BotStatus.PAUSED -> DiamondBlue
                                    BotStatus.ERROR -> RedstoneRed
                                    else -> TextSecondary
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                windowInsets = WindowInsets.navigationBars
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = {
                            focusManager.clearFocus()
                            currentTab = index
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = DiamondBlue,
                            indicatorColor = DiamondBlue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        containerColor = ObsidianBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = currentTab, label = "tab_fade") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardScreen(
                        botState = botState,
                        aiSettings = aiSettings,
                        onStartBot = { host, port, name, direct -> viewModel.startBot(host, port, name, direct) },
                        onStopBot = { viewModel.stopBot() },
                        onPauseBot = { viewModel.pauseBot() },
                        onSendChatMessage = { msg -> viewModel.sendChatMessage(msg) },
                        onToggleAiAutopilot = { viewModel.toggleAiAutopilot() },
                        onSetVersionDetection = { auto, proto -> viewModel.setVersionDetection(auto, proto) }
                    )
                    1 -> AiConfigScreen(
                        aiSettings = aiSettings,
                        localModels = localModels,
                        onSelectProvider = { provider -> viewModel.setAiProvider(provider) },
                        onSelectModel = { model -> viewModel.setSelectedModel(model) },
                        onSetApiKey = { provider, key -> viewModel.setApiKey(provider, key) },
                        onSetCustomEndpoint = { endpoint -> viewModel.setCustomEndpoint(endpoint) },
                        onStartDownload = { modelId -> viewModel.startModelDownload(modelId) },
                        onPauseDownload = { modelId -> viewModel.pauseModelDownload(modelId) },
                        onDeleteModel = { modelId -> viewModel.deleteModel(modelId) },
                        onActivateModel = { modelId -> viewModel.activateLocalModel(modelId) },
                        onAddCustomModel = { name, url, mb -> viewModel.addCustomModel(name, url, mb) }
                    )
                    2 -> ServerQueryScreen(
                        queryResult = serverQueryResult,
                        isLoading = isQueryingServer,
                        onQueryServer = { host, port -> viewModel.queryServer(host, port) },
                        onConnectToThisServer = { host, port ->
                            viewModel.startBot(host, port, botState.botName, directJoin = true)
                            currentTab = 0
                        }
                    )
                    3 -> ConsoleScreen(
                        logs = recentLogs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }
            }
        }
    }
}
