package com.lance.llamacppchat.tools

class ToolRegistry(private val tools: List<Tool> = emptyList()) {

    fun promptBlock(): String {
        if (tools.isEmpty()) return ""
        val toolList = tools.joinToString("\n") { tool ->
            "- ${tool.definition.name}: ${tool.definition.description}. Parameters: ${tool.definition.parametersSchema}"
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
