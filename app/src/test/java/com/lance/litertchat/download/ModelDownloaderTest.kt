package com.lance.litertchat.download

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

class ModelDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    @Test
    fun trimsUrlBeforeValidation() {
        val input = "  https://example.com/model.gguf  "

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals("https://example.com/model.gguf", result)
    }

    @Test
    fun convertsHuggingFaceBlobUrlToResolveUrl() {
        val input = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/blob/main/smollm2-360m-instruct-q8_0.gguf"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(
            "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
            result
        )
    }

    @Test
    fun convertsHuggingFaceSubdomainBlobUrlToResolveUrl() {
        val input = "https://cdn.huggingface.co/repo/blob/main/model.gguf"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals("https://cdn.huggingface.co/repo/resolve/main/model.gguf", result)
    }

    @Test
    fun keepsNonHuggingFaceBlobUrlUnchanged() {
        val input = "https://example.com/repo/blob/main/model.gguf"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(input, result)
    }

    @Test
    fun keepsDirectResolveUrlUnchanged() {
        val input = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(input, result)
    }

    @Test
    fun preservesQueryWhenNormalizingHuggingFaceBlobUrl() {
        val input = "https://huggingface.co/repo/blob/main/model.gguf?download=true"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals("https://huggingface.co/repo/resolve/main/model.gguf?download=true", result)
    }

    @Test
    fun preservesFragmentWhenNormalizingHuggingFaceBlobUrl() {
        val input = "https://huggingface.co/repo/blob/main/model.gguf#section"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals("https://huggingface.co/repo/resolve/main/model.gguf#section", result)
    }

    @Test
    fun validatesExtensionUsingPathBeforeQueryAndFragment() {
        val input = "https://example.com/model.gguf?download=model.bin#readme"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(input, result)
    }

    @Test
    fun requiresHttpsScheme() {
        val input = "http://example.com/model.gguf"

        val result = runCatching { ModelDownloader.normalizeModelUrl(input) }

        assertTrue(result.isFailure)
        assertEquals("Model URL must use HTTPS.", result.exceptionOrNull()?.message)
    }

    @Test
    fun rejectsMalformedHttpsUrl() {
        val input = "https://[invalid-host]/model.gguf"

        val result = runCatching { ModelDownloader.normalizeModelUrl(input) }

        assertTrue(result.isFailure)
        assertEquals("Invalid model URL.", result.exceptionOrNull()?.message)
    }

    @Test
    fun requiresGgufExtension() {
        val input = "https://example.com/model.bin"

        val result = runCatching { ModelDownloader.normalizeModelUrl(input) }

        assertTrue(result.isFailure)
        assertEquals("Model URL must point to a .gguf file.", result.exceptionOrNull()?.message)
    }

    @Test
    fun successfulDownloadWritesDestinationAndReportsProgress() = runBlocking {
        val content = byteArrayOf(0, 1, 2, 3, 4, 127, -128, -1)
        val (server, client) = startHttpsServer()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
        val destination = File(temporaryFolder.root, "models/model.gguf")
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = ModelDownloader(client).download(server.url("/model.gguf").toString(), destination) { downloaded, total ->
            progress += downloaded to total
        }

        assertEquals(destination, result)
        assertTrue(destination.exists())
        assertTrue(content.contentEquals(destination.readBytes()))
        assertTrue(progress.isNotEmpty())
        assertEquals(content.size.toLong() to content.size.toLong(), progress.last())
    }

    @Test
    fun httpFailureThrowsAndDoesNotCreateFinalDestination() = runBlocking {
        val (server, client) = startHttpsServer()
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = File(temporaryFolder.root, "model.gguf")

        val result = runCatching {
            ModelDownloader(client).download(server.url("/model.gguf").toString(), destination) { _, _ -> }
        }

        assertTrue(result.isFailure)
        assertEquals("Download failed with HTTP 500.", result.exceptionOrNull()?.message)
        assertFalse(destination.exists())
    }

    @Test
    fun emptySuccessBodyFailsAndPreservesExistingDestination() = runBlocking {
        val (server, client) = startHttpsServer()
        server.enqueue(MockResponse().setBody(""))
        val destination = File(temporaryFolder.root, "model.gguf")
        destination.writeText("existing model")
        val tempFile = File(destination.parentFile, "${destination.name}.download")

        val result = runCatching {
            ModelDownloader(client).download(server.url("/model.gguf").toString(), destination) { _, _ -> }
        }

        assertTrue(result.isFailure)
        assertEquals("Download response was empty.", result.exceptionOrNull()?.message)
        assertEquals("existing model", destination.readText())
        assertFalse(tempFile.exists())
    }

    @Test
    fun staleTempFileIsReplacedAndMovedOnSuccess() = runBlocking {
        val (server, client) = startHttpsServer()
        server.enqueue(MockResponse().setBody("fresh model"))
        val destination = File(temporaryFolder.root, "models/model.gguf")
        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.download")
        tempFile.writeText("stale model")

        ModelDownloader(client).download(server.url("/model.gguf").toString(), destination) { _, _ -> }

        assertEquals("fresh model", destination.readText())
        assertFalse(tempFile.exists())
    }

    @Test
    fun failedProgressCallbackDeletesTempAndPreservesExistingDestination() = runBlocking {
        val (server, client) = startHttpsServer()
        server.enqueue(MockResponse().setBody("new model"))
        val destination = File(temporaryFolder.root, "models/model.gguf")
        destination.parentFile?.mkdirs()
        destination.writeText("existing model")
        val tempFile = File(destination.parentFile, "${destination.name}.download")

        val result = runCatching {
            ModelDownloader(client).download(server.url("/model.gguf").toString(), destination) { _, _ ->
                throw IOException("progress failed")
            }
        }

        assertTrue(result.isFailure)
        assertEquals("existing model", destination.readText())
        assertFalse(tempFile.exists())
    }

    private fun startHttpsServer(): Pair<MockWebServer, OkHttpClient> {
        val heldCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()

        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        this.server = server

        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        return server to client
    }
}
