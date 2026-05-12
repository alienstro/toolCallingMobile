# RunAnywhere Migration Design

## Goal

Fully replace the current LiteRT-LM Android inference path with RunAnywhere's Kotlin SDK, making RunAnywhere the only supported local LLM runtime in the app.

## Current State

The app is a native Android Kotlin and Jetpack Compose project. It currently stores one active model in app-private storage, downloads or imports `.litertlm` files, and runs chat through `LiteRtChatEngine`, which wraps `com.google.ai.edge.litertlm`.

The existing architecture has useful boundaries that should remain:

- `AppViewModel` owns UI state, chat history, model lifecycle actions, and generation flow.
- `ChatEngine` is the inference abstraction used by `AppViewModel`.
- `ModelRepository` owns app-private model storage and active model metadata.
- `ModelDownloader` owns HTTPS download and Hugging Face `/blob/` to `/resolve/` normalization.
- Compose screens display model lifecycle, chat, diagnostics, and settings.

## Target State

The app should use RunAnywhere as the sole inference runtime. The app should support `.gguf` LLM models only, because RunAnywhere's Android LLM backend uses llama.cpp and GGUF files for text generation.

Visible LiteRT-LM references should be removed from app behavior, README instructions, diagnostics wording, and model manager copy. Internal package names can remain `com.lance.litertchat` for now to avoid an Android application/package rename. The user-facing app name can be updated separately if desired.

## Runtime Architecture

Keep the existing `ChatEngine` interface:

```kotlin
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
```

Replace `LiteRtChatEngine` with `RunAnywhereChatEngine`.

`RunAnywhereChatEngine` responsibilities:

- Validate that the model file exists and ends with `.gguf`.
- Initialize RunAnywhere once before model load if initialization has not already happened.
- Register the installed model with a stable local model id.
- Load the registered model through `RunAnywhere.loadLLMModel(modelId)`.
- Generate non-streaming text through `RunAnywhere.chat(prompt)`.
- Generate streaming text through `RunAnywhere.generateStream(prompt)`.
- Track the loaded model path so repeated sends do not reload the same model unnecessarily.
- Expose cancellation best-effort through coroutine cancellation and any RunAnywhere cancellation API available in the installed SDK version.
- Make `release()` clear local engine state. If RunAnywhere exposes an unload/release API in the installed SDK version, call it there.

## SDK Initialization

RunAnywhere initialization must happen before inference:

- `AndroidPlatformContext.initialize(context)`
- `RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)` for debug-oriented local testing
- `CppBridgeModelPaths.setBaseDirectory(File(context.filesDir, "runanywhere").absolutePath)`
- `LlamaCPP.register(priority = 100)`

Add a small initializer, for example `RunAnywhereInitializer`, so the ordering is explicit and unit-testable at the Kotlin boundary. `MainActivity.onCreate()` should call it before `setContent`, and `RunAnywhereChatEngine.load()` should also call the initializer defensively if needed.

The app only needs the core SDK and llama.cpp module. It should not add ONNX, STT, TTS, VAD, RAG, or VLM dependencies for this migration.

## Gradle And Repositories

Update `settings.gradle.kts` to include JitPack because RunAnywhere documents it as required for transitive dependencies:

```kotlin
maven { url = uri("https://jitpack.io") }
```

Update `app/build.gradle.kts`:

- Remove `com.google.ai.edge.litertlm:litertlm-android`.
- Add `io.github.sanchitmonga22:runanywhere-sdk-android:0.20.6`.
- Add `io.github.sanchitmonga22:runanywhere-llamacpp-android:0.20.6`.
- Keep current Android `minSdk = 26`, `compileSdk = 35`, Kotlin, and Java 21 settings unless Gradle resolution proves the SDK requires a lower `jvmTarget` setting.

## Model Lifecycle

Change the supported model extension from `.litertlm` to `.gguf`.

`ModelConstants` should provide:

- `MODEL_EXTENSION = ".gguf"`
- a default GGUF model URL
- optional hardware warnings only when the file name suggests a device-specific build

The default model should be Hugging Face's SmolLM2 360M Instruct Q8_0 GGUF because RunAnywhere's Kotlin quick start uses `smollm2-360m-instruct-q8_0` as an example model id and the file is small enough for first-device testing:

```text
https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf
```

`ModelDownloader.normalizeModelUrl()` should continue to convert Hugging Face `/blob/` links to `/resolve/`, but tests and error messages should require `.gguf`.

Imports should copy selected content into app-private storage with a `.gguf` filename. If Android's document picker does not expose a trustworthy filename, preserve the current timestamped import naming pattern with `.gguf`.

Existing installed `.litertlm` metadata should not be treated as compatible. If old metadata points to a `.litertlm` file, the app should report no usable active model or show a clear "Install a GGUF model" error when loading.

## UI And Diagnostics

Keep the current screens and workflows:

- Models: download default model, paste custom URL, import local model, delete model.
- Chat: send prompts, stream responses when enabled, stop generation.
- Settings: prompt formatter and streaming toggle.
- Diagnostics: active model path, size, device info, storage, last error.

Update visible text from LiteRT-LM and `.litertlm` to RunAnywhere and `.gguf`.

Generation statistics can remain estimated from text output because RunAnywhere chat/stream APIs used here do not guarantee token timing metadata in the app abstraction.

## Error Handling

Handle these cases explicitly:

- Missing model: chat remains disabled and prompts user to install a GGUF model.
- Wrong extension: download/import/load fails with "Model file must end with .gguf."
- RunAnywhere initialization failure: show "RunAnywhere initialization failed" plus the SDK error message.
- Backend registration failure: show "RunAnywhere llama.cpp backend registration failed" plus the SDK error message.
- Model load failure: remove the loading assistant placeholder and show the SDK error message.
- Stream failure: preserve any partial generated text only if RunAnywhere emitted it before failure; otherwise remove the loading placeholder and show the error.
- Stop generation: cancel the active coroutine and call the engine's cancellation hook.

## Testing Strategy

Automated tests should cover app-owned logic without loading a real native model:

- `ModelConstantsTest`: `.gguf` extension and default URL shape.
- `ModelDownloaderTest`: `.gguf` validation, Hugging Face `/blob/` to `/resolve/`, rejection of non-HTTPS URLs, rejection of non-GGUF URLs.
- `ModelRepositoryTest`: active metadata still saves, loads, and deletes app-private GGUF files safely.
- `AppViewModelTest`: download/import metadata uses `.gguf`, missing model disables chat, engine load/generate errors are surfaced, streaming partial updates still work through a fake `ChatEngine`.

Manual device verification remains required for native RunAnywhere inference:

- Install debug APK on the OPPO Reno11 5G or another Android API 26+ device.
- Download the default GGUF model.
- Import a local GGUF model.
- Send a prompt with streaming enabled.
- Send a prompt with streaming disabled.
- Stop generation mid-stream.
- Delete the model and confirm chat disables.
- Check diagnostics after success and after a forced invalid model URL.

## Out Of Scope

- Supporting LiteRT-LM and RunAnywhere side by side.
- Renaming the Android package/application id.
- Adding STT, TTS, VLM, RAG, LoRA, or tool-calling features.
- Building a model discovery browser.
- Bundling model files inside the APK.
