package dev.quinton.goa.feature.board.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText

/** The Play tab's entries, as the screen opens them. */
data class PlayHomeActions(
    val onPlay: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onPuzzles: () -> Unit = {},
    val onBoards: () -> Unit = {},
    val onOnline: () -> Unit = {},
    val onTrivia: () -> Unit = {},
    /** Game night at the table: the Draft, which goes on to Match setup and the Timer. */
    val onGameNight: () -> Unit = {},
    /** Settings: the board style matters on this tab. */
    val onSettings: () -> Unit = {},
)

/**
 * The Play tab's first screen (ADR-055): the game in progress to resume, then the headline entry, Play vs computer
 * (ADR-035), and Game night (the table's own path: Draft, Match setup, Timer), Puzzles, the Board sandbox (ADR-033),
 * Online (ADR-056) and Card trivia (ADR-028), each a painted scene with its name and nothing more (show, don't tell).
 * The scenes share the screen's height, so the tab opens on a full page with all six entries in view (411 × 891).
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
                maxWidth >= WIDE -> 3
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
                    enabled = play.available,
                    onClick = actions.onPlay,
                    artHeight = leadArt,
                    artAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth(),
                )
                val puzzles = play.puzzles
                val tiles = listOf(
                    // The real game at the table, the way most evenings start: draft, set up, time it.
                    Tile(R.drawable.play_art_gamenight, "Game night", onClick = actions.onGameNight),
                    Tile(
                        R.drawable.play_art_puzzles,
                        "Puzzles",
                        enabled = puzzles != null,
                        onClick = actions.onPuzzles,
                        // Both parts of the puzzle list in one count; TalkBack hears them part by part.
                        state = puzzles?.let(::puzzlesSolved),
                        badge = puzzles?.let { p -> { SolvedBadge(p.solved, p.total) } },
                    ),
                    Tile(R.drawable.play_art_sandbox, "Board sandbox", onClick = actions.onBoards),
                    Tile(R.drawable.play_art_online, "Online", onClick = actions.onOnline),
                    Tile(R.drawable.play_art_trivia, "Card trivia", onClick = actions.onTrivia),
                )
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        row.forEach { tile ->
                            ArtPlate(
                                art = tile.art,
                                title = tile.title,
                                enabled = tile.enabled,
                                onClick = tile.onClick,
                                state = tile.state,
                                badge = tile.badge,
                                artHeight = tileArt,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "1 of 50 positions, 1 of 22 rounds solved": the badge's count, part by part, for TalkBack. */
private fun puzzlesSolved(p: PuzzleHubUi): String = listOfNotNull(
    PuzzleText.partCount(p.positionsSolved, p.positionsTotal, rounds = false),
    PuzzleText.partCount(p.roundsSolved, p.roundsTotal, rounds = true).takeIf { p.roundsTotal > 0 },
).joinToString(", ") + " solved"

private class Tile(
    val art: Int,
    val title: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val state: String? = null,
    val badge: (@Composable () -> Unit)? = null,
)

private val WIDE: Dp = 720.dp
private val NARROW: Dp = 340.dp
private const val LARGE_TEXT = 1.5f
private const val LEAD_SHARE = 0.26f
private val LEAD_MIN: Dp = 150.dp
private val LEAD_MAX: Dp = 280.dp
private const val TILE_SHARE = 0.2f
private val TILE_MIN: Dp = 112.dp
private val TILE_MAX: Dp = 220.dp
