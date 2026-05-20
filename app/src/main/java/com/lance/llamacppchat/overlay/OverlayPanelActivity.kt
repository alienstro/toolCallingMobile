package com.lance.llamacppchat.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lance.llamacppchat.IInferenceCallback
import com.lance.llamacppchat.IInferenceService
import com.lance.llamacppchat.keyboard.InferenceService
import com.lance.llamacppchat.keyboard.KeyboardPanelState

class OverlayPanelActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var inferenceService: IInferenceService? = null
    private val panelState = mutableStateOf<KeyboardPanelState>(KeyboardPanelState.Idle)
    private val inputText = mutableStateOf("")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            inferenceService = IInferenceService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            inferenceService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySelectedText(intent)
        bindService(
            Intent(this, InferenceService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrim — tapping outside the panel dismisses it
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { finish() }
                )
                OverlayPanel(
                    state = panelState.value,
                    inputText = inputText.value,
                    onInputChange = { inputText.value = it },
                    onAsk = ::onAsk,
                    onStop = ::onStopGeneration,
                    onCopy = ::onCopy,
                    onReset = {
                        panelState.value = KeyboardPanelState.Idle
                        inputText.value = ""
                    },
                    onDismiss = ::finish,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applySelectedText(intent)
        panelState.value = KeyboardPanelState.Idle
    }

    override fun onDestroy() {
        runCatching { unbindService(serviceConnection) }
        super.onDestroy()
    }

    private fun applySelectedText(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_SELECTED_TEXT)
        if (!text.isNullOrBlank()) {
            inputText.value = "Rewrite this: $text"
        }
    }

    private fun onAsk() {
        val prompt = inputText.value.trim()
        if (prompt.isBlank()) return
        val service = inferenceService ?: return
        mainHandler.post { panelState.value = KeyboardPanelState.Generating("") }
        service.generate(prompt, object : IInferenceCallback.Stub() {
            override fun onToken(token: String) {
                mainHandler.post {
                    val cur = panelState.value
                    if (cur is KeyboardPanelState.Generating) {
                        panelState.value = cur.copy(partialResponse = cur.partialResponse + token)
                    }
                }
            }
            override fun onComplete() {
                mainHandler.post {
                    val cur = panelState.value
                    if (cur is KeyboardPanelState.Generating) {
                        panelState.value = KeyboardPanelState.Done(cur.partialResponse)
                    }
                }
            }
            override fun onError(message: String) {
                mainHandler.post { panelState.value = KeyboardPanelState.Error(message) }
            }
            override fun onModelLoading() {
                mainHandler.post { panelState.value = KeyboardPanelState.Loading("Starting app…") }
            }
            override fun onModelReady() {
                mainHandler.post { panelState.value = KeyboardPanelState.Generating("") }
            }
        })
    }

    private fun onStopGeneration() {
        inferenceService?.cancel()
        mainHandler.post {
            val cur = panelState.value
            if (cur is KeyboardPanelState.Generating) {
                panelState.value = KeyboardPanelState.Done(cur.partialResponse)
            }
        }
    }

    private fun onCopy() {
        val response = (panelState.value as? KeyboardPanelState.Done)?.response ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", response))
    }

    companion object {
        const val EXTRA_SELECTED_TEXT = "extra_selected_text"
    }
}
