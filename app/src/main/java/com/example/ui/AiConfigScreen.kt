package com.example.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiProvider
import com.example.data.model.AiSettings
import com.example.data.model.DownloadState
import com.example.data.model.LocalAiModel
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
fun AiConfigScreen(
    aiSettings: AiSettings,
    localModels: List<LocalAiModel>,
    onSelectProvider: (AiProvider) -> Unit,
    onSelectModel: (String) -> Unit,
    onSetApiKey: (AiProvider, String) -> Unit,
    onSetCustomEndpoint: (String) -> Unit,
    onStartDownload: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onActivateModel: (String) -> Unit,
    onAddCustomModel: (String, String, Double) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        Text(
            text = "AI Brain Setup",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Text(
            text = "Choose whether your bot is powered by a cloud API or a downloaded local model.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = DiamondBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = DiamondBlue
                )
            },
            divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder)) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    focusManager.clearFocus()
                    selectedTab = 0
                },
                text = { Text("Cloud APIs", style = MaterialTheme.typography.titleSmall) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    focusManager.clearFocus()
                    selectedTab = 1
                },
                text = { Text("Local Model (Offline)", style = MaterialTheme.typography.titleSmall) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Cloud APIs
            val providers = listOf(
                AiProvider.GEMINI,
                AiProvider.OPENAI,
                AiProvider.ANTHROPIC,
                AiProvider.GROQ,
                AiProvider.CUSTOM_HTTP
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers) { provider ->
                    val isSelected = aiSettings.activeProvider == provider
                    val currentKey = aiSettings.apiKeys[provider].orEmpty()
                    var showKey by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_card_${provider.name.lowercase()}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SurfaceElevated else SurfaceDark
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) DiamondBlue else SurfaceBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) DiamondBlue else TextPrimary
                                    )
                                    Text(
                                        text = "Model: ${provider.defaultModel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }

                                Button(
                                    onClick = { onSelectProvider(provider) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) DiamondBlue else SurfaceElevated
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (isSelected) "Active" else "Use",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.Black else TextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (provider != AiProvider.CUSTOM_HTTP) {
                                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                                OutlinedTextField(
                                    value = currentKey,
                                    onValueChange = { onSetApiKey(provider, it) },
                                    label = { Text("${provider.displayName} API Key") },
                                    placeholder = { Text("Paste key here") },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { showKey = !showKey }) {
                                                Icon(
                                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = TextMuted
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                                    if (!clip.isNullOrBlank()) {
                                                        onSetApiKey(provider, clip.trim())
                                                        focusManager.clearFocus()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = DiamondBlue)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DiamondBlue,
                                        unfocusedBorderColor = SurfaceBorder
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            } else {
                                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                                OutlinedTextField(
                                    value = aiSettings.customEndpointUrl,
                                    onValueChange = onSetCustomEndpoint,
                                    label = { Text("Ollama URL") },
                                    placeholder = { Text("http://192.168.1.X:11434/v1") },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DiamondBlue,
                                        unfocusedBorderColor = SurfaceBorder
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Local Model Downloader
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Download a lightweight model to run offline directly on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                items(localModels) { model ->
                    val isCurrentActive = aiSettings.activeProvider == AiProvider.LOCAL_EDGE && model.id == aiSettings.activeLocalModelId

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentActive) SurfaceElevated else SurfaceDark
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrentActive) EmeraldGreen else SurfaceBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrentActive) EmeraldGreen else TextPrimary
                                    )
                                    Text(
                                        text = "${model.totalSizeMb.toInt()} MB • On-device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }

                                if (model.downloadState == DownloadState.COMPLETED) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Downloaded", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // MB Progress
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceElevated, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", model.downloadedSizeMb)} MB / ${String.format("%.1f", model.totalSizeMb)} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(model.progressPercent * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DiamondBlue
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { model.progressPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = when (model.downloadState) {
                                        DownloadState.COMPLETED -> EmeraldGreen
                                        DownloadState.DOWNLOADING -> DiamondBlue
                                        else -> TextMuted
                                    },
                                    trackColor = SurfaceDark
                                )

                                if (model.downloadState == DownloadState.DOWNLOADING) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${String.format("%.1f", model.downloadSpeedMbPerSec)} MB/s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldGreen
                                        )
                                        Text(
                                            text = "${model.downloadEtaSeconds}s remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (model.downloadState) {
                                    DownloadState.IDLE, DownloadState.PAUSED, DownloadState.ERROR -> {
                                        Button(
                                            onClick = { onStartDownload(model.id) },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DiamondBlue),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (model.downloadState == DownloadState.PAUSED) "Resume Download" else "Download Model (${model.totalSizeMb.toInt()} MB)")
                                        }
                                    }
                                    DownloadState.DOWNLOADING -> {
                                        OutlinedButton(
                                            onClick = { onPauseDownload(model.id) },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow)
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pause", color = GoldYellow)
                                        }
                                    }
                                    DownloadState.COMPLETED -> {
                                        Button(
                                            onClick = { onActivateModel(model.id) },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrentActive) EmeraldGreen else DiamondBlue
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(if (isCurrentActive) "Active" else "Use This Model")
                                        }

                                        IconButton(onClick = { onDeleteModel(model.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedstoneRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    var customName by remember { mutableStateOf("") }
                    var customUrl by remember { mutableStateOf("") }
                    var customSizeMb by remember { mutableStateOf("250") }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Download Custom Model URL",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Paste any direct Hugging Face or HTTP/HTTPS link to a .gguf model. Supports pausing and resume via OkHttp Range headers.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Model Name") },
                                placeholder = { Text("e.g. Gemma 2 2B Instruct") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DiamondBlue,
                                    unfocusedBorderColor = SurfaceBorder
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text("Direct GGUF URL") },
                                placeholder = { Text("https://huggingface.co/.../model.gguf") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DiamondBlue,
                                    unfocusedBorderColor = SurfaceBorder
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (customUrl.isNotBlank()) {
                                        focusManager.clearFocus()
                                        val mb = customSizeMb.toDoubleOrNull() ?: 250.0
                                        onAddCustomModel(customName, customUrl, mb)
                                        customName = ""
                                        customUrl = ""
                                    }
                                },
                                enabled = customUrl.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DiamondBlue),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Resumable Download")
                            }
                        }
                    }
                }
            }
        }
    }
}
