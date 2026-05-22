# Calendar Delete and Update Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `delete_event` and `update_event` tools to the Google Calendar integration so the LLM can delete and reschedule calendar events by natural language.

**Architecture:** Two new `Tool` subclasses (`DeleteCalendarEventTool`, `UpdateCalendarEventTool`) follow the exact same pattern as `ListCalendarEventsTool` and `CreateCalendarEventTool`. Two new methods are added to `GoogleCalendarClient` and implemented in `GoogleCalendarClientImpl` using the Calendar API v3 DELETE and PATCH endpoints. `ListCalendarEventsTool` is updated to include event IDs in its result so the LLM can reference them. `googleCalendarTools()` is updated to return all four tools — no other files change.

**Tech Stack:** Kotlin, OkHttp (already present), `org.json.JSONObject` (built-in), JUnit4, kotlinx-coroutines-test

---

## File Map

### Modified files only (no new files needed)
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt` — add `deleteEvent` and `updateEvent` to interface
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClientImpl.kt` — implement the two new methods
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt` — update `ListCalendarEventsTool` result format; add `DeleteCalendarEventTool`, `UpdateCalendarEventTool`; update `googleCalendarTools()`
- `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt` — extend `FakeGoogleCalendarClient`; update existing test; add new tests

---

### Task 1: Extend GoogleCalendarClient interface

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt`

- [ ] **Step 1: Add the two new methods to the interface**

Replace the entire file content with:

```kotlin
package com.lance.llamacppchat.tools.calendar

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

interface GoogleCalendarClient {
    fun isSignedIn(): Boolean
    fun handleSignInResult(account: GoogleSignInAccount?)
    suspend fun listEvents(date: String): Result<List<CalendarEvent>>
    suspend fun createEvent(
        title: String,
        date: String,
        time: String,
        durationMinutes: Int
    ): Result<CalendarEvent>
    suspend fun deleteEvent(eventId: String): Result<Unit>
    suspend fun updateEvent(
        eventId: String,
        title: String?,
        date: String?,
        time: String?,
        durationMinutes: Int?
    ): Result<CalendarEvent>
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String
)
```

- [ ] **Step 2: Verify the project still compiles**

```
./gradlew :app:compileDebugKotlin
```
Expected: compilation errors in `GoogleCalendarClientImpl.kt` and `GoogleCalendarToolTest.kt` — both need to implement the new interface methods. This is expected.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt
git commit -m "feat: add deleteEvent and updateEvent to GoogleCalendarClient interface"
```

---

### Task 2: Fix FakeGoogleCalendarClient to restore compilation

**Files:**
- Modify: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`

- [ ] **Step 1: Add the new methods to FakeGoogleCalendarClient**

Find the `private class FakeGoogleCalendarClient(` block at the bottom of `GoogleCalendarToolTest.kt` and replace it entirely with:

```kotlin
internal class FakeGoogleCalendarClient(
    private val signedIn: Boolean,
    private val listResult: Result<List<CalendarEvent>> = Result.success(emptyList()),
    private val createResult: Result<CalendarEvent> = Result.success(CalendarEvent("id", "Event", "", "")),
    private val deleteResult: Result<Unit> = Result.success(Unit),
    private val updateResult: Result<CalendarEvent> = Result.success(CalendarEvent("id", "Event", "", ""))
) : GoogleCalendarClient {
    var lastDurationMinutes: Int = -1
    var lastDeletedEventId: String? = null
    var lastUpdatedEventId: String? = null
    var lastUpdateTitle: String? = null
    var lastUpdateDate: String? = null
    var lastUpdateTime: String? = null
    var lastUpdateDuration: Int? = null

    override fun isSignedIn(): Boolean = signedIn
    override fun handleSignInResult(account: GoogleSignInAccount?) = Unit
    override suspend fun listEvents(date: String): Result<List<CalendarEvent>> = listResult
    override suspend fun createEvent(
        title: String, date: String, time: String, durationMinutes: Int
    ): Result<CalendarEvent> {
        lastDurationMinutes = durationMinutes
        return createResult
    }
    override suspend fun deleteEvent(eventId: String): Result<Unit> {
        lastDeletedEventId = eventId
        return deleteResult
    }
    override suspend fun updateEvent(
        eventId: String, title: String?, date: String?, time: String?, durationMinutes: Int?
    ): Result<CalendarEvent> {
        lastUpdatedEventId = eventId
        lastUpdateTitle = title
        lastUpdateDate = date
        lastUpdateTime = time
        lastUpdateDuration = durationMinutes
        return updateResult
    }
}
```

