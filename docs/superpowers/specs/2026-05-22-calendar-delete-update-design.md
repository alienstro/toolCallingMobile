# Calendar Delete and Update Events — Design Spec

**Date:** 2026-05-22
**Branch:** llamacpp
**Status:** Approved

---

## Overview

Extend the Google Calendar tool set with two new operations: `delete_event` and `update_event`. Both follow the exact same architecture as the existing `list_events` and `create_event` tools — no changes to `ToolRegistry`, `AppViewModel`, or `App.kt` are needed.

Since both operations require an event ID, the LLM will naturally chain a `list_events` call first (to get the ID), then `delete_event` or `update_event`. This 2-hop pattern fits within the existing 3-hop max in the agentic loop.

---

## New Tools

### `delete_event`

| Field | Value |
|---|---|
| Tool name | `delete_event` |
| Required args | `event_id: string` |
| Optional args | none |
| API call | `DELETE /calendars/primary/events/{eventId}` |
| Success result | `"Event deleted."` |
| Error result | `"delete_event error: {message}"` |

### `update_event`

| Field | Value |
|---|---|
| Tool name | `update_event` |
| Required args | `event_id: string` |
| Optional args | `title: string`, `date: YYYY-MM-DD`, `time: HH:MM`, `duration_minutes: integer` |
| API call | `PATCH /calendars/primary/events/{eventId}` (partial update — only changed fields sent) |
| Success result | `"Event updated: '{title}' on {date} at {time}."` |
| Error result | `"update_event error: {message}"` |

At least one optional field must be present; if only `event_id` is provided the tool returns an error: `"Provide at least one field to update (title, date, time, or duration_minutes)."`.

---

## Data Flow Examples

### Delete: "Delete my 3pm meeting today"

```
Hop 1:
  LLM → {"tool":"list_events","args":{"date":"2026-05-22"}}
  Result: "Events for 2026-05-22:\n- Feeding Cats: 2026-05-22T15:00:00 to 2026-05-22T16:00:00 (id: abc123)"

Hop 2:
  LLM → {"tool":"delete_event","args":{"event_id":"abc123"}}
  Result: "Event deleted."

Final response: "Done — I've deleted your 3pm Feeding Cats event."
```

### Update: "Move my feeding cats event to 4pm"

```
Hop 1:
  LLM → {"tool":"list_events","args":{"date":"2026-05-22"}}
  Result: "Events for 2026-05-22:\n- Feeding Cats: 2026-05-22T15:00:00 (id: abc123)"

Hop 2:
  LLM → {"tool":"update_event","args":{"event_id":"abc123","time":"16:00"}}
  Result: "Event updated: 'Feeding Cats' on 2026-05-22 at 16:00."

Final response: "Done — Feeding Cats has been moved to 4pm."
```

**Note:** The event ID must be included in `list_events` results so the LLM can reference it in subsequent calls. The existing `ListCalendarEventsTool` result format must be updated to include the ID.

---

## Architecture

### Files to create or modify

| File | Change |
|---|---|
| `GoogleCalendarClient.kt` | Add `suspend fun deleteEvent(eventId: String): Result<Unit>` and `suspend fun updateEvent(eventId: String, title: String?, date: String?, time: String?, durationMinutes: Int?): Result<CalendarEvent>` |
| `GoogleCalendarClientImpl.kt` | Implement `DELETE /events/{id}` and `PATCH /events/{id}` with partial body |
| `GoogleCalendarTool.kt` | Add `DeleteCalendarEventTool` and `UpdateCalendarEventTool`; add both to `googleCalendarTools()` factory |
| `GoogleCalendarToolTest.kt` | Add `deleteEvent` and `updateEvent` to `FakeGoogleCalendarClient`; add tests for both new tools |

### No changes needed

- `ToolRegistry` — new tools register automatically via `googleCalendarTools()`
- `AppViewModel` — agentic loop handles any number of tools
- `App.kt` — `googleCalendarTools(calendarClient)` will return 4 tools instead of 2

### `list_events` result format update

The result string from `ListCalendarEventsTool` must include event IDs so the LLM can reference them:

**Before:**
```
Events for 2026-05-22:
- Feeding Cats: 2026-05-22T15:00:00 to 2026-05-22T16:00:00
```

**After:**
```
Events for 2026-05-22:
- Feeding Cats: 2026-05-22T15:00:00 to 2026-05-22T16:00:00 (id: abc123)
```

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Not signed in | Returns `NEEDS_SIGN_IN_SENTINEL`, sign-in flow triggered (same as other tools) |
| Event ID not found (404) | `"delete_event error: Event not found."` |
| Network error | `"delete_event error: {message}"` |
| `update_event` with no fields | `"Provide at least one field to update (title, date, time, or duration_minutes)."` |
| Invalid date/time format | Calendar API returns 400; surfaced as `"update_event error: {message}"` |

---

## Testing Plan

### Unit tests (new)

| Test | What it verifies |
|---|---|
| `deleteEventSucceeds` | `DeleteCalendarEventTool` calls `client.deleteEvent`, returns success message |
| `deleteEventReturnsNeedsSignInWhenNotSignedIn` | Returns `NEEDS_SIGN_IN_SENTINEL` when `isSignedIn() == false` |
| `deleteEventReturnsErrorOnApiFailure` | Propagates client failure message |
| `updateEventChangesTitle` | `UpdateCalendarEventTool` calls `client.updateEvent` with only `title` set |
| `updateEventChangesTimeOnly` | Calls `client.updateEvent` with only `time` set, other fields null |
| `updateEventWithNoFieldsReturnsError` | Returns error when only `event_id` provided, no other fields |
| `updateEventReturnsNeedsSignInWhenNotSignedIn` | Returns `NEEDS_SIGN_IN_SENTINEL` |
| `listEventsIncludesEventIdInResult` | Verifies ID appears in `list_events` result string |
| `googleCalendarToolsReturnsFourTools` | Factory returns 4 tools including delete and update |
| `listEventsReturnsFormattedEventSummary` (existing — update) | Verify result now includes `(id: ...)` in each event line |
