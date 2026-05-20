package com.lance.llamacppchat.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.keyboard.KeyboardPanelState
import com.lance.llamacppchat.keyboard.LOADING_MESSAGES
import com.lance.llamacppchat.ui.AppAccent
import com.lance.llamacppchat.ui.AppBackground
import com.lance.llamacppchat.ui.AppBorder
import com.lance.llamacppchat.ui.AppFaint
import com.lance.llamacppchat.ui.AppMuted
import com.lance.llamacppchat.ui.AppSurface
import com.lance.llamacppchat.ui.AppText
import com.lance.llamacppchat.ui.AppTheme
import com.lance.llamacppchat.ui.BannerTone
import com.lance.llamacppchat.ui.CompactActionButton
import com.lance.llamacppchat.ui.StopButton
import com.lance.llamacppchat.ui.WarningBanner
import kotlinx.coroutines.delay

@Composable
fun OverlayPanel(
    state: KeyboardPanelState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppTheme {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(AppBackground, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .border(
                    1.dp, AppBorder,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}  // absorb touches so they don't reach the scrim behind
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(AppBorder, RoundedCornerShape(2.dp))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 16.dp)
            ) {
                when (state) {
                    is KeyboardPanelState.Idle -> IdleContent(
                        inputText = inputText,
                        onInputChange = onInputChange,
                        onAsk = onAsk,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Loading -> LoadingContent(
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Generating -> GeneratingContent(
                        partialResponse = state.partialResponse,
                        onStop = onStop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Done -> DoneContent(
                        response = state.response,
                        onCopy = onCopy,
                        onReset = onReset,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Error -> ErrorContent(
                        message = state.message,
                        onReset = onReset,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRewrite = inputText.startsWith("Rewrite this:")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isRewrite) {
            Text(
                text = "Selected text detected",
                color = AppAccent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = {
                    Text("Ask AI…", color = AppFaint, style = MaterialTheme.typography.bodySmall)
                },
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppSurface,
                    unfocusedContainerColor = AppSurface,
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AppAccent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            CompactActionButton(
                text = "Ask",
                onClick = onAsk,
                enabled = inputText.isNotBlank(),
                primary = true,
                modifier = Modifier.widthIn(min = 64.dp)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            messageIndex = (messageIndex + 1) % LOADING_MESSAGES.size
        }
    }
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(28.dp))
        Text(
            text = LOADING_MESSAGES[messageIndex],
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GeneratingContent(
    partialResponse: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(partialResponse) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Generating…",
                color = AppFaint,
                style = MaterialTheme.typography.labelSmall
            )
            StopButton(onClick = onStop, modifier = Modifier.size(36.dp))
        }
        Text(
            text = partialResponse,
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        )
    }
}

@Composable
private fun DoneContent(
    response: String,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = response,
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactActionButton(
                text = if (copied) "Copied!" else "Copy",
                onClick = { onCopy(); copied = true },
                primary = true,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = "Ask again",
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningBanner(message = message, tone = BannerTone.Warning)
        CompactActionButton(text = "Try again", onClick = onReset)
    }
}
