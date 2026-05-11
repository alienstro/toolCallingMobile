# Kotlin LiteRT Chatbot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android Kotlin chatbot app that downloads/imports a `.litertlm` model, runs LiteRT-LM locally, and supports deleting the installed model.

**Architecture:** Use a single native Android app with Jetpack Compose for UI, Kotlin coroutines for state and IO, app-controlled internal storage for model files, and a focused LiteRT-LM engine wrapper for inference. Keep model storage, downloading, diagnostics, and inference in separate classes so the UI can be tested without loading a real multi-GB model.

**Tech Stack:** Kotlin 2.2.21, Android Gradle Plugin, Jetpack Compose, Material 3, Navigation Compose, Kotlin coroutines, OkHttp, Android document picker, LiteRT-LM Android dependency, JUnit.

**Primary LiteRT-LM Reference:** Use the official Android Kotlin guide at `https://ai.google.dev/edge/litert-lm/android`. The guide documents the `com.google.ai.edge.litertlm:litertlm-android:latest.release` dependency and `Engine`, `EngineConfig`, `Backend`, and `Conversation` Kotlin API. This project pins the resolved dependency to `com.google.ai.edge.litertlm:litertlm-android:0.11.0` for reproducible builds.

---

## File Structure

Create a native Android project in the repository root:

- `settings.gradle.kts` - Gradle project settings.
- `build.gradle.kts` - root Gradle plugin declarations.
- `gradle.properties` - AndroidX and Gradle JVM memory settings.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` - Gradle wrapper for reproducible builds.
- `app/build.gradle.kts` - Android app, Compose, test, OkHttp, and LiteRT-LM dependencies.
- `app/src/main/AndroidManifest.xml` - app permissions and main activity declaration.
- `app/src/main/java/com/lance/litertchat/MainActivity.kt` - Compose host activity.
- `app/src/main/java/com/lance/litertchat/App.kt` - app-level navigation and dependency construction.
- `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt` - default URL and compatibility warning rules.
- `app/src/main/java/com/lance/litertchat/model/ModelMetadata.kt` - installed model metadata.
- `app/src/main/java/com/lance/litertchat/model/ModelRepository.kt` - model file storage and metadata persistence.
- `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt` - URL normalization and large file download.
- `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt` - LiteRT-LM wrapper.
- `app/src/main/java/com/lance/litertchat/diagnostics/DeviceDiagnostics.kt` - device and model diagnostics.
- `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt` - model lifecycle UI.
- `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt` - chatbot UI.
- `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt` - diagnostics UI.
- `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt` - shared UI state and actions.
- `app/src/test/java/com/lance/litertchat/model/ModelRepositoryTest.kt` - model metadata/storage tests.
- `app/src/test/java/com/lance/litertchat/download/ModelDownloaderTest.kt` - URL normalization tests.
- `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt` - lifecycle state tests.

## Task 1: Scaffold Native Android Kotlin Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/lance/litertchat/MainActivity.kt`

- [ ] **Step 1: Create Gradle settings**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ToolCallingMobile"
include(":app")
```

- [ ] **Step 2: Create root Gradle build file**

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
```

- [ ] **Step 3: Create app Gradle build file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lance.litertchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lance.litertchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 4: Create Gradle properties**

Create `gradle.properties`:

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
```

- [ ] **Step 5: Add Gradle wrapper**

Add Gradle wrapper files for Gradle 8.10.2:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

`gradle/wrapper/gradle-wrapper.properties` must contain:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 6: Create manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="LiteRT Chat"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Create minimal activity**

Create `app/src/main/java/com/lance/litertchat/MainActivity.kt`:

```kotlin
package com.lance.litertchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("LiteRT Chat")
                }
            }
        }
    }
}
```

- [ ] **Step 8: Add app theme resource**

Create `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 9: Run Gradle build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: build succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts app
git commit -m "chore: scaffold native android app"
```

If the repository has not been initialized yet, run `git init` before this commit.

## Task 2: Add Model Constants and URL Normalization

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`
- Create: `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`
- Create: `app/src/test/java/com/lance/litertchat/download/ModelDownloaderTest.kt`

- [ ] **Step 1: Write failing URL normalization tests**

