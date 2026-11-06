package dev.quinton.goa.feature.board.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.board.R

/** The Play tab's entries, as the screen opens them. */
data class PlayHomeActions(
    val onPlay: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onPuzzles: () -> Unit = {},
    val onBoards: () -> Unit = {},
    val onOnline: () -> Unit = {},
    val onTrivia: () -> Unit = {},
    /** Settings: the board style matters on this tab. */
    val onSettings: () -> Unit = {},
)

/**
 * The Play tab's first screen (ADR-055): the game in progress to resume, then the headline entry, Play vs computer
 * (ADR-035), and Puzzles, the Board sandbox (ADR-033), Online (ADR-056) and Card trivia (ADR-028), each a plate
 * with a painted scene. The plates share the screen's height, so the tab opens on a full page, not a short list.
 */
@Composable
fun PlayHomeScreen(state: PlayHomeUiState, actions: PlayHomeActions, modifier: Modifier = Modifier) {
    val ready = state as? PlayHomeUiState.Ready
    val play = ready?.play ?: PlayHubUi(available = true)
    GoaScreen(
        title = "Play",
        modifier = modifier,
        actions = { SettingsActionButton(actions.onSettings) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val fontScale = LocalDensity.current.fontScale
            val columns = when {
                maxWidth >= WIDE -> 4
                fontScale > LARGE_TEXT || maxWidth < NARROW -> 1
                else -> 2
            }
            // The scenes grow with the screen (within limits), so the plates fill it.
            val leadArt = (maxHeight * LEAD_SHARE).coerceIn(LEAD_MIN, LEAD_MAX)
            val tileArt = (maxHeight * TILE_SHARE).coerceIn(TILE_MIN, TILE_MAX)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                play.resume?.let { ResumeCard(it, ready?.now ?: 0L, actions.onResume) }
                ArtPlate(
                    art = R.drawable.play_art_computer,
                    title = "Play vs computer",
                    text = "The real game on the map: the app runs every rule, the computer plays at a strength " +
                        "from 1 to 10, and a coach can nudge.",
                    status = if (play.available) null else "Not available in this build",
                    enabled = play.available,
                    onClick = actions.onPlay,
                    artHeight = leadArt,
                    artAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth(),
                )
                val puzzles = play.puzzles
                val tiles = listOf(
                    Tile(
                        R.drawable.play_art_puzzles,
                        "Puzzles",
                        "Hard positions, gentle nudges, the rule behind each answer.",
                        puzzles?.let { "${it.solved} of ${it.total} solved" } ?: "Not available in this build",
                        enabled = puzzles != null,
                        onClick = actions.onPuzzles,
                    ),
                    Tile(
                        R.drawable.play_art_sandbox,
                        "Board sandbox",
                        "Any line-up on the real map: who can reach whom.",
                        onClick = actions.onBoards,
                    ),
                    Tile(
                        R.drawable.play_art_online,
                        "Online",
                        "Play people on their own phones. Needs a connection.",
                        onClick = actions.onOnline,
                    ),
                    Tile(
                        R.drawable.play_art_trivia,
                        "Card trivia",
                        "A quiz on the cards' numbers, with your group's leaderboard.",
                        onClick = actions.onTrivia,
                    ),
                )
                tiles.chunked(columns).forEach { row ->
                    // One height per row: the plates line up whatever their words.
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        row.forEach { tile ->
                            ArtPlate(
                                art = tile.art,
                                title = tile.title,
                                text = tile.text,
                                status = tile.status,
                                enabled = tile.enabled,
                                onClick = tile.onClick,
                                artHeight = tileArt,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class Tile(
    val art: Int,
    val title: String,
    val text: String,
    val status: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private val WIDE: Dp = 720.dp
private val NARROW: Dp = 340.dp
private const val LARGE_TEXT = 1.5f
private const val LEAD_SHARE = 0.25f
private val LEAD_MIN: Dp = 132.dp
private val LEAD_MAX: Dp = 220.dp
private const val TILE_SHARE = 0.17f
private val TILE_MIN: Dp = 92.dp
private val TILE_MAX: Dp = 150.dp
