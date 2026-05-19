package com.lance.llamacppchat.keyboard

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.lance.llamacppchat.IInferenceCallback
import com.lance.llamacppchat.IInferenceService
import com.lance.llamacppchat.MainActivity
import com.lance.llamacppchat.inference.InferenceRuntimeConfig
import com.lance.llamacppchat.inference.LlamaCppChatEngine
import com.lance.llamacppchat.model.ModelRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: LlamaCppChatEngine
    private val busy = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        engine = LlamaCppChatEngine(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        engine.release()
    }

    private val binder = object : IInferenceService.Stub() {

        override fun isModelLoaded(): Boolean = engine.isLoaded

        override fun isBusy(): Boolean = busy.get()

        override fun generate(prompt: String, callback: IInferenceCallback) {
            if (busy.getAndSet(true)) {
                callback.onError("Engine is busy — please wait")
                return
            }
            scope.launch {
                try {
                    if (!engine.isLoaded) {
                        callback.onModelLoading()
                        val modelFile = ModelRepository(filesDir).installedModelFile()
                        if (modelFile == null) {
                            callback.onError("No model installed. Open LlamaCpp Chat to download one.")
                            return@launch
                        }
                        startActivity(
                            Intent(this@InferenceService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                        engine.load(modelFile, InferenceRuntimeConfig.defaultCpu)
                            .onFailure {
                                callback.onError(it.message ?: "Failed to load model")
                                return@launch
                            }
                        callback.onModelReady()
                    }
                    engine.generateStreaming(prompt) { token ->
                        callback.onToken(token)
                    }.onFailure {
                        callback.onError(it.message ?: "Generation failed")
                        return@launch
                    }
                    callback.onComplete()
                } finally {
                    busy.set(false)
                }
            }
        }

        override fun cancel() {
            engine.cancelGeneration()
        }
    }
}
