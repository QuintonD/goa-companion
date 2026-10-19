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
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.BoardsRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.feature.board.board.BoardScreen
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.list.BoardsScreen
import dev.quinton.goa.feature.board.list.BoardsUiState
import dev.quinton.goa.feature.board.list.BoardsViewModel
import dev.quinton.goa.feature.board.setup.NewBoardScreen
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import kotlinx.coroutines.launch

/** Board sandbox (ADR-033): the Boards list, New board, and a board. */
fun EntryProviderScope<NavKey>.boardEntries(navigator: Navigator) {
    entry<BoardsRoute> {
        val viewModel = hiltViewModel<BoardsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        // With no boards yet, Boards opens straight on New board (once: deleting the last board
        // later leaves the list showing). Back from New board returns to where Board was opened.
        var checked by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(state) {
            val ready = state as? BoardsUiState.Ready ?: return@LaunchedEffect
            if (checked) return@LaunchedEffect
            checked = true
            if (ready.boards.isEmpty() && !ready.unreadable) {
                navigator.back()
                navigator.navigate(NewBoardRoute())
            }
        }
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
}
