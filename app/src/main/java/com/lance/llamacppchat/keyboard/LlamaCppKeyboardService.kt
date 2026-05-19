package com.lance.llamacppchat.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lance.llamacppchat.IInferenceCallback
import com.lance.llamacppchat.IInferenceService

class LlamaCppKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ── Compose-in-Service boilerplate ────────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ── State ─────────────────────────────────────────────────────────────────
    private val panelState = mutableStateOf<KeyboardPanelState>(KeyboardPanelState.Idle)
    private val inputText = mutableStateOf("")
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── IPC ───────────────────────────────────────────────────────────────────
    private var inferenceService: IInferenceService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            inferenceService = IInferenceService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            inferenceService = null
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        bindService(
            Intent(this, InferenceService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LlamaCppKeyboardService)
            setViewTreeViewModelStoreOwner(this@LlamaCppKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@LlamaCppKeyboardService)
            setContent {
                KeyboardPanel(
                    state = panelState.value,
                    inputText = inputText.value,
                    onInputChange = { inputText.value = it },
                    onAsk = ::onAsk,
                    onStop = ::onStop,
                    onInsert = ::onInsert,
                    onCopy = ::onCopy,
                    onReset = {
                        panelState.value = KeyboardPanelState.Idle
                        inputText.value = ""
                    }
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting && panelState.value == KeyboardPanelState.Idle) {
            val selected = currentInputConnection?.getSelectedText(0)?.toString()
            if (!selected.isNullOrBlank()) {
                inputText.value = "Rewrite this: $selected"
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { unbindService(serviceConnection) }
        viewModelStore.clear()
        super.onDestroy()
    }

    // ── Actions ───────────────────────────────────────────────────────────────
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

    private fun onStop() {
        inferenceService?.cancel()
        mainHandler.post {
            val cur = panelState.value
            if (cur is KeyboardPanelState.Generating) {
                panelState.value = KeyboardPanelState.Done(cur.partialResponse)
            }
        }
    }

    private fun onInsert() {
        val response = (panelState.value as? KeyboardPanelState.Done)?.response ?: return
        currentInputConnection?.commitText(response, 1)
    }

    private fun onCopy() {
        val response = (panelState.value as? KeyboardPanelState.Done)?.response ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", response))
    }
}