Note: changed `private class` to `internal class` so it can be used across tests in this package.

- [ ] **Step 2: Verify compilation is restored**

```
./gradlew :app:compileDebugUnitTestKotlin
```
Expected: BUILD SUCCESSFUL — the fake now satisfies the updated interface.

- [ ] **Step 3: Run existing tests to confirm nothing broke**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: BUILD SUCCESSFUL, all 8 existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "test: extend FakeGoogleCalendarClient with deleteEvent and updateEvent stubs"
```

---

### Task 3: Update ListCalendarEventsTool to include event ID in result (TDD)

**Files:**
- Modify: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`

- [ ] **Step 1: Update existing test to assert event ID appears in result**

In `GoogleCalendarToolTest.kt`, find `fun listEventsReturnsFormattedEventSummary()` and replace it with:

```kotlin
@Test
fun listEventsReturnsFormattedEventSummary() = runTest {
    val client = FakeGoogleCalendarClient(
        signedIn = true,
        listResult = Result.success(
            listOf(
                CalendarEvent("evt-1", "Meeting with Alex", "2026-05-23T10:00:00", "2026-05-23T11:00:00"),
                CalendarEvent("evt-2", "Standup", "2026-05-23T14:00:00", "2026-05-23T14:30:00")
            )
        )
    )
    val tool = ListCalendarEventsTool(client)

    val result = tool.execute(mapOf("date" to "2026-05-23"))

    assertFalse(result.isError)
    assertTrue(result.content.contains("Meeting with Alex"))
    assertTrue(result.content.contains("Standup"))
    assertTrue("Result should include event ID", result.content.contains("evt-1"))
    assertTrue("Result should include event ID", result.content.contains("evt-2"))
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.listEventsReturnsFormattedEventSummary"
```
Expected: FAIL — `result.content` does not currently contain the event IDs.

- [ ] **Step 3: Update ListCalendarEventsTool to include event ID in each line**

In `GoogleCalendarTool.kt`, find the `onSuccess` lambda inside `ListCalendarEventsTool.execute` and replace just the `joinToString` line:

```kotlin
events.joinToString("\n") { event ->
    "- ${event.title}: ${event.start} to ${event.end} (id: ${event.id})"
}
```

The full `execute` method after the change:

```kotlin
override suspend fun execute(args: Map<String, Any>): ToolResult {
    if (!client.isSignedIn()) return ToolResult(definition.name, NEEDS_SIGN_IN_SENTINEL, isError = true)
    val date = args["date"] as? String
        ?: return ToolResult(definition.name, "Missing 'date' parameter (YYYY-MM-DD)", isError = true)

    return client.listEvents(date).fold(
        onSuccess = { events ->
            val body = if (events.isEmpty()) {
                "No events found for $date."
            } else {
                events.joinToString("\n") { event ->
                    "- ${event.title}: ${event.start} to ${event.end} (id: ${event.id})"
                }
            }
            ToolResult(definition.name, "Events for $date:\n$body")
        },
        onFailure = { ToolResult(definition.name, "list_events error: ${it.message}", isError = true) }
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.listEventsReturnsFormattedEventSummary"
```
Expected: PASS.

- [ ] **Step 5: Run all calendar tests to confirm no regressions**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: BUILD SUCCESSFUL, all 8 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt \
        app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "feat: include event ID in list_events result so LLM can reference it"
```

---

### Task 4: Add DeleteCalendarEventTool (TDD)

**Files:**
- Modify: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`

- [ ] **Step 1: Write the failing tests**

Add these three tests inside `GoogleCalendarToolTest` (before the closing `}`):

