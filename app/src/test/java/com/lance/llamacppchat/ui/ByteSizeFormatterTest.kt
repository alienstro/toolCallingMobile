package com.lance.llamacppchat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {
    @Test
    fun formatsSmallValuesAsMegabytes() {
        assertEquals("0.0 MB", formatByteSize(512L))
    }

    @Test
    fun formatsMegabyteValuesAsMegabytes() {
        assertEquals("12.5 MB", formatByteSize(12_500_000L))
    }

    @Test
    fun formatsGigabyteValuesAsGigabytes() {
        assertEquals("1.50 GB", formatByteSize(1_500_000_000L))
    }
}
