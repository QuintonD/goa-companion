package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.review.ReviewButton
import dev.quinton.goa.feature.board.play.review.ReviewOpenEffect
import dev.quinton.goa.feature.board.play.review.ReviewSheet
import dev.quinton.goa.feature.board.play.table.CardDetail
import dev.quinton.goa.feature.board.play.table.DecisionPanel
import dev.quinton.goa.feature.board.play.table.EventLog
import dev.quinton.goa.feature.board.play.table.GameTable
import dev.quinton.goa.feature.board.play.table.HeroDetail
import dev.quinton.goa.feature.board.play.table.PeekConfirm
import dev.quinton.goa.feature.board.play.table.RecentEvents
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.YourHand
import dev.quinton.goa.feature.board.ui.BoardScaffold

/**
 * One puzzle (PRODUCT §10): the game table with the goal floating at the top; in the panel the
 * puzzle's name and difficulty, "What you know" (folds after the first move), a nudge when there is
 * one, then the question. The other side's replies are narrated in the panel; a miss explains
 * itself gently with Try again from before that move, Restart and Show solution; solving shows the
 * seal, then why it works. The solution is replayed on the board step by step. [onTap] reports
 * whether a tap was an option (otherwise a tap on a hero opens its status).
 */
@Composable
fun PuzzleScreen(
    state: PuzzleUiState,
    onAction: (PuzzleAction) -> Unit,
    onTap: (TableTap) -> Boolean,
    onNext: (String) -> Unit,
    onList: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSheet: PlaySheet? = null,
    initialPanelHidden: Boolean = false,
) {
    val puzzle = (state as? PuzzleUiState.Ready)?.puzzle
    var sheet by remember { mutableStateOf(initialSheet) }
    var menu by remember { mutableStateOf(false) }
    val viewport = remember { MapViewport() }
    KeepScreenOn(puzzle != null)
    BoardScaffold(
        title = puzzle?.let { "Puzzle ${it.info.number}" } ?: "Puzzle",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (puzzle != null) {
                ReviewButton(onClick = { sheet = PlaySheet.Review() })
                IconButton(onClick = { sheet = PlaySheet.Log }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Puzzle log")
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    PuzzleMenu(puzzle, expanded = menu, onDismiss = { menu = false }, onAction = onAction)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PuzzleUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PuzzleUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = if (state.why ==
                        NoPuzzle.MISSING
                    ) {
                        "This puzzle isn't in this build"
                    } else {
                        "This puzzle can't be set up"
                    },
                    message = if (state.why == NoPuzzle.MISSING) {
                        "Its heroes may not be playable yet. Pick another from the list."
                    } else {
                        "The rules engine couldn't build its position. Pick another from the list."
                    },
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Button(onClick = onList, modifier = Modifier.heightIn(min = 48.dp)) { Text("All puzzles") }
                    },
                )

                is PuzzleUiState.Ready -> {
                    val p = state.puzzle
                    GameTable(
                        table = p.table,
                        viewport = viewport,
                        onTap = { tap ->
                            if (!onTap(tap) && tap is TableTap.Piece && p.table.heroView(tap.pieceId) != null) {
                                sheet = PlaySheet.Hero(tap.pieceId)
                            }
                        },
                        onHero = { sheet = PlaySheet.Hero(it) },
                        onFlip = { onAction(PuzzleAction.Flip) },
                        banner = { GoalBanner(p.info.goal.text, p.playing) },
                        peek = puzzlePeek(p),
                        peekTrailing = {
                            if (p.phase == PuzzlePhase.SOLVING && p.replay == null) {
                                PeekConfirm(p.table, p.busy) { onAction(PuzzleAction.Answer(it)) }
                            }
                        },
                        initialPanelHidden = initialPanelHidden,
                    ) {
                        PuzzlePanel(p, onAction, onNext, onList, onCard = { sheet = PlaySheet.Card(it.id) })
                    }
                    // TalkBack hears what just happened.
                    p.announcement?.let { said ->
                        Box(
                            Modifier.size(1.dp).semantics {
                                contentDescription = said.text
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                }
            }
        }
    }
    ReviewOpenEffect(sheet) { onAction(PuzzleAction.Review(it)) }
    if (puzzle != null) {
        when (val open = sheet) {
            PlaySheet.Log -> BoardSheet(onDismiss = { sheet = null }) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle("Puzzle log", "Every event, newest at the bottom.", onDone = { sheet = null })
                    EventLog(puzzle.log, Modifier.weight(1f))
                }
            }

            is PlaySheet.Hero -> BoardSheet(onDismiss = { sheet = null }) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle(puzzle.table.hero(open.heroId).name, "Open information (p.4)", onDone = { sheet = null })
                    HeroDetail(puzzle.table, open.heroId, onCard = { sheet = PlaySheet.Card(it.id, back = open) })
                }
            }

            is PlaySheet.Card -> BoardSheet(onDismiss = { sheet = null }) {
                val card = puzzle.table.card(open.cardId)
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle(card?.name ?: "Card", null, onDone = { sheet = open.back })
                    if (card != null) CardDetail(card)
                }
            }

            is PlaySheet.Review ->
                ReviewSheet(puzzle.table, puzzle.review, open, {
                    sheet = it
                }, { onAction(PuzzleAction.Review(it)) }, puzzle = true)

            null -> Unit
        }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun PuzzleMenu(puzzle: PuzzleUi, expanded: Boolean, onDismiss: () -> Unit, onAction: (PuzzleAction) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (puzzle.nudgeLevel == 0) "Nudge me" else "Another nudge") },
            leadingIcon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
            enabled = puzzle.canNudge && puzzle.replay == null && puzzle.phase != PuzzlePhase.SOLVED,
            onClick = {
                onDismiss()
                onAction(PuzzleAction.Nudge)
            },
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text("Nudges")
                    Text(
                        if (puzzle.nudgesOn) "When you're stuck" else "Only when you ask",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Spacer(Modifier.size(24.dp)) },
            trailingIcon = { Switch(checked = puzzle.nudgesOn, onCheckedChange = null) },
            onClick = { onAction(PuzzleAction.SetNudges(!puzzle.nudgesOn)) },
            modifier = Modifier.semantics { stateDescription = if (puzzle.nudgesOn) "On" else "Off" },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Show solution") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
            enabled = puzzle.replay == null && !puzzle.busy,
            onClick = {
                onDismiss()
                onAction(PuzzleAction.ShowSolution)
            },
        )
        DropdownMenuItem(
            text = { Text("Restart") },
            leadingIcon = { Icon(Icons.Outlined.Replay, contentDescription = null) },
            enabled = !puzzle.busy,
            onClick = {
                onDismiss()
                onAction(PuzzleAction.Restart)
            },
        )
        DropdownMenuItem(
            text = { Text("Flip the board") },
            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(PuzzleAction.Flip)
            },
        )
    }
}

