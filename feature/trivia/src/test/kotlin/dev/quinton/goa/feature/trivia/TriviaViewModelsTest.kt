package dev.quinton.goa.feature.trivia

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.presentation.trivia.QuizQuestionUi
import dev.quinton.goa.core.presentation.trivia.TriviaHomeAction
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.core.presentation.trivia.TriviaText
import dev.quinton.goa.core.presentation.trivia.startNote
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.FakeTriviaProgressRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.trivia.home.TriviaHomeViewModel
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriviaViewModelsTest {
    private val catalog = TestCatalog.catalog
    private val progress = FakeTriviaProgressRepository()
    private val settings = FakeSettingsRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun ReceiveTurbine<TriviaHomeUiState>.ready(): TriviaHomeUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is TriviaHomeUiState.Ready) return item
        }
    }

    private fun home(heroId: String? = null, data: Catalog = catalog) =
        TriviaHomeViewModel(heroId, FakeCatalogRepository(data), settings, progress, Dispatchers.Main)

    private fun quiz(config: TriviaConfig, factKeys: List<String> = emptyList(), data: Catalog = catalog) =
        TriviaQuizViewModel(
            TriviaQuizRoute(TriviaText.encode(config), factKeys),
            FakeCatalogRepository(data),
            settings,
            progress,
            EditClock { NOW },
        )

    private fun facts(heroId: String) = TriviaFacts.of(catalog.hero(heroId)!!)

    private fun mastered(fact: TriviaFact) = FactProgress(fact.value, fact.plus, box = TriviaMastery.MASTERED_BOX)

    // ---- Home -----------------------------------------------------------------------------

    @Test
    fun `overall progress counts every number on the owned heroes' cards`() = runTest {
        settings.state.value = AppSettings(ownedProducts = setOf("test-core"))
        val core = catalog.heroes.filter { it.product == "test-core" }
        val learned = facts(core.first().id).take(5)
        val learning = facts(core.last().id).take(2)
        // Progress on a hero outside the owned products doesn't count towards the overall card.
        val unowned = facts("test-hero-1").take(3)
        progress.state.value = (learned + unowned).associate { it.key to mastered(it) } +
            learning.associate { it.key to FactProgress(it.value, it.plus, box = 1) }

        home().uiState.test {
            val ready = ready()
            val total = core.sumOf { TriviaFacts.of(it).size }
            assertThat(ready.overall.total).isEqualTo(total)
            assertThat(ready.overall.mastered).isEqualTo(5)
            assertThat(ready.overall.learning).isEqualTo(2)
            assertThat(ready.heroes.map { it.heroId }).containsExactlyElementsIn(core.map { it.id })
            assertThat(ready.ownedOnly).isTrue()
            // Reset forgets everything stored, owned or not.
            assertThat(ready.stored.mastered).isEqualTo(8)
        }
    }

    @Test
    fun `a learned number whose printed value changed counts as new`() = runTest {
        val fact = facts("test-hero-2").first()
        progress.state.value = mapOf(fact.key to FactProgress(fact.value + 1, box = 5))
        home().uiState.test { assertThat(ready().overall.mastered).isEqualTo(0) }
    }

    @Test
    fun `setup starts from the last quiz, and a hero page preselects that hero alone`() = runTest {
        progress.configState.value = TriviaConfig(
            heroIds = listOf("test-hero-1", "test-hero-2"),
            stats = setOf(TriviaStat.ATTACK),
            length = 20,
        )
        home().uiState.test {
            assertThat(ready().setup.heroIds).containsExactly("test-hero-1", "test-hero-2")
        }
        home("test-hero-4").uiState.test {
            val setup = ready().setup
            assertThat(setup.heroIds).containsExactly("test-hero-4")
            assertThat(setup.stats).containsExactly(TriviaStat.ATTACK)
            assertThat(setup.length).isEqualTo(20)
        }
    }

    @Test
    fun `without a last quiz the setup is the defaults`() = runTest {
        home().uiState.test { assertThat(ready().setup).isEqualTo(TriviaConfig()) }
    }

    @Test
    fun `the last number and the last card group can't be turned off`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            TriviaStat.entries.forEach { vm.onAction(TriviaHomeAction.ToggleStat(it)) }
            CardGroup.entries.forEach { vm.onAction(TriviaHomeAction.ToggleGroup(it)) }
            val setup = expectMostRecentItem().let { it as TriviaHomeUiState.Ready }.setup
            assertThat(setup.stats).containsExactly(TriviaStat.RADIUS)
            assertThat(setup.groups).containsExactly(CardGroup.ULTIMATE)
        }
    }

    @Test
    fun `a setup with no numbers can't start`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            // The test cards print no Radius.
            TriviaStat.entries.filter {
                it != TriviaStat.RADIUS
            }.forEach { vm.onAction(TriviaHomeAction.ToggleStat(it)) }
            val ready = expectMostRecentItem() as TriviaHomeUiState.Ready
            assertThat(ready.setup.stats).containsExactly(TriviaStat.RADIUS)
            assertThat(ready.selection.total).isEqualTo(0)
            assertThat(ready.canStart).isFalse()
            assertThat(vm.start()).isNull()
            assertThat(progress.configState.value).isNull()
            assertThat(startNote(ready)).isEqualTo(
                "No Basic, Tier I, Tier II, Tier III or Ultimate card prints Radius. Add a number or more cards.",
            )
        }
    }

    @Test
    fun `why Start is off names the quiz's own scope, so the claim is true as written`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            // The test cards print Range on Tier II only.
            CardGroup.entries.filter { it != CardGroup.ULTIMATE }
                .forEach { vm.onAction(TriviaHomeAction.ToggleGroup(it)) }
            TriviaStat.entries.filter { it != TriviaStat.RANGE }
                .forEach { vm.onAction(TriviaHomeAction.ToggleStat(it)) }
            fun note() = startNote(expectMostRecentItem() as TriviaHomeUiState.Ready)
            val add = " Add a number or more cards."
            // Settings limits the heroes to one product: the scope is the heroes you own.
            settings.state.value = AppSettings(ownedProducts = setOf("test-core"))
            assertThat(note()).isEqualTo("No Ultimate card of the heroes you own prints Range.$add")
            vm.onAction(TriviaHomeAction.ToggleHero("test-hero-1"))
            assertThat(note()).isEqualTo("No Ultimate card of the chosen hero prints Range.$add")
            vm.onAction(TriviaHomeAction.ToggleHero("test-hero-2"))
            assertThat(note()).isEqualTo("No Ultimate card of the chosen heroes prints Range.$add")
            // Every hero, nothing limited: the plain claim.
            vm.onAction(TriviaHomeAction.AllHeroes)
            settings.state.value = AppSettings()
            assertThat(note()).isEqualTo("No Ultimate card prints Range.$add")
        }
    }

    @Test
    fun `all heroes counts the owned heroes, not a chosen hero outside them`() = runTest {
        settings.state.value = AppSettings(ownedProducts = setOf("test-core"))
        val core = catalog.heroes.filter { it.product == "test-core" }
        val vm = home()
        vm.uiState.test {
            ready()
            vm.onAction(TriviaHomeAction.ToggleHero("test-hero-1")) // a Test Pack hero
            val ready = expectMostRecentItem() as TriviaHomeUiState.Ready
            assertThat(ready.heroOptions).hasSize(core.size + 1)
            assertThat(ready.ownedHeroCount).isEqualTo(core.size)
        }
    }

    @Test
    fun `unverified heroes are marked in the picker options and progress rows`() = runTest {
        home().uiState.test {
            val ready = ready()
            val unverified = catalog.heroes.filterNot { it.verified }.map { it.id }
            assertThat(unverified).isNotEmpty()
            assertThat(ready.heroOptions.filterNot { it.verified }.map { it.heroId })
                .containsExactlyElementsIn(unverified)
            assertThat(ready.heroes.filterNot { it.verified }.map { it.heroId }).containsExactlyElementsIn(unverified)
        }
    }

    @Test
    fun `a pool smaller than the round says numbers will repeat`() = runTest {
        val vm = home("test-hero-1")
        vm.uiState.test {
            ready()
            TriviaStat.entries.filter {
                it != TriviaStat.INITIATIVE
            }.forEach { vm.onAction(TriviaHomeAction.ToggleStat(it)) }
            CardGroup.entries.filter { it != CardGroup.BASIC }.forEach { vm.onAction(TriviaHomeAction.ToggleGroup(it)) }
            val ready = expectMostRecentItem() as TriviaHomeUiState.Ready
            assertThat(ready.selection.total).isEqualTo(2)
            assertThat(ready.canStart).isTrue()
            assertThat(startNote(ready)).isEqualTo("Only 2 numbers for 10 questions, so some come up more than once.")
            vm.onAction(TriviaHomeAction.SetLength(TriviaConfig.ENDLESS))
            assertThat(startNote(expectMostRecentItem() as TriviaHomeUiState.Ready)).isNull()
        }
    }

    @Test
    fun `the live line counts the numbers in the chosen heroes, numbers and cards`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            vm.onAction(TriviaHomeAction.ToggleHero("test-hero-3"))
            TriviaStat.entries.filter {
                it != TriviaStat.INITIATIVE
            }.forEach { vm.onAction(TriviaHomeAction.ToggleStat(it)) }
            val ready = expectMostRecentItem() as TriviaHomeUiState.Ready
            // 18 deck cards, one initiative each.
            assertThat(ready.selection.total).isEqualTo(18)
            assertThat(ready.selectedHeroNames).containsExactly("Test Hero 3")
            vm.onAction(TriviaHomeAction.ToggleHero("test-hero-3"))
            assertThat((expectMostRecentItem() as TriviaHomeUiState.Ready).setup.heroIds).isEmpty()
        }
    }

    @Test
    fun `start saves the setup and opens a quiz with it`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            vm.onAction(TriviaHomeAction.SetAnswerStyle(AnswerStyle.RECALL))
            vm.onAction(TriviaHomeAction.SetLength(TriviaConfig.ENDLESS))
            vm.onAction(TriviaHomeAction.SetDuels(false))
            expectMostRecentItem()
            val route = vm.start()!!
            val config = TriviaText.decode(route.config)
            assertThat(config.answerStyle).isEqualTo(AnswerStyle.RECALL)
            assertThat(config.length).isEqualTo(TriviaConfig.ENDLESS)
            assertThat(config.duels).isFalse()
            assertThat(route.factKeys).isEmpty()
            assertThat(progress.configState.value).isEqualTo(config)
        }
    }

    @Test
    fun `tapping a hero quizzes it alone with the current settings and keeps the setup's heroes`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            vm.onAction(TriviaHomeAction.ToggleStat(TriviaStat.DEFENSE))
            expectMostRecentItem()
            val route = vm.startHero("test-hero-5")!!
            val config = TriviaText.decode(route.config)
            assertThat(config.heroIds).containsExactly("test-hero-5")
            assertThat(config.stats).doesNotContain(TriviaStat.DEFENSE)
            assertThat(progress.configState.value!!.heroIds).isEmpty()
        }
    }

    @Test
    fun `reset forgets all progress`() = runTest {
        val fact = facts("test-hero-1").first()
        progress.state.value = mapOf(fact.key to mastered(fact))
        val vm = home()
        vm.uiState.test {
            assertThat(ready().stored.mastered).isEqualTo(1)
            vm.resetProgress()
            assertThat((expectMostRecentItem() as TriviaHomeUiState.Ready).stored.mastered).isEqualTo(0)
            assertThat(progress.state.value).isEmpty()
        }
    }

    // ---- Quiz -----------------------------------------------------------------------------

    private fun TriviaQuizViewModel.playing() = uiState.value as TriviaQuizUiState.Playing

    private fun rightAnswer(state: TriviaQuizUiState.Playing): TriviaAnswer = when (val q = state.question) {
        is QuizQuestionUi.Number -> TriviaAnswer.Number(q.value)
        is QuizQuestionUi.Duel -> TriviaAnswer.Duel(q.right)
    }

    private fun wrongAnswer(state: TriviaQuizUiState.Playing): TriviaAnswer = when (val q = state.question) {
        is QuizQuestionUi.Number -> TriviaAnswer.Number(q.choices.firstOrNull { it != q.value } ?: (q.value + 1))

        is QuizQuestionUi.Duel -> TriviaAnswer.Duel(
            dev.quinton.goa.core.domain.trivia.DuelPick.entries.first { it != q.right },
        )
    }

    @Test
    fun `the pool comes from the config's heroes, numbers and cards`() = runTest {
        val vm = quiz(
            TriviaConfig(
                heroIds = listOf("test-hero-1"),
                stats = setOf(TriviaStat.INITIATIVE),
                groups = setOf(CardGroup.BASIC),
                duels = false,
            ),
        )
        repeat(5) {
            val q = vm.playing().question as QuizQuestionUi.Number
            assertThat(q.card.heroId).isEqualTo("test-hero-1")
            assertThat(q.card.cardId).isAnyOf("test-hero-1-gold", "test-hero-1-silver")
            assertThat(q.stat).isEqualTo(TriviaStat.INITIATIVE)
            assertThat(q.card.label).isAnyOf("Gold · Basic", "Silver · Basic")
            assertThat(q.choices).hasSize(4)
            assertThat(q.choices).contains(q.value)
            vm.answer(rightAnswer(vm.playing()))
            vm.next()
        }
    }

    @Test
    fun `an empty hero list quizzes the owned heroes`() = runTest {
        settings.state.value = AppSettings(ownedProducts = setOf("test-pack"))
        val vm = quiz(TriviaConfig(length = 20, duels = false))
        repeat(20) {
            val q = vm.playing().question as QuizQuestionUi.Number
            assertThat(catalog.hero(q.card.heroId)!!.product).isEqualTo("test-pack")
            vm.answer(rightAnswer(vm.playing()))
            vm.next()
        }
    }

    @Test
    fun `fact keys limit the round to those numbers`() = runTest {
        val fact = facts("test-hero-2").first { it.stat == TriviaStat.RANGE }
        val vm = quiz(TriviaConfig(duels = true, stats = setOf(TriviaStat.ATTACK)), factKeys = listOf(fact.key))
        repeat(3) {
            val q = vm.playing().question as QuizQuestionUi.Number
            assertThat(q.card.cardId).isEqualTo(fact.cardId)
            assertThat(q.stat).isEqualTo(TriviaStat.RANGE)
            vm.answer(rightAnswer(vm.playing()))
            vm.next()
        }
    }

    @Test
    fun `an answer is stored right away and locks the question`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), duels = false))
        val first = vm.playing()
        assertThat(first.number).isEqualTo(1)
        assertThat(first.answered).isFalse()
        val q = first.question as QuizQuestionUi.Number
        vm.answer(TriviaAnswer.Number(q.value))
        val answered = vm.playing()
        assertThat(answered.correct).isTrue()
        assertThat(answered.number).isEqualTo(1)
        assertThat(answered.feedback).startsWith("Right!")
        assertThat(answered.correctCount).isEqualTo(1)
        val key = TriviaFact.keyOf(q.card.cardId, q.stat)
        assertThat(progress.state.value[key]!!.box).isEqualTo(1)
        assertThat(progress.state.value[key]!!.lastSeenAt).isEqualTo(NOW)

        // A second answer to the same question is ignored.
        vm.answer(wrongAnswer(answered))
        assertThat(vm.playing().correct).isTrue()
        assertThat(progress.state.value[key]!!.seen).isEqualTo(1)
    }

    @Test
    fun `a wrong answer names the printed number`() = runTest {
        val vm =
            quiz(TriviaConfig(heroIds = listOf("test-hero-1"), stats = setOf(TriviaStat.INITIATIVE), duels = false))
        val q = vm.playing().question as QuizQuestionUi.Number
        vm.answer(wrongAnswer(vm.playing()))
        assertThat(vm.playing().correct).isFalse()
        assertThat(vm.playing().feedback).isEqualTo("Not quite. ${q.card.name} is initiative ${q.value}.")
        assertThat(progress.state.value.values.single().box).isEqualTo(0)
    }

    @Test
    fun `a fixed round ends in a summary with the misses and newly learned numbers`() = runTest {
        // A is one right answer away from learned; B is new and always missed. Two cards, so they alternate.
        val known = facts("test-hero-3").first()
        val missed = facts("test-hero-3").first { it.cardId != known.cardId }
        progress.state.value = mapOf(known.key to FactProgress(known.value, known.plus, box = 2))
        val vm = quiz(TriviaConfig(length = 10, duels = false), factKeys = listOf(known.key, missed.key))
        vm.uiState.test {
            repeat(10) { i ->
                val state = expectMostRecentItem() as TriviaQuizUiState.Playing
                assertThat(state.number).isEqualTo(i + 1)
                assertThat(state.length).isEqualTo(10)
                val q = state.question as QuizQuestionUi.Number
                vm.answer(if (q.card.cardId == missed.cardId) wrongAnswer(state) else rightAnswer(state))
                val answered = expectMostRecentItem() as TriviaQuizUiState.Playing
                assertThat(answered.lastQuestion).isEqualTo(i == 9)
                vm.next()
            }
            val summary = expectMostRecentItem() as TriviaQuizUiState.Summary
            assertThat(summary.asked).isEqualTo(10)
            assertThat(summary.correct).isEqualTo(5)
            assertThat(summary.bestStreak).isEqualTo(1)
            // A's first answer this round lifts it to box 3: learned (it wasn't when the round began).
            // Its later answers come two questions apart: counted, but they don't lift it further.
            assertThat(summary.learned).isEqualTo(1)
            assertThat(progress.state.value.getValue(known.key).box).isEqualTo(TriviaMastery.MASTERED_BOX)
            assertThat(progress.state.value.getValue(known.key).seen).isEqualTo(5)
            assertThat(summary.misses).hasSize(5)
            assertThat(summary.misses.map { it.card.cardId }.toSet()).containsExactly(missed.cardId)
            assertThat(summary.misses.first().right).isEqualTo("${missed.value}")
            assertThat(summary.canPractise).isTrue()
        }
        val practise = vm.practiseRoute()!!
        assertThat(practise.factKeys).containsExactly(missed.key)
        val config = TriviaText.decode(practise.config)
        assertThat(config.duels).isFalse()
        // Each missed number twice, never more than the round's length.
        assertThat(config.length).isEqualTo(2)
    }

    @Test
    fun `practising the misses of an endless round is a fixed round`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), length = TriviaConfig.ENDLESS, duels = false))
        val keys = mutableSetOf<String>()
        repeat(6) {
            val q = vm.playing().question as QuizQuestionUi.Number
            keys += TriviaFact.keyOf(q.card.cardId, q.stat)
            vm.answer(wrongAnswer(vm.playing()))
            vm.next()
        }
        vm.stop()
        val config = TriviaText.decode(vm.practiseRoute()!!.config)
        assertThat(config.length).isEqualTo(2 * vm.practiseRoute()!!.factKeys.size)
        assertThat(config.length).isNotEqualTo(TriviaConfig.ENDLESS)
    }

    @Test
    fun `an answer's progress is stored even when the quiz is closed straight after`() = runTest {
        val gated = GatedProgress(progress)
        val vm = TriviaQuizViewModel(
            TriviaQuizRoute(TriviaText.encode(TriviaConfig(heroIds = listOf("test-hero-1"), duels = false))),
            FakeCatalogRepository(catalog),
            settings,
            gated,
            EditClock { NOW },
        )
        val q = vm.playing().question as QuizQuestionUi.Number
        vm.answer(TriviaAnswer.Number(q.value))
        // The write is under way when the screen goes (its view model scope is cancelled).
        vm.viewModelScope.cancel()
        gated.gate.complete(Unit)
        assertThat(progress.state.value[TriviaFact.keyOf(q.card.cardId, q.stat)]!!.box).isEqualTo(1)
    }

    /** Holds every write until [gate] opens. */
    private class GatedProgress(private val inner: FakeTriviaProgressRepository) :
        TriviaProgressRepository by inner {
        val gate = CompletableDeferred<Unit>()

        override suspend fun record(updates: Map<String, FactProgress>) {
            gate.await()
            inner.record(updates)
        }
    }

    @Test
    fun `leaving asks only once a question is answered`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), duels = false))
        assertThat(vm.playing().started).isFalse()
        vm.answer(rightAnswer(vm.playing()))
        assertThat(vm.playing().started).isTrue()
        vm.next()
        assertThat(vm.playing().started).isTrue()
    }

    @Test
    fun `a question on an unverified hero's card says so`() = runTest {
        val unverified = catalog.heroes.first { !it.verified }
        val vm = quiz(TriviaConfig(heroIds = listOf(unverified.id), duels = false))
        assertThat((vm.playing().question as QuizQuestionUi.Number).card.heroVerified).isFalse()
        val verified = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), duels = false))
        assertThat((verified.playing().question as QuizQuestionUi.Number).card.heroVerified).isTrue()
    }

    @Test
    fun `an endless round runs until it is ended`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), length = TriviaConfig.ENDLESS))
        repeat(25) {
            assertThat(vm.playing().length).isNull()
            vm.answer(rightAnswer(vm.playing()))
            assertThat(vm.playing().lastQuestion).isFalse()
            vm.next()
        }
        vm.stop()
        val summary = vm.uiState.value as TriviaQuizUiState.Summary
        assertThat(summary.asked).isEqualTo(25)
        assertThat(summary.correct).isEqualTo(25)
        assertThat(summary.misses).isEmpty()
        assertThat(vm.practiseRoute()).isNull()
    }

    @Test
    fun `play again starts a fresh round on the same numbers`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1"), length = 10, duels = false))
        repeat(10) {
            vm.answer(rightAnswer(vm.playing()))
            vm.next()
        }
        assertThat(vm.uiState.value).isInstanceOf(TriviaQuizUiState.Summary::class.java)
        vm.playAgain()
        assertThat(vm.playing().number).isEqualTo(1)
        assertThat(vm.playing().correctCount).isEqualTo(0)
    }

    @Test
    fun `duels compare two cards' initiative`() = runTest {
        val vm = quiz(TriviaConfig(heroIds = listOf("test-hero-1", "test-hero-2"), length = TriviaConfig.ENDLESS))
        var duels = 0
        repeat(200) {
            val q = vm.playing().question
            if (q is QuizQuestionUi.Duel) {
                duels++
                assertThat(q.first.cardId).isNotEqualTo(q.second.cardId)
                vm.answer(TriviaAnswer.Duel(q.right))
                assertThat(vm.playing().correct).isTrue()
            } else {
                vm.answer(rightAnswer(vm.playing()))
            }
            vm.next()
        }
        assertThat(duels).isGreaterThan(0)
    }

    @Test
    fun `a setup whose cards print none of the numbers shows the empty state`() = runTest {
        val vm = quiz(TriviaConfig(stats = setOf(TriviaStat.RADIUS)))
        assertThat(vm.uiState.value).isEqualTo(TriviaQuizUiState.Empty)
    }

    @Test
    fun `a config from another build still opens`() = runTest {
        val vm = TriviaQuizViewModel(
            TriviaQuizRoute("""{"heroIds":["test-hero-1"],"answerStyle":"someday","future":1}"""),
            FakeCatalogRepository(catalog),
            settings,
            progress,
            EditClock { NOW },
        )
        assertThat(vm.uiState.value).isInstanceOf(TriviaQuizUiState.Playing::class.java)
    }

    @Test
    fun `printed plus values keep their plus`() = runTest {
        val plusCatalog = catalog.copy(
            heroes = catalog.heroes.map { hero ->
                hero.copy(
                    cards = hero.cards.map {
                        it.copy(values = it.values.copy(plus = listOf(dev.quinton.goa.core.model.catalog.Stat.ATTACK)))
                    },
                )
            },
        )
        val vm = quiz(
            TriviaConfig(heroIds = listOf("test-hero-1"), stats = setOf(TriviaStat.ATTACK), duels = false),
            data = plusCatalog,
        )
        val q = vm.playing().question as QuizQuestionUi.Number
        assertThat(q.plus).isTrue()
        vm.answer(TriviaAnswer.Number(q.value))
        assertThat(vm.playing().feedback).isEqualTo("Right! ${q.card.name} has Attack ${q.value}+.")
    }

    private companion object {
        const val NOW = 1_000L
    }
}
