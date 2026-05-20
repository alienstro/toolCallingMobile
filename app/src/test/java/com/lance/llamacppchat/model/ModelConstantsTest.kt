package com.lance.llamacppchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun defaultModelUrlMatchesBartowskiQwen25GgufResolveUrl() {
        assertEquals(
            "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            ModelConstants.DEFAULT_MODEL_URL
        )
    }

    @Test
    fun defaultModelRepoIsBartowskiQwen25GgufRepo() {
        assertEquals(
            "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
            ModelConstants.DEFAULT_MODEL_REPOSITORY
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
        assertNull(ModelConstants.hardwareWarningForFileName("Qwen3.5-0.8B-Q4_K_M.gguf"))
    }
}
