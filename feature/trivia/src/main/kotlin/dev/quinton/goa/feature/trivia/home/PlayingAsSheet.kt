package dev.quinton.goa.feature.trivia.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.trivia.PlayingAsState

/** Why the Playing-as picker is open. */
enum class PlayingAsPicker {
    /** "Playing as ▾": any active player or Guest. */
    CHOOSE,

    /** Challenge tapped while playing as Guest: a named player is needed, then the Challenge opens. */
    CHALLENGE,
}

/** What the Playing-as picker can do. */
data class PlayingAsActions(
    /** Plays as this player id, or [dev.quinton.goa.core.data.TriviaProgressRepository.GUEST]. */
    val onPick: (String) -> Unit = {},
    /** Adds a player by name (an existing name picks that player) and plays as them. */
    val onAdd: (String) -> Unit = {},
    /** [PlayingAsPicker.CHALLENGE]: plays as this player, then opens the Challenge. */
    val onPickForChallenge: (String) -> Unit = {},
    val onAddForChallenge: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayingAsSheet(
    state: PlayingAsState,
    mode: PlayingAsPicker,
    actions: PlayingAsActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PlayingAsContent(
            state = state,
            mode = mode,
            onPick = { id ->
                onDismiss()
                if (mode == PlayingAsPicker.CHALLENGE) actions.onPickForChallenge(id) else actions.onPick(id)
            },
            onAdd = { name ->
                onDismiss()
                if (mode == PlayingAsPicker.CHALLENGE) actions.onAddForChallenge(name) else actions.onAdd(name)
            },
        )
    }
}

/**
 * Who plays the next rounds: active players (this phone's "You" first), Guest (not in Challenge
 * mode) and an "Add player" field, never a dead end when the group is empty.
 */
@Composable
internal fun PlayingAsContent(
    state: PlayingAsState,
    mode: PlayingAsPicker,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    val challenge = mode == PlayingAsPicker.CHALLENGE
    LazyColumn(
        modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item(key = "title") {
            Column(Modifier.padding(horizontal = Spacing.screen)) {
                Text(
                    if (challenge) "Who's taking the Challenge?" else "Who's playing?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (challenge) {
                        "Challenge scores go on the leaderboard under a player's name."
                    } else {
                        "Rounds played as a player go on the leaderboard. Guest rounds aren't recorded."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
                )
            }
        }
        items(state.players, key = { it.id }) { player ->
            PickRow(
                selected = !challenge && player.id == state.player?.id,
                title = player.name,
                subtitle = null,
                isMe = player.id == state.me?.id,
                onClick = { onPick(player.id) },
                leading = { PlayerAvatar(player, size = 40.dp) },
            )
        }
        if (!challenge) {
            item(key = "guest") {
                PickRow(
                    selected = state.isGuest,
                    title = "Guest",
                    subtitle = "Not on the leaderboard",
                    isMe = false,
                    onClick = { onPick(GUEST_ID) },
                    leading = { GuestAvatar() },
                )
            }
        }
        item(key = "add") {
            val add = {
                if (newName.isNotBlank()) {
                    onAdd(newName)
                    newName = ""
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(MAX_NAME_LENGTH) },
                    // A label (read by TalkBack as the field's name, and kept above the text once typing).
                    label = {
                        Text(if (state.players.isEmpty()) "Name of the first player" else "Someone missing? Add them")
                    },
                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                TextButton(onClick = add, enabled = newName.isNotBlank()) { Text("Add") }
            }
        }
    }
}

@Composable
private fun PickRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    isMe: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = listOfNotNull(title, "you".takeIf { isMe }, subtitle).joinToString(", ")
            }
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    YouTag()
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** A neutral silhouette for Guest: no colour, no crest, nobody in particular. */
@Composable
internal fun GuestAvatar(size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.PersonOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** Marks this phone's own player, as on the Players tab. */
@Composable
internal fun YouTag(modifier: Modifier = Modifier) {
    Text(
        "You",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/** Avatar for whoever is playing: their crest, or the Guest silhouette. */
@Composable
internal fun PlayingAvatar(player: Player?, size: Dp = 40.dp) {
    if (player == null) GuestAvatar(size) else PlayerAvatar(player, size = size)
}

internal const val GUEST_ID = TriviaProgressRepository.GUEST
