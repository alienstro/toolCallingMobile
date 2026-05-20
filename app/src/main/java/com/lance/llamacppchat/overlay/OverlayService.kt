package com.lance.llamacppchat.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var buttonView: View
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addFloatingButton()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(PREF_OVERLAY_ENABLED, false).apply()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { windowManager.removeView(buttonView) }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Floating AI assistant button" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LlamaCpp AI Overlay")
            .setContentText("Floating AI button is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Disable", stopIntent)
            .build()
    }

    private fun addFloatingButton() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val buttonSize = (52 * metrics.density).toInt()
        val margin = (16 * metrics.density).toInt()

        val savedEdge = prefs.getString(PREF_EDGE, EDGE_RIGHT)
        val savedYFraction = prefs.getFloat(PREF_Y_FRACTION, 0.7f)

        val initialX = if (savedEdge == EDGE_RIGHT) screenWidth - buttonSize - margin else margin
        val initialY = (screenHeight * savedYFraction).toInt()

        val params = WindowManager.LayoutParams(
            buttonSize, buttonSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        buttonView = buildButtonView(params, screenWidth, screenHeight, buttonSize, margin)
        windowManager.addView(buttonView, params)
    }

    private fun buildButtonView(
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        screenHeight: Int,
        buttonSize: Int,
        margin: Int
    ): View {
        val button = FrameLayout(this)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFA8513D.toInt())
        }

        val label = TextView(this).apply {
            text = "AI"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
        }
        button.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        var rawDownX = 0f
        var rawDownY = 0f
        var paramDownX = 0
        var paramDownY = 0
        var isDragging = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rawDownX = event.rawX
                    rawDownY = event.rawY
                    paramDownX = params.x
                    paramDownY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - rawDownX
                    val dy = event.rawY - rawDownY
                    if (!isDragging && (abs(dx) > 8f || abs(dy) > 8f)) isDragging = true
                    if (isDragging) {
                        params.x = (paramDownX + dx).toInt()
                        params.y = (paramDownY + dy).toInt().coerceIn(0, screenHeight - buttonSize)
                        windowManager.updateViewLayout(button, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        openPanel()
                    } else {
                        snapToEdge(params, screenWidth, screenHeight, buttonSize, margin)
                    }
                    true
                }
                else -> false
            }
        }

        return button
    }

    private fun snapToEdge(
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        screenHeight: Int,
        buttonSize: Int,
        margin: Int
    ) {
        val snapRight = params.x + buttonSize / 2 > screenWidth / 2
        val edge = if (snapRight) EDGE_RIGHT else EDGE_LEFT
        params.x = if (snapRight) screenWidth - buttonSize - margin else margin
        params.y = params.y.coerceIn(0, screenHeight - buttonSize)
        windowManager.updateViewLayout(buttonView, params)
        val yFraction = params.y.toFloat() / screenHeight
        prefs.edit().putString(PREF_EDGE, edge).putFloat(PREF_Y_FRACTION, yFraction).apply()
    }

    private fun openPanel() {
        startActivity(
            Intent(this, OverlayPanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.lance.llamacppchat.STOP_OVERLAY"
        const val PREFS_NAME = "overlay_prefs"
        const val PREF_EDGE = "button_edge"
        const val PREF_Y_FRACTION = "button_y_fraction"
        const val PREF_OVERLAY_ENABLED = "overlay_enabled"
        const val EDGE_LEFT = "left"
        const val EDGE_RIGHT = "right"
    }
}
