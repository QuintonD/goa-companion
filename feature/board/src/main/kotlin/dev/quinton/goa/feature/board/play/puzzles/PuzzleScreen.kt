package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaProgressBar
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.scaledIconSize
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
import dev.quinton.goa.feature.board.play.table.BoardPeek
import dev.quinton.goa.feature.board.play.table.DecisionPanel
import dev.quinton.goa.feature.board.play.table.DecisionWho
import dev.quinton.goa.feature.board.play.table.EventLog
import dev.quinton.goa.feature.board.play.table.GameTable
import dev.quinton.goa.feature.board.play.table.HandTab
import dev.quinton.goa.feature.board.play.table.HeroDetail
import dev.quinton.goa.feature.board.play.table.MomentCard
import dev.quinton.goa.feature.board.play.table.PeekConfirm
import dev.quinton.goa.feature.board.play.table.RecentEvents
import dev.quinton.goa.feature.board.play.table.ResolutionFlowView
import dev.quinton.goa.feature.board.play.table.SpellbookSheet
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.spellbookUi
import dev.quinton.goa.feature.board.ui.BarLabelButton
import dev.quinton.goa.feature.board.ui.BoardScaffold
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateTone
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.cards.CardFocusHost
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState

/**
 * One puzzle (PRODUCT §10): the game table; in the panel the goal, the puzzle's name and difficulty on a small line,
 * "What you know" (open the first time, folded once played), a nudge when there is one, then the
 * question, which alone has the title plate. The other side's replies are narrated in the panel; a miss explains
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
    var handOpen by rememberSaveable { mutableStateOf(false) }
    val viewport = remember { MapViewport() }
    val focus = rememberCardFocusState()
    KeepScreenOn(puzzle != null)
    BoardScaffold(
        title = puzzle?.let { PuzzleText.short(it.info) } ?: "Puzzle",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (puzzle != null) {
                // Each control with its word under its glyph, as on the game's bar.
                ReviewButton(onClick = { sheet = PlaySheet.Review() })
                BarLabelButton(
                    "Log",
                    Icons.AutoMirrored.Outlined.FormatListBulleted,
                    onClick = { sheet = PlaySheet.Log },
                    description = "Puzzle log",
                )
                Box {
                    BarLabelButton("More", Icons.Outlined.MoreVert, onClick = {
                        menu = true
                    }, description = "More options")
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
                        GoaButton(onClick = onList, modifier = Modifier.heightIn(min = 48.dp)) { Text("All puzzles") }
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
                        peek = puzzlePeek(p),
                        peekTrailing = {
                            if (p.phase == PuzzlePhase.SOLVING && p.replay == null) {
                                PeekConfirm(p.table, p.busy) { onAction(PuzzleAction.Answer(it)) }
                            }
                        },
                        initialPanelHidden = initialPanelHidden,
                        focus = focus,
                        onSpellbook = { sheet = PlaySheet.Spellbook(it) },
                        panelTitle = if (p.asksOnPanel()) {
                            { DecisionWho(p.table) }
                        } else {
                            null
                        },
                        // A space or piece on the map, no nudge open: the panel peeks with the goal and the commit.
                        peekBody = if (p.asksOnPanel() && p.nudge == null && p.phase == PuzzlePhase.SOLVING) {
                            {
                                Text(
                                    p.info.goal.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                BoardPeek(p.table, { onAction(PuzzleAction.Answer(it)) }, p.busy)
                            }
                        } else {
                            null
                        },
                        // The hand while the question is asked: the panel's pinned tab.
                        panelFooter = if (p.handShown()) {
                            { HandTab(p.table, focus, handOpen, onOpenChange = { handOpen = it }) }
                        } else {
                            null
                        },
                    ) {
                        PuzzlePanel(p, onAction, onNext, onList, focus)
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
                    SheetTitle("Puzzle log", "The latest turn first; each turn in the order it happened.", onDone = {
                        sheet =
                            null
                    })
                    EventLog(puzzle.log, Modifier.weight(1f), table = puzzle.table)
                }
            }

            is PlaySheet.Hero -> BoardSheet(onDismiss = { sheet = null }) {
                HeroDetail(
                    puzzle.table,
                    open.heroId,
                    focus,
                    onDone = { sheet = null },
                    modifier = Modifier.fillMaxHeight(SHEET_HEIGHT),
                    onSpellbook = { sheet = PlaySheet.Spellbook(it) },
                )
            }

            is PlaySheet.Review ->
                ReviewSheet(puzzle.table, puzzle.review, open, {
                    sheet = it
                }, { onAction(PuzzleAction.Review(it)) }, focus, puzzle = true)

            is PlaySheet.Spellbook -> spellbookUi(puzzle.table, open.heroId)?.let { ui ->
                SpellbookSheet(ui, focus, onDismiss = { sheet = null })
            }

            null -> if (puzzle.introOpen) SetupSheet(puzzle) { onAction(PuzzleAction.SetIntroOpen(false)) }
        }
    }
    CardFocusHost(focus)
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
            trailingIcon = { GoaSwitch(checked = puzzle.nudgesOn, onCheckedChange = null) },
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

/** The panel holds the question (who is asked goes on its strip): not during a replay, a reply, or once it is over. */
private fun PuzzleUi.asksOnPanel(): Boolean = replay == null && table.decision != null &&
    phase != PuzzlePhase.SOLVED && phase != PuzzlePhase.REPLYING && phase != PuzzlePhase.MISSED

