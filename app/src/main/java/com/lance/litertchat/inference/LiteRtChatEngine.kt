package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

interface ChatEngine {
    suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig = InferenceRuntimeConfig.defaultCpu
    ): Result<Unit>
    suspend fun generate(prompt: String): Result<String>
    suspend fun generateStreaming(
        prompt: String,
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
                        backend = backendConfig.backend
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

    private companion object {
        const val MODEL_EXTENSION = ".litertlm"
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
    fun create(modelPath: String, backend: Backend): LiteRtEngineHandle
}

internal interface LiteRtEngineHandle {
    fun initialize()
    fun createConversation(): LiteRtConversationHandle
    fun close()
}

internal interface LiteRtConversationHandle {
    fun sendMessage(prompt: String): Any?
    fun sendMessageAsync(prompt: String): Flow<Any?>
    fun cancelProcess()
    fun close()
}

private object RealLiteRtEngineHandleFactory : LiteRtEngineHandleFactory {
    override fun create(modelPath: String, backend: Backend): LiteRtEngineHandle =
        RealLiteRtEngineHandle(
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend
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

    override fun sendMessageAsync(prompt: String): Flow<Any?> =
        conversation.sendMessageAsync(prompt)

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
