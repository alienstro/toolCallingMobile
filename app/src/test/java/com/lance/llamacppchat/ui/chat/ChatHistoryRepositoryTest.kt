package com.lance.llamacppchat.ui.chat

import com.lance.llamacppchat.ui.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatHistoryRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesAndLoadsMessageImagePath() {
        val repository = ChatHistoryRepository(temporaryFolder.root)
        val session = ChatSession(
            id = "session-1",
            title = "Image chat",
            messages = listOf(
                ChatMessage("user", "Describe this", imagePath = "/tmp/photo.jpg"),
                ChatMessage("assistant", "A photo.")
            ),
            updatedAtEpochMillis = 1000L
        )

        repository.saveState(
            ChatHistoryState(
                sessions = listOf(session),
                activeSessionId = session.id
            )
        )

        assertEquals(
            ChatHistoryState(
                sessions = listOf(session),
                activeSessionId = session.id
            ),
            repository.loadState()
        )
    }
}
