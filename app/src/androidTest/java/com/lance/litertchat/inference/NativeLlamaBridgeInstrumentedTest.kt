package com.lance.litertchat.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLlamaBridgeInstrumentedTest {
    @Test
    fun nativeLibraryLoads() {
        val bridge = NativeLlamaBridge()

        assertEquals("native-llamacpp", bridge.nativeRuntimeVersion())

        bridge.release()
    }
}
