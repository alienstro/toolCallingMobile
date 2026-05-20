package com.lance.llamacppchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lance.llamacppchat.overlay.OverlayPanelActivity

class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        startActivity(
            Intent(this, OverlayPanelActivity::class.java).apply {
                putExtra(OverlayPanelActivity.EXTRA_SELECTED_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
