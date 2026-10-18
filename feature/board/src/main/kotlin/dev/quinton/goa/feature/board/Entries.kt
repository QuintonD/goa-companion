package dev.quinton.goa.feature.board

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
        BoardsScreen(
            state = state,
            onOpen = { navigator.navigate(BoardRoute(boardId = it)) },
            onNew = { navigator.navigate(NewBoardRoute()) },
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
        NewBoardScreen(
            state = state,
            onAction = viewModel::onAction,
            onSetUp = {
                val route = viewModel.setUp() ?: return@NewBoardScreen
                // The board replaces New board: Back from it returns to Boards.
                navigator.back()
                navigator.navigate(route)
            },
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
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
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