Create `app/src/test/java/com/lance/litertchat/download/ModelDownloaderTest.kt`:

```kotlin
package com.lance.litertchat.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloaderTest {
    @Test
    fun convertsHuggingFaceBlobUrlToResolveUrl() {
        val input = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/main/gemma-4-E2B-it.litertlm"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            result
        )
    }

    @Test
    fun keepsDirectResolveUrlUnchanged() {
        val input = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        val result = ModelDownloader.normalizeModelUrl(input)

        assertEquals(input, result)
    }

    @Test
    fun requiresLitertLmExtension() {
        val input = "https://example.com/model.bin"

        val result = runCatching { ModelDownloader.normalizeModelUrl(input) }

        assertTrue(result.isFailure)
        assertEquals("Model URL must point to a .litertlm file.", result.exceptionOrNull()?.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.download.ModelDownloaderTest`

Expected: FAIL because `ModelDownloader` does not exist.

- [ ] **Step 3: Add model constants**

Create `app/src/main/java/com/lance/litertchat/model/ModelConstants.kt`:

```kotlin
package com.lance.litertchat.model

object ModelConstants {
    const val DEFAULT_MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    const val MODEL_EXTENSION = ".litertlm"

    fun hardwareWarningForFileName(fileName: String): String? {
        val lower = fileName.lowercase()
        return when {
            "qualcomm_sm8750" in lower ->
                "This model is optimized for Qualcomm SM8750 devices. Your OPPO Reno11 5G uses MediaTek Dimensity 7050, so use the generic model first."
            "qualcomm_gcs8275" in lower ->
                "This model is optimized for Qualcomm Dragonwing GCS8275 devices, not a typical OPPO Reno11 5G phone."
            else -> null
        }
    }
}
```

- [ ] **Step 4: Implement URL normalization**

Create `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`:

```kotlin
package com.lance.litertchat.download

import com.lance.litertchat.model.ModelConstants

class ModelDownloader {
    companion object {
        fun normalizeModelUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            require(trimmed.startsWith("https://")) { "Model URL must use HTTPS." }

            val normalized = trimmed.replace("/blob/", "/resolve/")
            require(normalized.substringBefore("?").endsWith(ModelConstants.MODEL_EXTENSION)) {
                "Model URL must point to a .litertlm file."
            }

            return normalized
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.download.ModelDownloaderTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/model/ModelConstants.kt app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt app/src/test/java/com/lance/litertchat/download/ModelDownloaderTest.kt
git commit -m "feat: normalize litert model urls"
```

