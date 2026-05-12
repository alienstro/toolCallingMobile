package com.lance.litertchat.ui

import com.lance.litertchat.download.ModelDownloadClient
import com.lance.litertchat.inference.ChatEngine
import com.lance.litertchat.model.ModelMetadata
import com.lance.litertchat.model.ModelRepository
import com.lance.litertchat.prompt.PromptFormatterRepository
import com.lance.litertchat.settings.AppSettingsRepository
import com.lance.litertchat.ui.chat.ChatHistoryRepository
import com.lance.litertchat.ui.chat.ChatHistoryState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun chatIsDisabledWithoutModel() {
        assertFalse(AppState(activeModel = null).canChat)
    }

    @Test
    fun chatIsEnabledWithInstalledModelWhenIdle() {
        val state = AppState(activeModel = installedModel())

        assertTrue(state.canChat)
    }

    @Test
    fun chatIsDisabledWhileDownloading() {
        val state = AppState(
            activeModel = installedModel(),
            isDownloading = true
        )

        assertFalse(state.canChat)
    }

    @Test
    fun chatIsDisabledWhileLoadingModel() {
        val state = AppState(
            activeModel = installedModel(),
            isLoadingModel = true
        )

        assertFalse(state.canChat)
    }

    @Test
    fun chatIsDisabledWhileGenerating() {
        val state = AppState(
            activeModel = installedModel(),
            isGenerating = true
        )

        assertFalse(state.canChat)
    }

    @Test
    fun viewModelStartsWithRepositoryMetadata() {
        val repository = ModelRepository(temporaryFolder.root)
        val metadata = installedModel()
        repository.saveMetadata(metadata)

        val viewModel = AppViewModel(repository)

        assertEquals(metadata, viewModel.state.value.activeModel)
        assertEquals(PromptFormatterRepository.DEFAULT_FORMATTER_ID, viewModel.state.value.activePromptFormatterId)
        assertEquals(1, viewModel.state.value.promptFormatters.size)
    }

    @Test
    fun downloadModelDownloadsFileAndSavesMetadata() = runTest(mainDispatcherRule.testDispatcher) {
        val content = "model bytes".toByteArray()
        val downloader = FakeDownloader { _, destination, onProgress ->
            destination.parentFile?.mkdirs()
            destination.writeBytes(content)
            onProgress(content.size.toLong(), content.size.toLong())
        }
        val repository = ModelRepository(temporaryFolder.root)
        val viewModel = testViewModel(repository, downloader)
        val url = "https://huggingface.co/repo/blob/main/model.gguf?download=true"

        viewModel.downloadModel(url)
        advanceUntilIdle()

        val activeModel = viewModel.state.value.activeModel
        val modelFile = File(repository.modelDirectory(), "model.gguf")
        assertEquals("model.gguf", activeModel?.fileName)
        assertEquals(modelFile.absolutePath, activeModel?.absolutePath)
        assertEquals("download", activeModel?.source)
        assertEquals("https://huggingface.co/repo/resolve/main/model.gguf?download=true", activeModel?.sourceUrl)
        assertEquals(content.size.toLong(), activeModel?.sizeBytes)
        assertEquals(content.decodeToString(), modelFile.readText())
        assertEquals(activeModel, repository.loadMetadata())
        assertFalse(viewModel.state.value.isDownloading)
        assertEquals("Download complete", viewModel.state.value.downloadProgressText)
        assertNull(viewModel.state.value.errorText)
        assertEquals(listOf(activeModel?.sourceUrl), downloader.requestedUrls)
    }

    @Test
    fun deleteModelDeletesRepositoryModelAndClearsState() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        val modelFile = File(repository.modelDirectory(), "model.gguf")
        modelFile.writeText("model")
        repository.saveMetadata(
            ModelMetadata(
                fileName = modelFile.name,
                absolutePath = modelFile.absolutePath,
                source = "download",
                sourceUrl = "https://example.com/model.gguf",
                sizeBytes = modelFile.length(),
                installedAtEpochMillis = 1000L
            )
        )
        val viewModel = testViewModel(repository)

        viewModel.deleteModel()
        advanceUntilIdle()

        assertNull(repository.loadMetadata())
        assertFalse(modelFile.exists())
        assertEquals(AppState(), viewModel.state.value)
    }

    @Test
    fun duplicateDownloadCallIsIgnoredWhileDownloading() = runTest(mainDispatcherRule.testDispatcher) {
        val downloader = FakeDownloader { _, destination, _ ->
            destination.parentFile?.mkdirs()
            destination.writeText("model bytes")
        }
        val repository = ModelRepository(temporaryFolder.root)
        val viewModel = testViewModel(repository, downloader)
        val url = "https://example.com/model.gguf"

        viewModel.downloadModel(url)
        viewModel.downloadModel(url)
        advanceUntilIdle()

        assertEquals(listOf(url), downloader.requestedUrls)
        assertFalse(viewModel.state.value.isDownloading)
        assertEquals("model.gguf", viewModel.state.value.activeModel?.fileName)
    }

    @Test
    fun deleteModelIsIgnoredWhileDownloading() = runTest(mainDispatcherRule.testDispatcher) {
        val releaseDownload = CompletableDeferred<Unit>()
        val downloader = FakeDownloader { _, destination, _ ->
            releaseDownload.await()
            destination.parentFile?.mkdirs()
            destination.writeText("new model")
        }
        val repository = ModelRepository(temporaryFolder.root)
        val existingFile = File(repository.modelDirectory(), "existing.gguf")
        existingFile.writeText("existing model")
        val existingMetadata = ModelMetadata(
            fileName = existingFile.name,
            absolutePath = existingFile.absolutePath,
            source = "download",
            sourceUrl = "https://example.com/existing.gguf",
            sizeBytes = existingFile.length(),
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(existingMetadata)
        val viewModel = testViewModel(repository, downloader)

        viewModel.downloadModel("https://example.com/new-model.gguf")
        viewModel.deleteModel()

        assertEquals(existingMetadata, repository.loadMetadata())
        assertTrue(existingFile.exists())

        releaseDownload.complete(Unit)
        advanceUntilIdle()
        assertEquals("new-model.gguf", viewModel.state.value.activeModel?.fileName)
    }

    @Test
    fun downloadFailureClearsProgressAndReportsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            downloader = FakeDownloader { _, _, _ -> error("network unavailable") }
        )

        viewModel.downloadModel("https://example.com/model.gguf")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDownloading)
        assertNull(viewModel.state.value.downloadProgressText)
        assertEquals("network unavailable", viewModel.state.value.errorText)
        assertNull(viewModel.state.value.activeModel)
    }

    @Test
    fun downloadModelUsesDecodedFileNameFromUrl() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            downloader = FakeDownloader { _, destination, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("model")
            }
        )

        viewModel.downloadModel("https://example.com/my%20model.gguf?x=y")
        advanceUntilIdle()

        assertEquals("my model.gguf", viewModel.state.value.activeModel?.fileName)
        assertTrue(File(repository.modelDirectory(), "my model.gguf").exists())
    }

    @Test
    fun sendAddsUserAndAssistantMessages() {
        val state = AppState()
            .withUserMessage("Hello")
            .withAssistantMessage("Hi")

        assertEquals(2, state.messages.size)
        assertEquals("user", state.messages[0].role)
        assertEquals("assistant", state.messages[1].role)
    }

    @Test
    fun sendMessageTitlesNewChatFromFirstUserPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val viewModel = testViewModel(repository, engine = FakeChatEngine(response = "Hi"))

        viewModel.sendMessage(" Explain RunAnywhere chat state on mobile ")
        advanceUntilIdle()

        assertEquals("Explain RunAnywhere chat state on mobile", viewModel.state.value.activeChatSession?.title)
        assertEquals(
            viewModel.state.value.messages,
            viewModel.state.value.activeChatSession?.messages
        )
    }

    @Test
    fun newChatCreatesBlankActiveSessionAndKeepsOldChatInHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val viewModel = testViewModel(repository, engine = FakeChatEngine(response = "Done"))

        viewModel.sendMessage("First question")
        advanceUntilIdle()
        val oldSession = viewModel.state.value.activeChatSession
        viewModel.startNewChat()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertEquals("New chat", viewModel.state.value.activeChatSession?.title)
        assertTrue(viewModel.state.value.chatSessions.any { it.id == oldSession?.id && it.title == "First question" })
        assertTrue(viewModel.state.value.activeChatSessionId != oldSession?.id)
    }

    @Test
    fun selectChatSessionRestoresOldMessages() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val viewModel = testViewModel(repository, engine = FakeChatEngine(response = "First answer"))

        viewModel.sendMessage("First question")
        advanceUntilIdle()
        val firstSessionId = viewModel.state.value.activeChatSessionId
        viewModel.startNewChat()
        viewModel.sendMessage("Second question")
        advanceUntilIdle()

        viewModel.selectChatSession(firstSessionId)

        assertEquals("First question", viewModel.state.value.activeChatSession?.title)
        assertEquals(
            listOf(ChatMessage("user", "First question"), ChatMessage("assistant", "First answer")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun deleteChatSessionRemovesItAndSelectsAnotherSession() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val viewModel = testViewModel(repository, engine = FakeChatEngine(response = "Done"))

        viewModel.sendMessage("First question")
        advanceUntilIdle()
        val firstSessionId = viewModel.state.value.activeChatSessionId
        viewModel.startNewChat()
        viewModel.sendMessage("Second question")
        advanceUntilIdle()

        viewModel.deleteChatSession(firstSessionId)

        assertTrue(viewModel.state.value.chatSessions.none { it.id == firstSessionId })
        assertEquals("Second question", viewModel.state.value.activeChatSession?.title)
        assertEquals(listOf(ChatMessage("user", "Second question"), ChatMessage("assistant", "Done")), viewModel.state.value.messages)
    }

    @Test
    fun chatHistoryPersistsAcrossViewModelInstances() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val chatHistoryRepository = ChatHistoryRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            chatHistoryRepository = chatHistoryRepository,
            engine = FakeChatEngine(response = "Saved answer")
        )

        viewModel.sendMessage("Persist this chat")
        advanceUntilIdle()
        val restored = testViewModel(
            repository = repository,
            chatHistoryRepository = chatHistoryRepository,
            engine = FakeChatEngine(response = "Other")
        )

        assertEquals("Persist this chat", restored.state.value.activeChatSession?.title)
        assertEquals(
            listOf(ChatMessage("user", "Persist this chat"), ChatMessage("assistant", "Saved answer")),
            restored.state.value.messages
        )
    }

    @Test
    fun sendMessageRequiresInstalledModel() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = testViewModel(ModelRepository(temporaryFolder.root))

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("Install a model before chatting.", viewModel.state.value.errorText)
        assertTrue(viewModel.state.value.messages.isEmpty())
    }

    @Test
    fun sendMessageIgnoresBlankPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel())
        val viewModel = testViewModel(repository)

        viewModel.sendMessage("   ")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertNull(viewModel.state.value.errorText)
    }

    @Test
    fun sendMessageLoadsModelAndAddsAssistantResponse() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val metadata = installedModel(path = modelFile.absolutePath)
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(metadata)
        val engine = FakeChatEngine(response = "Hi there")
        val viewModel = testViewModel(repository, engine = engine)

        viewModel.sendMessage(" Hello ")
        advanceUntilIdle()

        assertEquals(listOf(modelFile.absolutePath), engine.loadedPaths)
        assertEquals(
            listOf("${PromptFormatterRepository.DEFAULT_FORMATTER_BODY}\n\nUser message:\nHello"),
            engine.streamingPrompts
        )
        assertFalse(viewModel.state.value.isGenerating)
        assertNull(viewModel.state.value.errorText)
        assertEquals(
            listOf(ChatMessage("user", "Hello"), ChatMessage("assistant", "Hi there")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun sendMessagePrependsActiveFormatterOnlyForModelPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val metadata = installedModel(path = modelFile.absolutePath)
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(metadata)
        val formatterRepository = PromptFormatterRepository(temporaryFolder.root)
        val formatter = formatterRepository.createFormatter("Brief", "Answer in two bullets.")
        formatterRepository.selectFormatter(formatter.id)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            formatterRepository = formatterRepository,
            engine = engine
        )

        viewModel.sendMessage("What is the sun?")
        advanceUntilIdle()

        assertEquals(
            listOf("Answer in two bullets.\n\nUser message:\nWhat is the sun?"),
            engine.streamingPrompts
        )
        assertEquals(
            listOf(ChatMessage("user", "What is the sun?"), ChatMessage("assistant", "Done")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun promptFormatterActionsUpdateState() = runTest(mainDispatcherRule.testDispatcher) {
        val formatterRepository = PromptFormatterRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            formatterRepository = formatterRepository
        )

        viewModel.createPromptFormatter("Brief", "Use bullets.")
        advanceUntilIdle()
        val created = viewModel.state.value.promptFormatters.first { it.name == "Brief" }

        viewModel.selectPromptFormatter(created.id)
        advanceUntilIdle()
        viewModel.updatePromptFormatter(created.id, "Brief Mobile", "Use two bullets.")
        advanceUntilIdle()

        assertEquals(created.id, viewModel.state.value.activePromptFormatterId)
        assertEquals(
            "Use two bullets.",
            viewModel.state.value.promptFormatters.first { it.id == created.id }.body
        )

        viewModel.deletePromptFormatter(created.id)
        advanceUntilIdle()
        assertEquals(PromptFormatterRepository.DEFAULT_FORMATTER_ID, viewModel.state.value.activePromptFormatterId)
        assertTrue(viewModel.state.value.promptFormatters.none { it.id == created.id })
    }

    @Test
    fun resetDefaultPromptFormatterRestoresDefaultBody() = runTest(mainDispatcherRule.testDispatcher) {
        val formatterRepository = PromptFormatterRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            formatterRepository = formatterRepository
        )

        viewModel.updatePromptFormatter(PromptFormatterRepository.DEFAULT_FORMATTER_ID, "Default", "Changed")
        advanceUntilIdle()
        viewModel.resetDefaultPromptFormatter()
        advanceUntilIdle()

        assertEquals(
            PromptFormatterRepository.DEFAULT_FORMATTER_BODY,
            viewModel.state.value.promptFormatters.first {
                it.id == PromptFormatterRepository.DEFAULT_FORMATTER_ID
            }.body
        )
    }

    @Test
    fun sendMessageShowsLoadingAssistantWhileGenerating() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val response = CompletableDeferred<String>()
        val viewModel = testViewModel(
            repository = repository,
            engine = FakeChatEngine(responseDeferred = response)
        )

        viewModel.sendMessage("Hello")
        runCurrent()

        assertTrue(viewModel.state.value.isGenerating)
        assertEquals(
            listOf(
                ChatMessage("user", "Hello"),
                ChatMessage("assistant", "Processing...", isLoading = true)
            ),
            viewModel.state.value.messages
        )

        response.complete("Done")
        advanceUntilIdle()
        assertEquals(
            listOf(
                ChatMessage("user", "Hello"),
                ChatMessage("assistant", "Done")
            ),
            viewModel.state.value.messages
        )
    }

    @Test
    fun sendMessageStreamsPartialAssistantResponseWhenEnabled() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val engine = FakeChatEngine(streamingResponses = listOf("Hel", "Hello"))
        val viewModel = testViewModel(repository = repository, engine = engine)

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertTrue(engine.streamingPrompts.isNotEmpty())
        assertEquals(
            listOf(ChatMessage("user", "Hi"), ChatMessage("assistant", "Hello")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun streamingPartialResponsesDoNotPersistChatHistoryUntilFinalResponse() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val chatHistoryRepository = CountingChatHistoryRepository(temporaryFolder.root)
        val engine = FakeChatEngine(streamingResponses = listOf("H", "He", "Hel", "Hell", "Hello"))
        val viewModel = testViewModel(
            repository = repository,
            chatHistoryRepository = chatHistoryRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(2, chatHistoryRepository.saveCount)
        assertEquals(
            listOf(ChatMessage("user", "Hi"), ChatMessage("assistant", "Hello")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun sendMessageAccumulatesStreamingDeltas() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val engine = FakeChatEngine(streamingResponses = listOf("with", " more", " text"))
        val viewModel = testViewModel(repository = repository, engine = engine)

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(ChatMessage("user", "Hi"), ChatMessage("assistant", "with more text")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun sendMessageUsesBlockingGenerationWhenStreamingDisabled() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setStreamResponsesEnabled(false)
        val engine = FakeChatEngine(response = "Done", streamingResponses = listOf("Streamed"))
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertTrue(engine.streamingPrompts.isEmpty())
        assertEquals(listOf("Done"), viewModel.state.value.messages.filter { it.role == "assistant" }.map { it.content })
    }

    @Test
    fun setStreamResponsesEnabledPersistsAndUpdatesState() {
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            appSettingsRepository = settingsRepository
        )

        viewModel.setStreamResponsesEnabled(false)

        assertFalse(viewModel.state.value.streamResponsesEnabled)
        assertFalse(settingsRepository.load().streamResponsesEnabled)
    }

    @Test
    fun stopGenerationCancelsActiveResponseAndRemovesLoadingAssistant() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val response = CompletableDeferred<String>()
        val engine = FakeChatEngine(responseDeferred = response)
        val viewModel = testViewModel(repository = repository, engine = engine)

        viewModel.sendMessage("Hello")
        runCurrent()
        viewModel.stopGeneration()
        runCurrent()

        assertFalse(viewModel.state.value.isGenerating)
        assertEquals("Generation stopped.", viewModel.state.value.errorText)
        assertEquals(1, engine.releaseCount)
        assertEquals(1, engine.cancelCount)
        assertEquals(listOf(ChatMessage("user", "Hello")), viewModel.state.value.messages)

        response.complete("Late response")
        advanceUntilIdle()
        assertEquals(listOf(ChatMessage("user", "Hello")), viewModel.state.value.messages)
    }

    @Test
    fun sendMessageStoresGenerationStatsForAssistantResponse() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val metadata = installedModel(path = modelFile.absolutePath)
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(metadata)
        val engine = FakeChatEngine(response = "One two three four")
        val times = ArrayDeque(listOf(1_000_000_000L, 3_000_000_000L))
        val viewModel = testViewModel(
            repository = repository,
            engine = engine,
            nanoTimeProvider = { times.removeFirst() }
        )

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals(
            GenerationStats(
                elapsedSeconds = 2.0,
                totalTokens = 4,
                tokensPerSecond = 2.0
            ),
            viewModel.state.value.generationStats
        )
    }

    @Test
    fun sendMessageReportsLoadFailure() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel())
        val viewModel = testViewModel(
            repository = repository,
            engine = FakeChatEngine(loadFailure = IllegalStateException("Model file does not exist."))
        )

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isGenerating)
        assertEquals("Model file does not exist.", viewModel.state.value.errorText)
        assertEquals(listOf(ChatMessage("user", "Hello")), viewModel.state.value.messages)
    }

    @Test
    fun sendMessageReportsGenerationFailure() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel())
        val viewModel = testViewModel(
            repository = repository,
            engine = FakeChatEngine(generateFailure = IllegalStateException("Generation failed"))
        )

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isGenerating)
        assertEquals("Generation failed", viewModel.state.value.errorText)
        assertEquals(listOf(ChatMessage("user", "Hello")), viewModel.state.value.messages)
    }

    private fun installedModel(
        path: String = "/models/smollm2-360m-instruct-q8_0.gguf"
    ): ModelMetadata =
        ModelMetadata(
            fileName = "smollm2-360m-instruct-q8_0.gguf",
            absolutePath = path,
            source = "local",
            sourceUrl = null,
            sizeBytes = 1234L,
            installedAtEpochMillis = 1000L
        )

    private fun testViewModel(
        repository: ModelRepository,
        downloader: ModelDownloadClient = FakeDownloader { _, destination, _ ->
            destination.parentFile?.mkdirs()
            destination.writeText("model")
        },
        formatterRepository: PromptFormatterRepository = PromptFormatterRepository(temporaryFolder.root),
        appSettingsRepository: AppSettingsRepository = AppSettingsRepository(temporaryFolder.root),
        chatHistoryRepository: ChatHistoryRepository = ChatHistoryRepository(temporaryFolder.root),
        engine: ChatEngine = FakeChatEngine(),
        nanoTimeProvider: () -> Long = { 0L }
    ): AppViewModel =
        AppViewModel(
            repository = repository,
            promptFormatterRepository = formatterRepository,
            appSettingsRepository = appSettingsRepository,
            chatHistoryRepository = chatHistoryRepository,
            downloader = downloader,
            engine = engine,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            nanoTimeProvider = nanoTimeProvider
        )
}

