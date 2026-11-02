package dev.quinton.goa.feature.board.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.OnlineHomeUi
import dev.quinton.goa.feature.board.play.online.OnlineRooms
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.ui.bronzeSegments

/** What the online home can do; a class rather than two dozen parameters. */
class OnlineHomeActions(
    val onName: (String) -> Unit = {},
    val onCreateAccount: () -> Unit = {},
    val onLinkCode: (String) -> Unit = {},
    val onLink: () -> Unit = {},
    val onCodeSaved: () -> Unit = {},
    val onCopyCode: (String) -> Unit = {},
    val onMap: (String) -> Unit = {},
    val onLength: (GameLength) -> Unit = {},
    val onTeamSize: (Int) -> Unit = {},
    val onRanked: (Boolean) -> Unit = {},
    val onCreateRoom: () -> Unit = {},
    val onJoinText: (String) -> Unit = {},
    val onJoin: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onGame: (MyGame) -> Unit = {},
    val onLeaderboard: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The online home (ADR-056): a name or a recovery code; then the code to save, which nothing passes without
 * "I've saved it"; then a new room, joining, the games to go back to, the leaderboard and the account.
 */
@Composable
fun OnlineHomeScreen(
    state: OnlineHomeUi,
    mapNames: Map<String, String>,
    actions: OnlineHomeActions,
    modifier: Modifier = Modifier,
) {
    val gate = state.stage == AccountStage.SAVE_CODE
    // Nothing leaves the gate but "I've saved it": linking has burnt the old code, so this one is the only one.
    BackHandler(enabled = gate) {}
    GoaScreen(title = "Online", onBack = actions.onBack.takeUnless { gate }, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.message?.let { Message(it) }
            when (state.stage) {
                AccountStage.LOADING -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                AccountStage.PICK_NAME -> PickName(state, actions)

                AccountStage.SAVE_CODE -> CodeGate(state, actions)

                AccountStage.READY -> Ready(state, mapNames, actions)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    CardTextBox(PlateColor.GOLD, Modifier.fillMaxWidth(), cap = "Note") {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun OnlineSection(title: String) {
    Text(
        title.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun PickName(state: OnlineHomeUi, actions: OnlineHomeActions) {
    OnlineSection("Play online")
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Pick the name the others will see. There's no email and nothing else to fill in.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onName,
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            GoaButton(
                onClick = actions.onCreateAccount,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Create account") }
        }
    }
    OnlineSection("Link this device")
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Already playing on another phone, or lost it? Your recovery code brings the account here. It " +
                    "works once, and you'll get a new one to save.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.linkCode,
                onValueChange = actions.onLinkCode,
                label = { Text("Recovery code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = actions.onLink,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Link this device") }
        }
    }
    LeaderboardEntry(actions)
}

/** The recovery code, shown once. Nothing else is on this screen, and no way off it but saying it is saved. */
@Composable
private fun CodeGate(state: OnlineHomeUi, actions: OnlineHomeActions) {
    val code = state.recoveryCode.orEmpty()
    OnlineSection("Your recovery code")
    CardTextBox(PlateColor.GOLD, Modifier.fillMaxWidth(), cap = "Save this") {
        Text(OnlineWords.SAVE_RECOVERY_CODE, style = MaterialTheme.typography.bodyMedium)
    }
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SelectionContainer {
                Text(
                    code,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(
                onClick = { actions.onCopyCode(code) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Copy")
            }
        }
    }
    GoaButton(onClick = actions.onCodeSaved, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("I've saved it")
    }
}

@Composable
private fun Ready(state: OnlineHomeUi, mapNames: Map<String, String>, actions: OnlineHomeActions) {
    Text("Playing as ${state.name}", style = MaterialTheme.typography.titleMedium)
    if (state.games.isNotEmpty()) MyGames(state, mapNames, actions)
    JoinRoom(state, actions)
    NewRoom(state, mapNames, actions)
    LeaderboardEntry(actions)
    Account(state, actions)
}

@Composable
private fun MyGames(state: OnlineHomeUi, mapNames: Map<String, String>, actions: OnlineHomeActions) {
    OnlineSection("My games")
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        state.games.forEachIndexed { i, game ->
            if (i > 0) HorizontalDivider()
            Surface(
                onClick = { actions.onGame(game) },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            game.seats.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            mapNames[game.mapId] ?: game.mapId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Back to this game")
                }
            }
        }
    }
}

@Composable
private fun JoinRoom(state: OnlineHomeUi, actions: OnlineHomeActions) {
    OnlineSection("Join a room")
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = state.joinText,
                onValueChange = actions.onJoinText,
                label = { Text("Room code or invite link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = actions.onScan,
                    enabled = !state.working,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Scan QR")
                }
                GoaButton(
                    onClick = actions.onJoin,
                    enabled = !state.working,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Join") }
            }
        }
    }
}

@Composable
private fun NewRoom(state: OnlineHomeUi, mapNames: Map<String, String>, actions: OnlineHomeActions) {
    OnlineSection("New room")
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val form = state.room
            Segments(OnlineRooms.options.map { it.mapId }, form.mapId, { mapNames[it] ?: it }, actions.onMap)
            Segments(form.option.teamSizes, form.teamSize, { "${it}v$it" }, actions.onTeamSize)
            Segments(form.option.lengths, form.length, PlayText::length, actions.onLength)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Ranked", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A finished game counts on the public leaderboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Switch(checked = form.ranked, onCheckedChange = actions.onRanked)
            }
            GoaButton(
                onClick = actions.onCreateRoom,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Create room") }
        }
    }
}

@Composable
private fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = bronzeSegments(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun LeaderboardEntry(actions: OnlineHomeActions) {
    OutlinedButton(onClick = actions.onLeaderboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
        Text("Leaderboard")
    }
}

@Composable
private fun Account(state: OnlineHomeUi, actions: OnlineHomeActions) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    OnlineSection("Your account")
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "To play on another device, enter your recovery code there. It works once, and that device gets a " +
                    "new one to save.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { renaming = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Rename") }
                TextButton(onClick = { deleting = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (renaming) RenameDialog(state.name, actions.onRename) { renaming = false }
    if (deleting) {
        ConfirmDialog(
            title = "Delete your account?",
            message = "Your name is removed from the server and the leaderboard: your games stay in the log as " +
                "\"Former player\". You leave any game you're in, and your team still wins or loses. It can't be " +
                "undone.",
            confirmLabel = "Delete account",
            destructive = true,
            icon = Icons.Outlined.PersonRemove,
            onConfirm = {
                deleting = false
                actions.onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    GoaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            GoaButton(
                onClick = {
                    onRename(name)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}
