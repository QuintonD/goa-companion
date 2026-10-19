package dev.quinton.goa.feature.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.presentation.players.MatchLogUiState
import dev.quinton.goa.core.presentation.players.MetaUiState
import dev.quinton.goa.feature.players.common.LogMatchFab
import dev.quinton.goa.feature.players.common.rememberFabExpansion
import dev.quinton.goa.feature.players.match.MatchFollowUps
import dev.quinton.goa.feature.players.match.MatchLogContent
import dev.quinton.goa.feature.players.match.UndoSnackbarHost
import dev.quinton.goa.feature.players.meta.MetaActions
import dev.quinton.goa.feature.players.meta.MetaContent

/** [dev.quinton.goa.core.navigation.MatchLogRoute]: the match log on its own (e.g. from a player profile). */
@Composable
fun MatchLogScreen(
    state: MatchLogUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onLogMatch: () -> Unit,
    onDeleteMatch: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    followUps: MatchFollowUps = MatchFollowUps(),
) {
    val fab = rememberFabExpansion()
    GoaScreen(
        title = "Matches",
        onBack = onBack,
        floatingActionButton = {
            if (state.undo == null && !state.isEmpty) {
                LogMatchFab(onLogMatch, expanded = fab.expanded)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(fab.connection)) {
            MatchLogContent(
                state,
                onOpenMatch = onOpenMatch,
                onDeleteMatch = onDeleteMatch,
                onLogMatch = onLogMatch,
                followUps = followUps,
            )
            UndoSnackbarHost(snackbarHostState, state.undo, onUndo = onUndoDelete, onDismiss = onDismissUndo)
        }
    }
}

/** [dev.quinton.goa.core.navigation.MetaRoute]: the meta dashboard on its own. */
@Composable
fun MetaScreen(state: MetaUiState, onBack: () -> Unit, actions: MetaActions) {
    GoaScreen(title = "Meta", onBack = onBack) { padding ->
        MetaContent(state, actions, modifier = Modifier.padding(padding))
    }
}
