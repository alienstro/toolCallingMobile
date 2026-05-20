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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.prompt.PromptFormatterRepository
import kotlinx.coroutines.delay

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
    onGemmaMtpChanged: (Boolean) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var memoryKey by rememberSaveable { mutableStateOf("") }
    var memoryValue by rememberSaveable { mutableStateOf("") }
    var bannerMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(5_000)
            bannerMessage = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        bannerMessage?.let {
            item { WarningBanner(it, bannerToneForMessage(it)) }
        }
        item {
            AppCard {
                SectionTitle("Runtime and generation")
                SettingSwitchRow(
                    title = "Stream responses",
                    help = "Show assistant text as tokens arrive.",
                    checked = state.streamResponsesEnabled,
                    onCheckedChange = onStreamResponsesChanged
                )
                InfoRow(
                    label = "llama.cpp runtime",
                    value = "CPU GGUF, 4 threads, 1024 context, 1024 max generated tokens."
                )
            }
        }
        item {
            AppCard {
                SectionTitle("AI Overlay")
                SettingSwitchRow(
                    title = "Floating AI button",
                    help = "Show a draggable AI button over all apps. Requires 'Display over other apps' permission.",
                    checked = state.overlayEnabled,
                    onCheckedChange = onOverlayChanged
                )
            }
        }
        item {
            AppCard {
                PanelTitle("Memory") {
                    StatusPill("${state.memories.size} saved")
                }
                DesignTextField(memoryKey, { memoryKey = it }, "Key", singleLine = true)
                DesignTextField(memoryValue, { memoryValue = it }, "Value", minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        text = "Save memory",
                        enabled = memoryKey.isNotBlank() && memoryValue.isNotBlank(),
                        primary = true,
                        onClick = {
                            onUpsertMemory(memoryKey, memoryValue)
                            memoryKey = ""
                            memoryValue = ""
                            bannerMessage = "Memory saved"
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
                state.memories.take(3).forEach { memory ->
                    ListItemBlock(title = memory.key, value = memory.value)
                }
            }
        }
        item {
            AppCard {
                PanelTitle("Prompt formatter") {
                    StatusPill("Active", PillTone.Accent)
                }
                DesignTextField(name, { name = it }, "Name", singleLine = true)
                DesignTextField(body, { body = it }, "Body", minLines = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        text = if (editingId == null) "Create" else "Save formatter",
                        enabled = name.isNotBlank() && body.isNotBlank(),
                        primary = true,
                        onClick = {
                            val activeEditingId = editingId
                            if (activeEditingId == null) {
                                onCreateFormatter(name, body)
                                bannerMessage = "Formatter created"
                            } else {
                                onUpdateFormatter(activeEditingId, name, body)
                                bannerMessage = "Formatter updated"
                            }
                            editingId = null
                            name = ""
                            body = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactActionButton(
                        text = "Reset default",
                        onClick = {
                            onResetDefaultFormatter()
                            bannerMessage = "Default formatter reset"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                state.promptFormatters.forEach { formatter ->
                    ListItemBlock(
                        title = formatter.name,
                        value = if (formatter.id == state.activePromptFormatterId) {
                            "Selected. ${formatter.body}"
                        } else {
                            formatter.body
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton(
                            "Use",
                            onClick = {
                                onSelectFormatter(formatter.id)
                                bannerMessage = "${formatter.name} selected"
                            },
                            modifier = Modifier.weight(1f)
                        )
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
                            danger = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        items(state.memories.drop(3)) { memory ->
            AppCard {
                ListItemBlock(title = memory.key, value = memory.value)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                        danger = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
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
}

@Composable
private fun ListItemBlock(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
            .background(AppPanelAlt.copy(alpha = 0.80f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(title, color = AppText, fontWeight = FontWeight.SemiBold)
        Text(value, color = AppFaint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.46f)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppText, fontWeight = FontWeight.SemiBold)
            Text(help, color = AppFaint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
