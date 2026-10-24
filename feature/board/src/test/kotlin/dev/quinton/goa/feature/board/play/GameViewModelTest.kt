package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.GameViewModel
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableTap
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val factory = fakeFactory()
    private val ai = FakeAi()
    private val coach = FakeCoach()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(saves: FakeGameSaveRepository) = GameViewModel(
        catalogRepository = FakeCatalogRepository(bundledCatalog),
        mapRepository = FakeBoardMapRepository(bundledMaps),
        saves = saves,
        factory = { factory },
        ai = fakeAiPlayers(ai),
        coach = coach,
        clock = EditClock { 5_000L },
        engine = dispatcher,
        random = Random(1),
    ).also { runCurrent() }

    private fun saved(coach: Boolean = true, pace: AiPace = AiPace.NORMAL) =
        FakeGameSaveRepository(StoredGame.Saved(fakeInProgress(factory, coach = coach, pace = pace)))

    private val GameViewModel.play: PlayUi get() = (uiState.value as GameUiState.Playing).play

    private val session get() = factory.created.last()

    /** Answers the person's question with the script's choice, then lets the computer play on. */
    private fun TestScope.answerScripted(vm: GameViewModel) {
        val choice = session.preferred()!!
        vm.onAction(GameAction.Answer(choice))
        advanceUntilIdle()
    }

    @Test
    fun `opens on the person's first question with their hand`() = runTest(dispatcher) {
        val vm = viewModel(saved())
        advanceUntilIdle()
        val play = vm.play
        assertThat(play.status).isEqualTo(TurnStatus.YourTurn)
        assertThat(play.table.asking).isTrue()
        assertThat(play.table.decision?.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(play.table.heroView("arien")?.hand).hasSize(5)
        // Others' hands stay hidden (p.4): counts only.
        assertThat(play.table.heroView("wasp")?.hand).isNull()
        assertThat(play.table.heroView("wasp")?.handCount).isEqualTo(5)
    }

    @Test
    fun `a card is selected, then played, the computer plays its cards, the reveal waits a moment`() = runTest(dispatcher) {
        val vm = viewModel(saved(pace = AiPace.NORMAL))
        advanceUntilIdle()
        vm.onAction(GameAction.Select(Choice.Card("arien-red-1")))
        runCurrent()
        assertThat(vm.play.table.selected).isEqualTo(Choice.Card("arien-red-1"))
        vm.onAction(GameAction.Answer(Choice.Card("arien-red-1")))
        runCurrent()
        // Brogan's card goes down at once; the others follow a quarter pace apart (hidden plays).
        assertThat(ai.calls).isEqualTo(1)
        assertThat(vm.play.status).isInstanceOf(TurnStatus.Waiting::class.java)
        advanceTimeBy(AiPace.NORMAL.millis / 4 + 1)
        assertThat(ai.calls).isEqualTo(2)
        advanceTimeBy(AiPace.NORMAL.millis / 4 + 1)
        assertThat(ai.calls).isEqualTo(3)
        // The reveal: every card with its initiative, highest first, held for two paces.
        val reveal = vm.play.reveal
        assertThat(reveal.map { it.heroId }).containsExactly("wasp", "tigerclaw", "arien", "brogan").inOrder()
        assertThat(reveal.first().initiative).isEqualTo(10)
        advanceTimeBy(AiPace.NORMAL.millis * 2 - 10)
        assertThat(ai.calls).isEqualTo(3)
        advanceTimeBy(20)
        assertThat(ai.calls).isEqualTo(4)
        assertThat(vm.play.status).isEqualTo(TurnStatus.Computer("wasp"))
        assertThat(vm.play.recent.last()).contains("Wasp chooses movement")
    }

    @Test
    fun `the speed setting sets how long each computer action stays on screen`() = runTest(dispatcher) {
        val saves = saved(pace = AiPace.FAST)
        val vm = viewModel(saves)
        advanceUntilIdle()
        vm.onAction(GameAction.Answer(Choice.Card("arien-red-1")))
        runCurrent()
        advanceTimeBy(AiPace.FAST.millis / 4 * 2 + 2)
        assertThat(ai.calls).isEqualTo(3)
        advanceTimeBy(AiPace.FAST.millis * 2 + 1)
        // Wasp's action is on screen; from now on each computer action stays a Slow pace.
        assertThat(ai.calls).isEqualTo(4)
        vm.onAction(GameAction.SetPace(AiPace.SLOW))
        runCurrent()
        assertThat((saves.state.value as StoredGame.Saved).game.pace).isEqualTo(AiPace.SLOW)
        advanceTimeBy(AiPace.FAST.millis + 1)
        assertThat(ai.calls).isEqualTo(5)
        advanceTimeBy(AiPace.SLOW.millis - 10)
        assertThat(ai.calls).isEqualTo(5)
        advanceTimeBy(20)
        assertThat(ai.calls).isEqualTo(6)
    }

    @Test
    fun `answering moves the game on and saves after every answer`() = runTest(dispatcher) {
        val saves = saved()
        val vm = viewModel(saves)
        advanceUntilIdle()
        answerScripted(vm)
        // The computer has answered for Brogan, Wasp and Tigerclaw: Arien's action is next.
        assertThat(vm.play.status).isEqualTo(TurnStatus.YourTurn)
        assertThat(vm.play.table.decision?.kind).isEqualTo(DecisionKind.CHOOSE_ACTION)
        assertThat(vm.play.table.sourceCard?.name).isEqualTo("Dangerous Current")
        assertThat(saves.saves.size).isAtLeast(7)
        val last = (saves.state.value as StoredGame.Saved).game
        assertThat(last.game.savedAt).isEqualTo(5_000L)
    }

    @Test
    fun `a tap on an option selects it, and the computer not defending costs a Life counter`() =
        runTest(dispatcher) {
            val vm = viewModel(saved())
            advanceUntilIdle()
            answerScripted(vm) // card
            vm.onAction(GameAction.Answer(Choice.Action(ActionType.PRIMARY)))
            advanceUntilIdle()
            val table = vm.play.table
            assertThat(table.decision?.kind).isEqualTo(DecisionKind.CHOOSE_UNIT)
            assertThat(vm.tap(TableTap.Piece("tigerclaw"))).isTrue()
            runCurrent()
            assertThat(vm.play.table.selected).isEqualTo(Choice.Unit("tigerclaw"))
            vm.onAction(GameAction.Answer(Choice.Unit("tigerclaw")))
            advanceUntilIdle()
            assertThat(vm.play.table.view.lifeCounters[Team.BLUE]).isEqualTo(1)
            val log = vm.play.log.map { it.text }
            assertThat(log).contains("Tigerclaw is defeated by Arien.")
            // Defeated, then (the computer's choice) back on a Spawn point in turn 4.
            assertThat(log.indexOf("Tigerclaw respawns in the Blue Throne Zone.")).isGreaterThan(
                log.indexOf("Tigerclaw is defeated by Arien."),
            )
        }

    @Test
    fun `take back undoes the person's last answer and is counted`() = runTest(dispatcher) {
        val saves = saved()
        val vm = viewModel(saves)
        advanceUntilIdle()
        assertThat(vm.play.canTakeBack).isFalse()
        answerScripted(vm)
        assertThat(vm.play.canTakeBack).isTrue()
        vm.onAction(GameAction.TakeBack)
        advanceUntilIdle()
        assertThat(vm.play.table.decision?.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(vm.play.table.heroView("arien")?.hand).hasSize(5)
        assertThat(vm.play.takeBacks).isEqualTo(1)
        assertThat((saves.state.value as StoredGame.Saved).game.takeBacks).isEqualTo(1)
    }

    @Test
    fun `take back mid computer turn stops it first`() = runTest(dispatcher) {
        val vm = viewModel(saved(pace = AiPace.SLOW))
        advanceUntilIdle()
        vm.onAction(GameAction.Answer(Choice.Card("arien-red-1")))
        runCurrent()
        assertThat(ai.calls).isEqualTo(1)
        vm.onAction(GameAction.TakeBack)
        advanceUntilIdle()
        assertThat(vm.play.status).isEqualTo(TurnStatus.YourTurn)
        assertThat(vm.play.table.decision?.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `coach nudges before a clearly worse choice, Rethink keeps the question, Choose anyway answers`() =
        runTest(dispatcher) {
            val saves = saved(coach = true)
            val vm = viewModel(saves)
            advanceUntilIdle()
            answerScripted(vm)
            vm.onAction(GameAction.Answer(Choice.Action(ActionType.HOLD)))
            advanceUntilIdle()
            val nudge = vm.play.nudge
            assertThat(nudge?.note?.text).isEqualTo("Tigerclaw is next to you")
            assertThat(nudge?.choiceLabel).isEqualTo("Hold")
            assertThat(vm.play.table.asking).isFalse()
            vm.onAction(GameAction.Rethink)
            advanceUntilIdle()
            assertThat(vm.play.nudge).isNull()
            assertThat(vm.play.table.decision?.kind).isEqualTo(DecisionKind.CHOOSE_ACTION)
            val stored = (saves.state.value as StoredGame.Saved).game
            assertThat(stored.nudges).isEqualTo(1)
            assertThat(stored.rethinks).isEqualTo(1)
            // Again, then Choose anyway: the hold goes through.
            vm.onAction(GameAction.Answer(Choice.Action(ActionType.HOLD)))
            advanceUntilIdle()
            vm.onAction(GameAction.ChooseAnyway)
            advanceUntilIdle()
            assertThat(vm.play.log.map { it.text }).contains("Arien holds.")
        }

    @Test
    fun `with Coach off no choice is shown to the coach`() = runTest(dispatcher) {
        val vm = viewModel(saved(coach = false))
        advanceUntilIdle()
        answerScripted(vm)
        vm.onAction(GameAction.Answer(Choice.Action(ActionType.HOLD)))
        advanceUntilIdle()
        assertThat(coach.asked).isEqualTo(0)
        assertThat(vm.play.nudge).isNull()
        assertThat(vm.play.log.map { it.text }).contains("Arien holds.")
    }

    @Test
    fun `a good choice is checked with the coach and goes straight through`() = runTest(dispatcher) {
        val vm = viewModel(saved(coach = true))
        advanceUntilIdle()
        answerScripted(vm)
        assertThat(coach.asked).isEqualTo(1)
        assertThat(vm.play.nudge).isNull()
    }

    @Test
    fun `Coach can be switched in game and is saved`() = runTest(dispatcher) {
        val saves = saved(coach = true)
        val vm = viewModel(saves)
        advanceUntilIdle()
        vm.onAction(GameAction.SetCoach(false))
        advanceUntilIdle()
        assertThat(vm.play.coach).isFalse()
        assertThat((saves.state.value as StoredGame.Saved).game.coach).isFalse()
    }

    @Test
    fun `a saved game resumes exactly where it was`() = runTest(dispatcher) {
        val saves = saved()
        val vm = viewModel(saves)
        advanceUntilIdle()
        answerScripted(vm)
        vm.onAction(GameAction.Answer(Choice.Action(ActionType.PRIMARY)))
        advanceUntilIdle()
        val before = vm.play.table
        val again = viewModel(FakeGameSaveRepository(saves.state.value))
        advanceUntilIdle()
        assertThat(again.play.table.decision).isEqualTo(before.decision)
        assertThat(again.play.table.view).isEqualTo(before.view)
        assertThat(again.play.canTakeBack).isTrue()
    }

    @Test
    fun `a corrupt or incompatible save is offered for deletion, never a crash`() = runTest(dispatcher) {
        val unreadable = viewModel(FakeGameSaveRepository(StoredGame.Unreadable))
        advanceUntilIdle()
        assertThat(unreadable.uiState.value).isEqualTo(GameUiState.Unavailable(NoGame.UNREADABLE))

        val saves = saved()
        factory.failRestore = true
        val incompatible = viewModel(saves)
        advanceUntilIdle()
        assertThat(incompatible.uiState.value).isEqualTo(GameUiState.Unavailable(NoGame.INCOMPATIBLE))
        incompatible.deleteSaved()
        advanceUntilIdle()
        assertThat(saves.state.value).isEqualTo(StoredGame.None)

        val none = viewModel(FakeGameSaveRepository())
        advanceUntilIdle()
        assertThat(none.uiState.value).isEqualTo(GameUiState.Unavailable(NoGame.NONE))
    }

    @Test
    fun `the game plays to its end with winner, reason, results and the coach turning points`() = runTest(dispatcher) {
        val vm = viewModel(saved(coach = true))
        advanceUntilIdle()
        var guard = 0
        while (vm.play.over == null && guard++ < 40) answerScripted(vm)
        val over = vm.play.over!!
        assertThat(over.winner).isEqualTo(Team.RED)
        assertThat(over.reason).isEqualTo(WinReason.LIFE_COUNTERS)
        assertThat(over.youWon).isTrue()
        assertThat(over.rounds).isEqualTo(2)
        val arien = over.heroes.first { it.heroId == "arien" }
        assertThat(arien.heroDefeats).isEqualTo(1)
        assertThat(arien.level).isEqualTo(2)
        assertThat(over.turningPoints?.map { it.round }).containsExactly(1, 2).inOrder()
        assertThat(vm.play.status).isEqualTo(TurnStatus.Over)
    }

    @Test
    fun `every kind of question reaches the person when they play all four heroes`() = runTest(dispatcher) {
        val all = setOf("arien", "brogan", "wasp", "tigerclaw")
        val saves = FakeGameSaveRepository(StoredGame.Saved(fakeInProgress(factory, fakeSetup(humans = all), coach = false)))
        val vm = viewModel(saves)
        advanceUntilIdle()
        val kinds = mutableSetOf<DecisionKind>()
        var guard = 0
        while (vm.play.over == null && guard++ < 60) {
            kinds += vm.play.table.decision!!.kind
            assertThat(vm.play.status).isEqualTo(TurnStatus.YourTurn)
            answerScripted(vm)
        }
        assertThat(ai.calls).isEqualTo(0)
        assertThat(kinds).containsExactlyElementsIn(DecisionKind.entries)
    }

    @Test
    fun `rematch keeps the seats and flips the coin again`() = runTest(dispatcher) {
        val saves = saved()
        val vm = viewModel(saves)
        advanceUntilIdle()
        var guard = 0
        while (vm.play.over == null && guard++ < 40) answerScripted(vm)
        vm.onAction(GameAction.Rematch)
        advanceUntilIdle()
        assertThat(vm.play.over).isNull()
        assertThat(vm.play.takeBacks).isEqualTo(0)
        assertThat(vm.play.table.decision?.kind).isEqualTo(DecisionKind.PLAY_CARD)
        val stored = (saves.state.value as StoredGame.Saved).game
        assertThat(stored.game.setup.seats).isEqualTo(fakeSetup().seats)
    }
}
