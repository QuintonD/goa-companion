package dev.quinton.goa.core.data.trivia

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.datastore.trivia.TriviaProgressStore
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.testing.FakeCatalogRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreTriviaProgressRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun card(id: String, heroId: String) = Card(id = id, heroId = heroId, name = id, color = CardColor.RED)

    private fun hero(id: String, vararg cards: String) = Hero(
        id = id,
        name = id,
        title = "",
        product = "p",
        printing = 2,
        cards = cards.map { card(it, id) },
        handicap = card("$id-handicap", id),
        verified = true,
    )

    /** "fox" is a prefix of "fox-tail"; "owl"'s cards don't start with "owl" at all. */
    private val catalog = Catalog(
        dataVersion = 1,
        printing = 2,
        products = emptyList(),
        heroes = listOf(
            hero("fox", "fox-gold", "fox-red-1"),
            hero("fox-tail", "fox-tail-gold"),
            hero("owl", "night-gold"),
        ),
    )

    private lateinit var store: TriviaProgressStore

    /** Counts the work handed to it: shows the JSON work left the caller's dispatcher. */
    private class CountingDispatcher(private val inner: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatched = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched++
            inner.dispatch(context, block)
        }
    }

    private lateinit var compute: CountingDispatcher

    private fun repository(scope: TestScope): DataStoreTriviaProgressRepository {
        store = TriviaProgressStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                folder.root.resolve("trivia_progress.preferences_pb")
            },
        )
        compute = CountingDispatcher(StandardTestDispatcher(scope.testScheduler))
        return DataStoreTriviaProgressRepository(store, FakeCatalogRepository(catalog), compute)
    }

    @Test
    fun `decoding, merging and encoding run on the compute dispatcher, not the caller's`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository(this)
            repo.record(mapOf(key("fox-gold") to learned))
            val afterRecord = compute.dispatched
            assertThat(afterRecord).isGreaterThan(0)
            assertThat(repo.progress.first()).containsExactly(key("fox-gold"), learned)
            assertThat(compute.dispatched).isGreaterThan(afterRecord)
            repo.reset(setOf("fox"))
            assertThat(repo.progress.first()).isEmpty()
        }

    private fun key(card: String, stat: TriviaStat = TriviaStat.INITIATIVE) = TriviaFact.keyOf(card, stat)

    private val learned = FactProgress(value = 7, box = 3, seen = 4, correct = 3, lastSeenAt = 1_700_000_000_000)

    @Test
    fun `progress round-trips and starts empty`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        assertThat(repo.progress.first()).isEmpty()
        val updates = mapOf(
            key("fox-gold") to learned,
            key("fox-gold", TriviaStat.ATTACK) to FactProgress(value = 4, plus = true, box = 1, seen = 1, correct = 1),
            key("fox-red-1", TriviaStat.RADIUS) to FactProgress(value = 2),
        )
        repo.record(updates)
        assertThat(repo.progress.first()).isEqualTo(updates)
        // Survives a new repository on the same file.
        assertThat(DataStoreTriviaProgressRepository(store, FakeCatalogRepository(catalog)).progress.first())
            .isEqualTo(updates)
    }

    @Test
    fun `record merges into what is stored`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.record(mapOf(key("fox-gold") to learned, key("fox-red-1") to learned))
        val relearned = learned.copy(box = 0, seen = 5)
        repo.record(mapOf(key("fox-red-1") to relearned, key("night-gold") to learned))
        repo.record(emptyMap())
        assertThat(repo.progress.first()).containsExactly(
            key("fox-gold"),
            learned,
            key("fox-red-1"),
            relearned,
            key("night-gold"),
            learned,
        )
    }

    @Test
    fun `reset by hero drops that hero's cards, found through the catalog`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.record(
            mapOf(
                key("fox-gold") to learned,
                key("fox-red-1", TriviaStat.DEFENSE) to learned,
                key("fox-tail-gold") to learned,
                key("night-gold") to learned,
                key("gone-card") to learned, // a card no longer in the catalog
            ),
        )
        repo.reset(setOf("fox"))
        // "fox-tail" survives although its card ids start with "fox-".
        assertThat(
            repo.progress.first().keys,
        ).containsExactly(key("fox-tail-gold"), key("night-gold"), key("gone-card"))
        repo.reset(setOf("owl"))
        assertThat(repo.progress.first().keys).containsExactly(key("fox-tail-gold"), key("gone-card"))
        repo.reset(setOf("no-such-hero"))
        repo.reset(emptySet())
        assertThat(repo.progress.first().keys).containsExactly(key("fox-tail-gold"), key("gone-card"))
        repo.reset(setOf("fox-tail", "fox"))
        assertThat(repo.progress.first().keys).containsExactly(key("gone-card"))
    }

    @Test
    fun `reset of everything clears all progress but keeps the last setup`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.record(mapOf(key("fox-gold") to learned, key("gone-card") to learned))
        repo.saveConfig(TriviaConfig(length = 20))
        repo.reset()
        assertThat(repo.progress.first()).isEmpty()
        assertThat(store.progressJson.first()).isNull()
        assertThat(repo.lastConfig.first()).isEqualTo(TriviaConfig(length = 20))
    }

    @Test
    fun `unreadable progress reads as none and is replaced by the next answer`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        listOf("{not json", "[]", "42", "null", "").forEach { junk ->
            store.updateProgress { junk }
            assertThat(repo.progress.first()).isEmpty()
        }
        repo.record(mapOf(key("fox-gold") to learned))
        assertThat(repo.progress.first()).containsExactly(key("fox-gold"), learned)
    }

    @Test
    fun `one unreadable entry loses only that entry, and odd numbers are brought into range`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository(this)
            store.updateProgress {
                """
                {
                  "fox-gold/initiative": {"value": 7, "box": 3, "seen": 4, "correct": 3, "lastSeenAt": 1700000000000},
                  "fox-red-1/initiative": {"box": 2},
                  "fox-red-1/attack": "three",
                  "night-gold/defense": {"value": 2, "box": 99, "seen": -1, "correct": 5, "future": true}
                }
                """.trimIndent()
            }
            assertThat(repo.progress.first()).containsExactly(
                key("fox-gold"),
                learned,
                key("night-gold", TriviaStat.DEFENSE),
                FactProgress(value = 2, box = TriviaMastery.MAX_BOX, seen = 0, correct = 0),
            )
        }

    @Test
    fun `the last setup round-trips`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        assertThat(repo.lastConfig.first()).isNull()
        val config = TriviaConfig(
            heroIds = listOf("fox", "owl"),
            stats = setOf(TriviaStat.INITIATIVE, TriviaStat.RADIUS),
            groups = setOf(CardGroup.BASIC, CardGroup.ULTIMATE),
            answerStyle = AnswerStyle.RECALL,
            length = TriviaConfig.ENDLESS,
            duels = false,
        )
        repo.saveConfig(config)
        assertThat(repo.lastConfig.first()).isEqualTo(config)
        repo.saveConfig(TriviaConfig())
        assertThat(repo.lastConfig.first()).isEqualTo(TriviaConfig())
        // Written in full: a later change of a default can't change a saved setup.
        assertThat(store.configJson.first()).contains("\"answerStyle\"")
    }

    @Test
    fun `a stored setup with unknown values falls back where it must`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        store.writeConfig(
            """
            {"heroIds": ["fox"], "stats": ["initiative", "speed", 3], "groups": ["tier9"],
             "answerStyle": "typing", "length": 20, "duels": false, "theme": "dark"}
            """.trimIndent(),
        )
        assertThat(repo.lastConfig.first()).isEqualTo(
            TriviaConfig(
                heroIds = listOf("fox"),
                stats = setOf(TriviaStat.INITIATIVE),
                groups = CardGroup.entries.toSet(), // none known: the default, not an empty quiz
                answerStyle = AnswerStyle.CHOICES,
                length = 20,
                duels = false,
            ),
        )
        store.writeConfig("""{"length": -5}""")
        assertThat(repo.lastConfig.first()).isEqualTo(TriviaConfig())
        listOf("{oops", "[]", """{"heroIds": "fox"}""", """{"length": "long"}""").forEach { junk ->
            store.writeConfig(junk)
            assertThat(repo.lastConfig.first()).isNull()
        }
    }
}
