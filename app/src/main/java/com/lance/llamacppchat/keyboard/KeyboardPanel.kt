package com.lance.llamacppchat.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.ui.AppAccent
import com.lance.llamacppchat.ui.AppBackground
import com.lance.llamacppchat.ui.AppBorder
import com.lance.llamacppchat.ui.AppFaint
import com.lance.llamacppchat.ui.AppMuted
import com.lance.llamacppchat.ui.AppPanelAlt
import com.lance.llamacppchat.ui.AppSurface
import com.lance.llamacppchat.ui.AppText
import com.lance.llamacppchat.ui.AppTheme
import com.lance.llamacppchat.ui.BannerTone
import com.lance.llamacppchat.ui.CompactActionButton
import com.lance.llamacppchat.ui.StopButton
import com.lance.llamacppchat.ui.WarningBanner
import kotlinx.coroutines.delay

@Composable
fun KeyboardPanel(
    state: KeyboardPanelState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showKeys = state is KeyboardPanelState.Idle || state is KeyboardPanelState.Loading
    // Fixed heights so the IME window never expands to full screen
    val panelHeight = if (showKeys) 110.dp else 320.dp

    AppTheme {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(AppBackground)
        ) {
            // Panel zone — fixed height prevents unbounded expansion in IME window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
            ) {
                when (state) {
                    is KeyboardPanelState.Idle -> IdlePanel(
                        inputText = inputText,
                        onInputChange = onInputChange,
                        onAsk = onAsk,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Loading -> LoadingPanel(
                        modifier = Modifier.fillMaxSize()
                    )
                    is KeyboardPanelState.Generating -> GeneratingPanel(
                        partialResponse = state.partialResponse,
                        onStop = onStop,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Done -> DonePanel(
                        response = state.response,
                        onInsert = onInsert,
                        onCopy = onCopy,
                        onReset = onReset,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Error -> ErrorPanel(
                        message = state.message,
                        onReset = onReset,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }

            // QWERTY keys zone — hidden during Generating/Done/Error to give response more space
            if (showKeys) {
                KeyboardKeys(
                    onChar = { char -> onInputChange(inputText + char) },
                    onDelete = {
                        if (inputText.isNotEmpty()) onInputChange(inputText.dropLast(1))
                    },
                    onAsk = onAsk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
            }
        }
    }
}

@Composable
private fun IdlePanel(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRewrite = inputText.startsWith("Rewrite this:")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
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
private fun LoadingPanel(modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            messageIndex = (messageIndex + 1) % LOADING_MESSAGES.size
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(28.dp))
        Text(
            text = LOADING_MESSAGES[messageIndex],
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun GeneratingPanel(
    partialResponse: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(partialResponse) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun DonePanel(
    response: String,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                text = "Insert",
                onClick = onInsert,
                primary = true,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = if (copied) "Copied" else "Copy",
                onClick = {
                    onCopy()
                    copied = true
                },
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
private fun ErrorPanel(
    message: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningBanner(message = message, tone = BannerTone.Warning)
        CompactActionButton(text = "Try again", onClick = onReset)
    }
}

// ── QWERTY Keys ───────────────────────────────────────────────────────────────

private val QWERTY_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    listOf("123", "     ", "↵")
)

private val NUMBER_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
    listOf("ABC", ".", ",", "?", "!", "'", "⌫"),
    listOf("ABC", "     ", "↵")
)

private fun keyWeight(key: String): Float = when (key) {
    "     " -> 4f
    "⌫", "↵", "123", "ABC", "⇧" -> 1.5f
    else -> 1f
}

@Composable
fun KeyboardKeys(
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNumbers by remember { mutableStateOf(false) }
    val rows = if (showNumbers) NUMBER_ROWS else QWERTY_ROWS

    Column(
        modifier = modifier
            .background(AppBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        modifier = Modifier.weight(keyWeight(key)),
                        onClick = {
                            when (key) {
                                "⌫" -> onDelete()
                                "↵" -> onAsk()
                                "⇧" -> { /* caps lock — no-op for MVP */ }
                                "123" -> showNumbers = true
                                "ABC" -> showNumbers = false
                                "     " -> onChar(" ")
                                else -> onChar(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSpecial = key in setOf("⌫", "↵", "123", "ABC", "⇧")
    val displayText = when (key) {
        "     " -> "space"
        else -> key
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSpecial) AppPanelAlt else AppSurface)
            .border(1.dp, AppBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = displayText,
            color = if (key == "↵") AppAccent else AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (key == "↵") FontWeight.Bold else FontWeight.Normal
        )
    }
}
