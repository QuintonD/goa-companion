package dev.quinton.goa.core.presentation

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.presentation.catalog.CatalogControls
import dev.quinton.goa.core.presentation.catalog.CatalogFilter
import dev.quinton.goa.core.presentation.catalog.CatalogSearchIndex
import dev.quinton.goa.core.presentation.catalog.CatalogUiState
import dev.quinton.goa.core.presentation.catalog.activeFilters
import dev.quinton.goa.core.presentation.catalog.cleared
import dev.quinton.goa.core.presentation.catalog.kindLabel
import dev.quinton.goa.core.presentation.rules.LadderSelection
import dev.quinton.goa.core.presentation.rules.RulesText
import dev.quinton.goa.core.presentation.trivia.Mark
import dev.quinton.goa.core.presentation.trivia.QuizQuestionUi
import dev.quinton.goa.core.presentation.trivia.TriviaCatalogFacts
import dev.quinton.goa.core.presentation.trivia.TriviaHomeAction
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.core.presentation.trivia.TriviaRound
import dev.quinton.goa.core.presentation.trivia.markFor
import kotlin.random.Random
import org.junit.Test

/**
 * The state builders the phone's ViewModels and the hub's pages share (ADR-030 §1). The ViewModel
 * tests cover them through the phone; these pin the parts the web uses directly.
 */
class SharedStateTest {
    private val catalog = TestCatalog.catalog
    private val index = CatalogSearchIndex(catalog.heroes)

    @Test
    fun `owned products hide heroes and the empty state says so`() {
        val owned = setOf("test-core")
        val query = TestCatalog.hero(11).name // a Test Pack hero
        val state = CatalogUiState.build(catalog, index, owned, query, CatalogControls()) as CatalogUiState.Ready
        assertThat(state.heroes).isEmpty()
        assertThat(state.hiddenByCollection).isGreaterThan(0)
        val empty = state.noResults(query)
        assertThat(empty.showAllProducts).isTrue()
        assertThat(empty.action).isEqualTo("Show all products")
        val all = CatalogUiState.build(
            catalog,
            index,
            owned,
            query,
            CatalogControls(filter = CatalogFilter(includeUnowned = true)),
        ) as CatalogUiState.Ready
        assertThat(all.heroes.map { it.hero.id }).contains(TestCatalog.hero(11).id)
    }

    @Test
    fun `active filters remove themselves and clearing keeps the collection switch`() {
        val filter =
            CatalogFilter(
                complexities = setOf(2),
                roles = setOf(TestCatalog.hero(1).roles.first()),
                includeUnowned = true,
            )
        val state = CatalogUiState.build(
            catalog,
            index,
            emptySet(),
            "",
            CatalogControls(filter = filter),
        ) as CatalogUiState.Ready
        val chips = activeFilters(state.controls.filter, state.options)
        assertThat(chips.map { it.key }).containsExactly("c2", "r${filter.roles.single().name}").inOrder()
        assertThat(chips.first().remove(filter).complexities).isEmpty()
        assertThat(filter.cleared()).isEqualTo(CatalogFilter(includeUnowned = true))
        assertThat(state.resultSummary("")).endsWith("of 12 heroes")
    }

    @Test
    fun `rules labels match the catalog's, and the ladder picker toggles`() {
        val hero = TestCatalog.hero(1)
        hero.allCards.forEach { card ->
            assertThat(RulesText.cardLabel(hero, card)).isEqualTo(card.kindLabel(card.id == hero.handicap?.id))
        }
        assertThat(LadderSelection.afterPicking(null, 3)).isEqualTo(3)
        assertThat(LadderSelection.afterPicking(3, 3)).isNull()
        assertThat(RulesText.showsLadder("")).isTrue()
        assertThat(RulesText.showsLadder("coins level")).isTrue()
        assertThat(RulesText.showsLadder("respawn")).isFalse()
    }