/** The hand shows (as the panel's tab) while the person's question is asked, not in a replay or once it is over. */
private fun PuzzleUi.handShown(): Boolean =
    replay == null && phase != PuzzlePhase.SOLVED && phase != PuzzlePhase.REPLYING && phase != PuzzlePhase.MISSED

/** The panel's content for where the puzzle stands. */
@Composable
private fun ColumnScope.PuzzlePanel(
    puzzle: PuzzleUi,
    onAction: (PuzzleAction) -> Unit,
    onNext: (String) -> Unit,
    onList: () -> Unit,
    focus: CardFocusState,
) {
    val replay = puzzle.replay
    when {
        replay != null -> ReplayPanel(puzzle, replay, onAction, focus)

        puzzle.phase == PuzzlePhase.SOLVED -> SolvedPanel(puzzle, onAction, onNext, onList)

        puzzle.phase == PuzzlePhase.REPLYING -> {
            GoalLine(puzzle.info.goal.text, puzzle.playing)
            puzzle.replying?.let { Replies(it) }
        }

        puzzle.phase == PuzzlePhase.MISSED -> {
            MissCard(puzzle, onAction)
            puzzle.nudge?.let { Nudge(it, onAction) }
            ResolutionFlowView(puzzle.flow, puzzle.table, focus)
        }

        else -> {
            // The decision first (both judges, round 2): the goal and the puzzle on two short lines with Setup,
            // which opens what you know in full in a sheet, so no fact is ever cut to make the choices fit.
            BriefHeader(puzzle, onOpen = { onAction(PuzzleAction.SetIntroOpen(true)) })
            puzzle.nudge?.let { Nudge(it, onAction) }
            // The person's own question first, the moment after it (as in the game).
            val moment = puzzle.table.moment
            val questionFirst = puzzle.table.asking
            if (moment != null && !questionFirst) MomentCard(moment, puzzle.table, focus = focus)
            DecisionPanel(
                puzzle.table,
                onSelect = { onAction(PuzzleAction.Select(it)) },
                onAnswer = { onAction(PuzzleAction.Answer(it)) },
                focus = focus,
                busy = puzzle.busy,
                who = false,
            )
            ResolutionFlowView(puzzle.flow, puzzle.table, focus, Modifier.padding(top = Spacing.xs))
            puzzle.round?.let {
                KnownPlaysStrip(it, focus, showNote = puzzle.attempts == 0 || puzzle.introOpen, onLater = { shown ->
                    onAction(PuzzleAction.SetLaterShown(shown))
                })
            }
            if (moment != null && questionFirst) MomentCard(moment, puzzle.table, focus = focus)
        }
    }
}

