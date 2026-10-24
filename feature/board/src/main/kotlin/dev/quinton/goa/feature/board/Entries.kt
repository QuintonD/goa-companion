package dev.quinton.goa.feature.board

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.BoardsRoute
import dev.quinton.goa.core.navigation.GameRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.navigation.PuzzleRoute
import dev.quinton.goa.core.navigation.PuzzlesRoute
import dev.quinton.goa.feature.board.board.BoardScreen
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.list.BoardsScreen
import dev.quinton.goa.feature.board.list.BoardsViewModel
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.GameViewModel
import dev.quinton.goa.feature.board.play.puzzles.PuzzleListScreen
import dev.quinton.goa.feature.board.play.puzzles.PuzzleListViewModel
import dev.quinton.goa.feature.board.play.puzzles.PuzzleScreen
import dev.quinton.goa.feature.board.play.puzzles.PuzzleViewModel
import dev.quinton.goa.feature.board.play.setup.NewGameScreen
import dev.quinton.goa.feature.board.play.setup.NewGameViewModel
import dev.quinton.goa.feature.board.setup.NewBoardScreen
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import kotlinx.coroutines.launch

/** Board sandbox (ADR-033): the Boards list, New board, and a board; play and puzzles (ADR-035). */
fun EntryProviderScope<NavKey>.boardEntries(navigator: Navigator) {
    entry<BoardsRoute> {
        val viewModel = hiltViewModel<BoardsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        // Boards is also where games against the computer start and resume (ADR-035), so it no
        // longer jumps straight to New board when there are no boards yet.
        BoardsScreen(
            state = state,
            onOpen = { navigator.navigate(BoardRoute(boardId = it)) },
            onNew = { navigator.navigate(NewBoardRoute()) },
            onSetUp = { source -> navigator.navigate(BoardRoute(red = source.lineup.red, blue = source.lineup.blue)) },
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicate,
            onDelete = { id ->
                scope.launch {
                    val deleted = viewModel.delete(id) ?: return@launch
                    navigator.showMessage("Deleted “${deleted.name}”", "Undo") { viewModel.restore(deleted) }
                }
            },
            onBack = navigator::back,
            onPlay = { navigator.navigate(NewGameRoute()) },
            onResume = { navigator.navigate(GameRoute) },
            onPuzzles = { navigator.navigate(PuzzlesRoute) },
        )
    }
    entry<NewBoardRoute> { key ->
        val viewModel = hiltViewModel<NewBoardViewModel, NewBoardViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // A second tap on Set up before the board shows must not open a second board.
        var leaving by remember { mutableStateOf(false) }
        fun open(route: BoardRoute?) {
            if (route == null || leaving) return
            leaving = true
            // The board replaces New board: Back from it returns to where New board was opened.
            navigator.back()
            navigator.navigate(route)
        }
        NewBoardScreen(
            state = state,
            onAction = viewModel::onAction,
            onSetUp = { open(viewModel.setUp()) },
            onSetUpLatest = { open(viewModel.setUp(it)) },
            onBack = navigator::back,
        )
    }
    entry<BoardRoute> { key ->
        val viewModel = hiltViewModel<BoardViewModel, BoardViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // Every change is saved as it happens; a pending one is written when the board stops showing.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                viewModel.flush()
            }
        }
        BoardScreen(
            state = state,
            onAction = viewModel::onAction,
            onTap = viewModel::tap,
            onBack = navigator::back,
        )
    }
    playEntries(navigator)
    puzzleEntries(navigator)
}

/** Puzzles (ADR-035, PRODUCT §10): the list and one puzzle. */
private fun EntryProviderScope<NavKey>.puzzleEntries(navigator: Navigator) {
    entry<PuzzlesRoute> {
        val viewModel = hiltViewModel<PuzzleListViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PuzzleListScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpen = { navigator.navigate(PuzzleRoute(it)) },
            onBack = navigator::back,
        )
    }
    entry<PuzzleRoute> { key ->
        val viewModel = hiltViewModel<PuzzleViewModel, PuzzleViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PuzzleScreen(
            state = state,
            onAction = viewModel::onAction,
            onTap = viewModel::tap,
            onNext = { next ->
                // The next puzzle replaces this one: Back from it returns to the list.
                navigator.back()
                navigator.navigate(PuzzleRoute(next))
            },
            onList = navigator::back,
            onBack = navigator::back,
        )
    }
}

/** Play against the computer (ADR-035): New game and the game in progress. */
private fun EntryProviderScope<NavKey>.playEntries(navigator: Navigator) {
    entry<NewGameRoute> { key ->
        val viewModel = hiltViewModel<NewGameViewModel, NewGameViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        // One tap on Start makes one game, even if tapped again while it is being set up.
        var starting by remember { mutableStateOf(false) }
        NewGameScreen(
            state = state,
            onAction = viewModel::onAction,
            onStart = {
                if (!starting) {
                    starting = true
                    scope.launch {
                        if (viewModel.start()) {
                            // The game replaces New game: Back from it returns to Boards.
                            navigator.back()
                            navigator.navigate(GameRoute)
                        } else {
                            starting = false
                            navigator.showMessage("This game couldn't be set up.")
                        }
                    }
                }
            },
            onBack = navigator::back,
            starting = starting,
        )
    }
    entry<GameRoute> {
        val viewModel = hiltViewModel<GameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        GameScreen(
            state = state,
            onAction = viewModel::onAction,
            onTap = viewModel::tap,
            onDelete = {
                viewModel.deleteSaved()
                navigator.back()
            },
            onNewGame = {
                val heroes = (state as? GameUiState.Playing)?.play?.table?.view?.heroes.orEmpty()
                viewModel.discardFinished()
                navigator.back()
                navigator.navigate(
                    NewGameRoute(
                        red = heroes.filter { it.team == Team.RED }.map { it.heroId },
                        blue = heroes.filter { it.team == Team.BLUE }.map { it.heroId },
                    ),
                )
            },
            onDone = {
                viewModel.discardFinished()
                navigator.back()
            },
            onBack = navigator::back,
        )
    }
}
