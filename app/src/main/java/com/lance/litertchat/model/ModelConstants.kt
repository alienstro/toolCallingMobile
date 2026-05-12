package com.lance.litertchat.model

object ModelConstants {
    const val DEFAULT_MODEL_URL =
        "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-UD-Q8_K_XL.gguf"
    const val MODEL_EXTENSION = ".gguf"

    fun hardwareWarningForFileName(fileName: String): String? {
        return null
    }
}
