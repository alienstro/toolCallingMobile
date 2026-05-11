package com.lance.litertchat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownMessageParserTest {
    @Test
    fun parsesHeadingsAndParagraphs() {
        val blocks = parseMarkdownMessage("### Summary\nThe sun is hot.")

        assertEquals(
            listOf(
                MessageBlock.Heading("Summary"),
                MessageBlock.Paragraph("The sun is hot.")
            ),
            blocks
        )
    }

    @Test
    fun parsesMarkdownTableAsRowsWithoutSeparator() {
        val blocks = parseMarkdownMessage(
            """
            | Feature | Description |
            |:--- | --- |
            | **Type** | G-type star |
            | **Function** | Provides light |
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MessageBlock.Table(
                    rows = listOf(
                        listOf("Feature", "Description"),
                        listOf("Type", "G-type star"),
                        listOf("Function", "Provides light")
                    )
                )
            ),
            blocks
        )
    }

    @Test
    fun parsesBullets() {
        val blocks = parseMarkdownMessage("- one\n- two")

        assertEquals(
            listOf(
                MessageBlock.Bullet("one"),
                MessageBlock.Bullet("two")
            ),
            blocks
        )
    }
}
