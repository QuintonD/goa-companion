package dev.quinton.goa.feature.sync.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.player.Player

/** "You're <name>?" (ADR-031): a personal invite's player, to confirm before anything else. */
@Composable
internal fun InvitedCard(invited: Player, actions: WhoAreYouActions) {
    GoaCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(invited, size = 56.dp)
            Spacer(Modifier.width(Spacing.lg))
            Text(
                "You're ${invited.name}?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            "Your invite was made for ${invited.name}. If that's you, this phone becomes ${invited.name}: " +
                "your matches and rating are already there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedButton(onClick = actions.onNotMe, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not me") }
            Button(onClick = actions.onConfirmInvited, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Yes, I'm ${invited.name}")
            }
        }
    }
}

/** The dialogs of linking a phone to its player: merge the old one, a refusal, "Is one of these you?". */
@Composable
internal fun LinkDialogs(state: WhoAreYouUiState, actions: WhoAreYouActions) {
    state.mergeOffer?.let { offer -> MergeOfferDialog(offer, actions) }
    state.mergeRefused?.let { lines -> RefusedDialog(state.meId, state.players, lines, actions.onRefusalShown) }
    state.addCheck?.let { check -> AddCheckDialog(check, actions) }
}

@Composable
private fun MergeOfferDialog(offer: MergeOffer, actions: WhoAreYouActions) {
    val old = offer.old.name
    val into = offer.into.name
    val matches = if (offer.oldMatches == 1) "1 match" else "${offer.oldMatches} matches"
    AlertDialog(
        onDismissRequest = actions.onKeepBoth,
        title = { Text("Merge your old player into $into?") },
        text = {
            Text(
                "Before, this phone was “$old” ($matches). If that was you too, merge $old into $into: those " +
                    "matches count for $into, on every phone after the next sync, and $old disappears from the " +
                    "lists. This can't be undone. If $old is someone else, keep both.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = actions.onMergeOld) { Text("Merge") } },
        dismissButton = { TextButton(onClick = actions.onKeepBoth) { Text("Keep both") } },
    )
}

@Composable
private fun RefusedDialog(meId: String?, players: List<Player>, lines: List<String>, onDismiss: () -> Unit) {
    val me = players.firstOrNull { it.id == meId }?.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Can't merge") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "The two played in the same match, so they are two people (one person can't sit on both " +
                        "teams, and teammates are two players). Both stay" +
                        (me?.let { "; this phone is $it now." } ?: "."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun AddCheckDialog(check: AddCheck, actions: WhoAreYouActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissAddCheck,
        title = { Text("Is one of these you?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (check.nameTaken) {
                        "“${check.name}” is already in the group. If that's you, pick it. If not, add yourself " +
                            "with another name, like a last initial."
                    } else {
                        "These players have a name like “${check.name}”. If one is you, pick it: your matches and " +
                            "rating are already there."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                check.candidates.forEach { player ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "This is me", role = Role.Button) { actions.onChoose(player.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerAvatar(player, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Text(player.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            if (!check.nameTaken) TextButton(onClick = actions.onAddAnyway) { Text("No, add ${check.name}") }
        },
        dismissButton = { TextButton(onClick = actions.onDismissAddCheck) { Text("Cancel") } },
    )
}
