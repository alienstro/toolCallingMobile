# LiteRT Gemma 4 MTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in GPU and Gemma 4 MTP speculative decoding support while keeping CPU chat as the default and fallback path.

**Architecture:** Persist backend/MTP settings in `AppSettingsRepository`, convert them to an `InferenceRuntimeConfig` in `AppViewModel`, and pass that config into `ChatEngine.load`. `LiteRtChatEngine` maps app config to LiteRT-LM `Backend` and `ExperimentalFlags`, while `AppViewModel` owns GPU-to-CPU fallback state and user-visible warnings.

**Tech Stack:** Kotlin, Android Jetpack Compose, LiteRT-LM Android `0.11.0`, coroutines, JUnit.

---

## File Structure

- Modify `app/src/main/java/com/lance/litertchat/settings/AppSettingsRepository.kt`: persist GPU and MTP toggles.
- Modify `app/src/test/java/com/lance/litertchat/settings/AppSettingsRepositoryTest.kt`: cover defaults and toggle interaction.
- Create `app/src/main/java/com/lance/litertchat/inference/InferenceRuntimeConfig.kt`: runtime config and status types.
- Modify `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`: accept runtime config, choose CPU/GPU backend, set speculative decoding flag.
- Create `app/src/test/java/com/lance/litertchat/inference/LiteRtChatEngineConfigTest.kt`: test config-to-SDK mapping through a pure helper.
- Modify `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`: add Gemma 4 E4B MTP URL and compatibility note.
- Modify `app/src/test/java/com/lance/litertchat/model/ModelConstantsTest.kt`: cover MTP URL.
- Modify `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`: request runtime config from settings, handle fallback, expose runtime status.
- Modify `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`: cover runtime config and fallback behavior.
- Modify `app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt`: add GPU and MTP switches.
- Modify `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`: show requested/active runtime mode and fallback reason.
- Modify `app/src/main/java/com/lance/litertchat/App.kt`: wire new settings callbacks into `SettingsScreen`.

## Task 1: Persist GPU And MTP Settings

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/settings/AppSettingsRepository.kt`
- Modify: `app/src/test/java/com/lance/litertchat/settings/AppSettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing settings tests**

Add these tests to `AppSettingsRepositoryTest`:

```kotlin
@Test
fun gpuBackendIsDisabledByDefault() {
    val repository = AppSettingsRepository(temporaryFolder.root)

    assertFalse(repository.load().gpuBackendEnabled)
}

@Test
fun gemmaMtpIsDisabledByDefault() {
    val repository = AppSettingsRepository(temporaryFolder.root)

    assertFalse(repository.load().gemmaMtpEnabled)
}

@Test
fun savesGpuBackendToggle() {
    val repository = AppSettingsRepository(temporaryFolder.root)

    repository.setGpuBackendEnabled(true)

    assertTrue(repository.load().gpuBackendEnabled)
}

@Test
fun savesGemmaMtpToggleOnlyWhenGpuIsEnabled() {
    val repository = AppSettingsRepository(temporaryFolder.root)

    repository.setGemmaMtpEnabled(true)
    assertFalse(repository.load().gemmaMtpEnabled)

    repository.setGpuBackendEnabled(true)
    repository.setGemmaMtpEnabled(true)
    assertTrue(repository.load().gemmaMtpEnabled)
}

@Test
fun disablingGpuAlsoDisablesGemmaMtp() {
    val repository = AppSettingsRepository(temporaryFolder.root)

    repository.setGpuBackendEnabled(true)
    repository.setGemmaMtpEnabled(true)
    repository.setGpuBackendEnabled(false)

    assertFalse(repository.load().gpuBackendEnabled)
    assertFalse(repository.load().gemmaMtpEnabled)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.settings.AppSettingsRepositoryTest`

Expected: FAIL because `gpuBackendEnabled`, `gemmaMtpEnabled`, `setGpuBackendEnabled`, and `setGemmaMtpEnabled` do not exist.

- [ ] **Step 3: Implement settings persistence**

Replace `AppSettings` and add the methods/keys in `AppSettingsRepository`:

```kotlin
data class AppSettings(
    val streamResponsesEnabled: Boolean = true,
    val gpuBackendEnabled: Boolean = false,
    val gemmaMtpEnabled: Boolean = false
)
```

