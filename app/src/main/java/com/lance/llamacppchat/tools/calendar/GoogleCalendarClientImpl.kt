package com.lance.llamacppchat.tools.calendar

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
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
    private var account: GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf { signedInAccount ->
            GoogleSignIn.hasPermissions(signedInAccount, CALENDAR_SCOPE)
        }

    override fun isSignedIn(): Boolean = account != null

    override fun handleSignInResult(account: GoogleSignInAccount?) {
        this.account = account
    }

    private suspend fun accessToken(): String {
        val signedInAccount = account ?: error("Not signed in")
        val androidAccount = signedInAccount.account ?: error("Signed-in account has no Android account")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(
                context,
                androidAccount,
                "oauth2:$CALENDAR_SCOPE_URI"
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
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                if (!response.isSuccessful) error(calendarApiErrorMessage(response.code, responseBody))
                responseBody
            }
        }

        val items = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
        (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            CalendarEvent(
                id = item.optString("id"),
                title = item.optString("summary", "(No title)"),
                start = item.optJSONObject("start")?.optString("dateTime")
                    ?: item.optJSONObject("start")?.optString("date")
                    ?: "",
                end = item.optJSONObject("end")?.optString("dateTime")
                    ?: item.optJSONObject("end")?.optString("date")
                    ?: ""
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
        val start = LocalDateTime.parse("${date}T$time").atZone(zone)
        val end = start.plusMinutes(durationMinutes.toLong())

        val bodyJson = JSONObject().apply {
            put("summary", title)
            put(
                "start",
                JSONObject().apply {
                    put("dateTime", start.format(fmt))
                    put("timeZone", zone.id)
                }
            )
            put(
                "end",
                JSONObject().apply {
                    put("dateTime", end.format(fmt))
                    put("timeZone", zone.id)
                }
            )
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
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
            title = obj.optString("summary", title),
            start = obj.optJSONObject("start")?.optString("dateTime") ?: "",
            end = obj.optJSONObject("end")?.optString("dateTime") ?: ""
        )
    }

    private companion object {
        const val CALENDAR_SCOPE_URI = "https://www.googleapis.com/auth/calendar"
        val CALENDAR_SCOPE = Scope(CALENDAR_SCOPE_URI)
    }
}

internal fun calendarApiErrorMessage(code: Int, body: String): String {
    val reason = body.extractJsonString("reason")
    val message = body.extractJsonString("message")
    val detail = listOfNotNull(reason, message)
        .distinct()
        .joinToString(" - ")
        .takeIf { it.isNotBlank() }

    return if (detail == null) {
        "Calendar API $code"
    } else {
        "Calendar API $code: $detail"
    }
}

private fun String.extractJsonString(key: String): String? {
    val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
    return pattern.find(this)?.groupValues?.getOrNull(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\n", "\n")
        ?.replace("\\r", "\r")
        ?.replace("\\t", "\t")
        ?.takeIf { it.isNotBlank() }
}
