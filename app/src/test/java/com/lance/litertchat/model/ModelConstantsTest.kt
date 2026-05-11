package com.lance.litertchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun defaultModelUrlMatchesGemmaLiteRtLmResolveUrl() {
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            ModelConstants.DEFAULT_MODEL_URL
        )
    }

    @Test
    fun modelExtensionIsLiteRtLm() {
        assertEquals(".litertlm", ModelConstants.MODEL_EXTENSION)
    }

    @Test
    fun sm8750ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("Qualcomm SM8750"))
        assertTrue(warning.contains("MediaTek Dimensity 7050"))
    }

    @Test
    fun gcs8275ModelFileNameReturnsHardwareWarning() {
        val warning = ModelConstants.hardwareWarningForFileName(
            "gemma-4-E2B-it_qualcomm_gcs8275.litertlm"
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("GCS8275"))
    }

    @Test
    fun genericModelFileNameDoesNotReturnHardwareWarning() {
        assertNull(ModelConstants.hardwareWarningForFileName("gemma-4-E2B-it.litertlm"))
    }
}
