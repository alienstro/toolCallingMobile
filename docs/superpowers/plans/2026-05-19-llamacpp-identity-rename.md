# LlamaCpp Identity Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app identity from LiteRT Chat to LlamaCpp Chat while preserving the existing llama.cpp GGUF runtime.

**Architecture:** Keep runtime behavior unchanged. Rename the Android namespace/application ID, Kotlin packages, source paths, tests, and user-facing labels from `litertchat`/LiteRT Chat to `llamacppchat`/LlamaCpp Chat.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose Material 3, llama.cpp Android library.

---

### Task 1: Android Package Identity

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Change namespace and application ID to `com.lance.llamacppchat`.
- [ ] Update manifest package-sensitive provider authority references if present.

### Task 2: Kotlin Package Rename

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/**`
- Modify: `app/src/test/java/com/lance/litertchat/**`

- [ ] Replace package declarations and imports from `com.lance.litertchat` to `com.lance.llamacppchat`.
- [ ] Move source and test directories to `app/src/main/java/com/lance/llamacppchat` and `app/src/test/java/com/lance/llamacppchat`.

### Task 3: User-Facing Identity

**Files:**
- Modify: Android string resources and Compose UI text.
- Modify: Tests that assert app text.

- [ ] Replace remaining LiteRT Chat wording with LlamaCpp Chat.
- [ ] Keep neutral historical test fixture text only where it is data content and not product identity.

### Task 4: Verification

**Files:**
- All touched files.

- [ ] Run `.\gradlew.bat testDebugUnitTest assembleDebug`.
- [ ] Fix any compile, resource, or stale import failures.
