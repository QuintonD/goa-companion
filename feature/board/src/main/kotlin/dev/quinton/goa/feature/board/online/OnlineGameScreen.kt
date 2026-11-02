package dev.quinton.goa.feature.board.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.ui.BoardScaffold

/**
 * An online game (ADR-056): before the first table, why there is none; then the existing stateless [GameScreen]
 * with what the driver makes of the server's frames, and the banner while the connection is away.
 */
@Composable
fun OnlineGameScreen(
    state: OnlineState,
    onAction: (GameAction) -> Unit,
    onTap: (TableTap) -> Boolean,
    onLeave: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is OnlineState.Connecting -> Waiting(state.note, onBack, modifier)

        is OnlineState.Refused -> Stopped(OnlineWords.refusal(state.reason, state.retryAfterSeconds), onBack, modifier)

        is OnlineState.Reconnecting -> Table(state.game, onAction, onTap, onLeave, onDone, onBack, modifier, true)

        is OnlineState.InGame -> Table(state.game, onAction, onTap, onLeave, onDone, onBack, modifier, false)
    }
}

@Suppress("LongParameterList")
@Composable
private fun Table(
    game: GameUiState,
    onAction: (GameAction) -> Unit,
    onTap: (TableTap) -> Boolean,
    onLeave: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    reconnecting: Boolean,
) {
    // The one way the driver has no table to show: a map this build doesn't have.
    if (game is GameUiState.Unavailable) return Stopped(OnlineWords.refusal(Refusal.UPDATE_APP), onBack, modifier)
    GameScreen(
        state = game,
        onAction = onAction,
        onTap = onTap,
        onDelete = onLeave,
        onNewGame = onDone,
        onDone = onDone,
        onBack = onBack,
        modifier = modifier,
        banner = { if (reconnecting) ReconnectingBanner() },
    )
}

@Composable
private fun Waiting(note: String?, onBack: () -> Unit, modifier: Modifier) {
    BoardScaffold(title = "Online game", onBack = onBack, modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (note == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Connecting to your game…",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Words(Icons.Outlined.CloudOff, "Not connected", note, onBack)
            }
        }
    }
}

@Composable
private fun Stopped(words: String, onBack: () -> Unit, modifier: Modifier) {
    BoardScaffold(title = "Online game", onBack = onBack, modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Words(Icons.Outlined.Public, "This game can't open", words, onBack)
        }
    }
}

@Composable
private fun Words(icon: ImageVector, title: String, words: String, onBack: () -> Unit) {
    EmptyState(
        icon = icon,
        title = title,
        message = words,
        action = { GoaButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
    )
}
