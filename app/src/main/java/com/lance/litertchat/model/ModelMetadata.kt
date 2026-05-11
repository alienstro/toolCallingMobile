package com.lance.litertchat.model

data class ModelMetadata(
    val fileName: String,
    val absolutePath: String,
    val source: String,
    val sourceUrl: String?,
    val sizeBytes: Long,
    val installedAtEpochMillis: Long
)
