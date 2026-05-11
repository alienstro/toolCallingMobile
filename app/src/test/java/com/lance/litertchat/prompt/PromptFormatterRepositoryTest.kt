package com.lance.litertchat.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PromptFormatterRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadsDefaultFormatterWhenNoSettingsExist() {
        val repository = PromptFormatterRepository(temporaryFolder.root)

        val state = repository.loadState()

        assertEquals(PromptFormatterRepository.DEFAULT_FORMATTER_ID, state.activeFormatterId)
        assertEquals(1, state.formatters.size)
        assertEquals(PromptFormatterRepository.DEFAULT_FORMATTER_ID, state.activeFormatter?.id)
        assertFalse(state.activeFormatter?.isCustom ?: true)
    }

    @Test
    fun createsCustomFormatterAndSelectsIt() {
        val repository = PromptFormatterRepository(temporaryFolder.root)

        val formatter = repository.createFormatter("Mobile", "Use short bullets.")
        repository.selectFormatter(formatter.id)
        val state = repository.loadState()

        assertEquals(formatter.id, state.activeFormatterId)
        assertEquals("Mobile", state.activeFormatter?.name)
        assertEquals("Use short bullets.", state.activeFormatter?.body)
        assertTrue(state.activeFormatter?.isCustom ?: false)
    }

    @Test
    fun updatesCustomFormatter() {
        val repository = PromptFormatterRepository(temporaryFolder.root)
        val formatter = repository.createFormatter("Draft", "Old")

        repository.updateFormatter(formatter.id, "Final", "New")
        val updated = repository.loadState().formatters.first { it.id == formatter.id }

        assertEquals("Final", updated.name)
        assertEquals("New", updated.body)
    }

    @Test
    fun deletesCustomFormatterAndFallsBackToDefaultWhenActive() {
        val repository = PromptFormatterRepository(temporaryFolder.root)
        val formatter = repository.createFormatter("Temporary", "Prompt")
        repository.selectFormatter(formatter.id)

        repository.deleteFormatter(formatter.id)
        val state = repository.loadState()

        assertEquals(PromptFormatterRepository.DEFAULT_FORMATTER_ID, state.activeFormatterId)
        assertTrue(state.formatters.none { it.id == formatter.id })
    }

    @Test
    fun resetDefaultRestoresDefaultBody() {
        val repository = PromptFormatterRepository(temporaryFolder.root)

        repository.updateFormatter(
            PromptFormatterRepository.DEFAULT_FORMATTER_ID,
            "Default",
            "Changed"
        )
        repository.resetDefaultFormatter()

        assertEquals(
            PromptFormatterRepository.DEFAULT_FORMATTER_BODY,
            repository.loadState().formatters.first {
                it.id == PromptFormatterRepository.DEFAULT_FORMATTER_ID
            }.body
        )
    }
}