## Task 3: Implement Model Metadata and Storage Repository

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/model/ModelMetadata.kt`
- Create: `app/src/main/java/com/lance/litertchat/model/ModelRepository.kt`
- Create: `app/src/test/java/com/lance/litertchat/model/ModelRepositoryTest.kt`

- [ ] **Step 1: Write metadata repository tests**

Create `app/src/test/java/com/lance/litertchat/model/ModelRepositoryTest.kt`:

```kotlin
package com.lance.litertchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ModelRepositoryTest {
    @Test
    fun savesAndLoadsMetadata() {
        val root = createTempDir()
        val repository = ModelRepository(root)
        val metadata = ModelMetadata(
            fileName = "gemma-4-E2B-it.litertlm",
            absolutePath = File(root, "models/gemma-4-E2B-it.litertlm").absolutePath,
            source = "download",
            sourceUrl = ModelConstants.DEFAULT_MODEL_URL,
            sizeBytes = 1234L,
            installedAtEpochMillis = 1000L
        )

        repository.saveMetadata(metadata)

        assertEquals(metadata, repository.loadMetadata())
    }

    @Test
    fun deleteInstalledModelClearsMetadataAndFile() {
        val root = createTempDir()
        val repository = ModelRepository(root)
        val modelFile = File(root, "models/gemma-4-E2B-it.litertlm")
        modelFile.parentFile?.mkdirs()
        modelFile.writeText("fake")
        repository.saveMetadata(
            ModelMetadata(
                fileName = modelFile.name,
                absolutePath = modelFile.absolutePath,
                source = "import",
                sourceUrl = null,
                sizeBytes = modelFile.length(),
                installedAtEpochMillis = 1000L
            )
        )

        repository.deleteInstalledModel()

        assertNull(repository.loadMetadata())
        assertEquals(false, modelFile.exists())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.model.ModelRepositoryTest`

Expected: FAIL because `ModelMetadata` and `ModelRepository` do not exist.

- [ ] **Step 3: Add metadata type**

Create `app/src/main/java/com/lance/litertchat/model/ModelMetadata.kt`:

```kotlin
package com.lance.litertchat.model

data class ModelMetadata(
    val fileName: String,
    val absolutePath: String,
    val source: String,
    val sourceUrl: String?,
    val sizeBytes: Long,
    val installedAtEpochMillis: Long
)
```

- [ ] **Step 4: Add model repository**

Create `app/src/main/java/com/lance/litertchat/model/ModelRepository.kt`:

```kotlin
package com.lance.litertchat.model

import java.io.File

class ModelRepository(private val rootDir: File) {
    private val modelDir = File(rootDir, "models")
    private val metadataFile = File(modelDir, "active-model.properties")

    fun modelDirectory(): File {
        modelDir.mkdirs()
        return modelDir
    }

    fun saveMetadata(metadata: ModelMetadata) {
        modelDirectory()
        metadataFile.writeText(
            listOf(
                "fileName=${metadata.fileName}",
                "absolutePath=${metadata.absolutePath}",
                "source=${metadata.source}",
                "sourceUrl=${metadata.sourceUrl.orEmpty()}",
                "sizeBytes=${metadata.sizeBytes}",
                "installedAtEpochMillis=${metadata.installedAtEpochMillis}"
            ).joinToString(separator = "\n")
        )
    }

    fun loadMetadata(): ModelMetadata? {
        if (!metadataFile.exists()) return null
        val values = metadataFile.readLines()
            .mapNotNull { line ->
                val index = line.indexOf("=")
                if (index == -1) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()

        val sourceUrl = values["sourceUrl"].orEmpty().ifBlank { null }
        return ModelMetadata(
            fileName = values.getValue("fileName"),
            absolutePath = values.getValue("absolutePath"),
            source = values.getValue("source"),
            sourceUrl = sourceUrl,
            sizeBytes = values.getValue("sizeBytes").toLong(),
            installedAtEpochMillis = values.getValue("installedAtEpochMillis").toLong()
        )
    }

    fun installedModelFile(): File? {
        return loadMetadata()?.let { File(it.absolutePath) }?.takeIf { it.exists() }
    }

    fun deleteInstalledModel() {
        loadMetadata()?.let { File(it.absolutePath).delete() }
        metadataFile.delete()
    }
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.model.ModelRepositoryTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/model app/src/test/java/com/lance/litertchat/model/ModelRepositoryTest.kt
git commit -m "feat: store installed model metadata"
```

## Task 4: Add Large File Download Implementation

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt`

- [ ] **Step 1: Extend downloader with progress API**

Modify `ModelDownloader.kt`:

```kotlin
package com.lance.litertchat.download

import com.lance.litertchat.model.ModelConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun download(
        rawUrl: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = normalizeModelUrl(rawUrl)
        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.download")
        if (tempFile.exists()) tempFile.delete()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download failed with HTTP ${response.code}.")
            }
            val body = response.body ?: error("Download response was empty.")
            val total = body.contentLength().takeIf { it >= 0L }
            var downloaded = 0L

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }

        if (destination.exists()) destination.delete()
        check(tempFile.renameTo(destination)) { "Could not move downloaded model into place." }
        destination
    }

    companion object {
        fun normalizeModelUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            require(trimmed.startsWith("https://")) { "Model URL must use HTTPS." }

            val normalized = trimmed.replace("/blob/", "/resolve/")
            require(normalized.substringBefore("?").endsWith(ModelConstants.MODEL_EXTENSION)) {
                "Model URL must point to a .litertlm file."
            }

            return normalized
        }
    }
}
```

- [ ] **Step 2: Run existing downloader tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.download.ModelDownloaderTest`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/download/ModelDownloader.kt
git commit -m "feat: download litert model files"
```

## Task 5: Add LiteRT-LM Engine Wrapper

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`

- [ ] **Step 1: Confirm LiteRT-LM dependency**

Confirm `app/build.gradle.kts` dependencies include this line:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
```

The official LiteRT-LM Android Kotlin guide documents the `latest.release` Maven coordinate for Android Gradle users. The project pins the resolved version to `0.11.0` so builds do not change when Google publishes a new LiteRT-LM release.

- [ ] **Step 2: Add engine wrapper**

Create `app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt`:

```kotlin
package com.lance.litertchat.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtChatEngine {
    private var loadedModelPath: String? = null
    private var engine: Engine? = null

    suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(modelFile.exists()) { "Model file does not exist: ${modelFile.absolutePath}" }
            require(modelFile.name.endsWith(".litertlm")) { "Model file must end with .litertlm." }
            if (loadedModelPath == modelFile.absolutePath && engine != null) return@runCatching

            release()
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU()
            )
            engine = Engine(config).also { it.initialize() }
            loadedModelPath = modelFile.absolutePath
        }
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val currentEngine = requireNotNull(engine) { "Model is not loaded." }
            require(prompt.isNotBlank()) { "Prompt cannot be blank." }

            currentEngine.createConversation().use { conversation ->
                conversation.sendMessage(prompt).toString()
            }
        }
    }

    fun release() {
        engine?.close()
        engine = null
        loadedModelPath = null
    }
}
```

- [ ] **Step 3: Resolve dependency and compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: PASS with the LiteRT-LM Android dependency and Kotlin API imports resolved.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/lance/litertchat/inference/LiteRtChatEngine.kt
git commit -m "feat: add litert chat engine wrapper"
```

## Task 6: Build App ViewModel State Machine

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Create: `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`

- [ ] **Step 1: Write ViewModel state tests**

Create `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`:

```kotlin
package com.lance.litertchat.ui

import com.lance.litertchat.model.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTest {
    @Test
    fun chatIsDisabledWithoutModel() {
        val state = AppState(activeModel = null)

        assertFalse(state.canChat)
    }

    @Test
    fun chatIsEnabledWithInstalledModelWhenIdle() {
        val state = AppState(
            activeModel = ModelMetadata(
                fileName = "gemma-4-E2B-it.litertlm",
                absolutePath = "/tmp/gemma-4-E2B-it.litertlm",
                source = "download",
                sourceUrl = null,
                sizeBytes = 100L,
                installedAtEpochMillis = 1L
            )
        )

        assertTrue(state.canChat)
    }

    @Test
    fun sendAddsUserAndAssistantMessages() {
        val state = AppState()
            .withUserMessage("Hello")
            .withAssistantMessage("Hi")

        assertEquals(2, state.messages.size)
        assertEquals("user", state.messages[0].role)
        assertEquals("assistant", state.messages[1].role)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.ui.AppViewModelTest`

Expected: FAIL because `AppState` does not exist.

- [ ] **Step 3: Add app state and ViewModel shell**

Create `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`:

```kotlin
package com.lance.litertchat.ui

import androidx.lifecycle.ViewModel
import com.lance.litertchat.model.ModelMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ChatMessage(
    val role: String,
    val content: String
)

data class AppState(
    val activeModel: ModelMetadata? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isDownloading: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val downloadProgressText: String? = null,
    val errorText: String? = null
) {
    val canChat: Boolean
        get() = activeModel != null && !isDownloading && !isLoadingModel && !isGenerating

    fun withUserMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "user", content = content))

    fun withAssistantMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "assistant", content = content))
}

class AppViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutableState
}
```

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.lance.litertchat.ui.AppViewModelTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt
git commit -m "feat: add chatbot app state"
```

## Task 7: Add Compose Navigation and Screens

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/App.kt`
- Create: `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`
- Create: `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt`
- Create: `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/MainActivity.kt`

- [ ] **Step 1: Create app navigation**

Create `app/src/main/java/com/lance/litertchat/App.kt`:

```kotlin
package com.lance.litertchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lance.litertchat.ui.AppViewModel
import com.lance.litertchat.ui.ChatScreen
import com.lance.litertchat.ui.DiagnosticsScreen
import com.lance.litertchat.ui.ModelManagerScreen

@Composable
fun LiteRtChatApp(appViewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by appViewModel.state.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: "models"

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("models" to "Models", "chat" to "Chat", "diagnostics" to "Diagnostics").forEach { (route, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navController.navigate(route) },
                            label = { Text(label) },
                            icon = {}
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = "models") {
                composable("models") { ModelManagerScreen(state = state, contentPadding = padding) }
                composable("chat") { ChatScreen(state = state, contentPadding = padding) }
                composable("diagnostics") { DiagnosticsScreen(state = state, contentPadding = padding) }
            }
        }
    }
}
```

- [ ] **Step 2: Create Model Manager screen**

Create `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`:

```kotlin
package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lance.litertchat.model.ModelConstants

