package com.lance.litertchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun defaultModelUrlMatchesQwen35GgufResolveUrl() {
        assertEquals(
            "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-UD-Q8_K_XL.gguf",
            ModelConstants.DEFAULT_MODEL_URL
        )
    }

    @Test
    fun modelExtensionIsGguf() {
        assertEquals(".gguf", ModelConstants.MODEL_EXTENSION)
    }

    @Test
    fun ggufModelFileNameDoesNotReturnHardwareWarning() {
        assertNull(ModelConstants.hardwareWarningForFileName("Qwen3.5-2B-UD-Q8_K_XL.gguf"))
    }
}