```kotlin
return AppSettings(
    streamResponsesEnabled = properties
        .getProperty(KEY_STREAM_RESPONSES_ENABLED)
        ?.toBooleanStrictOrNull()
        ?: true,
    gpuBackendEnabled = properties
        .getProperty(KEY_GPU_BACKEND_ENABLED)
        ?.toBooleanStrictOrNull()
        ?: false,
    gemmaMtpEnabled = properties
        .getProperty(KEY_GEMMA_MTP_ENABLED)
        ?.toBooleanStrictOrNull()
        ?: false
)
```

Normalize impossible persisted states after loading:

```kotlin
private fun normalize(settings: AppSettings): AppSettings =
    if (settings.gpuBackendEnabled) {
        settings
    } else {
        settings.copy(gemmaMtpEnabled = false)
    }
```

Call `normalize(...)` before returning from `load()`.

Add setters:

```kotlin
fun setGpuBackendEnabled(enabled: Boolean) {
    val current = load()
    save(
        current.copy(
            gpuBackendEnabled = enabled,
            gemmaMtpEnabled = if (enabled) current.gemmaMtpEnabled else false
        )
    )
}

fun setGemmaMtpEnabled(enabled: Boolean) {
    val current = load()
    save(current.copy(gemmaMtpEnabled = enabled && current.gpuBackendEnabled))
}
```

Persist all fields:

```kotlin
properties.setProperty(KEY_STREAM_RESPONSES_ENABLED, settings.streamResponsesEnabled.toString())
properties.setProperty(KEY_GPU_BACKEND_ENABLED, settings.gpuBackendEnabled.toString())
properties.setProperty(KEY_GEMMA_MTP_ENABLED, settings.gemmaMtpEnabled.toString())
```

Add keys:

```kotlin
const val KEY_GPU_BACKEND_ENABLED = "gpuBackendEnabled"
const val KEY_GEMMA_MTP_ENABLED = "gemmaMtpEnabled"
```

- [ ] **Step 4: Run settings tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.settings.AppSettingsRepositoryTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/settings/AppSettingsRepository.kt app/src/test/java/com/lance/litertchat/settings/AppSettingsRepositoryTest.kt
git commit -m "feat: persist inference runtime settings"
```

## Task 2: Add Inference Runtime Types

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/inference/InferenceRuntimeConfig.kt`

- [ ] **Step 1: Create runtime config types**

Create `InferenceRuntimeConfig.kt`:

```kotlin
package com.lance.litertchat.inference

enum class InferenceBackend {
    CPU,
    GPU
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
```

- [ ] **Step 2: Run compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/inference/InferenceRuntimeConfig.kt
git commit -m "feat: add inference runtime config"
```

## Task 3: Map Runtime Config To LiteRT-LM

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`
- Create: `app/src/test/java/com/lance/litertchat/inference/LiteRtChatEngineConfigTest.kt`

- [ ] **Step 1: Write pure config mapping tests**

Create `LiteRtChatEngineConfigTest.kt`:

```kotlin
package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtChatEngineConfigTest {
    @Test
    fun cpuConfigMapsToCpuBackendWithoutSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.CPU,
                speculativeDecodingEnabled = false
            )
        )

        assertTrue(mapped.backend is Backend.CPU)
        assertFalse(mapped.speculativeDecodingEnabled)
    }

    @Test
    fun gpuConfigMapsToGpuBackendWithoutSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = false
            )
        )

        assertTrue(mapped.backend is Backend.GPU)
        assertFalse(mapped.speculativeDecodingEnabled)
    }

    @Test
    fun mtpConfigMapsToGpuBackendWithSpeculativeDecoding() {
        val mapped = LiteRtBackendConfig.from(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            )
        )

        assertTrue(mapped.backend is Backend.GPU)
        assertTrue(mapped.speculativeDecodingEnabled)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.inference.LiteRtChatEngineConfigTest`

Expected: FAIL because `LiteRtBackendConfig` does not exist.

- [ ] **Step 3: Update `ChatEngine` and `LiteRtChatEngine`**

Change the interface:

