package com.lance.litertchat.inference

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

class LlamaCppChatEngine(
    private val bridge: NativeLlamaBridge = NativeLlamaBridge(),
    private val config: NativeLlamaConfig = NativeLlamaConfig(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatEngine {
    private val lock = Any()
    private val loadMutex = Mutex()
    private var loadedModelPath: String? = null
    private var loadGeneration: Long = 0L

    override suspend fun load(modelFile: File): Result<Unit> = withContext(ioDispatcher) {
        loadMutex.withLock {
            val modelPath = modelFile.absolutePath
            val generation = synchronized(lock) {
                if (loadedModelPath == modelPath) {
                    return@withContext Result.success(Unit)
                }
                loadedModelPath = null
                loadGeneration += 1
                loadGeneration
            }

            val result = bridge.load(modelFile, config)
            result.fold(
                onSuccess = {
                    synchronized(lock) {
                        if (loadGeneration == generation) {
                            loadedModelPath = modelPath
                            return@withContext Result.success(Unit)
                        }
                    }
                    bridge.release()
                    Result.failure(IllegalStateException("Model load was superseded."))
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    override suspend fun generate(prompt: String): Result<String> =
        generateStreaming(prompt) { }

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = withContext(ioDispatcher) {
        val partialText = StringBuilder()
        bridge.generate(prompt, config) { token ->
            partialText.append(token)
            onPartialResponse(partialText.toString())
        }
    }

    override fun cancelGeneration() {
        bridge.cancelGeneration()
    }

    override fun release() {
        synchronized(lock) {
            loadedModelPath = null
            loadGeneration += 1
        }
        bridge.release()
    }
}
