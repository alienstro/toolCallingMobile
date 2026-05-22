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

class UpdateCalendarEventTool(private val client: GoogleCalendarClient) : Tool {
    override val definition = ToolDefinition(
        name = "update_event",
        description = "Update a Google Calendar event by its ID",
        parametersSchema = "event_id: string (required), " +
            "title: string (optional), " +
            "date: YYYY-MM-DD + time: HH:MM - both required together to reschedule, " +
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
        if (duration != null && (date == null || time == null)) {
            return ToolResult(
                definition.name,
                "Provide both 'date' (YYYY-MM-DD) and 'time' (HH:MM) when changing duration_minutes.",
                isError = true
            )
        }

        return client.updateEvent(eventId, title, date, time, duration).fold(
            onSuccess = { event ->
                ToolResult(definition.name, "Event updated: '${event.title}' from ${event.start} to ${event.end}.")
            },
            onFailure = { ToolResult(definition.name, "update_event error: ${it.message}", isError = true) }
        )
    }
}

fun googleCalendarTools(client: GoogleCalendarClient): List<Tool> =
    listOf(
        ListCalendarEventsTool(client),
        CreateCalendarEventTool(client),
        DeleteCalendarEventTool(client),
        UpdateCalendarEventTool(client)
    )
