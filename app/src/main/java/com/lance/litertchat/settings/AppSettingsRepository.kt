package com.lance.litertchat.settings

import java.io.File
import java.util.Properties

data class AppSettings(
    val streamResponsesEnabled: Boolean = true,
    val gpuBackendEnabled: Boolean = false,
    val npuBackendEnabled: Boolean = false,
    val gemmaMtpEnabled: Boolean = false
)

class AppSettingsRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val settingsFile = File(settingsDir, "app-settings.properties")

    fun load(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()

        val properties = Properties()
        settingsFile.inputStream().use { properties.load(it) }
        val gpuBackendEnabled = properties.booleanValue(KEY_GPU_BACKEND_ENABLED, defaultValue = false)
        val npuBackendEnabled = !gpuBackendEnabled &&
            properties.booleanValue(KEY_NPU_BACKEND_ENABLED, defaultValue = false)
        return AppSettings(
            streamResponsesEnabled = properties.booleanValue(KEY_STREAM_RESPONSES_ENABLED, defaultValue = true),
            gpuBackendEnabled = gpuBackendEnabled,
            npuBackendEnabled = npuBackendEnabled,
            gemmaMtpEnabled = gpuBackendEnabled &&
                properties.booleanValue(KEY_GEMMA_MTP_ENABLED, defaultValue = false)
        )
    }

    fun setStreamResponsesEnabled(enabled: Boolean) {
        save(load().copy(streamResponsesEnabled = enabled))
    }

    fun setGpuBackendEnabled(enabled: Boolean) {
        val currentSettings = load()
        save(
            currentSettings.copy(
                gpuBackendEnabled = enabled,
                npuBackendEnabled = if (enabled) false else currentSettings.npuBackendEnabled,
                gemmaMtpEnabled = enabled && currentSettings.gemmaMtpEnabled
            )
        )
    }

    fun setNpuBackendEnabled(enabled: Boolean) {
        val currentSettings = load()
        save(
            currentSettings.copy(
                gpuBackendEnabled = if (enabled) false else currentSettings.gpuBackendEnabled,
                npuBackendEnabled = enabled,
                gemmaMtpEnabled = if (enabled) false else currentSettings.gemmaMtpEnabled
            )
        )
    }

    fun setGemmaMtpEnabled(enabled: Boolean) {
        val currentSettings = load()
        save(
            currentSettings.copy(
                gemmaMtpEnabled = currentSettings.gpuBackendEnabled && enabled
            )
        )
    }

    private fun save(settings: AppSettings) {
        settingsDir.mkdirs()
        val properties = Properties()
        properties.setProperty(KEY_STREAM_RESPONSES_ENABLED, settings.streamResponsesEnabled.toString())
        properties.setProperty(KEY_GPU_BACKEND_ENABLED, settings.gpuBackendEnabled.toString())
        properties.setProperty(KEY_NPU_BACKEND_ENABLED, settings.npuBackendEnabled.toString())
        properties.setProperty(KEY_GEMMA_MTP_ENABLED, settings.gemmaMtpEnabled.toString())
        settingsFile.outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private fun Properties.booleanValue(key: String, defaultValue: Boolean): Boolean =
        getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue

    private companion object {
        const val KEY_STREAM_RESPONSES_ENABLED = "streamResponsesEnabled"
        const val KEY_GPU_BACKEND_ENABLED = "gpuBackendEnabled"
        const val KEY_NPU_BACKEND_ENABLED = "npuBackendEnabled"
        const val KEY_GEMMA_MTP_ENABLED = "gemmaMtpEnabled"
    }
}
