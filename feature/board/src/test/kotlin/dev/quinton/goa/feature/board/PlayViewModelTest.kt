package dev.quinton.goa.feature.board

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.data.game.GameEntry
import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.data.game.PlaySpeed
import dev.quinton.goa.core.data.game.UnreadableGame
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.navigation.PlayRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeSavedGameRepository
import dev.quinton.goa.feature.board.play.game.Interlude
import dev.quinton.goa.feature.board.play.game.PlayUiState
import dev.quinton.goa.feature.board.play.game.PlayViewModel
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The play screen's ViewModel driving the real engine with the four playable heroes. Everything is
 * deterministic: the computer is a seeded random-legal player and every dispatcher (the engine's
 * thread, the computer's, the main one) is the test dispatcher, so pacing runs in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val catalog = FakeCatalogRepository(bundledCatalog)
    private val maps = FakeBoardMapRepository(bundledMaps)
    private val provider = GameFactoryProvider { PlayTestData.engine }

    /** Every computer answer as (virtual time, decision kind). */
    private val answered = mutableListOf<Pair<Long, DecisionKind>>()

    private fun TestScope.viewModel(
        games: FakeSavedGameRepository,
        id: String = "game",
        coach: Coach = FakeCoach(),
    ): PlayViewModel = PlayViewModel(
        route = PlayRoute(id),
        provider = provider,
        games = games,
        catalogRepository = catalog,
        mapRepository = maps,
        aiPlayers = AiPlayers { _, seed ->
            PlayTestData.TestAi(seed) { answered += testScheduler.currentTime to it.kind }
        },
        coach = coach,
        clock = EditClock { PlayTestData.CREATED + testScheduler.currentTime },
        engine = dispatcher,
        compute = dispatcher,
        random = Random(7),
        newId = { "rematch" },
    )

    private fun games(vararg entries: GameEntry) = FakeSavedGameRepository(entries.toList())

    private fun PlayViewModel.playing(): PlayUiState.Playing = uiState.value as PlayUiState.Playing

    /** Runs until the player has something to do (answer, Continue) or the game is over. */
    private fun TestScope.settle(vm: PlayViewModel): PlayUiState.Playing {
        advanceUntilIdle()
        return vm.playing()
    }

    /** Plays the player's seats with a seeded busy random player until [until] or the end. */
    private fun TestScope.playOn(
        vm: PlayViewModel,
        seed: Long = 3,
        maxAnswers: Int = 20_000,
        until: (PlayUiState.Playing) -> Boolean = { false },
    ): PlayUiState.Playing {
        val player = PlayTestData.Busy(seed)
        var state = settle(vm)
        var n = 0
        while (state.over == null && !until(state) && n++ < maxAnswers) {
            when {
                state.interlude != null -> vm.continueGame()
                state.coach != null -> vm.chooseAnyway()
                else -> {
                    val decision = state.table.decision
                    assertThat(decision).isNotNull()
                    assertThat(decision!!.mine).isTrue()
                    vm.choose(decision.id, player.choose(decision.decision))
                }
            }
            state = settle(vm)
        }
        return state
    }

    @Test
    fun `a new game opens on the player's first card, every computer seat before it answered`() = runTest(dispatcher) {
        val games = games(PlayTestData.stored(PlayTestData.firstGame()))
        val vm = viewModel(games)
        val state = settle(vm)
        val decision = state.table.decision!!
        assertThat(decision.mine).isTrue()
        assertThat(decision.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(decision.seat?.id).isEqualTo("wasp")
        // The player sees their own hand, and the prompt says what is asked.
        assertThat(state.table.viewer?.id).isEqualTo("wasp")
        assertThat(state.table.hand.map { it.id }).containsExactlyElementsIn(decision.cards.map { it.cardId })
        assertThat(decision.prompt).isNotEmpty()
        assertThat(state.table.view.phase).isEqualTo(GamePhase.PLAY_CARDS)
        assertThat(state.canTakeBack).isFalse()
    }

    @Test
    fun `an answer is applied and saved, the computer plays on, and the reveal waits for Continue`() =
        runTest(dispatcher) {
            val games = games(PlayTestData.stored(PlayTestData.firstGame()))
            val vm = viewModel(games)
            val first = settle(vm).table.decision!!
            val writes = games.writes
            vm.choose(first.id, first.cards.first().choice)
            val state = settle(vm)
            assertThat(games.writes).isGreaterThan(writes)
            assertThat(games.stored("game")!!.game).isNotEqualTo(PlayTestData.stored(PlayTestData.firstGame()).game)
            // Everyone has played: the reveal shows every card in initiative order and waits.
            val reveal = state.interlude as Interlude.Reveal
            assertThat(reveal.order.map { it.hero.id }).containsExactly("wasp", "xargatha", "arien", "brogan")
            assertThat(reveal.order.all { it.card != null && it.initiative != null }).isTrue()
            assertThat(reveal.order.map { it.initiative!! }).isInOrder(Comparator.reverseOrder<Int>())
            assertThat(state.canTakeBack).isTrue()
            assertThat(state.log).isNotEmpty()
            vm.continueGame()
            val after = settle(vm)
            assertThat(after.interlude).isNull()
            assertThat(after.table.decision!!.mine).isTrue()
        }

    @Test
    fun `the computer's actions are paced by the speed setting, card play quickly`() = runTest(dispatcher) {
        val games = games(PlayTestData.stored(PlayTestData.firstGame(), options = PlayOptions(speed = PlaySpeed.SLOW)))
        val vm = viewModel(games)
        playOn(vm, until = { answered.count { it.second != DecisionKind.PLAY_CARD } >= 6 })
        val pace = PlayViewModel.pace(PlaySpeed.SLOW)
        // Each computer answer after one that wasn't card play waited at least the pace.
        answered.zipWithNext().forEach { (a, b) ->
            if (a.second != DecisionKind.PLAY_CARD && b.first > a.first) {
                assertThat(b.first - a.first).isAtLeast(pace)
            }
        }
        // Card play is private and quick: the four cards of a turn go down in well under one pace.
        val plays = answered.filter { it.second == DecisionKind.PLAY_CARD }.take(3)
        assertThat(plays.last().first - plays.first().first).isLessThan(pace)
        // What the computer just did stays on screen with the next question.
        assertThat(vm.playing().activity?.last).isNotEmpty()
    }

    @Test
    fun `take back undoes the player's last answer, counted and saved`() = runTest(dispatcher) {
        val games = games(PlayTestData.stored(PlayTestData.firstGame()))
        val vm = viewModel(games)
        val first = settle(vm).table.decision!!
        vm.choose(first.id, first.cards.last().choice)
        settle(vm)
        vm.takeBack()
        val state = settle(vm)
        val again = state.table.decision!!
        assertThat(again.id).isEqualTo(first.id)
        assertThat(again.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(state.takeBacks).isEqualTo(1)
        assertThat(state.interlude).isNull()
        assertThat(state.canTakeBack).isFalse()
        assertThat(games.stored("game")!!.takeBacks).isEqualTo(1)
        // A different card this time: the game goes on from there.
        vm.choose(again.id, again.cards.first().choice)
        assertThat(settle(vm).interlude).isInstanceOf(Interlude.Reveal::class.java)
    }

    @Test
    fun `the coach's nudge waits for Rethink or Choose anyway and never chooses`() = runTest(dispatcher) {
        val coach = FakeCoach(nudging = true)
        val games = games(PlayTestData.stored(PlayTestData.firstGame()))
        val vm = viewModel(games, coach = coach)
        val first = settle(vm).table.decision!!
        val card = first.cards.first().choice
        vm.choose(first.id, card)
        var state = settle(vm)
        assertThat(state.coach).isNotNull()
        assertThat(state.coach!!.note.text).contains("Lift Up")
        assertThat(state.coach!!.choice).isEqualTo(card)
        assertThat(state.table.decision!!.id).isEqualTo(first.id)
        // Rethink: back to the question, nothing played.
        vm.rethink()
        state = settle(vm)
        assertThat(state.coach).isNull()
        assertThat(state.table.decision!!.id).isEqualTo(first.id)
        // Choose anyway: the nudged card is played.
        vm.choose(first.id, card)
        settle(vm)
        vm.chooseAnyway()
        state = settle(vm)
        assertThat(state.coach).isNull()
        assertThat(state.interlude).isInstanceOf(Interlude.Reveal::class.java)
        assertThat(coach.calls).isEqualTo(2)
    }

    @Test
    fun `with the coach off or silent, no nudge is shown`() = runTest(dispatcher) {
        val silent = FakeCoach(nudging = false)
        val games = games(PlayTestData.stored(PlayTestData.firstGame()))
        val vm = viewModel(games, coach = silent)
        val first = settle(vm).table.decision!!
        vm.choose(first.id, first.cards.first().choice)
        assertThat(settle(vm).coach).isNull()
        assertThat(silent.calls).isEqualTo(1)

        val off = FakeCoach(nudging = true)
        val games2 = games(PlayTestData.stored(PlayTestData.firstGame(), options = PlayOptions(coach = false)))
        val vm2 = viewModel(games2, coach = off)
        val d = settle(vm2).table.decision!!
        vm2.choose(d.id, d.cards.first().choice)
        assertThat(settle(vm2).coach).isNull()
        assertThat(off.calls).isEqualTo(0)
        // Switched on in the game, it speaks again.
        vm2.setCoach(true)
        assertThat(settle(vm2).options.coach).isTrue()
        assertThat(games2.stored("game")!!.options.coach).isTrue()
    }

    @Test
    fun `a game saved mid-card resumes on the same question`() = runTest(dispatcher) {
        val games = games(PlayTestData.stored(PlayTestData.firstGame(human = setOf("wasp", "xargatha"))))
        val vm = viewModel(games)
        // Play until the player is asked something inside a card's resolution.
        val midCard = playOn(vm) { s ->
            s.interlude == null && s.table.decision?.kind.let {
                it != null && it != DecisionKind.PLAY_CARD && it != DecisionKind.CHOOSE_ACTION
            }
        }
        val pending = midCard.table.decision!!
        assertThat(pending.kind).isNotEqualTo(DecisionKind.PLAY_CARD)
        val resumed = viewModel(games)
        val state = settle(resumed)
        assertThat(state.table.decision!!.id).isEqualTo(pending.id)
        assertThat(state.table.decision!!.decision).isEqualTo(pending.decision)
        assertThat(state.table.view.round).isEqualTo(midCard.table.view.round)
        assertThat(state.log).isEqualTo(midCard.log)
    }

    @Test
    fun `a game played to its end shows the winner, the reason and a summary, and rematch flips again`() =
        runTest(dispatcher) {
            val games = games(PlayTestData.stored(PlayTestData.duel()))
            val vm = viewModel(games)
            val end = playOn(vm, seed = 11)
            val over = end.over!!
            assertThat(over.winner).isNotNull()
            assertThat(over.reason).isNotNull()
            assertThat(over.story).isNotEmpty()
            assertThat(over.heroes.map { it.hero.id }).containsExactly("wasp", "arien")
            assertThat(over.rounds).isAtLeast(1)
            assertThat(over.turningPoints).isEmpty()
            val stored = games.stored("game")!!
            assertThat(stored.finished).isTrue()
            assertThat(stored.winner).isEqualTo(over.winner)
            // Answers after the end are ignored.
            vm.choose(0, dev.quinton.goa.core.game.Choice.Pass)
            assertThat(settle(vm).over).isNotNull()
            val rematch = vm.rematch()!!
            assertThat(rematch.gameId).isEqualTo("rematch")
            assertThat(games.stored("game")).isNull()
            val next = games.stored("rematch")!!
            assertThat(next.game.setup.seats).isEqualTo(stored.game.setup.seats)
            assertThat(next.round).isEqualTo(1)
        }

    @Test
    fun `the coach's turning points are shown after the game`() = runTest(dispatcher) {
        val points = listOf(CoachNote("Brogan pushed the lane while you held back.", round = 2, turn = 3))
        val games = games(PlayTestData.stored(PlayTestData.duel()))
        val vm = viewModel(games, coach = FakeCoach(points = points))
        val end = playOn(vm, seed = 11)
        assertThat(end.over!!.turningPoints).isEqualTo(points)
    }

    @Test
    fun `an unreadable save, another engine version or a missing one are offered for deletion`() = runTest(dispatcher) {
        val unreadable = viewModel(games(UnreadableGame("game")))
        advanceUntilIdle()
        assertThat(unreadable.uiState.value).isInstanceOf(PlayUiState.Unplayable::class.java)

        val old = PlayTestData.stored(PlayTestData.firstGame()).let { it.copy(game = it.game.copy(engineVersion = 0)) }
        val repo = games(old)
        val stale = viewModel(repo)
        advanceUntilIdle()
        val state = stale.uiState.value as PlayUiState.Unplayable
        assertThat(state.otherRules).isTrue()
        stale.deleteUnplayable()
        assertThat(repo.state.value).isEmpty()

        val broken = PlayTestData.stored(PlayTestData.firstGame()).let { it.copy(game = it.game.copy(payload = "{\"x\":1}")) }
        val brokenVm = viewModel(games(broken))
        advanceUntilIdle()
        assertThat((brokenVm.uiState.value as PlayUiState.Unplayable).otherRules).isTrue()

        val missing = viewModel(games(), id = "nope")
        advanceUntilIdle()
        assertThat(missing.uiState.value).isInstanceOf(PlayUiState.Unplayable::class.java)
    }
}
