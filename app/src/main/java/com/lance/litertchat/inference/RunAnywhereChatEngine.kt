package com.lance.litertchat.inference

import com.lance.litertchat.model.ModelConstants
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelRegistry
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.public.extensions.cancelGeneration
import com.runanywhere.sdk.public.extensions.chat
import com.runanywhere.sdk.public.extensions.generateStream
import com.runanywhere.sdk.public.extensions.loadLLMModel
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.unloadLLMModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class RunAnywhereChatEngine : ChatEngine {
    private val lock = Any()
    private var loadedModelPath: String? = null
    private var loadedModelId: String? = null

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(RunAnywhereInitializer.isInitialized) {
                "RunAnywhere initialization failed: initialize the Kotlin SDK before loading a model."
            }
            require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
            require(modelFile.name.endsWith(ModelConstants.MODEL_EXTENSION)) {
                "Model file must end with ${ModelConstants.MODEL_EXTENSION}."
            }

            val modelPath = modelFile.absolutePath
            synchronized(lock) {
                if (loadedModelPath == modelPath && loadedModelId != null) {
                    return@runCatching
                }
            }

            val modelId = modelIdFor(modelFile)
            registerLocalModel(modelId, modelFile)
            RunAnywhere.loadLLMModel(modelId)

            synchronized(lock) {
                loadedModelPath = modelPath
                loadedModelId = modelId
            }
        }
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }
            requireLoadedModel()
            RunAnywhere.chat(prompt)
        }
    }

    override suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            require(prompt.isNotBlank()) { "Prompt must not be blank." }
            requireLoadedModel()

            val response = StringBuilder()
            RunAnywhere.generateStream(prompt).collect { token ->
                response.append(token)
                onPartialResponse(response.toString())
            }
            response.toString()
        }
    }

    override fun cancelGeneration() {
        runCatching { RunAnywhere.cancelGeneration() }
    }

    override fun release() {
        synchronized(lock) {
            loadedModelPath = null
            loadedModelId = null
        }
        runCatching {
            runBlocking {
                RunAnywhere.unloadLLMModel()
            }
        }
    }

    private fun requireLoadedModel() {
        synchronized(lock) {
            check(loadedModelPath != null && loadedModelId != null) {
                "RunAnywhere model is not loaded."
            }
        }
    }

    private fun registerLocalModel(modelId: String, modelFile: File) {
        RunAnywhere.registerModel(
            id = modelId,
            name = modelFile.nameWithoutExtension,
            url = modelFile.toURI().toString(),
            framework = InferenceFramework.LLAMA_CPP,
            modality = ModelCategory.LANGUAGE,
            memoryRequirement = modelFile.length()
        )

        val bridgeModel = CppBridgeModelRegistry.get(modelId)
            ?: error("RunAnywhere model registration failed.")
        CppBridgeModelRegistry.registerModel(
            bridgeModel.copy(localPath = modelFile.absolutePath)
        )
    }

    private fun modelIdFor(modelFile: File): String {
        val normalizedName = modelFile.nameWithoutExtension
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "local-gguf" }
        return "local-$normalizedName"
    }
}
