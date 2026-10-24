package dev.quinton.goa.feature.board

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.BoardsRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.navigation.PlayRoute
import dev.quinton.goa.feature.board.board.BoardScreen
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.list.BoardsScreen
import dev.quinton.goa.feature.board.list.BoardsViewModel
import dev.quinton.goa.feature.board.play.game.PlayCallbacks
import dev.quinton.goa.feature.board.play.game.PlayScreen
import dev.quinton.goa.feature.board.play.game.PlayViewModel
import dev.quinton.goa.feature.board.play.newgame.NewGameScreen
import dev.quinton.goa.feature.board.play.newgame.NewGameViewModel
import dev.quinton.goa.feature.board.setup.NewBoardScreen
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import kotlinx.coroutines.launch

/**
 * Board sandbox (ADR-033) and play against the computer (ADR-035): the Boards list, New board, a
 * board, New game and a game.
 */
fun EntryProviderScope<NavKey>.boardEntries(navigator: Navigator) {
    entry<BoardsRoute> {
        val viewModel = hiltViewModel<BoardsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
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
            onNewGame = { navigator.navigate(NewGameRoute()) },
            onResume = { navigator.navigate(PlayRoute(it)) },
            onDeleteGame = viewModel::deleteGame,
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
}

/** Play against the computer (ADR-035): New game and a game. */
private fun EntryProviderScope<NavKey>.playEntries(navigator: Navigator) {
    entry<NewGameRoute> { key ->
        val viewModel = hiltViewModel<NewGameViewModel, NewGameViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var leaving by remember { mutableStateOf(false) }
        NewGameScreen(
            state = state,
            onAction = viewModel::onAction,
            onStart = {
                if (!leaving) {
                    scope.launch {
                        val route = viewModel.start() ?: return@launch
                        if (leaving) return@launch
                        leaving = true
                        // The game replaces New game: Back from it returns to Boards.
                        navigator.back()
                        navigator.navigate(route)
                    }
                }
            },
            onBack = navigator::back,
        )
    }
    entry<PlayRoute> { key ->
        val viewModel = hiltViewModel<PlayViewModel, PlayViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var leaving by remember { mutableStateOf(false) }
        fun leave(then: (() -> Unit)? = null) {
            if (leaving) return
            leaving = true
            navigator.back()
            then?.invoke()
        }
        PlayScreen(
            state = state,
            callbacks = PlayCallbacks(
                onChoose = viewModel::choose,
                onContinue = viewModel::continueGame,
                onTakeBack = viewModel::takeBack,
                onChooseAnyway = viewModel::chooseAnyway,
                onRethink = viewModel::rethink,
                onCoach = viewModel::setCoach,
                onSpeed = viewModel::setSpeed,
                onRematch = {
                    scope.launch {
                        val route = viewModel.rematch() ?: return@launch
                        leave { navigator.navigate(route) }
                    }
                },
                onNewGame = {
                    scope.launch {
                        val (red, blue) = viewModel.lineup
                        viewModel.discardFinished()
                        leave { navigator.navigate(NewGameRoute(red = red, blue = blue)) }
                    }
                },
                onDone = {
                    scope.launch {
                        viewModel.discardFinished()
                        leave()
                    }
                },
                onDelete = {
                    scope.launch {
                        viewModel.deleteUnplayable()
                        leave()
                    }
                },
                onBack = { leave() },
            ),
        )
    }
}