@Composable
fun ModelManagerScreen(state: AppState, contentPadding: PaddingValues) {
    val url = remember { mutableStateOf(ModelConstants.DEFAULT_MODEL_URL) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Model Manager")
        Spacer(Modifier.height(12.dp))
        Text("Installed: ${state.activeModel?.fileName ?: "No model installed"}")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = url.value, onValueChange = { url.value = it }, label = { Text("Model URL") })
        Spacer(Modifier.height(12.dp))
        Button(onClick = {}) { Text("Download") }
        Button(onClick = {}) { Text("Import .litertlm") }
        Button(onClick = {}) { Text("Delete model") }
        state.downloadProgressText?.let { Text(it) }
        state.errorText?.let { Text(it) }
    }
}
```

- [ ] **Step 3: Create Chat screen**

Create `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt`:

```kotlin
package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(state: AppState, contentPadding: PaddingValues) {
    val prompt = remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Chat")
        if (!state.canChat) {
            Text("Install and load a model before chatting.")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.messages) { message ->
                Text("${message.role}: ${message.content}")
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = prompt.value,
                onValueChange = { prompt.value = it },
                label = { Text("Message") }
            )
            Button(enabled = state.canChat, onClick = {}) { Text("Send") }
        }
    }
}
```

- [ ] **Step 4: Create Diagnostics screen**

Create `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`:

```kotlin
package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(state: AppState, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Diagnostics")
        Text("Model path: ${state.activeModel?.absolutePath ?: "None"}")
        Text("Model size: ${state.activeModel?.sizeBytes ?: 0} bytes")
        Text("Last error: ${state.errorText ?: "None"}")
    }
}
```

- [ ] **Step 5: Wire activity to app**

Modify `MainActivity.kt`:

```kotlin
package com.lance.litertchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiteRtChatApp()
        }
    }
}
```

- [ ] **Step 6: Build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lance/litertchat
git commit -m "feat: add compose app screens"
```

