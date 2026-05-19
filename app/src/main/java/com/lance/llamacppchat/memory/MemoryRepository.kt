package com.lance.llamacppchat.memory

import java.io.File
import java.util.Base64
import java.util.Properties

data class MemoryItem(
    val key: String,
    val value: String,
    val updatedAtEpochMillis: Long
)

class MemoryRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val memoryFile = File(settingsDir, "memories.properties")

    fun loadMemories(): List<MemoryItem> {
        if (!memoryFile.exists()) return emptyList()

        val properties = Properties()
        memoryFile.inputStream().use { properties.load(it) }

        return properties.getProperty(KEY_MEMORY_KEYS)
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { encodedKey ->
                val key = decode(encodedKey) ?: return@mapNotNull null
                val value = decode(properties.getProperty("memory.$encodedKey.value")) ?: return@mapNotNull null
                val updatedAt = properties.getProperty("memory.$encodedKey.updatedAt")
                    ?.toLongOrNull()
                    ?: return@mapNotNull null
                MemoryItem(key = key, value = value, updatedAtEpochMillis = updatedAt)
            }
            .sortedByDescending { it.updatedAtEpochMillis }
    }

    fun upsertMemory(key: String, value: String, now: Long = System.currentTimeMillis()) {
        val cleanedKey = cleanKey(key) ?: return
        val cleanedValue = cleanValue(value) ?: return
        val next = (
            listOf(MemoryItem(cleanedKey, cleanedValue, now)) +
                loadMemories().filterNot { it.key == cleanedKey }
            )
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(MAX_MEMORIES)
        saveMemories(next)
    }

    fun deleteMemory(key: String) {
        val cleanedKey = cleanKey(key) ?: return
        saveMemories(loadMemories().filterNot { it.key == cleanedKey })
    }

    fun captureExplicitMemories(text: String, now: Long = System.currentTimeMillis()): List<MemoryItem> {
        val captured = extractExplicitMemories(text, now)
        captured.forEach { memory -> upsertMemory(memory.key, memory.value, memory.updatedAtEpochMillis) }
        return captured
    }

    fun selectForPrompt(userPrompt: String, limit: Int = PROMPT_MEMORY_LIMIT): List<MemoryItem> {
        val memories = loadMemories()
        val pinned = memories.filter { it.key in PINNED_KEYS }
            .sortedBy { PINNED_KEYS.indexOf(it.key) }
        val words = userPrompt.lowercase()
            .split(Regex("[^a-z0-9.]+"))
            .filter { it.length >= 3 }
            .toSet()
        val relevant = memories.filter { memory ->
            memory !in pinned && words.any { word ->
                memory.key.contains(word) || memory.value.lowercase().contains(word)
            }
        }
        val recent = memories.filter { it !in pinned && it !in relevant }
        return (pinned + relevant + recent).take(limit)
    }

    private fun extractExplicitMemories(text: String, now: Long): List<MemoryItem> {
        val cleanedText = text.trim()
        val nameMatch = NAME_PATTERN.find(cleanedText)
        if (nameMatch != null) {
            val value = nameMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            return listOf(MemoryItem("user.name", value, now))
        }

        val callMeMatch = CALL_ME_PATTERN.find(cleanedText)
        if (callMeMatch != null) {
            val value = callMeMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            return listOf(MemoryItem("user.name", value, now))
        }

        val favoritePluralMatch = FAVORITE_PLURAL_PATTERN.find(cleanedText)
        if (favoritePluralMatch != null) {
            val subject = favoritePluralMatch.groupValues[1].trim()
            val value = favoritePluralMatch.groupValues[2].trim().trimEnd('.', '!', '?')
            return listOf(MemoryItem("user.favorite.$subject", value, now))
        }

        val favoriteMatch = FAVORITE_PATTERN.find(cleanedText)
        if (favoriteMatch != null) {
            val subject = favoriteMatch.groupValues[1].trim()
            val value = favoriteMatch.groupValues[2].trim().trimEnd('.', '!', '?')
            return listOf(MemoryItem("user.favorite.$subject", value, now))
        }

        val likesToEatMatch = LIKES_TO_EAT_PATTERN.find(cleanedText)
        if (likesToEatMatch != null) {
            val likesToEatValue = likesToEatMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (likesToEatValue.isNotBlank()) {
                return listOf(MemoryItem("user.likes.to.eat", likesToEatValue, now))
            }
        }

        SIMPLE_STATEMENT_PATTERNS.forEach { (key, pattern) ->
            val match = pattern.find(cleanedText)
            if (match != null) {
                val value = match.groupValues[1].trim().trimEnd('.', '!', '?')
                if (value.isNotBlank()) {
                    return listOf(MemoryItem(key, value, now))
                }
            }
        }

        val myFactMatch = MY_FACT_PATTERN.find(cleanedText) ?: return emptyList()
        val subject = myFactMatch.groupValues[1].trim().replace(Regex("\\s+"), ".")
        val value = myFactMatch.groupValues[2].trim().trimEnd('.', '!', '?')
        return listOf(MemoryItem("user.$subject", value, now))
    }

    private fun saveMemories(memories: List<MemoryItem>) {
        settingsDir.mkdirs()
        val properties = Properties()
        properties.setProperty(KEY_MEMORY_KEYS, memories.joinToString(",") { encode(it.key) })
        memories.forEach { memory ->
            val encodedKey = encode(memory.key)
            properties.setProperty("memory.$encodedKey.value", encode(memory.value))
            properties.setProperty("memory.$encodedKey.updatedAt", memory.updatedAtEpochMillis.toString())
        }
        memoryFile.outputStream().use { output -> properties.store(output, null) }
    }

    private fun cleanKey(rawKey: String): String? {
        val key = rawKey.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .take(MAX_KEY_LENGTH)
            .trim('.')
        return key.takeIf { it.isNotBlank() }
    }

    private fun cleanValue(rawValue: String): String? {
        val value = rawValue.trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_VALUE_LENGTH)
            .trim()
        return value.takeIf { it.isNotBlank() }
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String?): String? =
        value?.let { encoded -> String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }

    private companion object {
        const val KEY_MEMORY_KEYS = "memoryKeys"
        const val MAX_KEY_LENGTH = 48
        const val MAX_VALUE_LENGTH = 160
        const val MAX_MEMORIES = 40
        const val PROMPT_MEMORY_LIMIT = 6
        val PINNED_KEYS = listOf("user.name", "user.prefers")
        val NAME_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+name(?:\\s+is)?\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
        val CALL_ME_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?call\\s+me\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
        val FAVORITE_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+favou?rite\\s+([a-z0-9][a-z0-9 ]{0,31})\\s+is\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
        val FAVORITE_PLURAL_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+favou?rite\\s+([a-z0-9][a-z0-9 ]{0,31}s)\\s+are\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
        val LIKES_TO_EAT_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?i\\s+like\\s+to\\s+eat\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
        val SIMPLE_STATEMENT_PATTERNS = listOf(
            "user.likes" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+like\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.loves" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+love\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.prefers" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+prefer\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.uses" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+use\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.wants" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+want\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.is" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+am\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.from" to Regex("^(?:remember(?:\\s+that)?\\s+)?(?:i'm|i\\s+am)\\s+from\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
            "user.works.as" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+work\\s+as\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE)
        )
        val MY_FACT_PATTERN = Regex(
            pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+([a-z0-9][a-z0-9 ]{0,31})\\s+(?:is|are)\\s+([^.!?\\n]+)[.!?]?$",
            option = RegexOption.IGNORE_CASE
        )
    }
}
