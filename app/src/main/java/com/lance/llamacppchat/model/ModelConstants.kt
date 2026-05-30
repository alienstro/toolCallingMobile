package com.lance.llamacppchat.model

object ModelConstants {
    const val DEFAULT_MODEL_REPOSITORY = "unsloth/Qwen3.5-0.8B-GGUF"
    const val DEFAULT_MODEL_FILE = "Qwen3.5-0.8B-Q8_0.gguf"
    const val DEFAULT_MODEL_URL =
        "https://huggingface.co/$DEFAULT_MODEL_REPOSITORY/resolve/main/$DEFAULT_MODEL_FILE"
    const val MODEL_EXTENSION = ".gguf"

    fun hardwareWarningForFileName(fileName: String): String? {
        val lower = fileName.lowercase()
        return when {
            "qualcomm_sm8750" in lower ->
                "This model is optimized for Qualcomm SM8750 devices. Your OPPO Reno11 5G uses MediaTek Dimensity 7050, so use the generic model first."
            "qualcomm_gcs8275" in lower ->
                "This model is optimized for Qualcomm Dragonwing GCS8275 devices, not a typical OPPO Reno11 5G phone."
            else -> null
        }
    }
}
