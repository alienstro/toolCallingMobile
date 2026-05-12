package com.lance.litertchat.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPromptTemplateTest {
    @Test
    fun formatsSystemAndUserMessagesForQwenChat() {
        val prompt = ChatPromptTemplate.format(
            systemPrompt = "You are concise.",
            userPrompt = "What model are you?"
        )

        assertEquals(
            """
            <|im_start|>system
            You are concise.
            <|im_end|>
            <|im_start|>user
            What model are you?
            <|im_end|>
            <|im_start|>assistant
            """.trimIndent(),
            prompt
        )
    }

    @Test
    fun omitsBlankSystemMessage() {
        val prompt = ChatPromptTemplate.format(
            systemPrompt = "   ",
            userPrompt = "Hello"
        )

        assertEquals(
            """
            <|im_start|>user
            Hello
            <|im_end|>
            <|im_start|>assistant
            """.trimIndent(),
            prompt
        )
    }
}
