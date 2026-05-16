# Key-Value Memory Design

## Goal

Add lightweight persistent memory to the Android LiteRT chat app without filling the 1024-token mobile context window.

## Decision

Use a local key-value memory store. Do not add conversation summaries and do not include recent conversation in the first version. The model prompt will contain the active prompt formatter, a small capped memory block, and the latest user message only.

Example prompt shape:

```text
You are a helpful mobile assistant.

Memory:
- user.name: Lance
- user.prefers: concise Android-focused answers
- project.current: local LiteRT Android chat app

User message:
How should I keep memory small?
```

## Architecture

Create a `MemoryRepository` in `com.lance.litertchat.memory`, backed by app-private `settings/memories.properties`. The repository owns persistence, key cleanup, value cleanup, ordering, duplicate-key replacement, and hard limits.

`AppViewModel` loads memories into `AppState`, exposes create/update/delete actions, and injects a capped relevant memory block in `promptForModel()`. `SettingsScreen` adds a small memory editor below the existing generation and formatter controls.

## Memory Rules

Each memory has a stable key, a value, and an update timestamp. Keys are lowercase, trimmed, whitespace-normalized to dots, and limited to 48 characters. Values are trimmed and limited to 160 characters. The repository stores at most 40 memories.

Prompt injection is capped at 6 memory items. Always include `user.name` and `user.prefers` when present. Include additional memories whose key or value contains words from the latest user prompt. Fill remaining slots with most recently updated memories.

## Error Handling

Blank keys or blank values are ignored. Updating or deleting a missing key is a no-op. Corrupt or incomplete persisted records are skipped during load rather than crashing app startup.

## Testing

Add repository tests for save/load, key cleanup, duplicate replacement, deletion, max value length, and corrupt-record skipping. Add ViewModel tests for initial memory loading, memory actions, and prompt injection. Existing prompt formatter tests should be updated so formatter-only behavior still passes when no memories exist.
