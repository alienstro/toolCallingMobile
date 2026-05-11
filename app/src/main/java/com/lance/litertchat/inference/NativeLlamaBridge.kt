package com.lance.litertchat.inference

import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class NativeLlamaBridge(
    loadLibrary: Boolean = true
) {
    private val handleLock = ReentrantReadWriteLock()
    private var handle: Long = 0L

    init {
        if (loadLibrary) {
            System.loadLibrary("native-llamacpp")
        }
    }

    fun load(modelFile: File, config: NativeLlamaConfig): Result<Unit> = runCatching {
        require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
        require(modelFile.name.endsWith(MODEL_EXTENSION)) {
            "Model file must end with $MODEL_EXTENSION."
        }

        val sanitized = config.sanitized()
        handleLock.write {
            releaseLocked()
            val loadedHandle = nativeLoadModel(
                modelPath = modelFile.absolutePath,
                contextLength = sanitized.contextLength,
                batchSize = sanitized.batchSize,
                threads = sanitized.threads
            )
            check(loadedHandle != 0L) { "Failed to load llama.cpp model." }
            handle = loadedHandle
        }
    }

    fun generate(
        prompt: String,
        config: NativeLlamaConfig,
        onToken: (String) -> Unit
    ): Result<String> = runCatching {
        handleLock.read {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }
            val currentHandle = handle
            check(currentHandle != 0L) { "llama.cpp model is not loaded." }

            val sanitized = config.sanitized()
            nativeGenerate(
                handle = currentHandle,
                prompt = prompt,
                maxTokens = sanitized.maxTokens,
                temperature = sanitized.temperature,
                topK = sanitized.topK,
                topP = sanitized.topP,
                callback = object : TokenCallback {
                    override fun onToken(token: String) {
                        onToken(token)
                    }
                }
            )
        }
    }

    fun cancelGeneration() {
        handleLock.read {
            val currentHandle = handle
            if (currentHandle != 0L) {
                nativeCancel(currentHandle)
            }
        }
    }

    fun release() {
        handleLock.write {
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        val currentHandle = handle
        if (currentHandle != 0L) {
            nativeRelease(currentHandle)
            handle = 0L
        }
    }

    external fun nativeRuntimeVersion(): String

    private external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        batchSize: Int,
        threads: Int
    ): Long

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): String

    private external fun nativeCancel(handle: Long)

    private external fun nativeRelease(handle: Long)

    interface TokenCallback {
        fun onToken(token: String)
    }

    private companion object {
        const val MODEL_EXTENSION = ".gguf"
    }
}
