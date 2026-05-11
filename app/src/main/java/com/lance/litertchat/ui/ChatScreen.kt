package com.lance.litertchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ChatScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onSend: (String) -> Unit,
    onStop: () -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(AppBackground)
    ) {
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
                                "Install or import a LiteRT model, then start chatting."
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
