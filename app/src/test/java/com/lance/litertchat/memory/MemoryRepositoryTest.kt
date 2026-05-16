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

    @Test
    fun captureExplicitMemoriesStoresFavoriteFactsFromUserText() {
        val repository = MemoryRepository(temporaryFolder.root)

        val captured = repository.captureExplicitMemories("my favorite color is blue", now = 1000L)

        assertEquals(listOf(MemoryItem("user.favorite.color", "blue", 1000L)), captured)
        assertEquals(listOf(MemoryItem("user.favorite.color", "blue", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesSupportsBritishFavouriteSpelling() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("My favourite color is blue.", now = 1000L)

        assertEquals(listOf(MemoryItem("user.favorite.color", "blue", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesSupportsRememberPrefix() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("remember my favorite color is blue", now = 1000L)

        assertEquals(listOf(MemoryItem("user.favorite.color", "blue", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresPluralFavoriteFoods() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("my favorite foods are donuts and hotdogs", now = 1000L)

        assertEquals(listOf(MemoryItem("user.favorite.foods", "donuts and hotdogs", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresRememberFavoriteFood() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("remember my favorite food is donuts and hotdogs", now = 1000L)

        assertEquals(listOf(MemoryItem("user.favorite.food", "donuts and hotdogs", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresFoodsUserLikesToEat() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I like to eat donuts and hotdogs", now = 1000L)

        assertEquals(listOf(MemoryItem("user.likes.to.eat", "donuts and hotdogs", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresGeneralLikes() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I like chess", now = 1000L)

        assertEquals(listOf(MemoryItem("user.likes", "chess", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresGeneralLoves() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I love Kotlin and Android", now = 1000L)

        assertEquals(listOf(MemoryItem("user.loves", "Kotlin and Android", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresGeneralPreferences() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I prefer short answers", now = 1000L)

        assertEquals(listOf(MemoryItem("user.prefers", "short answers", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesIgnoresQuestionsAboutLikes() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("what do I like to eat?", now = 1000L)

        assertTrue(repository.loadMemories().isEmpty())
    }

    @Test
    fun captureExplicitMemoriesStoresNameFactsFromUserText() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("my name is robinx", now = 1000L)

        assertEquals(listOf(MemoryItem("user.name", "robinx", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresRememberMyNameWithoutIs() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("remember my name robinx", now = 1000L)

        assertEquals(listOf(MemoryItem("user.name", "robinx", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresCallMeName() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("call me robinx", now = 1000L)

        assertEquals(listOf(MemoryItem("user.name", "robinx", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresUserIsFacts() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I am a mobile developer", now = 1000L)

        assertEquals(listOf(MemoryItem("user.is", "a mobile developer", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresUserLocation() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I'm from Manila", now = 1000L)

        assertEquals(listOf(MemoryItem("user.from", "Manila", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresWorkRole() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I work as an Android developer", now = 1000L)

        assertEquals(listOf(MemoryItem("user.works.as", "an Android developer", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresToolsUserUses() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I use Kotlin", now = 1000L)

        assertEquals(listOf(MemoryItem("user.uses", "Kotlin", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresUserGoals() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("I want short responses", now = 1000L)

        assertEquals(listOf(MemoryItem("user.wants", "short responses", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresGenericMyFacts() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("my phone is Pixel 8", now = 1000L)

        assertEquals(listOf(MemoryItem("user.phone", "Pixel 8", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesStoresGenericPluralMyFacts() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("my hobbies are chess and coding", now = 1000L)

        assertEquals(listOf(MemoryItem("user.hobbies", "chess and coding", 1000L)), repository.loadMemories())
    }

    @Test
    fun captureExplicitMemoriesIgnoresQuestionsAboutName() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("what is my name?", now = 1000L)

        assertTrue(repository.loadMemories().isEmpty())
    }

    @Test
    fun captureExplicitMemoriesIgnoresQuestionsAboutFavorites() {
        val repository = MemoryRepository(temporaryFolder.root)

        repository.captureExplicitMemories("what is my favorite color?", now = 1000L)

        assertTrue(repository.loadMemories().isEmpty())
    }
}
