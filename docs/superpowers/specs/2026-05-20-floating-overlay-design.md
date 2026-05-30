# Floating AI Overlay Design

**Date:** 2026-05-20
**Branch:** llamacpp
**Status:** Approved

## Overview

Replace the custom IME keyboard with a floating overlay button that appears over any app. Tapping the button opens an AI panel above the active keyboard (Gboard or any other). A second entry point — "Ask xChat" in the Android text selection menu — opens the main app with selected text pre-filled for a full conversation.

---

## Architecture

All new code lives inside the existing `app` module.

```
com.lance.llamacppchat
├── overlay/
│   ├── OverlayService.kt       ← ForegroundService: manages WindowManager views, binds to InferenceService
│   ├── OverlayButton.kt        ← Compose UI: draggable floating button
│   └── OverlayPanel.kt         ← Compose UI: bottom sheet panel above keyboard
└── ProcessTextActivity.kt      ← Thin activity: receives ACTION_PROCESS_TEXT, pre-fills MainActivity chat
```

**Removed from previous IME implementation:**
- `keyboard/LlamaCppKeyboardService.kt`
- `keyboard/KeyboardPanel.kt` (QWERTY version)
- `res/xml/method.xml`
- IME `<service>` manifest entries

**Reused unchanged:**
- `keyboard/InferenceService.kt`
- `keyboard/KeyboardPanelState.kt`
- `IInferenceService.aidl` / `IInferenceCallback.aidl`
- `ui/UiKit.kt` (all design tokens)

---

## Entry Points

### Entry Point 1: Floating Button → Overlay Panel

`OverlayService` starts as a foreground service when the user enables the toggle in the main app. It adds two `ComposeView`s to `WindowManager` using `TYPE_APPLICATION_OVERLAY`:

1. **Floating button** — always visible, draggable, snaps to nearest screen edge on release, position persisted in `SharedPreferences`
2. **Bottom sheet panel** — hidden by default, shown when button is tapped

The panel is positioned just above the active keyboard. `OverlayService` listens for keyboard insets on a transparent full-screen helper window (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`) to detect current keyboard height, then positions the panel window at `Gravity.BOTTOM` with `y = keyboardHeight`.

`OverlayService` binds to `InferenceService` with `BIND_AUTO_CREATE` and forwards generate/cancel calls and all five AIDL callbacks (`onToken`, `onComplete`, `onError`, `onModelLoading`, `onModelReady`) to the panel's Compose state via `Handler(Looper.getMainLooper())`.

### Entry Point 2: Text Selection → Main App

`ProcessTextActivity` is registered for `android.intent.action.PROCESS_TEXT` in `AndroidManifest.xml`. When the user selects text in any app and taps "Ask xChat" in the selection menu, Android delivers the selected text via `Intent.EXTRA_PROCESS_TEXT`. `ProcessTextActivity` launches `MainActivity` with the text as an extra, then finishes itself. `MainActivity` reads the extra in `onNewIntent`/`onCreate` and pre-fills the chat input with `"Rewrite this: $selectedText"`.

---

## Floating Button

- **Default position:** bottom-right, 16dp from edges
- **Drag:** user can drag anywhere on screen; on finger release, snaps to nearest vertical edge (left or right), maintaining the same vertical position
- **Position persistence:** saved to `SharedPreferences` as `(edge: LEFT|RIGHT, yFraction: Float)` so position survives restarts
- **Appearance:** 52dp circular button, `AppAccent` background, app icon or brain icon in white
- **Tap:** toggles panel open/closed
- **Long-press:** no action (reserved for future)

---

## Overlay Panel UI

Bottom sheet anchored at `Gravity.BOTTOM`, full screen width, `wrapContentHeight`. Positioned above the keyboard.

### Layout

```
┌──────────────────────────────────────┐
│           ─────  (drag handle)       │
│  Ask AI…                    [Ask]   │  ← Idle: text input (expands to 3 lines)
│                                      │
│  [response text, scrollable]         │  ← Generating / Done
│                                      │
│  [Copy]          [Ask again]         │  ← Done state only
└──────────────────────────────────────┘
```

### State machine (reuses `KeyboardPanelState`)

| State | Panel content |
|-------|--------------|
| `Idle` | Text input + Ask button; if launched from text selection: pre-filled with "Rewrite this: …" + "Selected text detected" label |
| `Loading` | Centered spinner + cycling messages ("Starting app…" → "Loading model…" → "Almost ready…") |
| `Generating` | Scrollable streaming text + StopButton |
| `Done` | Full response + Copy button (copies to clipboard) + Ask again link |
| `Error` | `WarningBanner` with error message + Try again button |

**Note:** No "Insert" button — the overlay is not an IME and cannot call `commitText`. Copy + manual paste is the insertion mechanism.

### Dismissal

- Tap the drag handle or swipe down → panel hides, button remains
- Tap the floating button again → panel hides
- Back button press → panel hides (handled via `OnBackPressedDispatcher` or `KeyEvent` on the panel window)

---

## Main App Toggle

A new switch row in the main app's existing settings/screen:

- **Label:** "AI Overlay" with subtitle "Float an AI button over any app"
- **On toggle ON:**
  1. Check `Settings.canDrawOverlays(context)` — if not granted, navigate to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` and return (the toggle stays off until permission is confirmed)
  2. If granted, call `startForegroundService(Intent(context, OverlayService::class.java))`
- **On toggle OFF:** call `stopService(Intent(context, OverlayService::class.java))`
- **State persistence:** `SharedPreferences` boolean `overlay_enabled`; read on app start to reflect current state

### Foreground Service Notification

Required by Android for foreground services:
- **Title:** "LlamaCpp AI Overlay"
- **Text:** "Floating AI button is active"
- **Action:** "Disable" — stops the service and updates the toggle

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `android.permission.SYSTEM_ALERT_WINDOW` | Draw over other apps (`TYPE_APPLICATION_OVERLAY`) |
| `android.permission.FOREGROUND_SERVICE` | Run foreground service |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Required on API 34+ for foreground services without a specific type |

---

## Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayService.kt` | ForegroundService: WindowManager, button/panel lifecycle, InferenceService binding |
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayButton.kt` | Compose UI: draggable floating button |
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanel.kt` | Compose UI: bottom sheet panel with all states |
| `app/src/main/java/com/lance/llamacppchat/ProcessTextActivity.kt` | Receives ACTION_PROCESS_TEXT, launches MainActivity with selected text |

## Files to Modify

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | Add permissions, `OverlayService` service entry, `ProcessTextActivity` entry; remove IME service entries |
| `app/src/main/java/com/lance/llamacppchat/MainActivity.kt` | Add overlay toggle switch; handle `EXTRA_PROCESS_TEXT` in `onNewIntent`/`onCreate` |

## Files to Delete

| File | Reason |
|------|--------|
| `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt` | Replaced by overlay |
| `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt` | IME-specific QWERTY UI, replaced by OverlayPanel |
| `app/src/main/res/xml/method.xml` | IME metadata, no longer needed |

---

## Out of Scope

- Swipe-to-dismiss gesture on the panel (tap handle / back button is sufficient)
- Keyboard height animation tracking (static position based on keyboard height at open time is sufficient)
- Conversation history across panel sessions — one-shot only
- Auto-start on device boot
- Paste automation via accessibility service