```kotlin
interface ChatEngine {
    suspend fun load(
        modelFile: File,
        runtimeConfig: InferenceRuntimeConfig = InferenceRuntimeConfig.defaultCpu
    ): Result<Unit>
    suspend fun generate(prompt: String): Result<String>
    suspend fun generateStreaming(
        prompt: String,
        onPartialResponse: (String) -> Unit
    ): Result<String>
    fun cancelGeneration()
    fun release()
}
```

Add this internal helper in `LiteRtChatEngine.kt`:

```kotlin
internal data class LiteRtBackendConfig(
    val backend: Backend,
    val speculativeDecodingEnabled: Boolean
) {
    companion object {
        fun from(config: InferenceRuntimeConfig): LiteRtBackendConfig =
            LiteRtBackendConfig(
                backend = when (config.backend) {
                    InferenceBackend.CPU -> Backend.CPU()
                    InferenceBackend.GPU -> Backend.GPU()
                },
                speculativeDecodingEnabled = config.speculativeDecodingEnabled
            )
    }
}
```

Import `ExperimentalFlags`:

```kotlin
import com.google.ai.edge.litertlm.ExperimentalFlags
```

Add cached runtime state:

```kotlin
private var loadedRuntimeConfig: InferenceRuntimeConfig? = null
```

Update `load` so it accepts `runtimeConfig` and reloads when config changes:

```kotlin
override suspend fun load(
    modelFile: File,
    runtimeConfig: InferenceRuntimeConfig
): Result<Unit> = withContext(Dispatchers.Default) {
    runCatching {
        synchronized(lock) {
            require(modelFile.exists() && modelFile.isFile) { "Model file does not exist." }
            require(modelFile.name.endsWith(MODEL_EXTENSION)) {
                "Model file must end with $MODEL_EXTENSION."
            }

            val modelPath = modelFile.absolutePath
            if (
                loadedModelPath == modelPath &&
                loadedRuntimeConfig == runtimeConfig &&
                engine != null &&
                conversation != null
            ) {
                return@runCatching
            }

            if (loadedModelPath != modelPath || loadedRuntimeConfig != runtimeConfig) {
                release()
            } else if (engine == null || conversation == null) {
                release()
            }

            val backendConfig = LiteRtBackendConfig.from(runtimeConfig)
            ExperimentalFlags.enableSpeculativeDecoding = backendConfig.speculativeDecodingEnabled

            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backendConfig.backend
                )
            )

            try {
                newEngine.initialize()
                val newConversation = newEngine.createConversation()

                engine = newEngine
                conversation = newConversation
                loadedModelPath = modelPath
                loadedRuntimeConfig = runtimeConfig
            } catch (error: Throwable) {
                release()
                runCatching { newEngine.close() }
                throw error
            }
        }
    }
}
```

Reset config and speculative flag in `release()`:

```kotlin
loadedRuntimeConfig = null
ExperimentalFlags.enableSpeculativeDecoding = false
```

- [ ] **Step 4: Run inference mapping tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.inference.LiteRtChatEngineConfigTest`

Expected: PASS.

- [ ] **Step 5: Compile to find call sites**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: FAIL only where fake engines or callers still implement/call old `load(modelFile)` signature.

- [ ] **Step 6: Commit after Task 4 updates call sites**

Do not commit yet if compile is failing. Continue to Task 4, then commit both runtime and call-site updates together.

## Task 4: Wire Runtime Config And CPU Fallback In ViewModel

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Modify: `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`

- [ ] **Step 1: Update fake engine signature**

In `FakeChatEngine`, replace `loadedPaths` with:

```kotlin
val loadRequests = mutableListOf<Pair<String, InferenceRuntimeConfig>>()
```

Update fake load:

```kotlin
override suspend fun load(
    modelFile: File,
    runtimeConfig: InferenceRuntimeConfig
): Result<Unit> {
    loadRequests += modelFile.absolutePath to runtimeConfig
    return loadFailure?.let { Result.failure(it) } ?: Result.success(Unit)
}
```

Update existing assertions from `engine.loadedPaths` to:

```kotlin
assertEquals(
    listOf(modelFile.absolutePath to InferenceRuntimeConfig.defaultCpu),
    engine.loadRequests
)
```

- [ ] **Step 2: Add failing ViewModel tests for config and fallback**

Add imports:

```kotlin
import com.lance.litertchat.inference.InferenceBackend
import com.lance.litertchat.inference.InferenceRuntimeConfig
```

Add tests:

```kotlin
@Test
fun sendMessageRequestsGpuWhenSettingIsEnabled() = runTest(mainDispatcherRule.testDispatcher) {
    val modelFile = File(temporaryFolder.root, "model.litertlm")
    modelFile.writeText("model")
    val repository = ModelRepository(temporaryFolder.root)
    repository.saveMetadata(installedModel(path = modelFile.absolutePath))
    val settingsRepository = AppSettingsRepository(temporaryFolder.root)
    settingsRepository.setGpuBackendEnabled(true)
    val engine = FakeChatEngine(response = "Done")
    val viewModel = testViewModel(
        repository = repository,
        appSettingsRepository = settingsRepository,
        engine = engine
    )

    viewModel.sendMessage("Hi")
    advanceUntilIdle()

    assertEquals(
        listOf(
            modelFile.absolutePath to InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = false
            )
        ),
        engine.loadRequests
    )
}

