package com.lance.litertchat.inference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

interface ChatEngine {
    suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig = InferenceRuntimeConfig.defaultCpu
    ): Result<Unit>
    suspend fun generate(prompt: String): Result<String>
    suspend fun generateWithImage(prompt: String, imagePath: String): Result<String>
    suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String>
    suspend fun generateStreamingWithImage(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String>
    fun cancelGeneration()
    fun release()
}

@OptIn(ExperimentalApi::class)
class LiteRtChatEngine : ChatEngine {
    private val engineFactory: LiteRtEngineHandleFactory
    private val speculativeFlagOwner = Any()
    private val lock = Any()
    private var engine: LiteRtEngineHandle? = null
    private var conversation: LiteRtConversationHandle? = null
    private var loadedModelPath: String? = null
    private var loadedRuntimeConfig: InferenceRuntimeConfig? = null

    constructor() {
        engineFactory = RealLiteRtEngineHandleFactory
    }

    internal constructor(engineFactory: LiteRtEngineHandleFactory) {
        this.engineFactory = engineFactory
    }

    override suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(lock) {
                require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
                require(modelFile.name.endsWith(MODEL_EXTENSION)) {
                    "Model file must end with $MODEL_EXTENSION."
                }

                val modelPath = modelFile.absolutePath
                if (
                    loadedModelPath == modelPath &&
                    loadedRuntimeConfig == runtimeConfig &&
                    engine != null &&
                    conversation != null
                ) {
                    return@runCatching
                }

                if (loadedModelPath != modelPath || loadedRuntimeConfig != runtimeConfig) {
                    release()
                } else if (engine == null || conversation == null) {
                    release()
                }

                val backendConfig = LiteRtBackendConfig.from(runtimeConfig)
                setSpeculativeFlagOwner(
                    owner = speculativeFlagOwner,
                    enabled = backendConfig.speculativeDecodingEnabled
                )

                val newEngine = try {
                    engineFactory.create(
                        modelPath = modelPath,
                        backend = backendConfig.backend,
                        visionBackend = backendConfig.backend
                    )
                } catch (error: Throwable) {
                    setSpeculativeFlagOwner(owner = speculativeFlagOwner, enabled = false)
                    throw error
                }

                try {
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation()

                    engine = newEngine
                    conversation = newConversation
                    loadedModelPath = modelPath
                    loadedRuntimeConfig = runtimeConfig
                } catch (error: Throwable) {
                    runCatching { newEngine.close() }
                    setSpeculativeFlagOwner(owner = speculativeFlagOwner, enabled = false)
                    throw error
                }
            }
        }
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }

            val activeConversation = synchronized(lock) {
                val activeConversation = conversation
                    ?: error("LiteRT-LM model is not loaded.")
                if (engine == null || loadedModelPath == null) {
                    error("LiteRT-LM model is not loaded.")
                }
                activeConversation
            }

            val message = activeConversation.sendMessage(prompt)
            message.toString()
        }
    }

    override suspend fun generateWithImage(prompt: String, imagePath: String): Result<String> =
        generateContents(prompt = prompt, imagePath = imagePath)

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }

            val activeConversation = synchronized(lock) {
                val activeConversation = conversation
                    ?: error("LiteRT-LM model is not loaded.")
                if (engine == null || loadedModelPath == null) {
                    error("LiteRT-LM model is not loaded.")
                }
                activeConversation
            }

            var latest = ""
            activeConversation.sendMessageAsync(prompt).collect { message ->
                latest = message.toString()
                onPartialResponse(latest)
            }
            latest
        }
    }

    override suspend fun generateStreamingWithImage(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = generateContentsStreaming(
        prompt = prompt,
        imagePath = imagePath,
        onPartialResponse = onPartialResponse
    )

    private suspend fun generateContents(prompt: String, imagePath: String): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val contents = promptImageContents(prompt, imagePath)
                val activeConversation = activeConversation()

                val message = activeConversation.sendMessage(contents)
                message.toString()
            }
        }

    private suspend fun generateContentsStreaming(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val contents = promptImageContents(prompt, imagePath)
            val activeConversation = activeConversation()

            var latest = ""
            activeConversation.sendMessageAsync(contents).collect { message ->
                latest = message.toString()
                onPartialResponse(latest)
            }
            latest
        }
    }

    override fun cancelGeneration() {
        synchronized(lock) {
            runCatching { conversation?.cancelProcess() }
        }
    }

    override fun release() {
        synchronized(lock) {
            val activeConversation = conversation
            val activeEngine = engine

            conversation = null
            engine = null
            loadedModelPath = null
            loadedRuntimeConfig = null
            setSpeculativeFlagOwner(owner = speculativeFlagOwner, enabled = false)

            runCatching { activeConversation?.close() }
            runCatching { activeEngine?.close() }
        }
    }

    private fun activeConversation(): LiteRtConversationHandle =
        synchronized(lock) {
            val activeConversation = conversation
                ?: error("LiteRT-LM model is not loaded.")
            if (engine == null || loadedModelPath == null) {
                error("LiteRT-LM model is not loaded.")
            }
            activeConversation
        }

    private fun promptImageContents(prompt: String, imagePath: String): Contents {
        require(prompt.isNotBlank()) { "Prompt must not be blank." }
        require(imagePath.isNotBlank()) { "Image path must not be blank." }
        val imageFile = File(imagePath)
        require(imageFile.exists() && imageFile.isFile) { "Image file does not exist." }
        val imageBytes = normalizedImageBytes(imageFile)
        require(imageBytes.isNotEmpty()) { "Image file is empty." }
        return Contents.of(
            Content.ImageBytes(imageBytes),
            Content.Text(prompt)
        )
    }

    private fun normalizedImageBytes(imageFile: File): ByteArray {
        val bitmap = runCatching { decodeSampledBitmap(imageFile, MAX_IMAGE_SIDE_PX) }.getOrNull()
            ?: return imageFile.readBytes()
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            return output.toByteArray()
        }
    }

    private fun decodeSampledBitmap(imageFile: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = max(
            1,
            max(
                (bounds.outWidth + maxSide - 1) / maxSide,
                (bounds.outHeight + maxSide - 1) / maxSide
            )
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(imageFile.absolutePath, options)
    }

    private companion object {
        const val MODEL_EXTENSION = ".litertlm"
        const val MAX_IMAGE_SIDE_PX = 1024
        private val speculativeFlagOwners = mutableSetOf<Any>()
        private val speculativeFlagLock = Any()

        fun setSpeculativeFlagOwner(owner: Any, enabled: Boolean) {
            synchronized(speculativeFlagLock) {
                if (enabled) {
                    speculativeFlagOwners += owner
                } else {
                    speculativeFlagOwners -= owner
                }
                ExperimentalFlags.enableSpeculativeDecoding = speculativeFlagOwners.isNotEmpty()
            }
        }
    }
}

