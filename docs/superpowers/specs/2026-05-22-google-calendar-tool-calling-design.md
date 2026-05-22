# Google Calendar Tool Calling — Design Spec

**Date:** 2026-05-22
**Branch:** llamacpp
**Status:** Approved

---

## Overview

Add Google Calendar read/write as the first tool in the app's tool-calling system. The local LLM detects when a user wants to interact with their calendar, emits a structured JSON tool call, and the app executes it via the Google Calendar API v3. The feature establishes a `ToolRegistry` architecture that future tools (reminders, web search, etc.) can plug into without touching the ViewModel.

Auth is handled via Google Sign-In (OAuth 2.0). Tool invocation uses structured JSON parsing — no model-specific grammar or fine-tuning required.

---

## Architecture

### New package: `com.lance.llamacppchat.tools`

| File | Purpose |
|---|---|
| `Tool.kt` | `Tool` interface + `ToolDefinition`, `ToolCall`, `ToolResult` data classes |
| `ToolRegistry.kt` | Holds registered tools, provides prompt-injectable definitions, dispatches `ToolCall` → `Tool` |
| `ToolCallParser.kt` | Scans LLM output for a `{"tool":"...","args":{...}}` JSON block |
| `calendar/GoogleCalendarClient.kt` | Google Sign-In OAuth + Calendar REST API v3 HTTP calls |
| `calendar/GoogleCalendarTool.kt` | `Tool` impl — wraps `list_events` and `create_event` operations |

### Core contracts (`Tool.kt`)

```kotlin
interface Tool {
    val definition: ToolDefinition
    suspend fun execute(args: Map<String, Any>): ToolResult
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: String  // injected verbatim into the system prompt
)

data class ToolCall(val tool: String, val args: Map<String, Any>)

data class ToolResult(val tool: String, val content: String, val isError: Boolean = false)
```

### No changes to existing interfaces

`ChatEngine`, `MemoryRepository`, `PromptFormatterRepository`, and the embedding system are untouched.

---

## AppViewModel Changes

### New dependencies

```kotlin
class AppViewModel(
    // ... existing ...
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val onRequestGoogleSignIn: () -> Unit = {}
)
```

### New AppState fields

```kotlin
data class AppState(
    // ... existing ...
    val isToolExecuting: Boolean = false,
    val googleSignedIn: Boolean = false
)
```

### Agentic loop in `sendMessage`

`sendMessage` runs up to 3 generation hops per user message:

```
generate()
  → if ToolCallParser finds a tool call:
      set isToolExecuting = true, show "Using tool..." bubble
      ToolRegistry.dispatch(toolCall)
        → if NeedsSignIn: fire onRequestGoogleSignIn, suspend until signed in, retry
        → on success: append hidden "tool" role message with result
        → on error: append error string as tool result
      set isToolExecuting = false
      generate() again with tool result in context
  → if no tool call: done
  → if 4th hop reached: stop, show error message
```

Hidden tool messages are included in the prompt but not displayed in the chat UI. The user sees only the "Using tool..." indicator during execution and the final LLM response.

### Google Sign-In event

`AppViewModel` exposes a `SharedFlow<Unit>` called `signInRequest`. `MainActivity` collects it and launches the Google Sign-In intent via `ActivityResultLauncher`. After the user completes consent, `MainActivity` calls `viewModel.handleGoogleSignInResult(account: GoogleSignInAccount?)`.

---

## Data Flow

### Read example: "What meetings do I have tomorrow?"

```
1. promptForModel() injects into system prompt:
   Current date/time: Thursday, May 22 2026, 2:41 PM
   Tools available:
     list_events(date: YYYY-MM-DD) — list calendar events for a date
     create_event(title, date: YYYY-MM-DD, time: HH:MM, duration_minutes) — create a new event

2. LLM generates:
   ```json
   {"tool":"list_events","args":{"date":"2026-05-23"}}
   ```

3. ToolCallParser extracts the ToolCall

4. GoogleCalendarTool.execute() → GET /calendars/primary/events?timeMin=...&timeMax=...

5. Tool result appended (hidden): "list_events result: Meeting with Alex 10:00 AM, Standup 2:00 PM"

6. LLM generates final response:
   "You have two meetings tomorrow: Alex at 10 AM and Standup at 2 PM."
```

### Write example: "Create me a schedule at 3pm and title it feeding cats"

```
1. Same system prompt with current date/time injected

2. LLM generates:
   ```json
   {"tool":"create_event","args":{"title":"Feeding Cats","date":"2026-05-22","time":"15:00","duration_minutes":60}}
   ```

3. GoogleCalendarTool.execute() → POST /calendars/primary/events

4. Tool result: "create_event result: Event 'Feeding Cats' created for May 22 at 3:00 PM"

5. LLM final response: "Done! I've added 'Feeding Cats' to your calendar today at 3:00 PM."
```

**Default duration:** 60 minutes when not specified by the user. Documented in the tool schema string so the LLM knows.

---

## Google OAuth Flow

### Setup (one-time, outside the app)

