package com.lance.llamacppchat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.model.ModelConstants

@Composable
fun ModelManagerScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onDownload: (String) -> Unit,
    onDelete: () -> Unit,
    onImport: (Uri) -> Unit
) {
    var modelUrl by rememberSaveable { mutableStateOf(ModelConstants.DEFAULT_MODEL_URL) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val hardwareWarning = state.activeModel?.fileName?.let(ModelConstants::hardwareWarningForFileName)
    val runtimeFallback = state.runtimeStatus.fallbackReason
    val hasModelNotice = hardwareWarning != null || runtimeFallback != null || state.errorText != null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard {
                PanelTitle("Active model") {
                    StatusPill(
                        text = if (state.activeModel != null) "Ready" else "Missing",
                        tone = if (state.activeModel != null) PillTone.Good else PillTone.Warn,
                        withDot = true
                    )
                }
                DataTable(
                    rows = listOf(
                        "File" to (state.activeModel?.fileName ?: "No model installed"),
                        "Source" to (state.activeModel?.sourceUrl ?: state.activeModel?.source ?: "Download or import a .gguf model."),
                        "Path" to (state.activeModel?.absolutePath ?: "None"),
                        "Size" to formatByteSize(state.activeModel?.sizeBytes ?: 0L)
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        text = "Delete installed model",
                        onClick = onDelete,
                        enabled = state.activeModel != null && !state.isDownloading,
                        danger = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            AppCard {
                PanelTitle("Download model") {
                    StatusPill(
                        text = state.downloadProgressText ?: if (state.isDownloading) "Working" else "Idle",
                        tone = if (state.isDownloading) PillTone.Accent else PillTone.Neutral
                    )
                }
                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = { modelUrl = it },
                    label = { Text("HTTPS model URL") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground,
                        focusedBorderColor = AppBorder,
                        unfocusedBorderColor = AppBorder
                    )
                )
                Text(
                    text = "/blob/ links are normalized to /resolve/ before download.",
                    color = AppFaint,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                if (state.isDownloading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppBackground, RoundedCornerShape(999.dp)),
                        color = AppAccent,
                        trackColor = AppBackground
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        text = if (state.isDownloading) "Downloading..." else "Download",
                        onClick = { onDownload(modelUrl) },
                        enabled = !state.isDownloading,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        text = "Import local file",
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isDownloading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (hasModelNotice) item {
            AppCard {
                PanelTitle("Model status") {
                    StatusPill(
                        text = if (state.errorText == null) "Notice" else "Error",
                        tone = if (state.errorText == null) PillTone.Warn else PillTone.Danger
                    )
                }
                hardwareWarning?.let {
                    InfoRow(label = "Hardware", value = it)
                }
                runtimeFallback?.let {
                    InfoRow(label = "Runtime fallback", value = it)
                }
                state.errorText?.let {
                    InfoRow(
                        label = "Load error",
                        value = it
                    )
                }
            }
        }
    }
}