```kotlin
@Test
fun deleteEventSucceeds() = runTest {
    val client = FakeGoogleCalendarClient(
        signedIn = true,
        deleteResult = Result.success(Unit)
    )
    val tool = DeleteCalendarEventTool(client)

    val result = tool.execute(mapOf("event_id" to "abc123"))

    assertFalse(result.isError)
    assertTrue(result.content.contains("deleted"))
    assertEquals("abc123", client.lastDeletedEventId)
}

@Test
fun deleteEventReturnsNeedsSignInWhenNotSignedIn() = runTest {
    val tool = DeleteCalendarEventTool(FakeGoogleCalendarClient(signedIn = false))

    val result = tool.execute(mapOf("event_id" to "abc123"))

    assertTrue(result.isError)
    assertEquals(NEEDS_SIGN_IN_SENTINEL, result.content)
}

@Test
fun deleteEventReturnsErrorOnApiFailure() = runTest {
    val client = FakeGoogleCalendarClient(
        signedIn = true,
        deleteResult = Result.failure(Exception("event not found"))
    )

    val result = DeleteCalendarEventTool(client).execute(mapOf("event_id" to "abc123"))

    assertTrue(result.isError)
    assertTrue(result.content.contains("event not found"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.deleteEventSucceeds"
```
Expected: compilation error — `DeleteCalendarEventTool` does not exist yet.

- [ ] **Step 3: Implement DeleteCalendarEventTool**

In `GoogleCalendarTool.kt`, add this class after `CreateCalendarEventTool` and before `googleCalendarTools()`:

```kotlin
class DeleteCalendarEventTool(private val client: GoogleCalendarClient) : Tool {
    override val definition = ToolDefinition(
        name = "delete_event",
        description = "Delete a Google Calendar event by its ID",
        parametersSchema = "event_id: string (get the ID from list_events)"
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        if (!client.isSignedIn()) return ToolResult(definition.name, NEEDS_SIGN_IN_SENTINEL, isError = true)
        val eventId = args["event_id"] as? String
            ?: return ToolResult(definition.name, "Missing 'event_id'", isError = true)
        return client.deleteEvent(eventId).fold(
            onSuccess = { ToolResult(definition.name, "Event deleted.") },
            onFailure = { ToolResult(definition.name, "delete_event error: ${it.message}", isError = true) }
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.deleteEventSucceeds" && ./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.deleteEventReturnsNeedsSignInWhenNotSignedIn" && ./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.deleteEventReturnsErrorOnApiFailure"
```
Expected: BUILD SUCCESSFUL, all 3 new tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt \
        app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "feat: add DeleteCalendarEventTool"
```

---

### Task 5: Add UpdateCalendarEventTool (TDD)

**Files:**
- Modify: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`

- [ ] **Step 1: Write the failing tests**

Add these tests inside `GoogleCalendarToolTest`:

```kotlin
@Test
fun updateEventChangesTitleOnly() = runTest {
    val client = FakeGoogleCalendarClient(
        signedIn = true,
        updateResult = Result.success(CalendarEvent("abc123", "New Title", "2026-05-22T10:00:00", "2026-05-22T11:00:00"))
    )
    val tool = UpdateCalendarEventTool(client)

    val result = tool.execute(mapOf("event_id" to "abc123", "title" to "New Title"))

    assertFalse(result.isError)
    assertTrue(result.content.contains("New Title"))
    assertEquals("abc123", client.lastUpdatedEventId)
    assertEquals("New Title", client.lastUpdateTitle)
    assertNull(client.lastUpdateDate)
    assertNull(client.lastUpdateTime)
}

@Test
fun updateEventChangesDateAndTime() = runTest {
    val client = FakeGoogleCalendarClient(
        signedIn = true,
        updateResult = Result.success(CalendarEvent("abc123", "Feeding Cats", "2026-05-23T16:00:00", "2026-05-23T17:00:00"))
    )
    val tool = UpdateCalendarEventTool(client)

    val result = tool.execute(mapOf("event_id" to "abc123", "date" to "2026-05-23", "time" to "16:00"))

    assertFalse(result.isError)
    assertEquals("abc123", client.lastUpdatedEventId)
    assertEquals("2026-05-23", client.lastUpdateDate)
    assertEquals("16:00", client.lastUpdateTime)
}

@Test
fun updateEventWithOnlyDateReturnsError() = runTest {
    val tool = UpdateCalendarEventTool(FakeGoogleCalendarClient(signedIn = true))

    val result = tool.execute(mapOf("event_id" to "abc123", "date" to "2026-05-23"))

    assertTrue(result.isError)
    assertTrue(result.content.contains("time"))
}

@Test
fun updateEventWithOnlyTimeReturnsError() = runTest {
    val tool = UpdateCalendarEventTool(FakeGoogleCalendarClient(signedIn = true))

    val result = tool.execute(mapOf("event_id" to "abc123", "time" to "16:00"))

    assertTrue(result.isError)
    assertTrue(result.content.contains("date"))
}

@Test
fun updateEventWithNoFieldsReturnsError() = runTest {
    val tool = UpdateCalendarEventTool(FakeGoogleCalendarClient(signedIn = true))

    val result = tool.execute(mapOf("event_id" to "abc123"))

    assertTrue(result.isError)
    assertTrue(result.content.contains("at least one field"))
}

@Test
fun updateEventReturnsNeedsSignInWhenNotSignedIn() = runTest {
    val result = UpdateCalendarEventTool(FakeGoogleCalendarClient(signedIn = false))
        .execute(mapOf("event_id" to "abc123", "title" to "New Title"))

    assertTrue(result.isError)
    assertEquals(NEEDS_SIGN_IN_SENTINEL, result.content)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.updateEventChangesTitleOnly"
```
Expected: compilation error — `UpdateCalendarEventTool` does not exist yet.

