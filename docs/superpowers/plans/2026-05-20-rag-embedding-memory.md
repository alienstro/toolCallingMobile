# RAG Embedding Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace keyword-based memory retrieval with semantic embedding search using a user-supplied GGUF embedding model, while keeping key-value pairs as the storage source of truth.

**Architecture:** A new `EmbeddingStore` mirrors the KV store with float vectors per memory. When the embedding model is loaded, queries are embedded and matched by cosine similarity; otherwise the app falls back to existing keyword matching. The embedding model runs through the existing llama.cpp JNI layer as a second independent model context.

**Tech Stack:** Kotlin, llama.cpp C++ JNI, `DataOutputStream`/`DataInputStream` binary storage, Jetpack Compose, coroutines

---

## File Structure

**New files:**
- `app/src/main/java/com/lance/llamacppchat/memory/EmbeddingStore.kt` — binary storage of `encodedKey → FloatArray` + cosine similarity
- `app/src/test/java/com/lance/llamacppchat/memory/EmbeddingStoreTest.kt` — unit tests
- `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/EmbeddingEngine.kt` — arm-layer interface
- `app/src/main/java/com/lance/llamacppchat/inference/EmbeddingEngine.kt` — app-layer interface + `UnavailableEmbeddingEngine`
- `app/src/main/java/com/lance/llamacppchat/inference/LlamaCppEmbeddingEngine.kt` — app wrapper around arm interface

**Modified files:**
- `third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp` — add 2 C++ globals + 3 JNI functions
- `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt` — implement `EmbeddingEngine`, add 3 `external fun`, add 3 interface implementations
- `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/AiChat.kt` — add `getEmbeddingEngine()`
- `app/src/main/java/com/lance/llamacppchat/memory/MemoryRepository.kt` — remove 40-item cap, expose `encodeKey()`, add `memoriesByEncodedKeys()`, make companion object public
- `app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt` — add `embeddingModelPath`
- `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt` — wire embedding engine, update `upsertMemory`/`deleteMemory`/`promptForModel`, add `selectEmbeddingModel`/`removeEmbeddingModel`/`reIndexMemories`
- `app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt` — add embedding model section
- `app/src/main/java/com/lance/llamacppchat/App.kt` — inject `LlamaCppEmbeddingEngine` + wire new Settings callbacks

---

## Task 1: EmbeddingStore — binary storage and cosine similarity

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/memory/EmbeddingStore.kt`
- Create: `app/src/test/java/com/lance/llamacppchat/memory/EmbeddingStoreTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/lance/llamacppchat/memory/EmbeddingStoreTest.kt
package com.lance.llamacppchat.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmbeddingStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun storeAndLoadRoundTrip() {
        val store = EmbeddingStore(temporaryFolder.root)
        val vector = floatArrayOf(1f, 0f, 0f)
        store.storeEmbedding("key1", vector)
        val loaded = EmbeddingStore(temporaryFolder.root).loadAll()
        assertEquals(1, loaded.size)
        assertTrue(loaded["key1"]!!.contentEquals(vector))
    }

    @Test
    fun storeOverwritesExistingKey() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("key1", floatArrayOf(0f, 1f, 0f))
        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertTrue(loaded["key1"]!!.contentEquals(floatArrayOf(0f, 1f, 0f)))
    }

    @Test
    fun deleteRemovesKey() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("key2", floatArrayOf(0f, 1f, 0f))
        store.deleteEmbedding("key1")
        val loaded = EmbeddingStore(temporaryFolder.root).loadAll()
        assertEquals(setOf("key2"), loaded.keys)
    }

    @Test
    fun clearAllRemovesAll() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("key1", floatArrayOf(1f, 0f, 0f))
        store.clearAll()
        assertTrue(EmbeddingStore(temporaryFolder.root).loadAll().isEmpty())
    }

    @Test
    fun findTopKReturnsMostSimilarKeysInOrder() {
        val store = EmbeddingStore(temporaryFolder.root)
        store.storeEmbedding("pizza", floatArrayOf(1f, 0f, 0f))
        store.storeEmbedding("weather", floatArrayOf(0f, 0f, 1f))
        store.storeEmbedding("food", floatArrayOf(0.9f, 0.1f, 0f))
        val results = store.findTopK(floatArrayOf(1f, 0f, 0f), k = 2)
        assertEquals(listOf("pizza", "food"), results)
    }

    @Test
    fun findTopKOnEmptyStoreReturnsEmpty() {
        val store = EmbeddingStore(temporaryFolder.root)
        assertTrue(store.findTopK(floatArrayOf(1f, 0f, 0f), k = 3).isEmpty())
    }

    @Test
    fun loadAllOnMissingFileReturnsEmpty() {
        assertTrue(EmbeddingStore(temporaryFolder.root).loadAll().isEmpty())
    }

    @Test
    fun cosineSimilarityIdenticalVectorsReturnsOne() {
        val v = floatArrayOf(0.5f, 0.5f, 0.5f)
        assertEquals(1.0f, cosineSimilarity(v, v), 0.001f)
    }

    @Test
    fun cosineSimilarityOrthogonalVectorsReturnsZero() {
        assertEquals(0.0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.001f)
    }

    @Test
    fun cosineSimilarityZeroVectorReturnsZero() {
        assertEquals(0.0f, cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.001f)
    }
}
```

- [ ] **Step 2: Run tests — expect failure**

```
./gradlew :app:test --tests "com.lance.llamacppchat.memory.EmbeddingStoreTest"
```
Expected: compilation error — `EmbeddingStore` does not exist yet.

- [ ] **Step 3: Create EmbeddingStore**

```kotlin
// app/src/main/java/com/lance/llamacppchat/memory/EmbeddingStore.kt
package com.lance.llamacppchat.memory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.sqrt

