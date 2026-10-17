package dev.quinton.goa.feature.draft.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.SeatAssignment
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.goaFilterChipColors
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.teamLabel

/** Finished draft: arrange heroes on seats/pilots, then save and hand off to match setup. */
@Composable
internal fun CompletionContent(state: BoardUiState, session: DraftSession, actions: BoardActions) {
    val assignments = DraftEngine.assignments(session)
    LazyColumn(
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Draft complete",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    when (session.seatLock) {
                        null -> "Tap two heroes on the same team to swap who plays them."
                        DraftError.PlayerDraftFixed -> "Everyone plays the hero chosen for them."
                        else -> "Everyone plays the hero they picked from their own hand."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (team in listOf(session.teamA, session.teamB)) {
            item(key = team.name) {
                TeamResult(state, session, team, assignments.filter { it.seat.team == team }, actions)
            }
        }
        if (session.bans.isNotEmpty()) {
            item {
                Text(
                    "Banned: " + session.bans.joinToString(" · ") { state.heroes.info(it.heroId).name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { actions.finish(openMatchSetup = true) },
                    enabled = !state.local.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Timer, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Set up match")
                }
                OutlinedButton(
                    onClick = { actions.finish(openMatchSetup = false) },
                    enabled = !state.local.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Log result later")
                }
                if (session.poolEditable()) {
                    OutlinedButton(
                        onClick = actions::reroll,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Outlined.Casino, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Deal again")
                    }
                }
                Text(
                    "Both save a pending match to the log with these seats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TeamResult(
    state: BoardUiState,
    session: DraftSession,
    team: Team,
    seats: List<SeatAssignment>,
    actions: BoardActions,
) {
    val color = teamColor(team)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    teamLabel(team),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (team == session.teamA) "Team A · Tie Breaker" else "Team B",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            seats.forEach { assignment -> SeatRow(state, assignment, session.canSwapSeats, actions) }
        }
    }
}

@Composable
private fun SeatRow(state: BoardUiState, assignment: SeatAssignment, swappable: Boolean, actions: BoardActions) {
    val seat = assignment.seat
    val hero = assignment.heroId?.let { state.heroes.info(it) }
    val selected = state.local.selectedSeat == seat.id
    val pilot = state.players[seat.pilotId]
    Surface(
        onClick = { actions.selectSeat(seat.id) },
        enabled = swappable,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondary.copy(
                alpha = 0.14f,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (swappable) role = Role.Button
                contentDescription = "${hero?.name ?: "Open seat"}, played by ${state.playerName(seat.pilotId)}" +
                    if (seat.handicap) ", Handicap card" else ""
                stateDescription = if (selected) "Selected — tap another hero on this team to swap" else ""
            },
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 70.dp)) {
                if (hero != null) {
                    HeroPortrait(hero.id, hero.portrait, null, Modifier.matchParentSize())
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(hero?.name ?: "Open seat", style = MaterialTheme.typography.titleMedium)
                if (hero != null && hero.title.isNotEmpty()) {
                    Text(
                        hero.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(pilot, size = 24.dp)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(state.playerName(seat.pilotId), style = MaterialTheme.typography.labelLarge)
                }
            }
            FlowRow {
                FilterChip(
                    colors = goaFilterChipColors(),
                    selected = seat.handicap,
                    onClick = { actions.toggleHandicap(seat.id) },
                    label = { Text("Handicap") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
