package com.lance.llamacppchat.inference

import com.arm.aichat.InferenceEngine
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlamaCppChatEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadRejectsNonGgufModelFiles() = runTest {
        val modelFile = temporaryFolder.newFile("model.litertlm")
        val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)

        val result = engine.load(modelFile)

        assertTrue(result.isFailure)
        assertEquals("Model file must end with .gguf.", result.exceptionOrNull()?.message)
    }

    @Test
    fun loadCallsLlamaEngineForGgufModel() = runTest {
        val modelFile = temporaryModelFile()
        val fakeEngine = FakeInferenceEngine()
        val engine = LlamaCppChatEngine(fakeEngine, Unit)

        val result = engine.load(modelFile)

        assertTrue(result.isSuccess)
        assertEquals(listOf(modelFile.absolutePath), fakeEngine.loadedModels)
    }

    @Test
    fun loadSkipsWhenSameModelIsAlreadyLoaded() = runTest {
        val modelFile = temporaryModelFile()
        val fakeEngine = FakeInferenceEngine()
        val engine = LlamaCppChatEngine(fakeEngine, Unit)

        assertTrue(engine.load(modelFile).isSuccess)
        assertTrue(engine.load(modelFile).isSuccess)

        assertEquals(listOf(modelFile.absolutePath), fakeEngine.loadedModels)
    }

    @Test
    fun generateStreamingEmitsChunksAndReturnsFullResponse() = runTest {
        val modelFile = temporaryModelFile()
        val fakeEngine = FakeInferenceEngine(chunks = listOf("Hel", "lo"))
        val engine = LlamaCppChatEngine(fakeEngine, Unit)
        val streamedChunks = mutableListOf<String>()

        assertTrue(engine.load(modelFile).isSuccess)
        val result = engine.generateStreaming("Say hello") { streamedChunks += it }

        assertEquals("Hello", result.getOrThrow())
        assertEquals(listOf("Hel", "lo"), streamedChunks)
        assertEquals(listOf("Say hello" to 1024), fakeEngine.prompts)
    }

    @Test
    fun imagePromptsFailClearly() = runTest {
        val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)

        val result = engine.generateWithImage("Describe", "/tmp/photo.jpg")

        assertTrue(result.isFailure)
        assertEquals(
            "Image prompts are not supported by the llama.cpp GGUF runtime.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun isLoadedReturnsFalseBeforeLoad() = runTest {
        val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun isLoadedReturnsTrueAfterSuccessfulLoad() = runTest {
        val modelFile = temporaryModelFile()
        val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
        engine.load(modelFile)
        assertTrue(engine.isLoaded)
    }

    @Test
    fun isLoadedReturnsFalseAfterRelease() = runTest {
        val modelFile = temporaryModelFile()
        val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
        engine.load(modelFile)
        engine.release()
        assertFalse(engine.isLoaded)
    }

    private fun temporaryModelFile(): File =
        temporaryFolder.newFile("model-${System.nanoTime()}.gguf").also {
            it.writeText("model")
        }
}

private class FakeInferenceEngine(
    private val chunks: List<String> = listOf("Done")
) : InferenceEngine {
    private val mutableState = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)

    override val state: StateFlow<InferenceEngine.State> = mutableState
    val loadedModels = mutableListOf<String>()
    val prompts = mutableListOf<Pair<String, Int>>()
    var cleanUpCalls = 0

    override suspend fun loadModel(pathToModel: String) {
        loadedModels += pathToModel
        mutableState.value = InferenceEngine.State.ModelReady
    }

    override suspend fun setSystemPrompt(systemPrompt: String) = Unit

    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> =
        flow {
            prompts += message to predictLength
            chunks.forEach { emit(it) }
        }

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = ""

    override fun cleanUp() {
        cleanUpCalls += 1
        mutableState.value = InferenceEngine.State.Initialized
    }

    override fun destroy() = Unit
}
