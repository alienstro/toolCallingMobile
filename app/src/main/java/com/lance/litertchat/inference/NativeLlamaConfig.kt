package com.lance.litertchat.inference

data class NativeLlamaConfig(
    val contextLength: Int = 1024,
    val batchSize: Int = 512,
    val maxTokens: Int = 256,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f
) {
    fun sanitized(): NativeLlamaConfig =
        copy(
            contextLength = contextLength.coerceAtLeast(512),
            batchSize = batchSize.coerceAtLeast(1),
            maxTokens = maxTokens.coerceAtLeast(1),
            threads = threads.coerceAtLeast(1),
            temperature = temperature.sanitizeTemperature(),
            topK = topK.coerceAtLeast(0),
            topP = topP.sanitizeTopP()
        )

    private fun Float.sanitizeTemperature(): Float =
        if (isFinite()) {
            coerceAtLeast(0.0f)
        } else {
            DEFAULT_TEMPERATURE
        }

    private fun Float.sanitizeTopP(): Float =
        if (isNaN()) {
            DEFAULT_TOP_P
        } else {
            coerceIn(0.0f, 1.0f)
        }

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 0.9f
    }
}
