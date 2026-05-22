# Google Calendar Tool Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a ToolRegistry architecture with Google Calendar read/write so the on-device LLM can create and list calendar events via natural language.

**Architecture:** `ToolCallParser` detects a JSON code block in LLM output; `ToolRegistry` dispatches it to the matching `Tool`; `AppViewModel.sendMessage` runs an agentic loop (max 3 hops) injecting tool results back into the prompt; Google Sign-In OAuth is coordinated via a `SharedFlow<Unit>` from ViewModel to `LlamaCppChatApp`.

**Tech Stack:** Kotlin, Jetpack Compose, `play-services-auth:21.2.0`, `security-crypto:1.1.0-alpha06`, OkHttp (already in project), `org.json.JSONObject` (built into Android), JUnit4, kotlinx-coroutines-test

---

## File Map

### New files
- `app/src/main/java/com/lance/llamacppchat/tools/Tool.kt` — `Tool` interface, `ToolDefinition`, `ToolCall`, `ToolResult`, `NEEDS_SIGN_IN_SENTINEL`
- `app/src/main/java/com/lance/llamacppchat/tools/ToolCallParser.kt` — finds first ` ```json {...} ``` ` block in LLM output and returns a `ToolCall`
- `app/src/main/java/com/lance/llamacppchat/tools/ToolRegistry.kt` — holds registered tools, produces prompt block, dispatches `ToolCall`
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt` — interface + `CalendarEvent` model
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClientImpl.kt` — Google Sign-In + OkHttp REST calls to Calendar API v3
- `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt` — `ListCalendarEventsTool` (name `"list_events"`) and `CreateCalendarEventTool` (name `"create_event"`), plus a `googleCalendarTools(client)` convenience factory
- `app/src/test/java/com/lance/llamacppchat/tools/ToolCallParserTest.kt`
- `app/src/test/java/com/lance/llamacppchat/tools/ToolRegistryTest.kt`
- `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`

### Modified files
- `app/build.gradle.kts` — add `play-services-auth`, `security-crypto`
- `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt` — `toolRegistry` + `googleCalendarClient` params, `isToolExecuting`/`googleSignedIn` state, `signInRequest` SharedFlow, `handleGoogleSignInResult`, agentic loop in `sendMessage`, tool block + date injection in `promptForModel`
- `app/src/main/java/com/lance/llamacppchat/App.kt` — `rememberAppViewModel()` wires `GoogleCalendarClientImpl` + `ToolRegistry`; `LlamaCppChatApp` registers sign-in launcher and collects `signInRequest`
- `app/src/test/java/com/lance/llamacppchat/ui/AppViewModelTest.kt` — extend `testViewModel` helper with `toolRegistry` param; add agentic loop tests

---

### Task 1: Add Gradle dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add dependencies**

Inside the `dependencies {}` block in `app/build.gradle.kts`, add after the last `implementation(...)` line:

```kotlin
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

- [ ] **Step 2: Sync Gradle**

In Android Studio: File → Sync Project with Gradle Files.
Expected: BUILD SUCCESSFUL, no dependency conflicts.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add play-services-auth and security-crypto dependencies"
```

---

### Task 2: Define core tool contracts

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/tools/Tool.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.lance.llamacppchat.tools

interface Tool {
    val definition: ToolDefinition
    suspend fun execute(args: Map<String, Any>): ToolResult
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: String
)

data class ToolCall(val tool: String, val args: Map<String, Any>)

data class ToolResult(
    val tool: String,
    val content: String,
    val isError: Boolean = false
)

