# RAG + Embedding Memory Design

**Date:** 2026-05-20  
**Branch:** llamacpp  

---

## Overview

Upgrade the memory system from a capped keyword-search KV store to an unbounded hybrid system: key-value pairs remain the human-readable source of truth, while a parallel embedding index enables semantic (meaning-based) retrieval via RAG.

When the user asks a question, their message is converted into a vector (list of numbers representing meaning). All stored memory vectors are compared to it using cosine similarity (angle between arrows). The top 6 closest matches are injected into the prompt.

---

## Goals

- Remove the 40-memory cap
- Replace keyword matching in `selectForPrompt` with cosine similarity over embeddings
- Keep key-value storage format unchanged (readable, editable)
- Fall back to keyword matching when no embedding model is loaded
- Support a separate user-selected GGUF embedding model

---

## Storage

Two files in `settings/`, kept in sync:

| File | Purpose |
|---|---|
| `memories.properties` | Key-value source of truth (existing format, cap removed) |
| `embeddings.bin` | Binary sidecar: `encodedKey → FloatArray` (embedding vector for each memory's value) |

**`embeddings.bin` format:** length-prefixed binary records.  
Each record: `[keyLength: Int][key: ByteArray][vectorLength: Int][vector: FloatArray]`

If `embeddings.bin` is missing or a key has no entry, the system falls back to keyword matching for that memory.

---

## C++ Layer (`ai_chat.cpp`)

Add a second parallel model/context pair for embeddings, independent of the chat globals:

```cpp
static llama_model   * g_embed_model;
static llama_context * g_embed_context;
```

The embed context is created with `ctx_params.embeddings = true`.

**Three new JNI functions:**

```cpp
// Load a GGUF in embedding mode
Java_..._loadEmbeddingModel(path: String): Int   // 0 = success

// Tokenize text, run decode, return llama_get_embeddings_seq()
Java_..._computeEmbedding(text: String): FloatArray

// Free embed model and context
Java_..._unloadEmbeddingModel()
```

The chat globals (`g_model`, `g_context`) and embed globals operate independently — both can be loaded at the same time.

---

## Kotlin Layer

### `EmbeddingEngine` interface (`inference/`)

```kotlin
interface EmbeddingEngine {
    suspend fun load(modelFile: File): Result<Unit>
    suspend fun embed(text: String): Result<FloatArray>
    fun unload()
    fun release()
}
```

`LlamaCppEmbeddingEngine` implements this, wrapping the new JNI calls.  
`UnavailableEmbeddingEngine` returns failures for all calls (default when not configured).

### `EmbeddingStore` (`memory/`)

Responsible for persisting and querying embedding vectors.

```kotlin
class EmbeddingStore(rootDir: File) {
    fun storeEmbedding(encodedKey: String, vector: FloatArray)
    fun deleteEmbedding(encodedKey: String)
    fun loadAll(): Map<String, FloatArray>           // encodedKey → vector
    fun findTopK(queryVector: FloatArray, k: Int): List<String>  // returns encodedKeys ranked by cosine similarity
}
```

Cosine similarity is computed in pure Kotlin:
```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
    val normA = sqrt(a.sumOf { (it * it).toDouble() })
    val normB = sqrt(b.sumOf { (it * it).toDouble() })
    return (dot / (normA * normB)).toFloat()
}
```

For 500–1000 memories this scan takes microseconds on-device. No vector DB library needed.

### `MemoryRepository` changes (`memory/`)

- Remove `MAX_MEMORIES = 40` constant and the `.take(MAX_MEMORIES)` call in `saveMemories`
- Add `selectForPromptByEmbedding(queryVector: FloatArray, limit: Int): List<MemoryItem>`:
  1. Call `embeddingStore.findTopK(queryVector, limit)`
  2. Load matching `MemoryItem`s from the KV store by their decoded keys
- Keep `selectForPrompt` (keyword) unchanged as fallback

---

## Data Flow

### Saving a memory

```
upsertMemory(key, value)
  → save KV pair to memories.properties  (always, instant)
  → embed(value) via EmbeddingEngine      (async, requires model loaded)
  → storeEmbedding(encodedKey, vector)    (only if embed succeeded)
```

If the embedding model is not loaded when a memory is saved, the embedding is computed lazily when the model becomes available (re-index on model load).

### Sending a message

```
promptForModel(userPrompt)
  → if embedding model loaded:
      embed(userPrompt)
      → findTopK(queryVector, k=6)
      → fetch matching MemoryItems
      → inject into prompt as memory block
  → else:
      selectForPrompt(userPrompt)   ← existing keyword fallback
```

### Deleting a memory

```
deleteMemory(key)
  → remove from memories.properties
  → deleteEmbedding(encodedKey) from embeddings.bin
```

---

## Re-indexing

When the embedding model is first loaded (or changed), memories that have no stored embedding are re-indexed:

```
onEmbeddingModelLoaded()
  → loadMemories()
  → for each memory without a stored embedding:
      embed(memory.value) → storeEmbedding(encodedKey, vector)
```

This runs in the background on IO dispatcher.

---

## AppViewModel Changes

New fields in `AppState`:
```kotlin
val embeddingModelMetadata: ModelMetadata? = null
val isEmbeddingModelLoaded: Boolean = false
```

New `AppViewModel` dependencies:
```kotlin
private val embeddingEngine: EmbeddingEngine = UnavailableEmbeddingEngine
private val embeddingStore: EmbeddingStore = EmbeddingStore(repository.rootDir)
```

New actions:
- `selectEmbeddingModel(file: File)` — loads the embedding model, triggers re-index
- `removeEmbeddingModel()` — unloads, clears metadata

`promptForModel` updated to use `selectForPromptByEmbedding` when embedding model is loaded.

---

## Settings UI

New section in `SettingsScreen`: **"Memory Search"**

- Button to select/import an embedding GGUF file
- Status indicator: "Not configured" / "Loading..." / "Ready (nomic-embed-text)"
- Explanatory note: "A separate embedding model improves memory search by understanding meaning, not just keywords."

---

## Fallback Behavior

| Condition | Behavior |
|---|---|
| Embedding model not loaded | Keyword matching (existing) |
| Memory has no stored embedding | Excluded from embedding results, keyword fallback includes it |
| Embed call fails | Log error, fall back to keyword for that query |

---

## What Does NOT Change

- `memories.properties` file format — fully backward compatible
- `MemoryItem` data class
- `captureExplicitMemories` pattern extraction
- Pinned keys (`user.name`, `user.prefers`) — still always prepended before the top-k results, same as today
- `upsertMemory` / `deleteMemory` / `loadMemories` signatures
