package dev.quinton.goa.feature.board.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.AccountAvatar
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.IdentitySelector
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.SectionHeaderPaddingTight
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.MyGameState
import dev.quinton.goa.feature.board.play.online.MyGameStatuses
import dev.quinton.goa.feature.board.play.online.OnlineHomeUi
import dev.quinton.goa.feature.board.play.online.OnlineRooms
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.online.parseInvite

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
    /** Every hero by id: a game's seats are its heroes, shown as portraits. */
    heroes: Map<String, BoardHero> = emptyMap(),
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

                AccountStage.READY -> Ready(state, mapNames, heroes, actions)
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
    SectionHeader(title, padding = SectionHeaderPaddingTight)
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
            GoaOutlinedButton(
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
            GoaOutlinedButton(
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
private fun Ready(
    state: OnlineHomeUi,
    mapNames: Map<String, String>,
    heroes: Map<String, BoardHero>,
    actions: OnlineHomeActions,
) {
    // Who this phone plays as, the row Card trivia and Group sync show too; read only here (Rename is in the account),
    // and one compact line, so Create room stays in view.
    IdentitySelector(
        title = "Playing as ${state.name}",
        scope = "Online",
        avatar = { size -> AccountAvatar(state.you ?: state.name, state.name, size = size) },
        compact = true,
    )
    if (state.games.isNotEmpty()) MyGames(state, mapNames, heroes, actions)
    JoinRoom(state, actions)
    NewRoom(state, mapNames, actions)
    LeaderboardEntry(actions)
    Account(state, actions)
}

/**
 * The games to go back to, compact so a new room stays in view. Those waiting on you come first, each on a tight
 * plate of its own with a bronze edge ("Your turn" in teal with a hand: you can act, never colour alone) and Play;
 * the rest in one plate, a line each saying where it stands in words with an icon ("Waiting for Bob", "Finished")
 * and where. Every game shows its heroes as small overlapping portraits in their team's ring; TalkBack hears who
 * plays whom.
 */
@Composable
private fun MyGames(
    state: OnlineHomeUi,
    mapNames: Map<String, String>,
    heroes: Map<String, BoardHero>,
    actions: OnlineHomeActions,
) {
    val (yours, others) = state.games.partition { MyGameStatuses.of(it, state.you).state == MyGameState.YOUR_TURN }
    OnlineSection("My games")
    yours.forEach { game -> YourTurnPlate(game, state, mapNames, heroes, actions) }
    if (others.isNotEmpty()) {
        GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            others.forEachIndexed { i, game ->
                if (i > 0) HorizontalDivider()
                GameRow(game, state, mapNames, heroes, actions)
            }
        }
    }
}

/** A game waiting on you, on a tight plate with a bronze edge: the heroes, "Your turn" and where, and Play. */
@Composable
private fun YourTurnPlate(
    game: MyGame,
    state: OnlineHomeUi,
    mapNames: Map<String, String>,
    heroes: Map<String, BoardHero>,
    actions: OnlineHomeActions,
) {
    val status = MyGameStatuses.of(game, state.you)
    val teal = MaterialTheme.colorScheme.secondary
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .border(2.dp, GoaTheme.colors.selection, PlateShape)
            .semantics(mergeDescendants = true) {
                contentDescription = "${status.words}. ${spokenSeats(game, heroes)}. ${where(game, mapNames)}"
            },
        onClick = { actions.onGame(game) },
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeatPortraits(game, heroes, size = 32.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = teal,
                        modifier = Modifier.size(scaledIconSize(18.dp, 1.4f)),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        status.words,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = teal,
                    )
                }
                Text(
                    where(game, mapNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            GoaButton(
                onClick = { actions.onGame(game) },
                modifier = Modifier.heightIn(min = 48.dp).clearAndSetSemantics {},
            ) { Text("Play") }
        }
    }
}