@Composable
private fun Nudge(nudge: NudgeUi, onAction: (PuzzleAction) -> Unit) {
    NudgeCard(nudge, onMore = { onAction(PuzzleAction.Nudge) }, onDismiss = { onAction(PuzzleAction.DismissNudge) })
}

/**
 * The panel's head while a question is asked: the goal (flag), then who you play, the puzzle and its difficulty on
 * one small line, and **Setup** at the end of the goal's row, which opens [SetupSheet]: what you know in full, never
 * cut mid-sentence to make room. A full round's turn heading follows (a polite live region).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefHeader(puzzle: PuzzleUi, onOpen: () -> Unit) {
    val info = puzzle.info
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalLine(info.goal.text, puzzle.playing, Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            SetupButton(info, onOpen)
        }
        FlowRow(
            Modifier.padding(start = scaledIconSize(20.dp, 1.4f) + Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${info.title} · ${PuzzleText.place(info, puzzle.total)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DifficultyMark(info.difficulty)
        }
        puzzle.round?.let {
            Text(
                it.heading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** "Setup": opens what you know in full. A steel button with the book, its words, a 48 dp target. */
@Composable
private fun SetupButton(info: PuzzleInfo, onOpen: () -> Unit) {
    PlateButton(
        onClick = onOpen,
        tone = PlateTone.STEEL,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Read the full setup: what you know. ${info.theme}" },
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(scaledIconSize(18.dp, 1.3f)))
        Spacer(Modifier.width(6.dp))
        Text("Setup", maxLines = 1, softWrap = false)
    }
}

/**
 * The puzzle's setup in full (what the player knows that the board doesn't show, in the puzzle's own words), one
 * sentence a line to scan, under its theme; then the goal and who you play. Opened from Setup in the panel.
 */
