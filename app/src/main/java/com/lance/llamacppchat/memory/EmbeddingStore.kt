package com.lance.llamacppchat.memory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.sqrt

class EmbeddingStore(rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val embeddingsFile = File(settingsDir, "embeddings.bin")

    @Synchronized
    fun storeEmbedding(encodedKey: String, vector: FloatArray) {
        val all = loadAll().toMutableMap()
        all[encodedKey] = vector
        saveAll(all)
    }

    @Synchronized
    fun deleteEmbedding(encodedKey: String) {
        val all = loadAll().toMutableMap()
        if (all.remove(encodedKey) != null) saveAll(all)
    }

    @Synchronized
    fun clearAll() {
        embeddingsFile.delete()
    }

    @Synchronized
    fun loadAll(): Map<String, FloatArray> {
        if (!embeddingsFile.exists()) return emptyMap()
        val result = mutableMapOf<String, FloatArray>()
        try {
            DataInputStream(FileInputStream(embeddingsFile)).use { input ->
                while (input.available() > 0) {
                    val keyLength = input.readInt()
                    val keyBytes = ByteArray(keyLength)
                    input.readFully(keyBytes)
                    val key = String(keyBytes, Charsets.UTF_8)
                    val vectorLength = input.readInt()
                    val vector = FloatArray(vectorLength) { input.readFloat() }
                    result[key] = vector
                }
            }
        } catch (_: Exception) {
            // Corrupt file — return what we managed to read
        }
        return result
    }

    fun findTopK(queryVector: FloatArray, k: Int): List<String> =
        loadAll().entries
            .map { (key, vec) -> key to cosineSimilarity(queryVector, vec) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }

    private fun saveAll(embeddings: Map<String, FloatArray>) {
        settingsDir.mkdirs()
        DataOutputStream(FileOutputStream(embeddingsFile)).use { out ->
            embeddings.forEach { (key, vector) ->
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                out.writeInt(keyBytes.size)
                out.write(keyBytes)
                out.writeInt(vector.size)
                vector.forEach { out.writeFloat(it) }
            }
        }
    }
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    if (denom == 0.0) return 0f
    return (dot / denom).toFloat()
}
