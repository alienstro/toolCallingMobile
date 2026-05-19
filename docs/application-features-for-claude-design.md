# LiteRT Chat Mobile App Features

## App Summary

LiteRT Chat is a native Android app for running and testing LiteRT-LM chat models directly on a phone. It focuses on local model management, on-device chat, prompt formatting, memory, runtime controls, and diagnostics for debugging model behavior.

## Core Experience

- Native Android app built with Kotlin and Jetpack Compose.
- Material 3-inspired mobile interface with compact utility screens.
- Local, on-device chat flow using an installed `.litertlm` model.
- Bottom navigation for the main areas: Chat, Models, Settings, and Diagnostics.
- Top status area that communicates active model and runtime state.

## Chat Features

- Send text prompts to the active local LiteRT-LM model.
- Attach an image from the camera or image picker.
- Ask image-only prompts, which default to an image description request.
- Stream assistant responses as text arrives.
- Disable streaming to show complete responses only after generation finishes.
- Stop an in-progress generation.
- Show a temporary assistant placeholder while the model is processing.
- Render assistant responses in a mobile-friendly Markdown format:
  - headings
  - paragraphs
  - bold text
  - bullet lists
  - simple tables
- Display compact generation stats after a response:
  - estimated token count
  - elapsed generation time
  - estimated tokens per second

## Chat History

- Create a new chat session.
- Automatically title new chats from the first prompt.
- Switch between saved chat sessions.
- Delete existing chat sessions.
- Persist chat messages and sessions in app-private storage.
- Prevent history changes while generation is running.

## Model Management

- Show active model file name, path, source, and size.
- Download a `.litertlm` model from a default or pasted HTTPS URL.
- Normalize Hugging Face `/blob/` URLs into `/resolve/` download URLs.
- Import a local `.litertlm` model through Android's document picker.
- Delete the installed model from app storage.
- Show download progress and errors.
- Warn for known hardware-specific model filenames when relevant.

## Prompt Formatter Features

- Use an active prompt formatter before sending prompts to the model.
- Create custom prompt formatters.
- Edit formatter name and body.
- Select the active formatter.
- Delete custom formatters.
- Reset the built-in default formatter.
- Keep the user-facing chat transcript clean by showing the original user message, not the expanded model prompt.

## Memory Features

- Save key-value memories manually in Settings.
- Edit a memory by loading it back into the editor.
- Delete saved memories.
- Include relevant saved memories in the model prompt.
- Capture explicit memory-style user input and refresh stored memory state.
- Show stored memories in Diagnostics.

## Runtime And Generation Settings

- Toggle streamed assistant responses.
- Toggle GPU backend usage when available.
- Toggle experimental NPU backend usage for supported devices and models.
- Toggle Gemma 4 MTP speculative decoding when GPU backend is enabled.
- Track requested runtime versus active runtime.
- Fall back to CPU if a requested GPU or NPU runtime fails.
- Surface runtime fallback reasons in Diagnostics.

## Diagnostics Features

- Show high-level app health:
  - repository status
  - current error state
- Show device information:
  - manufacturer and model
  - Android version and API level
  - available app storage
- Show active model information:
  - file name
  - absolute path
  - file size
  - source
- Show runtime information:
  - requested runtime
  - active runtime
  - fallback reason
  - streaming status
  - message count
  - latest error
- Show latest generation stats when available.
- Show stored memory count and memory entries.

## Data Storage

- Downloaded and imported models are stored in app-private storage.
- Prompt formatters are stored locally in app-private storage.
- Chat history is stored locally in app-private storage.
- Memories are stored locally in app-private storage.
- Android removes this app-private data when the app is uninstalled.

## Important States For Design

- Empty chat state with no model installed.
- Empty chat state with a model installed.
- User message bubble.
- User message bubble with attached image preview.
- Assistant response bubble.
- Assistant loading or streaming bubble.
- Generation stopped state.
- Model missing state.
- Model downloading state.
- Model ready state.
- Runtime fallback warning state.
- Error state in Models and Diagnostics.
- Empty memory list.
- Active prompt formatter state.
- Disabled controls while downloading or generating.

## Main Screens

### Chat

Primary conversation surface. Includes chat history controls, message list, generation stats, image attachment preview, text composer, camera button, image picker button, and send or stop action.

### Models

Model installation and management surface. Includes active model summary, editable model URL field, download action, import action, delete action, progress state, and error state.

### Settings

Configuration surface. Includes generation toggles, runtime backend toggles, Gemma MTP toggle, memory editor, saved memory list, prompt formatter editor, and prompt formatter list.

### Diagnostics

Debugging and visibility surface. Includes health, device, model, runtime, generation, error, and memory information.

## Design Direction Notes

- The app should feel like a compact technical utility, not a marketing page.
- Prioritize scanability, clear status labels, and dense but readable controls.
- Make model readiness, generation status, runtime fallback, and error states obvious.
- Keep chat interactions comfortable for one-handed mobile use.
- Use clear separation between operational screens: Chat for use, Models for installation, Settings for configuration, Diagnostics for debugging.
