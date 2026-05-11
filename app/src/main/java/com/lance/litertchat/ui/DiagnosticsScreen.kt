package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lance.litertchat.diagnostics.DeviceDiagnostics

@Composable
fun DiagnosticsScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues()
) {
    val info = DeviceDiagnostics.collect(LocalContext.current.filesDir)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Diagnostics")
        Text("Device: ${info.manufacturer} ${info.model}")
        Text("Android: ${info.androidVersion} API ${info.apiLevel}")
        Text("Available app storage: ${info.availableStorageBytes} bytes")
        Text("Model path: ${state.activeModel?.absolutePath ?: "None"}")
        Text("Model size bytes: ${state.activeModel?.sizeBytes ?: 0}")
        Text("Last error: ${state.errorText ?: "None"}")
    }
}
