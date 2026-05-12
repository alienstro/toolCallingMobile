package com.lance.litertchat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSizeFormatterTest {
    @Test
    fun formatsZeroAsMegabytes() {
        assertEquals("0 MB", formatModelSize(0L))
    }

    @Test
    fun formatsSmallValuesAsMegabytesInsteadOfBytesOrKilobytes() {
        assertEquals("1 MB", formatModelSize(1L))
        assertEquals("1 MB", formatModelSize(999_999L))
    }

    @Test
    fun formatsMegabytes() {
        assertEquals("12.3 MB", formatModelSize(12_300_000L))
    }

    @Test
    fun formatsGigabytesWhenPossible() {
        assertEquals("2.5 GB", formatModelSize(2_500_000_000L))
    }
}
