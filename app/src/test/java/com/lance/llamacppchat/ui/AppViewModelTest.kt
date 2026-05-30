package com.lance.llamacppchat.ui

import com.lance.llamacppchat.download.ModelDownloadClient
import com.lance.llamacppchat.inference.ChatEngine
import com.lance.llamacppchat.inference.InferenceBackend
import com.lance.llamacppchat.inference.InferenceRuntimeConfig
import com.lance.llamacppchat.inference.InferenceRuntimeStatus
import com.lance.llamacppchat.memory.MemoryItem
import com.lance.llamacppchat.memory.MemoryRepository
import com.lance.llamacppchat.model.ModelMetadata
import com.lance.llamacppchat.model.ModelRepository
import com.lance.llamacppchat.prompt.PromptFormatterRepository
import com.lance.llamacppchat.settings.AppSettingsRepository
import com.lance.llamacppchat.tools.Tool
import com.lance.llamacppchat.tools.ToolDefinition
import com.lance.llamacppchat.tools.ToolRegistry
import com.lance.llamacppchat.tools.ToolResult
import com.lance.llamacppchat.ui.chat.ChatHistoryRepository
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
    fun viewModelStartsWithRequestedRuntimeFromPersistedSettings() {
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setGpuBackendEnabled(true)
        settingsRepository.setGemmaMtpEnabled(true)

        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            appSettingsRepository = settingsRepository
        )

        assertEquals(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            ),
            viewModel.state.value.runtimeStatus.requested
        )
        assertEquals(InferenceRuntimeConfig.defaultCpu, viewModel.state.value.runtimeStatus.active)
        assertNull(viewModel.state.value.runtimeStatus.fallbackReason)
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
        assertNull(viewModel.state.value.activeModel)
        assertFalse(viewModel.state.value.isDownloading)
        assertFalse(viewModel.state.value.isLoadingModel)
        assertFalse(viewModel.state.value.isGenerating)
        assertNull(viewModel.state.value.downloadProgressText)
        assertNull(viewModel.state.value.errorText)
        assertNull(viewModel.state.value.generationStats)
        assertEquals(InferenceRuntimeStatus(), viewModel.state.value.runtimeStatus)
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
    fun deleteModelIsIgnoredWhileGeneratingAndGenerationCompletes() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val metadata = installedModel(path = modelFile.absolutePath)
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(metadata)
        val response = CompletableDeferred<String>()
        val engine = FakeChatEngine(responseDeferred = response)
        val viewModel = testViewModel(repository = repository, engine = engine)

        viewModel.sendMessage("Hello")
        runCurrent()
        viewModel.deleteModel()
        runCurrent()

        assertEquals(metadata, repository.loadMetadata())
        assertTrue(modelFile.exists())
        assertEquals(metadata, viewModel.state.value.activeModel)
        assertTrue(viewModel.state.value.isGenerating)
        assertEquals(0, engine.releaseCount)

        response.complete("Done")
        advanceUntilIdle()

        assertEquals(metadata, repository.loadMetadata())
        assertTrue(modelFile.exists())
        assertEquals(metadata, viewModel.state.value.activeModel)
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals(0, engine.releaseCount)
        assertEquals(
            listOf(ChatMessage("user", "Hello"), ChatMessage("assistant", "Done")),
            viewModel.state.value.messages
        )
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

        viewModel.sendMessage(" Explain how xChat state works on mobile ")
        advanceUntilIdle()

        assertEquals("Explain how xChat state works on mobile", viewModel.state.value.activeChatSession?.title)
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

        assertEquals(listOf(modelFile.absolutePath to InferenceRuntimeConfig.defaultCpu), engine.loadRequests)
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
    fun sendMessageWithImagePassesImagePathToEngineAndStoresPreview() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val imageFile = File(temporaryFolder.root, "photo.jpg")
        imageFile.writeText("image")
        val metadata = installedModel(path = modelFile.absolutePath)
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(metadata)
        val engine = FakeChatEngine(response = "That is a photo")
        val viewModel = testViewModel(repository, engine = engine)

        viewModel.sendMessage("Describe this", imageFile.absolutePath)
        advanceUntilIdle()

        assertEquals(listOf(imageFile.absolutePath), engine.streamingImagePaths)
        assertEquals(
            listOf(
                ChatMessage("user", "Describe this", imagePath = imageFile.absolutePath),
                ChatMessage("assistant", "That is a photo")
            ),
            viewModel.state.value.messages
        )
    }

    @Test
    fun duplicateQuickSendOnlyStartsOneGeneration() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(repository, engine = engine)

        viewModel.sendMessage("First")
        viewModel.sendMessage("Second")
        advanceUntilIdle()

        assertEquals(1, engine.loadRequests.size)
        assertEquals(1, engine.streamingPrompts.size)
        assertEquals(
            listOf(ChatMessage("user", "First"), ChatMessage("assistant", "Done")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun sendMessageRequestsGpuWhenSettingIsEnabled() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setGpuBackendEnabled(true)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(
                modelFile.absolutePath to InferenceRuntimeConfig(
                    backend = InferenceBackend.GPU,
                    speculativeDecodingEnabled = false
                )
            ),
            engine.loadRequests
        )
    }

    @Test
    fun sendMessageRequestsGpuMtpWhenBothSettingsAreEnabled() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setGpuBackendEnabled(true)
        settingsRepository.setGemmaMtpEnabled(true)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(
                modelFile.absolutePath to InferenceRuntimeConfig(
                    backend = InferenceBackend.GPU,
                    speculativeDecodingEnabled = true
                )
            ),
            engine.loadRequests
        )
    }

    @Test
    fun sendMessageRequestsNpuWhenSettingIsEnabled() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setNpuBackendEnabled(true)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(
                modelFile.absolutePath to InferenceRuntimeConfig(
                    backend = InferenceBackend.NPU,
                    speculativeDecodingEnabled = false
                )
            ),
            engine.loadRequests
        )
    }

    @Test
    fun npuLoadFailureFallsBackToCpu() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setNpuBackendEnabled(true)
        val npuConfig = InferenceRuntimeConfig(
            backend = InferenceBackend.NPU,
            speculativeDecodingEnabled = false
        )
        val engine = FakeChatEngine(
            response = "CPU answer",
            loadFailuresByConfig = mapOf(
                npuConfig to IllegalStateException("NPU delegate unavailable")
            )
        )
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(
                modelFile.absolutePath to npuConfig,
                modelFile.absolutePath to InferenceRuntimeConfig.defaultCpu
            ),
            engine.loadRequests
        )
        assertEquals(
            "NPU is not supported for this model/device, so the app fell back to CPU. Details: NPU delegate unavailable",
            viewModel.state.value.runtimeStatus.fallbackReason
        )
    }

    @Test
    fun gpuMtpLoadFailureFallsBackToCpu() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setGpuBackendEnabled(true)
        settingsRepository.setGemmaMtpEnabled(true)
        val gpuMtpConfig = InferenceRuntimeConfig(
            backend = InferenceBackend.GPU,
            speculativeDecodingEnabled = true
        )
        val engine = FakeChatEngine(
            response = "CPU answer",
            loadFailuresByConfig = mapOf(
                gpuMtpConfig to IllegalStateException("GPU unavailable")
            )
        )
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertEquals(
            listOf(
                modelFile.absolutePath to gpuMtpConfig,
                modelFile.absolutePath to InferenceRuntimeConfig.defaultCpu
            ),
            engine.loadRequests
        )
        assertEquals(1, engine.releaseCount)
        assertEquals(
            "GPU + MTP is not supported for this model/device, so the app fell back to CPU. Details: GPU unavailable",
            viewModel.state.value.runtimeStatus.fallbackReason
        )
        viewModel.setGemmaMtpEnabled(false)

        assertEquals(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = false
            ),
            viewModel.state.value.runtimeStatus.requested
        )
        assertNull(viewModel.state.value.runtimeStatus.fallbackReason)
        assertEquals(
            listOf(ChatMessage("user", "Hi"), ChatMessage("assistant", "CPU answer")),
            viewModel.state.value.messages
        )
    }

    @Test
    fun settingsToggleUpdatesDiagnosticsRequestedRuntimeImmediately() {
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            appSettingsRepository = settingsRepository
        )
        val gpuMtpConfig = InferenceRuntimeConfig(
            backend = InferenceBackend.GPU,
            speculativeDecodingEnabled = true
        )

        viewModel.setGpuBackendEnabled(true)
        viewModel.setGemmaMtpEnabled(true)

        assertEquals(gpuMtpConfig, viewModel.state.value.runtimeStatus.requested)
        assertEquals(InferenceRuntimeConfig.defaultCpu, viewModel.state.value.runtimeStatus.active)

        viewModel.setNpuBackendEnabled(true)

        assertEquals(
            InferenceRuntimeConfig(
                backend = InferenceBackend.NPU,
                speculativeDecodingEnabled = false
            ),
            viewModel.state.value.runtimeStatus.requested
        )
        assertFalse(viewModel.state.value.gpuBackendEnabled)
        assertFalse(viewModel.state.value.gemmaMtpEnabled)
        assertTrue(viewModel.state.value.npuBackendEnabled)
    }

    @Test
    fun deleteModelReleasesEngineAndResetsRuntimeStatus() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setGpuBackendEnabled(true)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()
        viewModel.deleteModel()
        advanceUntilIdle()

        assertEquals(1, engine.releaseCount)
        assertEquals(InferenceRuntimeStatus(), viewModel.state.value.runtimeStatus)
        assertNull(viewModel.state.value.activeModel)
    }

    @Test
    fun deleteModelPreservesPromptFormattersAndSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val formatterRepository = PromptFormatterRepository(temporaryFolder.root)
        val formatter = formatterRepository.createFormatter("Brief", "Keep it short.")
        formatterRepository.selectFormatter(formatter.id)
        val settingsRepository = AppSettingsRepository(temporaryFolder.root)
        settingsRepository.setStreamResponsesEnabled(false)
        settingsRepository.setGpuBackendEnabled(true)
        settingsRepository.setGemmaMtpEnabled(true)
        val viewModel = testViewModel(
            repository = repository,
            formatterRepository = formatterRepository,
            appSettingsRepository = settingsRepository
        )
        val expectedFormatters = viewModel.state.value.promptFormatters

        viewModel.deleteModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeModel)
        assertEquals(expectedFormatters, viewModel.state.value.promptFormatters)
        assertEquals(formatter.id, viewModel.state.value.activePromptFormatterId)
        assertFalse(viewModel.state.value.streamResponsesEnabled)
        assertTrue(viewModel.state.value.gpuBackendEnabled)
        assertTrue(viewModel.state.value.gemmaMtpEnabled)
        assertEquals(InferenceRuntimeStatus(), viewModel.state.value.runtimeStatus)
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
    fun viewModelStartsWithPersistedMemories() {
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        memoryRepository.upsertMemory("user.name", "Lance", now = 1000L)

        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            memoryRepository = memoryRepository
        )

        assertEquals(listOf(MemoryItem("user.name", "Lance", 1000L)), viewModel.state.value.memories)
    }

    @Test
    fun memoryActionsUpdateStateAndRepository() {
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = ModelRepository(temporaryFolder.root),
            memoryRepository = memoryRepository
        )

        viewModel.upsertMemory(" User Name ", " Lance ")
        viewModel.deleteMemory("missing")

        assertEquals("user.name", viewModel.state.value.memories.single().key)
        assertEquals("Lance", memoryRepository.loadMemories().single().value)

        viewModel.deleteMemory("User Name")

        assertTrue(viewModel.state.value.memories.isEmpty())
        assertTrue(memoryRepository.loadMemories().isEmpty())
    }

    @Test
    fun sendMessageInjectsRelevantMemoriesIntoModelPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        memoryRepository.upsertMemory("user.name", "Lance", now = 1000L)
        memoryRepository.upsertMemory("project.current", "local LlamaCpp Android chat app", now = 2000L)
        val engine = FakeChatEngine(response = "Done")
        val viewModel = testViewModel(
            repository = repository,
            memoryRepository = memoryRepository,
            engine = engine
        )

        viewModel.sendMessage("How should this Android project store memory?")
        advanceUntilIdle()

        assertEquals(
            listOf(
                "${PromptFormatterRepository.DEFAULT_FORMATTER_BODY}\n\n" +
                    "Memory:\n" +
                    "- user.name: Lance\n" +
                    "- project.current: local LlamaCpp Android chat app\n\n" +
                    "User message:\n" +
                    "How should this Android project store memory?"
            ),
            engine.streamingPrompts
        )
    }

    @Test
    fun sendMessageStoresExplicitFavoriteMemoryFromUserPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            memoryRepository = memoryRepository,
            engine = FakeChatEngine(response = "Saved"),
            epochTimeProvider = { 1000L }
        )

        viewModel.sendMessage("my favorite color is blue")
        advanceUntilIdle()

        assertEquals(
            listOf(MemoryItem("user.favorite.color", "blue", 1000L)),
            memoryRepository.loadMemories()
        )
        assertEquals(
            listOf(MemoryItem("user.favorite.color", "blue", 1000L)),
            viewModel.state.value.memories
        )
    }

    @Test
    fun sendMessageStoresExplicitNameMemoryFromUserPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            memoryRepository = memoryRepository,
            engine = FakeChatEngine(response = "Saved"),
            epochTimeProvider = { 1000L }
        )

        viewModel.sendMessage("remember my name robinx")
        advanceUntilIdle()

        assertEquals(
            listOf(MemoryItem("user.name", "robinx", 1000L)),
            memoryRepository.loadMemories()
        )
        assertEquals(
            listOf(MemoryItem("user.name", "robinx", 1000L)),
            viewModel.state.value.memories
        )
    }

    @Test
    fun sendMessageStoresExplicitFoodMemoryFromUserPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val memoryRepository = MemoryRepository(temporaryFolder.root)
        val viewModel = testViewModel(
            repository = repository,
            memoryRepository = memoryRepository,
            engine = FakeChatEngine(response = "Saved"),
            epochTimeProvider = { 1000L }
        )

        viewModel.sendMessage("I like to eat donuts and hotdogs")
        advanceUntilIdle()

        assertEquals(
            listOf(MemoryItem("user.likes.to.eat", "donuts and hotdogs", 1000L)),
            memoryRepository.loadMemories()
        )
        assertEquals(
            listOf(MemoryItem("user.likes.to.eat", "donuts and hotdogs", 1000L)),
            viewModel.state.value.memories
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
    fun sendMessageExecutesToolCallAndGeneratesFinalResponse() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val fakeTool = FakeToolForViewModel(
            "list_events",
            ToolResult("list_events", "Events for 2026-05-22:\n- Standup: 10:00 to 10:30")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "```json\n{\"tool\":\"list_events\",\"args\":{\"date\":\"2026-05-22\"}}\n```",
                "You have a Standup at 10 AM."
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool))
        )

        viewModel.sendMessage("What's on my calendar today?")
        advanceUntilIdle()

        assertEquals("You have a Standup at 10 AM.", viewModel.state.value.messages.last().content)
        assertFalse(viewModel.state.value.isGenerating)
        assertFalse(viewModel.state.value.isToolExecuting)
        assertEquals(1, fakeTool.executeCount)
    }

    @Test
    fun sendMessageUsesRangeToolWhenMonthPromptProducesSingleDayCalendarToolCall() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val singleDayTool = FakeToolForViewModel(
            "list_events",
            ToolResult("list_events", "Events for one day")
        )
        val rangeTool = FakeToolForViewModel(
            "list_events_range",
            ToolResult("list_events_range", "Events from 2026-05-01 to 2026-06-01:\n- Workout")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "{\"tool\":\"list_events\",\"args\":{date:\"2026-05-01\"}}",
                "You have Workout this month."
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(singleDayTool, rangeTool))
        )

        viewModel.sendMessage("give me my sched for this month")
        advanceUntilIdle()

        assertEquals(0, singleDayTool.executeCount)
        assertEquals(1, rangeTool.executeCount)
        assertEquals(
            mapOf("start_date" to "2026-05-01", "end_date" to "2026-06-01"),
            rangeTool.lastArgs
        )
        assertEquals("You have Workout this month.", viewModel.state.value.messages.last().content)
    }

    @Test
    fun sendMessageFixesMalformedListEventsRangeCallMissingStartDate() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val rangeTool = FakeToolForViewModel(
            "list_events_range",
            ToolResult("list_events_range", "Events from 1970-01-01 to 1970-02-01:\n- Workout")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "{\"tool\":\"list_events_range\",\"args\":{}}",
                "You have Workout this month."
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(rangeTool)),
            epochTimeProvider = { 1_252_800_000L }
        )

        viewModel.sendMessage("give me my sched for this month")
        advanceUntilIdle()

        assertEquals(1, rangeTool.executeCount)
        assertEquals(
            mapOf("start_date" to "1970-01-01", "end_date" to "1970-02-01"),
            rangeTool.lastArgs
        )
    }

    @Test
    fun sendMessageUsesRangeToolWhenMonthSchedulePromptGetsDirectModelAnswer() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val rangeTool = FakeToolForViewModel(
            "list_events_range",
            ToolResult("list_events_range", "Events from 1970-01-01 to 1970-02-01:\n- Workout")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "It seems there are no events scheduled for this month.",
                "You have Workout this month."
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(rangeTool)),
            epochTimeProvider = { 1_252_800_000L }
        )

        viewModel.sendMessage("give me sched for thid month")
        advanceUntilIdle()

        assertEquals(1, rangeTool.executeCount)
        assertEquals(
            mapOf("start_date" to "1970-01-01", "end_date" to "1970-02-01"),
            rangeTool.lastArgs
        )
        assertEquals("You have Workout this month.", viewModel.state.value.messages.last().content)
    }

    @Test
    fun sendMessageUsesRangeToolWhenMonthPhraseContainsThisButNoCalendarKeyword() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val rangeTool = FakeToolForViewModel(
            "list_events_range",
            ToolResult("list_events_range", "Events from 1970-01-01 to 1970-02-01:\n- Workout")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "",
                "You have Workout this month."
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(rangeTool)),
            epochTimeProvider = { 1_252_800_000L }
        )

        viewModel.sendMessage("this month of may from may 1st to last day of may")
        advanceUntilIdle()

        assertEquals(1, rangeTool.executeCount)
        assertEquals(
            mapOf("start_date" to "1970-01-01", "end_date" to "1970-02-01"),
            rangeTool.lastArgs
        )
    }

    @Test
    fun sendMessageReportsToolExecutionCrashAsAssistantError() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val throwingTool = ThrowingToolForViewModel(
            name = "create_calendar_event",
            error = IllegalStateException("Calendar API unavailable")
        )
        val engine = FakeChatEngine(
            responses = listOf(
                "```json\n{\"tool\":\"create_calendar_event\",\"args\":{\"title\":\"workout\",\"start\":\"2026-05-23T21:00:00\"}}\n```"
            )
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(throwingTool))
        )

        viewModel.sendMessage("create me a schedule tomorrow 9pm, workout as the title")
        advanceUntilIdle()

        assertEquals(
            "Tool 'create_calendar_event' failed: Calendar API unavailable",
            viewModel.state.value.messages.last().content
        )
        assertFalse(viewModel.state.value.isGenerating)
        assertFalse(viewModel.state.value.isToolExecuting)
        assertEquals(1, throwingTool.executeCount)
    }

    @Test
    fun sendMessageWithNoToolCallSkipsToolLoop() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val fakeTool = FakeToolForViewModel("list_events", ToolResult("list_events", "irrelevant"))
        val engine = FakeChatEngine(responses = listOf("Sure, here is the answer."))
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool))
        )

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("Sure, here is the answer.", viewModel.state.value.messages.last().content)
        assertEquals(0, fakeTool.executeCount)
    }

    @Test
    fun sendMessageReplacesBlankModelResponseWithFallbackMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = FakeChatEngine(response = "")
        )

        viewModel.sendMessage("try it again")
        advanceUntilIdle()

        assertEquals(
            "I didn't receive a usable response from the model. Please try again.",
            viewModel.state.value.messages.last().content
        )
        assertFalse(viewModel.state.value.isGenerating)
    }

    @Test
    fun sendMessageStopsAfterMaxToolHopsWithErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also {
            it.saveMetadata(installedModel(modelFile.absolutePath))
        }
        val fakeTool = FakeToolForViewModel("list_events", ToolResult("list_events", "some result"))
        val engine = FakeChatEngine(
            responses = List(10) {
                "```json\n{\"tool\":\"list_events\",\"args\":{\"date\":\"2026-05-22\"}}\n```"
            }
        )
        val settingsRepository = AppSettingsRepository(temporaryFolder.root).also {
            it.setStreamResponsesEnabled(false)
        }
        val viewModel = testViewModel(
            repository = repository,
            appSettingsRepository = settingsRepository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool))
        )

        viewModel.sendMessage("loop forever")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.last().content.contains("Try rephrasing"))
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals(3, fakeTool.executeCount)
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
    fun onClearedReleasesIdleEngine() {
        val repository = ModelRepository(temporaryFolder.root)
        val engine = FakeChatEngine()
        val viewModel = testViewModel(repository = repository, engine = engine)

        clearViewModel(viewModel)

        assertEquals(1, engine.releaseCount)
        assertEquals(0, engine.cancelCount)
    }

    @Test
    fun onClearedCancelsActiveGenerationAndReleasesEngine() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf")
        modelFile.writeText("model")
        val repository = ModelRepository(temporaryFolder.root)
        repository.saveMetadata(installedModel(path = modelFile.absolutePath))
        val response = CompletableDeferred<String>()
        val engine = FakeChatEngine(responseDeferred = response)
        val viewModel = testViewModel(repository = repository, engine = engine)

        viewModel.sendMessage("Hello")
        runCurrent()
        clearViewModel(viewModel)
        runCurrent()

        assertEquals(1, engine.cancelCount)
        assertEquals(1, engine.releaseCount)

        response.complete("Late response")
        advanceUntilIdle()
        assertEquals(
            listOf(
                ChatMessage("user", "Hello"),
                ChatMessage("assistant", "Processing...", isLoading = true)
            ),
            viewModel.state.value.messages
        )
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
        path: String = "/models/gemma-4-E2B-it.gguf"
    ): ModelMetadata =
        ModelMetadata(
            fileName = "gemma-4-E2B-it.gguf",
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
        memoryRepository: MemoryRepository = MemoryRepository(temporaryFolder.root),
        engine: ChatEngine = FakeChatEngine(),
        toolRegistry: ToolRegistry = ToolRegistry(),
        nanoTimeProvider: () -> Long = { 0L },
        epochTimeProvider: () -> Long = { 0L }
    ): AppViewModel =
        AppViewModel(
            repository = repository,
            promptFormatterRepository = formatterRepository,
            appSettingsRepository = appSettingsRepository,
            chatHistoryRepository = chatHistoryRepository,
            memoryRepository = memoryRepository,
            downloader = downloader,
            engine = engine,
            toolRegistry = toolRegistry,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            nanoTimeProvider = nanoTimeProvider,
            epochTimeProvider = epochTimeProvider
        )

    private fun clearViewModel(viewModel: AppViewModel) {
        AppViewModel::class.java.getDeclaredMethod("onCleared")
            .also { it.isAccessible = true }
            .invoke(viewModel)
    }
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

