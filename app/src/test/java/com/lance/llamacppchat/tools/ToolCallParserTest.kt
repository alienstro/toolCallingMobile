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
                mapOf(
                    "title" to "Feeding Cats",
                    "date" to "2026-05-22",
                    "time" to "15:00",
                    "duration_minutes" to 60
                )
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
