package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick as semanticsClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.ui.PlateSurface

/**
 * The puzzles (PRODUCT §10): Continue with the first unsolved one at the top ("Up next · Puzzle 2 of 50"),
 * difficulty and theme filters, then the book's two parts, Positions and Full rounds, each with its progress
 * as the Play tab shows it ("1 of 50 positions", [SolvedCount]), and every puzzle by number with its title,
 * theme, difficulty (hexes and words), heroes and progress. Reset progress is in the menu, behind a confirmation.
 */
@Composable
fun PuzzleListScreen(
    state: PuzzleListUiState,
    onAction: (PuzzleListAction) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    val ready = state as? PuzzleListUiState.Ready
    GoaScreen(
        title = "Puzzles",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (ready != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset progress") },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            enabled = ready.rows.any { it.progress.tried } || ready.solved > 0,
                            onClick = {
                                menu = false
                                resetting = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PuzzleListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                PuzzleListUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = "No puzzles yet",
                    message = "Puzzles appear as the heroes they use become playable.",
                    modifier = Modifier.align(Alignment.Center),
                )

                is PuzzleListUiState.Ready -> PuzzleList(state, onAction, onOpen)
            }
        }
    }
    if (resetting) {
        ConfirmDialog(
            title = "Reset puzzle progress?",
            message = "Every puzzle goes back to unsolved and untried on this phone. This can't be undone.",
            confirmLabel = "Reset",
            destructive = true,
            icon = Icons.Outlined.RestartAlt,
            onConfirm = {
                resetting = false
                onAction(PuzzleListAction.ResetProgress)
            },
            onDismiss = { resetting = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleList(state: PuzzleListUiState.Ready, onAction: (PuzzleListAction) -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "continue") { ContinueCard(state, onOpen) }
        if (state.difficulties.size > 1 || state.themes.size > 1) {
            item(key = "filters") {
                Column(
                    Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.difficulties.size > 1) DifficultyFilter(state, onAction)
                    if (state.themes.size > 1) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            state.themes.forEach { theme ->
                                GoaFilterChip(
                                    selected = theme in state.filters.themes,
                                    onClick = { onAction(PuzzleListAction.ToggleTheme(theme)) },
                                    label = theme,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "heading") {
            PartHeading(
                "Positions",
                state.positionsSolved,
                state.positionsTotal,
                false,
                "One position and a goal: find the line that makes it against the best replies.",
                Modifier.padding(top = Spacing.sm),
            )
        }
        if (state.rows.isEmpty()) {
            item(key = "none") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No puzzle matches these filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { onAction(PuzzleListAction.ClearFilters) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Show every puzzle") }
                }
            }
        }
        items(state.rows.filterNot { it.info.fullRound }, key = { it.info.id }) { row ->
            PuzzleRow(row) { onOpen(row.info.id) }
        }
        val rounds = state.rows.filter { it.info.fullRound }
        if (rounds.isNotEmpty()) {
            item(key = "rounds-heading") {
                PartHeading(
                    "Full rounds",
                    state.roundsSolved,
                    state.roundsTotal,
                    true,
                    "Four turns planned from turn 1, through the Minion Battle. You know what they will play.",
                    Modifier.padding(top = Spacing.md),
                )
            }
            items(rounds, key = { it.info.id }) { row -> PuzzleRow(row) { onOpen(row.info.id) } }
        }
    }
}

/** The next unsolved puzzle with Continue (or Start), "Up next · Puzzle 2 of 50"; all solved says so. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContinueCard(state: PuzzleListUiState.Ready, onOpen: (String) -> Unit) {
    val next = state.next
    // Large text: the button under the words, so they keep the width.
    val stacked = LocalDensity.current.fontScale > LARGE_TEXT
    PlateSurface(brackets = true, modifier = Modifier.fillMaxWidth()) {
        val content = MaterialTheme.colorScheme.onSurface
        FlowRow(
            Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
            maxItemsInEachRow = if (stacked || next == null) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (next == null) SolvedSeal(size = 48.dp)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                if (next != null) {
                    // "Puzzle 2 of 50": the same words and count as the puzzle's own screen.
                    val part = if (next.info.fullRound) state.roundsTotal else state.positionsTotal
                    Text(
                        "UP NEXT · ${PuzzleText.place(next.info, part).uppercase()}",
                        style = GoaTextStyles.overline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(next.info.title, style = MaterialTheme.typography.titleMedium, color = content)
                    Text(next.info.goal.text, style = MaterialTheme.typography.bodySmall, color = content)
                } else {
                    Text(
                        listOfNotNull(
                            PuzzleText.partCount(state.positionsSolved, state.positionsTotal, rounds = false),
                            state.roundsTotal.takeIf { it > 0 }
                                ?.let { PuzzleText.partCount(state.roundsSolved, it, rounds = true) },
                        ).joinToString(" · ").uppercase(),
                        style = GoaTextStyles.overline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Every puzzle solved", style = MaterialTheme.typography.titleMedium, color = content)
                    Text(
                        "Replay any of them below; more arrive as heroes become playable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                    )
                }
            }
            if (next != null) {
                GoaButton(
                    onClick = { onOpen(next.info.id) },
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    modifier = Modifier.heightIn(min = 48.dp).then(if (stacked) Modifier.fillMaxWidth() else Modifier),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (next.progress.tried) "Continue" else "Start", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * A part of the book: "POSITIONS" and its progress ("✓ 1 of 50 positions", the very line the Play tab shows) on one
 * line, what the part is under it. The count is the one the puzzle's own screen uses ("Puzzle 3 of 50").
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PartHeading(
    title: String,
    solved: Int,
    total: Int,
    rounds: Boolean,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SolvedCount(solved, total, rounds)
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The difficulty filter as one checked segmented bar (opus #13): "All" checked by default, then each difficulty, each
 * with how many positions it holds under its name ("All / 50", as the Positions part under it counts). One is shown
 * at a time; "All" clears the filter.
 */
@Composable
private fun DifficultyFilter(state: PuzzleListUiState.Ready, onAction: (PuzzleListAction) -> Unit) {
    val chosen = state.filters.difficulties
    fun only(d: Int?) {
        chosen.filter { it != d }.forEach { onAction(PuzzleListAction.ToggleDifficulty(it)) }
        if (d != null && d !in chosen) onAction(PuzzleListAction.ToggleDifficulty(d))
    }
    val all = state.difficultyCounts.values.sum().takeIf { it > 0 } ?: state.positionsTotal
    GoaSegmentedRow(Modifier.fillMaxWidth()) {
        GoaSegment(selected = chosen.isEmpty(), onClick = { only(null) }) { FilterLabel("All", all) }
        state.difficulties.forEach { d ->
            GoaSegment(selected = chosen == setOf(d), onClick = { only(d) }) {
                FilterLabel(PuzzleText.difficulty(d), state.difficultyCounts[d])
            }
        }
    }
}

@Composable
private fun FilterLabel(name: String, count: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, maxLines = 1)
        count?.let {
            Text(
                "$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** One puzzle: its number in a bronze hex ring, title, theme, difficulty, heroes, progress. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleRow(row: PuzzleRowUi, onClick: () -> Unit) {
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
    PlateSurface(
        onClick = onClick,
        brackets = true,
        border = if (progress.solved) BorderStroke(1.dp, SOLVED_EDGE) else null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clearAndSetSemantics {
                contentDescription = listOf(
                    "${PuzzleText.short(info)}: ${info.title}",
                    info.theme,
                    PuzzleText.difficulty(info.difficulty),
                    PuzzleText.names(row.heroes.map { it.name }),
                    status,
                    *extras.toTypedArray(),
                ).joinToString(". ")
                // Clearing the semantics drops the Surface's own click: a screen reader must still open it.
                semanticsClick {
                    onClick()
                    true
                }
            },
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            NumberMark(info.number, progress.solved)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    info.theme,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    DifficultyMark(info.difficulty)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.heroes.forEach { hero ->
                            HeroPortrait(
                                heroId = hero.id,
                                portrait = hero.portrait,
                                contentDescription = null,
                                shape = CircleShape,
                                framed = false,
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(scaledIconSize(22.dp, maxScale = 1.4f))
                                    .border(1.dp, GoaTheme.colors.engravedBorder, CircleShape),
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (progress.solved) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(scaledIconSize(16.dp, 1.4f)),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (progress.solved) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.run { if (progress.solved) onSurface else onSurfaceVariant },
                    )
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.width(Spacing.sm))
                        if (progress.nudgesUsed > 0) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(scaledIconSize(14.dp, 1.4f)),
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            extras.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The puzzle's number, Cinzel in a bronze ring (filled once solved). */
@Composable
private fun NumberMark(number: Int, solved: Boolean) {
    val size = scaledIconSize(40.dp, 1.4f)
    Surface(
        shape = CircleShape,
        color = if (solved) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleLarge,
                color = if (solved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val SOLVED_EDGE = Color(0x99C9A35B)
private const val LARGE_TEXT = 1.3f
