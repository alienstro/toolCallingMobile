# Llama.cpp Android Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app's LiteRT-LM integration with llama.cpp-backed GGUF inference on Android using the Kotlin-LlamaCpp binding.

**Architecture:** Keep the existing app shape: Compose UI, `AppViewModel`, model repository, downloader, prompt formatter, and `ChatEngine` interface. Swap the engine implementation and model contract from `.litertlm`/LiteRT to `.gguf`/llama.cpp, while preserving tests around download/import/chat lifecycle.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, OkHttp, Kotlin coroutines, JUnit, `io.github.ljcamargo:llamacpp-kotlin:0.4.0`.

---

### Task 1: Convert Model Contract To GGUF

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`
- Modify: `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`
- Modify: model/downloader/view-model tests under `app/src/test/java/com/lance/litertchat`

- [ ] Write failing tests that expect `.gguf` URLs and reject non-GGUF model URLs.
- [ ] Run model and downloader tests and confirm failures mention `.litertlm` expectations.
- [ ] Change `MODEL_EXTENSION` to `.gguf`, update default GGUF URL, update error text, and remove LiteRT hardware warning copy.
- [ ] Run the same tests and confirm they pass.

### Task 2: Replace LiteRT Engine With Llama.cpp Engine

**Files:**
- Delete: `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`
- Create: `app/src/main/java/com/lance/litertchat/inference/LlamaCppChatEngine.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Modify: `app/build.gradle.kts`

- [ ] Write/adjust tests so default `AppViewModel` wiring no longer references `LiteRtChatEngine`.
- [ ] Replace LiteRT Maven dependency with `io.github.ljcamargo:llamacpp-kotlin:0.4.0`.
- [ ] Implement `LlamaCppChatEngine` behind the existing `ChatEngine` interface using `LlamaHelper`, `Uri.fromFile(modelFile).toString()`, `load(..., contextLength = 2048)`, `predict(...)`, and `LLMEvent` streaming.
- [ ] Ensure `cancelGeneration()` aborts prediction and `release()` releases native resources.

### Task 3: Update User-Facing LiteRT Copy

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`
- Modify: `README.md`

- [ ] Replace LiteRT labels, empty states, and README instructions with llama.cpp/GGUF language.
- [ ] Keep package names unchanged to avoid a broad Android namespace migration.

### Task 4: Verification

**Files:**
- All touched source and test files.

- [ ] Run `.\gradlew.bat testDebugUnitTest`.
- [ ] Run `.\gradlew.bat assembleDebug`.
- [ ] Search for remaining LiteRT references in app source and report any intentionally retained historical docs.
