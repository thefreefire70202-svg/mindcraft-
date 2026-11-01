package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.BotLogEntity
import com.example.data.db.ErrorReportEntity
import com.example.data.model.AiProvider
import com.example.data.model.AiSettings
import com.example.data.model.BotState
import com.example.data.model.LocalAiModel
import com.example.engine.AiClient
import com.example.engine.LocalModelDownloadManager
import com.example.engine.MindcraftBotEngine
import com.example.engine.ServerStatusResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val aiClient = AiClient()
    private val downloadManager = LocalModelDownloadManager(application, viewModelScope)
    private val botEngine = MindcraftBotEngine(database, aiClient, viewModelScope)

    val botState: StateFlow<BotState> = botEngine.botState

    private val _aiSettings = MutableStateFlow(
        AiSettings(
            activeProvider = AiProvider.GEMINI,
            selectedModel = AiProvider.GEMINI.defaultModel
        )
    )
    val aiSettings: StateFlow<AiSettings> = _aiSettings.asStateFlow()

    val localModels: StateFlow<List<LocalAiModel>> = downloadManager.models

    val recentLogs: StateFlow<List<BotLogEntity>> = database.botLogDao()
        .getRecentLogs(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val errorReports: StateFlow<List<ErrorReportEntity>> = database.errorReportDao()
        .getAllErrorReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serverQueryResult = MutableStateFlow<ServerStatusResult?>(null)
    val serverQueryResult: StateFlow<ServerStatusResult?> = _serverQueryResult.asStateFlow()

    private val _isQueryingServer = MutableStateFlow(false)
    val isQueryingServer: StateFlow<Boolean> = _isQueryingServer.asStateFlow()

    fun startBot(
        host: String = "localhost",
        port: Int = 25565,
        botName: String = "MindcraftBot",
        directJoin: Boolean = false
    ) {
        botEngine.startBot(host, port, botName, _aiSettings.value, directJoin)
    }

    fun stopBot() {
        botEngine.stopBot()
    }

    fun pauseBot() {
        botEngine.pauseBot()
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        botEngine.sendInGameChatMessage(text.trim(), _aiSettings.value)
    }

    fun queryServer(host: String, port: Int = 25565) {
        viewModelScope.launch {
            _isQueryingServer.value = true
            val result = botEngine.protocolClient.queryServerStatus(host.trim(), port)
            _serverQueryResult.value = result
            _isQueryingServer.value = false
        }
    }

    fun setAiProvider(provider: AiProvider) {
        _aiSettings.value = _aiSettings.value.copy(
            activeProvider = provider,
            selectedModel = provider.defaultModel
        )
    }

    fun setSelectedModel(model: String) {
        _aiSettings.value = _aiSettings.value.copy(selectedModel = model)
    }

    fun setApiKey(provider: AiProvider, key: String) {
        val updated = _aiSettings.value.apiKeys.toMutableMap()
        updated[provider] = key.trim()
        _aiSettings.value = _aiSettings.value.copy(apiKeys = updated)
    }

    fun setCustomEndpoint(url: String) {
        _aiSettings.value = _aiSettings.value.copy(customEndpointUrl = url.trim())
    }

    fun startModelDownload(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    fun pauseModelDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        downloadManager.deleteModel(modelId)
    }

    fun addCustomModel(name: String, url: String, estimatedMb: Double) {
        downloadManager.addCustomModel(name, url, estimatedMb)
    }

    fun activateLocalModel(modelId: String) {
        downloadManager.activateModel(modelId)
        _aiSettings.value = _aiSettings.value.copy(
            activeProvider = AiProvider.LOCAL_EDGE,
            activeLocalModelId = modelId,
            selectedModel = modelId
        )
    }

    fun clearLogs() {
        viewModelScope.launch {
            database.botLogDao().clearAllLogs()
        }
    }

    fun clearErrorReports() {
        viewModelScope.launch {
            database.errorReportDao().clearErrorReports()
        }
    }

    fun toggleAutoRecovery() {
        botEngine.toggleAutoRecovery()
    }

    fun toggleAiAutopilot() {
        botEngine.toggleAiAutopilot()
    }

    fun setVersionDetection(autoDetect: Boolean, manualProtocol: Int) {
        botEngine.setVersionDetection(autoDetect, manualProtocol)
    }
}
