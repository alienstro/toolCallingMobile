package com.lance.litertchat.settings

import java.io.File
import java.util.Properties

data class AppSettings(
    val streamResponsesEnabled: Boolean = true
)

class AppSettingsRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val settingsFile = File(settingsDir, "app-settings.properties")

    fun load(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()

        val properties = Properties()
        settingsFile.inputStream().use { properties.load(it) }
        return AppSettings(
            streamResponsesEnabled = properties
                .getProperty(KEY_STREAM_RESPONSES_ENABLED)
                ?.toBooleanStrictOrNull()
                ?: true
        )
    }

    fun setStreamResponsesEnabled(enabled: Boolean) {
        save(load().copy(streamResponsesEnabled = enabled))
    }

    private fun save(settings: AppSettings) {
        settingsDir.mkdirs()
        val properties = Properties()
        properties.setProperty(KEY_STREAM_RESPONSES_ENABLED, settings.streamResponsesEnabled.toString())
        settingsFile.outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private companion object {
        const val KEY_STREAM_RESPONSES_ENABLED = "streamResponsesEnabled"
    }
}
