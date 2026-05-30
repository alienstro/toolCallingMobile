# Floating AI Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom IME keyboard with a floating AI button that appears over any app — tapping it opens a bottom sheet AI panel above the active keyboard, and a text selection menu entry ("Ask xChat") opens the same panel pre-filled with the selected text.

**Architecture:** `OverlayService` (ForegroundService) manages a draggable floating button added to `WindowManager`. Tapping the button starts `OverlayPanelActivity` (transparent theme), which binds to the existing `InferenceService` and shows `OverlayPanel` (Compose bottom sheet). `ProcessTextActivity` registers for `ACTION_PROCESS_TEXT` and starts `OverlayPanelActivity` with the selected text. The main app's Settings screen gains an overlay toggle that checks `SYSTEM_ALERT_WINDOW` permission before starting/stopping the service.

**Tech Stack:** Kotlin, Jetpack Compose, AIDL, Android WindowManager, NotificationCompat, existing `InferenceService` + `KeyboardPanelState` + `UiKit.kt`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt` | **Delete** | Replaced by overlay |
| `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt` | **Delete** | IME-specific QWERTY UI, replaced by OverlayPanel |
| `app/src/main/res/xml/method.xml` | **Delete** | IME metadata, no longer needed |
| `app/src/main/res/values/styles.xml` | Modify | Add transparent activity theme |
| `app/src/main/AndroidManifest.xml` | Modify | Remove IME entries; add permissions, OverlayService, OverlayPanelActivity, ProcessTextActivity |
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanel.kt` | **Create** | Compose bottom sheet UI with all 5 states |
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanelActivity.kt` | **Create** | Transparent Activity: hosts OverlayPanel, binds to InferenceService |
| `app/src/main/java/com/lance/llamacppchat/ProcessTextActivity.kt` | **Create** | Receives ACTION_PROCESS_TEXT, starts OverlayPanelActivity with selected text |
| `app/src/main/java/com/lance/llamacppchat/overlay/OverlayService.kt` | **Create** | ForegroundService: floating button, drag+snap, position persistence, notification |
| `app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt` | Modify | Add `overlayEnabled` field |
| `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt` | Modify | Add `overlayEnabled` to `AppState`, add `setOverlayEnabled` |
| `app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt` | Modify | Add overlay toggle row |
| `app/src/main/java/com/lance/llamacppchat/App.kt` | Modify | Handle overlay toggle callback, start/stop OverlayService |

---

## Task 1: Remove old IME code and update manifest + styles

Remove the custom keyboard and replace the manifest + styles with everything the overlay needs.

**Files:**
- Delete: `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt`
- Delete: `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt`
- Delete: `app/src/main/res/xml/method.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Delete the three IME files**

```
git rm app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt
git rm app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt
git rm app/src/main/res/xml/method.xml
```

- [ ] **Step 2: Replace `styles.xml` content**

Full replacement of `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
    <style name="Theme.Overlay" parent="android:style/Theme.Translucent.NoTitleBar" />
</resources>
```

- [ ] **Step 3: Replace `AndroidManifest.xml` content**

Full replacement of `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:label="xChat"
        android:theme="@style/AppTheme">
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".overlay.OverlayPanelActivity"
            android:exported="false"
            android:theme="@style/Theme.Overlay"
            android:windowSoftInputMode="adjustResize"
            android:launchMode="singleTask" />

        <activity
            android:name=".ProcessTextActivity"
            android:exported="true"
            android:label="Ask xChat">
            <intent-filter>
                <action android:name="android.intent.action.PROCESS_TEXT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <service
            android:name=".keyboard.InferenceService"
            android:exported="false" />

        <service
            android:name=".overlay.OverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Floating AI assistant button for quick access" />
        </service>
    </application>
</manifest>
```

- [ ] **Step 4: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. There will be errors about missing `LlamaCppKeyboardService` references — none should exist since we deleted it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/styles.xml app/src/main/AndroidManifest.xml
git commit -m "feat: remove IME keyboard, add overlay permissions and manifest entries"
```

---

## Task 2: `OverlayPanel.kt` — Compose bottom sheet UI

The AI panel Compose UI. Reuses `KeyboardPanelState` and all `UiKit.kt` design tokens. No QWERTY keyboard zone. No Insert button — Copy is the only action in Done state.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanel.kt`

