package com.lance.llamacppchat.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lance.llamacppchat.download.ModelDownloadClient
import com.lance.llamacppchat.download.ModelDownloader
import com.lance.llamacppchat.inference.ChatEngine
import com.lance.llamacppchat.inference.EmbeddingEngine
import com.lance.llamacppchat.inference.InferenceBackend
import com.lance.llamacppchat.inference.InferenceRuntimeConfig
import com.lance.llamacppchat.inference.InferenceRuntimeStatus
import com.lance.llamacppchat.inference.UnavailableChatEngine
import com.lance.llamacppchat.inference.UnavailableEmbeddingEngine
import com.lance.llamacppchat.memory.EmbeddingStore
import com.lance.llamacppchat.memory.MemoryItem
import com.lance.llamacppchat.memory.MemoryRepository
import com.lance.llamacppchat.model.ModelConstants
import com.lance.llamacppchat.model.ModelMetadata
import com.lance.llamacppchat.model.ModelRepository
import com.lance.llamacppchat.prompt.PromptFormatter
import com.lance.llamacppchat.prompt.PromptFormatterRepository
import com.lance.llamacppchat.settings.AppSettings
import com.lance.llamacppchat.settings.AppSettingsRepository
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import com.lance.llamacppchat.tools.ToolCall
import com.lance.llamacppchat.tools.ToolCallParser
import com.lance.llamacppchat.tools.ToolRegistry
import com.lance.llamacppchat.tools.ToolResult
import com.lance.llamacppchat.tools.calendar.GoogleCalendarClient
import com.lance.llamacppchat.ui.chat.ChatHistoryRepository
import com.lance.llamacppchat.ui.chat.ChatHistoryState
import com.lance.llamacppchat.ui.chat.ChatSession
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false,
    val imagePath: String? = null
)

data class GenerationStats(
    val elapsedSeconds: Double,
    val totalTokens: Int,
    val tokensPerSecond: Double
)

data class AppState(
    val activeModel: ModelMetadata? = null,
    val messages: List<ChatMessage> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val activeChatSessionId: String? = null,
    val isDownloading: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val downloadProgressText: String? = null,
    val errorText: String? = null,
    val generationStats: GenerationStats? = null,
    val promptFormatters: List<PromptFormatter> = emptyList(),
    val activePromptFormatterId: String = PromptFormatterRepository.DEFAULT_FORMATTER_ID,
    val memories: List<MemoryItem> = emptyList(),
    val streamResponsesEnabled: Boolean = true,
    val gpuBackendEnabled: Boolean = false,
    val npuBackendEnabled: Boolean = false,
    val gemmaMtpEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val runtimeStatus: InferenceRuntimeStatus = InferenceRuntimeStatus(),
    val embeddingModelPath: String? = null,
    val isEmbeddingModelLoaded: Boolean = false,
    val isReIndexing: Boolean = false,
    val isToolExecuting: Boolean = false,
    val googleSignedIn: Boolean = false,
) {
    val canChat: Boolean
        get() = activeModel != null && !isDownloading && !isLoadingModel && !isGenerating

    val activeChatSession: ChatSession?
        get() = chatSessions.firstOrNull { it.id == activeChatSessionId }

    fun withUserMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "user", content = content))

    fun withAssistantMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "assistant", content = content))
}

