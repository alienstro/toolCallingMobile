package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.litertchat.diagnostics.DeviceDiagnostics

@Composable
fun DiagnosticsScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues()
) {
    val info = DeviceDiagnostics.collect(LocalContext.current.filesDir)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            SectionTitle("Health")
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill("Repository OK", PillTone.Good)
                    StatusPill(if (state.errorText == null) "No errors" else "Warning", if (state.errorText == null) PillTone.Good else PillTone.Warn)
                }
                Text(
                    text = "Current runtime checks are based on app state and device storage.",
                    color = AppMuted
                )
            }
        }
        item {
            SectionTitle("Device")
            AppCard {
                InfoRow("Device", "${info.manufacturer} ${info.model}")
                InfoRow("Android", "${info.androidVersion} API ${info.apiLevel}")
                InfoRow("Storage", "${info.availableStorageBytes} bytes available")
            }
        }
        item {
            SectionTitle("Model")
            AppCard {
                InfoRow("File", state.activeModel?.fileName ?: "None")
                InfoRow("Path", state.activeModel?.absolutePath ?: "None")
                InfoRow("Size", "${state.activeModel?.sizeBytes ?: 0} bytes")
                InfoRow("Source", state.activeModel?.source ?: "None")
            }
        }
        item {
            SectionTitle("Runtime")
            AppCard {
                InfoRow("Streaming", if (state.streamResponsesEnabled) "Enabled" else "Disabled")
                InfoRow("Messages", state.messages.size.toString())
                InfoRow("Last error", state.errorText ?: "None")
                state.generationStats?.let { stats ->
                    Text("Latest response", color = AppText, fontWeight = FontWeight.ExtraBold)
                    InfoRow("Seconds", "%.2f".format(stats.elapsedSeconds))
                    InfoRow("Tokens", stats.totalTokens.toString())
                    InfoRow("Rate", "%.1f t/s".format(stats.tokensPerSecond))
                }
            }
        }
    }
}
