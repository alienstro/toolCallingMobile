package com.lance.llamacppchat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiDesignStateTest {
    @Test
    fun bannerToneTreatsBlockedUnavailableAndErrorsAsWarnings() {
        assertEquals(BannerTone.Warning, bannerToneForMessage("New chat blocked while generation is running"))
        assertEquals(BannerTone.Warning, bannerToneForMessage("Camera unavailable while generation is running"))
        assertEquals(BannerTone.Warning, bannerToneForMessage("Previous import rejected: expected .gguf extension."))
    }

    @Test
    fun bannerToneTreatsPositiveActionsAsInfo() {
        assertEquals(BannerTone.Info, bannerToneForMessage("Memory saved"))
        assertEquals(BannerTone.Info, bannerToneForMessage("Formatter updated"))
    }

    @Test
    fun chromeRuntimeStatusPrefersActiveWorkAndFallbackState() {
        assertEquals(
            ChromeRuntimeStatus("Running", PillTone.Accent),
            chromeRuntimeStatus(AppState(isGenerating = true))
        )
        assertEquals(
            ChromeRuntimeStatus("Downloading", PillTone.Accent),
            chromeRuntimeStatus(AppState(isDownloading = true))
        )
        assertEquals(
            ChromeRuntimeStatus("CPU fallback", PillTone.Warn),
            chromeRuntimeStatus(AppState(gpuBackendEnabled = true))
        )
    }
}