@Test
fun sendMessageRequestsGpuMtpWhenBothSettingsAreEnabled() = runTest(mainDispatcherRule.testDispatcher) {
    val modelFile = File(temporaryFolder.root, "model.litertlm")
    modelFile.writeText("model")
    val repository = ModelRepository(temporaryFolder.root)
    repository.saveMetadata(installedModel(path = modelFile.absolutePath))
    val settingsRepository = AppSettingsRepository(temporaryFolder.root)
    settingsRepository.setGpuBackendEnabled(true)
    settingsRepository.setGemmaMtpEnabled(true)
    val engine = FakeChatEngine(response = "Done")
    val viewModel = testViewModel(
        repository = repository,
        appSettingsRepository = settingsRepository,
        engine = engine
    )

    viewModel.sendMessage("Hi")
    advanceUntilIdle()

    assertEquals(
        listOf(
            modelFile.absolutePath to InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            )
        ),
        engine.loadRequests
    )
}

@Test
fun gpuLoadFailureFallsBackToCpu() = runTest(mainDispatcherRule.testDispatcher) {
    val modelFile = File(temporaryFolder.root, "model.litertlm")
    modelFile.writeText("model")
    val repository = ModelRepository(temporaryFolder.root)
    repository.saveMetadata(installedModel(path = modelFile.absolutePath))
    val settingsRepository = AppSettingsRepository(temporaryFolder.root)
    settingsRepository.setGpuBackendEnabled(true)
    settingsRepository.setGemmaMtpEnabled(true)
    val engine = FakeChatEngine(
        response = "CPU answer",
        loadFailuresByConfig = mapOf(
            InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            ) to IllegalStateException("GPU unavailable")
        )
    )
    val viewModel = testViewModel(
        repository = repository,
        appSettingsRepository = settingsRepository,
        engine = engine
    )

    viewModel.sendMessage("Hi")
    advanceUntilIdle()

    assertEquals(
        listOf(
            modelFile.absolutePath to InferenceRuntimeConfig(
                backend = InferenceBackend.GPU,
                speculativeDecodingEnabled = true
            ),
            modelFile.absolutePath to InferenceRuntimeConfig.defaultCpu
        ),
        engine.loadRequests
    )
    assertEquals("GPU + MTP failed: GPU unavailable. Fell back to CPU.", viewModel.state.value.runtimeStatus.fallbackReason)
    assertEquals(listOf(ChatMessage("user", "Hi"), ChatMessage("assistant", "CPU answer")), viewModel.state.value.messages)
}
```

Extend `FakeChatEngine` constructor:

```kotlin
private val loadFailuresByConfig: Map<InferenceRuntimeConfig, Throwable> = emptyMap()
```

Use it in fake load before `loadFailure`:

```kotlin
loadFailuresByConfig[runtimeConfig]?.let { return Result.failure(it) }
```

- [ ] **Step 3: Run tests to verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.ui.AppViewModelTest`

Expected: FAIL because `runtimeStatus` and runtime settings wiring do not exist yet.

- [ ] **Step 4: Add runtime state to `AppState`**

Import runtime types:

