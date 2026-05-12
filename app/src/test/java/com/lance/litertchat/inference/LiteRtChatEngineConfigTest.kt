package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
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
    fun npuConfigMapsToNpuBackendWithoutSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.NPU,
                speculativeDecodingEnabled = false
            ),
            npuNativeLibraryDir = "/native/lib"
        )

        val backend = mapped.backend
        assertTrue(backend is Backend.NPU)
        assertTrue((backend as Backend.NPU).nativeLibraryDir == "/native/lib")
        assertFalse(mapped.speculativeDecodingEnabled)
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
    fun nonSpeculativeEngineDoesNotClearSpeculativeFlagOwnedByAnotherEngine() = runTest {
        val speculativeEngine = LiteRtChatEngine(FakeLiteRtEngineFactory(EmptyLiteRtConversationHandle()))
        val cpuEngine = LiteRtChatEngine(FakeLiteRtEngineFactory(EmptyLiteRtConversationHandle()))
        val gpuMtpConfig = InferenceRuntimeConfig(
            backend = InferenceBackend.GPU,
            speculativeDecodingEnabled = true
        )
        ExperimentalFlags.enableSpeculativeDecoding = false

        try {
            assertTrue(speculativeEngine.load(temporaryModelFile(), gpuMtpConfig).isSuccess)
            assertTrue(ExperimentalFlags.enableSpeculativeDecoding == true)

            assertTrue(cpuEngine.load(temporaryModelFile(), InferenceRuntimeConfig.defaultCpu).isSuccess)
            assertTrue(
                "CPU load must not clear another active engine's speculative flag",
                ExperimentalFlags.enableSpeculativeDecoding == true
            )

            cpuEngine.release()
            assertTrue(
                "CPU release must not clear another active engine's speculative flag",
                ExperimentalFlags.enableSpeculativeDecoding == true
            )
        } finally {
            cpuEngine.release()
            speculativeEngine.release()
        }

        assertFalse(ExperimentalFlags.enableSpeculativeDecoding == true)
    }

    @Test
    fun failedSpeculativeLoadDoesNotClearSpeculativeFlagOwnedByAnotherEngine() = runTest {
        val activeEngine = LiteRtChatEngine(FakeLiteRtEngineFactory(EmptyLiteRtConversationHandle()))
        val failingEngine = LiteRtChatEngine(
            ThrowingLiteRtEngineFactory(IllegalStateException("constructor failed"))
        )
        val gpuMtpConfig = InferenceRuntimeConfig(
            backend = InferenceBackend.GPU,
            speculativeDecodingEnabled = true
        )
        ExperimentalFlags.enableSpeculativeDecoding = false

        try {
            assertTrue(activeEngine.load(temporaryModelFile(), gpuMtpConfig).isSuccess)
            assertTrue(ExperimentalFlags.enableSpeculativeDecoding == true)

            val result = failingEngine.load(temporaryModelFile(), gpuMtpConfig)

            assertTrue(result.isFailure)
            assertTrue(
                "Failed load must not clear another active engine's speculative flag",
                ExperimentalFlags.enableSpeculativeDecoding == true
            )
        } finally {
            failingEngine.release()
            activeEngine.release()
        }

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

    @Test
    fun generateWithImageSendsImageBytesInsteadOfFilePath() = runTest {
        val conversation = CapturingLiteRtConversationHandle()
        val engine = LiteRtChatEngine(FakeLiteRtEngineFactory(conversation))
        val imageFile = temporaryFolder.newFile("photo.jpg").also {
            it.writeBytes(byteArrayOf(1, 2, 3))
        }
        assertTrue(engine.load(temporaryModelFile()).isSuccess)

        val result = engine.generateWithImage("Describe this", imageFile.absolutePath)

        assertTrue(result.isSuccess)
        val imageContent = conversation.lastContents
            ?.contents
            ?.filterIsInstance<Content.ImageBytes>()
            ?.singleOrNull()
        assertTrue("image should be passed as bytes", imageContent != null)
        assertTrue(imageContent!!.bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun loadConfiguresVisionBackendForMultimodalModels() = runTest {
        val factory = CapturingLiteRtEngineFactory(EmptyLiteRtConversationHandle())
        val engine = LiteRtChatEngine(factory)

        val result = engine.load(temporaryModelFile(), InferenceRuntimeConfig.defaultCpu)

        assertTrue(result.isSuccess)
        assertTrue(factory.visionBackends.single() is Backend.CPU)
    }

    private fun temporaryModelFile(): File =
        temporaryFolder.newFile("model-${System.nanoTime()}.litertlm").also {
            it.writeText("model")
        }
}

private class ThrowingLiteRtEngineFactory(
    private val error: Throwable
) : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend, visionBackend: Backend?): LiteRtEngineHandle {
        throw error
    }
}

private class FakeLiteRtEngineFactory(
    private val conversation: LiteRtConversationHandle
) : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend, visionBackend: Backend?): LiteRtEngineHandle =
        FakeLiteRtEngineHandle(conversation)
}

private class CapturingLiteRtEngineFactory(
    private val conversation: LiteRtConversationHandle
) : LiteRtEngineHandleFactory {
    val visionBackends = mutableListOf<Backend?>()

    override fun create(modelPath: String, backend: Backend, visionBackend: Backend?): LiteRtEngineHandle {
        visionBackends += visionBackend
        return FakeLiteRtEngineHandle(conversation)
    }
}

private class FakeLiteRtEngineHandle(
    private val conversation: LiteRtConversationHandle
) : LiteRtEngineHandle {
    override fun initialize() = Unit

    override fun createConversation(): LiteRtConversationHandle = conversation

    override fun close() = Unit
}

private class EmptyLiteRtConversationHandle : LiteRtConversationHandle {
    override fun sendMessage(prompt: String): Any = "Done"

    override fun sendMessage(contents: Contents): Any = "Done"

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        flowOf(sendMessage(prompt))

    override fun sendMessageAsync(contents: Contents): Flow<Any?> =
        flowOf(sendMessage(contents))

    override fun cancelProcess() = Unit

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

    override fun sendMessage(contents: Contents): Any = sendMessage("image")

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        flowOf(sendMessage(prompt))

    override fun sendMessageAsync(contents: Contents): Flow<Any?> =
        flowOf(sendMessage(contents))

    override fun cancelProcess() = Unit

    override fun close() = Unit
}

private class CapturingLiteRtConversationHandle : LiteRtConversationHandle {
    var lastContents: Contents? = null

    override fun sendMessage(prompt: String): Any = "Done"

    override fun sendMessage(contents: Contents): Any {
        lastContents = contents
        return "Done"
    }

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        flowOf(sendMessage(prompt))

    override fun sendMessageAsync(contents: Contents): Flow<Any?> =
        flowOf(sendMessage(contents))

    override fun cancelProcess() = Unit

    override fun close() = Unit
}
