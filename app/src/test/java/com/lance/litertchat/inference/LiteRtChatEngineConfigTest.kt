package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalApi::class, ExperimentalCoroutinesApi::class)
class LiteRtChatEngineConfigTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cpuConfigMapsToCpuBackendWithoutSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.CPU,
                speculativeDecodingEnabled = false
            )
        )

        assertTrue(mapped.backend is Backend.CPU)
        assertFalse(mapped.speculativeDecodingEnabled)
    }

    @Test
    fun gpuConfigMapsToGpuBackendWithoutSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = false
            )
        )

        assertTrue(mapped.backend is Backend.GPU)
        assertFalse(mapped.speculativeDecodingEnabled)
    }

    @Test
    fun mtpConfigMapsToGpuBackendWithSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            )
        )

        assertTrue(mapped.backend is Backend.GPU)
        assertTrue(mapped.speculativeDecodingEnabled)
    }

    @Test
    fun loadResetsSpeculativeFlagWhenEngineConstructionFails() = runTest {
        val modelFile = temporaryModelFile()
        val engine = LiteRtChatEngine(
            ThrowingLiteRtEngineFactory(IllegalStateException("constructor failed"))
        )
        ExperimentalFlags.enableSpeculativeDecoding = false

        val result = engine.load(
            modelFile,
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            )
        )

        assertTrue(result.isFailure)
        assertFalse(ExperimentalFlags.enableSpeculativeDecoding == true)
    }

    @Test
    fun generateDoesNotHoldEngineLockWhileSendingBlockingMessage() = runTest {
        val conversation = BlockingLiteRtConversationHandle()
        val engine = LiteRtChatEngine(FakeLiteRtEngineFactory(conversation))
        assertTrue(engine.load(temporaryModelFile()).isSuccess)

        val generation = async {
            engine.generate("Hello")
        }
        runCurrent()
        assertTrue(conversation.sendStarted.await(1, TimeUnit.SECONDS))

        val releaseFinished = CountDownLatch(1)
        val releaseThread = thread(start = true) {
            engine.release()
            releaseFinished.countDown()
        }

        try {
            assertTrue(
                "release should not wait for Conversation.sendMessage() to finish",
                releaseFinished.await(500, TimeUnit.MILLISECONDS)
            )
        } finally {
            conversation.allowSend.countDown()
            releaseThread.join(1000)
        }
        assertTrue(generation.await().isSuccess)
    }

    private fun temporaryModelFile(): File =
        temporaryFolder.newFile("model-${System.nanoTime()}.litertlm").also {
            it.writeText("model")
        }
}

private class ThrowingLiteRtEngineFactory(
    private val error: Throwable
) : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend): LiteRtEngineHandle {
        throw error
    }
}

private class FakeLiteRtEngineFactory(
    private val conversation: LiteRtConversationHandle
) : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend): LiteRtEngineHandle =
        FakeLiteRtEngineHandle(conversation)
}

private class FakeLiteRtEngineHandle(
    private val conversation: LiteRtConversationHandle
) : LiteRtEngineHandle {
    override fun initialize() = Unit

    override fun createConversation(): LiteRtConversationHandle = conversation

    override fun close() = Unit
}

private class BlockingLiteRtConversationHandle : LiteRtConversationHandle {
    val sendStarted = CountDownLatch(1)
    val allowSend = CountDownLatch(1)

    override fun sendMessage(prompt: String): Any {
        sendStarted.countDown()
        allowSend.await(5, TimeUnit.SECONDS)
        return "Done"
    }

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        flowOf(sendMessage(prompt))

    override fun cancelProcess() = Unit

    override fun close() = Unit
}
