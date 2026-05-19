# llama.cpp GGUF Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace LiteRT-LM `.litertlm` inference with embedded llama.cpp GGUF inference using `unsloth/Qwen3.5-0.8B-GGUF` `UD-Q4_K_XL`.

**Architecture:** Reuse the vendored `third_party/llama.cpp/examples/llama.android/lib` Android library as the native llama.cpp wrapper and adapt the app's existing `ChatEngine` interface to call it. Keep the Compose UI, chat history, memory, formatter, download/import, and repository shape, but change model format and runtime labels from LiteRT-LM to llama.cpp GGUF.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle Android modules, JNI/CMake via vendored llama.cpp, GGUF model files, Kotlin coroutines/Flow.

---

## File Structure

- Modify `settings.gradle.kts`: include a local `:llama-android-lib` module from `third_party/llama.cpp/examples/llama.android/lib`.
- Modify `app/build.gradle.kts`: remove `litertlm-android`, depend on `:llama-android-lib`, set native ABI expectations if needed.
- Create `app/src/main/java/com/lance/litertchat/inference/LlamaCppChatEngine.kt`: adapter from app `ChatEngine` to `com.arm.aichat.InferenceEngine`.
- Modify `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`: remove after replacement or leave unused until tests are migrated.
- Modify `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`: default URL and extension become GGUF.
- Modify `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`: validate `.gguf`.
- Modify `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`: simplify runtime config to llama.cpp CPU, remove LiteRT fallback assumptions, report true-ish chars/word timing from generation wall time.
- Modify `app/src/main/java/com/lance/litertchat/App.kt`: instantiate `LlamaCppChatEngine`.
- Modify UI strings in `ModelManagerScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`, `README.md`.
- Update tests that assert `.litertlm`, LiteRT backend mapping, and default URLs.

## Tasks

### Task 1: Add GGUF constants and downloader validation

- [ ] Write failing tests in `ModelConstantsTest.kt` and `ModelDownloaderTest.kt` expecting `.gguf` and the Unsloth URL.
- [ ] Run targeted tests and verify failures.
- [ ] Change constants and validation.
- [ ] Run targeted tests and verify pass.

### Task 2: Add llama.cpp Android module dependency

- [ ] Include `third_party/llama.cpp/examples/llama.android/lib` as `:llama-android-lib`.
- [ ] Add `implementation(project(":llama-android-lib"))` to app.
- [ ] Remove `com.google.ai.edge.litertlm:litertlm-android`.
- [ ] Run `assembleDebug`; if CMake/SDK versions fail, patch the library module minimally to match app compile settings.

### Task 3: Implement `LlamaCppChatEngine`

- [ ] Create tests for app engine behavior using a fake wrapper if needed.
- [ ] Add `LlamaCppChatEngine` that loads GGUF, sends prompts, streams token chunks, and cancels/cleans up.
- [ ] Use context size `1024`, threads are controlled by the vendored native wrapper's 2-4 thread range, matching the Termux `-t 4` intent on phones with enough cores.
- [ ] Wire `App.kt` to construct this engine.

### Task 4: Remove LiteRT-specific runtime controls

- [ ] Update `AppViewModel` runtime status to report llama.cpp CPU.
- [ ] Keep settings toggles that are still useful; remove GPU/NPU/MTP UI and tests.
- [ ] Update diagnostics strings to llama.cpp / GGUF.

### Task 5: Update model manager and docs

- [ ] Change model manager text from `.litertlm` to `.gguf`.
- [ ] Update README model instructions and performance note.
- [ ] Run full unit tests and debug build.

## Self-Review

- Spec coverage: The plan replaces the runtime, changes model format, uses Unsloth `UD-Q4_K_XL`, removes LiteRT-LM, and preserves existing UI/state features.
- Placeholder scan: No unspecified implementation placeholders remain; native build failures are explicitly handled by patching the local module minimally.
- Type consistency: `ChatEngine` remains the app boundary; `LlamaCppChatEngine` is the new implementation.
