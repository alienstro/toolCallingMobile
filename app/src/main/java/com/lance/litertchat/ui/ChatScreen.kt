package com.lance.litertchat.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onSend: (String, String?) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onCreateImageCaptureFile: () -> File,
    onImageCaptureUri: (File) -> Uri,
    onImportImage: (Uri) -> Result<String>
) {
    var message by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var bannerMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(5_000)
            bannerMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(Color.Transparent)
    ) {
        bannerMessage?.let {
            WarningBanner(
                message = it,
                tone = bannerToneForMessage(it),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        SessionRail(
            state = state,
            onNewChat = {
                if (state.isGenerating) {
                    bannerMessage = "New chat blocked while generation is running"
                } else {
                    bannerMessage = null
                    onNewChat()
                }
            },
            onSelectChat = onSelectChat,
            onDeleteChat = {
                onDeleteChat(it)
                bannerMessage = "Chat deleted"
            }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                ChatBubble(chatMessage = chatMessage, stats = state.generationStats)
            }
        }
        Composer(
            state = state,
            message = message,
            imagePath = imagePath,
            onMessageChange = { message = it },
            onSend = {
                bannerMessage = null
                onSend(message, imagePath)
                message = ""
                imagePath = null
            },
            onStop = {
                bannerMessage = "Generation stopped"
                onStop()
            },
            onImageSelected = {
                bannerMessage = null
                imagePath = it
            },
            onClearImage = { imagePath = null },
            onBanner = { bannerMessage = it },
            onCreateImageCaptureFile = onCreateImageCaptureFile,
            onImageCaptureUri = onImageCaptureUri,
            onImportImage = onImportImage
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRail(
    state: AppState,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var pendingDeleteSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteSession = state.chatSessions.firstOrNull { it.id == pendingDeleteSessionId }
    val sessions = state.chatSessions.sortedByDescending { it.updatedAtEpochMillis }

    if (pendingDeleteSession != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSessionId = null },
            containerColor = AppPanel,
            titleContentColor = AppText,
            textContentColor = AppMuted,
            title = { Text("Delete chat?") },
            text = { Text(pendingDeleteSession.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sessionId = pendingDeleteSession.id
                        pendingDeleteSessionId = null
                        onDeleteChat(sessionId)
                    }
                ) {
                    Text("Delete", color = AppDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSessionId = null }) {
                    Text("Cancel", color = AppMuted, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SessionChip("+ New", selected = false, onClick = onNewChat, onLongClick = {})
        sessions.forEach { session ->
            SessionChip(
                text = session.title,
                selected = session.id == state.activeChatSessionId,
                onClick = { if (!state.isGenerating) onSelectChat(session.id) },
                onLongClick = {
                    if (!state.isGenerating) pendingDeleteSessionId = session.id
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(if (selected) AppAccentSoft else AppPanel, RoundedCornerShape(999.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFFE9B7A6) else AppMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun Composer(
    state: AppState,
    message: String,
    imagePath: String?,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageSelected: (String) -> Unit,
    onClearImage: () -> Unit,
    onBanner: (String) -> Unit,
    onCreateImageCaptureFile: () -> File,
    onImageCaptureUri: (File) -> Uri,
    onImportImage: (Uri) -> Result<String>
) {
    val composerControlHeight = 56.dp
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val path = pendingCapturePath
        if (captured && path != null) onImageSelected(path)
        pendingCapturePath = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = onCreateImageCaptureFile()
            pendingCapturePath = file.absolutePath
            cameraLauncher.launch(onImageCaptureUri(file))
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            onImportImage(it).onSuccess(onImageSelected).onFailure { error ->
                onBanner(error.message ?: "Image picker unavailable")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        imagePath?.let { path ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
                    .background(AppPanel, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(File(path)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .background(AppBackground, RoundedCornerShape(10.dp))
                )
                Text("Image attached", color = AppText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                CompactActionButton("Remove", onClick = onClearImage, danger = true)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppBorder, RoundedCornerShape(18.dp))
                .background(AppPanel.copy(alpha = 0.88f), RoundedCornerShape(18.dp))
                .padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                CompactActionButton(
                    "+",
                    onClick = { menuOpen = true },
                    enabled = state.canChat || state.isGenerating,
                    modifier = Modifier
                        .width(composerControlHeight)
                        .height(composerControlHeight)
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = AppPanel,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    DropdownMenuItem(
                        text = { Text("Camera", color = AppText, fontWeight = FontWeight.Bold) },
                        onClick = {
                            menuOpen = false
                            if (state.isGenerating) {
                                onBanner("Camera unavailable while generation is running")
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gallery", color = AppText, fontWeight = FontWeight.Bold) },
                        onClick = {
                            menuOpen = false
                            if (state.isGenerating) {
                                onBanner("Image picker unavailable while generation is running")
                            } else {
                                galleryLauncher.launch("image/*")
                            }
                        }
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = { Text(if (state.isGenerating) "Generation running..." else "Chat...") },
                modifier = Modifier
                    .weight(1f)
                    .height(composerControlHeight),
                enabled = !state.isGenerating,
                minLines = 1,
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    disabledTextColor = AppFaint,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = AppBackground,
                    unfocusedContainerColor = AppBackground,
                    disabledContainerColor = AppBackground
                )
            )
            CompactActionButton(
                text = if (state.isGenerating) "Stop" else ">",
                onClick = if (state.isGenerating) onStop else onSend,
                enabled = state.isGenerating || (state.canChat && (message.isNotBlank() || imagePath != null)),
                primary = !state.isGenerating,
                danger = state.isGenerating,
                modifier = Modifier
                    .width(composerControlHeight)
                    .height(composerControlHeight)
            )
        }
    }
}

@Composable
private fun ChatBubble(chatMessage: ChatMessage, stats: GenerationStats?) {
    val isUser = chatMessage.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.92f)
                .background(
                    color = if (isUser) AppAccentSoft else AppPanel.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) AppAccent.copy(alpha = 0.45f) else AppBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(12.dp)
        ) {
            if (isUser) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chatMessage.imagePath?.let { path ->
                        Image(
                            painter = rememberAsyncImagePainter(File(path)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(128.dp)
                                .background(AppBackground, RoundedCornerShape(14.dp))
                        )
                    }
                    if (chatMessage.content.isNotBlank()) Text(chatMessage.content, color = AppText)
                }
            } else {
                ChatMessageContent(chatMessage = chatMessage, stats = stats)
            }
        }
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(AppPanel.copy(alpha = 0.88f), RoundedCornerShape(18.dp))
                .border(1.dp, AppBorder, RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ChatMessageContent(chatMessage: ChatMessage, stats: GenerationStats?) {
    if (chatMessage.isLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(chatMessage.content, fontStyle = FontStyle.Italic, color = AppMuted)
            StreamingDots()
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMarkdownMessage(chatMessage.content).forEach { block ->
            when (block) {
                is MessageBlock.Heading -> Text(
                    text = block.text,
                    color = AppText,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
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

@Composable
private fun StreamingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(AppAccent, RoundedCornerShape(999.dp))
            )
        }
    }
}

private fun markdownText(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
    }
}
