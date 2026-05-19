# Keyboard IME Integration Design

**Date:** 2026-05-20  
**Branch:** llamacpp  
**Status:** Approved

## Overview

Add an AI assistant panel to the existing app's custom keyboard (IME). A logo button in the keyboard toolbar opens a panel that replaces the keyboard area. The user types a prompt, the AI responds with streaming tokens, and the response can be inserted directly into the focused text field or copied to clipboard. If the app selects text before opening the panel, the panel auto-detects it and pre-fills a rewrite prompt.

---

## Architecture

All new code lives inside the existing `app` module — no new Gradle module.

```
com.lance.llamacppchat
├── keyboard/
│   ├── InferenceService.kt        ← bound service, owns model lifecycle
│   ├── LlamaCppKeyboardService.kt ← InputMethodService (the keyboard)
│   └── KeyboardPanel.kt           ← Compose UI inside the keyboard
└── (existing aidl/ or new)
    ├── IInferenceService.aidl     ← generate / cancel / isModelLoaded / isBusy
    └── IInferenceCallback.aidl    ← onToken / onComplete / onError / onModelLoading / onModelReady
```

`InferenceService` calls `AiChat.getInferenceEngine(applicationContext)`, which returns the same singleton engine instance the main app uses. The model therefore loads only once regardless of whether the main app or the keyboard triggered it.

`LlamaCppKeyboardService` binds to `InferenceService` with `BIND_AUTO_CREATE`. Android starts the service automatically when the IME connects.

`AndroidManifest.xml` additions:
- `<service android:name=".keyboard.InferenceService" android:exported="false" />`
- `<service android:name=".keyboard.LlamaCppKeyboardService" android:permission="android.permission.BIND_INPUT_METHOD">` with `<intent-filter>` for `android.view.InputMethod` and `<meta-data>` pointing to `res/xml/method.xml`

---

## IPC Layer

### IInferenceCallback.aidl
```aidl
oneway interface IInferenceCallback {
    void onToken(String token);
    void onComplete();
    void onError(String message);
    void onModelLoading();
    void onModelReady();
}
```

### IInferenceService.aidl
```aidl
interface IInferenceService {
    void generate(String prompt, IInferenceCallback callback);
    void cancel();
    boolean isModelLoaded();
    boolean isBusy();
}
```

### InferenceService behaviour

| Condition | Action |
|-----------|--------|
| Model not loaded | Call `callback.onModelLoading()`, fire `startActivity(MainActivity, FLAG_ACTIVITY_NEW_TASK)`, load model, call `callback.onModelReady()` |
| Engine busy (main app generating) | Call `callback.onError("Engine is busy — please wait")` |
| Ready | Stream via `generateStreaming()` → `onToken()` per chunk → `onComplete()` |
| `cancel()` called | Call `cancelGeneration()` on the engine |

The service calls `stopSelf()` once all clients unbind — no persistent background drain.

---

## IME Service

`LlamaCppKeyboardService` extends `InputMethodService`.

### Keyboard design
This IME shows **only the AI panel** — there are no typing keys. It is a purpose-built "AI assistant" keyboard, not a replacement for Gboard. The intended workflow:

1. User is typing in any app (using Gboard or their default keyboard)
2. They tap the keyboard selector icon (globe/keyboard icon in the Android nav bar) and switch to "LlamaCpp Chat"
3. The AI panel opens immediately
4. They ask their question, get a response, tap Insert or Copy
5. They switch back to Gboard via the same keyboard selector to continue typing

This avoids implementing a full QWERTY layout and keeps the scope focused.

### Panel lifecycle
`onCreateInputView()` returns a `ComposeView` that fills the keyboard height (~280dp). The panel is the only view — no toggle needed.

### Selected text detection
Immediately on panel open:
```kotlin
val selected = currentInputConnection?.getSelectedText(0)?.toString()
```
If non-empty, pre-fill the input field with `"Rewrite this: $selected"` and show a label "Selected text detected".

---

## Panel UI

Rendered by `KeyboardPanel.kt` as a `@Composable`. Uses existing design tokens from `UiKit.kt` (`AppTheme`, `AppCard`, `AppAccent`, `StopButton`, etc.) — no new tokens.

### State machine
```
Idle ──► Loading ──► Idle (model ready) ──► Generating ──► Done
                                                               │
                                          (user taps Ask again)◄─┘
```

### State descriptions

**Idle**
- Single-line text input (expands to 3 lines max)
- "Ask" primary button
- If selected text detected: input pre-filled, label "Selected text detected" above field

**Loading**
- Centered spinner
- Status message cycling: `"Starting app…"` → `"Loading model…"` → `"Almost ready…"`
- Transitions driven by `onModelLoading()` → `onModelReady()` AIDL callbacks

**Generating**
- Response text area (scrollable, streaming tokens appended)
- `StopButton` from `UiKit.kt` — calls `InferenceService.cancel()`

**Done**
- Full response displayed in scrollable text area
- **Insert** button — `currentInputConnection?.commitText(response, 1)` — types response into focused field
- **Copy** button — writes to `ClipboardManager`, shows brief `"Copied"` inline label
- "Ask again" link resets to Idle, clears response

---

## Error handling

| Error | Display |
|-------|---------|
| Engine busy | `WarningBanner` with "Engine is busy — please wait" |
| No model installed | `WarningBanner` with "No model installed. Open LlamaCpp Chat to download one." |
| Generation failed | `WarningBanner` with error message; Insert/Copy hidden |

Uses existing `WarningBanner` composable from `UiKit.kt`.

---

## Files to create

| File | Purpose |
|------|---------|
| `app/src/main/aidl/com/lance/llamacppchat/IInferenceService.aidl` | Service interface |
| `app/src/main/aidl/com/lance/llamacppchat/IInferenceCallback.aidl` | Streaming callback interface |
| `app/src/main/java/com/lance/llamacppchat/keyboard/InferenceService.kt` | Bound service |
| `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt` | InputMethodService |
| `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt` | Compose panel UI |
| `app/src/main/res/xml/method.xml` | IME metadata |

## Files to modify

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | Add two `<service>` entries |
| `app/build.gradle.kts` | No changes expected (AIDL enabled by default in AGP) |

---

## Out of scope

- Keyboard layout customisation (keys, swipe typing) — this is a panel-only feature; the base keyboard remains the system default
- Persistent chat history in the panel — one-shot only
- Image input from the keyboard panel
- Multi-language prompt support
