# Keyboard IME Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a purpose-built AI keyboard (IME) to the app — a QWERTY keyboard whose top zone shows an AI chat panel where the user types a prompt, gets a streaming response, and inserts or copies it into any app.

**Architecture:** An `InferenceService` (Android bound service) wraps the existing `LlamaCppChatEngine` singleton and exposes it over AIDL. A `LlamaCppKeyboardService` (InputMethodService) binds to it, renders a `ComposeView` containing a panel zone (prompt input / streaming response) stacked above a QWERTY keys zone (Compose grid). Both components live inside the existing `app` module.

**Tech Stack:** Kotlin, Jetpack Compose, AIDL, Android InputMethodService, kotlinx-coroutines, JUnit4 + coroutines-test (existing test setup)

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/aidl/com/lance/llamacppchat/IInferenceCallback.aidl` | Create | Streaming callback interface (onToken, onComplete, onError, onModelLoading, onModelReady) |
| `app/src/main/aidl/com/lance/llamacppchat/IInferenceService.aidl` | Create | Service interface (generate, cancel, isModelLoaded, isBusy) |
| `app/src/main/res/xml/method.xml` | Create | IME metadata required by Android for keyboard registration |
| `app/src/main/AndroidManifest.xml` | Modify | Add `<service>` entries for InferenceService and LlamaCppKeyboardService |
| `app/src/main/java/com/lance/llamacppchat/inference/LlamaCppChatEngine.kt` | Modify | Add `val isLoaded: Boolean` public property |
| `app/src/main/java/com/lance/llamacppchat/keyboard/InferenceService.kt` | Create | Bound service: model loading, streaming inference, cancel |
| `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanelState.kt` | Create | Sealed interface for panel state machine: Idle, Loading, Generating, Done, Error |
| `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt` | Create | Full Compose UI: panel zone + QWERTY keys zone |
| `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt` | Create | InputMethodService: binds to InferenceService, hosts ComposeView, dispatches actions |
| `app/src/test/java/com/lance/llamacppchat/keyboard/KeyboardPanelStateTest.kt` | Create | Unit tests for state machine transitions |
| `app/src/test/java/com/lance/llamacppchat/keyboard/InferenceServiceLogicTest.kt` | Create | Unit tests for InferenceService coordination logic |

---

## Task 1: Expose `isLoaded` on `LlamaCppChatEngine`

`InferenceService` needs to know if the engine already has a model loaded. `LlamaCppChatEngine.loadedModelPath` is private — add a public property.

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/inference/LlamaCppChatEngine.kt`
- Test: `app/src/test/java/com/lance/llamacppchat/inference/LlamaCppChatEngineTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `LlamaCppChatEngineTest`:

```kotlin
@Test
fun isLoadedReturnsFalseBeforeLoad() = runTest {
    val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
    assertFalse(engine.isLoaded)
}

@Test
fun isLoadedReturnsTrueAfterSuccessfulLoad() = runTest {
    val modelFile = temporaryModelFile()
    val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
    engine.load(modelFile)
    assertTrue(engine.isLoaded)
}

