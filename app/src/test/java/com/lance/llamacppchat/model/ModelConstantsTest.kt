package com.lance.llamacppchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun defaultModelUrlMatchesUnslothQwen35Q8GgufResolveUrl() {
        assertEquals(
            "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
            ModelConstants.DEFAULT_MODEL_URL
        )
    }

    @Test
    fun defaultModelRepoIsUnslothQwen35GgufRepo() {
        assertEquals(
            "unsloth/Qwen3.5-0.8B-GGUF",
            ModelConstants.DEFAULT_MODEL_REPOSITORY
        )
    }

    @Test
    fun defaultModelFileIsQwen35Q8Gguf() {
        assertEquals(
            "Qwen3.5-0.8B-Q8_0.gguf",
            ModelConstants.DEFAULT_MODEL_FILE
        )
    }

    @Test
    fun modelExtensionIsGguf() {
        assertEquals(".gguf", ModelConstants.MODEL_EXTENSION)
    }

    @Test
    fun sm8750ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "gemma-4-E2B-it_qualcomm_sm8750.gguf"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("Qualcomm SM8750"))
        assertTrue(warning.contains("MediaTek Dimensity 7050"))
    }

    @Test
    fun gcs8275ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "gemma-4-E2B-it_qualcomm_gcs8275.gguf"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("GCS8275"))
    }

    @Test
    fun genericModelFileNameDoesNotReturnHardwareWarning() {
        assertNull(ModelConstants.hardwareWarningForFileName("Qwen3.5-0.8B-Q8_0.gguf"))
    }
}
