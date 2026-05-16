package com.lance.litertchat.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.litertchat.prompt.PromptFormatterRepository

@Composable
fun SettingsScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onCreateFormatter: (String, String) -> Unit,
    onUpdateFormatter: (String, String, String) -> Unit,
    onDeleteFormatter: (String) -> Unit,
    onSelectFormatter: (String) -> Unit,
    onResetDefaultFormatter: () -> Unit,
    onUpsertMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onStreamResponsesChanged: (Boolean) -> Unit,
    onGpuBackendChanged: (Boolean) -> Unit,
    onNpuBackendChanged: (Boolean) -> Unit,
    onGemmaMtpChanged: (Boolean) -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var memoryKey by rememberSaveable { mutableStateOf("") }
    var memoryValue by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            SectionTitle("Generation")
            AppCard {
                SettingSwitchRow(
                    title = "Stream responses",
                    help = "Show assistant text as it arrives.",
                    checked = state.streamResponsesEnabled,
                    onCheckedChange = onStreamResponsesChanged
                )
                SettingSwitchRow(
                    title = "Use GPU backend",
                    help = "Run LiteRT-LM with the Android GPU backend when available.",
                    checked = state.gpuBackendEnabled,
                    onCheckedChange = onGpuBackendChanged
                )
                SettingSwitchRow(
                    title = "Use NPU backend",
                    help = "Try the experimental LiteRT-LM NPU backend for supported devices and models.",
                    checked = state.npuBackendEnabled,
                    onCheckedChange = onNpuBackendChanged
                )
                SettingSwitchRow(
                    title = "Enable Gemma 4 MTP",
                    help = "Use speculative decoding for MTP-capable Gemma 4 LiteRT-LM models.",
                    checked = state.gemmaMtpEnabled,
                    enabled = state.gpuBackendEnabled,
                    onCheckedChange = onGemmaMtpChanged
                )
            }
        }
        item {
            SectionTitle("Memory")
            AppCard {
                OutlinedTextField(
                    value = memoryKey,
                    onValueChange = { memoryKey = it },
                    label = { Text("Memory key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground
                    )
                )
                OutlinedTextField(
                    value = memoryValue,
                    onValueChange = { memoryValue = it },
                    label = { Text("Memory value") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactActionButton(
                        text = "Save",
                        enabled = memoryKey.isNotBlank() && memoryValue.isNotBlank(),
                        primary = true,
                        onClick = {
                            onUpsertMemory(memoryKey, memoryValue)
                            memoryKey = ""
                            memoryValue = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        text = "Clear",
                        onClick = {
                            memoryKey = ""
                            memoryValue = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SectionTitle("Saved memories")
        }
        items(state.memories) { memory ->
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(memory.key, color = AppText, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = memory.value,
                        color = AppMuted,
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                            .background(AppBackground, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactActionButton(
                        "Edit",
                        onClick = {
                            memoryKey = memory.key
                            memoryValue = memory.value
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        "Delete",
                        onClick = { onDeleteMemory(memory.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SectionTitle("Formatter editor")
            AppCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Formatter name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground
                    )
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Prompt formatter") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactActionButton(
                        text = if (editingId == null) "Create" else "Save",
                        enabled = name.isNotBlank() && body.isNotBlank(),
                        primary = true,
                        onClick = {
                            val activeEditingId = editingId
                            if (activeEditingId == null) {
                                onCreateFormatter(name, body)
                            } else {
                                onUpdateFormatter(activeEditingId, name, body)
                            }
                            editingId = null
                            name = ""
                            body = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        text = "New",
                        onClick = {
                            editingId = null
                            name = ""
                            body = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        text = "Reset",
                        onClick = onResetDefaultFormatter,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SectionTitle("Formatters")
        }
        items(state.promptFormatters) { formatter ->
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatter.name, color = AppText, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = formatter.body,
                            color = AppMuted,
                            maxLines = 4,
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                                .background(AppBackground, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        )
                    }
                    if (formatter.id == state.activePromptFormatterId) {
                        StatusPill("Active", PillTone.Good)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactActionButton("Use", onClick = { onSelectFormatter(formatter.id) }, modifier = Modifier.weight(1f))
                    CompactActionButton(
                        "Edit",
                        onClick = {
                            editingId = formatter.id
                            name = formatter.name
                            body = formatter.body
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        "Delete",
                        enabled = formatter.id != PromptFormatterRepository.DEFAULT_FORMATTER_ID,
                        onClick = { onDeleteFormatter(formatter.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    help: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppText, fontWeight = FontWeight.Bold)
            Text(help, color = AppMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
