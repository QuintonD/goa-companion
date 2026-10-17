package dev.quinton.goa.feature.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.feature.players.match.MatchFollowUps
import dev.quinton.goa.feature.players.match.MatchLogContent
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.UndoSnackbarHost
import dev.quinton.goa.feature.players.meta.MetaActions
import dev.quinton.goa.feature.players.meta.MetaContent
import dev.quinton.goa.feature.players.meta.MetaUiState

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
    GoaScreen(
        title = "Matches",
        onBack = onBack,
        floatingActionButton = {
            if (state.undo == null && !state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onLogMatch,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Log match") },
                    // M3 1.4 leaves the extended FAB's text out of the semantics tree.
                    modifier = Modifier.semantics { contentDescription = "Log match" },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