    @Test
    fun `the setup reducer never turns off the last number or card group`() {
        val one = TriviaConfig(stats = setOf(TriviaStat.ATTACK), groups = setOf(CardGroup.BASIC))
        assertThat(TriviaHomeAction.reduce(one, TriviaHomeAction.ToggleStat(TriviaStat.ATTACK))).isEqualTo(one)
        assertThat(TriviaHomeAction.reduce(one, TriviaHomeAction.ToggleGroup(CardGroup.BASIC))).isEqualTo(one)
        val hero = TriviaHomeAction.reduce(one, TriviaHomeAction.ToggleHero("x"))
        assertThat(TriviaHomeAction.reduce(hero, TriviaHomeAction.AllHeroes).heroIds).isEmpty()
    }

    @Test
    fun `the home scopes heroes to the owned products and explains an empty quiz`() {
        val facts = TriviaCatalogFacts(catalog)
        val owned = setOf("test-core")
        val state = TriviaHomeUiState.of(facts, owned, emptyMap(), TriviaConfig()) as TriviaHomeUiState.Ready
        assertThat(state.heroes.map { it.heroId }.toSet())
            .isEqualTo(catalog.heroes.filter { it.product in owned }.map { it.id }.toSet())
        assertThat(state.heroScopeSummary).isEqualTo("All heroes you own")
        // The test cards print no Radius: nothing to ask.
        val none = TriviaHomeUiState.of(
            facts,
            owned,
            emptyMap(),
            TriviaConfig(stats = setOf(TriviaStat.RADIUS)),
        ) as TriviaHomeUiState.Ready
        assertThat(none.canStart).isFalse()
        assertThat(none.startConfig()).isNull()
        assertThat(none.startNote).isEqualTo(
            "No Basic, Tier I, Tier II, Tier III or Ultimate card of the heroes you own prints Radius. " +
                "Add a number or more cards.",
        )
    }

    @Test
    fun `a round scores answers, stores progress and practises exactly the misses`() {
        val hero = TestCatalog.hero(2)
        val round = TriviaRound.start(
            catalog,
            TriviaConfig(heroIds = listOf(hero.id), length = 4, duels = false),
            emptyList(),
            emptySet(),
            emptyMap(),
            random = Random(1),
        )
        var missed: String? = null
        while (true) {
            val playing = round.state as? TriviaQuizUiState.Playing ?: break
            val q = playing.question as QuizQuestionUi.Number
            val wrong = q.choices.firstOrNull { it != q.value } ?: (q.value + 1)
            val answer = if (missed == null) wrong else q.value
            val updates = requireNotNull(round.answer(TriviaAnswer.Number(answer), now = 1L))
            assertThat(updates).hasSize(1)
            if (missed == null) missed = updates.keys.single()
            val answered = round.state as TriviaQuizUiState.Playing
            assertThat(answered.mark(q.value)).isEqualTo(Mark.RIGHT)
            // Answering twice changes nothing.
            assertThat(round.answer(TriviaAnswer.Number(q.value), now = 2L)).isNull()
            round.next()
        }
        val summary = round.state as TriviaQuizUiState.Summary
        assertThat(summary.asked).isEqualTo(4)
        assertThat(summary.misses).hasSize(1)
        val practice = requireNotNull(round.practice())
        assertThat(practice.factKeys).containsExactly(missed)
        assertThat(practice.config.length).isEqualTo(2)
        assertThat(practice.config.duels).isFalse()
    }

    @Test
    fun `duel marks follow the right pick`() {
        val card = dev.quinton.goa.core.presentation.trivia.TriviaCardUi.of(
            TestCatalog.hero(1),
            TestCatalog.hero(1).cards[0],
        )
        val duel = QuizQuestionUi.Duel(card, card, 5, 7, DuelPick.SECOND)
        assertThat(markFor(duel, null, DuelPick.FIRST)).isEqualTo(Mark.OPEN)
        assertThat(markFor(duel, TriviaAnswer.Duel(DuelPick.FIRST), DuelPick.FIRST)).isEqualTo(Mark.WRONG)
        assertThat(markFor(duel, TriviaAnswer.Duel(DuelPick.FIRST), DuelPick.SECOND)).isEqualTo(Mark.RIGHT)
        assertThat(markFor(duel, TriviaAnswer.Duel(DuelPick.FIRST), DuelPick.TIE)).isEqualTo(Mark.OTHER)
    }
}
