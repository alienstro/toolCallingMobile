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
