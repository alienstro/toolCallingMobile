package com.lance.litertchat.inference

import android.content.Context
import android.util.Log
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.storage.AndroidPlatformContext
import java.io.File

object RunAnywhereInitializer {
    private val lock = Any()
    @Volatile
    private var initialized = false

    val isInitialized: Boolean
        get() = initialized

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(lock) {
            if (initialized) return

            runCatching {
                val appContext = context.applicationContext
                AndroidPlatformContext.initialize(appContext)
                RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)

                val runAnywhereDirectory = File(appContext.filesDir, "runanywhere")
                runAnywhereDirectory.mkdirs()
                CppBridgeModelPaths.setBaseDirectory(runAnywhereDirectory.absolutePath)

                try {
                    LlamaCPP.register(priority = 100)
                } catch (error: Exception) {
                    Log.w(TAG, "RunAnywhere llama.cpp registration warning: ${error.message}")
                }
            }.onFailure { error ->
                throw IllegalStateException(
                    "RunAnywhere initialization failed: ${error.message ?: error::class.java.simpleName}",
                    error
                )
            }

            initialized = true
        }
    }

    private const val TAG = "RunAnywhereInit"
}
