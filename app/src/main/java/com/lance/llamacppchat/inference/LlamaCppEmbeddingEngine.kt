package com.lance.llamacppchat.inference

import android.content.Context
import com.arm.aichat.AiChat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaCppEmbeddingEngine(context: Context) : EmbeddingEngine {
    private val engine = AiChat.getEmbeddingEngine(context.applicationContext)

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(modelFile.exists() && modelFile.isFile) { "Embedding model file does not exist." }
            engine.loadEmbeddingModel(modelFile.absolutePath)
        }
    }

    override suspend fun embed(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        runCatching {
            require(text.isNotBlank()) { "Cannot embed blank text." }
            engine.embed(text)
        }
    }

    override fun unload() = engine.unloadEmbeddingModel()
}