```kotlin
import com.lance.litertchat.inference.InferenceBackend
import com.lance.litertchat.inference.InferenceRuntimeConfig
import com.lance.litertchat.inference.InferenceRuntimeStatus
```

Add fields:

```kotlin
val gpuBackendEnabled: Boolean = false,
val gemmaMtpEnabled: Boolean = false,
val runtimeStatus: InferenceRuntimeStatus = InferenceRuntimeStatus()
```

Initialize from settings in the `AppViewModel` constructor. Store `val initialSettings = appSettingsRepository.load()` once, then use it for all settings fields.

- [ ] **Step 5: Add config helpers to `AppViewModel`**

Add:

```kotlin
private fun requestedRuntimeConfig(): InferenceRuntimeConfig {
    val settings = appSettingsRepository.load()
    return if (settings.gpuBackendEnabled) {
        InferenceRuntimeConfig(
            backend = InferenceBackend.GPU,
            speculativeDecodingEnabled = settings.gemmaMtpEnabled
        )
    } else {
        InferenceRuntimeConfig.defaultCpu
    }
}

private suspend fun loadModelWithFallback(model: ModelMetadata): Result<InferenceRuntimeConfig> {
    val requested = requestedRuntimeConfig()
    val modelFile = File(model.absolutePath)
    val firstLoad = engine.load(modelFile, requested)
    if (firstLoad.isSuccess) {
        mutableState.update {
            it.copy(runtimeStatus = InferenceRuntimeStatus(requested = requested, active = requested))
        }
        return Result.success(requested)
    }

    if (requested.backend == InferenceBackend.CPU) {
        return Result.failure(firstLoad.exceptionOrNull() ?: IllegalStateException("Model load failed"))
    }

    val firstError = firstLoad.exceptionOrNull()
    engine.release()
    val fallback = InferenceRuntimeConfig.defaultCpu
    val fallbackLoad = engine.load(modelFile, fallback)
    if (fallbackLoad.isSuccess) {
        val reason = "${requested.label} failed: ${firstError?.message ?: "Model load failed"}. Fell back to CPU."
        mutableState.update {
            it.copy(runtimeStatus = InferenceRuntimeStatus(requested = requested, active = fallback, fallbackReason = reason))
        }
        return Result.success(fallback)
    }

    val fallbackError = fallbackLoad.exceptionOrNull()
    return Result.failure(
        IllegalStateException(
            "${requested.label} failed: ${firstError?.message ?: "Model load failed"}. CPU fallback failed: ${fallbackError?.message ?: "Model load failed"}"
        )
    )
}
```

Replace `engine.load(File(model.absolutePath)).fold(` with:

```kotlin
loadModelWithFallback(model).fold(
```

The success branch does not need the loaded config value.

- [ ] **Step 6: Add settings mutators**

Add:

```kotlin
fun setGpuBackendEnabled(enabled: Boolean) {
    appSettingsRepository.setGpuBackendEnabled(enabled)
    refreshSettingsState()
}

fun setGemmaMtpEnabled(enabled: Boolean) {
    appSettingsRepository.setGemmaMtpEnabled(enabled)
    refreshSettingsState()
}

private fun refreshSettingsState() {
    val settings = appSettingsRepository.load()
    mutableState.update {
        it.copy(
            streamResponsesEnabled = settings.streamResponsesEnabled,
            gpuBackendEnabled = settings.gpuBackendEnabled,
            gemmaMtpEnabled = settings.gemmaMtpEnabled
        )
    }
}
```

Update `setStreamResponsesEnabled` to call `refreshSettingsState()`.

- [ ] **Step 7: Run ViewModel tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.ui.AppViewModelTest`

Expected: PASS.

- [ ] **Step 8: Run inference tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.inference.LiteRtChatEngineConfigTest`

Expected: PASS.

- [ ] **Step 9: Commit runtime wiring**

```bash
git add app/src/main/java/com/lance/litertchat/inference app/src/test/java/com/lance/litertchat/inference app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt
git commit -m "feat: wire gpu mtp inference runtime"
```

