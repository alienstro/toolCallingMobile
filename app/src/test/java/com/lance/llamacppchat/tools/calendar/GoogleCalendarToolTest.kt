package com.lance.llamacppchat.tools.calendar

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lance.llamacppchat.tools.NEEDS_SIGN_IN_SENTINEL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarToolTest {

    @Test
    fun listEventsReturnsFormattedEventSummary() = runTest {
        val client = FakeGoogleCalendarClient(
            signedIn = true,
            listResult = Result.success(
                listOf(
                    CalendarEvent("1", "Meeting with Alex", "2026-05-23T10:00:00", "2026-05-23T11:00:00"),
                    CalendarEvent("2", "Standup", "2026-05-23T14:00:00", "2026-05-23T14:30:00")
                )
            )
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
    fun googleCalendarToolsReturnsTwoTools() {
        val tools = googleCalendarTools(FakeGoogleCalendarClient(signedIn = true))

        assertEquals(2, tools.size)
        assertTrue(tools.any { it.definition.name == "list_events" })
        assertTrue(tools.any { it.definition.name == "create_event" })
    }
}

private class FakeGoogleCalendarClient(
    private val signedIn: Boolean,
    private val listResult: Result<List<CalendarEvent>> = Result.success(emptyList()),
    private val createResult: Result<CalendarEvent> = Result.success(CalendarEvent("id", "Event", "", ""))
) : GoogleCalendarClient {
    var lastDurationMinutes: Int = -1

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
}
