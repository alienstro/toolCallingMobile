# Tool Calling Mobile

Native Android Kotlin app for testing a LiteRT-LM chatbot on-device.

## Current Features

- Native Android app built with Kotlin and Jetpack Compose.
- Modern Material 3 utility UI based on the project `index.html` prototype:
  - top app bar with model/status line
  - compact bottom navigation
  - chat bubbles with AI avatar
  - rounded composer and stats pills
  - card-based Models, Settings, and Diagnostics screens
- Model manager for `.litertlm` files.
- Download a model from a fixed or user-entered HTTPS URL.
- Converts Hugging Face `/blob/` model URLs to `/resolve/` download URLs.
- Import a local `.litertlm` file through Android's document picker.
- Delete the installed model from app storage.
- Diagnostics screen showing device, Android version, storage, model path, model size, and last error.
- Settings screen for prompt formatter management.
- Settings toggle for streaming assistant responses while the model is generating.
- Prompt formatter CRUD:
  - create custom prompt formatters
  - edit formatter name and body
  - delete custom formatters
  - select the active formatter
  - reset the built-in default formatter
- Active prompt formatter is prepended to model prompts, but the chat transcript only shows the user's original message.
- Local LiteRT-LM chat flow using the installed model.
- Assistant responses render common markdown in a mobile-friendly format:
  - headings
  - bold text
  - bullets
  - simple markdown tables
- Chat status while generation is running:
  - Send button changes to `Processing`.
  - Assistant placeholder appears as `Processing...`.
  - When streaming is enabled, the assistant response updates while text is generated.
  - Tapping `Processing` requests generation stop.
- Compact generation stats after each response:
  - elapsed seconds
  - estimated output tokens
  - estimated tokens per second

## Notes

- Token count is currently estimated from the assistant text because the current LiteRT wrapper returns generated text, not token metadata.
- Response time measures the latest assistant response cycle, not the whole chat history. It includes model load time when the model is not already loaded.
- Stop behavior cancels the app coroutine, calls LiteRT `cancelProcess()`, and releases the LiteRT engine. If a native call is blocking, stopping may take effect after that native call returns.
- Hugging Face model search is planned later. For now, paste a direct `.litertlm` URL.
- Downloaded/imported models and prompt formatters are stored in app-private storage. Android deletes this app-private data when the app is uninstalled.

## Test On Phone

Connect an Android phone with USB debugging enabled, then run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

Expected device status:

```text
device
```

Install the debug build:

```powershell
.\gradlew.bat :app:installDebug
```

Open `LiteRT Chat` on the phone and test:

- Download a `.litertlm` model.
- Import a `.litertlm` model.
- Delete the installed model.
- Send a chat prompt.
- Tap `Processing` during generation to stop the response.
- Open Settings and create, edit, select, and delete prompt formatters.
- Turn streaming responses on or off in Settings.
- Confirm assistant markdown is rendered cleanly instead of showing raw markdown syntax.
- Check diagnostics if model load or generation fails.

## Build And Verify

Run unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```
