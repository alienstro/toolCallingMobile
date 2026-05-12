# RunAnywhere Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace LiteRT-LM with the official RunAnywhere Kotlin SDK and make GGUF the only supported model format.

**Architecture:** Keep the existing native Android Kotlin app boundaries. `AppViewModel` continues to depend on the `ChatEngine` interface, while the concrete engine changes to `RunAnywhereChatEngine`; model storage and downloads switch from `.litertlm` to `.gguf`.

**Tech Stack:** Android Kotlin, Jetpack Compose, Kotlin coroutines/Flow, RunAnywhere Kotlin SDK `0.20.6`, llama.cpp GGUF backend, JUnit.

---

### Task 1: Switch Model Format Tests To GGUF

**Files:**
- Modify: `app/src/test/java/com/lance/litertchat/model/ModelConstantsTest.kt`
- Modify: `app/src/test/java/com/lance/litertchat/download/ModelDownloaderTest.kt`
- Modify: `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`

- [ ] **Step 1: Update model constants tests first**

Change `ModelConstantsTest` expectations to:

```kotlin
assertEquals(
    "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
    ModelConstants.DEFAULT_MODEL_URL
)
assertEquals(".gguf", ModelConstants.MODEL_EXTENSION)
```

- [ ] **Step 2: Update downloader tests first**

Replace `.litertlm` test URLs/files with `.gguf` and expect the rejection message:

```kotlin
assertEquals("Model URL must point to a .gguf file.", result.exceptionOrNull()?.message)
```

- [ ] **Step 3: Update view model tests first**

Replace installed/downloaded/imported model test paths from `.litertlm` to `.gguf`, and update any LiteRT wording in prompt titles to RunAnywhere-neutral wording.

- [ ] **Step 4: Run tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.model.ModelConstantsTest --tests com.lance.litertchat.download.ModelDownloaderTest --tests com.lance.litertchat.ui.AppViewModelTest
```

Expected: FAIL because production code still returns `.litertlm` constants and messages.

### Task 2: Implement GGUF Model Lifecycle

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`
- Modify: `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`

- [ ] **Step 1: Update model constants**

Set:

```kotlin
const val DEFAULT_MODEL_URL =
    "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf"
const val MODEL_EXTENSION = ".gguf"
```

- [ ] **Step 2: Update downloader validation message**

Use:

```kotlin
"Model URL must point to a ${ModelConstants.MODEL_EXTENSION} file."
```

- [ ] **Step 3: Keep import naming extension-driven**

Confirm `importModelFromUri()` still uses `ModelConstants.MODEL_EXTENSION`, so imported files become `.gguf`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the same focused test command from Task 1.

Expected: PASS for the updated tests.

### Task 3: Add RunAnywhere Kotlin SDK Dependencies

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add JitPack**

Add:

```kotlin
maven { url = uri("https://jitpack.io") }
```

- [ ] **Step 2: Replace LiteRT dependency**

Remove:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
```

Add:

```kotlin
implementation("io.github.sanchitmonga22:runanywhere-sdk-android:0.20.6")
implementation("io.github.sanchitmonga22:runanywhere-llamacpp-android:0.20.6")
```

- [ ] **Step 3: Align JVM target if needed**

If Gradle resolution or compilation fails with Java 21 target issues, change Java/Kotlin target to 17 as documented by RunAnywhere.

- [ ] **Step 4: Update launcher label**

Change:

```xml
android:label="RunAnywhere Chat"
```

- [ ] **Step 5: Run dependency compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: FAIL until LiteRT engine imports are removed in Task 4.

### Task 4: Replace LiteRT Engine With RunAnywhere Engine

**Files:**
- Delete: `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`
- Create: `app/src/main/java/com/lance/litertchat/inference/ChatEngine.kt`
- Create: `app/src/main/java/com/lance/litertchat/inference/RunAnywhereInitializer.kt`
- Create: `app/src/main/java/com/lance/litertchat/inference/RunAnywhereChatEngine.kt`
- Modify: `app/src/main/java/com/lance/litertchat/MainActivity.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`

- [ ] **Step 1: Extract `ChatEngine` interface**

Create `ChatEngine.kt` with the existing interface so tests can continue using fakes.

- [ ] **Step 2: Add `RunAnywhereInitializer`**

Initialize Android platform context, SDK environment, C++ model directory, and llama.cpp backend using Kotlin SDK APIs.

- [ ] **Step 3: Add `RunAnywhereChatEngine`**

Validate `.gguf`, register the local model with `InferenceFramework.LLAMA_CPP` and `ModelCategory.LANGUAGE`, load it through `RunAnywhere.loadLLMModel(modelId)`, use `RunAnywhere.chat(prompt)` for non-streaming generation, and collect `RunAnywhere.generateStream(prompt)` for streaming.

- [ ] **Step 4: Wire defaults**

Replace `LiteRtChatEngine()` default construction with `RunAnywhereChatEngine()` in `AppViewModel`, and call `RunAnywhereInitializer.initialize(applicationContext)` in `MainActivity.onCreate()`.

- [ ] **Step 5: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: PASS or expose exact SDK API mismatch to fix against official Kotlin docs.

### Task 5: Clean UI And Documentation Wording

**Files:**
- Modify: `README.md`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt`
- Modify: any other app source matched by `rg -n "LiteRT|litertlm|\\.litertlm|MLC|mlc" app README.md docs`

- [ ] **Step 1: Replace user-visible model format wording**

Use `.gguf`, GGUF, and RunAnywhere wording in visible strings.

- [ ] **Step 2: Update README**

Describe native Android Kotlin + RunAnywhere Kotlin SDK, GGUF model download/import, and the same phone verification workflow.

- [ ] **Step 3: Search for stale wording**

Run:

```powershell
rg -n "LiteRT|litertlm|\\.litertlm|MLC|mlc" app README.md docs
```

Expected: only historical design/spec/plan references may remain in `docs/superpowers`; no stale app or README runtime wording.

### Task 6: Full Verification

**Files:**
- No edits unless verification exposes failures.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 3: Inspect final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only RunAnywhere migration files changed; pre-existing untracked `third_party/` remains untouched.
