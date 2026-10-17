package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Pill
import dev.quinton.goa.feature.players.common.ResultChip
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.label
import kotlinx.coroutines.delay

/** Per-match follow-ups offered by the log beyond edit and delete. */
data class MatchFollowUps(
    /** Rematch of a completed match; kind null = draft again. */
    val onRematch: (matchId: String, kind: RematchKind?) -> Unit = { _, _ -> },
    /** A pending match continues in Match setup and the timer. */
    val onResume: (matchId: String) -> Unit = {},
    /** A pending match's "Who won?". */
    val onLogResult: (matchId: String) -> Unit = {},
    val onHighlightShown: (matchId: String) -> Unit = {},
)

/** Match log grouped by day, newest first. */
@Composable
fun MatchLogContent(
    state: MatchLogUiState,
    onOpenMatch: (String) -> Unit,
    onDeleteMatch: (String) -> Unit,
    onLogMatch: () -> Unit,
    modifier: Modifier = Modifier,
    followUps: MatchFollowUps = MatchFollowUps(),
) {
    val listState = rememberLazyListState()
    var flash by remember { mutableStateOf<String?>(null) }
    var rematchFor by remember { mutableStateOf<MatchRowUi?>(null) }
    // After saving in the editor, bring the new or edited match into view and outline it briefly.
    LaunchedEffect(state.highlightId) {
        val id = state.highlightId ?: return@LaunchedEffect
        var header = 0
        for (group in state.groups) {
            val at = group.matches.indexOfFirst { it.id == id }
            if (at >= 0) {
                // The day header when it's the day's first match, otherwise the match itself.
                listState.animateScrollToItem(if (at == 0) header else header + 1 + at)
                break
            }
            header += 1 + group.matches.size
        }
        followUps.onHighlightShown(id)
        flash = id
        delay(FLASH_MS)
        flash = null
    }
    rematchFor?.let { match ->
        RematchSheet(
            options = match.rematchOptions,
            onPick = {
                rematchFor = null
                followUps.onRematch(match.id, it)
            },
            onDismiss = { rematchFor = null },
        )
    }
    when {
        state.loading -> LoadingState(modifier)

        state.groups.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "No matches yet",
                message = "Log your games to build ratings, records and the meta. Drafts add pending matches here too.",
                action = {
                    Button(onClick = onLogMatch) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Log a match")
                    }
                },
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = FAB_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.groups.forEach { group ->
                item(key = "h-" + group.header + group.matches.first().id) { SectionHeader(group.header) }
                items(group.matches, key = { it.id }) { match ->
                    MatchCard(
                        match,
                        onOpen = { onOpenMatch(match.id) },
                        onDelete = { onDeleteMatch(match.id) },
                        highlighted = flash == match.id,
                        onRematch = { rematchFor = match }.takeIf { match.rematchOptions.isNotEmpty() },
                        onResume = { followUps.onResume(match.id) }.takeIf { match.status == MatchStatus.PENDING },
                        onLogResult = {
                            followUps.onLogResult(match.id)
                        }.takeIf { match.status == MatchStatus.PENDING },
                    )
                }
            }
        }
    }
}

private const val FLASH_MS = 2_000L

/** Bottom padding of lists under an extended FAB: 56 dp FAB + 16 dp margin + room to read the last row. */
val FAB_CLEARANCE = 112.dp

/**
 * One match in a list. [onDelete] null hides the menu (e.g. on a profile). Completed matches offer
 * [onRematch]; pending ones show "Who won?" ([onLogResult]) and "Resume" ([onResume]) right on the card.
 */
@Composable
fun MatchCard(
    match: MatchRowUi,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onRematch: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onLogResult: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val summary = listOfNotNull(
        match.mode.label(),
        match.winCondition?.label(),
        match.durationMin?.let { "$it min" },
        // Older matches stored "Single lane"/"Double lane" as the map: don't repeat the mode.
        match.map?.takeIf { it.isNotBlank() && !it.equals(match.mode.label(), ignoreCase = true) },
    ).joinToString(" · ")
    EngravedCard(
        modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .semantics {
                customActions = listOfNotNull(
                    onLogResult?.let {
                        CustomAccessibilityAction("Log result") {
                            it()
                            true
                        }
                    },
                    onResume?.let {
                        CustomAccessibilityAction("Resume in Match setup") {
                            it()
                            true
                        }
                    },
                    onRematch?.let {
                        CustomAccessibilityAction("Rematch") {
                            it()
                            true
                        }
                    },
                    onDelete?.let {
                        CustomAccessibilityAction("Delete match") {
                            it()
                            true
                        }
                    },
                )
            },
    ) {
        Column(
            Modifier
                .clickable(onClickLabel = "Edit match", role = Role.Button, onClick = onOpen)
                .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultChip(match.status, match.winner)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    match.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete == null) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onOpen()
                                },
                            )
                            onRematch?.let { rematch ->
                                DropdownMenuItem(
                                    text = { Text("Rematch") },
                                    leadingIcon = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        rematch()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.md, bottom = Spacing.sm),
                )
            }
            val stacked = LocalDensity.current.fontScale >= 1.5f
            if (stacked) {
                Column(Modifier.padding(end = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TeamColumn(Team.RED, match.red, winner = match.winner)
                    TeamColumn(Team.BLUE, match.blue, winner = match.winner)
                }
            } else {
                Row(Modifier.padding(end = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    TeamColumn(Team.RED, match.red, winner = match.winner, modifier = Modifier.weight(1f))
                    TeamColumn(Team.BLUE, match.blue, winner = match.winner, modifier = Modifier.weight(1f))
                }
            }
            if (match.notes.isNotBlank()) {
                Text(
                    "“${match.notes}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.sm, end = Spacing.md),
                )
            }
            if (onLogResult != null || onResume != null) {
                FlowRow(
                    Modifier.padding(top = Spacing.sm, end = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    onLogResult?.let {
                        FilledTonalButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Who won?")
                        }
                    }
                    onResume?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Resume: setup & timer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamColumn(team: Team, seats: List<SeatUi>, winner: Team?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(team)
            if (winner == team) {
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "· Winner",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        seats.forEach { SeatLine(it) }
    }
}

@Composable
private fun SeatLine(seat: SeatUi) {
    val description = buildString {
        append("${seat.heroName}, piloted by ${seat.playerName}")
        if (seat.handicap) append(", handicap")
        if (seat.doublePilot) append(", double pilot")
        seat.delta?.let { append(", rating ${formatDelta(it)}") }
    }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroPortrait(
            heroId = seat.heroId ?: "?",
            portrait = seat.portrait,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            // The rating change sits on the hero line, so the pilot line keeps the full width for
            // the name and its ×2/H marks. When both don't fit (large text in a half-width column)
            // the change wraps below instead of cutting the hero's name ("Xarga…").
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    seat.heroName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                DeltaIndicator(seat.delta, label = "rating")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    seat.playerName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (seat.handicap) Pill("H")
                if (seat.doublePilot) Pill("×2")
            }
        }
    }
}
