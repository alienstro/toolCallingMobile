# Key-Value Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lightweight user-controlled key-value memories to the Android LiteRT chat app and inject only a capped relevant memory block into model prompts.

**Architecture:** Add a focused `MemoryRepository` backed by app-private properties storage, wire it through `AppViewModel`, and expose manual memory editing in Settings. Prompt construction remains local and cheap: active formatter, at most 6 key-value memories, then the latest user message.

**Tech Stack:** Kotlin, Android app-private file storage, Java `Properties`, Jetpack Compose, JUnit 4, kotlinx-coroutines-test.

---

## File Structure

- Create `app/src/main/java/com/lance/litertchat/memory/MemoryRepository.kt`
  Owns the `MemoryItem` model, persistence, cleanup, ordering, deletion, and prompt-memory selection.
- Create `app/src/test/java/com/lance/litertchat/memory/MemoryRepositoryTest.kt`
  Unit tests for storage and selection behavior.
- Modify `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
  Adds memory state, actions, dependency injection, and memory-aware `promptForModel()`.
- Modify `app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt`
  Adds a manual key-value memory editor and list.
- Modify `app/src/main/java/com/lance/litertchat/App.kt`
  Passes ViewModel memory callbacks into Settings.
- Modify `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`
  Adds memory prompt and action tests, updates helper construction.

---

### Task 1: Memory Repository

**Files:**
- Create: `app/src/main/java/com/lance/litertchat/memory/MemoryRepository.kt`
- Test: `app/src/test/java/com/lance/litertchat/memory/MemoryRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

Create `app/src/test/java/com/lance/litertchat/memory/MemoryRepositoryTest.kt`:

```kotlin
package com.lance.litertchat.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun upsertCleansKeyAndPersistsMemory() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.upsertMemory(" User Name ", " Lance ", now = 1000L)

        assertEquals(
            listOf(MemoryItem("user.name", "Lance", 1000L)),
            MemoryRepository(temporaryFolder.root).loadMemories()
        )
    }

    @Test
    fun upsertReplacesExistingKeyAndMovesItToMostRecent() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.upsertMemory("project.current", "old app", now = 1000L)
        repository.upsertMemory("user.name", "Lance", now = 2000L)
        repository.upsertMemory("project.current", "LiteRT Android app", now = 3000L)

        assertEquals(
            listOf(
                MemoryItem("project.current", "LiteRT Android app", 3000L),
                MemoryItem("user.name", "Lance", 2000L)
            ),
            repository.loadMemories()
        )
    }

    @Test
    fun blankKeyOrValueIsIgnored() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.upsertMemory("", "Lance", now = 1000L)
        repository.upsertMemory("user.name", "   ", now = 2000L)

        assertTrue(repository.loadMemories().isEmpty())
    }

    @Test
    fun deleteRemovesMemoryByCleanedKey() {
        val repository = MemoryRepository(temporaryFolder.root)
        repository.upsertMemory("User Name", "Lance", now = 1000L)

        repository.deleteMemory(" user name ")

        assertTrue(repository.loadMemories().isEmpty())
    }

    @Test
    fun selectForPromptIncludesPinnedRelevantThenRecentMemories() {
        val repository = MemoryRepository(temporaryFolder.root)
        repository.upsertMemory("project.current", "LiteRT Android app", now = 1000L)
        repository.upsertMemory("user.name", "Lance", now = 2000L)
        repository.upsertMemory("user.prefers", "concise answers", now = 3000L)
        repository.upsertMemory("food.favorite", "adobo", now = 4000L)

        assertEquals(
            listOf(
                MemoryItem("user.name", "Lance", 2000L),
                MemoryItem("user.prefers", "concise answers", 3000L),
                MemoryItem("project.current", "LiteRT Android app", 1000L),
                MemoryItem("food.favorite", "adobo", 4000L)
            ),
            repository.selectForPrompt("How should this Android project remember me?")
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lance.litertchat.memory.MemoryRepositoryTest"
```

Expected: FAIL because `MemoryRepository` and `MemoryItem` do not exist.

- [ ] **Step 3: Implement repository**

Create `app/src/main/java/com/lance/litertchat/memory/MemoryRepository.kt`:

