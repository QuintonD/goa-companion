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
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.feature.players.match.MatchLogContent
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.UndoSnackbarHost
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
) {
    GoaScreen(
        title = "Matches",
        onBack = onBack,
        floatingActionButton = {
            if (state.undo == null) {
                ExtendedFloatingActionButton(
                    onClick = onLogMatch,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Log match") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MatchLogContent(state, onOpenMatch = onOpenMatch, onDeleteMatch = onDeleteMatch, onLogMatch = onLogMatch)
            UndoSnackbarHost(snackbarHostState, state.undo, onUndo = onUndoDelete, onDismiss = onDismissUndo)
        }
    }
}

/** [dev.quinton.goa.core.navigation.MetaRoute]: the meta dashboard on its own. */
@Composable
fun MetaScreen(state: MetaUiState, onBack: () -> Unit, onOpenRatingSettings: () -> Unit) {
    GoaScreen(title = "Meta", onBack = onBack) { padding ->
        MetaContent(state, onOpenRatingSettings = onOpenRatingSettings, modifier = Modifier.padding(padding))
    }
}
