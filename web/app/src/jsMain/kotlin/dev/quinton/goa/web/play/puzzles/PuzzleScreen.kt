package dev.quinton.goa.web.play.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.play.puzzles.NoPuzzle
import dev.quinton.goa.feature.board.play.puzzles.PuzzleAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzlePhase
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUiState
import dev.quinton.goa.feature.board.play.puzzles.puzzlePeek
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.ui.PlayScaffold
import dev.quinton.goa.web.play.ui.landscapeQuery
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.LinearProgress
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The sheets a puzzle opens over its table. */
private sealed interface PuzzleSheet {
    data object Menu : PuzzleSheet

    data object Log : PuzzleSheet

    data object Review : PuzzleSheet

    data class Hero(val heroId: String) : PuzzleSheet
}

/**
 * One puzzle (PRODUCT §10), as on the phone: the game table with the goal under the top row; in the
 * panel the puzzle's name and difficulty, "What you know" (folds after the first move), a nudge when
 * there is one, then the question. The other side's replies are narrated in the panel; a miss
 * explains itself gently with Try again from before that move, Restart and Show solution; solving
 * shows the seal, then why it works. The solution is replayed on the board step by step.
 */
@Composable
fun PuzzleScreen(puzzleId: String) {
    val services = LocalServices.current
    val data = services.data
    val scope = rememberCoroutineScope()
    val controller = remember(puzzleId) {
        PuzzleController(
            puzzleId = puzzleId,
            scope = scope,
            engine = data.play.engine,
            catalog = { data.content.catalog.catalog.first() },
            maps = { data.play.boardMaps.maps.first() },
            settings = data.content.settings,
            progressRepository = data.play.puzzleProgress,
            clock = data.clock,
        )
    }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.dispose() }
    }
    when (val state = controller.state) {
        PuzzleUiState.Loading -> Screen("Puzzle") { LoadingState("Setting up the puzzle") }

        is PuzzleUiState.Unavailable -> Screen("Puzzle") {
            EmptyState(
                "puzzle",
                if (state.why ==
                    NoPuzzle.MISSING
                ) {
                    "This puzzle isn't in this version"
                } else {
                    "This puzzle can't be set up"
                },
                if (state.why == NoPuzzle.MISSING) {
                    "Its heroes may not be playable yet. Pick another from the list."
                } else {
                    "The rules engine couldn't build its position. Pick another from the list."
                },
            ) { GoaButton("All puzzles", { services.router.navigate(Route.Puzzles, replace = true) }) }
        }

        is PuzzleUiState.Ready -> PuzzleTable(state.puzzle, controller)
    }
}

