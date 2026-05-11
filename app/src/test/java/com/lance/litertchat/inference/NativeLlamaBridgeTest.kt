package com.lance.litertchat.inference

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeLlamaBridgeTest {
    @Test
    fun loadRejectsMissingModelBeforeCallingNativeCode() {
        val bridge = NativeLlamaBridge(loadLibrary = false)

        val result = bridge.load(File("/missing/model.gguf"), NativeLlamaConfig())

        assertFalse(result.isSuccess)
        assertEquals("Model file does not exist.", result.exceptionOrNull()?.message)
    }

    @Test
    fun loadRejectsNonGgufBeforeCallingNativeCode() {
        val bridge = NativeLlamaBridge(loadLibrary = false)
        val file = kotlin.io.path.createTempFile(suffix = ".bin").toFile()

        try {
            file.writeText("not a model")

            val result = bridge.load(file, NativeLlamaConfig())

            assertFalse(result.isSuccess)
            assertEquals("Model file must end with .gguf.", result.exceptionOrNull()?.message)
        } finally {
            file.delete()
        }
    }
}
