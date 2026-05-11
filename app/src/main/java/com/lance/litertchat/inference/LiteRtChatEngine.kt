package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

interface ChatEngine {
    suspend fun load(modelFile: File): Result<Unit>
    suspend fun generate(prompt: String): Result<String>
    suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String>
    fun cancelGeneration()
    fun release()
}

class LiteRtChatEngine : ChatEngine {
    private val lock = Any()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelPath: String? = null

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(lock) {
                require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
                require(modelFile.name.endsWith(MODEL_EXTENSION)) {
                    "Model file must end with $MODEL_EXTENSION."
                }

                val modelPath = modelFile.absolutePath
                if (
                    loadedModelPath == modelPath &&
                    engine != null &&
                    conversation != null
                ) {
                    return@runCatching
                }

                if (loadedModelPath != modelPath) {
                    release()
                } else if (engine == null || conversation == null) {
                    release()
                }

                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU()
                    )
                )

                try {
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation()

                    engine = newEngine
                    conversation = newConversation
                    loadedModelPath = modelPath
                } catch (error: Throwable) {
                    release()
                    runCatching { newEngine.close() }
                    throw error
                }
            }
        }
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(lock) {
                require(prompt.isNotBlank()) { "Prompt must not be blank." }

                val activeConversation = conversation
                    ?: error("LiteRT-LM model is not loaded.")
                if (engine == null || loadedModelPath == null) {
                    error("LiteRT-LM model is not loaded.")
                }

                val message = activeConversation.sendMessage(prompt)
                message.toString()
            }
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

            runCatching { activeConversation?.close() }
            runCatching { activeEngine?.close() }
        }
    }

    private companion object {
        const val MODEL_EXTENSION = ".litertlm"
    }
}
