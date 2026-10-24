package dev.quinton.goa.feature.board.play.review

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakePuzzleProgressRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzleAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUiState
import dev.quinton.goa.feature.board.play.puzzles.PuzzleViewModel
import dev.quinton.goa.feature.board.play.puzzles.realPuzzleBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The game-state review through the play and puzzle ViewModels on the real engine (ADR-037): built
 * only while open, the person's view only (the computer's facedown cards never named), boards of
 * earlier turns stepped through, a puzzle's hands open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private fun open(vm: dev.quinton.goa.feature.board.play.game.GameViewModel, action: ReviewAction) =
        vm.onAction(GameAction.Review(action))

    @Test
    fun `the review is built only while open and follows the game turn by turn`() {
        val game = reviewGameAt(round = 2, turn = 2)
        var closed: Any? = Unit
        val play = reviewViewModel(game) { vm, settle ->
            closed = (vm.uiState.value as dev.quinton.goa.feature.board.play.game.GameUiState.Playing).play.review
            open(vm, ReviewAction.Open(true))
            settle()
        }
        assertThat(closed).isNull()
        val review = play.review!!
        assertThat(review.turns.map { it.round to it.turn }.take(5)).containsExactly(
            1 to 1,
            1 to 2,
            1 to 3,
            1 to 4,
            2 to 1,
        ).inOrder()
        assertThat(review.turns.last().finished).isFalse()
        assertThat(review.turns.dropLast(1).all { it.finished && it.plays.size == 4 }).isTrue()
        // Lines leave out headings and "plays a card facedown".
        assertThat(review.turns.flatMap { it.lines }.none { it.text.endsWith("plays a card facedown.") }).isTrue()
        assertThat(review.turns.first { it.turn == 4 }.endOfRound).isNotEmpty()
    }

    @Test
    fun `before the reveal the computer's cards are down and unnamed`() {
        // Brogan plays last: the other three have played facedown when his question is up.
        val game = reviewEngine.newGame(reviewSetup(humans = setOf("brogan")))
        val ai = dev.quinton.goa.core.game.ai.AiPlayers.deterministic(dev.quinton.goa.core.game.AiLevel.EASY, 1)
        while (game.pending!!.seat != "brogan") game.answer(game.pending!!.id, ai.choose(game, game.pending!!))
        assertThat(game.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        val play = reviewViewModel(game) { vm, settle ->
            open(vm, ReviewAction.Open(true))
            settle()
        }
        val now = play.review!!.turns.single()
        assertThat(now.plays).isEmpty()
        play.table.view.heroes.filter { it.heroId != "brogan" }.forEach { hero ->
            assertThat(hero.played!!.cardId).isNull()
            assertThat(hero.played!!.facedown).isTrue()
            assertThat(hero.hand).isNull()
            assertThat(hero.items).isEmpty()
        }
    }

    @Test
    fun `boards of earlier turns are rewound, stepped and closed`() {
        val game = reviewGameAt(round = 1, turn = 3)
        val states = mutableListOf<ReviewUi?>()
        reviewViewModel(game) { vm, settle ->
            fun now() = (vm.uiState.value as dev.quinton.goa.feature.board.play.game.GameUiState.Playing).play.review
            open(vm, ReviewAction.Open(true))
            settle()
            open(vm, ReviewAction.Rewind(1, 1))
            settle()
            states += now()
            open(vm, ReviewAction.Step(1))
            settle()
            states += now()
            open(vm, ReviewAction.CloseRewind)
            settle()
            states += now()
            open(vm, ReviewAction.Open(false))
            settle()
            states += now()
        }
        val first = states[0]!!.rewind!!
        assertThat(first.round to first.turn).isEqualTo(1 to 1)
        assertThat(first.hasEarlier).isFalse()
        assertThat(first.view.turn).isEqualTo(1)
        // The person's hand then, the others' only as counts.
        assertThat(first.view.heroes.first { it.heroId == "wasp" }.hand).isNotNull()
        assertThat(first.view.heroes.filter { it.heroId != "wasp" }.all { it.hand == null }).isTrue()
        assertThat(states[1]!!.rewind!!.turn).isEqualTo(2)
        assertThat(states[2]!!.rewind).isNull()
        assertThat(states[3]).isNull()
    }

    @Test
    fun `a puzzle's review shows every hand`() {
        val id = "p17"
        assumeTrue("Needs $id", realPuzzleBook.puzzles.any { it.id == id })
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val vm = PuzzleViewModel(
                puzzleId = id,
                catalogRepository = FakeCatalogRepository(bundledCatalog),
                mapRepository = FakeBoardMapRepository(bundledMaps),
                settingsRepository = FakeSettingsRepository(AppSettings(puzzleNudges = false)),
                progressRepository = FakePuzzleProgressRepository(),
                book = { realPuzzleBook },
                clock = EditClock { 0L },
                engine = dispatcher,
            )
            fun settle() {
                scheduler.advanceTimeBy(5_000)
                scheduler.runCurrent()
            }
            settle()
            vm.onAction(PuzzleAction.Review(ReviewAction.Open(true)))
            settle()
            val review = (vm.uiState.value as PuzzleUiState.Ready).puzzle.review!!
            val first = review.turns.first()
            assertThat(first.hasStart).isTrue()
            vm.onAction(PuzzleAction.Review(ReviewAction.Rewind(first.round, first.turn)))
            settle()
            val board = (vm.uiState.value as PuzzleUiState.Ready).puzzle.review!!.rewind!!.view
            assertThat(board.heroes.all { it.hand != null }).isTrue()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
