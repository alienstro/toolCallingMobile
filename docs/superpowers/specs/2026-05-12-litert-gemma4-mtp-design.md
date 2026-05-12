# LiteRT Gemma 4 MTP Design

Date: 2026-05-12

## Goal

Add an opt-in LiteRT-LM runtime mode that can run Gemma 4 MTP through GPU speculative decoding when the installed model and Android device support it, while preserving the current CPU-only chat path as the safe default.

## Context

LiteRT-LM v0.11.0 documents Gemma 4 Multi-token Prediction support through speculative decoding. The CLI example uses the Gemma 4 E4B `.litertlm` model, `--backend=gpu`, and `--enable-speculative-decoding=true`.

The current Android app already pins `com.google.ai.edge.litertlm:litertlm-android:0.11.0`, and the local AAR exposes:

- `Backend.GPU()`
- `ExperimentalFlags.enableSpeculativeDecoding`
- `Backend.CPU()`, which the app currently uses

The current app does not expose backend selection. `LiteRtChatEngine` always creates `EngineConfig(..., backend = Backend.CPU())`.

## Product Behavior

Settings gets two new generation controls:

- `Use GPU backend`
- `Enable Gemma 4 MTP`

MTP can only be enabled when GPU is enabled. Turning GPU off also turns MTP off. The defaults remain `Use GPU backend = false` and `Enable Gemma 4 MTP = false`.

The chat runtime reads settings when a generation starts. If GPU is enabled, it creates the LiteRT-LM engine with `Backend.GPU()`. If MTP is also enabled, it sets `ExperimentalFlags.enableSpeculativeDecoding = true` before engine initialization. Otherwise it sets that flag to `false`.

The active runtime mode is exposed in app state and diagnostics:

- `CPU`
- `GPU`
- `GPU + MTP`

If GPU or GPU+MTP model loading fails, the app falls back to CPU without speculative decoding for that message. The user sees a warning that includes the failed mode and the fallback mode, while the message still completes if CPU generation succeeds.

## Model Handling

The existing default E2B model remains valid for baseline local chat. Add the Gemma 4 E4B LiteRT-LM URL as the recommended MTP model:

```text
https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm
```

The app should not block custom imports or downloads. It should warn that MTP is intended for Gemma 4 MTP-capable `.litertlm` bundles and may fall back or fail on other models.

## Architecture

Introduce a small inference configuration boundary so settings do not leak SDK-specific details through the view model:

- `InferenceBackend`: app enum with `CPU` and `GPU`.
- `InferenceRuntimeConfig`: data class containing `backend` and `speculativeDecodingEnabled`.
- `InferenceRuntimeStatus`: data class containing the requested config, active config, and optional fallback reason.

Change the `ChatEngine.load` API to accept `InferenceRuntimeConfig`. `LiteRtChatEngine` maps it to LiteRT-LM:

- `CPU` -> `Backend.CPU()`
- `GPU` -> `Backend.GPU()`
- `speculativeDecodingEnabled` -> `ExperimentalFlags.enableSpeculativeDecoding`

The engine caches the loaded model by both model path and runtime config. If either changes, it releases and reloads.

Fallback belongs in `AppViewModel`, not inside `LiteRtChatEngine`, because the UI state owns warnings and because tests can verify the fallback workflow with a fake engine.

## Error Handling

If the requested config fails to load:

1. Release any partially initialized engine.
2. If the requested backend was GPU, try CPU with speculative decoding disabled.
3. If CPU succeeds, continue generation and set a fallback warning.
4. If CPU fails too, report the original GPU error plus CPU fallback failure.

Speculative decoding is an SDK-global experimental flag. `LiteRtChatEngine` must set it before every engine initialization and reset it to `false` when releasing a speculative engine or before loading a non-speculative config.

## Testing

Automated tests should cover:

- Settings defaults and persistence for GPU and MTP.
- GPU off disables MTP.
- View model computes CPU, GPU, and GPU+MTP requested configs from settings.
- GPU load failure falls back to CPU and preserves the user message flow.
- Engine config factory selects `Backend.CPU()` and `Backend.GPU()`.

Manual device verification should cover:

- CPU baseline with current E2B model.
- GPU mode with E4B model.
- GPU + MTP mode with E4B model.
- GPU/MTP fallback on an unsupported model or driver failure.

## Out of Scope

This change does not add tool calling. It does not add MediaTek NPU/NeuroPilot support. It does not bundle a model in the APK. It does not attempt to parse LiteRT-LM internal MTP counters unless the Android SDK exposes them in a public API later.
