package com.lance.llamacppchat.keyboard

sealed interface KeyboardPanelState {
    data object Idle : KeyboardPanelState
    data class Loading(val message: String) : KeyboardPanelState
    data class Generating(val partialResponse: String) : KeyboardPanelState
    data class Done(val response: String) : KeyboardPanelState
    data class Error(val message: String) : KeyboardPanelState
}

val LOADING_MESSAGES = listOf("Starting app…", "Loading model…", "Almost ready…")