@Composable
private fun PuzzleTable(puzzle: PuzzleUi, controller: PuzzleController) {
    val router = LocalServices.current.router
    var sheet by remember { mutableStateOf<PuzzleSheet?>(null) }
    var shown by remember { mutableStateOf(true) }
    val camera = remember { MapCamera() }
    val landscape = landscapeQuery()
    val table = puzzle.table
    val layout = remember(table.map, table.bottomTeam, landscape) {
        HexLayout(table.map, if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT, table.bottomTeam)
    }
    val onAction = controller::onAction
    LaunchedEffect(sheet is PuzzleSheet.Review) {
        onAction(PuzzleAction.Review(ReviewAction.Open(sheet is PuzzleSheet.Review)))
    }
    PlayScaffold(
        title = "Puzzle ${puzzle.info.number}",
        actions = {
            IconButton("article", "Game state", { sheet = PuzzleSheet.Review })
            IconButton("list", "Puzzle log", { sheet = PuzzleSheet.Log })
            IconButton("more", "More options", { sheet = PuzzleSheet.Menu })
        },
        panelShown = shown,
        onPanelShown = { shown = it },
        peek = puzzlePeek(puzzle),
        peekActions = {
            if (puzzle.phase == PuzzlePhase.SOLVING && puzzle.replay == null) {
                PeekConfirm(puzzle) { onAction(PuzzleAction.Answer(it)) }
            }
        },
        top = {
            TableTop(table) { sheet = PuzzleSheet.Hero(it) }
            if (shown) GoalBanner(puzzle.info.goal.text, puzzle.playing, compact = landscape)
        },
        map = {
            TableMap(
                table = table,
                layout = layout,
                camera = camera,
                onTap = { tap ->
                    val chose = controller.tap(tap)
                    if (!chose && tap is TableTap.Piece && table.heroView(tap.pieceId) != null) {
                        sheet = PuzzleSheet.Hero(tap.pieceId)
                    }
                    chose
                },
                onFit = {},
            )
        },
        panel = { PuzzlePanel(puzzle, onAction) },
        panelLabel = "Puzzle",
    )
    puzzle.announcement?.let { said ->
        Div({
            classes("goa-visually-hidden")
            attr("aria-live", "polite")
        }) { Text(said.text) }
    }
    when (val open = sheet) {
        PuzzleSheet.Menu -> PuzzleMenu(puzzle, onAction, onDismiss = { sheet = null })
        PuzzleSheet.Log -> LogSheet(puzzle) { sheet = null }
        PuzzleSheet.Review -> ReviewSheetHost(puzzle, onAction) { sheet = null }
        is PuzzleSheet.Hero -> HeroSheetHost(puzzle, open.heroId) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun PuzzleMenu(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Puzzle ${puzzle.info.number}", onDismiss = onDismiss) {
        GoaCard(flush = true) {
            RowList {
                val canNudge = puzzle.canNudge && puzzle.replay == null && puzzle.phase != PuzzlePhase.SOLVED
                ListRow(
                    if (puzzle.nudgeLevel == 0) "Nudge me" else "Another nudge",
                    leadingIcon = "lightbulb",
                    onClick = if (canNudge) {
                        {
                            onDismiss()
                            onAction(PuzzleAction.Nudge)
                        }
                    } else {
                        null
                    },
                )
                ListRow(
                    "Nudges",
                    subtitle = if (puzzle.nudgesOn) "When you're stuck" else "Only when you ask",
                    leadingIcon = "info",
                    trailing = if (puzzle.nudgesOn) "On" else "Off",
                    onClick = { onAction(PuzzleAction.SetNudges(!puzzle.nudgesOn)) },
                )
                ListRow(
                    "Show solution",
                    leadingIcon = "rules",
                    onClick = if (puzzle.replay == null && !puzzle.busy) {
                        {
                            onDismiss()
                            onAction(PuzzleAction.ShowSolution)
                        }
                    } else {
                        null
                    },
                )
                ListRow(
                    "Restart",
                    leadingIcon = "restart",
                    onClick = if (!puzzle.busy) {
                        {
                            onDismiss()
                            onAction(PuzzleAction.Restart)
                        }
                    } else {
                        null
                    },
                )
                ListRow("Flip the board", leadingIcon = "flip", onClick = {
                    onDismiss()
                    onAction(PuzzleAction.Flip)
                })
            }
        }
    }
}

/** The panel's content for where the puzzle stands. */
@Composable
private fun PuzzlePanel(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    val replay = puzzle.replay
    Div({ classes("goa-puzzle-panel") }) {
        when {
            replay != null -> ReplayPanel(puzzle, onAction)

            puzzle.phase == PuzzlePhase.SOLVED -> SolvedPanel(puzzle, onAction)

            puzzle.phase == PuzzlePhase.REPLYING -> puzzle.replying?.let { replying ->
                Div({ attr("aria-live", "polite") }) {
                    P({ classes("t-overline", "c-primary") }) { Text(replying.title) }
                    RecentLines(replying.lines)
                    LinearProgress(null, "The other side is answering")
                }
            }

            puzzle.phase == PuzzlePhase.MISSED -> {
                MissCard(puzzle, onAction)
                puzzle.nudge?.let {
                    NudgeCard(it, { onAction(PuzzleAction.Nudge) }, { onAction(PuzzleAction.DismissNudge) })
                }
                RecentLines(puzzle.recent)
            }

            else -> {
                PuzzleHeader(puzzle)
                if (puzzle.info.intro.isNotBlank()) Intro(puzzle, onAction)
                puzzle.nudge?.let {
                    NudgeCard(it, { onAction(PuzzleAction.Nudge) }, { onAction(PuzzleAction.DismissNudge) })
                }
                TableQuestion(
                    puzzle.table,
                    busy = puzzle.busy,
                    onSelect = { onAction(PuzzleAction.Select(it)) },
                    onAnswer = { onAction(PuzzleAction.Answer(it)) },
                )
                RecentLines(puzzle.recent.takeLast(2))
            }
        }
    }
}

/** "PUZZLE 3 OF 12 · ⬢⬢⬡ Harder", the title and the theme. */
@Composable
private fun PuzzleHeader(puzzle: PuzzleUi) {
    Div({ classes("goa-puzzle-header") }) {
        Div({ classes("goa-inline") }) {
            Span({ classes("t-overline", "c-primary") }) { Text("Puzzle ${puzzle.info.number} of ${puzzle.total}") }
            DifficultyMark(puzzle.info.difficulty)
        }
        H2({ classes("t-title-large") }) { Text(puzzle.info.title) }
        P({ classes("t-label-medium", "c-variant") }) { Text(puzzle.info.theme) }
    }
}

/** What the player knows that the board doesn't show; folds to one line once play has started. */
@Composable
private fun Intro(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    Button({
        classes("goa-puzzle-intro")
        if (!puzzle.introOpen) classes("folded")
        attr("type", "button")
        attr("aria-expanded", puzzle.introOpen.toString())
        onClick { onAction(PuzzleAction.SetIntroOpen(!puzzle.introOpen)) }
    }) {
        Span({ classes("t-overline", "c-primary") }) { Text("What you know") }
        Span({ classes("t-body-medium", "goa-puzzle-intro-text") }) { Text(puzzle.info.intro) }
    }
}

/** A miss, gently: why, then Try again from before that move, Restart, Show solution. */
@Composable
private fun MissCard(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    Div({ classes("goa-puzzle-miss") }) {
        H2({ classes("t-title-large") }) { Text("Not quite") }
        P({
            classes("t-body-large")
            attr("aria-live", "polite")
        }) { Text(puzzle.miss.orEmpty().removePrefix("Not quite — ").replaceFirstChar { it.uppercase() }) }
        P({ classes("t-body-small", "c-variant") }) { Text("The goal: ${puzzle.info.goal.text}") }
        GoaButton(
            "Try again from before that move",
            { onAction(PuzzleAction.TryAgain) },
            icon = "undo",
            enabled = !puzzle.busy,
            block = true,
        )
        Div({ classes("goa-button-row", "start") }) {
            GoaButton("Restart", { onAction(PuzzleAction.Restart) }, ButtonKind.OUTLINED, enabled = !puzzle.busy)
            GoaButton("Show solution", { onAction(PuzzleAction.ShowSolution) }, ButtonKind.TEXT, enabled = !puzzle.busy)
        }
    }
}

/** Solved: the seal, the record, then why it works with its rules; replay, next, or the list. */
@Composable
private fun SolvedPanel(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    val router = LocalServices.current.router
    Div({ classes("goa-puzzle-solved") }) {
        SolvedSeal()
        Div({ attr("aria-live", "polite") }) {
            H2({ classes("t-headline-small") }) { Text("Solved") }
            P({ classes("t-title-small") }) { Text(puzzle.info.title) }
            P({ classes("t-label-large") }) { Text(PuzzleText.record(puzzle.attempts, puzzle.nudgesUsed)) }
        }
    }
    Explanation(puzzle.info)
    GoaButton("Replay the solution", { onAction(PuzzleAction.ShowSolution) }, enabled = !puzzle.busy, block = true)
    Div({ classes("goa-button-row", "start") }) {
        puzzle.next?.let { next ->
            GoaButton("Next puzzle", { router.navigate(Route.Puzzle(next), replace = true) }, ButtonKind.OUTLINED)
        }
        GoaButton("All puzzles", { router.navigate(Route.Puzzles, replace = true) }, ButtonKind.TEXT)
        GoaButton("Play it again", { onAction(PuzzleAction.Restart) }, ButtonKind.TEXT)
    }
}

/** The solution on the board, step by step: Back / Next, the move outlined, what happened; then why. */
@Composable
private fun ReplayPanel(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    val replay = puzzle.replay ?: return
    Div({ classes("goa-puzzle-replay-head") }) {
        IconButton("back", "Step back", { if (!replay.first) onAction(PuzzleAction.ReplayBack) })
        P({ classes("t-overline", "c-primary") }) { Text("Solution · step ${replay.index + 1} of ${replay.count}") }
        IconButton("chevron_right", "Next step", { if (!replay.last) onAction(PuzzleAction.ReplayNext) })
    }
    val card = (puzzle.table.selected as? Choice.Card)?.let { puzzle.table.card(it.cardId) }
    Div({ classes("goa-puzzle-replay-move") }) {
        card?.image?.let { image ->
            org.jetbrains.compose.web.dom.Img(image, alt = card.name) {
                classes("goa-puzzle-replay-card")
                attr("loading", "lazy")
            }
        }
        P({
            classes("t-title-medium")
            if (replay.solved) classes("c-primary")
            attr("aria-live", "polite")
        }) { Text(replay.title) }
    }
    if (replay.lines.isNotEmpty()) {
        P({ classes("t-overline", "c-variant") }) { Text(if (replay.first) "Before it" else "What happened") }
        RecentLines(replay.lines)
    } else if (replay.first && puzzle.info.intro.isNotBlank()) {
        P({ classes("t-body-small", "c-variant") }) { Text(puzzle.info.intro) }
    }
    Div({ classes("goa-button-row", "split") }) {
        GoaButton(if (replay.afterSolve) "Done" else "Back to the puzzle", {
            onAction(PuzzleAction.CloseReplay)
        }, ButtonKind.OUTLINED)
        GoaButton("Next step", { onAction(PuzzleAction.ReplayNext) }, enabled = !replay.last)
    }
    Explanation(puzzle.info)
}

/** The latest lines, newest last. */
@Composable
internal fun RecentLines(lines: List<String>) {
    if (lines.isEmpty()) return
    Ul({ classes("goa-puzzle-recent", "t-body-medium") }) {
        lines.forEachIndexed { i, line ->
            Li({ if (i == lines.lastIndex) classes("newest") }) { Text(line) }
        }
    }
}

@Composable
private fun LogSheet(puzzle: PuzzleUi, onDismiss: () -> Unit) {
    BottomSheet("Puzzle log", onDismiss = onDismiss) {
        P({ classes("t-body-small", "c-variant") }) { Text("Every event, newest at the bottom.") }
        Ul({ classes("goa-puzzle-log") }) {
            puzzle.log.forEach { line ->
                Li({ if (line.heading) classes("heading", "t-overline", "c-primary") else classes("t-body-medium") }) {
                    Text(line.text)
                }
            }
        }
    }
}

@Composable
private fun ReviewSheetHost(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Game state", onDismiss = onDismiss) {
        val review = puzzle.review
        if (review == null) {
            LoadingState("Reading the game so far")
        } else {
            InterimReview(puzzle, review) { onAction(PuzzleAction.Review(it)) }
        }
    }
}

@Composable
private fun HeroSheetHost(puzzle: PuzzleUi, heroId: String, onDismiss: () -> Unit) {
    BottomSheet(puzzle.table.hero(heroId).name, onDismiss = onDismiss) {
        InterimHeroDetail(puzzle.table, heroId)
    }
}

/** H3 helper for the interim parts. */
@Composable
internal fun Heading(text: String) {
    H3({ classes("t-section-head") }) { Text(text) }
}