/** A game that isn't waiting on you, in one line: the heroes, where it stands in words with an icon, and where. */
@Composable
private fun GameRow(
    game: MyGame,
    state: OnlineHomeUi,
    mapNames: Map<String, String>,
    heroes: Map<String, BoardHero>,
    actions: OnlineHomeActions,
) {
    val status = MyGameStatuses.of(game, state.you)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = { actions.onGame(game) },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${status.words}. ${spokenSeats(game, heroes)}. ${where(game, mapNames)}. Back to this game"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatPortraits(game, heroes, size = 26.dp, dim = status.state == MyGameState.OVER)
            Spacer(Modifier.width(Spacing.md))
            Icon(
                when (status.state) {
                    MyGameState.YOUR_TURN -> Icons.Outlined.TouchApp
                    MyGameState.WAITING -> Icons.Outlined.HourglassTop
                    MyGameState.IN_PLAY -> Icons.Outlined.PlayCircleOutline
                    MyGameState.OVER -> Icons.Outlined.Flag
                },
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(scaledIconSize(16.dp, 1.4f)),
            )
            Spacer(Modifier.width(Spacing.xs))
            // Where it stands, then the map on a line of its own: a joined line broke the map's name in two.
            Column(Modifier.weight(1f)) {
                Text(status.words, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(where(game, mapNames), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * A game's heroes as overlapping round portraits, each in its team's ring (the team is in the words too). Red's
 * seats first. Decorative: the row's own words name everyone.
 */
@Composable
private fun SeatPortraits(game: MyGame, heroes: Map<String, BoardHero>, size: Dp, dim: Boolean = false) {
    val seats = game.seats.sortedBy { it.team != Team.RED }
    val step = size * PORTRAIT_OVERLAP
    val ground = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .size(width = size + step * (seats.size - 1).coerceAtLeast(0), height = size)
            .clearAndSetSemantics {}
            .then(if (dim) Modifier.alpha(DIM_FINISHED) else Modifier),
    ) {
        seats.forEachIndexed { i, seat ->
            val hero = heroes.hero(seat.seat)
            HeroPortrait(
                seat.seat,
                hero.portrait,
                null,
                Modifier
                    .offset(x = step * i)
                    .size(size)
                    .border(2.dp, ground, CircleShape)
                    .padding(1.dp)
                    .border(2.dp, teamColor(seat.team), CircleShape)
                    .padding(2.dp),
                CircleShape,
                framed = false,
            )
        }
    }
}

private fun where(game: MyGame, mapNames: Map<String, String>): String = mapNames[game.mapId] ?: game.mapId

/** "Ann as Arien (Red), Bob as Wasp (Blue)": who plays whom, for TalkBack. */
private fun spokenSeats(game: MyGame, heroes: Map<String, BoardHero>): String =
    game.seats.joinToString(", ") { seat ->
        val name = seat.name.ifBlank { "Former player" }
        val hero = heroes[seat.seat]?.name
        (if (hero != null) "$name as $hero" else name) + " (${BoardText.team(seat.team)})"
    }

/** How far each portrait in a stack sits from the last, as a share of its size. */
private const val PORTRAIT_OVERLAP = 0.62f

/** A finished game's portraits step back. */
private const val DIM_FINISHED = 0.6f

/**
 * Join by code or link. The field's label sits above it for good ("Room code or invite link"), and Join stays off
 * until the field holds a plausible code or link; the keyboard's Go with anything else says what a code is, under the
 * field, until the text changes.
 */
@Composable
private fun JoinRoom(state: OnlineHomeUi, actions: OnlineHomeActions) {
    OnlineSection("Join a room")
    val field = rememberTextFieldState(state.joinText)
    var invalid by rememberSaveable { mutableStateOf(false) }
    // The field's text goes to the presenter; the presenter's (cleared after a join) comes back.
    val joinText by rememberUpdatedState(state.joinText)
    val onJoinText by rememberUpdatedState(actions.onJoinText)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect {
            if (it != joinText) {
                invalid = false
                onJoinText(it)
            }
        }
    }
    LaunchedEffect(state.joinText) {
        if (field.text.toString() != state.joinText) field.setTextAndPlaceCursorAtEnd(state.joinText)
    }
    val plausible = parseInvite(inviteText(state.joinText)) != null
    GoaPlate(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                state = field,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text("Room code or invite link") },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(OnlineWords.NOT_A_CODE) }
                } else {
                    null
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go,
                ),
                onKeyboardAction = {
                    if (plausible) actions.onJoin() else invalid = state.joinText.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GoaOutlinedButton(
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
                    enabled = !state.working && plausible,
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
                        "Counts on the public leaderboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                GoaSwitch(checked = form.ranked, onCheckedChange = actions.onRanked)
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
    GoaSegmentedRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            GoaSegment(
                selected = option == selected,
                onClick = { onPick(option) },
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun LeaderboardEntry(actions: OnlineHomeActions) {
    GoaOutlinedButton(onClick = actions.onLeaderboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
        Text("Online ratings")
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
                TextButton(onClick = { renaming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Rename")
                }
                TextButton(
                    onClick = { deleting = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Delete account")
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
