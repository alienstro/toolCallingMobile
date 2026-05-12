package com.lance.litertchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lance.litertchat.inference.RunAnywhereInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RunAnywhereInitializer.initialize(applicationContext)
        setContent {
            RunAnywhereChatApp()
        }
    }
}