const val NEEDS_SIGN_IN_SENTINEL = "ERROR:NEEDS_SIGN_IN"
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/Tool.kt
git commit -m "feat: add core tool contracts (Tool, ToolDefinition, ToolCall, ToolResult)"
```

---

### Task 3: ToolCallParser (TDD)

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/tools/ToolCallParser.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/tools/ToolCallParserTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.lance.llamacppchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun parsesValidToolCallBlock() {
        val input = "```json\n{\"tool\":\"list_events\",\"args\":{\"date\":\"2026-05-22\"}}\n```"
        val result = ToolCallParser.parse(input)
        assertEquals(ToolCall("list_events", mapOf("date" to "2026-05-22")), result)
    }

    @Test
    fun parsesToolCallEmbeddedInProse() {
        val input = "Let me check.\n```json\n{\"tool\":\"create_event\",\"args\":{\"title\":\"Feeding Cats\",\"date\":\"2026-05-22\",\"time\":\"15:00\",\"duration_minutes\":60}}\n```\nDone."
        val result = ToolCallParser.parse(input)
        assertEquals(
            ToolCall(
                "create_event",
                mapOf("title" to "Feeding Cats", "date" to "2026-05-22", "time" to "15:00", "duration_minutes" to 60)
            ),
            result
        )
    }

    @Test
    fun returnsNullWhenNoJsonBlock() {
        assertNull(ToolCallParser.parse("Sure, I can help with that!"))
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertNull(ToolCallParser.parse("```json\n{not valid json}\n```"))
    }

    @Test
    fun returnsNullWhenJsonLacksToolField() {
        assertNull(ToolCallParser.parse("```json\n{\"action\":\"list_events\",\"args\":{}}\n```"))
    }

    @Test
    fun parsesIntegerArgFromJson() {
        val input = "```json\n{\"tool\":\"create_event\",\"args\":{\"duration_minutes\":90}}\n```"
        val result = ToolCallParser.parse(input)
        assertEquals(90, result?.args?.get("duration_minutes"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.ToolCallParserTest"
```
Expected: compilation error — `ToolCallParser` does not exist yet.

- [ ] **Step 3: Implement ToolCallParser**

```kotlin
package com.lance.llamacppchat.tools

import org.json.JSONObject

object ToolCallParser {
    private val codeBlockRegex = Regex("```json\\s*([\\s\\S]*?)```")

    fun parse(llmOutput: String): ToolCall? {
        val jsonString = codeBlockRegex.find(llmOutput)?.groupValues?.get(1)?.trim()
            ?: return null
        return runCatching {
            val obj = JSONObject(jsonString)
            val toolName = obj.optString("tool").takeIf { it.isNotBlank() } ?: return null
            val argsObj = obj.optJSONObject("args") ?: JSONObject()
            val args = mutableMapOf<String, Any>()
            for (key in argsObj.keys()) {
                args[key] = argsObj.get(key)
            }
            ToolCall(tool = toolName, args = args)
        }.getOrNull()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.ToolCallParserTest"
```
Expected: BUILD SUCCESSFUL, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/ToolCallParser.kt \
        app/src/test/java/com/lance/llamacppchat/tools/ToolCallParserTest.kt
git commit -m "feat: add ToolCallParser with JSON code block detection"
```

---

### Task 4: ToolRegistry (TDD)

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/tools/ToolRegistry.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/tools/ToolRegistryTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.lance.llamacppchat.tools

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun dispatchesToolCallToMatchingTool() = runTest {
        val tool = FakeTool("list_events", ToolResult("list_events", "Event A at 10am"))
        val registry = ToolRegistry(listOf(tool))

        val result = registry.dispatch(ToolCall("list_events", mapOf("date" to "2026-05-22")))

        assertEquals("Event A at 10am", result.content)
        assertFalse(result.isError)
        assertEquals(mapOf("date" to "2026-05-22"), tool.lastArgs)
    }

    @Test
    fun returnsErrorResultForUnknownTool() = runTest {
        val registry = ToolRegistry(emptyList())

        val result = registry.dispatch(ToolCall("unknown_tool", emptyMap()))

        assertTrue(result.isError)
        assertTrue(result.content.contains("unknown_tool"))
    }

    @Test
    fun promptBlockContainsAllToolNames() {
        val registry = ToolRegistry(listOf(
            FakeTool("list_events", ToolResult("list_events", "")),
            FakeTool("create_event", ToolResult("create_event", ""))
        ))
        val block = registry.promptBlock()
        assertTrue(block.contains("list_events"))
        assertTrue(block.contains("create_event"))
    }

    @Test
    fun promptBlockIsEmptyWhenNoToolsRegistered() {
        assertEquals("", ToolRegistry(emptyList()).promptBlock())
    }
}

private class FakeTool(name: String, private val result: ToolResult) : Tool {
    override val definition = ToolDefinition(name = name, description = "Fake", parametersSchema = "none")
    var lastArgs: Map<String, Any> = emptyMap()
    override suspend fun execute(args: Map<String, Any>): ToolResult {
        lastArgs = args
        return result
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.ToolRegistryTest"
```
Expected: compilation error — `ToolRegistry` does not exist yet.

- [ ] **Step 3: Implement ToolRegistry**

```kotlin
package com.lance.llamacppchat.tools

class ToolRegistry(private val tools: List<Tool> = emptyList()) {

    fun promptBlock(): String {
        if (tools.isEmpty()) return ""
        val toolList = tools.joinToString("\n") { t ->
            "- ${t.definition.name}: ${t.definition.description}. Parameters: ${t.definition.parametersSchema}"
        }
        return """
You have access to the following tools. To use a tool, output ONLY a JSON code block with no other text:
```json
{"tool":"<tool_name>","args":{<args>}}
```

Available tools:
$toolList

After receiving a tool result, respond naturally to the user. Do not output another tool call unless necessary.
        """.trimIndent()
    }

    suspend fun dispatch(toolCall: ToolCall): ToolResult {
        val tool = tools.firstOrNull { it.definition.name == toolCall.tool }
            ?: return ToolResult(
                tool = toolCall.tool,
                content = "Unknown tool '${toolCall.tool}'. Available: ${tools.map { it.definition.name }}",
                isError = true
            )
        return tool.execute(toolCall.args)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.ToolRegistryTest"
```
Expected: BUILD SUCCESSFUL, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/ToolRegistry.kt \
        app/src/test/java/com/lance/llamacppchat/tools/ToolRegistryTest.kt
git commit -m "feat: add ToolRegistry with dispatch and prompt block generation"
```

---

### Task 5: GoogleCalendarClient interface and real implementation

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClient.kt`
- Create: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarClientImpl.kt`

- [ ] **Step 1: Create the interface**

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
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String
)
```

- [ ] **Step 2: Create the real implementation**

```kotlin
package com.lance.llamacppchat.tools.calendar

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GoogleCalendarClientImpl(private val context: Context) : GoogleCalendarClient {
    private val httpClient = OkHttpClient()
    private var account: GoogleSignInAccount? = null

    override fun isSignedIn(): Boolean = account != null

    override fun handleSignInResult(googleAccount: GoogleSignInAccount?) {
        account = googleAccount
    }

    private suspend fun accessToken(): String {
        val a = account ?: error("Not signed in")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(
                context,
                a.account!!,
                "oauth2:https://www.googleapis.com/auth/calendar"
            )
        }
    }

    override suspend fun listEvents(date: String): Result<List<CalendarEvent>> = runCatching {
        val token = accessToken()
        val zone = ZoneId.systemDefault()
        val localDate = LocalDate.parse(date)
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val timeMin = URLEncoder.encode(localDate.atStartOfDay(zone).format(fmt), "UTF-8")
        val timeMax = URLEncoder.encode(localDate.plusDays(1).atStartOfDay(zone).format(fmt), "UTF-8")

        val request = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=$timeMin&timeMax=$timeMax&singleEvents=true&orderBy=startTime")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val body = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Calendar API ${resp.code}")
                resp.body?.string() ?: "{}"
            }
        }

        val items = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
        (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            CalendarEvent(
                id = item.optString("id"),
                title = item.optString("summary", "(No title)"),
                start = item.optJSONObject("start")?.optString("dateTime")
                    ?: item.optJSONObject("start")?.optString("date") ?: "",
                end = item.optJSONObject("end")?.optString("dateTime")
                    ?: item.optJSONObject("end")?.optString("date") ?: ""
            )
        }
    }

    override suspend fun createEvent(
        title: String,
        date: String,
        time: String,
        durationMinutes: Int
    ): Result<CalendarEvent> = runCatching {
        val token = accessToken()
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val start = LocalDateTime.parse("${date}T${time}").atZone(zone)
        val end = start.plusMinutes(durationMinutes.toLong())

        val bodyJson = JSONObject().apply {
            put("summary", title)
            put("start", JSONObject().apply {
                put("dateTime", start.format(fmt))
                put("timeZone", zone.id)
            })
            put("end", JSONObject().apply {
                put("dateTime", end.format(fmt))
                put("timeZone", zone.id)
            })
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Calendar API ${resp.code}")
                resp.body?.string() ?: "{}"
            }
        }

        val obj = JSONObject(responseBody)
        CalendarEvent(
            id = obj.optString("id"),
            title = obj.optString("summary", title),
            start = obj.optJSONObject("start")?.optString("dateTime") ?: "",
            end = obj.optJSONObject("end")?.optString("dateTime") ?: ""
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/
git commit -m "feat: add GoogleCalendarClient interface and OkHttp REST implementation"
```

---

### Task 6: GoogleCalendarTool (TDD)

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.lance.llamacppchat.tools.calendar

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarToolTest {

    // ---- ListCalendarEventsTool ----

    @Test
    fun listEventsReturnsFormattedEventSummary() = runTest {
        val client = FakeGoogleCalendarClient(
            signedIn = true,
            listResult = Result.success(listOf(
                CalendarEvent("1", "Meeting with Alex", "2026-05-23T10:00:00", "2026-05-23T11:00:00"),
                CalendarEvent("2", "Standup", "2026-05-23T14:00:00", "2026-05-23T14:30:00")
            ))
        )
        val tool = ListCalendarEventsTool(client)

        val result = tool.execute(mapOf("date" to "2026-05-23"))

        assertFalse(result.isError)
        assertTrue(result.content.contains("Meeting with Alex"))
        assertTrue(result.content.contains("Standup"))
    }

    @Test
    fun listEventsReturnsNeedsSignInWhenNotSignedIn() = runTest {
        val tool = ListCalendarEventsTool(FakeGoogleCalendarClient(signedIn = false))
        val result = tool.execute(mapOf("date" to "2026-05-23"))
        assertTrue(result.isError)
        assertEquals(NEEDS_SIGN_IN_SENTINEL, result.content)
    }

    @Test
    fun listEventsReturnsErrorOnApiFailure() = runTest {
        val client = FakeGoogleCalendarClient(
            signedIn = true,
            listResult = Result.failure(Exception("network error"))
        )
        val result = ListCalendarEventsTool(client).execute(mapOf("date" to "2026-05-22"))
        assertTrue(result.isError)
        assertTrue(result.content.contains("network error"))
    }

    @Test
    fun listEventsToolDefinitionNameIsListEvents() {
        assertEquals("list_events", ListCalendarEventsTool(FakeGoogleCalendarClient(signedIn = true)).definition.name)
    }

    // ---- CreateCalendarEventTool ----

    @Test
    fun createEventReturnsConfirmationContainingTitle() = runTest {
        val client = FakeGoogleCalendarClient(
            signedIn = true,
            createResult = Result.success(
                CalendarEvent("99", "Feeding Cats", "2026-05-22T15:00:00", "2026-05-22T16:00:00")
            )
        )
        val result = CreateCalendarEventTool(client).execute(
            mapOf("title" to "Feeding Cats", "date" to "2026-05-22", "time" to "15:00", "duration_minutes" to 60)
        )
        assertFalse(result.isError)
        assertTrue(result.content.contains("Feeding Cats"))
    }

    @Test
    fun createEventUsesDefaultDurationOf60WhenNotProvided() = runTest {
        val client = FakeGoogleCalendarClient(
            signedIn = true,
            createResult = Result.success(CalendarEvent("1", "Test", "", ""))
        )
        CreateCalendarEventTool(client).execute(
            mapOf("title" to "Test", "date" to "2026-05-22", "time" to "10:00")
        )
        assertEquals(60, client.lastDurationMinutes)
    }

    @Test
    fun createEventReturnsNeedsSignInWhenNotSignedIn() = runTest {
        val result = CreateCalendarEventTool(FakeGoogleCalendarClient(signedIn = false))
            .execute(mapOf("title" to "Test", "date" to "2026-05-22", "time" to "10:00"))
        assertTrue(result.isError)
        assertEquals(NEEDS_SIGN_IN_SENTINEL, result.content)
    }

    @Test
    fun createEventToolDefinitionNameIsCreateEvent() {
        assertEquals("create_event", CreateCalendarEventTool(FakeGoogleCalendarClient(signedIn = true)).definition.name)
    }

    // ---- googleCalendarTools factory ----

    @Test
    fun googleCalendarToolsReturnsTwoTools() {
        val tools = googleCalendarTools(FakeGoogleCalendarClient(signedIn = true))
        assertEquals(2, tools.size)
        assertTrue(tools.any { it.definition.name == "list_events" })
        assertTrue(tools.any { it.definition.name == "create_event" })
    }
}

class FakeGoogleCalendarClient(
    private val signedIn: Boolean,
    val listResult: Result<List<CalendarEvent>> = Result.success(emptyList()),
    val createResult: Result<CalendarEvent> = Result.success(CalendarEvent("id", "Event", "", ""))
) : GoogleCalendarClient {
    var lastDurationMinutes: Int = -1

    override fun isSignedIn(): Boolean = signedIn
    override fun handleSignInResult(account: GoogleSignInAccount?) {}
    override suspend fun listEvents(date: String): Result<List<CalendarEvent>> = listResult
    override suspend fun createEvent(title: String, date: String, time: String, durationMinutes: Int): Result<CalendarEvent> {
        lastDurationMinutes = durationMinutes
        return createResult
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: compilation error — `ListCalendarEventsTool`, `CreateCalendarEventTool`, `googleCalendarTools` do not exist yet.

- [ ] **Step 3: Implement GoogleCalendarTool.kt**

```kotlin
package com.lance.llamacppchat.tools.calendar

import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import com.lance.llamacppchat.tools.Tool
import com.lance.llamacppchat.tools.ToolDefinition
import com.lance.llamacppchat.tools.ToolResult

class ListCalendarEventsTool(private val client: GoogleCalendarClient) : Tool {
    override val definition = ToolDefinition(
        name = "list_events",
        description = "List Google Calendar events for a given date",
        parametersSchema = "date: YYYY-MM-DD"
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        if (!client.isSignedIn()) return ToolResult(definition.name, NEEDS_SIGN_IN_SENTINEL, isError = true)
        val date = args["date"] as? String
            ?: return ToolResult(definition.name, "Missing 'date' parameter (YYYY-MM-DD)", isError = true)
        return client.listEvents(date).fold(
            onSuccess = { events ->
                val body = if (events.isEmpty()) "No events found for $date." else
                    events.joinToString("\n") { "- ${it.title}: ${it.start} to ${it.end}" }
                ToolResult(definition.name, "Events for $date:\n$body")
            },
            onFailure = { ToolResult(definition.name, "list_events error: ${it.message}", isError = true) }
        )
    }
}

class CreateCalendarEventTool(private val client: GoogleCalendarClient) : Tool {
    override val definition = ToolDefinition(
        name = "create_event",
        description = "Create a new Google Calendar event",
        parametersSchema = "title: string, date: YYYY-MM-DD, time: HH:MM (24h), duration_minutes: integer [default 60]"
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        if (!client.isSignedIn()) return ToolResult(definition.name, NEEDS_SIGN_IN_SENTINEL, isError = true)
        val title = args["title"] as? String
            ?: return ToolResult(definition.name, "Missing 'title'", isError = true)
        val date = args["date"] as? String
            ?: return ToolResult(definition.name, "Missing 'date' (YYYY-MM-DD)", isError = true)
        val time = args["time"] as? String
            ?: return ToolResult(definition.name, "Missing 'time' (HH:MM)", isError = true)
        val duration = (args["duration_minutes"] as? Number)?.toInt() ?: 60
        return client.createEvent(title, date, time, duration).fold(
            onSuccess = { event ->
                ToolResult(definition.name, "Created '${event.title}' from ${event.start} to ${event.end}.")
            },
            onFailure = { ToolResult(definition.name, "create_event error: ${it.message}", isError = true) }
        )
    }
}

fun googleCalendarTools(client: GoogleCalendarClient): List<Tool> =
    listOf(ListCalendarEventsTool(client), CreateCalendarEventTool(client))
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.tools.calendar.GoogleCalendarToolTest"
```
Expected: BUILD SUCCESSFUL, 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarTool.kt \
        app/src/test/java/com/lance/llamacppchat/tools/calendar/GoogleCalendarToolTest.kt
git commit -m "feat: add ListCalendarEventsTool and CreateCalendarEventTool"
```

---

### Task 7: AppViewModel — agentic loop, state, and sign-in coordination (TDD)

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt`
- Modify: `app/src/test/java/com/lance/llamacppchat/ui/AppViewModelTest.kt`

- [ ] **Step 1: Add new state fields to AppState**

In `AppViewModel.kt`, find `data class AppState(` and add two fields before the closing `) {`:

```kotlin
    val isToolExecuting: Boolean = false,
    val googleSignedIn: Boolean = false,
```

- [ ] **Step 2: Add new imports to AppViewModel.kt**

Add these imports at the top of `AppViewModel.kt`:

```kotlin
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import com.lance.llamacppchat.tools.ToolCallParser
import com.lance.llamacppchat.tools.ToolRegistry
import com.lance.llamacppchat.tools.calendar.GoogleCalendarClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
```

- [ ] **Step 3: Add constructor parameters and SharedFlow to AppViewModel**

In the `AppViewModel` constructor (the `class AppViewModel(` block), add after `epochTimeProvider`:

```kotlin
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val googleCalendarClient: GoogleCalendarClient? = null,
```

Inside the `AppViewModel` class body, after `private var generationJob: Job? = null`, add:

```kotlin
    private val _signInRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInRequest: SharedFlow<Unit> = _signInRequest
    private var pendingSignIn: CompletableDeferred<Boolean>? = null

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        googleCalendarClient?.handleSignInResult(account)
        val success = account != null
        pendingSignIn?.complete(success)
        pendingSignIn = null
        mutableState.update { it.copy(googleSignedIn = success) }
    }

    private suspend fun awaitGoogleSignIn(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingSignIn = deferred
        _signInRequest.emit(Unit)
        return deferred.await()
    }
```

- [ ] **Step 4: Modify promptForModel to inject tool block and current date/time**

In `AppViewModel.kt`, find `private suspend fun promptForModel(userPrompt: String, hasImage: Boolean = false): String` and replace it with:

```kotlin
    private suspend fun promptForModel(
        userPrompt: String,
        hasImage: Boolean = false,
        toolContext: List<String> = emptyList()
    ): String {
        val formatter = promptFormatterRepository.loadState().activeFormatter
        val formatterBody = formatter?.body.orEmpty().trim()
        val prompt = if (userPrompt.isBlank() && hasImage) "Describe this image." else userPrompt

        val toolBlock = toolRegistry.promptBlock().takeIf { it.isNotEmpty() }?.let { block ->
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy, h:mm a"))
            "Current date/time: $now\n\n$block"
        }

        val selectedMemories: List<MemoryItem> = withContext(ioDispatcher) {
            if (mutableState.value.isEmbeddingModelLoaded) {
                embeddingEngine.embed(prompt).getOrNull()?.let { queryVector ->
                    val topKeys = embeddingStore.findTopK(queryVector, MemoryRepository.PROMPT_MEMORY_LIMIT)
                    val pinnedMemories = memoryRepository.loadMemories()
                        .filter { it.key in MemoryRepository.PINNED_KEYS }
                        .sortedBy { MemoryRepository.PINNED_KEYS.indexOf(it.key) }
                    val semanticMemories = memoryRepository.memoriesByEncodedKeys(topKeys)
                        .filterNot { it.key in MemoryRepository.PINNED_KEYS }
                    (pinnedMemories + semanticMemories).take(MemoryRepository.PROMPT_MEMORY_LIMIT)
                } ?: memoryRepository.selectForPrompt(prompt)
            } else {
                memoryRepository.selectForPrompt(prompt)
            }
        }

        val memoryBlock = selectedMemories
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n", prefix = "Memory:\n") { "- ${it.key}: ${it.value}" }

        val toolContextBlock = toolContext.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n\n")

        return listOf(formatterBody, toolBlock, memoryBlock, "User message:\n$prompt", toolContextBlock)
            .filterNot { it.isNullOrBlank() }
            .joinToString("\n\n")
    }
```

- [ ] **Step 5: Replace the generation logic inside sendMessage with an agentic loop**

In `AppViewModel.kt`, inside `sendMessage`, find the block starting with `generationJob = viewModelScope.launch {` and replace everything inside the launch (from `val modelPrompt = ...` through the final `}` that closes `loadModelWithFallback.fold`) with:

```kotlin
            val streamResponsesEnabled = mutableState.value.streamResponsesEnabled
            loadModelWithFallback(model).fold(
                onSuccess = {
                    val startedAtNanos = nanoTimeProvider()
                    val toolContext = mutableListOf<String>()
                    var hopsLeft = MAX_TOOL_HOPS

                    while (hopsLeft > 0 && isActive) {
                        hopsLeft--
                        val modelPrompt = promptForModel(cleanedPrompt, hasImage = cleanedImagePath != null, toolContext = toolContext)
                        val streamedText = StringBuilder()

                        val generationResult = if (streamResponsesEnabled && toolContext.isEmpty()) {
                            val onPartial: (String) -> Unit = { chunk ->
                                if (isActive) updateStreamingAssistant(streamedText, chunk)
                            }
                            if (cleanedImagePath == null) engine.generateStreaming(modelPrompt, onPartial)
                            else engine.generateStreamingWithImage(modelPrompt, cleanedImagePath, onPartial)
                        } else {
                            if (cleanedImagePath == null) engine.generate(modelPrompt)
                            else engine.generateWithImage(modelPrompt, cleanedImagePath)
                        }

                        val response = generationResult.getOrElse { error ->
                            if (!isActive) return@launch
                            updateChatState {
                                val updated = it.withoutLoadingAssistant()
                                updated.withActiveChatMessages(updated.messages).copy(
                                    isGenerating = false,
                                    isToolExecuting = false,
                                    errorText = error.message ?: "Generation failed"
                                )
                            }
                            return@launch
                        }

                        if (!isActive) return@launch

                        val finalResponse = if (streamResponsesEnabled && toolContext.isEmpty() && streamedText.isNotBlank()) {
                            streamedText.toString()
                        } else {
                            response
                        }

                        val toolCall = ToolCallParser.parse(finalResponse)

                        if (toolCall == null || hopsLeft == 0) {
                            val displayResponse = if (toolCall != null && hopsLeft == 0) {
                                "I wasn't able to complete that. Try rephrasing."
                            } else {
                                finalResponse
                            }
                            val elapsedSeconds = (nanoTimeProvider() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_SECOND
                            val totalTokens = estimateTokenCount(displayResponse)
                            val tokensPerSecond = if (elapsedSeconds > 0.0) totalTokens / elapsedSeconds else 0.0
                            updateChatState {
                                val updated = it.replaceLoadingAssistant(displayResponse)
                                updated.withActiveChatMessages(updated.messages).copy(
                                    isGenerating = false,
                                    isToolExecuting = false,
                                    generationStats = GenerationStats(elapsedSeconds, totalTokens, tokensPerSecond)
                                )
                            }
                            break
                        }

                        updateChatState { it.updateLoadingAssistant("Using tools…").copy(isToolExecuting = true) }

                        var toolResult = toolRegistry.dispatch(toolCall)

                        if (toolResult.isError && toolResult.content == NEEDS_SIGN_IN_SENTINEL) {
                            val signedIn = awaitGoogleSignIn()
                            if (!signedIn) {
                                updateChatState {
                                    it.replaceLoadingAssistant("I need Google Calendar access to do that. Try again when you're ready.")
                                        .let { s -> s.withActiveChatMessages(s.messages) }
                                        .copy(isGenerating = false, isToolExecuting = false)
                                }
                                break
                            }
                            toolResult = toolRegistry.dispatch(toolCall)
                        }

                        toolContext.add("Tool call: ${toolCall.tool}\nTool result: ${toolResult.content}")
                        updateChatState { it.copy(isToolExecuting = false) }
                    }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    updateChatState {
                        it.withoutLoadingAssistant().withActiveChatMessages(it.messages).copy(
                            isGenerating = false,
                            errorText = error.message ?: "Model load failed"
                        )
                    }
                }
            )
```

- [ ] **Step 6: Add MAX_TOOL_HOPS to the companion object**

In `AppViewModel.kt`, find `private companion object {` and add:

```kotlin
        const val MAX_TOOL_HOPS = 3
```

- [ ] **Step 7: Write new tests in AppViewModelTest**

In `AppViewModelTest.kt`, update the `testViewModel` helper to accept `toolRegistry`:

```kotlin
    private fun testViewModel(
        repository: ModelRepository,
        downloader: ModelDownloadClient = FakeDownloader { _, destination, _ ->
            destination.parentFile?.mkdirs()
            destination.writeText("model")
        },
        formatterRepository: PromptFormatterRepository = PromptFormatterRepository(temporaryFolder.root),
        appSettingsRepository: AppSettingsRepository = AppSettingsRepository(temporaryFolder.root),
        chatHistoryRepository: ChatHistoryRepository = ChatHistoryRepository(temporaryFolder.root),
        memoryRepository: MemoryRepository = MemoryRepository(temporaryFolder.root),
        engine: ChatEngine = FakeChatEngine(),
        toolRegistry: ToolRegistry = ToolRegistry(),
        nanoTimeProvider: () -> Long = { 0L },
        epochTimeProvider: () -> Long = { 0L }
    ): AppViewModel =
        AppViewModel(
            repository = repository,
            promptFormatterRepository = formatterRepository,
            appSettingsRepository = appSettingsRepository,
            chatHistoryRepository = chatHistoryRepository,
            memoryRepository = memoryRepository,
            downloader = downloader,
            engine = engine,
            toolRegistry = toolRegistry,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            nanoTimeProvider = nanoTimeProvider,
            epochTimeProvider = epochTimeProvider
        )
```

Add these imports to `AppViewModelTest.kt`:

```kotlin
import com.lance.llamacppchat.tools.Tool
import com.lance.llamacppchat.tools.ToolDefinition
import com.lance.llamacppchat.tools.ToolResult
import com.lance.llamacppchat.tools.ToolRegistry
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
```

Add these test cases to `AppViewModelTest`:

```kotlin
    @Test
    fun sendMessageExecutesToolCallAndGeneratesFinalResponse() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also { it.saveMetadata(installedModel(modelFile.absolutePath)) }

        val toolResult = ToolResult("list_events", "Events for 2026-05-22:\n- Standup: 10:00 to 10:30")
        val fakeTool = FakeToolForViewModel("list_events", toolResult)

        val engine = FakeChatEngine(
            responses = listOf(
                "```json\n{\"tool\":\"list_events\",\"args\":{\"date\":\"2026-05-22\"}}\n```",
                "You have a Standup at 10 AM."
            )
        )
        // Disable streaming so generate() is used for all hops (avoids FakeChatEngine.generateStreaming complexity)
        val settingsRepo = AppSettingsRepository(temporaryFolder.root).also { it.setStreamResponsesEnabled(false) }
        val viewModel = testViewModel(
            repository = repository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool)),
            appSettingsRepository = settingsRepo
        )

        viewModel.sendMessage("What's on my calendar today?")
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals("You have a Standup at 10 AM.", messages.last().content)
        assertFalse(viewModel.state.value.isGenerating)
        assertFalse(viewModel.state.value.isToolExecuting)
        assertEquals(1, fakeTool.executeCount)
    }

    @Test
    fun sendMessageWithNoToolCallSkipsToolLoop() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also { it.saveMetadata(installedModel(modelFile.absolutePath)) }
        val fakeTool = FakeToolForViewModel("list_events", ToolResult("list_events", "irrelevant"))
        val engine = FakeChatEngine(responses = listOf("Sure, here is the answer."))
        val settingsRepo = AppSettingsRepository(temporaryFolder.root).also { it.setStreamResponsesEnabled(false) }
        val viewModel = testViewModel(
            repository = repository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool)),
            appSettingsRepository = settingsRepo
        )

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("Sure, here is the answer.", viewModel.state.value.messages.last().content)
        assertEquals(0, fakeTool.executeCount)
    }

    @Test
    fun sendMessageStopsAfterMaxToolHopsWithErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val modelFile = File(temporaryFolder.root, "model.gguf").also { it.writeText("model") }
        val repository = ModelRepository(temporaryFolder.root).also { it.saveMetadata(installedModel(modelFile.absolutePath)) }
        val toolResult = ToolResult("list_events", "some result")
        val fakeTool = FakeToolForViewModel("list_events", toolResult)
        // Engine always returns a tool call (infinite loop scenario)
        val engine = FakeChatEngine(responses = List(10) {
            "```json\n{\"tool\":\"list_events\",\"args\":{\"date\":\"2026-05-22\"}}\n```"
        })
        val settingsRepo = AppSettingsRepository(temporaryFolder.root).also { it.setStreamResponsesEnabled(false) }
        val viewModel = testViewModel(
            repository = repository,
            engine = engine,
            toolRegistry = ToolRegistry(listOf(fakeTool)),
            appSettingsRepository = settingsRepo
        )

        viewModel.sendMessage("loop forever")
        advanceUntilIdle()

        val lastMessage = viewModel.state.value.messages.last().content
        assertTrue(lastMessage.contains("Try rephrasing"))
        assertFalse(viewModel.state.value.isGenerating)
    }
```

Add `FakeToolForViewModel` at the bottom of `AppViewModelTest.kt` (outside the class):

```kotlin
private class FakeToolForViewModel(
    name: String,
    private val result: ToolResult
) : Tool {
    override val definition = ToolDefinition(name = name, description = "fake", parametersSchema = "none")
    var executeCount = 0
    override suspend fun execute(args: Map<String, Any>): ToolResult {
        executeCount++
        return result
    }
}
```

Update `FakeChatEngine` to support a list of responses (one per `generate` call). Add a `responses: List<String> = emptyList()` parameter. When `responses` is non-empty, return `responses[callIndex++ % responses.size]` from `generate`. When empty, use `response`.

Find the `FakeChatEngine` class in `AppViewModelTest.kt`. Add `private val responses: List<String> = emptyList()` to its constructor and a `private var responseIndex = 0` field. Modify the `generate` function to:

```kotlin
    override suspend fun generate(prompt: String): Result<String> {
        prompts += prompt
        generateFailure?.let { return Result.failure(it) }
        responseDeferred?.let { return Result.success(it.await()) }
        val r = if (responses.isNotEmpty()) responses[responseIndex++ % responses.size] else response
        return Result.success(r)
    }
```

- [ ] **Step 8: Run all new and existing tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.lance.llamacppchat.ui.AppViewModelTest"
```
Expected: BUILD SUCCESSFUL, all tests pass (including pre-existing ones).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt \
        app/src/test/java/com/lance/llamacppchat/ui/AppViewModelTest.kt
git commit -m "feat: add ToolRegistry agentic loop and Google sign-in coordination to AppViewModel"
```

---

### Task 8: Wire ToolRegistry and sign-in launcher in App.kt

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/App.kt`

- [ ] **Step 1: Add imports to App.kt**

Add at the top of `App.kt`:

```kotlin
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.lance.llamacppchat.tools.ToolRegistry
import com.lance.llamacppchat.tools.calendar.GoogleCalendarClientImpl
import com.lance.llamacppchat.tools.calendar.googleCalendarTools
```

- [ ] **Step 2: Update rememberAppViewModel() to wire GoogleCalendarClientImpl and ToolRegistry**

Find `private fun rememberAppViewModel(): AppViewModel` in `App.kt` and replace it with:

```kotlin
@Composable
private fun rememberAppViewModel(): AppViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                    "Unknown ViewModel class ${modelClass.name}"
                }
                val calendarClient = GoogleCalendarClientImpl(context)
                return AppViewModel(
                    repository = ModelRepository(context.filesDir),
                    engine = LlamaCppChatEngine(context),
                    embeddingEngine = LlamaCppEmbeddingEngine(context),
                    toolRegistry = ToolRegistry(googleCalendarTools(calendarClient)),
                    googleCalendarClient = calendarClient
                ) as T
            }
        }
    )
}
```

- [ ] **Step 3: Add Google Sign-In launcher and signInRequest collector to LlamaCppChatApp**

In `LlamaCppChatApp`, after the existing `overlayPermissionLauncher` block, add:

```kotlin
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = runCatching { task.result }.getOrNull()
        appViewModel.handleGoogleSignInResult(account)
    }
    LaunchedEffect(Unit) {
        appViewModel.signInRequest.collect {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }
```

Note: `context` is already declared as `LocalContext.current.applicationContext` on line 1 of `LlamaCppChatApp`. The `remember` block needs `LocalContext.current` (not applicationContext) for `GoogleSignIn.getClient`. Replace the `context` reference inside `remember` with `LocalContext.current` (assign it to a local val):

```kotlin
    val localContext = LocalContext.current
    val context = localContext.applicationContext   // rename existing context usage
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
        GoogleSignIn.getClient(localContext, gso)
    }
```

Update the existing `val context = LocalContext.current.applicationContext` to `val localContext = LocalContext.current` and add `val context = localContext.applicationContext` on the next line.

- [ ] **Step 4: Build the project**

```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL with no compilation errors.

- [ ] **Step 5: Run all unit tests**

```
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/App.kt
git commit -m "feat: wire GoogleCalendarClientImpl, ToolRegistry, and sign-in launcher in App.kt"
```

---

## Manual E2E Checklist

After completing all tasks, verify on a physical Android device:

- [ ] Ask "What's on my calendar today?" — Google Sign-In consent screen appears
- [ ] Complete sign-in — app returns to chat and shows calendar events in response
- [ ] Ask "Create me a schedule at 3pm and title it feeding cats" — event appears in Google Calendar
- [ ] Cancel sign-in mid-flow — app shows graceful "I need Google Calendar access" message
- [ ] Ask a non-calendar question — no tool call, normal response
- [ ] Ask a calendar question after already signed in — no sign-in prompt, results appear directly
