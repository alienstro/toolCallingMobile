package com.lance.litertchat.ui

sealed class MessageBlock {
    data class Heading(val text: String) : MessageBlock()
    data class Paragraph(val text: String) : MessageBlock()
    data class Bullet(val text: String) : MessageBlock()
    data class Table(val rows: List<List<String>>) : MessageBlock()
}

fun parseMarkdownMessage(content: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    val paragraphLines = mutableListOf<String>()
    val tableRows = mutableListOf<List<String>>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks += MessageBlock.Paragraph(paragraphLines.joinToString(" ").trim())
            paragraphLines.clear()
        }
    }

    fun flushTable() {
        if (tableRows.isNotEmpty()) {
            blocks += MessageBlock.Table(tableRows.toList())
            tableRows.clear()
        }
    }

    content.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> {
                flushParagraph()
                flushTable()
            }
            line.startsWith("### ") -> {
                flushParagraph()
                flushTable()
                blocks += MessageBlock.Heading(cleanInlineMarkdown(line.removePrefix("### ")))
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                flushTable()
                blocks += MessageBlock.Bullet(line.drop(2).trim())
            }
            isTableLine(line) -> {
                flushParagraph()
                if (!isTableSeparator(line)) {
                    tableRows += line.trim('|')
                        .split("|")
                        .map { cleanInlineMarkdown(it.trim()) }
                }
            }
            else -> {
                flushTable()
                paragraphLines += line
            }
        }
    }

    flushParagraph()
    flushTable()
    return blocks
}

fun cleanInlineMarkdown(text: String): String =
    text.replace("**", "")
        .replace("__", "")
        .trim()

private fun isTableLine(line: String): Boolean =
    line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2

private fun isTableSeparator(line: String): Boolean {
    val cells = line.trim('|').split("|").map { it.trim() }
    return cells.isNotEmpty() && cells.all { cell ->
        cell.isNotBlank() && cell.all { it == '-' || it == ':' }
    }
}
