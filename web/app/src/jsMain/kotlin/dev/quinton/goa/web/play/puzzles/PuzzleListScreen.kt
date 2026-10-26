package dev.quinton.goa.web.play.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.puzzles.PuzzleFilters
import dev.quinton.goa.feature.board.play.puzzles.PuzzleListAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzleListUiState
import dev.quinton.goa.feature.board.play.puzzles.PuzzleRowUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText
import dev.quinton.goa.feature.board.play.puzzles.after
import dev.quinton.goa.feature.board.play.puzzles.puzzleList
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.RouteLink
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The list's filters while its page is in the history (Back returns to them). */
private class ListFilters {
    var filters by mutableStateOf(PuzzleFilters())
}

/**
 * The puzzles (PRODUCT §10), as on the phone: Continue with the first unsolved one at the top and
 * "N of M solved", difficulty and theme filters, then every puzzle by number with its title, theme,
 * difficulty (hexes and words), heroes and progress. Reset progress is in the menu, confirmed.
 */
@Composable
fun PuzzleListScreen() {
    val services = LocalServices.current
    val data = services.data
    val scope = rememberCoroutineScope()
    val retained = rememberRetained("puzzle-filters") { ListFilters() }
    var menu by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val loaded = rememberLoad(Unit) {
        val book = (data.play.engine.call(EngineRequest.PuzzleList) as? EngineResponse.Puzzles)?.puzzles
            ?: error("The puzzle book couldn't be read")
        book to data.content.catalog.catalog.first().boardHeroes()
    }
    val progress by remember { data.play.puzzleProgress.progress.catch { emit(emptyMap()) } }
        .collectAsState(emptyMap<String, PuzzleProgress>())
    val state = (loaded.state as? dev.quinton.goa.web.ui.Load.Ready)?.value?.let { (book, heroes) ->
        puzzleList(book, heroes, progress, retained.filters)
    }
    val ready = state as? PuzzleListUiState.Ready
    Screen("Puzzles", actions = {
        if (ready != null) IconButton("more", "More options", { menu = true })
    }) {
        LoadContent(loaded, "Couldn't open the puzzles") { _ ->
            when (state) {
                PuzzleListUiState.Empty, null -> EmptyState(
                    "puzzle",
                    "No puzzles yet",
                    "Puzzles appear as the heroes they use become playable.",
                )

                PuzzleListUiState.Loading -> Unit

                is PuzzleListUiState.Ready -> PuzzleList(state) { retained.filters = retained.filters.after(it) }
            }
        }
    }
    if (menu && ready != null) {
        BottomSheet("Puzzles", onDismiss = { menu = false }) {
            GoaCard(flush = true) {
                RowList {
                    val any = ready.rows.any { it.progress.tried } || ready.solved > 0
                    ListRow(
                        "Reset progress",
                        subtitle = if (any) "Every puzzle back to unsolved and untried" else "Nothing to reset yet",
                        leadingIcon = "restart",
                        onClick = if (any) {
                            {
                                menu = false
                                resetting = true
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
    if (resetting) {
        AlertDialog(
            title = "Reset puzzle progress?",
            onDismiss = { resetting = false },
            buttons = {
                GoaButton("Cancel", { resetting = false }, ButtonKind.TEXT)
                GoaButton("Reset", {
                    resetting = false
                    scope.launch { runCatching { data.play.puzzleProgress.reset() } }
                }, ButtonKind.DANGER)
            },
        ) {
            Text("Every puzzle goes back to unsolved and untried in this browser. This can't be undone.")
        }
    }
}

@Composable
private fun PuzzleList(state: PuzzleListUiState.Ready, onAction: (PuzzleListAction) -> Unit) {
    Div({ classes("goa-stack") }) {
        ContinueCard(state)
        if (state.difficulties.size > 1 || state.themes.size > 1) {
            ChipRow(wrap = true) {
                if (state.difficulties.size > 1) {
                    state.difficulties.forEach { d ->
                        Chip(PuzzleText.difficulty(d), d in state.filters.difficulties, {
                            onAction(PuzzleListAction.ToggleDifficulty(d))
                        })
                    }
                }
                if (state.themes.size > 1) {
                    state.themes.forEach { theme ->
                        Chip(theme, theme in state.filters.themes, { onAction(PuzzleListAction.ToggleTheme(theme)) })
                    }
                }
            }
        }
        SectionHeader("All puzzles")
        if (state.rows.isEmpty()) {
            Div({ classes("goa-puzzle-none") }) {
                P({ classes("t-body-medium", "c-variant") }) { Text("No puzzle matches these filters.") }
                GoaButton("Show every puzzle", { onAction(PuzzleListAction.ClearFilters) }, ButtonKind.TEXT)
            }
        }
        Ul({ classes("goa-puzzle-list") }) {
            state.rows.forEach { row -> Li { PuzzleRow(row) } }
        }
    }
}

/** The next unsolved puzzle with Continue (or Start), and how many are solved; all solved says so. */
@Composable
private fun ContinueCard(state: PuzzleListUiState.Ready) {
    val next = state.next
    Div({
        classes("goa-puzzle-continue")
        if (next == null) classes("all")
    }) {
        if (next == null) SolvedSeal(size = 48)
        Div({ classes("goa-puzzle-continue-text") }) {
            P({ classes("t-overline") }) { Text("${state.solved} of ${state.total} solved") }
            if (next != null) {
                H3({ classes("t-title-medium") }) { Text("${next.info.number}. ${next.info.title}") }
                P({ classes("t-body-small") }) { Text(next.info.goal.text) }
            } else {
                H3({ classes("t-title-medium") }) { Text("Every puzzle solved") }
                P({ classes("t-body-small") }) {
                    Text("Replay any of them below; more arrive as heroes become playable.")
                }
            }
        }
        if (next != null) {
            RouteLink(Route.Puzzle(next.info.id), { classes("goa-button", "filled") }) {
                Icon("play", IconSize.SMALL)
                Text(if (next.progress.tried) "Continue" else "Start")
            }
        }
    }
}

/** One puzzle: its number in a bronze ring, title, theme, difficulty, heroes, progress. */
@Composable
private fun PuzzleRow(row: PuzzleRowUi) {
    val info = row.info
    val progress = row.progress
    val status = when {
        progress.solved -> "Solved"
        progress.attempts > 0 -> "Tried ${BoardText.count(progress.attempts, "time")}"
        else -> "Not tried yet"
    }
    val extras = listOfNotNull(
        progress.nudgesUsed.takeIf { it > 0 }?.let { BoardText.count(it, "nudge") },
        "Solution seen".takeIf { progress.solutionShown },
    )
    val spoken = listOf(
        "Puzzle ${info.number}: ${info.title}",
        info.theme,
        PuzzleText.difficulty(info.difficulty),
        PuzzleText.names(row.heroes.map { it.name }),
        status,
    ).plus(extras).joinToString(". ")
    RouteLink(Route.Puzzle(info.id), {
        classes("goa-puzzle-row")
        if (progress.solved) classes("solved")
        attr("aria-label", spoken)
    }) {
        NumberMark(info.number, progress.solved)
        Span({ classes("goa-puzzle-row-text") }) {
            Span({ classes("t-title-medium") }) { Text(info.title) }
            Span({ classes("t-body-small", "c-variant") }) { Text(info.theme) }
            Span({ classes("goa-puzzle-row-meta") }) {
                DifficultyMark(info.difficulty)
                Span({ classes("goa-puzzle-heroes") }) {
                    row.heroes.forEach { hero ->
                        val portrait = hero.portrait
                        if (portrait != null) {
                            Img(portrait, alt = "") {
                                classes("goa-puzzle-hero")
                                attr("loading", "lazy")
                                attr("width", "24")
                                attr("height", "24")
                            }
                        }
                    }
                }
            }
            Span({ classes("goa-puzzle-status", "t-label-large") }) {
                if (progress.solved) Icon("check", IconSize.SMALL)
                Span({ if (progress.solved) classes("solved") }) { Text(status) }
                if (extras.isNotEmpty()) {
                    Span({ classes("t-label-medium", "c-variant") }) {
                        if (progress.nudgesUsed > 0) Icon("lightbulb", IconSize.SMALL)
                        Text(extras.joinToString(" · "))
                    }
                }
            }
        }
    }
}