## Task 8: Wire Model Download, Import, and Delete Actions

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/lance/litertchat/App.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`

- [ ] **Step 1: Add ViewModel dependencies and actions**

Modify `AppViewModel.kt`:

```kotlin
package com.lance.litertchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lance.litertchat.download.ModelDownloader
import com.lance.litertchat.model.ModelMetadata
import com.lance.litertchat.model.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(
    val role: String,
    val content: String
)

data class AppState(
    val activeModel: ModelMetadata? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isDownloading: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val downloadProgressText: String? = null,
    val errorText: String? = null
) {
    val canChat: Boolean
        get() = activeModel != null && !isDownloading && !isLoadingModel && !isGenerating

    fun withUserMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "user", content = content))

    fun withAssistantMessage(content: String): AppState =
        copy(messages = messages + ChatMessage(role = "assistant", content = content))
}

class AppViewModel(
    private val repository: ModelRepository,
    private val downloader: ModelDownloader = ModelDownloader()
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppState(activeModel = repository.loadMetadata()))
    val state: StateFlow<AppState> = mutableState

    fun downloadModel(url: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(isDownloading = true, errorText = null, downloadProgressText = "Starting download") }
            runCatching {
                val normalized = ModelDownloader.normalizeModelUrl(url)
                val fileName = normalized.substringBefore("?").substringAfterLast("/")
                val destination = File(repository.modelDirectory(), fileName)
                downloader.download(normalized, destination) { downloaded, total ->
                    mutableState.update {
                        val totalText = total?.let { value -> " / $value" }.orEmpty()
                        it.copy(downloadProgressText = "$downloaded$totalText bytes")
                    }
                }
                val metadata = ModelMetadata(
                    fileName = destination.name,
                    absolutePath = destination.absolutePath,
                    source = "download",
                    sourceUrl = normalized,
                    sizeBytes = destination.length(),
                    installedAtEpochMillis = System.currentTimeMillis()
                )
                repository.saveMetadata(metadata)
                metadata
            }.onSuccess { metadata ->
                mutableState.update { it.copy(activeModel = metadata, isDownloading = false, downloadProgressText = "Download complete") }
            }.onFailure { error ->
                mutableState.update { it.copy(isDownloading = false, errorText = error.message ?: "Download failed") }
            }
        }
    }

    fun registerImportedModel(file: File) {
        val metadata = ModelMetadata(
            fileName = file.name,
            absolutePath = file.absolutePath,
            source = "import",
            sourceUrl = null,
            sizeBytes = file.length(),
            installedAtEpochMillis = System.currentTimeMillis()
        )
        repository.saveMetadata(metadata)
        mutableState.update { it.copy(activeModel = metadata, errorText = null) }
    }

    fun deleteModel() {
        repository.deleteInstalledModel()
        mutableState.update { it.copy(activeModel = null, messages = emptyList(), errorText = null, downloadProgressText = null) }
    }
}
```

- [ ] **Step 2: Add ViewModel factory in app**

Modify `App.kt`:

```kotlin
package com.lance.litertchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lance.litertchat.model.ModelRepository
import com.lance.litertchat.ui.AppViewModel
import com.lance.litertchat.ui.ChatScreen
import com.lance.litertchat.ui.DiagnosticsScreen
import com.lance.litertchat.ui.ModelManagerScreen