private class FakeDownloader(
    private val onDownload: suspend (
        rawUrl: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ) -> Unit
) : ModelDownloadClient {
    val requestedUrls = mutableListOf<String>()

    override suspend fun download(
        rawUrl: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): File {
        requestedUrls += rawUrl
        onDownload(rawUrl, destination, onProgress)
        return destination
    }
}

private class CountingChatHistoryRepository(rootDir: File) : ChatHistoryRepository(rootDir) {
    var saveCount = 0

    override fun saveState(state: ChatHistoryState) {
        saveCount += 1
        super.saveState(state)
    }
}

private class FakeChatEngine(
    private val response: String = "response",
    private val responseDeferred: CompletableDeferred<String>? = null,
    private val streamingResponses: List<String> = emptyList(),
    private val loadFailure: Throwable? = null,
    private val generateFailure: Throwable? = null
) : ChatEngine {
    val loadedPaths = mutableListOf<String>()
    val prompts = mutableListOf<String>()
    val streamingPrompts = mutableListOf<String>()
    var releaseCount = 0
    var cancelCount = 0

    override suspend fun load(modelFile: File): Result<Unit> {
        loadedPaths += modelFile.absolutePath
        return loadFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun generate(prompt: String): Result<String> {
        prompts += prompt
        responseDeferred?.let { deferred ->
            return try {
                Result.success(deferred.await())
            } catch (error: CancellationException) {
                throw error
            }
        }
        return generateFailure?.let { Result.failure(it) } ?: Result.success(response)
    }

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> {
        streamingPrompts += prompt
        responseDeferred?.let { deferred ->
            return try {
                val value = deferred.await()
                onPartialResponse(value)
                Result.success(value)
            } catch (error: CancellationException) {
                throw error
            }
        }
        generateFailure?.let { return Result.failure(it) }
        streamingResponses.forEach(onPartialResponse)
        return Result.success(streamingResponses.lastOrNull() ?: response)
    }

    override fun release() {
        releaseCount += 1
    }

    override fun cancelGeneration() {
        cancelCount += 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}
