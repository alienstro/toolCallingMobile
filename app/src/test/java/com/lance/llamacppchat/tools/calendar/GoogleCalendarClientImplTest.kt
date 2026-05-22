package com.lance.llamacppchat.tools.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarClientImplTest {

    @Test
    fun calendarApiErrorMessageIncludesGoogleReasonAndMessage() {
        val body = """
            {
              "error": {
                "code": 403,
                "message": "Google Calendar API has not been used in project 123 before or it is disabled.",
                "errors": [
                  {
                    "message": "Google Calendar API has not been used in project 123 before or it is disabled.",
                    "domain": "usageLimits",
                    "reason": "accessNotConfigured"
                  }
                ],
                "status": "PERMISSION_DENIED"
              }
            }
        """.trimIndent()

        val message = calendarApiErrorMessage(403, body)

        assertEquals(
            "Calendar API 403: accessNotConfigured - Google Calendar API has not been used in project 123 before or it is disabled.",
            message
        )
    }

    @Test
    fun calendarApiErrorMessageFallsBackToStatusCode() {
        assertEquals("Calendar API 500", calendarApiErrorMessage(500, ""))
    }
}
