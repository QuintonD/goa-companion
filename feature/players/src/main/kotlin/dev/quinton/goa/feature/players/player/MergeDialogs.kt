package dev.quinton.goa.feature.players.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.plural

/**
 * "Merge into…" (DOMAIN.md *Duplicate players*): one person with two players, usually added on two
 * phones before they synced. Pick the player to keep, confirm with both names and match counts;
 * the app refuses when the two played in the same match and lists those matches.
 */
@Composable
internal fun MergeSection(state: PlayerDetailUiState, actions: PlayerDetailActions) {
    val player = state.player ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    OutlinedButton(
        onClick = { picking = true },
        modifier = Modifier.padding(top = Spacing.lg).heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.MergeType, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Merge into…")
    }
    Text(
        "Added twice (e.g. on two phones)? Merge this player into the other one: their matches and ratings " +
            "become one, on every phone after the next sync.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Merge ${player.name} into…") },
            text = {
                LazyColumn(Modifier.selectableGroup().heightIn(max = 360.dp)) {
                    items(state.mergeCandidates, key = { it.player.id }) { candidate ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = candidate.player.id == targetId,
                                    role = Role.RadioButton,
                                    onClick = { targetId = candidate.player.id },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = candidate.player.id == targetId, onClick = null)
                            Spacer(Modifier.width(Spacing.sm))
                            PlayerAvatar(candidate.player, size = 32.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text(candidate.player.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    plural(candidate.matches, "match", "matches"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }, enabled = targetId != null) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = {
                    picking = false
                    targetId = null
                }) { Text("Cancel") }
            },
        )
    }
    val target = state.mergeCandidates.firstOrNull { it.player.id == targetId }
    if (!picking && target != null) {
        ConfirmDialog(
            title = "Merge into ${target.player.name}?",
            message = "${player.name} (${plural(state.totalMatches, "match", "matches")}) becomes " +
                "${target.player.name} (${plural(target.matches, "match", "matches")}). Their matches and ratings " +
                "count as one player from now on, and the other phones follow at the next sync. " +
                "${player.name} disappears from the lists. This can't be undone.",
            confirmLabel = "Merge",
            destructive = true,
            icon = Icons.Outlined.MergeType,
            onConfirm = {
                targetId = null
                actions.onMerge(target.player.id)
            },
            onDismiss = { targetId = null },
        )
    }
    (state.mergeOutcome as? MergeOutcome.PlayedTogether)?.let { refused ->
        PlayedTogetherDialog(refused.matches, onDismiss = actions.onMergeOutcomeShown)
    }
}

/** A refused merge: the two played in these [matches] (one line each), so they are two people. */
@Composable
internal fun PlayedTogetherDialog(matches: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Can't merge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "They played in the same match, so they are two people (one person can't sit on both " +
                        "teams, and teammates are two players):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                matches.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** The matches a refused merge names, oldest first: "2026-10-12 · Ann, Bob vs Cid, Dee". */
internal fun playedTogether(matches: List<Match>, people: Map<String, Player>): List<String> =
    matches.sortedBy { it.playedAt }.map { m ->
        fun side(team: Team) = m.team(team).map { people[it.playerId]?.name ?: "Unknown player" }
            .distinct().joinToString()
        "${Dates.localDate(m.playedAt)} · ${side(Team.RED)} vs ${side(Team.BLUE)}"
    }