```kotlin
package com.lance.litertchat.memory

import java.io.File
import java.util.Base64
import java.util.Properties

data class MemoryItem(
    val key: String,
    val value: String,
    val updatedAtEpochMillis: Long
)

class MemoryRepository(private val rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val memoryFile = File(settingsDir, "memories.properties")

    fun loadMemories(): List<MemoryItem> {
        if (!memoryFile.exists()) return emptyList()

        val properties = Properties()
        memoryFile.inputStream().use { properties.load(it) }

        return properties.getProperty(KEY_MEMORY_KEYS)
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { encodedKey ->
                val key = decode(encodedKey) ?: return@mapNotNull null
                val value = decode(properties.getProperty("memory.$encodedKey.value")) ?: return@mapNotNull null
                val updatedAt = properties.getProperty("memory.$encodedKey.updatedAt")
                    ?.toLongOrNull()
                    ?: return@mapNotNull null
                MemoryItem(key = key, value = value, updatedAtEpochMillis = updatedAt)
            }
            .sortedByDescending { it.updatedAtEpochMillis }
    }

    fun upsertMemory(key: String, value: String, now: Long = System.currentTimeMillis()) {
        val cleanedKey = cleanKey(key) ?: return
        val cleanedValue = cleanValue(value) ?: return
        val next = (
            listOf(MemoryItem(cleanedKey, cleanedValue, now)) +
                loadMemories().filterNot { it.key == cleanedKey }
            )
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(MAX_MEMORIES)
        saveMemories(next)
    }

    fun deleteMemory(key: String) {
        val cleanedKey = cleanKey(key) ?: return
        saveMemories(loadMemories().filterNot { it.key == cleanedKey })
    }

    fun selectForPrompt(userPrompt: String, limit: Int = PROMPT_MEMORY_LIMIT): List<MemoryItem> {
        val memories = loadMemories()
        val pinned = memories.filter { it.key in PINNED_KEYS }
            .sortedBy { PINNED_KEYS.indexOf(it.key) }
        val words = userPrompt.lowercase()
            .split(Regex("[^a-z0-9.]+"))
            .filter { it.length >= 3 }
            .toSet()
        val relevant = memories.filter { memory ->
            memory !in pinned && words.any { word ->
                memory.key.contains(word) || memory.value.lowercase().contains(word)
            }
        }
        val recent = memories.filter { it !in pinned && it !in relevant }
        return (pinned + relevant + recent).take(limit)
    }

    private fun saveMemories(memories: List<MemoryItem>) {
        settingsDir.mkdirs()
        val properties = Properties()
        properties.setProperty(KEY_MEMORY_KEYS, memories.joinToString(",") { encode(it.key) })
        memories.forEach { memory ->
            val encodedKey = encode(memory.key)
            properties.setProperty("memory.$encodedKey.value", encode(memory.value))
            properties.setProperty("memory.$encodedKey.updatedAt", memory.updatedAtEpochMillis.toString())
        }
        memoryFile.outputStream().use { output -> properties.store(output, null) }
    }

    private fun cleanKey(rawKey: String): String? {
        val key = rawKey.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .take(MAX_KEY_LENGTH)
            .trim('.')
        return key.takeIf { it.isNotBlank() }
    }

    private fun cleanValue(rawValue: String): String? {
        val value = rawValue.trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_VALUE_LENGTH)
            .trim()
        return value.takeIf { it.isNotBlank() }
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String?): String? =
        value?.let { encoded -> String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }

    private companion object {
        const val KEY_MEMORY_KEYS = "memoryKeys"
        const val MAX_KEY_LENGTH = 48
        const val MAX_VALUE_LENGTH = 160
        const val MAX_MEMORIES = 40
        const val PROMPT_MEMORY_LIMIT = 6
        val PINNED_KEYS = listOf("user.name", "user.prefers")
    }
}
```

- [ ] **Step 4: Run repository tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lance.litertchat.memory.MemoryRepositoryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lance/litertchat/memory/MemoryRepository.kt app/src/test/java/com/lance/litertchat/memory/MemoryRepositoryTest.kt
git commit -m "feat: add key value memory repository"
```

---

### Task 2: ViewModel State And Prompt Injection

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt`
- Modify: `app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

In `AppViewModelTest.kt`, add imports:

```kotlin
import com.lance.litertchat.memory.MemoryItem
import com.lance.litertchat.memory.MemoryRepository
```

Add tests near the prompt formatter tests:

```kotlin
@Test
fun viewModelStartsWithPersistedMemories() {
    val memoryRepository = MemoryRepository(temporaryFolder.root)
    memoryRepository.upsertMemory("user.name", "Lance", now = 1000L)

    val viewModel = testViewModel(
        repository = ModelRepository(temporaryFolder.root),
        memoryRepository = memoryRepository
    )

    assertEquals(listOf(MemoryItem("user.name", "Lance", 1000L)), viewModel.state.value.memories)
}

