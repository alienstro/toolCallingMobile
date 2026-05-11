package com.lance.litertchat.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeLlamaConfigTest {
    @Test
    fun defaultsFavorMobileCpuInference() {
        val config = NativeLlamaConfig()

        assertEquals(1024, config.contextLength)
        assertEquals(512, config.batchSize)
        assertEquals(256, config.maxTokens)
        assertEquals(4, config.threads)
        assertEquals(0.7f, config.temperature)
        assertEquals(40, config.topK)
        assertEquals(0.9f, config.topP)
    }

    @Test
    fun sanitizedClampsUnsafeValues() {
        val config = NativeLlamaConfig(
            contextLength = 0,
            batchSize = -1,
            maxTokens = 0,
            threads = -8,
            temperature = -1.0f,
            topK = -5,
            topP = 2.0f
        ).sanitized()

        assertEquals(512, config.contextLength)
        assertEquals(1, config.batchSize)
        assertEquals(1, config.maxTokens)
        assertEquals(1, config.threads)
        assertEquals(0.0f, config.temperature)
        assertEquals(0, config.topK)
        assertEquals(1.0f, config.topP)
    }

    @Test
    fun sanitizedClampsTopPToLowerBound() {
        val config = NativeLlamaConfig(topP = -0.5f).sanitized()

        assertEquals(0.0f, config.topP)
    }

    @Test
    fun sanitizedUsesDefaultTemperatureForNonFiniteValues() {
        assertEquals(0.7f, NativeLlamaConfig(temperature = Float.NaN).sanitized().temperature)
        assertEquals(0.7f, NativeLlamaConfig(temperature = Float.POSITIVE_INFINITY).sanitized().temperature)
    }

    @Test
    fun sanitizedUsesDefaultTopPForNan() {
        val config = NativeLlamaConfig(topP = Float.NaN).sanitized()

        assertEquals(0.9f, config.topP)
    }

    @Test
    fun sanitizedClampsInfiniteTopPToUpperBound() {
        val config = NativeLlamaConfig(topP = Float.POSITIVE_INFINITY).sanitized()

        assertEquals(1.0f, config.topP)
    }
}
