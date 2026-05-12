package com.lance.litertchat.inference

enum class InferenceBackend {
    CPU,
    GPU,
    NPU
}

data class InferenceRuntimeConfig(
    val backend: InferenceBackend = InferenceBackend.CPU,
    val speculativeDecodingEnabled: Boolean = false
) {
    init {
        require(!speculativeDecodingEnabled || backend == InferenceBackend.GPU) {
            "Speculative decoding requires GPU backend."
        }
    }

    val label: String
        get() = when {
            backend == InferenceBackend.GPU && speculativeDecodingEnabled -> "GPU + MTP"
            backend == InferenceBackend.GPU -> "GPU"
            backend == InferenceBackend.NPU -> "NPU"
            else -> "CPU"
        }

    companion object {
        val defaultCpu = InferenceRuntimeConfig()
    }
}

data class InferenceRuntimeStatus(
    val requested: InferenceRuntimeConfig = InferenceRuntimeConfig.defaultCpu,
    val active: InferenceRuntimeConfig = InferenceRuntimeConfig.defaultCpu,
    val fallbackReason: String? = null
)
