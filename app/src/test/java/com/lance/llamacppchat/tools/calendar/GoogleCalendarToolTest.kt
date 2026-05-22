package com.lance.llamacppchat.tools.calendar

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarToolTest {

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
    fun updateEventWithOnlyDurationReturnsError() = runTest {
        val tool = UpdateCalendarEventTool(FakeGoogleCalendarClient(signedIn = true))

        val result = tool.execute(mapOf("event_id" to "abc123", "duration_minutes" to 30))

        assertTrue(result.isError)
        assertTrue(result.content.contains("date"))
        assertTrue(result.content.contains("time"))
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

    @Test
    fun googleCalendarToolsReturnsFourTools() {
        val tools = googleCalendarTools(FakeGoogleCalendarClient(signedIn = true))

        assertEquals(4, tools.size)
        assertTrue(tools.any { it.definition.name == "list_events" })
        assertTrue(tools.any { it.definition.name == "create_event" })
        assertTrue(tools.any { it.definition.name == "delete_event" })
        assertTrue(tools.any { it.definition.name == "update_event" })
    }
}

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
        title: String,
        date: String,
        time: String,
        durationMinutes: Int
    ): Result<CalendarEvent> {
        lastDurationMinutes = durationMinutes
        return createResult
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> {
        lastDeletedEventId = eventId
        return deleteResult
    }

    override suspend fun updateEvent(
        eventId: String,
        title: String?,
        date: String?,
        time: String?,
        durationMinutes: Int?
    ): Result<CalendarEvent> {
        lastUpdatedEventId = eventId
        lastUpdateTitle = title
        lastUpdateDate = date
        lastUpdateTime = time
        lastUpdateDuration = durationMinutes
        return updateResult
    }
}
