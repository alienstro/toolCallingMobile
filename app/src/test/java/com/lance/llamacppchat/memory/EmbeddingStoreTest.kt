package com.lance.llamacppchat.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmbeddingStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun storeAndLoadRoundTrip() {
        val store = EmbeddingStore(temporaryFolder.root)
        val vector = floatArrayOf(1f, 0f, 0f)
        store.storeEmbedding("key1", vector)
        val loaded = EmbeddingStore(temporaryFolder.root).loadAll()
        assertEquals(1, loaded.size)
        assertTrue(loaded["key1"]!!.contentEquals(vector))
    }

    @Test
    fun storeOverwritesExistingKey() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("key1", floatArrayOf(0f, 1f, 0f))
        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertTrue(loaded["key1"]!!.contentEquals(floatArrayOf(0f, 1f, 0f)))
    }

    @Test
    fun deleteRemovesKey() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("key2", floatArrayOf(0f, 1f, 0f))
        store.deleteEmbedding("key1")
        val loaded = EmbeddingStore(temporaryFolder.root).loadAll()
        assertEquals(setOf("key2"), loaded.keys)
    }

    @Test
    fun clearAllRemovesAll() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.clearAll()
        assertTrue(EmbeddingStore(temporaryFolder.root).loadAll().isEmpty())
    }

    @Test
    fun findTopKReturnsMostSimilarKeysInOrder() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("pizza", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("weather", floatArrayOf(0f, 0f, 1f))
        store.storeEmbedding("food", floatArrayOf(0.9f, 0.1f, 0f))
        val results = store.findTopK(floatArrayOf(1f, 0f, 0f), k = 2)
        assertEquals(listOf("pizza", "food"), results)
    }

    @Test
    fun findTopKOnEmptyStoreReturnsEmpty() {
        val store = EmbeddingStore(temporaryFolder.root)
        assertTrue(store.findTopK(floatArrayOf(1f, 0f, 0f), k = 3).isEmpty())
    }

    @Test
    fun loadAllOnMissingFileReturnsEmpty() {
        assertTrue(EmbeddingStore(temporaryFolder.root).loadAll().isEmpty())
    }

    @Test
    fun cosineSimilarityIdenticalVectorsReturnsOne() {
        val v = floatArrayOf(0.5f, 0.5f, 0.5f)
        assertEquals(1.0f, cosineSimilarity(v, v), 0.001f)
    }

    @Test
    fun cosineSimilarityOrthogonalVectorsReturnsZero() {
        assertEquals(0.0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.001f)
    }

    @Test
    fun cosineSimilarityZeroVectorReturnsZero() {
        assertEquals(0.0f, cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cosineSimilarityMismatchedLengthsThrows() {
        cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f))
    }
}
