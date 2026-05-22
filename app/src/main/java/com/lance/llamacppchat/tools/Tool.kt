package com.lance.llamacppchat.tools

interface Tool {
    val definition: ToolDefinition
    suspend fun execute(args: Map<String, Any>): ToolResult
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: String
)

data class ToolCall(val tool: String, val args: Map<String, Any>)

data class ToolResult(
    val tool: String,
    val content: String,
    val isError: Boolean = false
)

const val NEEDS_SIGN_IN_SENTINEL = "ERROR:NEEDS_SIGN_IN"
