package com.lance.litertchat.inference

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
