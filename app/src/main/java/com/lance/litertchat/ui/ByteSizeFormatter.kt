package com.lance.litertchat.ui

import java.util.Locale

fun formatByteSize(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    val megabytes = value / BYTES_PER_MB
    return if (megabytes < 1024.0) {
        String.format(Locale.US, "%.1f MB", megabytes)
    } else {
        String.format(Locale.US, "%.2f GB", value / BYTES_PER_GB)
    }
}

private const val BYTES_PER_MB = 1_000_000.0
private const val BYTES_PER_GB = 1_000_000_000.0
