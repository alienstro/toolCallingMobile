package com.lance.litertchat.ui.chat

import com.lance.litertchat.ui.ChatMessage
import java.io.File
import java.util.Base64
import java.util.Properties

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAtEpochMillis: Long
)

data class ChatHistoryState(
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null
) {
    val activeSession: ChatSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

class ChatHistoryRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val historyFile = File(settingsDir, "chat-history.properties")

    fun loadState(): ChatHistoryState {
        if (!historyFile.exists()) return ChatHistoryState()

        val properties = Properties()
        historyFile.inputStream().use { properties.load(it) }

        val sessions = properties.getProperty(KEY_SESSION_IDS)
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { id -> loadSession(properties, id) }

        val activeSessionId = properties.getProperty(KEY_ACTIVE_SESSION_ID)
            ?.takeIf { id -> sessions.any { it.id == id } }
            ?: sessions.maxByOrNull { it.updatedAtEpochMillis }?.id

        return ChatHistoryState(sessions = sessions, activeSessionId = activeSessionId)
    }

    fun saveState(state: ChatHistoryState) {
        settingsDir.mkdirs()
        val sessions = state.sessions.map { session ->
            session.copy(messages = session.messages.filterNot { it.isLoading })
        }
        val activeSessionId = state.activeSessionId?.takeIf { id -> sessions.any { it.id == id } }

        val properties = Properties()
        properties.setProperty(KEY_SESSION_IDS, sessions.joinToString(",") { it.id })
        activeSessionId?.let { properties.setProperty(KEY_ACTIVE_SESSION_ID, it) }
        sessions.forEach { session -> saveSession(properties, session) }

        historyFile.outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private fun loadSession(properties: Properties, id: String): ChatSession? {
        val title = decode(properties.getProperty("session.$id.title")) ?: return null
        val updatedAt = properties.getProperty("session.$id.updatedAt")
            ?.toLongOrNull()
            ?: 0L
        val messageCount = properties.getProperty("session.$id.messageCount")
            ?.toIntOrNull()
            ?: 0
        val messages = (0 until messageCount).mapNotNull { index ->
            val role = decode(properties.getProperty("session.$id.message.$index.role")) ?: return@mapNotNull null
            val content = decode(properties.getProperty("session.$id.message.$index.content")) ?: return@mapNotNull null
            val imagePath = decode(properties.getProperty("session.$id.message.$index.imagePath"))
            ChatMessage(role = role, content = content, imagePath = imagePath)
        }
        return ChatSession(
            id = id,
            title = title,
            messages = messages,
            updatedAtEpochMillis = updatedAt
        )
    }

    private fun saveSession(properties: Properties, session: ChatSession) {
        properties.setProperty("session.${session.id}.title", encode(session.title))
        properties.setProperty("session.${session.id}.updatedAt", session.updatedAtEpochMillis.toString())
        properties.setProperty("session.${session.id}.messageCount", session.messages.size.toString())
        session.messages.forEachIndexed { index, message ->
            properties.setProperty("session.${session.id}.message.$index.role", encode(message.role))
            properties.setProperty("session.${session.id}.message.$index.content", encode(message.content))
            message.imagePath?.let {
                properties.setProperty("session.${session.id}.message.$index.imagePath", encode(it))
            }
        }
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String?): String? =
        value?.let { encoded -> String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }

    private companion object {
        const val KEY_SESSION_IDS = "sessionIds"
        const val KEY_ACTIVE_SESSION_ID = "activeSessionId"
    }
}