internal interface LiteRtEngineHandleFactory {
    fun create(modelPath: String, backend: Backend, visionBackend: Backend?): LiteRtEngineHandle
}

internal interface LiteRtEngineHandle {
    fun initialize()
    fun createConversation(): LiteRtConversationHandle
    fun close()
}

internal interface LiteRtConversationHandle {
    fun sendMessage(prompt: String): Any?
    fun sendMessage(contents: Contents): Any?
    fun sendMessageAsync(prompt: String): Flow<Any?>
    fun sendMessageAsync(contents: Contents): Flow<Any?>
    fun cancelProcess()
    fun close()
}

private object RealLiteRtEngineHandleFactory : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend, visionBackend: Backend?): LiteRtEngineHandle =
        RealLiteRtEngineHandle(
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend
                )
            )
        )
}

private class RealLiteRtEngineHandle(
    private val engine: Engine
) : LiteRtEngineHandle {
    override fun initialize() {
        engine.initialize()
    }

    override fun createConversation(): LiteRtConversationHandle =
        RealLiteRtConversationHandle(engine.createConversation())

    override fun close() {
        engine.close()
    }
}

private class RealLiteRtConversationHandle(
    private val conversation: Conversation
) : LiteRtConversationHandle {
    override fun sendMessage(prompt: String): Any? =
        conversation.sendMessage(prompt)

    override fun sendMessage(contents: Contents): Any? =
        conversation.sendMessage(contents)

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        conversation.sendMessageAsync(prompt)

    override fun sendMessageAsync(contents: Contents): Flow<Any?> =
        conversation.sendMessageAsync(contents)

    override fun cancelProcess() {
        conversation.cancelProcess()
    }

    override fun close() {
        conversation.close()
    }
}

internal data class LiteRtBackendConfig(
    val backend: Backend,
    val speculativeDecodingEnabled: Boolean
) {
    companion object {
        fun from(config: InferenceRuntimeConfig): LiteRtBackendConfig =
            LiteRtBackendConfig(
                backend = when (config.backend) {
                    InferenceBackend.CPU -> Backend.CPU()
                    InferenceBackend.GPU -> Backend.GPU()
                },
                speculativeDecodingEnabled = config.speculativeDecodingEnabled
            )
    }
}
