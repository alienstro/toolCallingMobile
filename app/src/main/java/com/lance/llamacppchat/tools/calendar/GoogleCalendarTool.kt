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
                        "- ${event.title}: ${event.start} to ${event.end}"
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

fun googleCalendarTools(client: GoogleCalendarClient): List<Tool> =
    listOf(ListCalendarEventsTool(client), CreateCalendarEventTool(client))
