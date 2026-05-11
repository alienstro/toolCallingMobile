package com.lance.litertchat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.litertchat.model.ModelConstants

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            SectionTitle("Active model")
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.activeModel?.fileName ?: "No model installed",
                            color = AppText,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = state.activeModel?.absolutePath ?: "Download or import a .litertlm model.",
                            color = AppMuted,
                            maxLines = 2
                        )
                    }
                    StatusPill(
                        text = if (state.activeModel != null) "Ready" else "Missing",
                        tone = if (state.activeModel != null) PillTone.Good else PillTone.Warn
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MetricBox(
                        value = formatBytes(state.activeModel?.sizeBytes ?: 0L),
                        label = "Size",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        value = state.activeModel?.source ?: "none",
                        label = "Source",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        value = if (state.streamResponsesEnabled) "on" else "off",
                        label = "Stream",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SectionTitle("Model URL")
            OutlinedTextField(
                value = modelUrl,
                onValueChange = { modelUrl = it },
                label = { Text("Model URL") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppSurface,
                    unfocusedContainerColor = AppSurface
                )
            )
        }
        item {
            SectionTitle("Actions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactActionButton(
                    text = "Download",
                    onClick = { onDownload(modelUrl) },
                    enabled = !state.isDownloading,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
                    text = "Import",
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isDownloading,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactActionButton(
                    text = "Delete",
                    onClick = onDelete,
                    enabled = state.activeModel != null,
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
                    text = if (state.isDownloading) "Downloading" else "Idle",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            if (state.downloadProgressText != null || state.errorText != null) {
                SectionTitle("Status")
                AppCard {
                    state.downloadProgressText?.let { StatusPill(it, PillTone.Accent) }
                    state.errorText?.let { Text(it, color = AppDanger) }
                }
            }
        }
    }
}

private fun formatBytes(value: Long): String =
    when {
        value <= 0L -> "0"
        value >= 1_000_000_000L -> "${value / 1_000_000_000L} GB"
        value >= 1_000_000L -> "${value / 1_000_000L} MB"
        value >= 1_000L -> "${value / 1_000L} KB"
        else -> "$value B"
    }