@Composable
fun LiteRtChatApp() {
    val context = LocalContext.current
    val appViewModel: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(ModelRepository(context.filesDir)) as T
        }
    })
    val navController = rememberNavController()
    val state by appViewModel.state.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: "models"

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("models" to "Models", "chat" to "Chat", "diagnostics" to "Diagnostics").forEach { (route, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navController.navigate(route) },
                            label = { Text(label) },
                            icon = {}
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = "models") {
                composable("models") {
                    ModelManagerScreen(
                        state = state,
                        contentPadding = padding,
                        onDownload = appViewModel::downloadModel,
                        onDelete = appViewModel::deleteModel
                    )
                }
                composable("chat") { ChatScreen(state = state, contentPadding = padding) }
                composable("diagnostics") { DiagnosticsScreen(state = state, contentPadding = padding) }
            }
        }
    }
}
```

- [ ] **Step 3: Wire Model Manager buttons**

Modify `ModelManagerScreen.kt` function signature and buttons:

```kotlin
@Composable
fun ModelManagerScreen(
    state: AppState,
    contentPadding: PaddingValues,
    onDownload: (String) -> Unit,
    onDelete: () -> Unit
) {
    val url = remember { mutableStateOf(ModelConstants.DEFAULT_MODEL_URL) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Model Manager")
        Spacer(Modifier.height(12.dp))
        Text("Installed: ${state.activeModel?.fileName ?: "No model installed"}")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = url.value, onValueChange = { url.value = it }, label = { Text("Model URL") })
        Spacer(Modifier.height(12.dp))
        Button(enabled = !state.isDownloading, onClick = { onDownload(url.value) }) { Text("Download") }
        Button(onClick = {}) { Text("Import .litertlm") }
        Button(enabled = state.activeModel != null, onClick = onDelete) { Text("Delete model") }
        state.downloadProgressText?.let { Text(it) }
        state.errorText?.let { Text(it) }
    }
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat
git commit -m "feat: wire model download and delete"
```

## Task 9: Add Device Diagnostics

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/diagnostics/DeviceDiagnostics.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt`

- [ ] **Step 1: Add diagnostics provider**

Create `app/src/main/java/com/lance/litertchat/diagnostics/DeviceDiagnostics.kt`:

```kotlin
package com.lance.litertchat.diagnostics

import android.os.Build
import java.io.File

data class DiagnosticsInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val availableStorageBytes: Long
)

object DeviceDiagnostics {
    fun collect(filesDir: File): DiagnosticsInfo {
        return DiagnosticsInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            availableStorageBytes = filesDir.usableSpace
        )
    }
}
```