class AppViewModel(
    private val repository: ModelRepository,
    private val promptFormatterRepository: PromptFormatterRepository = PromptFormatterRepository(repository.rootDir),
    private val appSettingsRepository: AppSettingsRepository = AppSettingsRepository(repository.rootDir),
    private val chatHistoryRepository: ChatHistoryRepository = ChatHistoryRepository(repository.rootDir),
    private val memoryRepository: MemoryRepository = MemoryRepository(repository.rootDir),
    private val embeddingEngine: EmbeddingEngine = UnavailableEmbeddingEngine,
    private val embeddingStore: EmbeddingStore = EmbeddingStore(repository.rootDir),
    private val downloader: ModelDownloadClient = ModelDownloader(),
    private val engine: ChatEngine = UnavailableChatEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTimeProvider: () -> Long = System::nanoTime,
    private val epochTimeProvider: () -> Long = System::currentTimeMillis,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val googleCalendarClient: GoogleCalendarClient? = null
) : ViewModel() {
    private val initialChatHistoryState = chatHistoryRepository.loadState()
    private val initialPromptFormatterState = promptFormatterRepository.loadState()
    private val initialSettings = appSettingsRepository.load()
    private val initialMemories = memoryRepository.loadMemories()
    private val mutableState = MutableStateFlow(
        AppState(
            activeModel = repository.loadMetadata(),
            messages = initialChatHistoryState.activeSession?.messages.orEmpty(),
            chatSessions = initialChatHistoryState.sessions,
            activeChatSessionId = initialChatHistoryState.activeSessionId,
            promptFormatters = initialPromptFormatterState.formatters,
            activePromptFormatterId = initialPromptFormatterState.activeFormatterId,
            memories = initialMemories,
            streamResponsesEnabled = initialSettings.streamResponsesEnabled,
            gpuBackendEnabled = initialSettings.gpuBackendEnabled,
            npuBackendEnabled = initialSettings.npuBackendEnabled,
            gemmaMtpEnabled = initialSettings.gemmaMtpEnabled,
            overlayEnabled = initialSettings.overlayEnabled,
            runtimeStatus = InferenceRuntimeStatus(
                requested = runtimeConfigForSettings(initialSettings)
            ),
            embeddingModelPath = initialSettings.embeddingModelPath,
            isEmbeddingModelLoaded = false,
            isReIndexing = false,
            googleSignedIn = googleCalendarClient?.isSignedIn() == true,
        )
    )
    val state: StateFlow<AppState> = mutableState
    private var generationJob: Job? = null
    private val _signInRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInRequest: SharedFlow<Unit> = _signInRequest
    private var pendingSignIn: CompletableDeferred<Boolean>? = null

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        googleCalendarClient?.handleSignInResult(account)
        val success = account != null
        pendingSignIn?.complete(success)
        pendingSignIn = null
        mutableState.update { it.copy(googleSignedIn = success) }
    }

    fun handleGoogleSignInFailure(message: String) {
        pendingSignIn?.complete(false)
        pendingSignIn = null
        mutableState.update {
            it.copy(
                googleSignedIn = false,
                errorText = message
            )
        }
    }

    private suspend fun awaitGoogleSignIn(): Boolean {
        pendingSignIn?.complete(false)
        val deferred = CompletableDeferred<Boolean>()
        pendingSignIn = deferred
        _signInRequest.emit(Unit)
        return try {
            deferred.await()
        } finally {
            if (pendingSignIn === deferred) {
                pendingSignIn = null
            }
        }
    }

    init {
        val savedPath = initialSettings.embeddingModelPath
        if (savedPath != null) {
            viewModelScope.launch {
                val file = File(savedPath)
                if (!file.exists()) {
                    withContext(ioDispatcher) { appSettingsRepository.setEmbeddingModelPath(null) }
                    mutableState.update { it.copy(embeddingModelPath = null) }
                    return@launch
                }
                withContext(ioDispatcher) { embeddingEngine.load(file) }
                    .onSuccess {
                        mutableState.update { it.copy(isEmbeddingModelLoaded = true) }
                        reIndexMemories()
                    }
                    .onFailure { error ->
                        mutableState.update {
                            it.copy(errorText = error.message ?: "Failed to restore embedding model")
                        }
                    }
            }
        }
    }

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
                    val totalText = total?.let { "/${formatByteSize(it)}" }.orEmpty()
                    mutableState.update {
                        it.copy(downloadProgressText = "${formatByteSize(downloaded)}$totalText")
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
        if (mutableState.value.isDownloading || mutableState.value.isGenerating) return
        viewModelScope.launch {
            engine.release()
            withContext(ioDispatcher) {
                repository.deleteInstalledModel()
            }
            mutableState.update {
                it.copy(
                    activeModel = null,
                    isDownloading = false,
                    isLoadingModel = false,
                    isGenerating = false,
                    downloadProgressText = null,
                    errorText = null,
                    generationStats = null,
                    runtimeStatus = InferenceRuntimeStatus()
                )
            }
        }
    }

    fun createChatImageFile(context: Context): File {
        val directory = File(context.cacheDir, "chat-images").also { it.mkdirs() }
        return File(directory, "capture-${System.currentTimeMillis()}.jpg")
    }

    fun copyChatImageFromUri(context: Context, uri: Uri): Result<String> =
        runCatching {
            val destination = createChatImageFile(context)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected image." }
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            require(destination.length() > 0L) { "Selected image was empty." }
            destination.absolutePath
        }

    fun startNewChat() {
        if (mutableState.value.isGenerating) return

        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = NEW_CHAT_TITLE,
            messages = emptyList(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        mutableState.value = mutableState.value.copy(
            messages = emptyList(),
            chatSessions = mutableState.value.chatSessions + session,
            activeChatSessionId = session.id,
            generationStats = null,
            errorText = null
        )
        persistChatHistory()
    }

    fun selectChatSession(id: String?) {
        if (id == null || mutableState.value.isGenerating) return
        val session = mutableState.value.chatSessions.firstOrNull { it.id == id } ?: return
        mutableState.value = mutableState.value.copy(
            activeChatSessionId = session.id,
            messages = session.messages,
            generationStats = null,
            errorText = null
        )
        persistChatHistory()
    }

    fun deleteChatSession(id: String?) {
        if (id == null || mutableState.value.isGenerating) return

        val state = mutableState.value
        val remainingSessions = state.chatSessions.filterNot { it.id == id }
        val nextActiveSession = if (state.activeChatSessionId == id) {
            remainingSessions.maxByOrNull { it.updatedAtEpochMillis }
        } else {
            state.activeChatSession
        }

        mutableState.value = state.copy(
            chatSessions = remainingSessions,
            activeChatSessionId = nextActiveSession?.id,
            messages = nextActiveSession?.messages.orEmpty(),
            generationStats = null,
            errorText = null
        )
        persistChatHistory()
    }

    fun sendMessage(prompt: String, imagePath: String? = null) {
        val cleanedPrompt = prompt.trim()
        val cleanedImagePath = imagePath?.trim()?.takeIf { it.isNotBlank() }
        if (cleanedPrompt.isBlank() && cleanedImagePath == null) return

        captureExplicitMemories(cleanedPrompt)
        val model = beginGeneration(cleanedPrompt, cleanedImagePath) ?: return

        generationJob = viewModelScope.launch {
            val streamResponsesEnabled = mutableState.value.streamResponsesEnabled
            loadModelWithFallback(model).fold(
                onSuccess = {
                    val startedAtNanos = nanoTimeProvider()
                    val toolContext = mutableListOf<String>()
                    var toolExecutions = 0

                    while (isActive) {
                        val modelPrompt = promptForModel(
                            cleanedPrompt,
                            hasImage = cleanedImagePath != null,
                            toolContext = toolContext
                        )
                        val streamedText = StringBuilder()
                        val generationResult = if (streamResponsesEnabled && toolContext.isEmpty()) {
                            val onPartialResponse: (String) -> Unit = { partialResponse ->
                                if (isActive) updateStreamingAssistant(streamedText, partialResponse)
                            }
                            if (cleanedImagePath == null) {
                                engine.generateStreaming(modelPrompt, onPartialResponse)
                            } else {
                                engine.generateStreamingWithImage(modelPrompt, cleanedImagePath, onPartialResponse)
                            }
                        } else {
                            if (cleanedImagePath == null) {
                                engine.generate(modelPrompt)
                            } else {
                                engine.generateWithImage(modelPrompt, cleanedImagePath)
                            }
                        }

                        val response = generationResult.getOrElse { error ->
                            if (!isActive) return@launch
                            updateChatState {
                                val updated = it.withoutLoadingAssistant()
                                updated.withActiveChatMessages(updated.messages).copy(
                                    isGenerating = false,
                                    isToolExecuting = false,
                                    errorText = error.message ?: "Generation failed"
                                )
                            }
                            return@launch
                        }

                        if (!isActive) return@launch

                        val rawResponse = if (
                            streamResponsesEnabled &&
                            toolContext.isEmpty() &&
                            streamedText.isNotBlank()
                        ) {
                            streamedText.toString()
                        } else {
                            response
                        }
                        val finalResponse = rawResponse.stripThinkingTokens()

                        val toolCall = ToolCallParser.parse(rawResponse)
                            ?.let { normalizeCalendarToolCall(cleanedPrompt, it) }
                            ?: cleanedPrompt.inferredCalendarToolCall(toolContext)
                        if (toolCall == null || toolExecutions >= MAX_TOOL_HOPS) {
                            val displayResponse =
                                if (toolCall != null && toolExecutions >= MAX_TOOL_HOPS) {
                                    "I wasn't able to complete that. Try rephrasing."
                                } else if (finalResponse.isBlank()) {
                                    BLANK_MODEL_RESPONSE_MESSAGE
                                } else {
                                    finalResponse
                                }
                            val elapsedSeconds =
                                (nanoTimeProvider() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_SECOND
                            val totalTokens = estimateTokenCount(displayResponse)
                            val tokensPerSecond = if (elapsedSeconds > 0.0) {
                                totalTokens / elapsedSeconds
                            } else {
                                0.0
                            }
                            updateChatState {
                                val updated = it.replaceLoadingAssistant(displayResponse)
                                updated.withActiveChatMessages(updated.messages).copy(
                                    isGenerating = false,
                                    isToolExecuting = false,
                                    generationStats = GenerationStats(
                                        elapsedSeconds = elapsedSeconds,
                                        totalTokens = totalTokens,
                                        tokensPerSecond = tokensPerSecond
                                    )
                                )
                            }
                            break
                        }

                        updateChatState {
                            it.updateLoadingAssistant("Using tools...").copy(isToolExecuting = true)
                        }

                        var toolResult = dispatchToolSafely(toolCall)
                        if (toolResult.isError && toolResult.content == NEEDS_SIGN_IN_SENTINEL) {
                            val signedIn = awaitGoogleSignIn()
                            if (!signedIn) {
                                updateChatState {
                                    it.replaceLoadingAssistant(
                                        "I need Google Calendar access to do that. Try again when you're ready."
                                    ).let { state -> state.withActiveChatMessages(state.messages) }
                                        .copy(isGenerating = false, isToolExecuting = false)
                                }
                                break
                            }
                            toolResult = dispatchToolSafely(toolCall)
                        }

                        if (toolResult.isError) {
                            updateChatState {
                                it.replaceLoadingAssistant(toolResult.content)
                                    .let { state -> state.withActiveChatMessages(state.messages) }
                                    .copy(isGenerating = false, isToolExecuting = false)
                            }
                            break
                        }

                        toolContext += "Tool call: ${toolCall.tool}\nTool result: ${toolResult.content}"
                        toolExecutions += 1
                        updateChatState { it.copy(isToolExecuting = false) }
                    }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    updateChatState {
                        val updated = it.withoutLoadingAssistant()
                        updated.withActiveChatMessages(updated.messages).copy(
                            isGenerating = false,
                            isToolExecuting = false,
                            errorText = error.message ?: "Model load failed"
                        )
                    }
                }
            )
        }
    }

    private fun updateStreamingAssistant(streamedText: StringBuilder, partialResponse: String) {
        val displayText = mergeStreamChunk(
            currentText = streamedText.toString(),
            chunk = partialResponse
        )
        streamedText.clear()
        streamedText.append(displayText)
        updateChatState {
            val updated = it.updateLoadingAssistant(displayText)
            updated.withActiveChatMessages(updated.messages)
        }
    }

    private fun captureExplicitMemories(cleanedPrompt: String) {
        if (cleanedPrompt.isBlank()) return
        val captured = memoryRepository.captureExplicitMemories(cleanedPrompt, now = epochTimeProvider())
        if (captured.isNotEmpty()) {
            refreshMemoryState()
        }
    }

    private fun beginGeneration(cleanedPrompt: String, imagePath: String?): ModelMetadata? {
        var modelToUse: ModelMetadata? = null
        updateChatState { state ->
            val model = state.activeModel
            when {
                model == null -> state.copy(errorText = "Install a model before chatting.")
                !state.canChat -> state
                else -> {
                    modelToUse = model
                    val stateWithSession = state.ensureActiveChatSession(
                        cleanedPrompt.ifBlank { "Image" }
                    )
                    val nextMessages = stateWithSession.messages +
                        ChatMessage("user", cleanedPrompt, imagePath = imagePath) +
                        loadingAssistantMessage()

                    stateWithSession.withActiveChatMessages(nextMessages).copy(
                        isGenerating = true,
                        errorText = null,
                        generationStats = null
                    )
                }
            }
        }
        return modelToUse
    }

    fun stopGeneration() {
        val activeJob = generationJob ?: return
        if (!mutableState.value.isGenerating) return

        activeJob.cancel()
        engine.cancelGeneration()
        engine.release()
        generationJob = null
        updateChatState {
            val updated = it.withoutLoadingAssistant()
            updated.withActiveChatMessages(updated.messages).copy(
                isGenerating = false,
                isToolExecuting = false,
                errorText = "Generation stopped."
            )
        }
        pendingSignIn?.complete(false)
        pendingSignIn = null
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

    fun upsertMemory(key: String, value: String) {
        val encodedKey = memoryRepository.encodeKey(key)
        memoryRepository.upsertMemory(key, value)
        refreshMemoryState()
        if (encodedKey != null && mutableState.value.isEmbeddingModelLoaded) {
            viewModelScope.launch(ioDispatcher) {
                embeddingEngine.embed(value).onSuccess { vector ->
                    embeddingStore.storeEmbedding(encodedKey, vector)
                }
            }
        }
    }

    fun deleteMemory(key: String) {
        val encodedKey = memoryRepository.encodeKey(key)
        memoryRepository.deleteMemory(key)
        refreshMemoryState()
        if (encodedKey != null) {
            viewModelScope.launch(ioDispatcher) {
                embeddingStore.deleteEmbedding(encodedKey)
            }
        }
    }

    fun setStreamResponsesEnabled(enabled: Boolean) {
        appSettingsRepository.setStreamResponsesEnabled(enabled)
        refreshSettingsState()
    }

    fun setGpuBackendEnabled(enabled: Boolean) {
        appSettingsRepository.setGpuBackendEnabled(enabled)
        refreshSettingsState()
    }

    fun setNpuBackendEnabled(enabled: Boolean) {
        appSettingsRepository.setNpuBackendEnabled(enabled)
        refreshSettingsState()
    }

    fun setGemmaMtpEnabled(enabled: Boolean) {
        appSettingsRepository.setGemmaMtpEnabled(enabled)
        refreshSettingsState()
    }

    fun setOverlayEnabled(enabled: Boolean) {
        appSettingsRepository.setOverlayEnabled(enabled)
        mutableState.update { it.copy(overlayEnabled = enabled) }
    }

    fun selectEmbeddingModel(file: File) {
        viewModelScope.launch {
            withContext(ioDispatcher) { embeddingEngine.load(file) }
                .onSuccess {
                    withContext(ioDispatcher) {
                        appSettingsRepository.setEmbeddingModelPath(file.absolutePath)
                        embeddingStore.clearAll()
                    }
                    mutableState.update {
                        it.copy(
                            embeddingModelPath = file.absolutePath,
                            isEmbeddingModelLoaded = true,
                            errorText = null
                        )
                    }
                    reIndexMemories()
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(errorText = error.message ?: "Failed to load embedding model")
                    }
                }
        }
    }

    fun removeEmbeddingModel() {
        viewModelScope.launch(ioDispatcher) {
            embeddingEngine.unload()
            embeddingStore.clearAll()
            appSettingsRepository.setEmbeddingModelPath(null)
            mutableState.update {
                it.copy(embeddingModelPath = null, isEmbeddingModelLoaded = false)
            }
        }
    }

    fun importEmbeddingModelFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    // Delete the previously imported model file if it lives in our embedding-models dir
                    val embeddingDir = repository.embeddingModelDirectory()
                    mutableState.value.embeddingModelPath?.let { oldPath ->
                        val oldFile = File(oldPath)
                        if (oldFile.parentFile?.canonicalPath == embeddingDir.canonicalPath) {
                            oldFile.delete()
                        }
                    }
                    val destination = File(embeddingDir, "embedding-${System.currentTimeMillis()}.gguf")
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Could not open selected file." }
                            destination.outputStream().use { output -> input.copyTo(output) }
                        }
                        require(destination.length() > 0L) { "Selected file was empty." }
                    } catch (e: Exception) {
                        destination.delete()
                        throw e
                    }
                    destination
                }
            }.onSuccess { file ->
                selectEmbeddingModel(file)
            }.onFailure { error ->
                mutableState.update { it.copy(errorText = error.message ?: "Import failed") }
            }
        }
    }

    private fun reIndexMemories() {
        viewModelScope.launch(ioDispatcher) {
            mutableState.update { it.copy(isReIndexing = true) }
            val storedKeys = embeddingStore.loadAll().keys
            memoryRepository.loadMemories()
                .filter { memoryRepository.encodeKey(it.key) !in storedKeys }
                .forEach { memory ->
                    val encodedKey = memoryRepository.encodeKey(memory.key) ?: return@forEach
                    embeddingEngine.embed(memory.value).onSuccess { vector ->
                        embeddingStore.storeEmbedding(encodedKey, vector)
                    }
                }
            mutableState.update { it.copy(isReIndexing = false) }
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        if (mutableState.value.isGenerating) {
            engine.cancelGeneration()
        }
        generationJob = null
        embeddingEngine.unload()
        engine.release()
        super.onCleared()
    }

    private fun fileNameFromUrl(normalizedUrl: String): String {
        val rawName = URI(normalizedUrl).rawPath.substringAfterLast("/")
        return URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
    }

    private fun estimateTokenCount(text: String): Int =
        (text.length / 4).coerceAtLeast(1)

    private fun loadingAssistantMessage(): ChatMessage =
        ChatMessage("assistant", "Processing...", isLoading = true)

    private suspend fun promptForModel(
        userPrompt: String,
        hasImage: Boolean = false,
        toolContext: List<String> = emptyList()
    ): String {
        val formatter = promptFormatterRepository.loadState().activeFormatter
        val formatterBody = formatter?.body.orEmpty().trim()
        val prompt = if (userPrompt.isBlank() && hasImage) "Describe this image." else userPrompt
        val toolBlock = toolRegistry.promptBlock().takeIf { it.isNotEmpty() }?.let { block ->
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy, h:mm a"))
            "Current date/time: $now\n\n$block"
        }

        val selectedMemories: List<MemoryItem> = withContext(ioDispatcher) {
            if (mutableState.value.isEmbeddingModelLoaded) {
                embeddingEngine.embed(prompt).getOrNull()?.let { queryVector ->
                    val topKeys = embeddingStore.findTopK(queryVector, MemoryRepository.PROMPT_MEMORY_LIMIT)
                    val pinnedMemories = memoryRepository.loadMemories()
                        .filter { it.key in MemoryRepository.PINNED_KEYS }
                        .sortedBy { MemoryRepository.PINNED_KEYS.indexOf(it.key) }
                    val semanticMemories = memoryRepository.memoriesByEncodedKeys(topKeys)
                        .filterNot { it.key in MemoryRepository.PINNED_KEYS }
                    (pinnedMemories + semanticMemories).take(MemoryRepository.PROMPT_MEMORY_LIMIT)
                } ?: memoryRepository.selectForPrompt(prompt)
            } else {
                memoryRepository.selectForPrompt(prompt)
            }
        }

        val memoryBlock = selectedMemories
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n", prefix = "Memory:\n") { "- ${it.key}: ${it.value}" }

        val toolContextBlock = toolContext.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n\n")

        return listOf(formatterBody, toolBlock, memoryBlock, "User message:\n$prompt", toolContextBlock)
            .filterNot { it.isNullOrBlank() }
            .joinToString("\n\n")
    }

    private suspend fun dispatchToolSafely(toolCall: ToolCall): ToolResult =
        runCatching { toolRegistry.dispatch(toolCall) }
            .getOrElse { error ->
                ToolResult(
                    tool = toolCall.tool,
                    content = "Tool '${toolCall.tool}' failed: ${error.message ?: error::class.java.simpleName}",
                    isError = true
                )
            }

    private fun normalizeCalendarToolCall(userPrompt: String, toolCall: ToolCall): ToolCall {
        if (!userPrompt.requestsCalendarMonth()) return toolCall

        return when (toolCall.tool) {
            "list_events" -> {
                val date = (toolCall.args["date"] as? String)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return toolCall
                val start = date.withDayOfMonth(1)
                ToolCall(
                    tool = "list_events_range",
                    args = mapOf("start_date" to start.toString(), "end_date" to start.plusMonths(1).toString())
                )
            }
            "list_events_range" -> {
                val startDate = (toolCall.args["start_date"] as? String)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val endDate = (toolCall.args["end_date"] as? String)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (startDate != null && endDate != null) return toolCall
                val today = Instant.ofEpochMilli(epochTimeProvider())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val start = today.withDayOfMonth(1)
                ToolCall(
                    tool = "list_events_range",
                    args = mapOf("start_date" to start.toString(), "end_date" to start.plusMonths(1).toString())
                )
            }
            else -> toolCall
        }
    }

    private fun String.inferredCalendarToolCall(toolContext: List<String>): ToolCall? {
        if (toolContext.isNotEmpty() || !requestsCalendarMonth()) {
            return null
        }
        val currentDate = Instant.ofEpochMilli(epochTimeProvider())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val start = currentDate.withDayOfMonth(1)
        val end = start.plusMonths(1)
        return ToolCall(
            tool = "list_events_range",
            args = mapOf(
                "start_date" to start.toString(),
                "end_date" to end.toString()
            )
        )
    }

    private fun String.requestsCalendarMonth(): Boolean {
        val prompt = lowercase()
        return "month" in prompt && (
            "calendar" in prompt || "sched" in prompt || "schedule" in prompt ||
            "event" in prompt || "this" in prompt || "next" in prompt || "last" in prompt
        )
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
                streamResponsesEnabled = mutableState.value.streamResponsesEnabled
            )
        }
    }

    private fun refreshMemoryState() {
        mutableState.update {
            it.copy(memories = memoryRepository.loadMemories())
        }
    }

    private fun refreshSettingsState() {
        val settings = appSettingsRepository.load()
        val requestedRuntime = runtimeConfigForSettings(settings)
        mutableState.update {
            val previousRuntimeStatus = it.runtimeStatus
            it.copy(
                streamResponsesEnabled = settings.streamResponsesEnabled,
                gpuBackendEnabled = settings.gpuBackendEnabled,
                npuBackendEnabled = settings.npuBackendEnabled,
                gemmaMtpEnabled = settings.gemmaMtpEnabled,
                runtimeStatus = previousRuntimeStatus.copy(
                    requested = requestedRuntime,
                    fallbackReason = if (previousRuntimeStatus.requested == requestedRuntime) {
                        previousRuntimeStatus.fallbackReason
                    } else {
                        null
                    }
                )
            )
        }
    }

    private fun requestedRuntimeConfig(): InferenceRuntimeConfig {
        return runtimeConfigForSettings(appSettingsRepository.load())
    }

    private fun runtimeConfigForSettings(settings: AppSettings): InferenceRuntimeConfig {
        return when {
            settings.npuBackendEnabled -> InferenceRuntimeConfig(
                backend = InferenceBackend.NPU,
                speculativeDecodingEnabled = false
            )
            settings.gpuBackendEnabled -> InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = settings.gemmaMtpEnabled
            )
            else -> InferenceRuntimeConfig.defaultCpu
        }
    }

    private suspend fun loadModelWithFallback(model: ModelMetadata): Result<InferenceRuntimeConfig> {
        val requested = requestedRuntimeConfig()
        val modelFile = File(model.absolutePath)
        val firstLoad = engine.load(modelFile, requested)
        if (firstLoad.isSuccess) {
            mutableState.update {
                it.copy(runtimeStatus = InferenceRuntimeStatus(requested = requested, active = requested))
            }
            return Result.success(requested)
        }

        val firstError = firstLoad.exceptionOrNull()
        if (requested.backend == InferenceBackend.CPU) {
            return Result.failure(firstError ?: IllegalStateException("Model load failed"))
        }

        engine.release()
        val fallback = InferenceRuntimeConfig.defaultCpu
        val fallbackLoad = engine.load(modelFile, fallback)
        if (fallbackLoad.isSuccess) {
            val reason = gpuFallbackReason(
                requested = requested,
                detail = firstError?.message ?: "Model load failed"
            )
            mutableState.update {
                it.copy(
                    runtimeStatus = InferenceRuntimeStatus(
                        requested = requested,
                        active = fallback,
                        fallbackReason = reason
                    )
                )
            }
            return Result.success(fallback)
        }

        val fallbackError = fallbackLoad.exceptionOrNull()
        return Result.failure(
            IllegalStateException(
                "${requested.label} failed: ${firstError?.message ?: "Model load failed"}. " +
                    "CPU fallback failed: ${fallbackError?.message ?: "Model load failed"}"
            )
        )
    }

    private fun gpuFallbackReason(requested: InferenceRuntimeConfig, detail: String): String =
        "${requested.label} is not supported for this model/device, so the app fell back to CPU. Details: $detail"

    private fun updateChatState(transform: (AppState) -> AppState) {
        mutableState.value = transform(mutableState.value)
        persistChatHistory()
    }

    private fun persistChatHistory() {
        val state = mutableState.value
        chatHistoryRepository.saveState(
            ChatHistoryState(
                sessions = state.chatSessions,
                activeSessionId = state.activeChatSessionId
            )
        )
    }

    private fun AppState.ensureActiveChatSession(firstPrompt: String): AppState {
        val now = System.currentTimeMillis()
        val activeSession = activeChatSession
        if (activeSession != null) {
            val shouldTitleFromPrompt = activeSession.messages.isEmpty() && activeSession.title == NEW_CHAT_TITLE
            val updatedSession = activeSession.copy(
                title = if (shouldTitleFromPrompt) titleFromPrompt(firstPrompt) else activeSession.title,
                updatedAtEpochMillis = now
            )
            return copy(
                chatSessions = chatSessions.map { session ->
                    if (session.id == activeSession.id) updatedSession else session
                },
                activeChatSessionId = updatedSession.id
            )
        }

        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = titleFromPrompt(firstPrompt),
            messages = emptyList(),
            updatedAtEpochMillis = now
        )
        return copy(
            chatSessions = chatSessions + session,
            activeChatSessionId = session.id,
            messages = emptyList()
        )
    }

    private fun AppState.withActiveChatMessages(nextMessages: List<ChatMessage>): AppState {
        val activeId = activeChatSessionId ?: return copy(messages = nextMessages)
        val now = System.currentTimeMillis()
        return copy(
            messages = nextMessages,
            chatSessions = chatSessions.map { session ->
                if (session.id == activeId) {
                    session.copy(
                        messages = nextMessages,
                        updatedAtEpochMillis = now
                    )
                } else {
                    session
                }
            }
        )
    }

    private fun titleFromPrompt(prompt: String): String {
        val compact = prompt.trim().replace(Regex("\\s+"), " ")
        if (compact.isBlank()) return NEW_CHAT_TITLE
        return if (compact.length <= MAX_CHAT_TITLE_LENGTH) {
            compact
        } else {
            compact.take(MAX_CHAT_TITLE_LENGTH - 3).trimEnd() + "..."
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NEW_CHAT_TITLE = "New chat"
        const val MAX_CHAT_TITLE_LENGTH = 48
        const val MAX_TOOL_HOPS = 3
        const val BLANK_MODEL_RESPONSE_MESSAGE = "I didn't receive a usable response from the model. Please try again."
    }
}

private fun String.stripThinkingTokens(): String =
    replace(Regex("<think>[\\s\\S]*?</think>\\s*"), "").trim()

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
