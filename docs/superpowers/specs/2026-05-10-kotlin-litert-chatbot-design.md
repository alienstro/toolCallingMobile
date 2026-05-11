# Kotlin LiteRT Chatbot Design

Date: 2026-05-10

## Goal

Build a native Android Kotlin application that runs a local LiteRT-LM chatbot on the user's phone. The first target device is an OPPO Reno11 5G with a MediaTek Dimensity 7050 chipset, so the first model target is the generic Gemma LiteRT-LM file rather than Qualcomm-specific variants.

The app must not bundle the model inside the APK. The user installs the app first, then downloads or imports a `.litertlm` model from inside the app. The user can also delete the installed model.

## Platform Decision

Use native Android Kotlin for the whole v1 app.

This avoids React Native bridge complexity while the main technical risk is still LiteRT-LM integration, large model download, local file storage, and on-device inference. Jetpack Compose should be used for the UI unless the generated Android project or LiteRT sample code strongly suggests another native UI pattern.

## Default Model

Use this default direct download URL for the first working version:

```text
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
```

The app should also accept pasted direct `.litertlm` URLs. If a Hugging Face URL uses `/blob/`, the app should convert it to `/resolve/` before downloading.

The app should warn when the selected filename appears hardware-specific and likely mismatched for the current phone, such as `qualcomm_sm8750` on the OPPO Reno11 5G.

## V1 Features

### Model Manager

The Model Manager is responsible for model lifecycle:

- Download the default model.
- Paste and download a direct `.litertlm` URL.
- Import a local `.litertlm` file from device storage.
- Store the selected model in app-controlled storage.
- Delete the installed model.
- Show model status, filename, source, size when known, install date, and download progress.
- Prevent chat inference when no model is installed.

### Chat

The Chat screen provides a simple chatbot experience:

- Show a message list for user and assistant messages.
- Provide a text input and send action.
- Disable sending while the model is missing, loading, or generating.
- Load the installed `.litertlm` model through LiteRT-LM.
- Send the user's prompt to the local model.
- Show the assistant response.
- Show useful loading and error states.

Streaming output is desirable but not required for the first successful version. A complete response returned after generation is acceptable for v1.

### Settings and Diagnostics

The app should include basic diagnostics needed for device testing:

- Android version.
- Device manufacturer and model.
- Available storage if easily accessible.
- Installed model path.
- Installed model filename and size.
- LiteRT-LM load/generation errors.

## Architecture

### UI Layer

Jetpack Compose screens and state holders:

- `ModelManagerScreen`
- `ChatScreen`
- `DiagnosticsScreen`
- App-level navigation between the screens

### Model Storage Layer

Handles local files and metadata:

- Resolve the app model directory.
- Save downloaded or imported `.litertlm` files.
- Persist metadata for the active model.
- Delete the active model and metadata.
- Validate model filename and extension.

### Download Layer

Handles large file download:

- Download from HTTPS direct URLs.
- Convert Hugging Face `/blob/` URLs to `/resolve/`.
- Follow redirects.
- Report progress.
- Write to a temporary file first, then move into place after success.
- Handle cancellation or failed downloads without leaving a broken active model.

### LiteRT-LM Layer

Handles inference:

- Load LiteRT-LM with the installed model path.
- Create and maintain a conversation object.
- Generate a response for a user prompt.
- Release model resources when needed.
- Return structured success or error results to the UI.

## Error Handling

The app should show user-readable errors for:

- No model installed.
- Invalid URL.
- URL does not point to a `.litertlm` file.
- Download failed.
- Not enough storage.
- Import failed.
- Model load failed.
- Generation failed.

Errors should also keep enough technical detail in diagnostics to help debug on the connected Android phone.

## Testing and Verification

Initial verification should be done on the USB-debugged OPPO Reno11 5G:

- App installs successfully.
- Default model download starts and reports progress.
- Downloaded model is saved in app-controlled storage.
- Imported `.litertlm` files are accepted.
- Delete removes the installed model and disables chat.
- Chat is disabled with no installed model.
- Chat attempts to load and run the installed generic Gemma `.litertlm` model.
- Errors are visible when inference fails.

Automated tests should cover URL normalization, file metadata handling, and model lifecycle state transitions. LiteRT-LM inference itself can be verified manually on device for v1 because it depends on the downloaded model and phone runtime behavior.

## Later Features

Add Hugging Face model discovery after v1 works:

- Search Hugging Face for LiteRT-LM compatible models.
- Browse files in a selected Hugging Face repo.
- Let the user pick a `.litertlm` file from the repo.
- Show file size and hardware compatibility hints before download.
- Recommend generic Android models for broad compatibility and hardware-specific models only when the device matches.

Other later improvements:

- Streaming assistant responses.
- Multiple installed models.
- Model versioning and checksums.
- Better hardware/backend selection.
- Tool-calling support after the simple chatbot works.
