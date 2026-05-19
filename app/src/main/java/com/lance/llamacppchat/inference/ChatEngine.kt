package com.lance.llamacppchat.inference

import java.io.File

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

object UnavailableChatEngine : ChatEngine {
    override suspend fun load(modelFile: File, runtimeConfig: InferenceRuntimeConfig): Result<Unit> =
        Result.failure(IllegalStateException("No chat engine is configured."))

    override suspend fun generate(prompt: String): Result<String> =
        Result.failure(IllegalStateException("No chat engine is configured."))

    override suspend fun generateWithImage(prompt: String, imagePath: String): Result<String> =
        Result.failure(IllegalStateException("No chat engine is configured."))

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> =
        Result.failure(IllegalStateException("No chat engine is configured."))

    override suspend fun generateStreamingWithImage(
        prompt: String,
        imagePath: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> =
        Result.failure(IllegalStateException("No chat engine is configured."))

    override fun cancelGeneration() = Unit

    override fun release() = Unit
}
