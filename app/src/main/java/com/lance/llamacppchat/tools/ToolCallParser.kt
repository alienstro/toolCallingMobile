package com.lance.llamacppchat.tools

object ToolCallParser {
    private val codeBlockRegex = Regex("```json\\s*([\\s\\S]*?)```")

    fun parse(llmOutput: String): ToolCall? {
        val trimmedOutput = llmOutput.trim()
        val jsonString = codeBlockRegex.find(llmOutput)?.groupValues?.get(1)?.trim()
            ?: trimmedOutput.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?: return null
        return runCatching {
            val obj = JsonObjectParser(jsonString).parseObject()
            val toolName = obj["tool"] as? String ?: return null
            if (toolName.isBlank()) return null
            val args = (obj["args"] as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    if (key is String && value is Any) key to value else null
                }
                ?.toMap()
                ?: emptyMap()
            ToolCall(tool = toolName, args = args)
        }.getOrNull()
    }
}

private class JsonObjectParser(private val input: String) {
    private var index = 0

    fun parseObject(): Map<String, Any?> {
        skipWhitespace()
        expect('{')
        val map = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return map
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            map[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return map
                }
                else -> error("Expected ',' or '}'")
            }
        }
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '"' -> parseString()
            '{' -> parseObject()
            '[' -> parseArray()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val values = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return values
        }
        while (true) {
            values += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return values
                }
                else -> error("Expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < input.length) {
            when (val char = input[index++]) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(char)
            }
        }
        error("Unterminated string")
    }

    private fun parseEscape(): Char {
        if (index >= input.length) error("Unterminated escape")
        return when (val escaped = input[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > input.length) error("Invalid unicode escape")
                input.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
            }
            else -> error("Invalid escape")
        }
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index++
        while (peekOrNull()?.isDigit() == true) index++
        val isDecimal = if (peekOrNull() == '.') {
            index++
            while (peekOrNull()?.isDigit() == true) index++
            true
        } else {
            false
        }
        val hasExponent = if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            index++
            if (peekOrNull() == '+' || peekOrNull() == '-') index++
            while (peekOrNull()?.isDigit() == true) index++
            true
        } else {
            false
        }
        if (start == index) error("Expected number")
        val raw = input.substring(start, index)
        return if (isDecimal || hasExponent) {
            raw.toDouble()
        } else {
            raw.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        }
    }

    private fun parseLiteral(raw: String, value: Any?): Any? {
        if (!input.startsWith(raw, index)) error("Expected $raw")
        index += raw.length
        return value
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) index++
    }

    private fun expect(expected: Char) {
        if (peek() != expected) error("Expected '$expected'")
        index++
    }

    private fun peek(): Char = peekOrNull() ?: error("Unexpected end of input")

    private fun peekOrNull(): Char? = input.getOrNull(index)
}