@Test
fun isLoadedReturnsFalseAfterRelease() = runTest {
    val modelFile = temporaryModelFile()
    val engine = LlamaCppChatEngine(FakeInferenceEngine(), Unit)
    engine.load(modelFile)
    engine.release()
    assertFalse(engine.isLoaded)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew app:test --tests "*.LlamaCppChatEngineTest"
```

Expected: FAIL — `Unresolved reference: isLoaded`

- [ ] **Step 3: Add `isLoaded` to `LlamaCppChatEngine`**

In `LlamaCppChatEngine.kt`, add after the `lock` declaration:

```kotlin
val isLoaded: Boolean
    get() = synchronized(lock) { loadedModelPath != null } &&
            inferenceEngine.state.value.isModelLoaded
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew app:test --tests "*.LlamaCppChatEngineTest"
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/inference/LlamaCppChatEngine.kt
git add app/src/test/java/com/lance/llamacppchat/inference/LlamaCppChatEngineTest.kt
git commit -m "feat: expose isLoaded property on LlamaCppChatEngine"
```

---

## Task 2: AIDL interfaces

AIDL files define the cross-process API. AGP compiles them automatically if they exist under `src/main/aidl/`.

**Files:**
- Create: `app/src/main/aidl/com/lance/llamacppchat/IInferenceCallback.aidl`
- Create: `app/src/main/aidl/com/lance/llamacppchat/IInferenceService.aidl`

- [ ] **Step 1: Create the callback interface**

Create `app/src/main/aidl/com/lance/llamacppchat/IInferenceCallback.aidl`:

```aidl
package com.lance.llamacppchat;

oneway interface IInferenceCallback {
    void onToken(String token);
    void onComplete();
    void onError(String message);
    void onModelLoading();
    void onModelReady();
}
```

`oneway` means calls are non-blocking (fire-and-forget) — essential for streaming tokens from a background thread.

- [ ] **Step 2: Create the service interface**

Create `app/src/main/aidl/com/lance/llamacppchat/IInferenceService.aidl`:

```aidl
package com.lance.llamacppchat;

import com.lance.llamacppchat.IInferenceCallback;

interface IInferenceService {
    void generate(String prompt, IInferenceCallback callback);
    void cancel();
    boolean isModelLoaded();
    boolean isBusy();
}
```

- [ ] **Step 3: Verify AIDL compiles**

```
.\gradlew app:generateDebugAidl
```

Expected: BUILD SUCCESSFUL. Generated Java stubs appear in `app/build/generated/aidl_source_output_dir/`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/aidl/
git commit -m "feat: add AIDL interfaces for keyboard inference service"
```

---

## Task 3: IME metadata + AndroidManifest

Android requires an `xml/method.xml` resource and specific `<service>` declarations for keyboards.

**Files:**
- Create: `app/src/main/res/xml/method.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `method.xml`**

Create `app/src/main/res/xml/method.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <subtype
        android:label="English"
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="keyboard" />
</input-method>
```

- [ ] **Step 2: Add service declarations to AndroidManifest**

In `app/src/main/AndroidManifest.xml`, add inside `<application>` after the existing `<activity>` entry:

```xml
<service
    android:name=".keyboard.InferenceService"
    android:exported="false" />

<service
    android:name=".keyboard.LlamaCppKeyboardService"
    android:exported="true"
    android:permission="android.permission.BIND_INPUT_METHOD">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data
        android:name="android.view.im"
        android:resource="@xml/method" />
</service>
```

- [ ] **Step 3: Verify manifest compiles**

```
.\gradlew app:processDebugManifest
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/method.xml
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add IME metadata and manifest entries for keyboard services"
```

---

## Task 4: `KeyboardPanelState` — state machine

A sealed interface representing every state the panel can be in. Keeping it in its own file makes it independently testable.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanelState.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/keyboard/KeyboardPanelStateTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/lance/llamacppchat/keyboard/KeyboardPanelStateTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew app:test --tests "*.KeyboardPanelStateTest"
```

Expected: FAIL — `Unresolved reference: KeyboardPanelState`

- [ ] **Step 3: Create `KeyboardPanelState.kt`**

Create `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanelState.kt`:

```kotlin
package com.lance.llamacppchat.keyboard

sealed interface KeyboardPanelState {
    data object Idle : KeyboardPanelState
    data class Loading(val message: String) : KeyboardPanelState
    data class Generating(val partialResponse: String) : KeyboardPanelState
    data class Done(val response: String) : KeyboardPanelState
    data class Error(val message: String) : KeyboardPanelState
}

val LOADING_MESSAGES = listOf("Starting app…", "Loading model…", "Almost ready…")
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew app:test --tests "*.KeyboardPanelStateTest"
```

Expected: all 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanelState.kt
git add app/src/test/java/com/lance/llamacppchat/keyboard/KeyboardPanelStateTest.kt
git commit -m "feat: add KeyboardPanelState sealed interface and loading messages"
```

---

## Task 5: `InferenceService` — bound service

The bound service wraps `LlamaCppChatEngine`, handles model loading on demand, and streams tokens via the AIDL callback. Testable coordination logic is extracted into a helper to keep the Android `Service` class thin.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/keyboard/InferenceService.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/keyboard/InferenceServiceLogicTest.kt`

- [ ] **Step 1: Write failing tests for the coordinator logic**

Create `app/src/test/java/com/lance/llamacppchat/keyboard/InferenceServiceLogicTest.kt`:

```kotlin
package com.lance.llamacppchat.keyboard

import com.arm.aichat.InferenceEngine
import com.lance.llamacppchat.inference.LlamaCppChatEngine
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InferenceServiceLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun modelFile(): File = tmp.newFile("m.gguf").also { it.writeText("x") }

    @Test
    fun generateStreamsTokensWhenModelAlreadyLoaded() = runTest {
        val fake = FakeServiceEngine(chunks = listOf("Hel", "lo"))
        val engine = LlamaCppChatEngine(fake, Unit)
        engine.load(modelFile())
        val tokens = mutableListOf<String>()

        engine.generateStreaming("hi") { tokens += it }

        assertEquals(listOf("Hel", "lo"), tokens)
    }

    @Test
    fun generateFailsWithMessageWhenModelNotLoaded() = runTest {
        val engine = LlamaCppChatEngine(FakeServiceEngine(), Unit)

        val result = engine.generateStreaming("hi") { }

        assertTrue(result.isFailure)
        assertEquals("llama.cpp model is not loaded.", result.exceptionOrNull()?.message)
    }
}

private class FakeServiceEngine(
    private val chunks: List<String> = listOf("ok")
) : InferenceEngine {
    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
    override val state: StateFlow<InferenceEngine.State> = _state
    override suspend fun loadModel(pathToModel: String) {
        _state.value = InferenceEngine.State.ModelReady
    }
    override suspend fun setSystemPrompt(systemPrompt: String) = Unit
    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> =
        flow { chunks.forEach { emit(it) } }
    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = ""
    override fun cleanUp() { _state.value = InferenceEngine.State.Initialized }
    override fun destroy() = Unit
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew app:test --tests "*.InferenceServiceLogicTest"
```

Expected: FAIL — tests reference existing classes but confirm `generateStreaming` behaviour before service is built.

- [ ] **Step 3: Run tests to verify they pass (these use existing code)**

```
.\gradlew app:test --tests "*.InferenceServiceLogicTest"
```

Expected: PASS — this validates that the engine's streaming logic works correctly. If it fails, fix `LlamaCppChatEngine` before proceeding.

- [ ] **Step 4: Create `InferenceService.kt`**

Create `app/src/main/java/com/lance/llamacppchat/keyboard/InferenceService.kt`:

```kotlin
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
```

- [ ] **Step 5: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/keyboard/InferenceService.kt
git add app/src/test/java/com/lance/llamacppchat/keyboard/InferenceServiceLogicTest.kt
git commit -m "feat: add InferenceService bound service for keyboard inference"
```

---

## Task 6: `KeyboardPanel.kt` — full Compose UI

The complete keyboard UI: panel zone on top (prompt input, response, actions) and QWERTY keys zone on the bottom. During Generating/Done states the keys zone hides so the response has full height.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt`

- [ ] **Step 1: Create `KeyboardPanel.kt`**

Create `app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt`:

```kotlin
package com.lance.llamacppchat.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lance.llamacppchat.ui.AppAccent
import com.lance.llamacppchat.ui.AppBackground
import com.lance.llamacppchat.ui.AppBorder
import com.lance.llamacppchat.ui.AppFaint
import com.lance.llamacppchat.ui.AppMuted
import com.lance.llamacppchat.ui.AppPanel
import com.lance.llamacppchat.ui.AppPanelAlt
import com.lance.llamacppchat.ui.AppSurface
import com.lance.llamacppchat.ui.AppText
import com.lance.llamacppchat.ui.AppTheme
import com.lance.llamacppchat.ui.AppWarning
import com.lance.llamacppchat.ui.BannerTone
import com.lance.llamacppchat.ui.CompactActionButton
import com.lance.llamacppchat.ui.StopButton
import com.lance.llamacppchat.ui.WarningBanner
import kotlinx.coroutines.delay

@Composable
fun KeyboardPanel(
    state: KeyboardPanelState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showKeys = state is KeyboardPanelState.Idle || state is KeyboardPanelState.Loading

    AppTheme {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(AppBackground)
        ) {
            // Panel zone
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state) {
                    is KeyboardPanelState.Idle -> IdlePanel(
                        inputText = inputText,
                        onInputChange = onInputChange,
                        onAsk = onAsk,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Loading -> LoadingPanel(
                        modifier = Modifier.fillMaxSize()
                    )
                    is KeyboardPanelState.Generating -> GeneratingPanel(
                        partialResponse = state.partialResponse,
                        onStop = onStop,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Done -> DonePanel(
                        response = state.response,
                        onInsert = onInsert,
                        onCopy = onCopy,
                        onReset = onReset,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                    is KeyboardPanelState.Error -> ErrorPanel(
                        message = state.message,
                        onReset = onReset,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }

            // QWERTY keys zone — hidden during Generating/Done to give response more space
            if (showKeys) {
                KeyboardKeys(
                    onChar = { char -> onInputChange(inputText + char) },
                    onDelete = {
                        if (inputText.isNotEmpty()) onInputChange(inputText.dropLast(1))
                    },
                    onAsk = onAsk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
            }
        }
    }
}

@Composable
private fun IdlePanel(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRewrite = inputText.startsWith("Rewrite this:")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
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
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun LoadingPanel(modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            messageIndex = (messageIndex + 1) % LOADING_MESSAGES.size
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(28.dp))
        Text(
            text = LOADING_MESSAGES[messageIndex],
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun GeneratingPanel(
    partialResponse: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(partialResponse) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun DonePanel(
    response: String,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                text = "Insert",
                onClick = onInsert,
                primary = true,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = if (copied) "Copied" else "Copy",
                onClick = {
                    onCopy()
                    copied = true
                },
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
private fun ErrorPanel(
    message: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningBanner(message = message, tone = BannerTone.Warning)
        CompactActionButton(text = "Try again", onClick = onReset)
    }
}

// ── QWERTY Keys ───────────────────────────────────────────────────────────────

private val QWERTY_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    listOf("123", "     ", "↵")
)

private val NUMBER_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
    listOf("ABC", ".", ",", "?", "!", "'", "⌫"),
    listOf("ABC", "     ", "↵")
)

private fun keyWeight(key: String): Float = when (key) {
    "     " -> 4f
    "⌫", "↵", "123", "ABC", "⇧" -> 1.5f
    else -> 1f
}

@Composable
fun KeyboardKeys(
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNumbers by remember { mutableStateOf(false) }
    val rows = if (showNumbers) NUMBER_ROWS else QWERTY_ROWS

    Column(
        modifier = modifier
            .background(AppBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        modifier = Modifier.weight(keyWeight(key)),
                        onClick = {
                            when (key) {
                                "⌫" -> onDelete()
                                "↵" -> onAsk()
                                "⇧" -> { /* caps lock — no-op for MVP */ }
                                "123" -> showNumbers = true
                                "ABC" -> showNumbers = false
                                "     " -> onChar(" ")
                                else -> onChar(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSpecial = key in setOf("⌫", "↵", "123", "ABC", "⇧")
    val displayText = when (key) {
        "     " -> "space"
        else -> key
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSpecial) AppPanelAlt else AppSurface)
            .border(1.dp, AppBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = displayText,
            color = if (key == "↵") AppAccent else AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (key == "↵") FontWeight.Bold else FontWeight.Normal
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Fix any unresolved reference errors before continuing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/keyboard/KeyboardPanel.kt
git commit -m "feat: add KeyboardPanel Compose UI with QWERTY keys and panel states"
```

---

## Task 7: `LlamaCppKeyboardService` — InputMethodService

The keyboard service glues everything together: binds to `InferenceService`, detects selected text, hosts the `ComposeView`, and dispatches user actions.

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt`

**Background:** Hosting Compose inside an `InputMethodService` requires implementing four interfaces (`LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`) and wiring them to the `ComposeView` via `setViewTree*` calls. Without this, Compose will crash on start.

- [ ] **Step 1: Create `LlamaCppKeyboardService.kt`**

Create `app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

```
.\gradlew app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If you see `Unresolved reference: setViewTreeSavedStateRegistryOwner` or similar, add to `app/build.gradle.kts` dependencies:

```kotlin
implementation("androidx.savedstate:savedstate-ktx:1.2.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/keyboard/LlamaCppKeyboardService.kt
git commit -m "feat: add LlamaCppKeyboardService InputMethodService with ComposeView panel"
```

---

## Task 8: Full build + device installation

Build the APK, install it, enable the keyboard in Android settings, and verify the full flow.

**Files:** none — this is a device testing task.

- [ ] **Step 1: Build and install**

```
.\gradlew app:installDebug
```

Expected: BUILD SUCCESSFUL, app installed on device.

- [ ] **Step 2: Enable the keyboard on-device**

On the device:
1. Open **Settings → General Management → Keyboard list and default** (or search "keyboard" in Settings)
2. Tap **Add keyboard** → enable **LlamaCpp Chat**
3. In the same screen, set it as the default, or leave Gboard as default (the user switches per-use via the globe icon)

- [ ] **Step 3: Test the idle + ask flow**

1. Open any app with a text field (e.g. Notes)
2. Tap the text field to bring up the keyboard
3. Tap the globe/keyboard icon in the navigation bar → select **LlamaCpp Chat**
4. Verify: keyboard area shows the panel zone (text input + Ask button) above QWERTY keys
5. Type "What is 2+2?" using the QWERTY keys
6. Tap Ask (or ↵)
7. Verify: keys hide, panel expands, "Generating…" + stop button appears, tokens stream in
8. Verify: when done, Insert + Copy + Ask again buttons appear
9. Tap **Insert** — verify the response appears in the Notes text field
10. Tap **Ask again** — verify panel resets to Idle state

- [ ] **Step 4: Test the loading flow**

1. Force-stop the app: **Settings → Apps → LlamaCpp Chat → Force stop**
2. Open Notes, switch keyboard to LlamaCpp Chat
3. Type a prompt and tap Ask
4. Verify: panel shows Loading state with cycling messages ("Starting app…" → "Loading model…" → "Almost ready…")
5. Verify: main app opens in background during loading
6. Verify: after model loads, generation begins automatically

- [ ] **Step 5: Test selected text rewrite**

1. In Notes, type some text and select a word or sentence
2. Switch keyboard to LlamaCpp Chat
3. Verify: panel input is pre-filled with "Rewrite this: [selected text]" and "Selected text detected" label is shown
4. Tap Ask — verify the AI rewrites the text
5. Tap Insert — verify it replaces the input field content

- [ ] **Step 6: Test copy**

1. Complete a generation
2. Tap **Copy**
3. Verify: "Copied" label briefly appears on the button
4. Open a different app, long-press in a text field → Paste
5. Verify: the AI response pastes correctly

- [ ] **Step 7: Test engine busy**

1. Open the main LlamaCpp Chat app and start a long generation
2. Switch to another app, open LlamaCpp keyboard, type a prompt and tap Ask
3. Verify: error banner shows "Engine is busy — please wait"

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: keyboard IME integration complete"
```

---

## Spec coverage check

| Spec requirement | Covered by |
|-----------------|-----------|
| AIDL interfaces (IInferenceService, IInferenceCallback) | Task 2 |
| InferenceService bound service | Task 5 |
| IME metadata + manifest | Task 3 |
| LlamaCppKeyboardService | Task 7 |
| Panel zone: Idle state with text input | Task 6 |
| Panel zone: Loading with cycling messages | Task 6 |
| Panel zone: Generating with streaming + StopButton | Task 6 |
| Panel zone: Done with Insert + Copy + Ask again | Task 6 |
| Panel zone: Error with WarningBanner | Task 6 |
| QWERTY keys zone | Task 6 |
| 123 / ABC layer toggle | Task 6 |
| Keys hide during Generating/Done | Task 6 |
| Selected text detection + rewrite pre-fill | Task 7 |
| Open main app when model not loaded | Task 5 |
| Engine busy error | Task 5 |
| Insert via commitText | Task 7 |
| Copy to clipboard | Task 7 |
| isLoaded property on LlamaCppChatEngine | Task 1 |