@Composable
private fun SetupSheet(puzzle: PuzzleUi, onDismiss: () -> Unit) {
    val info = puzzle.info
    BoardSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(SETUP_HEIGHT)) {
            SheetTitle("Setup", "${PuzzleText.place(info, puzzle.total)} · ${info.title}", onDone = onDismiss)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                GoalLine(info.goal.text, puzzle.playing)
                if (info.intro.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            "WHAT YOU KNOW",
                            style = GoaTextStyles.overline,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            info.theme,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PuzzleText.sentences(info.intro).forEach { line ->
                            Row(Modifier.semantics(mergeDescendants = true) {}) {
                                Text(
                                    "•",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(line, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
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
        GoaProgressBar(null, Modifier.fillMaxWidth().padding(top = Spacing.xs))
    }
}

/** A miss, gently: why, then Try again from before that move, Restart, Show solution. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissCard(puzzle: PuzzleUi, onAction: (PuzzleAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PlateWell,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TitlePlate("Not quite", Modifier.fillMaxWidth())
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
            PlateButton(
                onClick = { onAction(PuzzleAction.TryAgain) },
                enabled = !puzzle.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(PuzzleText.tryAgain(puzzle.info), textAlign = TextAlign.Center)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                GoaOutlinedButton(
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
        shape = RoundedCornerShape(8.dp),
        color = PlateWell,
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
                    color = GoaTheme.colors.heading,
                    modifier = Modifier.semantics { heading() },
                )
                Text(puzzle.info.title, style = MaterialTheme.typography.titleSmall)
                Text(PuzzleText.record(puzzle.attempts, puzzle.nudgesUsed), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    // Onward first (sol-b #12): Next puzzle the bronze button, the replay on the board beside it, both over the
    // explanation so going on never waits on reading it. The last puzzle: the replay alone, bronze.
    val next = puzzle.next
    val replay: @Composable (Modifier, PlateTone) -> Unit = { modifier, tone ->
        PlateButton(
            onClick = { onAction(PuzzleAction.ShowSolution) },
            enabled = !puzzle.busy,
            tone = tone,
            modifier = modifier.heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (next != null) "Replay solution" else "Replay the solution on the board",
                textAlign = TextAlign.Center,
            )
        }
    }
    if (next != null) {
        val nextButton: @Composable (Modifier) -> Unit = { modifier ->
            PlateButton(onClick = { onNext(next) }, modifier = modifier.heightIn(min = 52.dp)) {
                Text("Next puzzle", textAlign = TextAlign.Center)
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Side by side; with large text, one over the other, each whole.
        if (LocalDensity.current.fontScale > STACK_BUTTONS_SCALE) {
            nextButton(Modifier.fillMaxWidth())
            replay(Modifier.fillMaxWidth(), PlateTone.STEEL)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                nextButton(Modifier.weight(1f))
                replay(Modifier.weight(1f), PlateTone.STEEL)
            }
        }
    } else {
        replay(Modifier.fillMaxWidth(), PlateTone.GOLD)
    }
    Explanation(puzzle.info)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TextButton(onClick = onList, modifier = Modifier.heightIn(min = 48.dp)) { Text("All puzzles") }
        TextButton(onClick = { onAction(PuzzleAction.Restart) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Play it again")
        }
    }
}

/**
 * Why it works, in the puzzle's words as numbered lines to scan (a full round's by turn, PuzzleText.whyItWorks),
 * and the rulebook pages it leans on.
 */
@Composable
private fun Explanation(info: PuzzleInfo) {
    val why = remember(info.explanation) { PuzzleText.whyItWorks(info.explanation) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "WHY IT WORKS",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        why.lead?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        why.steps.forEach { step -> ExplanationStep(step) }
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

/** One line of "Why it works": its number (or "Turn 2") in a bronze mark, then the puzzle's words. */
@Composable
private fun ExplanationStep(step: WhyItWorks.Step) {
    val turn = step.label.startsWith("Turn")
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Surface(
            shape = if (turn) RoundedCornerShape(50) else CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .padding(top = 1.dp)
                .then(if (turn) Modifier.heightIn(min = STEP_MARK) else Modifier.size(scaledIconSize(STEP_MARK, 1.4f))),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = if (turn) Modifier.padding(horizontal = 6.dp) else Modifier,
            ) {
                Text(
                    step.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(step.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** The solution on the board, step by step: Back / Next, the move outlined, what happened; then why. */
@Composable
private fun ReplayPanel(puzzle: PuzzleUi, replay: ReplayUi, onAction: (PuzzleAction) -> Unit, focus: CardFocusState) {
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
            Surface(
                onClick = { focus.show(listOf(card), title = "Solution · step ${replay.index + 1}") },
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                modifier = Modifier.cardFocusOrigin(focus, card.id).semantics {
                    contentDescription = "${card.name}. Look at the card"
                },
            ) { TableCard(card, width = 40.dp) }
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
        GoaOutlinedButton(
            onClick = { onAction(PuzzleAction.CloseReplay) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(if (replay.afterSolve) "Done" else "Back to the puzzle") }
        GoaButton(
            onClick = { onAction(PuzzleAction.ReplayNext) },
            enabled = !replay.last,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("Next step") }
    }
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = GoaTheme.colors.engravedBorder)
    Explanation(puzzle.info)
}

private const val SHEET_HEIGHT = 0.9f

/** Past this font scale the solved panel's two buttons stack, each whole. */
private const val STACK_BUTTONS_SCALE = 1.3f
private const val SETUP_HEIGHT = 0.75f
private val STEP_MARK = 22.dp