1. Create a Google Cloud project
2. Enable the Google Calendar API
3. Create an OAuth 2.0 client ID — type: Android, using the app's package name (`com.lance.llamacppchat`) and debug/release SHA-1 fingerprint
4. No API key required — OAuth covers all auth

### Runtime flow

```
App startup
  MainActivity registers ActivityResultLauncher<Intent> for Google Sign-In

User sends calendar message (not yet signed in)
  GoogleCalendarClient.ensureSignedIn() → returns NeedsSignIn
  AppViewModel emits signInRequest
  MainActivity launches Google Sign-In intent
  User completes OAuth consent screen
  MainActivity calls viewModel.handleGoogleSignInResult(account)
  GoogleCalendarClient stores tokens
  Tool execution resumes automatically
```

### Token storage

| Token | Storage | Lifetime |
|---|---|---|
| Access token | Memory only | ~1 hour |
| Refresh token | `EncryptedSharedPreferences` | Until revoked |
| Expiry timestamp | `EncryptedSharedPreferences` | — |

Before every API call, `GoogleCalendarClient.ensureValidToken()` checks expiry and silently refreshes if needed. The user only sees the consent screen once per install.

### Required Gradle dependencies

```kotlin
implementation("com.google.android.gms:play-services-auth:21.2.0")
implementation("com.google.apis:google-api-services-calendar:v3-rev20231123-2.0.0")
implementation("com.google.api-client:google-api-client-android:2.2.0")
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

### AndroidManifest.xml

`INTERNET` permission is already declared. No additional permissions needed — Calendar access is granted via OAuth scope (`https://www.googleapis.com/auth/calendar`), not an Android permission.

---

## Error Handling

### Auth errors

| Scenario | Behavior |
|---|---|
| Not signed in | Tool execution pauses; `signInRequest` fires; UI shows sign-in prompt in chat |
| Sign-in cancelled by user | Assistant responds: "I need Google Calendar access to do that. Try again when you're ready." |
| Token refresh fails | Treated as not signed in; re-triggers consent flow |

### Calendar API errors

| Scenario | Behavior |
|---|---|
| Network error | Tool returns error result; LLM responds: "I couldn't reach Google Calendar right now. Check your connection." |
| Permission denied / scope revoked | Treated as not signed in |
| Invalid args from LLM (bad date, etc.) | Tool returns error string; LLM sees it and responds naturally |

### Tool loop errors

| Scenario | Behavior |
|---|---|
| 4th tool call hop | Generation stops; assistant: "I wasn't able to complete that. Try rephrasing." |
| Malformed JSON from LLM | `ToolCallParser` returns null; generation continues as a normal text response (graceful degradation) |

---

## Prompt Injection Format

The following is appended to the system prompt when at least one tool is registered:

```
Current date/time: {day}, {month} {date} {year}, {HH:MM AM/PM}

You have access to the following tools. To use a tool, output ONLY a JSON code block with no other text:
```json
{"tool":"<tool_name>","args":{<args>}}
```

Available tools:
- list_events(date: YYYY-MM-DD): List calendar events for a given date.
- create_event(title: string, date: YYYY-MM-DD, time: HH:MM, duration_minutes: integer [default: 60]): Create a new calendar event.

After receiving a tool result, respond naturally to the user. Do not output another tool call unless necessary.
```

---

## Testing Plan

### Unit tests

| Class | What to test |
|---|---|
| `ToolCallParser` | Valid JSON block extracted; malformed JSON returns null; JSON embedded in prose; no JSON present |
| `GoogleCalendarTool` | `list_events` with fake client returning mock events; `create_event` with fake client; API error propagation |
| `ToolRegistry` | Dispatch to correct tool; unknown tool name returns error result; max-hop guard enforced |
| `AppViewModel` | Agentic loop runs one hop; loop stops after tool result; loop stops at max hops; no tool call goes straight to response |

### Manual E2E

- [ ] First-time sign-in consent screen appears and completes
- [ ] "What's on my calendar today?" returns real events
- [ ] "Create me a schedule at 3pm and title it feeding cats" creates the event in Google Calendar
- [ ] Token refresh works silently after 1 hour
- [ ] Cancelling sign-in shows graceful error message
- [ ] Malformed LLM output falls back to plain text response

---

## Files To Create or Modify

### New files
- `app/src/main/java/com/lance/llamacppchat/tools/Tool.kt`
- `app/src/main/java/com/lance/llamacppchat/tools/ToolRegistry.kt`
- `app/src/main/java/com/lance/llamacppchat/tools/ToolCallParser.kt`
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt`
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`
- `app/src/test/java/com/lance/llamacppchat/tools/ToolCallParserTest.kt`
- `app/src/test/java/com/lance/llamacppchat/tools/ToolRegistryTest.kt`
- `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`

### Modified files
- `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt` — agentic loop, new state fields, sign-in event
- `app/src/main/java/com/lance/llamacppchat/MainActivity.kt` — register sign-in launcher, collect signInRequest
- `app/build.gradle.kts` — add Gradle dependencies
- `app/src/main/AndroidManifest.xml` — add `<queries>` block for Google Sign-In if needed
- `docs/application-features-for-claude-design.md` — add tool calling section
