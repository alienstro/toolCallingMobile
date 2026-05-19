package com.lance.llamacppchat.keyboard

import com.arm.aichat.InferenceEngine
import com.lance.llamacppchat.inference.LlamaCppChatEngine
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InferenceServiceLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun modelFile(): File = tmp.newFile("m.gguf").also { it.writeText("x") }

    @Test
    fun generateStreamsTokensWhenModelAlreadyLoaded() = runTest {
        val fake = FakeServiceEngine(chunks = listOf("Hel", "lo"))
        val engine = LlamaCppChatEngine(fake, Unit)
        engine.load(modelFile())
        val tokens = mutableListOf<String>()

        engine.generateStreaming("hi") { tokens += it }

        assertEquals(listOf("Hel", "lo"), tokens)
    }

    @Test
    fun generateFailsWithMessageWhenModelNotLoaded() = runTest {
        val engine = LlamaCppChatEngine(FakeServiceEngine(), Unit)

        val result = engine.generateStreaming("hi") { }

        assertTrue(result.isFailure)
        assertEquals("llama.cpp model is not loaded.", result.exceptionOrNull()?.message)
    }
}

private class FakeServiceEngine(
    private val chunks: List<String> = listOf("ok")
) : InferenceEngine {
    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
    override val state: StateFlow<InferenceEngine.State> = _state
    override suspend fun loadModel(pathToModel: String) {
        _state.value = InferenceEngine.State.ModelReady
    }
    override suspend fun setSystemPrompt(systemPrompt: String) = Unit
    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> =
        flow { chunks.forEach { emit(it) } }
    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = ""
    override fun cleanUp() { _state.value = InferenceEngine.State.Initialized }
    override fun destroy() = Unit
}
