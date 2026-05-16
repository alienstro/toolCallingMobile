package com.lance.litertchat.ui

import com.lance.litertchat.memory.MemoryItem
import com.lance.litertchat.memory.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsMemoryRowsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun diagnosticMemoryRowsShowsEmptyStateWhenNoMemoriesExist() {
        assertEquals(
            listOf("Stored memories" to "None"),
            diagnosticMemoryRows(emptyList())
        )
    }

    @Test
    fun diagnosticMemoryRowsShowsStoredMemoryKeyValuePairs() {
        val memories = listOf(
            MemoryItem("user.name", "Lance", 1000L),
            MemoryItem("project.current", "LiteRT Android app", 2000L)
        )

        assertEquals(
            listOf(
                "Stored memories" to "2",
                "user.name" to "Lance",
                "project.current" to "LiteRT Android app"
            ),
            diagnosticMemoryRows(memories)
        )
    }

    @Test
    fun loadDiagnosticMemoriesReadsPersistedMemoryFile() {
        val repository = MemoryRepository(temporaryFolder.root)
        repository.upsertMemory("user.name", "Lance", now = 1000L)

        assertEquals(
            listOf(MemoryItem("user.name", "Lance", 1000L)),
            loadDiagnosticMemories(temporaryFolder.root)
        )
    }
}
