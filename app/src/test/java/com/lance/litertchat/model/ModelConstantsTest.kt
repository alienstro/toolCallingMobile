package com.lance.litertchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun defaultModelUrlMatchesSmolLmGgufResolveUrl() {
        assertEquals(
            "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
            ModelConstants.DEFAULT_MODEL_URL
        )
    }

    @Test
    fun modelExtensionIsGguf() {
        assertEquals(".gguf", ModelConstants.MODEL_EXTENSION)
    }

    @Test
    fun sm8750ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "model_qualcomm_sm8750.gguf"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("Qualcomm SM8750"))
        assertTrue(warning.contains("MediaTek Dimensity 7050"))
    }

    @Test
    fun gcs8275ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "model_qualcomm_gcs8275.gguf"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("GCS8275"))
    }

    @Test
    fun genericModelFileNameDoesNotReturnHardwareWarning() {
        assertNull(ModelConstants.hardwareWarningForFileName("smollm2-360m-instruct-q8_0.gguf"))
    }
}
