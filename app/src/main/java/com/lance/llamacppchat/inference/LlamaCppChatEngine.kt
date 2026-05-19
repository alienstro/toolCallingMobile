package com.lance.llamacppchat.inference

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.lance.llamacppchat.model.ModelConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class LlamaCppChatEngine private constructor(
    private val inferenceEngine: InferenceEngine
) : ChatEngine {
    private val lock = Any()
    private var loadedModelPath: String? = null

    constructor(context: Context) : this(AiChat.getInferenceEngine(context.applicationContext))

    internal constructor(inferenceEngine: InferenceEngine, @Suppress("UNUSED_PARAMETER") marker: Unit) : this(inferenceEngine)

    override suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
            require(modelFile.name.endsWith(ModelConstants.MODEL_EXTENSION, ignoreCase = true)) {
                "Model file must end with ${ModelConstants.MODEL_EXTENSION}."
            }
            require(runtimeConfig.backend == InferenceBackend.CPU && !runtimeConfig.speculativeDecodingEnabled) {
                "This llama.cpp build supports CPU GGUF inference only."
            }

            val modelPath = modelFile.absolutePath
            val alreadyLoaded = synchronized(lock) { loadedModelPath == modelPath }
            if (alreadyLoaded && inferenceEngine.state.value.isModelLoaded) {
                return@runCatching
            }

            if (inferenceEngine.state.value.isModelLoaded || loadedModelPath != null) {
                release()
            }

            inferenceEngine.loadModel(modelPath)
            synchronized(lock) {
                loadedModelPath = modelPath
            }
        }
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }
            requireLoaded()

            val response = StringBuilder()
            inferenceEngine.sendUserPrompt(prompt, PREDICT_LENGTH).collect { chunk ->
                response.append(chunk)
            }
            response.toString()
        }
    }

    override suspend fun generateWithImage(prompt: String, imagePath: String): Result<String> =
        unsupportedImagePrompt()

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }
            requireLoaded()

            val response = StringBuilder()
            inferenceEngine.sendUserPrompt(prompt, PREDICT_LENGTH).collect { chunk ->
                response.append(chunk)
                onPartialResponse(chunk)
            }
            response.toString()
        }
    }

    override suspend fun generateStreamingWithImage(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = unsupportedImagePrompt()

    override fun cancelGeneration() = Unit

    override fun release() {
        val shouldCleanUp = synchronized(lock) {
            val hasLoadedModel = loadedModelPath != null || inferenceEngine.state.value.isModelLoaded
            loadedModelPath = null
            hasLoadedModel
        }
        if (shouldCleanUp) {
            runCatching { inferenceEngine.cleanUp() }
        }
    }

    private fun requireLoaded() {
        val modelPath = synchronized(lock) { loadedModelPath }
        check(modelPath != null && inferenceEngine.state.value.isModelLoaded) {
            "llama.cpp model is not loaded."
        }
    }

    private fun unsupportedImagePrompt(): Result<String> =
        Result.failure(UnsupportedOperationException("Image prompts are not supported by the llama.cpp GGUF runtime."))

    private companion object {
        const val PREDICT_LENGTH = 1024
    }
}