- [ ] **Step 3: Implement UpdateCalendarEventTool**

In `GoogleCalendarTool.kt`, add this class after `DeleteCalendarEventTool` and before `googleCalendarTools()`:

```kotlin
class UpdateCalendarEventTool(private val client: GoogleCalendarClient) : Tool {
    override val definition = ToolDefinition(
        name = "update_event",
        description = "Update a Google Calendar event by its ID",
        parametersSchema = "event_id: string (required), " +
            "title: string (optional), " +
            "date: YYYY-MM-DD + time: HH:MM — both required together to reschedule, " +
            "duration_minutes: integer (optional, requires date+time)"
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        if (!client.isSignedIn()) return ToolResult(definition.name, NEEDS_SIGN_IN_SENTINEL, isError = true)
        val eventId = args["event_id"] as? String
            ?: return ToolResult(definition.name, "Missing 'event_id'", isError = true)

        val title = args["title"] as? String
        val date = args["date"] as? String
        val time = args["time"] as? String
        val duration = (args["duration_minutes"] as? Number)?.toInt()

        if (title == null && date == null && time == null && duration == null) {
            return ToolResult(
                definition.name,
                "Provide at least one field to update (title, date, time, or duration_minutes).",
                isError = true
            )
        }
        if (date != null && time == null) {
            return ToolResult(definition.name, "Provide 'time' (HH:MM) along with 'date' to reschedule.", isError = true)
        }
        if (time != null && date == null) {
            return ToolResult(definition.name, "Provide 'date' (YYYY-MM-DD) along with 'time' to reschedule.", isError = true)
        }

        return client.updateEvent(eventId, title, date, time, duration).fold(
            onSuccess = { event ->
                ToolResult(definition.name, "Event updated: '${event.title}' from ${event.start} to ${event.end}.")
            },
            onFailure = { ToolResult(definition.name, "update_event error: ${it.message}", isError = true) }
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: BUILD SUCCESSFUL, all tests pass (8 existing + 6 new = 14 total).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt \
        app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "feat: add UpdateCalendarEventTool with partial field validation"
```

---

### Task 6: Update googleCalendarTools() factory and add factory test

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`
- Modify: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`

- [ ] **Step 1: Write the failing factory test**

Add this test inside `GoogleCalendarToolTest`:

```kotlin
@Test
fun googleCalendarToolsReturnsFourTools() {
    val tools = googleCalendarTools(FakeGoogleCalendarClient(signedIn = true))

    assertEquals(4, tools.size)
    assertTrue(tools.any { it.definition.name == "list_events" })
    assertTrue(tools.any { it.definition.name == "create_event" })
    assertTrue(tools.any { it.definition.name == "delete_event" })
    assertTrue(tools.any { it.definition.name == "update_event" })
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest.googleCalendarToolsReturnsFourTools"
```
Expected: FAIL — factory still returns 2 tools and the existing `googleCalendarToolsReturnsTwoTools` test now contradicts the new one (both compile, but only one can pass).

