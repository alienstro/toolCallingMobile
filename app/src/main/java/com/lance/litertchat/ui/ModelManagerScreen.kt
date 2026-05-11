package com.lance.litertchat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImport(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Model Manager")
        Text(state.activeModel?.fileName ?: "No model installed")
        OutlinedTextField(
            value = modelUrl,
            onValueChange = { modelUrl = it },
            label = { Text("Model URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onDownload(modelUrl) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isDownloading
            ) {
                Text("Download")
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isDownloading
            ) {
                Text("Import .litertlm")
            }
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.activeModel != null
            ) {
                Text("Delete model")
            }
        }
        state.downloadProgressText?.let { progress ->
            Text(progress)
        }
        state.errorText?.let { error ->
            Text(error)
        }
    }
}
