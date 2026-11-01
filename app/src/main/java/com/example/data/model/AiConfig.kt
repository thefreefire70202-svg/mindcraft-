package com.example.data.model

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val docsUrl: String,
    val keyHint: String,
    val isLocal: Boolean = false
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro"),
        docsUrl = "https://aistudio.google.com/app/apikey",
        keyHint = "AIzaSy..."
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"),
        docsUrl = "https://platform.openai.com/api-keys",
        keyHint = "sk-proj-..."
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-sonnet-20241022",
        availableModels = listOf("claude-3-5-sonnet-20241022", "claude-3-haiku-20240307"),
        docsUrl = "https://console.anthropic.com/settings/keys",
        keyHint = "sk-ant-..."
    ),
    GROQ(
        displayName = "Groq Ultra-Fast",
        defaultModel = "llama-3.3-70b-versatile",
        availableModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
        docsUrl = "https://console.groq.com/keys",
        keyHint = "gsk_..."
    ),
    LOCAL_EDGE(
        displayName = "Local Edge AI",
        defaultModel = "SmolLM2-360M-Instruct",
        availableModels = listOf("SmolLM2-360M-Instruct", "TinyLlama-1.1B-Chat", "Qwen2.5-0.5B-Instruct"),
        docsUrl = "Offline On-Device",
        keyHint = "No API Key required (Offline)",
        isLocal = true
    ),
    CUSTOM_HTTP(
        displayName = "Custom Ollama / HTTP",
        defaultModel = "llama3:latest",
        availableModels = listOf("llama3:latest", "mistral:latest", "phi3:mini"),
        docsUrl = "http://192.168.1.X:11434/v1",
        keyHint = "Optional bearer token"
    )
}

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class LocalAiModel(
    val id: String,
    val name: String,
    val description: String,
    val badge: String,
    val parameterCount: String,
    val quantization: String,
    val totalSizeMb: Double,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = (totalSizeMb * 1024 * 1024).toLong(),
    val downloadState: DownloadState = DownloadState.IDLE,
    val downloadSpeedMbPerSec: Double = 0.0,
    val downloadEtaSeconds: Int = 0,
    val isActivated: Boolean = false,
    val downloadUrl: String
) {
    val downloadedSizeMb: Double
        get() = downloadedBytes / (1024.0 * 1024.0)

    val progressPercent: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class AiSettings(
    val activeProvider: AiProvider = AiProvider.GEMINI,
    val selectedModel: String = AiProvider.GEMINI.defaultModel,
    val apiKeys: Map<AiProvider, String> = emptyMap(),
    val customEndpointUrl: String = "http://10.0.2.2:11434/v1",
    val activeLocalModelId: String = "smollm2-360m",
    val temperature: Float = 0.7f,
    val systemPromptPersona: String = "Andy the Mindcraft Bot: You are an autonomous builder, miner, and companion inside Minecraft. Answer questions and output structured !commands."
)
