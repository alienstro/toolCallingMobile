package com.lance.litertchat.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

class AppSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun streamingIsEnabledByDefault() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        val settings = repository.load()

        assertTrue(settings.streamResponsesEnabled)
        assertFalse(settings.gpuBackendEnabled)
        assertFalse(settings.npuBackendEnabled)
        assertFalse(settings.gemmaMtpEnabled)
    }

    @Test
    fun savesStreamingToggle() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        repository.setStreamResponsesEnabled(false)

        assertFalse(repository.load().streamResponsesEnabled)
    }

    @Test
    fun invalidPersistedValuesUseDefaults() {
        writeSettingsProperties(
            "streamResponsesEnabled" to "invalid",
            "gpuBackendEnabled" to "invalid",
            "npuBackendEnabled" to "invalid",
            "gemmaMtpEnabled" to "invalid"
        )
        val repository = AppSettingsRepository(temporaryFolder.root)

        val settings = repository.load()

        assertTrue(settings.streamResponsesEnabled)
        assertFalse(settings.gpuBackendEnabled)
        assertFalse(settings.npuBackendEnabled)
        assertFalse(settings.gemmaMtpEnabled)
    }

    @Test
    fun loadClearsMtpWhenPersistedGpuIsDisabled() {
        writeSettingsProperties(
            "streamResponsesEnabled" to "true",
            "gpuBackendEnabled" to "false",
            "npuBackendEnabled" to "true",
            "gemmaMtpEnabled" to "true"
        )
        val repository = AppSettingsRepository(temporaryFolder.root)

        val settings = repository.load()

        assertFalse(settings.gpuBackendEnabled)
        assertTrue(settings.npuBackendEnabled)
        assertFalse(settings.gemmaMtpEnabled)
    }

    @Test
    fun savesGpuToggle() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        repository.setGpuBackendEnabled(true)

        assertTrue(repository.load().gpuBackendEnabled)
    }

    @Test
    fun savesNpuToggleAndDisablesGpuMtp() {
        val repository = AppSettingsRepository(temporaryFolder.root)
        repository.setGpuBackendEnabled(true)
        repository.setGemmaMtpEnabled(true)

        repository.setNpuBackendEnabled(true)

        val settings = repository.load()
        assertTrue(settings.npuBackendEnabled)
        assertFalse(settings.gpuBackendEnabled)
        assertFalse(settings.gemmaMtpEnabled)
    }

    @Test
    fun enablingGpuDisablesNpu() {
        val repository = AppSettingsRepository(temporaryFolder.root)
        repository.setNpuBackendEnabled(true)

        repository.setGpuBackendEnabled(true)

        val settings = repository.load()
        assertTrue(settings.gpuBackendEnabled)
        assertFalse(settings.npuBackendEnabled)
    }

    @Test
    fun savesMtpOnlyWhenGpuIsEnabled() {
        val repository = AppSettingsRepository(temporaryFolder.root)

        repository.setGemmaMtpEnabled(true)
        assertFalse(repository.load().gemmaMtpEnabled)

        repository.setGpuBackendEnabled(true)
        repository.setGemmaMtpEnabled(true)
        assertTrue(repository.load().gemmaMtpEnabled)
    }

    @Test
    fun disablingGpuClearsMtp() {
        val repository = AppSettingsRepository(temporaryFolder.root)
        repository.setGpuBackendEnabled(true)
        repository.setGemmaMtpEnabled(true)

        repository.setGpuBackendEnabled(false)

        val settings = repository.load()
        assertFalse(settings.gpuBackendEnabled)
        assertFalse(settings.gemmaMtpEnabled)
    }

    private fun writeSettingsProperties(vararg values: Pair<String, String>) {
        val settingsDir = temporaryFolder.newFolder("settings")
        val settingsFile = settingsDir.resolve("app-settings.properties")
        val properties = Properties()
        values.forEach { (key, value) -> properties.setProperty(key, value) }
        settingsFile.outputStream().use { output -> properties.store(output, null) }
    }
}
