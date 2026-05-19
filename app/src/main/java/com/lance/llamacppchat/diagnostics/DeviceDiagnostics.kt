package com.lance.llamacppchat.diagnostics

import android.os.Build
import java.io.File

data class DiagnosticsInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val availableStorageBytes: Long
)

object DeviceDiagnostics {
    fun collect(filesDir: File): DiagnosticsInfo =
        DiagnosticsInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            availableStorageBytes = filesDir.usableSpace
        )
}
