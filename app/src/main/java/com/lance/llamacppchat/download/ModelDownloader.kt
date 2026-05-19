package com.lance.llamacppchat.download

import com.lance.llamacppchat.model.ModelConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

interface ModelDownloadClient {
    suspend fun download(
        rawUrl: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): File
}

class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient()
) : ModelDownloadClient {
    override suspend fun download(
        rawUrl: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = normalizeModelUrl(rawUrl)
        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.download")
        if (tempFile.exists()) tempFile.delete()

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed with HTTP ${response.code}.")
                }
                val body = response.body ?: error("Download response was empty.")
                val total = body.contentLength().takeIf { it >= 0L }
                var downloaded = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }

            check(tempFile.length() > 0L) { "Download response was empty." }
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
        destination
    }

    companion object {
        fun normalizeModelUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val uri = parseModelUri(trimmed)

            require(uri.scheme.equals("https", ignoreCase = true)) { "Model URL must use HTTPS." }
            require(uri.path.endsWith(ModelConstants.MODEL_EXTENSION)) {
                "Model URL must point to a ${ModelConstants.MODEL_EXTENSION} file."
            }

            if (!isHuggingFaceHost(uri.host)) {
                return trimmed
            }

            val normalizedPath = uri.rawPath.replace("/blob/", "/resolve/")
            return if (normalizedPath == uri.rawPath) {
                trimmed
            } else {
                buildUrlWithPath(uri, normalizedPath)
            }
        }

        private fun parseModelUri(trimmed: String): URI {
            val uri = try {
                URI(trimmed)
            } catch (_: URISyntaxException) {
                throw IllegalArgumentException("Invalid model URL.")
            }

            if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank() || uri.path.isNullOrBlank()) {
                throw IllegalArgumentException("Invalid model URL.")
            }

            return uri
        }

        private fun isHuggingFaceHost(host: String): Boolean {
            val lowerHost = host.lowercase(Locale.US)
            return lowerHost == "huggingface.co" || lowerHost.endsWith(".huggingface.co")
        }

        private fun buildUrlWithPath(uri: URI, path: String): String {
            return buildString {
                append(uri.scheme)
                append("://")
                append(uri.rawAuthority)
                append(path)
                uri.rawQuery?.let { append('?').append(it) }
                uri.rawFragment?.let { append('#').append(it) }
            }
        }
    }
}
