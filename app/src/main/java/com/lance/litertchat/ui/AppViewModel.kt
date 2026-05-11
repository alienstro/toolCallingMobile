package com.lance.litertchat.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lance.litertchat.download.ModelDownloadClient
import com.lance.litertchat.download.ModelDownloader
import com.lance.litertchat.inference.ChatEngine
import com.lance.litertchat.inference.LiteRtChatEngine
import com.lance.litertchat.model.ModelConstants
import com.lance.litertchat.model.ModelMetadata
import com.lance.litertchat.model.ModelRepository
import com.lance.litertchat.prompt.PromptFormatter
import com.lance.litertchat.prompt.PromptFormatterRepository
import com.lance.litertchat.settings.AppSettingsRepository
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false
)

data class GenerationStats(
    val elapsedSeconds: Double,
    val totalTokens: Int,
    val tokensPerSecond: Double
)

data class AppState(
    val activeModel: ModelMetadata? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isDownloading: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val downloadProgressText: String? = null,
    val errorText: String? = null,
    val generationStats: GenerationStats? = null,
    val promptFormatters: List<PromptFormatter> = emptyList(),
    val activePromptFormatterId: String = PromptFormatterRepository.DEFAULT_FORMATTER_ID,
    val streamResponsesEnabled: Boolean = true
) {
    val canChat: Boolean
        get() = activeModel != null && !isDownloading && !isLoadingModel && !isGenerating

    fun withUserMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "user", content = content))

    fun withAssistantMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "assistant", content = content))
}

