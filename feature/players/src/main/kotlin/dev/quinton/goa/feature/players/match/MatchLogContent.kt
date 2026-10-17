package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Pill
import dev.quinton.goa.feature.players.common.ResultChip
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.label

/** Match log grouped by day, newest first. */
@Composable
fun MatchLogContent(
    state: MatchLogUiState,
    onOpenMatch: (String) -> Unit,
    onDeleteMatch: (String) -> Unit,
    onLogMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.groups.forEach { group ->
                item(key = "h-" + group.header + group.matches.first().id) { SectionHeader(group.header) }
                items(group.matches, key = { it.id }) { match ->
                    MatchCard(match, onOpen = { onOpenMatch(match.id) }, onDelete = { onDeleteMatch(match.id) })
                }
            }
        }
    }
}

@Composable
fun MatchCard(match: MatchRowUi, onOpen: () -> Unit, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val summary = listOfNotNull(
        match.mode.label(),
        match.winCondition?.label(),
        match.durationMin?.let { "$it min" },
        match.map?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    EngravedCard(
        modifier
            .fillMaxWidth()
            .semantics {
                if (onDelete != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Delete match") {
                            onDelete()
                            true
                        },
                    )
                }
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
                Text("· Winner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(seat.heroName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
        DeltaIndicator(seat.delta, label = "rating")
    }
}
