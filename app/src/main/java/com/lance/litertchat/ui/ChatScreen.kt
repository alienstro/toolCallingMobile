package com.lance.litertchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.util.Locale

@Composable
fun ChatScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(AppBackground)
    ) {
        ChatHistoryBar(
            state = state,
            onNewChat = onNewChat,
            onSelectChat = onSelectChat,
            onDeleteChat = onDeleteChat
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    AssistantBubble {
                        Text(
                            text = if (state.activeModel == null) {
                                "Install or import a GGUF model, then start chatting."
                            } else {
                                "Ask about the active model, formatter behavior, or anything you want to test."
                            },
                            color = AppText
                        )
                    }
                }
            }
            items(state.messages) { chatMessage ->
                ChatBubble(chatMessage = chatMessage)
            }
        }
        Composer(
            state = state,
            message = message,
            onMessageChange = { message = it },
            onSend = {
                onSend(message)
                message = ""
            },
            onStop = onStop
        )
    }
}

@Composable
private fun ChatHistoryBar(
    state: AppState,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sortedSessions = state.chatSessions.sortedByDescending { it.updatedAtEpochMillis }
    val currentTitle = state.activeChatSession?.title ?: "No chat selected"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurface)
            .border(1.dp, AppBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", color = AppText, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    expanded = false
                    onNewChat()
                },
                enabled = !state.isGenerating,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppAccent,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE3E5EB),
                    disabledContentColor = AppMuted
                )
            ) {
                Text("New Chat", fontWeight = FontWeight.Bold)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dropdownWidth = maxWidth
            val popupOffsetY = with(LocalDensity.current) { 54.dp.roundToPx() }
            Button(
                onClick = { expanded = true },
                enabled = !state.isGenerating && sortedSessions.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppBackground,
                    contentColor = AppText,
                    disabledContainerColor = Color(0xFFE3E5EB),
                    disabledContentColor = AppMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentTitle, maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text("v", color = AppMuted, fontWeight = FontWeight.Bold)
                }
            }

            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, popupOffsetY),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Box(
                        modifier = Modifier
                            .width(dropdownWidth)
                            .background(AppSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 256.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            sortedSessions.forEach { session ->
                                ChatHistoryMenuItem(
                                    title = session.title,
                                    selected = session.id == state.activeChatSessionId,
                                    onSelect = {
                                        expanded = false
                                        onSelectChat(session.id)
                                    },
                                    onDelete = {
                                        onDeleteChat(session.id)
                                        if (sortedSessions.size <= 1) {
                                            expanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryMenuItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (selected) AppAccentSoft else Color(0xFFF8FAFD))
            .border(
                width = 0.dp,
                color = Color.Transparent
            )
            .padding(start = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSelect,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = if (selected) AppAccent else AppText,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = AppMuted
            )
        ) {
            Text(
                title,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onDelete,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(42.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFDECEC),
                contentColor = AppDanger,
                disabledContainerColor = Color(0xFFE3E5EB),
                disabledContentColor = AppMuted
            )
        ) {
            TrashIcon(color = AppDanger)
        }
    }
}

@Composable
private fun TrashIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height

        drawLine(color, start = androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.30f), end = androidx.compose.ui.geometry.Offset(w * 0.76f, h * 0.30f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(w * 0.40f, h * 0.18f), end = androidx.compose.ui.geometry.Offset(w * 0.60f, h * 0.18f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.36f),
            size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.48f),
            style = stroke
        )
        drawLine(color, start = androidx.compose.ui.geometry.Offset(w * 0.43f, h * 0.46f), end = androidx.compose.ui.geometry.Offset(w * 0.43f, h * 0.74f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(w * 0.57f, h * 0.46f), end = androidx.compose.ui.geometry.Offset(w * 0.57f, h * 0.74f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    }
}

@Composable
private fun Composer(
    state: AppState,
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurface)
            .border(1.dp, AppBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        GenerationStatsRow(stats = state.generationStats, streaming = state.streamResponsesEnabled)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppBorder, RoundedCornerShape(23.dp))
                .background(AppSurface, RoundedCornerShape(23.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = { Text("Ask about Kotlin app state...") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp),
                enabled = !state.isGenerating,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                )
            )
            Button(
                onClick = if (state.isGenerating) onStop else onSend,
                enabled = state.isGenerating || (state.canChat && message.isNotBlank()),
                shape = RoundedCornerShape(topEnd = 23.dp, bottomEnd = 23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isGenerating) Color(0xFFFDECEC) else AppAccent,
                    contentColor = if (state.isGenerating) AppDanger else Color.White,
                    disabledContainerColor = Color(0xFFE3E5EB),
                    disabledContentColor = AppMuted
                ),
                modifier = Modifier.heightIn(min = 50.dp)
            ) {
                Text(if (state.isGenerating) "Stop" else "Send", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChatBubble(chatMessage: ChatMessage) {
    val isUser = chatMessage.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Avatar()
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.82f else 0.88f)
                .background(
                    color = when {
                        isUser -> AppAccent
                        chatMessage.isLoading -> AppAccentSoft
                        else -> AppSurface
                    },
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) AppAccent else AppBorder,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            if (isUser) {
                Text(chatMessage.content, color = Color.White)
            } else {
                ChatMessageContent(chatMessage = chatMessage)
            }
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        modifier = Modifier
            .background(AppText, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("AI", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Avatar()
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(AppSurface, RoundedCornerShape(18.dp))
                .border(1.dp, AppBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ChatMessageContent(chatMessage: ChatMessage) {
    if (chatMessage.isLoading) {
        Text(
            text = chatMessage.content,
            fontStyle = FontStyle.Italic,
            color = AppMuted
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMarkdownMessage(chatMessage.content).forEach { block ->
            when (block) {
                is MessageBlock.Heading -> Text(
                    text = block.text,
                    color = AppText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                is MessageBlock.Paragraph -> Text(markdownText(block.text), color = AppText)
                is MessageBlock.Bullet -> Row {
                    Text("-", color = AppText)
                    Spacer(Modifier.width(6.dp))
                    Text(markdownText(block.text), color = AppText)
                }
                is MessageBlock.Table -> MarkdownTable(block.rows)
            }
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        modifier = Modifier.weight(1f),
                        color = AppText,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun markdownText(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}

@Composable
private fun GenerationStatsRow(stats: GenerationStats?, streaming: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (stats != null) {
            StatusPill("${stats.totalTokens} tokens", PillTone.Accent)
            StatusPill(String.format(Locale.US, "%.2fs", stats.elapsedSeconds))
            StatusPill(String.format(Locale.US, "%.1f t/s", stats.tokensPerSecond), PillTone.Good)
        }
        StatusPill(if (streaming) "streaming" else "full response", if (streaming) PillTone.Good else PillTone.Neutral)
    }
}