class AppViewModel(
    private val repository: ModelRepository,
    private val promptFormatterRepository: PromptFormatterRepository = PromptFormatterRepository(repository.rootDir),
    private val appSettingsRepository: AppSettingsRepository = AppSettingsRepository(repository.rootDir),
    private val downloader: ModelDownloadClient = ModelDownloader(),
    private val engine: ChatEngine = LiteRtChatEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTimeProvider: () -> Long = System::nanoTime
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AppState(
            activeModel = repository.loadMetadata(),
            promptFormatters = promptFormatterRepository.loadState().formatters,
            activePromptFormatterId = promptFormatterRepository.loadState().activeFormatterId,
            streamResponsesEnabled = appSettingsRepository.load().streamResponsesEnabled
        )
    )
    val state: StateFlow<AppState> = mutableState
    private var generationJob: Job? = null

    fun downloadModel(url: String) {
        var shouldStart = false
        mutableState.update {
            if (it.isDownloading) {
                it
            } else {
                shouldStart = true
                it.copy(
                    isDownloading = true,
                    downloadProgressText = "Starting download",
                    errorText = null
                )
            }
        }
        if (!shouldStart) return

        viewModelScope.launch {
            runCatching {
                val normalizedUrl = ModelDownloader.normalizeModelUrl(url)
                val fileName = fileNameFromUrl(normalizedUrl)
                val destination = withContext(ioDispatcher) {
                    File(repository.modelDirectory(), fileName)
                }

                downloader.download(normalizedUrl, destination) { downloaded, total ->
                    val totalText = total?.let { "/$it" }.orEmpty()
                    mutableState.update {
                        it.copy(downloadProgressText = "$downloaded$totalText bytes")
                    }
                }

                ModelMetadata(
                    fileName = destination.name,
                    absolutePath = destination.absolutePath,
                    source = "download",
                    sourceUrl = normalizedUrl,
                    sizeBytes = destination.length(),
                    installedAtEpochMillis = System.currentTimeMillis()
                ).also { metadata ->
                    withContext(ioDispatcher) {
                        repository.saveMetadata(metadata)
                    }
                }
            }.onSuccess { metadata ->
                mutableState.update {
                    it.copy(
                        activeModel = metadata,
                        isDownloading = false,
                        downloadProgressText = "Download complete",
                        errorText = null
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgressText = null,
                        errorText = error.message ?: "Download failed"
                    )
                }
            }
        }
    }

    fun registerImportedModel(file: File) {
        if (mutableState.value.isDownloading) return
        viewModelScope.launch {
            val metadata = withContext(ioDispatcher) {
                ModelMetadata(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    source = "import",
                    sourceUrl = null,
                    sizeBytes = file.length(),
                    installedAtEpochMillis = System.currentTimeMillis()
                ).also { repository.saveMetadata(it) }
            }
            mutableState.update {
                it.copy(activeModel = metadata, errorText = null)
            }
        }
    }

    fun importModelFromUri(context: Context, uri: Uri) {
        if (mutableState.value.isDownloading) return

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    val destination = File(
                        repository.modelDirectory(),
                        "imported-${System.currentTimeMillis()}${ModelConstants.MODEL_EXTENSION}"
                    )
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open selected file." }
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    require(destination.length() > 0L) { "Imported model file was empty." }
                    ModelMetadata(
                        fileName = destination.name,
                        absolutePath = destination.absolutePath,
                        source = "import",
                        sourceUrl = null,
                        sizeBytes = destination.length(),
                        installedAtEpochMillis = System.currentTimeMillis()
                    ).also { metadata ->
                        repository.saveMetadata(metadata)
                    }
                }
            }.onSuccess { metadata ->
                mutableState.update {
                    it.copy(activeModel = metadata, errorText = null)
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(errorText = error.message ?: "Import failed")
                }
            }
        }
    }

    fun deleteModel() {
        if (mutableState.value.isDownloading) return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                repository.deleteInstalledModel()
            }
            mutableState.value = AppState()
        }
    }

    fun sendMessage(prompt: String) {
        val cleanedPrompt = prompt.trim()
        if (cleanedPrompt.isBlank()) return

        val currentState = mutableState.value
        val model = currentState.activeModel
        if (model == null) {
            mutableState.update {
                it.copy(errorText = "Install a model before chatting.")
            }
            return
        }
        if (!currentState.canChat) return

        generationJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    messages = it.messages + ChatMessage("user", cleanedPrompt) + loadingAssistantMessage(),
                    isGenerating = true,
                    errorText = null,
                    generationStats = null
                )
            }

            val startedAtNanos = nanoTimeProvider()
            val modelPrompt = promptForModel(cleanedPrompt)
            val streamResponsesEnabled = appSettingsRepository.load().streamResponsesEnabled
            engine.load(File(model.absolutePath)).fold(
                onSuccess = {
                    try {
                        val streamedText = StringBuilder()
                        val generationResult = if (streamResponsesEnabled) {
                            engine.generateStreaming(modelPrompt) { partialResponse ->
                                if (isActive) {
                                    val displayText = mergeStreamChunk(
                                        currentText = streamedText.toString(),
                                        chunk = partialResponse
                                    )
                                    streamedText.clear()
                                    streamedText.append(displayText)
                                    mutableState.update {
                                        it.updateLoadingAssistant(displayText)
                                    }
                                }
                            }
                        } else {
                            engine.generate(modelPrompt)
                        }
                        generationResult.fold(
                            onSuccess = { response ->
                                if (!isActive) return@fold
                                val finalResponse = if (streamResponsesEnabled && streamedText.isNotBlank()) {
                                    streamedText.toString()
                                } else {
                                    response
                                }
                                val elapsedSeconds =
                                    (nanoTimeProvider() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_SECOND
                                val totalTokens = estimateTokenCount(finalResponse)
                                val tokensPerSecond = if (elapsedSeconds > 0.0) {
                                    totalTokens / elapsedSeconds
                                } else {
                                    0.0
                                }
                                mutableState.update {
                                    it.replaceLoadingAssistant(finalResponse).copy(
                                        isGenerating = false,
                                        generationStats = GenerationStats(
                                            elapsedSeconds = elapsedSeconds,
                                            totalTokens = totalTokens,
                                            tokensPerSecond = tokensPerSecond
                                        )
                                    )
                                }
                            },
                            onFailure = { error ->
                                if (!isActive) return@fold
                                mutableState.update {
                                    it.withoutLoadingAssistant().copy(
                                        isGenerating = false,
                                        errorText = error.message ?: "Generation failed"
                                    )
                                }
                            }
                        )
                    } catch (error: CancellationException) {
                        throw error
                    }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    mutableState.update {
                        it.withoutLoadingAssistant().copy(
                            isGenerating = false,
                            errorText = error.message ?: "Model load failed"
                        )
                    }
                }
            )
        }
    }

    fun stopGeneration() {
        val activeJob = generationJob ?: return
        if (!mutableState.value.isGenerating) return

        activeJob.cancel()
        engine.cancelGeneration()
        engine.release()
        generationJob = null
        mutableState.update {
            it.withoutLoadingAssistant().copy(
                isGenerating = false,
                errorText = "Generation stopped."
            )
        }
    }

    fun createPromptFormatter(name: String, body: String) {
        promptFormatterRepository.createFormatter(name, body)
        refreshPromptFormatterState()
    }

    fun updatePromptFormatter(id: String, name: String, body: String) {
        promptFormatterRepository.updateFormatter(id, name, body)
        refreshPromptFormatterState()
    }

    fun deletePromptFormatter(id: String) {
        promptFormatterRepository.deleteFormatter(id)
        refreshPromptFormatterState()
    }

    fun selectPromptFormatter(id: String) {
        promptFormatterRepository.selectFormatter(id)
        refreshPromptFormatterState()
    }

    fun resetDefaultPromptFormatter() {
        promptFormatterRepository.resetDefaultFormatter()
        refreshPromptFormatterState()
    }

    fun setStreamResponsesEnabled(enabled: Boolean) {
        appSettingsRepository.setStreamResponsesEnabled(enabled)
        mutableState.update {
            it.copy(streamResponsesEnabled = enabled)
        }
    }

    private fun fileNameFromUrl(normalizedUrl: String): String {
        val rawName = URI(normalizedUrl).rawPath.substringAfterLast("/")
        return URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
    }

    private fun estimateTokenCount(text: String): Int =
        text.trim()
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }

    private fun loadingAssistantMessage(): ChatMessage =
        ChatMessage("assistant", "Processing...", isLoading = true)

    private fun promptForModel(userPrompt: String): String {
        val formatter = promptFormatterRepository.loadState().activeFormatter
        val formatterBody = formatter?.body.orEmpty().trim()
        return if (formatterBody.isBlank()) {
            userPrompt
        } else {
            "$formatterBody\n\nUser message:\n$userPrompt"
        }
    }

    private fun mergeStreamChunk(currentText: String, chunk: String): String =
        if (chunk.startsWith(currentText)) {
            chunk
        } else {
            currentText + chunk
        }

    private fun refreshPromptFormatterState() {
        val formatterState = promptFormatterRepository.loadState()
        mutableState.update {
            it.copy(
                promptFormatters = formatterState.formatters,
                activePromptFormatterId = formatterState.activeFormatterId,
                streamResponsesEnabled = appSettingsRepository.load().streamResponsesEnabled
            )
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

private fun AppState.withoutLoadingAssistant(): AppState =
    copy(messages = messages.filterNot { it.role == "assistant" && it.isLoading })

private fun AppState.replaceLoadingAssistant(content: String): AppState {
    val loadingIndex = messages.indexOfLast { it.role == "assistant" && it.isLoading }
    if (loadingIndex == -1) return withAssistantMessage(content)

    return copy(
        messages = messages.mapIndexed { index, message ->
            if (index == loadingIndex) {
                ChatMessage("assistant", content)
            } else {
                message
            }
        }
    )
}

private fun AppState.updateLoadingAssistant(content: String): AppState {
    val loadingIndex = messages.indexOfLast { it.role == "assistant" && it.isLoading }
    if (loadingIndex == -1) return this

    return copy(
        messages = messages.mapIndexed { index, message ->
            if (index == loadingIndex) {
                ChatMessage("assistant", content, isLoading = true)
            } else {
                message
            }
        }
    )
}