/** The panel's content for where the puzzle stands. */
@Composable
private fun ColumnScope.PuzzlePanel(
    puzzle: PuzzleUi,
    onAction: (PuzzleAction) -> Unit,
    onNext: (String) -> Unit,
    onList: () -> Unit,
    onCard: (Card) -> Unit,
) {
    val replay = puzzle.replay
    when {
        replay != null -> ReplayPanel(puzzle, replay, onAction, onCard)

        puzzle.phase == PuzzlePhase.SOLVED -> SolvedPanel(puzzle, onAction, onNext, onList)

        puzzle.phase == PuzzlePhase.REPLYING -> puzzle.replying?.let { Replies(it) }

        puzzle.phase == PuzzlePhase.MISSED -> {
            MissCard(puzzle, onAction)
            puzzle.nudge?.let { Nudge(it, onAction) }
            RecentEvents(puzzle.recent)
        }

        else -> {
            PuzzleHeader(puzzle.info, puzzle.total)
            if (puzzle.info.intro.isNotBlank()) Intro(puzzle.info.intro, puzzle.introOpen, onAction)
            puzzle.nudge?.let { Nudge(it, onAction) }
            DecisionPanel(
                puzzle.table,
                onSelect = { onAction(PuzzleAction.Select(it)) },
                onAnswer = { onAction(PuzzleAction.Answer(it)) },
                onCard = onCard,
                busy = puzzle.busy,
            )
            RecentEvents(puzzle.recent.takeLast(2), Modifier.padding(top = Spacing.xs))
            if (!puzzle.table.handDecision) YourHand(puzzle.table, onCard)
        }
    }
}

@Composable
private fun Nudge(nudge: NudgeUi, onAction: (PuzzleAction) -> Unit) {
    NudgeCard(nudge, onMore = { onAction(PuzzleAction.Nudge) }, onDismiss = { onAction(PuzzleAction.DismissNudge) })
}

