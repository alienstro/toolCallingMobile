package com.lance.llamacppchat.inference

import java.io.File

interface EmbeddingEngine {
    suspend fun load(modelFile: File): Result<Unit>
    suspend fun embed(text: String): Result<FloatArray>
    fun unload()
}

object UnavailableEmbeddingEngine : EmbeddingEngine {
    override suspend fun load(modelFile: File): Result<Unit> =
        Result.failure(IllegalStateException("No embedding engine configured."))

    override suspend fun embed(text: String): Result<FloatArray> =
        Result.failure(IllegalStateException("No embedding engine configured."))

    override fun unload() = Unit
}