## Task 5: Add MTP Model Constant

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`
- Modify: `app/src/test/java/com/lance/litertchat/model/ModelConstantsTest.kt`

- [ ] **Step 1: Add failing model constant test**

Add:

```kotlin
@Test
fun mtpModelUrlUsesGemma4E4bLitertLm() {
    assertEquals(
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        ModelConstants.GEMMA_4_E4B_MTP_MODEL_URL
    )
}
```

- [ ] **Step 2: Run model tests to verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.model.ModelConstantsTest`

Expected: FAIL because `GEMMA_4_E4B_MTP_MODEL_URL` does not exist.

- [ ] **Step 3: Add model constant**

In `ModelConstants.kt`, add:

```kotlin
const val GEMMA_4_E4B_MTP_MODEL_URL =
    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
```

- [ ] **Step 4: Run model tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.model.ModelConstantsTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/model/ModelConstants.kt app/src/test/java/com/lance/litertchat/model/ModelConstantsTest.kt
git commit -m "feat: add gemma mtp model url"
```

## Task 6: Add Settings UI Controls

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/App.kt`

- [ ] **Step 1: Extend `SettingsScreen` parameters**

Add callbacks:

```kotlin
onGpuBackendChanged: (Boolean) -> Unit,
onGemmaMtpChanged: (Boolean) -> Unit
```

- [ ] **Step 2: Add settings rows**

In the `Generation` card, after `Stream responses`, add:

```kotlin
SettingSwitchRow(
    title = "Use GPU backend",
    help = "Run LiteRT-LM with the Android GPU backend when available.",
    checked = state.gpuBackendEnabled,
    onCheckedChange = onGpuBackendChanged
)
SettingSwitchRow(
    title = "Enable Gemma 4 MTP",
    help = "Use speculative decoding for MTP-capable Gemma 4 LiteRT-LM models.",
    checked = state.gemmaMtpEnabled,
    enabled = state.gpuBackendEnabled,
    onCheckedChange = onGemmaMtpChanged
)
```

Update `SettingSwitchRow`:

```kotlin
private fun SettingSwitchRow(
    title: String,
    help: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
)
```

Pass `enabled` into `Switch`.

- [ ] **Step 3: Wire callbacks in `App.kt`**

Where `SettingsScreen` is called, pass:

```kotlin
onGpuBackendChanged = viewModel::setGpuBackendEnabled,
onGemmaMtpChanged = viewModel::setGemmaMtpEnabled
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt app/src/main/java/com/lance/litertchat/App.kt
git commit -m "feat: expose gpu mtp settings"
```

## Task 7: Add Runtime Diagnostics

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`

- [ ] **Step 1: Add diagnostic rows**

In `DiagnosticsScreen`, add rows near installed model details:

```kotlin
DiagnosticRow("Requested runtime", state.runtimeStatus.requested.label)
DiagnosticRow("Active runtime", state.runtimeStatus.active.label)
DiagnosticRow("Runtime fallback", state.runtimeStatus.fallbackReason ?: "None")
```

- [ ] **Step 2: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt
git commit -m "feat: show inference runtime diagnostics"
```

## Task 8: Full Verification

**Files:**
- Verify all modified app files.

- [ ] **Step 1: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Compile debug app**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 3: Manual device check**

On OPPO Reno11 5G:

1. Install debug build.
2. Confirm default settings show GPU off and MTP off.
3. Run a CPU chat with the current model.
4. Download or import `gemma-4-E4B-it.litertlm`.
5. Enable GPU, run a chat, and confirm diagnostics show requested and active runtime `GPU`.
6. Enable Gemma 4 MTP, run a chat, and confirm diagnostics show requested and active runtime `GPU + MTP`.
7. If GPU or MTP fails, confirm chat falls back to CPU and diagnostics show the fallback reason.

- [ ] **Step 4: Commit any verification fixes**

If verification requires fixes, commit them with a targeted message such as:

```bash
git add app/src/main/java app/src/test/java
git commit -m "fix: stabilize gpu mtp runtime fallback"
```

## Self-Review

- Spec coverage: The plan covers settings, runtime config, LiteRT-LM mapping, fallback, model URL, UI, diagnostics, and verification.
- Placeholder scan: No task relies on unspecified implementation details.
- Type consistency: `InferenceRuntimeConfig`, `InferenceBackend`, and `InferenceRuntimeStatus` are introduced before use and reused consistently.