private class FakeChatEngine(
    private val response: String = "response",
    private val responses: List<String> = emptyList(),
    private val responseDeferred: CompletableDeferred<String>? = null,
    private val streamingResponses: List<String> = emptyList(),
    private val loadFailure: Throwable? = null,
    private val loadFailuresByConfig: Map<InferenceRuntimeConfig, Throwable> = emptyMap(),
    private val generateFailure: Throwable? = null
) : ChatEngine {
    val loadRequests = mutableListOf<Pair<String, InferenceRuntimeConfig>>()
    val prompts = mutableListOf<String>()
    val streamingPrompts = mutableListOf<String>()
    val imagePaths = mutableListOf<String>()
    val streamingImagePaths = mutableListOf<String>()
    var releaseCount = 0
    var cancelCount = 0
    private var responseIndex = 0

    override suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig
    ): Result<Unit> {
        loadRequests += modelFile.absolutePath to runtimeConfig
        loadFailuresByConfig[runtimeConfig]?.let { return Result.failure(it) }
        return loadFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun generate(prompt: String): Result<String> {
        prompts += prompt
        generateFailure?.let { return Result.failure(it) }
        responseDeferred?.let { deferred ->
            return try {
                Result.success(deferred.await())
            } catch (error: CancellationException) {
                throw error
            }
        }
        val nextResponse = if (responses.isNotEmpty()) {
            responses[responseIndex++ % responses.size]
        } else {
            response
        }
        return Result.success(nextResponse)
    }

    override suspend fun generateWithImage(prompt: String, imagePath: String): Result<String> {
        prompts += prompt
        imagePaths += imagePath
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

    override suspend fun generateStreamingWithImage(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> {
        streamingPrompts += prompt
        streamingImagePaths += imagePath
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

private class FakeToolForViewModel(
    name: String,
    private val result: ToolResult
) : Tool {
    override val definition = ToolDefinition(name = name, description = "fake", parametersSchema = "none")
    var executeCount = 0
    var lastArgs: Map<String, Any>? = null

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        executeCount += 1
        lastArgs = args
        return result
    }
}

private class ThrowingToolForViewModel(
    name: String,
    private val error: Throwable
) : Tool {
    override val definition = ToolDefinition(name = name, description = "fake", parametersSchema = "none")
    var executeCount = 0

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        executeCount += 1
        throw error
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
