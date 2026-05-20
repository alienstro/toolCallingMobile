package com.lance.llamacppchat.model

import java.io.File

class ModelRepository(val rootDir: File) {
    private val modelDir = File(rootDir, "models")
    private val metadataFile = File(modelDir, "active-model.properties")

    fun modelDirectory(): File {
        modelDir.mkdirs()
        return modelDir
    }

    fun embeddingModelDirectory(): File = File(rootDir, "embedding-models").also { it.mkdirs() }

    fun saveMetadata(metadata: ModelMetadata) {
        modelDirectory()
        metadataFile.writeText(
            listOf(
                "fileName=${metadata.fileName}",
                "absolutePath=${metadata.absolutePath}",
                "source=${metadata.source}",
                "sourceUrlIsNull=${metadata.sourceUrl == null}",
                "sourceUrl=${metadata.sourceUrl.orEmpty()}",
                "sizeBytes=${metadata.sizeBytes}",
                "installedAtEpochMillis=${metadata.installedAtEpochMillis}"
            ).joinToString(separator = "\n")
        )
    }

    fun loadMetadata(): ModelMetadata? {
        if (!metadataFile.exists()) return null
        val values = metadataFile.readLines()
            .mapNotNull { line ->
                val index = line.indexOf("=")
                if (index == -1) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()

        val sourceUrl = when (values["sourceUrlIsNull"]) {
            "true" -> null
            "false" -> values["sourceUrl"].orEmpty()
            else -> values["sourceUrl"].orEmpty().ifBlank { null }
        }
        return ModelMetadata(
            fileName = values.getValue("fileName"),
            absolutePath = values.getValue("absolutePath"),
            source = values.getValue("source"),
            sourceUrl = sourceUrl,
            sizeBytes = values.getValue("sizeBytes").toLong(),
            installedAtEpochMillis = values.getValue("installedAtEpochMillis").toLong()
        )
    }

    fun installedModelFile(): File? {
        return loadMetadata()?.let { safeInstalledModelFile(it) }?.takeIf { it.exists() }
    }

    fun deleteInstalledModel() {
        loadMetadata()?.let { safeInstalledModelFile(it)?.delete() }
        metadataFile.delete()
    }

    private fun safeInstalledModelFile(metadata: ModelMetadata): File? {
        val canonicalModelDirectory = modelDirectory().canonicalFile.toPath()
        val canonicalTarget = File(metadata.absolutePath).canonicalFile
        return canonicalTarget.takeIf {
            val targetPath = it.toPath()
            targetPath.startsWith(canonicalModelDirectory) &&
                targetPath != canonicalModelDirectory &&
                it.isFile
        }
    }
}
