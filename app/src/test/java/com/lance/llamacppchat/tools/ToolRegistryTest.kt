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
        val registry = ToolRegistry(
            listOf(
                FakeTool("list_events", ToolResult("list_events", "")),
                FakeTool("create_event", ToolResult("create_event", ""))
            )
        )

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
