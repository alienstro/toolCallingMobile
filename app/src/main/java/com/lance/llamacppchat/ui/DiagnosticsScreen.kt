package com.lance.llamacppchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.diagnostics.DeviceDiagnostics
import com.lance.llamacppchat.memory.MemoryItem
import com.lance.llamacppchat.memory.MemoryRepository
import java.io.File

@Composable
fun DiagnosticsScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues()
) {
    val filesDir = LocalContext.current.filesDir
    val info = DeviceDiagnostics.collect(filesDir)
    val storedMemories = loadDiagnosticMemories(filesDir)
    val totalSessionMessages = state.chatSessions.sumOf { it.messages.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard {
                PanelTitle("Health") {
                    StatusPill(if (state.errorText == null) "Good" else "Degraded", if (state.errorText == null) PillTone.Good else PillTone.Warn)
                }
                HealthCard("Repository status", "Clean build target. App-private storage available.")
                HealthCard("Current error state", state.errorText ?: "No active errors.")
            }
        }
        item {
            AppCard {
                SectionTitle("Device")
                DataTable(
                    rows = listOf(
                        "Manufacturer" to info.manufacturer,
                        "Model" to info.model,
                        "Android" to "${info.androidVersion} / API ${info.apiLevel}",
                        "App storage" to "${formatByteSize(info.availableStorageBytes)} available"
                    )
                )
            }
        }
        item {
            AppCard {
                SectionTitle("Runtime")
                DataTable(
                    rows = listOf(
                        "Requested" to state.runtimeStatus.requested.label,
                        "Active" to state.runtimeStatus.active.label,
                        "Fallback" to (state.runtimeStatus.fallbackReason ?: "None"),
                        "Streaming" to if (state.streamResponsesEnabled) "Enabled" else "Disabled",
                        "Messages" to "${state.messages.size} current / $totalSessionMessages across ${state.chatSessions.size} sessions",
                        "Latest error" to (state.errorText ?: "None")
                    )
                )
            }
        }
        item {
            AppCard {
                SectionTitle("Model")
                DataTable(
                    rows = listOf(
                        "File" to (state.activeModel?.fileName ?: "None"),
                        "Path" to (state.activeModel?.absolutePath ?: "None"),
                        "Size" to formatByteSize(state.activeModel?.sizeBytes ?: 0L),
                        "Source" to (state.activeModel?.sourceUrl ?: state.activeModel?.source ?: "None")
                    )
                )
            }
        }
        item {
            AppCard {
                PanelTitle("Generation") {
                    StatusPill("Latest", if (state.generationStats == null) PillTone.Neutral else PillTone.Good)
                }
                val stats = state.generationStats
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricBox(stats?.totalTokens?.toString() ?: "0", "tokens", Modifier.weight(1f))
                    MetricBox(stats?.let { "%.1fs".format(it.elapsedSeconds) } ?: "0.0s", "elapsed", Modifier.weight(1f))
                    MetricBox(stats?.let { "%.1f".format(it.tokensPerSecond) } ?: "0.0", "tok/s", Modifier.weight(1f))
                }
            }
        }
        item {
            AppCard {
                PanelTitle("Memories") {
                    StatusPill("${storedMemories.size} entries")
                }
                DataTable(rows = diagnosticMemoryRows(storedMemories))
            }
        }
    }
}

fun diagnosticMemoryRows(memories: List<MemoryItem>): List<Pair<String, String>> {
    if (memories.isEmpty()) return listOf("Stored memories" to "None")

    return listOf("Stored memories" to memories.size.toString()) +
        memories.map { memory -> memory.key to memory.value }
}

fun loadDiagnosticMemories(rootDir: File): List<MemoryItem> =
    MemoryRepository(rootDir).loadMemories()

@Composable
private fun HealthCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
            .background(AppPanelAlt.copy(alpha = 0.80f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(label, color = AppText, fontWeight = FontWeight.SemiBold)
        Text(value, color = AppFaint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
    }
}