@Test
fun memoryActionsUpdateStateAndRepository() {
    val memoryRepository = MemoryRepository(temporaryFolder.root)
    val viewModel = testViewModel(
        repository = ModelRepository(temporaryFolder.root),
        memoryRepository = memoryRepository
    )

    viewModel.upsertMemory(" User Name ", " Lance ")
    viewModel.deleteMemory("missing")

    assertEquals("user.name", viewModel.state.value.memories.single().key)
    assertEquals("Lance", memoryRepository.loadMemories().single().value)

    viewModel.deleteMemory("User Name")

    assertTrue(viewModel.state.value.memories.isEmpty())
    assertTrue(memoryRepository.loadMemories().isEmpty())
}

@Test
fun sendMessageInjectsRelevantMemoriesIntoModelPrompt() = runTest(mainDispatcherRule.testDispatcher) {
    val modelFile = File(temporaryFolder.root, "model.litertlm")
    modelFile.writeText("model")
    val repository = ModelRepository(temporaryFolder.root)
    repository.saveMetadata(installedModel(path = modelFile.absolutePath))
    val memoryRepository = MemoryRepository(temporaryFolder.root)
    memoryRepository.upsertMemory("user.name", "Lance", now = 1000L)
    memoryRepository.upsertMemory("project.current", "local LiteRT Android chat app", now = 2000L)
    val engine = FakeChatEngine(response = "Done")
    val viewModel = testViewModel(
        repository = repository,
        memoryRepository = memoryRepository,
        engine = engine
    )

    viewModel.sendMessage("How should this Android project store memory?")
    advanceUntilIdle()

    assertEquals(
        listOf(
            "${PromptFormatterRepository.DEFAULT_FORMATTER_BODY}\n\n" +
                "Memory:\n" +
                "- user.name: Lance\n" +
                "- project.current: local LiteRT Android chat app\n\n" +
                "User message:\n" +
                "How should this Android project store memory?"
        ),
        engine.streamingPrompts
    )
}
```

Update the `testViewModel` helper signature:

```kotlin
memoryRepository: MemoryRepository = MemoryRepository(temporaryFolder.root),
```

and pass it to `AppViewModel`:

```kotlin
memoryRepository = memoryRepository,
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lance.litertchat.ui.AppViewModelTest"
```

Expected: FAIL because `AppState.memories`, `AppViewModel.upsertMemory`, `AppViewModel.deleteMemory`, and the constructor dependency do not exist.

- [ ] **Step 3: Modify AppViewModel**

In `AppViewModel.kt`, add import:

```kotlin
import com.lance.litertchat.memory.MemoryItem
import com.lance.litertchat.memory.MemoryRepository
```

Add to `AppState`:

```kotlin
val memories: List<MemoryItem> = emptyList(),
```

Add constructor dependency after `chatHistoryRepository`:

```kotlin
private val memoryRepository: MemoryRepository = MemoryRepository(repository.rootDir),
```

Load initial memories:

```kotlin
private val initialMemories = memoryRepository.loadMemories()
```

Set initial state:

```kotlin
memories = initialMemories,
```

Add actions:

```kotlin
fun upsertMemory(key: String, value: String) {
    memoryRepository.upsertMemory(key, value)
    refreshMemoryState()
}

fun deleteMemory(key: String) {
    memoryRepository.deleteMemory(key)
    refreshMemoryState()
}

private fun refreshMemoryState() {
    mutableState.update { it.copy(memories = memoryRepository.loadMemories()) }
}
```

Replace `promptForModel()` with:

```kotlin
private fun promptForModel(userPrompt: String, hasImage: Boolean = false): String {
    val formatter = promptFormatterRepository.loadState().activeFormatter
    val formatterBody = formatter?.body.orEmpty().trim()
    val prompt = if (userPrompt.isBlank() && hasImage) {
        "Describe this image."
    } else {
        userPrompt
    }
    val memoryBlock = memoryRepository.selectForPrompt(prompt)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "Memory:\n") { memory ->
            "- ${memory.key}: ${memory.value}"
        }

    return listOf(formatterBody, memoryBlock, "User message:\n$prompt")
        .filterNot { it.isNullOrBlank() }
        .joinToString("\n\n")
}
```

- [ ] **Step 4: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lance.litertchat.ui.AppViewModelTest"
```

