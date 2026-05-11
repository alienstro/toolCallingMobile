package com.lance.litertchat.prompt

import java.io.File
import java.util.Base64
import java.util.Properties
import java.util.UUID

data class PromptFormatter(
    val id: String,
    val name: String,
    val body: String,
    val isCustom: Boolean
)

data class PromptFormatterState(
    val formatters: List<PromptFormatter>,
    val activeFormatterId: String
) {
    val activeFormatter: PromptFormatter?
        get() = formatters.firstOrNull { it.id == activeFormatterId }
}

class PromptFormatterRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val settingsFile = File(settingsDir, "prompt-formatters.properties")

    fun loadState(): PromptFormatterState {
        if (!settingsFile.exists()) return defaultState()

        val properties = Properties()
        settingsFile.inputStream().use { properties.load(it) }

        val customIds = properties.getProperty(KEY_CUSTOM_IDS)
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }

        val defaultFormatter = PromptFormatter(
            id = DEFAULT_FORMATTER_ID,
            name = decode(properties.getProperty("formatter.$DEFAULT_FORMATTER_ID.name"))
                ?: DEFAULT_FORMATTER_NAME,
            body = decode(properties.getProperty("formatter.$DEFAULT_FORMATTER_ID.body"))
                ?: DEFAULT_FORMATTER_BODY,
            isCustom = false
        )
        val customFormatters = customIds.mapNotNull { id ->
            val name = decode(properties.getProperty("formatter.$id.name")) ?: return@mapNotNull null
            val body = decode(properties.getProperty("formatter.$id.body")) ?: return@mapNotNull null
            PromptFormatter(id = id, name = name, body = body, isCustom = true)
        }
        val formatters = listOf(defaultFormatter) + customFormatters
        val activeId = properties.getProperty(KEY_ACTIVE_ID)
            ?.takeIf { id -> formatters.any { it.id == id } }
            ?: DEFAULT_FORMATTER_ID

        return PromptFormatterState(formatters = formatters, activeFormatterId = activeId)
    }

    fun createFormatter(name: String, body: String): PromptFormatter {
        val formatter = PromptFormatter(
            id = UUID.randomUUID().toString(),
            name = cleanName(name),
            body = body.trim(),
            isCustom = true
        )
        saveState(loadState().let { state ->
            state.copy(formatters = state.formatters + formatter)
        })
        return formatter
    }

    fun updateFormatter(id: String, name: String, body: String) {
        val updatedFormatters = loadState().formatters.map { formatter ->
            if (formatter.id == id) {
                formatter.copy(
                    name = cleanName(name),
                    body = body.trim()
                )
            } else {
                formatter
            }
        }
        saveState(loadState().copy(formatters = updatedFormatters))
    }

    fun deleteFormatter(id: String) {
        if (id == DEFAULT_FORMATTER_ID) return

        val state = loadState()
        val nextActiveId = if (state.activeFormatterId == id) {
            DEFAULT_FORMATTER_ID
        } else {
            state.activeFormatterId
        }
        saveState(
            state.copy(
                formatters = state.formatters.filterNot { it.id == id },
                activeFormatterId = nextActiveId
            )
        )
    }

    fun selectFormatter(id: String) {
        val state = loadState()
        if (state.formatters.none { it.id == id }) return
        saveState(state.copy(activeFormatterId = id))
    }

    fun resetDefaultFormatter() {
        val state = loadState()
        saveState(
            state.copy(
                formatters = state.formatters.map { formatter ->
                    if (formatter.id == DEFAULT_FORMATTER_ID) {
                        formatter.copy(
                            name = DEFAULT_FORMATTER_NAME,
                            body = DEFAULT_FORMATTER_BODY
                        )
                    } else {
                        formatter
                    }
                }
            )
        )
    }

    private fun saveState(state: PromptFormatterState) {
        settingsDir.mkdirs()
        val properties = Properties()
        val formatters = state.formatters.ifEmpty { defaultState().formatters }
        val activeId = state.activeFormatterId.takeIf { id ->
            formatters.any { it.id == id }
        } ?: DEFAULT_FORMATTER_ID

        properties.setProperty(KEY_ACTIVE_ID, activeId)
        properties.setProperty(
            KEY_CUSTOM_IDS,
            formatters.filter { it.isCustom }.joinToString(",") { it.id }
        )
        formatters.forEach { formatter ->
            properties.setProperty("formatter.${formatter.id}.name", encode(formatter.name))
            properties.setProperty("formatter.${formatter.id}.body", encode(formatter.body))
        }
        settingsFile.outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private fun defaultState(): PromptFormatterState =
        PromptFormatterState(
            formatters = listOf(
                PromptFormatter(
                    id = DEFAULT_FORMATTER_ID,
                    name = DEFAULT_FORMATTER_NAME,
                    body = DEFAULT_FORMATTER_BODY,
                    isCustom = false
                )
            ),
            activeFormatterId = DEFAULT_FORMATTER_ID
        )

    private fun cleanName(name: String): String =
        name.trim().ifBlank { "Untitled formatter" }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String?): String? =
        value?.let { encoded ->
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        }

    companion object {
        const val DEFAULT_FORMATTER_ID = "default"
        const val DEFAULT_FORMATTER_NAME = "Mobile Friendly"
        val DEFAULT_FORMATTER_BODY = """
            You are a helpful mobile assistant.
            Format answers for mobile screens:
            - Use short paragraphs.
            - Avoid markdown tables unless necessary.
            - Prefer bullets for comparisons.
            - Keep code blocks only when needed.
        """.trimIndent()

        private const val KEY_ACTIVE_ID = "activeFormatterId"
        private const val KEY_CUSTOM_IDS = "customFormatterIds"
    }
}
