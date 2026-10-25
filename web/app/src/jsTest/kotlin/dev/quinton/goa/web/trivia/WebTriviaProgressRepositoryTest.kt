package dev.quinton.goa.web.trivia

import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.trivia.TriviaJson
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class WebTriviaProgressRepositoryTest {
    private val store = MemoryKeyValueStore()

    private fun repository() = WebTriviaProgressRepository(store, FakeCatalogRepository())

    private val hero1 = TriviaFacts.of(TestCatalog.heroes[0])
    private val hero2 = TriviaFacts.of(TestCatalog.heroes[1])

    @Test
    fun aFreshBrowserHasNoProgressNoSetupAndNobodyPicked() = runTest {
        val repository = repository()

        assertEquals(emptyMap(), repository.progress.first())
        assertNull(repository.lastConfig.first())
        assertNull(repository.playingAs.first())
    }

    @Test
    fun progressSetupAndPickSurviveAReload() = runTest {
        val config = TriviaConfig(heroIds = listOf("test-hero-2"), answerStyle = AnswerStyle.RECALL, length = 10)
        repository().apply {
            record(mapOf(hero1[0].key to FactProgress(value = hero1[0].value, box = 2, seen = 2, correct = 2)))
            record(mapOf(hero1[1].key to FactProgress(value = hero1[1].value, box = 0, seen = 1, correct = 0)))
            saveConfig(config)
            setPlayingAs(TriviaProgressRepository.GUEST)
        }

        val reloaded = repository()

        assertEquals(setOf(hero1[0].key, hero1[1].key), reloaded.progress.first().keys)
        assertEquals(2, reloaded.progress.first().getValue(hero1[0].key).box)
        assertEquals(config, reloaded.lastConfig.first())
        assertEquals(TriviaProgressRepository.GUEST, reloaded.playingAs.first())
    }

    @Test
    fun theStoredDocumentIsThePhonesFormat() = runTest {
        val updates = mapOf(hero1[0].key to FactProgress(value = hero1[0].value, box = 3, seen = 3, correct = 3))
        repository().record(updates)

        assertEquals(TriviaJson.encodeProgress(updates), store.get("trivia/progress"))
    }

    @Test
    fun resettingAHeroForgetsOnlyItsCards() = runTest {
        val repository = repository()
        repository.record(
            (hero1 + hero2).associate { it.key to FactProgress(value = it.value, box = 1, seen = 1, correct = 1) },
        )

        repository.reset(setOf("test-hero-1"))

        val left = repository.progress.first().keys
        assertTrue(hero1.none { it.key in left })
        assertEquals(hero2.map { it.key }.toSet(), left)

        repository.reset(null)
        assertEquals(emptyMap(), repository.progress.first())
        assertNull(store.get("trivia/progress"))
    }

    @Test
    fun anUnreadableDocumentIsNoProgressYet() = runTest {
        store.put("trivia/progress", "{broken")
        store.put("trivia/config", "[]")

        assertEquals(emptyMap(), repository().progress.first())
        assertNull(repository().lastConfig.first())
    }
}