Expected: PASS after updating any expected prompt strings that assume no memory block when the memory repository is empty.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lance/litertchat/ui/AppViewModel.kt app/src/test/java/com/lance/litertchat/ui/AppViewModelTest.kt
git commit -m "feat: inject key value memories into prompts"
```

---

### Task 3: Settings Memory Editor

**Files:**
- Modify: `app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/lance/litertchat/App.kt`

- [ ] **Step 1: Extend SettingsScreen API**

In `SettingsScreen.kt`, add parameters:

```kotlin
onUpsertMemory: (String, String) -> Unit,
onDeleteMemory: (String) -> Unit
```

Add local editor state near the formatter editor state:

```kotlin
var memoryKey by rememberSaveable { mutableStateOf("") }
var memoryValue by rememberSaveable { mutableStateOf("") }
```

- [ ] **Step 2: Add memory editor UI**

Add this LazyColumn item after the Generation card and before Formatter editor:

```kotlin
item {
    SectionTitle("Memory")
    AppCard {
        OutlinedTextField(
            value = memoryKey,
            onValueChange = { memoryKey = it },
            label = { Text("Memory key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppBackground,
                unfocusedContainerColor = AppBackground
            )
        )
        OutlinedTextField(
            value = memoryValue,
            onValueChange = { memoryValue = it },
            label = { Text("Memory value") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppBackground,
                unfocusedContainerColor = AppBackground
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactActionButton(
                text = "Save",
                enabled = memoryKey.isNotBlank() && memoryValue.isNotBlank(),
                primary = true,
                onClick = {
                    onUpsertMemory(memoryKey, memoryValue)
                    memoryKey = ""
                    memoryValue = ""
                },
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = "Clear",
                onClick = {
                    memoryKey = ""
                    memoryValue = ""
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

Add a memory list after the editor item:

```kotlin
item {
    SectionTitle("Saved memories")
}
items(state.memories) { memory ->
    AppCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(memory.key, color = AppText, fontWeight = FontWeight.ExtraBold)
            Text(
                text = memory.value,
                color = AppMuted,
                modifier = Modifier
                    .padding(top = 7.dp)
                    .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                    .background(AppBackground, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactActionButton(
                "Edit",
                onClick = {
                    memoryKey = memory.key
                    memoryValue = memory.value
                },
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                "Delete",
                onClick = { onDeleteMemory(memory.key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

- [ ] **Step 3: Wire callbacks in App.kt**

In `App.kt`, update the `SettingsScreen` call:

```kotlin
onUpsertMemory = appViewModel::upsertMemory,
onDeleteMemory = appViewModel::deleteMemory,
```

- [ ] **Step 4: Compile**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lance/litertchat/ui/SettingsScreen.kt app/src/main/java/com/lance/litertchat/App.kt
git commit -m "feat: add memory editor to settings"
```

---

### Task 4: Full Verification

**Files:**
- No production edits expected unless verification exposes a bug.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL and all unit tests pass.

- [ ] **Step 2: Build APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual Android smoke test**

Install:

```powershell
.\gradlew.bat :app:installDebug
```

On the phone:

1. Open Settings.
2. Add `user.name = Lance`.
3. Add `project.current = local LiteRT Android chat app`.
4. Send a chat question about the Android project.
5. Confirm the app responds normally.
6. Delete one memory and confirm it disappears from Settings.

- [ ] **Step 4: Commit verification fixes if any**

If verification required fixes:

```powershell
git add app/src/main/java/com/lance/litertchat app/src/test/java/com/lance/litertchat
git commit -m "fix: stabilize key value memory"
```

If no fixes were needed, do not create an empty commit.

---

## Self-Review

- Spec coverage: The plan implements local key-value memory, manual Settings controls, capped prompt injection, no recent conversation injection, and tests for persistence and prompt behavior.
- Placeholder scan: No implementation step uses placeholder work.
- Type consistency: `MemoryItem`, `MemoryRepository`, `upsertMemory`, `deleteMemory`, and `selectForPrompt` are named consistently across tasks.
