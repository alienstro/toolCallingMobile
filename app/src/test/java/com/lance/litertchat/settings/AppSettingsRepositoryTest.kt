package com.lance.litertchat.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun streamingIsEnabledByDefault() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        assertTrue(repository.load().streamResponsesEnabled)
    }

    @Test
    fun savesStreamingToggle() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        repository.setStreamResponsesEnabled(false)

        assertFalse(repository.load().streamResponsesEnabled)
    }
}
