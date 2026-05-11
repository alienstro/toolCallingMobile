package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ChatScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onSend: (String) -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Chat")
        if (!state.canChat) {
            Text("Install and load a model before chatting.")
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { chatMessage ->
                Text("${chatMessage.role}: ${chatMessage.content}")
            }
        }
        state.generationStats?.let { stats ->
            GenerationStatsRow(stats = stats)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    onSend(message)
                    message = ""
                },
                enabled = state.canChat && message.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun GenerationStatsRow(stats: GenerationStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatText("⏱", String.format(Locale.US, "%.2fs", stats.elapsedSeconds))
        StatText("#", stats.totalTokens.toString())
        StatText("⚡", String.format(Locale.US, "%.1f t/s", stats.tokensPerSecond))
    }
}

@Composable
private fun StatText(icon: String, value: String) {
    Text(
        text = "$icon $value",
        style = MaterialTheme.typography.labelSmall
    )
}