class EmbeddingStore(rootDir: File) {
    private val settingsDir = File(rootDir, "settings")
    private val embeddingsFile = File(settingsDir, "embeddings.bin")

    @Synchronized
    fun storeEmbedding(encodedKey: String, vector: FloatArray) {
        val all = loadAll().toMutableMap()
        all[encodedKey] = vector
        saveAll(all)
    }

    @Synchronized
    fun deleteEmbedding(encodedKey: String) {
        val all = loadAll().toMutableMap()
        if (all.remove(encodedKey) != null) saveAll(all)
    }

    @Synchronized
    fun clearAll() {
        embeddingsFile.delete()
    }

    @Synchronized
    fun loadAll(): Map<String, FloatArray> {
        if (!embeddingsFile.exists()) return emptyMap()
        val result = mutableMapOf<String, FloatArray>()
        try {
            DataInputStream(FileInputStream(embeddingsFile)).use { input ->
                while (input.available() > 0) {
                    val keyLength = input.readInt()
                    val keyBytes = ByteArray(keyLength)
                    input.readFully(keyBytes)
                    val key = String(keyBytes, Charsets.UTF_8)
                    val vectorLength = input.readInt()
                    val vector = FloatArray(vectorLength) { input.readFloat() }
                    result[key] = vector
                }
            }
        } catch (_: Exception) {
            // Corrupt file — return what we managed to read
        }
        return result
    }

    fun findTopK(queryVector: FloatArray, k: Int): List<String> =
        loadAll().entries
            .map { (key, vec) -> key to cosineSimilarity(queryVector, vec) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }

