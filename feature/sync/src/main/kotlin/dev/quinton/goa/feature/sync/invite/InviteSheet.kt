package dev.quinton.goa.feature.sync.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.group.InviteFor

class InviteSheetActions(
    val onChoose: (InviteFor) -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onDone: () -> Unit = {},
)

/**
 * "Invite a friend" (ADR-025, ADR-031): first who it is for ("Anyone" or a player entered here),
 * then the QR code, big and on white (scanners need the contrast), with what to do on the other
 * phone. [qr] draws the code; screenshot tests pass their own.
 */
@Composable
fun InviteSheet(
    state: GroupInviteUiState,
    actions: InviteSheetActions,
    modifier: Modifier = Modifier,
    qr: (String) -> ImageBitmap = InviteQr::bitmap,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when {
            state.loading -> Unit

            !state.inGroup -> {
                Title("Invite a friend")
                Text(
                    "This phone isn't in a group. Start or join one under Settings → Group sync first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = actions.onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("OK")
                }
            }

            state.choosing -> Choose(state, actions)

            else -> Show(state, actions, qr)
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Choose(state: GroupInviteUiState, actions: InviteSheetActions) {
    Title("Who's the invite for?")
    Text(
        "Already entered your friend as a player? Pick them: after the first sync their phone asks " +
            "“You're …?” and becomes that player, matches and rating included, instead of adding someone new.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        ChoiceRow(
            onClick = { actions.onChoose(InviteFor.Anyone) },
            leading = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            },
            title = "Anyone",
            subtitle = "They pick themselves, or add themselves, after joining",
        )
        state.players.forEach { player ->
            ChoiceRow(
                onClick = { actions.onChoose(InviteFor.One(player.id)) },
                leading = { PlayerAvatar(player, size = 40.dp) },
                title = player.name,
                subtitle = null,
            )
        }
    }
}

@Composable
private fun ChoiceRow(onClick: () -> Unit, leading: @Composable () -> Unit, title: String, subtitle: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Make the invite", role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColumnScope.Show(state: GroupInviteUiState, actions: InviteSheetActions, qr: (String) -> ImageBitmap) {
    val invite = state.invite ?: return
    val player = state.player
    val bitmap = remember(invite) { qr(invite) }
    Title(if (player != null) "Invite ${player.name}" else "Invite to “${state.groupName}”")
    player?.let { InvitedPlayer(it, state.groupName.orEmpty()) }
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(Spacing.md)
            .semantics {
                contentDescription = if (player != null) {
                    "Invite QR code for ${player.name}"
                } else {
                    "Invite QR code for ${state.groupName}"
                }
            },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
    }
    Text(
        buildString {
            if (!invite.startsWith("goa-sync:")) {
                // With a hub the code is a link (ADR-030 §6): iPhones open the web app from the Camera.
                append(
                    "iPhone: point the Camera at the code and tap the link; the web app opens. Android: " +
                        "in the app, Settings → Group sync → Scan invite QR code.",
                )
            } else {
                append("On your friend's phone: install the app, open Settings → Group sync → Scan invite QR code.")
            }
            if (player != null) append(" After the first sync it asks “You're ${player.name}?”.")
            append(" Anyone with this code can join, so only show it at the table.")
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(
        onClick = { state.shareText?.let(actions.onShare) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(scaledIconSize(18.dp)))
        Spacer(Modifier.width(Spacing.sm))
        Text("Share as text")
    }
    Button(onClick = actions.onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done") }
    TextButton(
        onClick = { actions.onChoose(InviteFor.Choosing) },
        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
    ) { Text(if (player != null) "For someone else" else "For a player you entered") }
}

@Composable
private fun InvitedPlayer(player: Player, groupName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(
            "A personal invite to “$groupName”: ${player.name}'s phone becomes ${player.name}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