- [ ] **Step 1: Create `OverlayPanel.kt`**

```kotlin
package com.lance.llamacppchat.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.keyboard.KeyboardPanelState
import com.lance.llamacppchat.keyboard.LOADING_MESSAGES
import com.lance.llamacppchat.ui.AppAccent
import com.lance.llamacppchat.ui.AppBackground
import com.lance.llamacppchat.ui.AppBorder
import com.lance.llamacppchat.ui.AppFaint
import com.lance.llamacppchat.ui.AppMuted
import com.lance.llamacppchat.ui.AppSurface
import com.lance.llamacppchat.ui.AppText
import com.lance.llamacppchat.ui.AppTheme
import com.lance.llamacppchat.ui.BannerTone
import com.lance.llamacppchat.ui.CompactActionButton
import com.lance.llamacppchat.ui.StopButton
import com.lance.llamacppchat.ui.WarningBanner
import kotlinx.coroutines.delay

@Composable
fun OverlayPanel(
    state: KeyboardPanelState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppTheme {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(AppBackground, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .border(
                    1.dp, AppBorder,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(AppBorder, RoundedCornerShape(2.dp))
                )
            }

            // Panel content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 16.dp)
            ) {
                when (state) {
                    is KeyboardPanelState.Idle -> IdleContent(
                        inputText = inputText,
                        onInputChange = onInputChange,
                        onAsk = onAsk,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Loading -> LoadingContent(
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Generating -> GeneratingContent(
                        partialResponse = state.partialResponse,
                        onStop = onStop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Done -> DoneContent(
                        response = state.response,
                        onCopy = onCopy,
                        onReset = onReset,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is KeyboardPanelState.Error -> ErrorContent(
                        message = state.message,
                        onReset = onReset,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRewrite = inputText.startsWith("Rewrite this:")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isRewrite) {
            Text(
                text = "Selected text detected",
                color = AppAccent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = {
                    Text("Ask AI…", color = AppFaint, style = MaterialTheme.typography.bodySmall)
                },
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppSurface,
                    unfocusedContainerColor = AppSurface,
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AppAccent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            CompactActionButton(
                text = "Ask",
                onClick = onAsk,
                enabled = inputText.isNotBlank(),
                primary = true,
                modifier = Modifier.widthIn(min = 64.dp)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            messageIndex = (messageIndex + 1) % LOADING_MESSAGES.size
        }
    }
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(28.dp))
        Text(
            text = LOADING_MESSAGES[messageIndex],
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GeneratingContent(
    partialResponse: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(partialResponse) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Generating…",
                color = AppFaint,
                style = MaterialTheme.typography.labelSmall
            )
            StopButton(onClick = onStop, modifier = Modifier.size(36.dp))
        }
        Text(
            text = partialResponse,
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        )
    }
}

@Composable
private fun DoneContent(
    response: String,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = response,
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactActionButton(
                text = if (copied) "Copied!" else "Copy",
                onClick = { onCopy(); copied = true },
                primary = true,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = "Ask again",
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningBanner(message = message, tone = BannerTone.Warning)
        CompactActionButton(text = "Try again", onClick = onReset)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanel.kt
git commit -m "feat: add OverlayPanel Compose bottom sheet UI"
```

---

## Task 3: `OverlayPanelActivity.kt` — transparent Activity

