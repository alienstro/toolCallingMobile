package com.lance.litertchat.ui

import java.util.Locale

fun formatModelSize(value: Long): String {
    if (value >= BYTES_PER_GB) {
        return String.format(Locale.US, "%.1f GB", value / BYTES_PER_GB.toDouble())
            .replace(".0 GB", " GB")
    }

    if (value <= 0L) {
        return "0 MB"
    }
    val megabytes = value.coerceAtLeast(BYTES_PER_MB) / BYTES_PER_MB.toDouble()
    return String.format(Locale.US, "%.1f MB", megabytes)
        .replace(".0 MB", " MB")
}

private const val BYTES_PER_MB = 1_000_000L
private const val BYTES_PER_GB = 1_000_000_000L