- [ ] **Step 2: Display diagnostics**

Modify `DiagnosticsScreen.kt` to accept diagnostics text later by keeping model diagnostics visible now:

```kotlin
package com.lance.litertchat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lance.litertchat.diagnostics.DeviceDiagnostics

@Composable
fun DiagnosticsScreen(state: AppState, contentPadding: PaddingValues) {
    val info = DeviceDiagnostics.collect(LocalContext.current.filesDir)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Diagnostics")
        Text("Device: ${info.manufacturer} ${info.model}")
        Text("Android: ${info.androidVersion} API ${info.apiLevel}")
        Text("Available app storage: ${info.availableStorageBytes} bytes")
        Text("Model path: ${state.activeModel?.absolutePath ?: "None"}")
        Text("Model size: ${state.activeModel?.sizeBytes ?: 0} bytes")
        Text("Last error: ${state.errorText ?: "None"}")
    }
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lance/litertchat/diagnostics app/src/main/java/com/lance/litertchat/ui/DiagnosticsScreen.kt
git commit -m "feat: show device diagnostics"
```

## Task 10: Wire Chat Generation

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/lance/litertchat/App.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt`

- [ ] **Step 1: Add engine dependency and send action**

Modify `AppViewModel` constructor and add `sendMessage`:

```kotlin
class AppViewModel(
    private val repository: ModelRepository,
    private val downloader: ModelDownloader = ModelDownloader(),
    private val engine: LiteRtChatEngine = LiteRtChatEngine()
) : ViewModel() {
    // keep existing state code

    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        val model = mutableState.value.activeModel ?: run {
            mutableState.update { it.copy(errorText = "Install a model before chatting.") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.withUserMessage(prompt).copy(isGenerating = true, errorText = null) }
            engine.load(File(model.absolutePath))
                .fold(
                    onSuccess = {
                        engine.generate(prompt)
                            .onSuccess { response ->
                                mutableState.update { it.withAssistantMessage(response).copy(isGenerating = false) }
                            }
                            .onFailure { error ->
                                mutableState.update { it.copy(isGenerating = false, errorText = error.message ?: "Generation failed") }
                            }
                    },
                    onFailure = { error ->
                        mutableState.update { it.copy(isGenerating = false, errorText = error.message ?: "Model load failed") }
                    }
                )
        }
    }
}
```

Add this import:

```kotlin
import com.lance.litertchat.inference.LiteRtChatEngine
```

- [ ] **Step 2: Pass send action to Chat screen**

Modify `App.kt` chat route:

```kotlin
composable("chat") {
    ChatScreen(
        state = state,
        contentPadding = padding,
        onSend = appViewModel::sendMessage
    )
}
```

- [ ] **Step 3: Wire Chat screen send button**

Modify `ChatScreen.kt` signature and button:

```kotlin
@Composable
fun ChatScreen(
    state: AppState,
    contentPadding: PaddingValues,
    onSend: (String) -> Unit
) {
    val prompt = remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text("Chat")
        if (!state.canChat) {
            Text("Install and load a model before chatting.")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.messages) { message ->
                Text("${message.role}: ${message.content}")
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = prompt.value,
                onValueChange = { prompt.value = it },
                label = { Text("Message") }
            )
            Button(
                enabled = state.canChat && prompt.value.isNotBlank(),
                onClick = {
                    onSend(prompt.value)
                    prompt.value = ""
                }
            ) { Text("Send") }
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat
git commit -m "feat: wire chat generation flow"
```

## Task 11: Add Import Flow

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/ModelManagerScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/App.kt`
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`

- [ ] **Step 1: Add import function to ViewModel**

Add this method to `AppViewModel`:

```kotlin
fun importModelFromUri(context: android.content.Context, uri: android.net.Uri) {
    viewModelScope.launch {
        runCatching {
            val destination = File(repository.modelDirectory(), "imported-${System.currentTimeMillis()}.litertlm")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected file." }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            registerImportedModel(destination)
        }.onFailure { error ->
            mutableState.update { it.copy(errorText = error.message ?: "Import failed") }
        }
    }
}
```

- [ ] **Step 2: Pass import action through app**

Modify `App.kt` model route:

```kotlin
ModelManagerScreen(
    state = state,
    contentPadding = padding,
    onDownload = appViewModel::downloadModel,
    onDelete = appViewModel::deleteModel,
    onImport = { uri -> appViewModel.importModelFromUri(context, uri) }
)
```

- [ ] **Step 3: Add document picker to Model Manager**

Modify `ModelManagerScreen.kt`:

```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
```

Update the function signature:

```kotlin
fun ModelManagerScreen(
    state: AppState,
    contentPadding: PaddingValues,
    onDownload: (String) -> Unit,
    onDelete: () -> Unit,
    onImport: (Uri) -> Unit
)
```

Add this before `Column`:

```kotlin
val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onImport(uri)
}
```

Replace the import button:

```kotlin
Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import .litertlm") }
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/litertchat
git commit -m "feat: import local litert model"
```

## Task 12: Device Verification on OPPO Reno11 5G

**Files:**
- No source files required unless verification reveals bugs.

- [ ] **Step 1: Confirm phone is visible**

Run: `adb devices`

Expected: one authorized device appears.

- [ ] **Step 2: Install debug build**

Run: `.\gradlew.bat :app:installDebug`

Expected: app installs on the OPPO Reno11 5G.

- [ ] **Step 3: Verify no-model behavior**

Open app on phone.

Expected:
- Model Manager shows no installed model.
- Chat tab disables send.
- Diagnostics shows OPPO device details and no model path.

- [ ] **Step 4: Verify default download flow**

Tap `Download` with the default URL.

Expected:
- Download starts.
- Progress text updates.
- On success, installed model filename is `gemma-4-E2B-it.litertlm`.
- Chat tab enables send.

- [ ] **Step 5: Verify delete flow**

Tap `Delete model`.

Expected:
- Installed model returns to none.
- Chat disables send.
- Diagnostics model path returns to none.

- [ ] **Step 6: Verify import flow**

Import a local `.litertlm` file using the document picker.

Expected:
- Imported file is copied to app storage.
- Installed model metadata updates.
- Chat enables send.

- [ ] **Step 7: Verify chat flow**

Send: `Hello. Reply in one short sentence.`

Expected:
- User message appears.
- App shows generating state.
- Assistant response or useful LiteRT-LM error appears.

- [ ] **Step 8: Capture logs if inference fails**

Run: `adb logcat | findstr /i "litert LiteRT gemma com.lance.litertchat"`

Expected: logs show the native LiteRT-LM load/generation error needed for the next fix.

- [ ] **Step 9: Commit verification fixes**

If fixes were needed:

```bash
git add app
git commit -m "fix: address device verification issues"
```

## Task 13: Document Later Hugging Face Search Work

**Files:**
- Create: `docs/later-hugging-face-model-search.md`

- [ ] **Step 1: Create later feature note**

Create `docs/later-hugging-face-model-search.md`:

```markdown
# Later: Hugging Face Model Search

After the simple local LiteRT-LM chatbot works on device, add model discovery:

- Search Hugging Face for LiteRT-LM compatible repositories.
- Browse files for a selected repository.
- Show only `.litertlm` files as selectable model files.
- Convert selected Hugging Face `/blob/` URLs to `/resolve/` URLs for download.
- Show file size before download when available.
- Warn when filenames include hardware-specific hints such as `qualcomm_sm8750` or `qualcomm_gcs8275`.
- Recommend generic Android-compatible models unless the device hardware matches a specialized model.

This is intentionally out of v1 scope because v1 must first prove local download/import/delete and LiteRT-LM inference on the OPPO Reno11 5G.
```

- [ ] **Step 2: Commit**

```bash
git add docs/later-hugging-face-model-search.md
git commit -m "docs: note later hugging face model search"
```

## Final Verification

- [ ] Run unit tests:

```bash
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] Build debug APK:

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: PASS.

- [ ] Install on phone:

```bash
.\gradlew.bat :app:installDebug
```

Expected: app installs on the authorized USB-debugged Android device.

- [ ] Manually verify model manager, delete, import, diagnostics, and chat behavior on OPPO Reno11 5G.