    private fun saveAll(embeddings: Map<String, FloatArray>) {
        settingsDir.mkdirs()
        DataOutputStream(FileOutputStream(embeddingsFile)).use { out ->
            embeddings.forEach { (key, vector) ->
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                out.writeInt(keyBytes.size)
                out.write(keyBytes)
                out.writeInt(vector.size)
                vector.forEach { out.writeFloat(it) }
            }
        }
    }
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    if (denom == 0.0) return 0f
    return (dot / denom).toFloat()
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :app:test --tests "com.lance.llamacppchat.memory.EmbeddingStoreTest"
```
Expected: `BUILD SUCCESSFUL`, all 10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/memory/EmbeddingStore.kt
git add app/src/test/java/com/lance/llamacppchat/memory/EmbeddingStoreTest.kt
git commit -m "feat: add EmbeddingStore with binary persistence and cosine similarity"
```

---

## Task 2: C++ embedding JNI functions + arm-library Kotlin bridge

**Files:**
- Modify: `third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp`
- Create: `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/EmbeddingEngine.kt`
- Modify: `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt`
- Modify: `third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/AiChat.kt`

- [ ] **Step 1: Add embedding globals and 3 JNI functions to ai_chat.cpp**

After the existing globals block (after the line `static common_sampler * g_sampler;`), add:

```cpp
static llama_model   * g_embed_model   = nullptr;
static llama_context * g_embed_context = nullptr;
```

At the end of `ai_chat.cpp`, before the closing of the file, add:

```cpp
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeLoadEmbeddingModel(
        JNIEnv *env, jobject, jstring jpath) {
    const auto *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params model_params = llama_model_default_params();
    auto *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) return 1;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings  = true;
    ctx_params.n_ctx       = 512;
    ctx_params.n_batch     = 512;
    ctx_params.n_threads   = N_THREADS_MAX;
    auto *context = llama_init_from_model(model, ctx_params);
    if (!context) { llama_model_free(model); return 2; }

    g_embed_model   = model;
    g_embed_context = context;
    return 0;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeComputeEmbedding(
        JNIEnv *env, jobject, jstring jtext) {
    if (!g_embed_model || !g_embed_context) return nullptr;

    const auto *text = env->GetStringUTFChars(jtext, nullptr);
    auto tokens = common_tokenize(g_embed_context, text, true, true);
    env->ReleaseStringUTFChars(jtext, text);
    if (tokens.empty()) return nullptr;

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    for (int i = 0; i < (int) tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, i == (int) tokens.size() - 1);
    }
    llama_memory_clear(llama_get_memory(g_embed_context), false);
    if (llama_decode(g_embed_context, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_batch_free(batch);

    const int n_embd = llama_model_n_embd(g_embed_model);
    float *embd = llama_get_embeddings_seq(g_embed_context, 0);
    if (!embd) embd = llama_get_embeddings(g_embed_context);
    if (!embd) return nullptr;

    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embd);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeUnloadEmbeddingModel(
        JNIEnv *, jobject) {
    if (g_embed_context) { llama_free(g_embed_context);      g_embed_context = nullptr; }
    if (g_embed_model)   { llama_model_free(g_embed_model);  g_embed_model   = nullptr; }
}
```

- [ ] **Step 2: Create the arm-layer EmbeddingEngine interface**

```kotlin
// third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/EmbeddingEngine.kt
package com.arm.aichat

interface EmbeddingEngine {
    suspend fun loadEmbeddingModel(pathToModel: String)
    suspend fun embed(text: String): FloatArray
    fun unloadEmbeddingModel()
}
```

- [ ] **Step 3: Implement EmbeddingEngine in InferenceEngineImpl**

Change the class declaration from:
```kotlin
internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {
```
to:
```kotlin
internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine, EmbeddingEngine {
```

Add these three `@FastNative external fun` declarations alongside the existing ones:
```kotlin
@FastNative
private external fun nativeLoadEmbeddingModel(path: String): Int

@FastNative
private external fun nativeComputeEmbedding(text: String): FloatArray?

@FastNative
private external fun nativeUnloadEmbeddingModel()
```

Add these three method implementations at the end of the class body (before the companion object):
```kotlin
override suspend fun loadEmbeddingModel(pathToModel: String) =
    withContext(llamaDispatcher) {
        val result = nativeLoadEmbeddingModel(pathToModel)
        if (result != 0) throw IllegalStateException("Failed to load embedding model (code $result)")
    }

override suspend fun embed(text: String): FloatArray =
    withContext(llamaDispatcher) {
        requireNotNull(nativeComputeEmbedding(text)) { "Embedding computation returned null" }
    }

override fun unloadEmbeddingModel() {
    runBlocking(llamaDispatcher) {
        nativeUnloadEmbeddingModel()
    }
}
```

- [ ] **Step 4: Expose EmbeddingEngine from AiChat**

Replace the entire content of `AiChat.kt` with:
```kotlin
package com.arm.aichat

import android.content.Context
import com.arm.aichat.internal.InferenceEngineImpl

object AiChat {
    fun getInferenceEngine(context: Context) = InferenceEngineImpl.getInstance(context)
    fun getEmbeddingEngine(context: Context): EmbeddingEngine = InferenceEngineImpl.getInstance(context)
}
```

- [ ] **Step 5: Build the library to verify C++ and Kotlin compile**

```
./gradlew :third_party:llama.cpp:examples:llama.android:lib:assembleDebug
```
Expected: `BUILD SUCCESSFUL` — no C++ or Kotlin compile errors.

- [ ] **Step 6: Commit**

```bash
git add third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp
git add third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/EmbeddingEngine.kt
git add third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt
git add third_party/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/AiChat.kt
git commit -m "feat: add embedding JNI functions and EmbeddingEngine interface to arm library"
```

---

## Task 3: App-layer EmbeddingEngine interface and LlamaCppEmbeddingEngine

**Files:**
- Create: `app/src/main/java/com/lance/llamacppchat/inference/EmbeddingEngine.kt`
- Create: `app/src/main/java/com/lance/llamacppchat/inference/LlamaCppEmbeddingEngine.kt`

- [ ] **Step 1: Create app-layer EmbeddingEngine interface and UnavailableEmbeddingEngine**

```kotlin
// app/src/main/java/com/lance/llamacppchat/inference/EmbeddingEngine.kt
package com.lance.llamacppchat.inference

import java.io.File

interface EmbeddingEngine {
    suspend fun load(modelFile: File): Result<Unit>
    suspend fun embed(text: String): Result<FloatArray>
    fun unload()
}

object UnavailableEmbeddingEngine : EmbeddingEngine {
    override suspend fun load(modelFile: File): Result<Unit> =
        Result.failure(IllegalStateException("No embedding engine configured."))

    override suspend fun embed(text: String): Result<FloatArray> =
        Result.failure(IllegalStateException("No embedding engine configured."))

    override fun unload() = Unit
}
```

- [ ] **Step 2: Create LlamaCppEmbeddingEngine**

```kotlin
// app/src/main/java/com/lance/llamacppchat/inference/LlamaCppEmbeddingEngine.kt
package com.lance.llamacppchat.inference

import android.content.Context
import com.arm.aichat.AiChat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaCppEmbeddingEngine(context: Context) : EmbeddingEngine {
    private val engine = AiChat.getEmbeddingEngine(context.applicationContext)

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(modelFile.exists() && modelFile.isFile) { "Embedding model file does not exist." }
            engine.loadEmbeddingModel(modelFile.absolutePath)
        }
    }

    override suspend fun embed(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        runCatching {
            require(text.isNotBlank()) { "Cannot embed blank text." }
            engine.embed(text)
        }
    }

    override fun unload() = engine.unloadEmbeddingModel()
}
```

- [ ] **Step 3: Build app to verify compile**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/inference/EmbeddingEngine.kt
git add app/src/main/java/com/lance/llamacppchat/inference/LlamaCppEmbeddingEngine.kt
git commit -m "feat: add app-layer EmbeddingEngine interface and LlamaCppEmbeddingEngine"
```

---

## Task 4: MemoryRepository — remove cap, expose encode key, add embedding retrieval

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/memory/MemoryRepository.kt`
- Modify: `app/src/test/java/com/lance/llamacppchat/memory/MemoryRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these tests to `MemoryRepositoryTest.kt`:

```kotlin
@Test
fun memoriesAreNotCapAt40() {
    val repository = MemoryRepository(temporaryFolder.root)
    repeat(50) { i ->
        repository.upsertMemory("key.$i", "value $i", now = i.toLong())
    }
    assertEquals(50, repository.loadMemories().size)
}

@Test
fun encodeKeyReturnsNullForBlankKey() {
    val repository = MemoryRepository(temporaryFolder.root)
    assertNull(repository.encodeKey("   "))
}

@Test
fun encodeKeyReturnsConsistentResultForSameInput() {
    val repository = MemoryRepository(temporaryFolder.root)
    val key1 = repository.encodeKey("user.name")
    val key2 = repository.encodeKey("user.name")
    assertEquals(key1, key2)
}

@Test
fun memoriesByEncodedKeysReturnsMatchingItems() {
    val repository = MemoryRepository(temporaryFolder.root)
    repository.upsertMemory("user.name", "Lance", now = 1000L)
    repository.upsertMemory("user.likes", "coffee", now = 2000L)
    val encodedKey = repository.encodeKey("user.name")!!
    val result = repository.memoriesByEncodedKeys(listOf(encodedKey))
    assertEquals(1, result.size)
    assertEquals("user.name", result[0].key)
}

@Test
fun memoriesByEncodedKeysReturnsEmptyForUnknownKeys() {
    val repository = MemoryRepository(temporaryFolder.root)
    repository.upsertMemory("user.name", "Lance", now = 1000L)
    val result = repository.memoriesByEncodedKeys(listOf("nonexistent-encoded-key"))
    assertTrue(result.isEmpty())
}
```

- [ ] **Step 2: Run tests — expect failure**

```
./gradlew :app:test --tests "com.lance.llamacppchat.memory.MemoryRepositoryTest"
```
Expected: failures on `memoriesAreNotCapAt40`, `encodeKey*`, `memoriesByEncodedKeys*`.

- [ ] **Step 3: Update MemoryRepository**

Make the following changes to `MemoryRepository.kt`:

**a) Change `private companion object` to `companion object`** (line 166) so `PINNED_KEYS` and `PROMPT_MEMORY_LIMIT` are accessible from `AppViewModel`.

**b) Remove the 40-item cap.** In `upsertMemory`, remove:
```kotlin
.take(MAX_MEMORIES)
```
Also remove the now-unused constant from the companion object:
```kotlin
// DELETE this line:
const val MAX_MEMORIES = 40
```

**c) Add `encodeKey` and `memoriesByEncodedKeys` public methods** before the `private fun saveMemories` method:

```kotlin
fun encodeKey(rawKey: String): String? = cleanKey(rawKey)?.let { encode(it) }

fun memoriesByEncodedKeys(encodedKeys: List<String>): List<MemoryItem> {
    if (encodedKeys.isEmpty()) return emptyList()
    val allByEncodedKey = loadMemories().associateBy { encode(it.key) }
    return encodedKeys.mapNotNull { allByEncodedKey[it] }
}
```

The full updated companion object (replace the existing one):
```kotlin
companion object {
    const val KEY_MEMORY_KEYS = "memoryKeys"
    const val MAX_KEY_LENGTH = 48
    const val MAX_VALUE_LENGTH = 160
    const val PROMPT_MEMORY_LIMIT = 6
    val PINNED_KEYS = listOf("user.name", "user.prefers")
    val NAME_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+name(?:\\s+is)?\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
    val CALL_ME_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?call\\s+me\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
    val FAVORITE_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+favou?rite\\s+([a-z0-9][a-z0-9 ]{0,31})\\s+is\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
    val FAVORITE_PLURAL_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+favou?rite\\s+([a-z0-9][a-z0-9 ]{0,31}s)\\s+are\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
    val LIKES_TO_EAT_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?i\\s+like\\s+to\\s+eat\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
    val SIMPLE_STATEMENT_PATTERNS = listOf(
        "user.likes" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+like\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.loves" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+love\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.prefers" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+prefer\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.uses" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+use\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.wants" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+want\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.is" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+am\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.from" to Regex("^(?:remember(?:\\s+that)?\\s+)?(?:i'm|i\\s+am)\\s+from\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE),
        "user.works.as" to Regex("^(?:remember(?:\\s+that)?\\s+)?i\\s+work\\s+as\\s+([^.!?\\n]+)[.!?]?$", RegexOption.IGNORE_CASE)
    )
    val MY_FACT_PATTERN = Regex(
        pattern = "^(?:remember(?:\\s+that)?\\s+)?my\\s+([a-z0-9][a-z0-9 ]{0,31})\\s+(?:is|are)\\s+([^.!?\\n]+)[.!?]?$",
        option = RegexOption.IGNORE_CASE
    )
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :app:test --tests "com.lance.llamacppchat.memory.MemoryRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/memory/MemoryRepository.kt
git add app/src/test/java/com/lance/llamacppchat/memory/MemoryRepositoryTest.kt
git commit -m "feat: remove 40-memory cap, expose encodeKey and memoriesByEncodedKeys"
```

---

## Task 5: AppSettings + AppViewModel — wire embedding engine

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/App.kt`

- [ ] **Step 1: Add embeddingModelPath to AppSettings**

In `AppSettingsRepository.kt`, update `AppSettings`:
```kotlin
data class AppSettings(
    val streamResponsesEnabled: Boolean = true,
    val gpuBackendEnabled: Boolean = false,
    val npuBackendEnabled: Boolean = false,
    val gemmaMtpEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val embeddingModelPath: String? = null
)
```

In `load()`, add after the existing properties:
```kotlin
embeddingModelPath = properties.getProperty(KEY_EMBEDDING_MODEL_PATH)
```

In `save()`, add inside the properties block:
```kotlin
if (settings.embeddingModelPath != null) {
    properties.setProperty(KEY_EMBEDDING_MODEL_PATH, settings.embeddingModelPath)
} else {
    properties.remove(KEY_EMBEDDING_MODEL_PATH)
}
```

Add new public method:
```kotlin
fun setEmbeddingModelPath(path: String?) {
    save(load().copy(embeddingModelPath = path))
}
```

Add to companion object:
```kotlin
const val KEY_EMBEDDING_MODEL_PATH = "embeddingModelPath"
```

- [ ] **Step 2: Run existing settings tests**

```
./gradlew :app:test --tests "com.lance.llamacppchat.settings.AppSettingsRepositoryTest"
```
Expected: all pass (backwards-compatible change).

- [ ] **Step 3: Update AppState with embedding fields**

In `AppViewModel.kt`, add to `AppState`:
```kotlin
val embeddingModelPath: String? = null,
val isEmbeddingModelLoaded: Boolean = false,
val isReIndexing: Boolean = false,
```

- [ ] **Step 4: Update AppViewModel constructor and init**

Add two new constructor parameters after `memoryRepository`:
```kotlin
private val embeddingEngine: EmbeddingEngine = UnavailableEmbeddingEngine,
private val embeddingStore: EmbeddingStore = EmbeddingStore(repository.rootDir),
```

Add the import:
```kotlin
import com.lance.llamacppchat.inference.EmbeddingEngine
import com.lance.llamacppchat.inference.UnavailableEmbeddingEngine
import com.lance.llamacppchat.memory.EmbeddingStore
```

Update the `mutableState` initial value to include the new fields:
```kotlin
embeddingModelPath = initialSettings.embeddingModelPath,
isEmbeddingModelLoaded = false,
isReIndexing = false,
```

Add an `init` block after the `mutableState` declaration:
```kotlin
init {
    val savedPath = initialSettings.embeddingModelPath
    if (savedPath != null) {
        viewModelScope.launch {
            val file = File(savedPath)
            if (file.exists()) {
                withContext(ioDispatcher) { embeddingEngine.load(file) }
                    .onSuccess {
                        mutableState.update { it.copy(isEmbeddingModelLoaded = true) }
                        reIndexMemories()
                    }
            }
        }
    }
}
```

- [ ] **Step 5: Add selectEmbeddingModel, removeEmbeddingModel, reIndexMemories**

Add these methods to `AppViewModel` after `deleteMemory`:

```kotlin
fun selectEmbeddingModel(file: File) {
    viewModelScope.launch {
        embeddingStore.clearAll()
        withContext(ioDispatcher) { embeddingEngine.load(file) }
            .onSuccess {
                appSettingsRepository.setEmbeddingModelPath(file.absolutePath)
                mutableState.update {
                    it.copy(
                        embeddingModelPath = file.absolutePath,
                        isEmbeddingModelLoaded = true,
                        errorText = null
                    )
                }
                reIndexMemories()
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(errorText = error.message ?: "Failed to load embedding model")
                }
            }
    }
}

fun removeEmbeddingModel() {
    embeddingEngine.unload()
    embeddingStore.clearAll()
    appSettingsRepository.setEmbeddingModelPath(null)
    mutableState.update {
        it.copy(embeddingModelPath = null, isEmbeddingModelLoaded = false)
    }
}

private fun reIndexMemories() {
    viewModelScope.launch(ioDispatcher) {
        mutableState.update { it.copy(isReIndexing = true) }
        val storedKeys = embeddingStore.loadAll().keys
        memoryRepository.loadMemories()
            .filter { memoryRepository.encodeKey(it.key) !in storedKeys }
            .forEach { memory ->
                val encodedKey = memoryRepository.encodeKey(memory.key) ?: return@forEach
                embeddingEngine.embed(memory.value).onSuccess { vector ->
                    embeddingStore.storeEmbedding(encodedKey, vector)
                }
            }
        mutableState.update { it.copy(isReIndexing = false) }
    }
}
```

- [ ] **Step 6: Update upsertMemory and deleteMemory to sync the embedding store**

Replace the existing `upsertMemory`:
```kotlin
fun upsertMemory(key: String, value: String) {
    val encodedKey = memoryRepository.encodeKey(key)
    memoryRepository.upsertMemory(key, value)
    refreshMemoryState()
    if (encodedKey != null && mutableState.value.isEmbeddingModelLoaded) {
        viewModelScope.launch(ioDispatcher) {
            embeddingEngine.embed(value).onSuccess { vector ->
                embeddingStore.storeEmbedding(encodedKey, vector)
            }
        }
    }
}
```

Replace the existing `deleteMemory`:
```kotlin
fun deleteMemory(key: String) {
    val encodedKey = memoryRepository.encodeKey(key)
    memoryRepository.deleteMemory(key)
    refreshMemoryState()
    if (encodedKey != null) embeddingStore.deleteEmbedding(encodedKey)
}
```

- [ ] **Step 7: Update promptForModel to use semantic search**

Change `private fun promptForModel` to `private suspend fun promptForModel` and replace its body:

```kotlin
private suspend fun promptForModel(userPrompt: String, hasImage: Boolean = false): String {
    val formatter = promptFormatterRepository.loadState().activeFormatter
    val formatterBody = formatter?.body.orEmpty().trim()
    val prompt = if (userPrompt.isBlank() && hasImage) "Describe this image." else userPrompt

    val selectedMemories: List<MemoryItem> = withContext(ioDispatcher) {
        if (mutableState.value.isEmbeddingModelLoaded) {
            embeddingEngine.embed(prompt).getOrNull()?.let { queryVector ->
                val topKeys = embeddingStore.findTopK(queryVector, MemoryRepository.PROMPT_MEMORY_LIMIT)
                val pinnedMemories = memoryRepository.loadMemories()
                    .filter { it.key in MemoryRepository.PINNED_KEYS }
                    .sortedBy { MemoryRepository.PINNED_KEYS.indexOf(it.key) }
                val semanticMemories = memoryRepository.memoriesByEncodedKeys(topKeys)
                    .filterNot { it.key in MemoryRepository.PINNED_KEYS }
                (pinnedMemories + semanticMemories).take(MemoryRepository.PROMPT_MEMORY_LIMIT)
            } ?: memoryRepository.selectForPrompt(prompt)
        } else {
            memoryRepository.selectForPrompt(prompt)
        }
    }

    val memoryBlock = selectedMemories
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "Memory:\n") { "- ${it.key}: ${it.value}" }

    return listOf(formatterBody, memoryBlock, "User message:\n$prompt")
        .filterNot { it.isNullOrBlank() }
        .joinToString("\n\n")
}
```

In `sendMessage`, the call site `val modelPrompt = promptForModel(...)` is already inside a `viewModelScope.launch` coroutine so the `suspend` change compiles as-is.

- [ ] **Step 8: Inject LlamaCppEmbeddingEngine in App.kt**

In `rememberAppViewModel()`, update the factory:
```kotlin
return AppViewModel(
    repository = ModelRepository(context.filesDir),
    engine = LlamaCppChatEngine(context),
    embeddingEngine = LlamaCppEmbeddingEngine(context)
) as T
```

Add the import:
```kotlin
import com.lance.llamacppchat.inference.LlamaCppEmbeddingEngine
```

- [ ] **Step 9: Run all tests**

```
./gradlew :app:test
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/settings/AppSettingsRepository.kt
git add app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt
git add app/src/main/java/com/lance/llamacppchat/App.kt
git commit -m "feat: wire EmbeddingEngine into AppViewModel with semantic memory retrieval"
```

---

## Task 6: Settings UI — embedding model selection

**Files:**
- Modify: `app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/App.kt`
- Modify: `app/src/main/java/com/lance/llamacppchat/model/ModelRepository.kt`

- [ ] **Step 1: Add embedding model callbacks to SettingsScreen signature**

Add two new parameters to the `SettingsScreen` composable:
```kotlin
onSelectEmbeddingModel: (Uri) -> Unit,
onRemoveEmbeddingModel: () -> Unit,
```

- [ ] **Step 2: Add embedding model section to SettingsScreen body**

Add a file picker launcher at the top of the `SettingsScreen` composable body, alongside the existing `var editingId` declarations:
```kotlin
val embeddingModelPickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
) { uri: Uri? ->
    if (uri != null) onSelectEmbeddingModel(uri)
}
```

Add the import:
```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
```

Add the embedding model section in the `LazyColumn` — place it after the existing memories section and before (or after) the stream responses toggle. The exact location should match the visual flow of the screen, but the content is:

```kotlin
// Embedding model section header
item {
    Text(
        text = "Memory Search",
        color = AppText,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}
item {
    val modelName = state.embeddingModelPath
        ?.let { java.io.File(it).name }
        ?: "Not configured"
    val statusColor = if (state.isEmbeddingModelLoaded) AppAccent else AppMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Embedding model: $modelName",
            color = statusColor,
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isReIndexing) {
            Text(
                text = "Indexing memories...",
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { embeddingModelPickerLauncher.launch("*/*") },
                label = { Text(if (state.embeddingModelPath == null) "Select model" else "Change model") }
            )
            if (state.embeddingModelPath != null) {
                AssistChip(
                    onClick = onRemoveEmbeddingModel,
                    label = { Text("Remove") }
                )
            }
        }
        Text(
            text = "Recommended: nomic-embed-text-v1.5 GGUF (~90 MB)",
            color = AppFaint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```

Add import:
```kotlin
import androidx.compose.material3.AssistChip
```

- [ ] **Step 3: Add importEmbeddingModelFromUri to AppViewModel**

Add this method to `AppViewModel.kt`:

```kotlin
fun importEmbeddingModelFromUri(context: Context, uri: Uri) {
    viewModelScope.launch {
        runCatching {
            withContext(ioDispatcher) {
                val destination = File(
                    repository.embeddingModelDirectory(),
                    "embedding-${System.currentTimeMillis()}.gguf"
                )
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected file." }
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                require(destination.length() > 0L) { "Selected file was empty." }
                destination
            }
        }.onSuccess { file ->
            selectEmbeddingModel(file)
        }.onFailure { error ->
            mutableState.update { it.copy(errorText = error.message ?: "Import failed") }
        }
    }
}
```

Add `embeddingModelDirectory()` to `ModelRepository.kt`:
```kotlin
fun embeddingModelDirectory(): File = File(rootDir, "embedding-models").also { it.mkdirs() }
```

- [ ] **Step 4: Wire callbacks in App.kt**

In the `composable(AppRoute.Settings.route)` block in `App.kt`, add the two new callbacks to the `SettingsScreen` call:

```kotlin
onSelectEmbeddingModel = { uri ->
    appViewModel.importEmbeddingModelFromUri(context, uri)
},
onRemoveEmbeddingModel = appViewModel::removeEmbeddingModel,
```

- [ ] **Step 5: Build the full app**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run all tests**

```
./gradlew :app:test
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lance/llamacppchat/ui/SettingsScreen.kt
git add app/src/main/java/com/lance/llamacppchat/ui/AppViewModel.kt
git add app/src/main/java/com/lance/llamacppchat/App.kt
git add app/src/main/java/com/lance/llamacppchat/model/ModelRepository.kt
git commit -m "feat: add embedding model selection UI in Settings"
```
