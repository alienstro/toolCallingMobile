package com.lance.llamacppchat.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPanelStateTest {

    @Test
    fun idleIsTheInitialState() {
        val state: KeyboardPanelState = KeyboardPanelState.Idle
        assertTrue(state is KeyboardPanelState.Idle)
    }

    @Test
    fun generatingAccumulatesTokens() {
        val state = KeyboardPanelState.Generating("Hello")
        val next = state.copy(partialResponse = state.partialResponse + " world")
        assertEquals("Hello world", next.partialResponse)
    }

    @Test
    fun doneHoldsFullResponse() {
        val state = KeyboardPanelState.Done("Final answer")
        assertEquals("Final answer", state.response)
    }

    @Test
    fun loadingHoldsMessage() {
        val state = KeyboardPanelState.Loading("Starting app…")
        assertEquals("Starting app…", state.message)
    }

    @Test
    fun errorHoldsMessage() {
        val state = KeyboardPanelState.Error("Engine is busy")
        assertEquals("Engine is busy", state.message)
    }

    @Test
    fun loadingMessagesListHasThreeEntries() {
        assertEquals(3, LOADING_MESSAGES.size)
        assertEquals("Starting app…", LOADING_MESSAGES[0])
        assertEquals("Loading model…", LOADING_MESSAGES[1])
        assertEquals("Almost ready…", LOADING_MESSAGES[2])
    }
}