- [ ] **Step 3: Update googleCalendarTools() to return all four tools**

In `GoogleCalendarTool.kt`, replace the `googleCalendarTools` function:

```kotlin
fun googleCalendarTools(client: GoogleCalendarClient): List<Tool> =
    listOf(
        ListCalendarEventsTool(client),
        CreateCalendarEventTool(client),
        DeleteCalendarEventTool(client),
        UpdateCalendarEventTool(client)
    )
```

- [ ] **Step 4: Update the old two-tools factory test**

In `GoogleCalendarToolTest.kt`, find `fun googleCalendarToolsReturnsTwoTools()` and delete it — it's replaced by `googleCalendarToolsReturnsFourTools`.

- [ ] **Step 5: Run all calendar tests**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt \
        app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "feat: add delete_event and update_event to googleCalendarTools factory"
```

---

### Task 7: Implement deleteEvent and updateEvent in GoogleCalendarClientImpl

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClientImpl.kt`

- [ ] **Step 1: Add deleteEvent**

In `GoogleCalendarClientImpl.kt`, add this method after `createEvent`:

```kotlin
override suspend fun deleteEvent(eventId: String): Result<Unit> = runCatching {
    val token = accessToken()
    val request = Request.Builder()
        .url("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId")
        .addHeader("Authorization", "Bearer $token")
        .delete()
        .build()
    withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                error(calendarApiErrorMessage(response.code, body))
            }
        }
    }
}
```

- [ ] **Step 2: Add updateEvent**

In `GoogleCalendarClientImpl.kt`, add this method after `deleteEvent`:

```kotlin
override suspend fun updateEvent(
    eventId: String,
    title: String?,
    date: String?,
    time: String?,
    durationMinutes: Int?
): Result<CalendarEvent> = runCatching {
    val token = accessToken()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    val patchBody = JSONObject()
    if (title != null) patchBody.put("summary", title)
    if (date != null && time != null) {
        val start = LocalDateTime.parse("${date}T$time").atZone(zone)
        val end = start.plusMinutes((durationMinutes ?: 60).toLong())
        patchBody.put("start", JSONObject().apply {
            put("dateTime", start.format(fmt))
            put("timeZone", zone.id)
        })
        patchBody.put("end", JSONObject().apply {
            put("dateTime", end.format(fmt))
            put("timeZone", zone.id)
        })
    }

    val request = Request.Builder()
        .url("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId")
        .addHeader("Authorization", "Bearer $token")
        .patch(patchBody.toString().toRequestBody("application/json".toMediaType()))
        .build()

    val responseBody = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) error(calendarApiErrorMessage(response.code, body))
            body
        }
    }

    val obj = JSONObject(responseBody)
    CalendarEvent(
        id = obj.optString("id"),
        title = obj.optString("summary", title ?: ""),
        start = obj.optJSONObject("start")?.optString("dateTime") ?: "",
        end = obj.optJSONObject("end")?.optString("dateTime") ?: ""
    )
}
```

- [ ] **Step 3: Build the project to confirm no compilation errors**

```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all unit tests**

```
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClientImpl.kt
git commit -m "feat: implement deleteEvent (DELETE) and updateEvent (PATCH) in GoogleCalendarClientImpl"
```

---

## Manual E2E Checklist

Test on a physical device after completing all tasks:

- [ ] "Delete my 3pm meeting today" — LLM calls `list_events`, gets ID, calls `delete_event`, event removed from Google Calendar
- [ ] "Move my feeding cats event to 4pm" — LLM calls `list_events`, gets ID, calls `update_event` with new time, event updated in Google Calendar
- [ ] "Rename my standup to Weekly Sync" — LLM calls `list_events`, gets ID, calls `update_event` with new title only
- [ ] Ask to delete a non-existent event — graceful error message in chat
- [ ] "What's on my calendar?" — list_events result now shows `(id: ...)` on each event line