The transparent Activity that hosts `OverlayPanel` and binds to `InferenceService`. Since it's an Activity, Compose lifecycle works without any boilerplate. Launched by both `OverlayService` (button tap) and `ProcessTextActivity` (text selection).

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanelActivity.kt`

- [ ] **Step 1: Create `OverlayPanelActivity.kt`**

```kotlin
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
import androidx.compose.runtime.mutableStateOf
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
            OverlayPanel(
                state = panelState.value,
                inputText = inputText.value,
                onInputChange = { inputText.value = it },
                onAsk = ::onAsk,
                onStop = ::onStop,
                onCopy = ::onCopy,
                onReset = {
                    panelState.value = KeyboardPanelState.Idle
                    inputText.value = ""
                },
                onDismiss = ::finish
            )
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

    private fun onStop() {
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
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/overlay/OverlayPanelActivity.kt
git commit -m "feat: add OverlayPanelActivity transparent activity with AI panel"
```

---

## Task 4: `ProcessTextActivity.kt` — text selection menu entry

Receives selected text from any app via `ACTION_PROCESS_TEXT`, launches `OverlayPanelActivity` with the text, then finishes itself.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/ProcessTextActivity.kt`

- [ ] **Step 1: Create `ProcessTextActivity.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/ProcessTextActivity.kt
git commit -m "feat: add ProcessTextActivity for text selection menu entry"
```

---

## Task 5: `OverlayService.kt` — floating button ForegroundService

ForegroundService that adds a draggable floating button to `WindowManager`. Tapping opens `OverlayPanelActivity`. Position is persisted to `SharedPreferences` and snaps to the nearest screen edge on drag release.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/overlay/OverlayService.kt`

- [ ] **Step 1: Create `OverlayService.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/overlay/OverlayService.kt
git commit -m "feat: add OverlayService floating button with drag, snap, and persistence"
```

---

## Task 6: Main app overlay toggle

Add `overlayEnabled` to settings persistence, `AppState`, `AppViewModel`, and wire a toggle in `SettingsScreen` that checks the `SYSTEM_ALERT_WINDOW` permission before starting/stopping `OverlayService`.

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/App.kt`

- [ ] **Step 1: Add `overlayEnabled` to `AppSettings` and `AppSettingsRepository`**

In `AppSettingsRepository.kt`, update `AppSettings` data class:

```kotlin
data class AppSettings(
    val streamResponsesEnabled: Boolean = true,
    val gpuBackendEnabled: Boolean = false,
    val npuBackendEnabled: Boolean = false,
    val gemmaMtpEnabled: Boolean = false,
    val overlayEnabled: Boolean = false
)
```

In the `load()` function, add inside the `return AppSettings(...)` block:

```kotlin
overlayEnabled = properties.booleanValue(KEY_OVERLAY_ENABLED, defaultValue = false),
```

In the `save()` function, add:

```kotlin
properties.setProperty(KEY_OVERLAY_ENABLED, settings.overlayEnabled.toString())
```

Add a new `setOverlayEnabled` function and companion constant:

```kotlin
fun setOverlayEnabled(enabled: Boolean) {
    save(load().copy(overlayEnabled = enabled))
}

// In companion object:
const val KEY_OVERLAY_ENABLED = "overlayEnabled"
```

- [ ] **Step 2: Add `overlayEnabled` to `AppState` and `AppViewModel`**

In `AppViewModel.kt`, add to `AppState` data class:

```kotlin
val overlayEnabled: Boolean = false,
```

In `AppViewModel`, inside `MutableStateFlow(AppState(...))` initial block, add:

```kotlin
overlayEnabled = initialSettings.overlayEnabled,
```

Add this function to `AppViewModel`:

```kotlin
fun setOverlayEnabled(enabled: Boolean) {
    appSettingsRepository.setOverlayEnabled(enabled)
    mutableState.update { it.copy(overlayEnabled = enabled) }
}
```

- [ ] **Step 3: Add overlay toggle to `SettingsScreen`**

In `SettingsScreen.kt`, add `onOverlayChanged: (Boolean) -> Unit` parameter to `SettingsScreen`:

```kotlin
@Composable
fun SettingsScreen(
    state: AppState,
    contentPadding: PaddingValues = PaddingValues(),
    onCreateFormatter: (String, String) -> Unit,
    onUpdateFormatter: (String, String, String) -> Unit,
    onDeleteFormatter: (String) -> Unit,
    onSelectFormatter: (String) -> Unit,
    onResetDefaultFormatter: () -> Unit,
    onUpsertMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onStreamResponsesChanged: (Boolean) -> Unit,
    onGpuBackendChanged: (Boolean) -> Unit,
    onNpuBackendChanged: (Boolean) -> Unit,
    onGemmaMtpChanged: (Boolean) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,        // ← add this
) {
```

Inside the `LazyColumn`, add a new `item` block after the "Runtime and generation" `AppCard` item (after the closing `}` of the first `item { AppCard { ... } }` block):

```kotlin
item {
    AppCard {
        SectionTitle("AI Overlay")
        SettingSwitchRow(
            title = "Floating AI button",
            help = "Show a draggable AI button over all apps. Requires 'Display over other apps' permission.",
            checked = state.overlayEnabled,
            onCheckedChange = onOverlayChanged
        )
    }
}
```

- [ ] **Step 4: Wire the toggle in `App.kt`**

In `App.kt`, add these imports at the top of the file:

```kotlin
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lance.llamacppchat.overlay.OverlayService
```

Inside the `LlamaCppChatApp` composable, after `val state by appViewModel.state.collectAsState()`, add:

```kotlin
val overlayPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    if (Settings.canDrawOverlays(context)) {
        appViewModel.setOverlayEnabled(true)
        context.startForegroundService(Intent(context, OverlayService::class.java))
    }
}
```

In the `composable(AppRoute.Settings.route)` block, update the `SettingsScreen(...)` call to add:

```kotlin
onOverlayChanged = { enabled ->
    if (enabled) {
        if (Settings.canDrawOverlays(context)) {
            appViewModel.setOverlayEnabled(true)
            context.startForegroundService(Intent(context, OverlayService::class.java))
        } else {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
            )
        }
    } else {
        appViewModel.setOverlayEnabled(false)
        context.stopService(Intent(context, OverlayService::class.java))
    }
},
```

- [ ] **Step 5: Build and install**

```
.\gradlew app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt
git add app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt
git add app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt
git add app/src/main/java/com/lance/llamacppchat/App.kt
git commit -m "feat: add overlay toggle to settings with permission check"
```

---

## Task 7: Device testing

Verify all flows on-device.

**Files:** none — device testing only.

- [ ] **Step 1: Enable the overlay**

1. Open xChat → Settings
2. Toggle "Floating AI button" ON
3. Android prompts "Display over other apps" → grant permission
4. Verify: small "AI" circle button appears floating on screen, bottom-right corner

- [ ] **Step 2: Test floating button**

1. Open any other app (Chrome, Notes, etc.)
2. Verify: the AI button stays visible over the other app
3. Drag the button left → verify it snaps to the left edge
4. Drag it right → verify it snaps to the right edge
5. Force-close the app and reopen it → verify button reappears at the saved position

- [ ] **Step 3: Test AI panel flow**

1. Tap the AI button
2. Verify: dark bottom sheet panel opens above the keyboard area
3. Tap the text field, type a prompt using Gboard
4. Tap Ask
5. Verify: panel shows Loading → Generating (streaming text) → Done (Copy + Ask again)
6. Tap Copy → verify "Copied!" appears briefly
7. Long-press in any text field → Paste → verify AI response pastes
8. Tap Ask again → verify panel resets to Idle

- [ ] **Step 4: Test text selection ("Ask xChat")**

1. Open Chrome/Notes, select some text
2. In the popup menu (Copy, Share, …), tap the three-dot → verify "Ask xChat" appears
3. Tap it → verify the AI panel opens with "Rewrite this: [selected text]" pre-filled and "Selected text detected" label
4. Tap Ask → verify the AI rewrites the text

- [ ] **Step 5: Test disable**

1. Swipe down notification shade → tap "Disable" on the overlay notification
2. Verify: floating button disappears
3. Open the app → Settings → verify toggle is now OFF

- [ ] **Step 6: Final commit**

```bash
git add .
git commit -m "feat: floating AI overlay complete"
```

---

## Spec coverage

| Spec requirement | Task |
|-----------------|------|
| Remove IME keyboard files | Task 1 |
| Update manifest (permissions, services, activities) | Task 1 |
| Transparent activity theme | Task 1 |
| OverlayPanel: all 5 states | Task 2 |
| Copy (no Insert) | Task 2 |
| OverlayPanelActivity: binds InferenceService | Task 3 |
| OverlayPanelActivity: pre-fills from selected text | Task 3 |
| OverlayPanelActivity: AIDL callbacks on main thread | Task 3 |
| ProcessTextActivity: ACTION_PROCESS_TEXT | Task 4 |
| "Ask xChat" in selection menu | Task 4 |
| OverlayService: ForegroundService + notification | Task 5 |
| Floating button: draggable | Task 5 |
| Floating button: snaps to edge | Task 5 |
| Floating button: position persisted | Task 5 |
| Notification "Disable" action | Task 5 |
| Main app toggle | Task 6 |
| SYSTEM_ALERT_WINDOW permission check | Task 6 |
| Persist overlay enabled state | Task 6 |
