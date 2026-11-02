package com.example.engine

import com.example.BuildConfig
import com.example.data.model.AiProvider
import com.example.data.model.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiResponse(
    val thoughtProcess: String,
    val commands: List<String>,
    val chatMessage: String,
    val latencyMs: Long,
    val providerUsed: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class AiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun queryBotBrain(
        prompt: String,
        systemPersona: String,
        settings: AiSettings
    ): AiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val provider = settings.activeProvider
        val apiKey = settings.apiKeys[provider] ?: ""

        when (provider) {
            AiProvider.GEMINI -> callGemini(prompt, systemPersona, settings.selectedModel, apiKey, startTime)
            AiProvider.OPENAI -> callOpenAi(prompt, systemPersona, settings.selectedModel, apiKey, startTime)
            AiProvider.ANTHROPIC -> callAnthropic(prompt, systemPersona, settings.selectedModel, apiKey, startTime)
            AiProvider.GROQ -> callGroq(prompt, systemPersona, settings.selectedModel, apiKey, startTime)
            AiProvider.CUSTOM_HTTP -> callCustomHttp(prompt, systemPersona, settings.customEndpointUrl, settings.selectedModel, apiKey, startTime)
            AiProvider.LOCAL_EDGE -> runLocalEdgeModel(prompt, settings.activeLocalModelId, startTime)
        }
    }

    private suspend fun callGemini(
        prompt: String,
        systemPersona: String,
        modelName: String,
        apiKey: String,
        startTime: Long
    ): AiResponse {
        val effectiveKey = apiKey.ifBlank {
            try {
                // Check if BuildConfig has GEMINI_API_KEY
                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                (field.get(null) as? String) ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        if (effectiveKey.isBlank() || effectiveKey == "MY_GEMINI_API_KEY") {
            return unavailableResponse("Gemini", modelName, startTime, "Gemini API key is missing.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$effectiveKey"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "$systemPersona\n\n$prompt")))
            }))
        }

        return executeHttpChatRequest(url, payload.toString(), "Bearer $effectiveKey", startTime, "Gemini $modelName")
    }

    private suspend fun callOpenAi(
        prompt: String,
        systemPersona: String,
        model: String,
        apiKey: String,
        startTime: Long
    ): AiResponse {
        if (apiKey.isBlank()) {
            return unavailableResponse("OpenAI", model, startTime, "OpenAI API key is missing.")
        }

        val url = "https://api.openai.com/v1/chat/completions"
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPersona)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
        }

        return executeHttpChatRequest(url, payload.toString(), "Bearer $apiKey", startTime, "OpenAI $model")
    }

    private suspend fun callAnthropic(
        prompt: String,
        systemPersona: String,
        model: String,
        apiKey: String,
        startTime: Long
    ): AiResponse {
        if (apiKey.isBlank()) {
            return unavailableResponse("Anthropic", model, startTime, "Anthropic API key is missing.")
        }

        val url = "https://api.anthropic.com/v1/messages"
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("system", systemPersona)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val contentArray = json.optJSONArray("content")
                val text = contentArray?.optJSONObject(0)?.optString("text").orEmpty()
                return parseMindcraftOutput(text, latency, "Claude $model")
            } else {
                return AiResponse(
                    thoughtProcess = "Anthropic API Error: ${response.code}",
                    commands = emptyList(),
                    chatMessage = "Error connecting to Claude API: ${response.message}",
                    latencyMs = latency,
                    providerUsed = "Claude $model",
                    isSuccess = false,
                    errorMessage = body
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            return AiResponse(
                thoughtProcess = "Connection exception: ${e.localizedMessage}",
                commands = emptyList(),
                chatMessage = "Failed to query Claude API",
                latencyMs = latency,
                providerUsed = "Claude $model",
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private suspend fun callGroq(
        prompt: String,
        systemPersona: String,
        model: String,
        apiKey: String,
        startTime: Long
    ): AiResponse {
        if (apiKey.isBlank()) {
            return unavailableResponse("Groq", model, startTime, "Groq API key is missing.")
        }

        val url = "https://api.groq.com/openai/v1/chat/completions"
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPersona)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        return executeHttpChatRequest(url, payload.toString(), "Bearer $apiKey", startTime, "Groq $model")
    }

    private suspend fun callCustomHttp(
        prompt: String,
        systemPersona: String,
        endpoint: String,
        model: String,
        apiKey: String,
        startTime: Long
    ): AiResponse {
        if (endpoint.isBlank()) {
            return unavailableResponse(
                "Custom HTTP",
                model,
                startTime,
                "Custom AI endpoint URL is missing."
            )
        }
        val targetUrl = if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPersona)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        return executeHttpChatRequest(targetUrl, payload.toString(), if (apiKey.isNotBlank()) "Bearer $apiKey" else null, startTime, "Custom $model")
    }

    private fun executeHttpChatRequest(
        url: String,
        jsonBody: String,
        authHeader: String?,
        startTime: Long,
        providerName: String
    ): AiResponse {
        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(mediaType))

            if (!authHeader.isNullOrBlank()) {
                reqBuilder.header("Authorization", authHeader)
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val rawText = when {
                    json.has("choices") -> json.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    json.has("candidates") -> json.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    else -> return AiResponse(
                        thoughtProcess = "Provider returned an unsupported response format.",
                        commands = emptyList(),
                        chatMessage = "",
                        latencyMs = latency,
                        providerUsed = providerName,
                        isSuccess = false,
                        errorMessage = "HTTP request succeeded, but the response did not contain a supported chat completion field."
                    )
                }
                return parseMindcraftOutput(rawText, latency, providerName)
            } else {
                return AiResponse(
                    thoughtProcess = "Provider returned HTTP ${response.code}",
                    commands = emptyList(),
                    chatMessage = "API error: ${response.message}",
                    latencyMs = latency,
                    providerUsed = providerName,
                    isSuccess = false,
                    errorMessage = body
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            return AiResponse(
                thoughtProcess = "Request Failed: ${e.localizedMessage}",
                commands = emptyList(),
                chatMessage = "Network error while calling AI provider.",
                latencyMs = latency,
                providerUsed = providerName,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private suspend fun runLocalEdgeModel(
        prompt: String,
        modelId: String,
        startTime: Long
    ): AiResponse {
        return unavailableResponse(
            "Local model",
            modelId,
            startTime,
            "No on-device model runtime is installed. Select a configured cloud provider or install a real local model runtime."
        )
    }

    private fun unavailableResponse(
        provider: String,
        model: String,
        startTime: Long,
        reason: String
    ): AiResponse = AiResponse(
        thoughtProcess = "AI request was not executed.",
        commands = emptyList(),
        chatMessage = reason,
        latencyMs = System.currentTimeMillis() - startTime,
        providerUsed = "$provider ($model)",
        isSuccess = false,
        errorMessage = reason
    )

    private fun parseMindcraftOutput(raw: String, latency: Long, providerName: String): AiResponse {
        if (raw.isBlank()) {
            return AiResponse(
                thoughtProcess = "The provider returned an empty response.",
                commands = emptyList(),
                chatMessage = "",
                latencyMs = latency,
                providerUsed = providerName,
                isSuccess = false,
                errorMessage = "The AI provider returned HTTP success but no usable response text."
            )
        }

        val lines = raw.lines()
        val commands = mutableListOf<String>()
        val thoughts = StringBuilder()
        val chatLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("!")) {
                commands.add(trimmed)
            } else if (trimmed.startsWith("Andy:") || trimmed.startsWith("Bot:")) {
                chatLines.add(trimmed.substringAfter(":").trim())
            } else if (trimmed.isNotBlank()) {
                if (thoughts.isNotEmpty()) thoughts.append("\\n")
                thoughts.append(trimmed)
            }
        }

        // A provider's actual text is the response. Never synthesize a success message.
        val chatMsg = chatLines.joinToString("\\n").ifBlank {
            if (commands.isEmpty()) raw.trim() else ""
        }

        return AiResponse(
            thoughtProcess = thoughts.toString(),
            commands = commands,
            chatMessage = chatMsg,
            latencyMs = latency,
            providerUsed = providerName,
            isSuccess = true
        )
    }
}
