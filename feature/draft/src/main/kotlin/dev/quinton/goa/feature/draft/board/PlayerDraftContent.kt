package dev.quinton.goa.feature.draft.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.HeroTile
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamLabel

/** The official Player Draft: heroes for every player first, then captains draft players. */
@Composable
internal fun PlayerDraftContent(state: BoardUiState, session: PlayerDraftSession, actions: BoardActions) {
    var drafting by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { PhaseBanner(state, session) }
        when (session.phase) {
            PlayerDraftPhase.HEROES -> items(session.selection.players, key = { it }) { playerId ->
                HeroChoiceRow(state, session, playerId, actions)
            }

            PlayerDraftPhase.CAPTAINS, PlayerDraftPhase.DONE -> {
                item { CaptainTeams(state, session) }
                item {
                    Text(
                        "Still to draft",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                    )
                }
                items(session.captains.remaining, key = { it }) { playerId ->
                    PlayerCard(state, session, playerId, onClick = { drafting = playerId })
                }
            }
        }
    }
    val pending = drafting
    val team = session.captains.nextTeam
    if (pending != null && team != null) {
        AlertDialog(
            onDismissRequest = { drafting = null },
            title = { Text("Draft ${state.playerName(pending)} to ${teamLabel(team)}?") },
            text = { Text("They play ${heroLabel(state.heroes, session.selection.choices[pending])}.") },
            confirmButton = {
                Button(
                    onClick = {
                        drafting = null
                        actions.draftPlayer(pending)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = teamColor(team),
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) { Text("${teamGlyph(team)} Draft") }
            },
            dismissButton = { TextButton(onClick = { drafting = null }) { Text("Cancel") } },
        )
    }
    state.local.choosingFor?.let { playerId -> HeroChooser(state, session, playerId, actions) }
}

@Composable
private fun PhaseBanner(state: BoardUiState, session: PlayerDraftSession) {
    val team = session.captains.nextTeam
    val color = team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.primary
    val (title, body) = when (session.phase) {
        PlayerDraftPhase.HEROES ->
            "Heroes for every player" to
                "${session.method.name}: ${session.method.summary} Choose in any order; teams come next."

        PlayerDraftPhase.CAPTAINS -> {
            val captain = state.playerName(team?.let { session.captains.captainOf(it) })
            "${team?.let { teamLabel(it) }} captain — draft a player" to
                "$captain picks. Team A's captain drafts first, then captains alternate."
        }

        PlayerDraftPhase.DONE -> "Teams complete" to ""
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(2.dp, color),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            if (body.isNotEmpty()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroChoiceRow(state: BoardUiState, session: PlayerDraftSession, playerId: String, actions: BoardActions) {
    val heroId = session.selection.choices[playerId]
    val captainOf = Team.entries.firstOrNull { session.captains.captainOf(it) == playerId }
    Surface(
        onClick = { if (!session.selection.auto) actions.chooseHeroFor(playerId) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(state.players[playerId])
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(state.playerName(playerId), style = MaterialTheme.typography.titleMedium)
                Text(
                    captainOf?.let { "${teamLabel(it)} captain" } ?: "Player",
                    style = MaterialTheme.typography.labelMedium,
                    color = captainOf?.let { teamColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (heroId != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(state.heroes.info(heroId).name, style = MaterialTheme.typography.labelLarge)
                    if (!session.selection.auto) {
                        TextButton(onClick = { actions.clearHero(playerId) }) { Text("Change") }
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                HeroPortrait(
                    heroId,
                    state.heroes.info(heroId).portrait,
                    null,
                    Modifier.size(width = 48.dp, height = 60.dp),
                )
            } else {
                Button(onClick = { actions.chooseHeroFor(playerId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Choose hero")
                }
            }
        }
    }
}

@Composable
private fun CaptainTeams(state: BoardUiState, session: PlayerDraftSession) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (team in listOf(session.teamA, session.teamA.opponent)) {
            val members = if (team == Team.RED) session.captains.red else session.captains.blue
            val active = session.captains.nextTeam == team
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (active) 2.dp else 1.dp,
                    if (active) teamColor(team) else GoaTheme.colors.engravedBorder,
                ),
            ) {
                Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        teamLabel(team) + if (team == session.teamA) " · A" else " · B",
                        style = MaterialTheme.typography.titleSmall,
                        color = teamColor(team),
                    )
                    members.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(state.players[id], size = 28.dp)
                            Spacer(Modifier.width(Spacing.xs))
                            Column {
                                Text(state.playerName(id), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    heroLabel(state.heroes, session.selection.choices[id]),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(state: BoardUiState, session: PlayerDraftSession, playerId: String, onClick: () -> Unit) {
    val heroId = session.selection.choices[playerId]
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "${state.playerName(playerId)}, plays ${heroLabel(state.heroes, heroId)}"
            },
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(state.players[playerId])
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(state.playerName(playerId), style = MaterialTheme.typography.titleMedium)
                Text(
                    heroLabel(state.heroes, heroId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(width = 48.dp, height = 60.dp)) {
                if (heroId !=
                    null
                ) {
                    HeroPortrait(heroId, state.heroes.info(heroId).portrait, null, Modifier.matchParentSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroChooser(state: BoardUiState, session: PlayerDraftSession, playerId: String, actions: BoardActions) {
    val available = session.selection.available(playerId).toSet()
    val shown = session.selection.hands[playerId] ?: session.selection.pool
    ModalBottomSheet(onDismissRequest = { actions.chooseHeroFor(null) }) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text("Hero for ${state.playerName(playerId)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.heightIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(shown, key = { it }) { heroId ->
                    val free = heroId in available
                    HeroTile(
                        hero = state.heroes.info(heroId),
                        state = if (free) TileState.Available else TileState.Inactive,
                        onClick = if (free) ({ actions.assignHero(heroId) }) else null,
                    )
                }
            }
        }
    }
}