/** "PUZZLE 3 OF 12 · ◆◆◇ Harder", the title and the theme. */
@Composable
private fun PuzzleHeader(info: PuzzleInfo, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PUZZLE ${info.number} OF $total",
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.sm))
            DifficultyMark(info.difficulty)
        }
        Text(info.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            info.theme,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the player knows that the board doesn't show; folds to one line once play has started. */
@Composable
private fun Intro(text: String, open: Boolean, onAction: (PuzzleAction) -> Unit) {
    Surface(
        onClick = { onAction(PuzzleAction.SetIntroOpen(!open)) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WHAT YOU KNOW",
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** The other side's replies, appearing one by one. */
@Composable
private fun Replies(replying: ReplyingUi) {
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(replying.title.uppercase(), style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
        RecentEvents(replying.lines)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.xs))
    }
}

/** A miss, gently: why, then Try again from before that move, Restart, Show solution. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissCard(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Not quite",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                puzzle.miss.orEmpty().removePrefix("Not quite — ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                "The goal: ${puzzle.info.goal.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onAction(PuzzleAction.TryAgain) },
                enabled = !puzzle.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Try again from before that move", textAlign = TextAlign.Center)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                OutlinedButton(
                    onClick = { onAction(PuzzleAction.Restart) },
                    enabled = !puzzle.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Restart") }
                TextButton(
                    onClick = { onAction(PuzzleAction.ShowSolution) },
                    enabled = !puzzle.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Show solution") }
            }
        }
    }
}

/** Solved: the seal, the record, then why it works with its rules; replay, next, or the list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SolvedPanel(
    puzzle: PuzzleUi,
    onAction: (PuzzleAction) -> Unit,
    onNext: (String) -> Unit,
    onList: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            SolvedSeal()
            Spacer(Modifier.width(Spacing.md))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    "Solved",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    puzzle.info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    PuzzleText.record(puzzle.attempts, puzzle.nudgesUsed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    Explanation(puzzle.info)
    Button(
        onClick = { onAction(PuzzleAction.ShowSolution) },
        enabled = !puzzle.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text("Replay the solution") }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val next = puzzle.next
        if (next != null) {
            OutlinedButton(onClick = { onNext(next) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Next puzzle")
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        TextButton(onClick = onList, modifier = Modifier.heightIn(min = 48.dp)) { Text("All puzzles") }
        TextButton(onClick = { onAction(PuzzleAction.Restart) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Play it again")
        }
    }
}

/** Why it works, in the puzzle's words, and the rulebook pages it leans on. */
@Composable
private fun Explanation(info: PuzzleInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "WHY IT WORKS",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(info.explanation, style = MaterialTheme.typography.bodyMedium)
        if (info.rules.isNotEmpty()) {
            Text(
                "RULES",
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
            )
            info.rules.forEach { rule ->
                Row {
                    Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(rule, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** The solution on the board, step by step: Back / Next, the move outlined, what happened; then why. */
@Composable
private fun ReplayPanel(puzzle: PuzzleUi, replay: ReplayUi, onAction: (PuzzleAction) -> Unit, onCard: (Card) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(PuzzleAction.ReplayBack) }, enabled = !replay.first) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Step back")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SOLUTION · STEP ${replay.index + 1} OF ${replay.count}",
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = { onAction(PuzzleAction.ReplayNext) }, enabled = !replay.last) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next step")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val card = (puzzle.table.selected as? Choice.Card)?.let { puzzle.table.card(it.cardId) }
        if (card != null) {
            Surface(onClick = { onCard(card) }, shape = RoundedCornerShape(4.dp)) { TableCard(card, width = 40.dp) }
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            replay.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (replay.solved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (replay.lines.isNotEmpty()) {
        Text(
            if (replay.first) "BEFORE IT" else "WHAT HAPPENED",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RecentEvents(replay.lines)
    } else if (replay.first && puzzle.info.intro.isNotBlank()) {
        Text(
            puzzle.info.intro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(
            onClick = { onAction(PuzzleAction.CloseReplay) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(if (replay.afterSolve) "Done" else "Back to the puzzle") }
        Button(
            onClick = { onAction(PuzzleAction.ReplayNext) },
            enabled = !replay.last,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("Next step") }
    }
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = GoaTheme.colors.engravedBorder)
    Explanation(puzzle.info)
}

private const val SHEET_HEIGHT = 0.9f
