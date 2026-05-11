package com.lance.litertchat.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelRepositoryTest {
    @get:org.junit.Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesAndLoadsMetadata() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val metadata = ModelMetadata(
            fileName = "gemma-4-E2B-it.litertlm",
            absolutePath = File(root, "models/gemma-4-E2B-it.litertlm").absolutePath,
            source = "download",
            sourceUrl = ModelConstants.DEFAULT_MODEL_URL,
            sizeBytes = 1234L,
            installedAtEpochMillis = 1000L
        )

        repository.saveMetadata(metadata)

        assertEquals(metadata, repository.loadMetadata())
    }

    @Test
    fun savesAndLoadsEmptySourceUrl() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val metadata = ModelMetadata(
            fileName = "gemma-4-E2B-it.litertlm",
            absolutePath = File(root, "models/gemma-4-E2B-it.litertlm").absolutePath,
            source = "local",
            sourceUrl = "",
            sizeBytes = 1234L,
            installedAtEpochMillis = 1000L
        )

        repository.saveMetadata(metadata)

        assertEquals(metadata, repository.loadMetadata())
    }

    @Test
    fun savesAndLoadsSourceUrlContainingEquals() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val metadata = ModelMetadata(
            fileName = "gemma-4-E2B-it.litertlm",
            absolutePath = File(root, "models/gemma-4-E2B-it.litertlm").absolutePath,
            source = "download",
            sourceUrl = "https://example.test/model?signature=a=b",
            sizeBytes = 1234L,
            installedAtEpochMillis = 1000L
        )

        repository.saveMetadata(metadata)

        assertEquals(metadata, repository.loadMetadata())
    }

    @Test
    fun loadsLegacyBlankSourceUrlAsNull() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val metadataFile = File(repository.modelDirectory(), "active-model.properties")
        metadataFile.writeText(
            listOf(
                "fileName=gemma-4-E2B-it.litertlm",
                "absolutePath=${File(root, "models/gemma-4-E2B-it.litertlm").absolutePath}",
                "source=local",
                "sourceUrl=",
                "sizeBytes=1234",
                "installedAtEpochMillis=1000"
            ).joinToString(separator = "\n")
        )

        assertEquals(
            ModelMetadata(
                fileName = "gemma-4-E2B-it.litertlm",
                absolutePath = File(root, "models/gemma-4-E2B-it.litertlm").absolutePath,
                source = "local",
                sourceUrl = null,
                sizeBytes = 1234L,
                installedAtEpochMillis = 1000L
            ),
            repository.loadMetadata()
        )
    }

    @Test
    fun deleteInstalledModelClearsMetadataAndFile() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val modelFile = File(repository.modelDirectory(), "gemma-4-E2B-it.litertlm")
        modelFile.writeText("fake model")
        val metadata = ModelMetadata(
            fileName = "gemma-4-E2B-it.litertlm",
            absolutePath = modelFile.absolutePath,
            source = "download",
            sourceUrl = ModelConstants.DEFAULT_MODEL_URL,
            sizeBytes = modelFile.length(),
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(metadata)

        repository.deleteInstalledModel()

        assertNull(repository.loadMetadata())
        assertFalse(modelFile.exists())
    }

    @Test
    fun installedModelFileIgnoresModelDirectoryPath() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val modelDirectory = repository.modelDirectory()
        val metadata = ModelMetadata(
            fileName = modelDirectory.name,
            absolutePath = modelDirectory.absolutePath,
            source = "local",
            sourceUrl = null,
            sizeBytes = 0L,
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(metadata)

        assertNull(repository.installedModelFile())
    }

    @Test
    fun deleteInstalledModelDoesNotDeleteModelDirectory() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val modelDirectory = repository.modelDirectory()
        val metadata = ModelMetadata(
            fileName = modelDirectory.name,
            absolutePath = modelDirectory.absolutePath,
            source = "local",
            sourceUrl = null,
            sizeBytes = 0L,
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(metadata)

        repository.deleteInstalledModel()

        assertTrue(modelDirectory.exists())
        assertTrue(modelDirectory.isDirectory)
        assertNull(repository.loadMetadata())
    }

    @Test
    fun installedModelFileIgnoresOutsideModelDirectory() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val outsideFile = temporaryFolder.newFile("outside-model.litertlm")
        outsideFile.writeText("outside model")
        val metadata = ModelMetadata(
            fileName = "outside-model.litertlm",
            absolutePath = outsideFile.absolutePath,
            source = "local",
            sourceUrl = null,
            sizeBytes = outsideFile.length(),
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(metadata)

        assertNull(repository.installedModelFile())
    }

    @Test
    fun deleteInstalledModelDoesNotDeleteOutsideModelDirectory() {
        val root = temporaryFolder.newFolder()
        val repository = ModelRepository(root)
        val outsideFile = temporaryFolder.newFile("outside-model.litertlm")
        outsideFile.writeText("outside model")
        val metadata = ModelMetadata(
            fileName = "outside-model.litertlm",
            absolutePath = outsideFile.absolutePath,
            source = "local",
            sourceUrl = null,
            sizeBytes = outsideFile.length(),
            installedAtEpochMillis = 1000L
        )
        repository.saveMetadata(metadata)

        repository.deleteInstalledModel()

        assertTrue(outsideFile.exists())
        assertNull(repository.loadMetadata())
    }
}
