package com.example.engine

import android.content.Context
import com.example.data.model.DownloadState
import com.example.data.model.LocalAiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Robust Model Download Manager Service using Kotlin Coroutines and OkHttp.
 * Supports:
 * - Resumable downloads via HTTP Range headers ("bytes=X-")
 * - Real-time progress tracking in MegaBytes (MB)
 * - Pause, resume, cancel, and activation of local AI model weights (.gguf)
 * - Accurate speed (MB/s) and ETA calculations
 */
class LocalModelDownloadManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val presetModels = listOf(
        LocalAiModel(
            id = "smollm2-360m",
            name = "SmolLM2 360M Instruct",
            description = "Ultra-lightweight edge LLM tuned for quick Minecraft command execution, spatial reasoning, and tool use on mobile devices.",
            badge = "FASTEST (RECOMMENDED)",
            parameterCount = "360 Million params",
            quantization = "Q4_K_M GGUF",
            totalSizeMb = 185.4,
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf"
        ),
        LocalAiModel(
            id = "qwen2.5-0.5b",
            name = "Qwen 2.5 0.5B Instruct",
            description = "High-accuracy compact model with superior multilingual Minecraft spatial reasoning and math skills.",
            badge = "HIGH REASONING",
            parameterCount = "500 Million params",
            quantization = "Q4_K_M GGUF",
            totalSizeMb = 312.8,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ),
        LocalAiModel(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B Chat",
            description = "Classic robust small language model for conversational roleplay, complex quest building, and dialog.",
            badge = "EXPANDED MEMORY",
            parameterCount = "1.1 Billion params",
            quantization = "Q4_0 GGUF",
            totalSizeMb = 638.2,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_0.gguf"
        )
    )

    private val _models = MutableStateFlow<List<LocalAiModel>>(emptyList())
    val models: StateFlow<List<LocalAiModel>> = _models.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    init {
        // Inspect files on disk to restore previously downloaded or partially downloaded state
        val restored = presetModels.map { model ->
            val file = File(modelsDir, "${model.id}.gguf")
            val partialFile = File(modelsDir, "${model.id}.gguf.part")

            if (file.exists() && file.length() > 0) {
                model.copy(
                    downloadedBytes = file.length(),
                    downloadState = DownloadState.COMPLETED,
                    isActivated = (model.id == "smollm2-360m")
                )
            } else if (partialFile.exists() && partialFile.length() > 0) {
                model.copy(
                    downloadedBytes = partialFile.length(),
                    downloadState = DownloadState.PAUSED
                )
            } else {
                model
            }
        }
        _models.value = restored
    }

    /**
     * Start or Resume downloading a model using Coroutines and OkHttp with Range headers.
     */
    fun startDownload(modelId: String) {
        val model = _models.value.find { it.id == modelId } ?: return
        if (model.downloadState == DownloadState.DOWNLOADING || model.downloadState == DownloadState.COMPLETED) return

        // Cancel any lingering job/call
        pauseDownload(modelId)

        val targetPartFile = File(modelsDir, "${model.id}.gguf.part")
        val finalFile = File(modelsDir, "${model.id}.gguf")

        updateModelState(modelId) {
            it.copy(
                downloadState = DownloadState.DOWNLOADING,
                downloadSpeedMbPerSec = 0.0
            )
        }

        val job = coroutineScope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var fileOutputStream: FileOutputStream? = null
            var response: Response? = null

            try {
                val existingBytes = if (targetPartFile.exists()) targetPartFile.length() else 0L

                val requestBuilder = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "MindcraftBotHost/2.0 (Android)")

                // Add HTTP Range header for resumable download if partial bytes exist
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val request = requestBuilder.build()
                val call = okHttpClient.newCall(request)
                activeCalls[modelId] = call

                response = call.execute()
                val code = response.code

                if (!response.isSuccessful && code != 206 && code != 416) {
                    throw IllegalStateException("Server returned HTTP $code: ${response.message}")
                }

                if (code == 416) {
                    // Range Not Satisfiable: partial file might already be complete!
                    if (existingBytes >= model.totalBytes && model.totalBytes > 0) {
                        targetPartFile.renameTo(finalFile)
                        updateModelState(modelId) {
                            it.copy(
                                downloadedBytes = finalFile.length(),
                                downloadState = DownloadState.COMPLETED,
                                downloadSpeedMbPerSec = 0.0,
                                downloadEtaSeconds = 0
                            )
                        }
                        return@launch
                    } else {
                        // File corrupted or invalid range, reset from scratch
                        targetPartFile.delete()
                    }
                }

                val isPartialContent = (code == 206)
                val body = response.body ?: throw IllegalStateException("Empty response body from server")
                val contentLength = body.contentLength()
                val totalExpectedBytes = if (isPartialContent) {
                    existingBytes + (if (contentLength > 0) contentLength else 0L)
                } else {
                    if (contentLength > 0) contentLength else model.totalBytes
                }

                var currentBytes = if (isPartialContent) existingBytes else 0L

                // If server sent full content (200 OK), overwrite/truncate partial file
                fileOutputStream = FileOutputStream(targetPartFile, isPartialContent)
                inputStream = body.byteStream()

                val buffer = ByteArray(64 * 1024) // 64 KB buffer for high-throughput mobile streaming
                var lastSampleTime = System.currentTimeMillis()
                var bytesInSample = 0L

                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    fileOutputStream.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead
                    bytesInSample += bytesRead

                    val now = System.currentTimeMillis()
                    val diff = now - lastSampleTime
                    if (diff >= 350) { // Update UI every 350ms
                        val seconds = diff / 1000.0
                        val speedMbPerSec = if (seconds > 0) (bytesInSample / (1024.0 * 1024.0)) / seconds else 0.0
                        val remainingBytes = max(0L, totalExpectedBytes - currentBytes)
                        val remainingSeconds = if (speedMbPerSec > 0) {
                            ((remainingBytes / (1024.0 * 1024.0)) / speedMbPerSec).toInt()
                        } else 0

                        updateModelState(modelId) {
                            it.copy(
                                downloadedBytes = currentBytes,
                                totalBytes = max(it.totalBytes, totalExpectedBytes),
                                totalSizeMb = if (totalExpectedBytes > 0) totalExpectedBytes / (1024.0 * 1024.0) else it.totalSizeMb,
                                downloadSpeedMbPerSec = speedMbPerSec,
                                downloadEtaSeconds = remainingSeconds
                            )
                        }
                        bytesInSample = 0L
                        lastSampleTime = now
                    }
                }

                fileOutputStream.flush()

                if (isActive) {
                    // Download completed successfully!
                    if (finalFile.exists()) finalFile.delete()
                    targetPartFile.renameTo(finalFile)

                    updateModelState(modelId) {
                        it.copy(
                            downloadedBytes = finalFile.length(),
                            totalBytes = finalFile.length(),
                            totalSizeMb = finalFile.length() / (1024.0 * 1024.0),
                            downloadState = DownloadState.COMPLETED,
                            downloadSpeedMbPerSec = 0.0,
                            downloadEtaSeconds = 0,
                            isActivated = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                // User paused or cancelled download
                val partialLen = if (targetPartFile.exists()) targetPartFile.length() else 0L
                updateModelState(modelId) {
                    it.copy(
                        downloadedBytes = partialLen,
                        downloadState = DownloadState.PAUSED,
                        downloadSpeedMbPerSec = 0.0,
                        downloadEtaSeconds = 0
                    )
                }
            } catch (e: Exception) {
                val partialLen = if (targetPartFile.exists()) targetPartFile.length() else 0L
                updateModelState(modelId) {
                    it.copy(
                        downloadedBytes = partialLen,
                        downloadState = DownloadState.ERROR,
                        downloadSpeedMbPerSec = 0.0,
                        downloadEtaSeconds = 0
                    )
                }
            } finally {
                try {
                    fileOutputStream?.close()
                } catch (_: Exception) {}
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    response?.close()
                } catch (_: Exception) {}

                activeCalls.remove(modelId)
                downloadJobs.remove(modelId)
            }
        }

        downloadJobs[modelId] = job
    }

    /**
     * Pause an active download, safely preserving downloaded bytes on disk for subsequent resume.
     */
    fun pauseDownload(modelId: String) {
        activeCalls[modelId]?.cancel()
        activeCalls.remove(modelId)

        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)

        val targetPartFile = File(modelsDir, "${modelId}.gguf.part")
        val currentBytes = if (targetPartFile.exists()) targetPartFile.length() else 0L

        updateModelState(modelId) {
            it.copy(
                downloadedBytes = currentBytes,
                downloadState = if (it.downloadState == DownloadState.COMPLETED) DownloadState.COMPLETED else DownloadState.PAUSED,
                downloadSpeedMbPerSec = 0.0,
                downloadEtaSeconds = 0
            )
        }
    }

    /**
     * Delete model from disk and reset state to IDLE.
     */
    fun deleteModel(modelId: String) {
        pauseDownload(modelId)

        val finalFile = File(modelsDir, "${modelId}.gguf")
        val partFile = File(modelsDir, "${modelId}.gguf.part")

        if (finalFile.exists()) finalFile.delete()
        if (partFile.exists()) partFile.delete()

        updateModelState(modelId) {
            it.copy(
                downloadedBytes = 0L,
                downloadState = DownloadState.IDLE,
                downloadSpeedMbPerSec = 0.0,
                downloadEtaSeconds = 0,
                isActivated = false
            )
        }
    }

    /**
     * Activate a downloaded model for local edge inference.
     */
    fun activateModel(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId && it.downloadState == DownloadState.COMPLETED) {
                it.copy(isActivated = true)
            } else {
                it.copy(isActivated = false)
            }
        }
    }

    /**
     * Add a custom model URL to download with resumption.
     */
    fun addCustomModel(name: String, url: String, estimatedSizeMb: Double = 250.0) {
        val id = "custom-" + System.currentTimeMillis()
        val customModel = LocalAiModel(
            id = id,
            name = name.ifBlank { "Custom GGUF Model" },
            description = "Custom edge LLM imported from external URL with resumable download.",
            badge = "CUSTOM",
            parameterCount = "Custom",
            quantization = "GGUF",
            totalSizeMb = estimatedSizeMb,
            downloadUrl = url.trim()
        )
        _models.value = _models.value + customModel
        startDownload(id)
    }

    private fun updateModelState(modelId: String, transform: (LocalAiModel) -> LocalAiModel) {
        _models.value = _models.value.map {
            if (it.id == modelId) transform(it) else it
        }
    }
}
