package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.CardDetail
import dev.quinton.goa.feature.board.play.table.CardRow
import dev.quinton.goa.feature.board.play.table.DecisionPanel
import dev.quinton.goa.feature.board.play.table.EventLog
import dev.quinton.goa.feature.board.play.table.GameTable
import dev.quinton.goa.feature.board.play.table.HeroDetail
import dev.quinton.goa.feature.board.play.table.RecentEvents
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi

/** The sheets the game opens (hoisted so screenshots can open one). */
sealed interface PlaySheet {
    data object Log : PlaySheet

    data class Hero(val heroId: String) : PlaySheet

    data class Card(val cardId: String, val back: PlaySheet? = null) : PlaySheet
}

/**
 * The game against the computer: the table, the question or the computer's move in the panel, the
 * log, Take back and the Coach; then the game-over summary. [onTap] reports whether a tap on the
 * board was an option (otherwise a tap on a hero opens its status).
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    onTap: (TableTap) -> Boolean,
    onDelete: () -> Unit,
    onNewGame: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSheet: PlaySheet? = null,
) {
    val play = (state as? GameUiState.Playing)?.play
    var sheet by remember { mutableStateOf(initialSheet) }
    var menu by remember { mutableStateOf(false) }
    var ending by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val viewport = remember { MapViewport() }
    KeepScreenOn(play != null && play.over == null)
    val message = play?.message
    LaunchedEffect(play?.messageId) {
        if (message != null) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
            onAction(GameAction.MessageShown(play.messageId))
        }
    }
    GoaScreen(
        title = when {
            play == null -> "Play"
            play.over != null -> "Game over"
            else -> "Round ${play.table.view.round} · Turn ${play.table.view.turn}"
        },
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (play != null && play.over == null) {
                IconButton(onClick = { onAction(GameAction.TakeBack) }, enabled = play.canTakeBack && !play.busy) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Take back")
                }
                IconButton(onClick = { sheet = PlaySheet.Log }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Game log")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                    GameMenu(play, expanded = menu, onDismiss = { menu = false }, onAction = onAction, onEnd = { ending = true })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                GameUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is GameUiState.Unavailable -> Unavailable(state.why, onDelete, onNewGame, onBack, Modifier.align(Alignment.Center))
                is GameUiState.Playing -> {
                    val over = state.play.over
                    if (over != null) {
                        GameOverContent(state.play, over, onAction, onNewGame, onDone, onHero = { sheet = PlaySheet.Hero(it) })
                    } else {
                        GameTable(
                            table = state.play.table,
                            viewport = viewport,
                            onTap = { tap ->
                                if (!onTap(tap) && tap is TableTap.Piece && state.play.table.heroView(tap.pieceId) != null) {
                                    sheet = PlaySheet.Hero(tap.pieceId)
                                }
                            },
                            onHero = { sheet = PlaySheet.Hero(it) },
                            onFlip = { onAction(GameAction.Flip) },
                        ) {
                            Panel(state.play, onAction, onCard = { sheet = PlaySheet.Card(it.id) })
                        }
                    }
                    // TalkBack hears what just happened.
                    state.play.announcement?.let { said ->
                        Box(
                            Modifier.size(1.dp).semantics {
                                contentDescription = said.text
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                }
            }
        }
    }
    if (play != null) {
        when (val open = sheet) {
            PlaySheet.Log -> BoardSheet(onDismiss = { sheet = null }) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle("Game log", "Every event, newest at the bottom.", onDone = { sheet = null })
                    EventLog(play.log, Modifier.weight(1f))
                }
            }
            is PlaySheet.Hero -> BoardSheet(onDismiss = { sheet = null }) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle(play.table.hero(open.heroId).name, "Open information (p.4)", onDone = { sheet = null })
                    HeroDetail(play.table, open.heroId, onCard = { sheet = PlaySheet.Card(it.id, back = open) })
                }
            }
            is PlaySheet.Card -> BoardSheet(onDismiss = { sheet = null }) {
                val card = play.table.card(open.cardId)
                Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
                    SheetTitle(
                        card?.name ?: "Card",
                        null,
                        onDone = { sheet = open.back },
                    )
                    if (card != null) CardDetail(card)
                }
            }
            null -> Unit
        }
    }
    if (ending) {
        ConfirmDialog(
            title = "End this game?",
            message = "The game is deleted from this phone. It is saved after every move, so you can also just leave it " +
                "and resume later from Boards.",
            confirmLabel = "End game",
            destructive = true,
            icon = Icons.Outlined.Flag,
            onConfirm = {
                ending = false
                onDelete()
            },
            onDismiss = { ending = false },
        )
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun GameMenu(
    play: PlayUi,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (GameAction) -> Unit,
    onEnd: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Column {
                    Text("Coach")
                    Text(
                        if (play.coach) "Nudges before a clearly worse choice" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
            trailingIcon = { Switch(checked = play.coach, onCheckedChange = null) },
            onClick = { onAction(GameAction.SetCoach(!play.coach)) },
        )
        HorizontalDivider()
        Text(
            "COMPUTER SPEED",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        AiPace.entries.forEach { pace ->
            DropdownMenuItem(
                text = { Text(PlayText.pace(pace)) },
                leadingIcon = {
                    if (pace == play.pace) Icon(Icons.Outlined.Check, contentDescription = "Chosen") else Spacer(Modifier.size(24.dp))
                },
                onClick = {
                    onAction(GameAction.SetPace(pace))
                    onDismiss()
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Flip the board") },
            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(GameAction.Flip)
            },
        )
        DropdownMenuItem(
            text = { Text("End this game", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onEnd()
            },
        )
    }
}

/** The panel's content: a nudge, the question, the computer's move or the wait, the reveal, your hand. */
@Composable
private fun ColumnScope.Panel(play: PlayUi, onAction: (GameAction) -> Unit, onCard: (dev.quinton.goa.core.model.catalog.Card) -> Unit) {
    val table = play.table
    val nudge = play.nudge
    when {
        nudge != null -> CoachNudge(nudge, onAction)
        play.status is TurnStatus.YourTurn -> {
            if (play.reveal.isNotEmpty() && table.decision?.kind != dev.quinton.goa.core.game.DecisionKind.PLAY_CARD) {
                RevealLine(table, play.reveal)
            }
            DecisionPanel(
                table,
                onSelect = { onAction(GameAction.Select(it)) },
                onAnswer = { onAction(GameAction.Answer(it)) },
                onCard = onCard,
                busy = play.busy,
            )
            if (play.busy && play.coach) CoachLooking()
        }
        play.status is TurnStatus.Computer -> {
            ComputerMove(table, play.status)
            if (play.reveal.isNotEmpty()) Reveal(table, play.reveal)
        }
        play.status is TurnStatus.Waiting -> Waiting(table, play.status)
        else -> Unit
    }
    when {
        nudge != null -> Unit
        play.status is TurnStatus.YourTurn -> RecentEvents(play.recent.takeLast(2), Modifier.padding(top = Spacing.xs))
        else -> RecentEvents(play.recent)
    }
    if (!table.handDecision && nudge == null) YourHand(table, onCard)
}

/** A gentle nudge before a clearly worse choice: never blocks, never chooses for you. */
@Composable
private fun CoachNudge(nudge: PendingNudge, onAction: (GameAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Coach: ${nudge.note.text}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (nudge.note.detail.isNotBlank()) {
                Text(nudge.note.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                "Your choice: ${nudge.choiceLabel}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { onAction(GameAction.ChooseAnyway) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Choose anyway") }
                Button(onClick = { onAction(GameAction.Rethink) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Rethink")
                }
            }
        }
    }
}

@Composable
private fun CoachLooking() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text("Coach is looking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The computer is choosing for one of its heroes; "thinking…" once it takes a while. */
@Composable
private fun ComputerMove(table: TableUi, status: TurnStatus.Computer) {
    val hero = table.heroView(status.heroId)
    val info = table.hero(status.heroId)
    val level = (hero?.controller as? Controller.Ai)?.level
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        HeroPortrait(
            status.heroId,
            info.portrait,
            null,
            Modifier.size(40.dp).border(2.dp, hero?.team?.let { teamColor(it) } ?: GoaTheme.colors.engravedBorder, CircleShape),
            shape = CircleShape,
            framed = false,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                "COMPUTER${level?.let { " · ${PlayText.level(it).uppercase()}" }.orEmpty()}",
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (status.thinking) "${info.name} is thinking…" else "${info.name} is choosing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            table.decision?.prompt?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (status.thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
}

/** Simultaneous play (p.7): the person's card is down (shown to them only), the others still choose. */
@Composable
private fun Waiting(table: TableUi, status: TurnStatus.Waiting) {
    val mine = table.mine.mapNotNull(table::heroView)
    Row(verticalAlignment = Alignment.CenterVertically) {
        mine.forEach { hero ->
            TableCard(table.card(hero.played?.cardId), width = 40.dp)
            Spacer(Modifier.width(Spacing.xs))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text("Your card is down", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Waiting for ${status.heroIds.joinToString(" and ") { table.hero(it).name }.ifEmpty { "the others" }} " +
                    "to play facedown. Cards are revealed together (p.7).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** The reveal (p.7): every played card with its initiative, highest first. */
@Composable
private fun Reveal(table: TableUi, reveal: List<RevealedCard>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("CARDS REVEALED · p.7", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            reveal.forEach { shown ->
                val hero = table.heroView(shown.heroId)
                val card = table.card(shown.cardId)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "${table.hero(shown.heroId).name}: " +
                            (card?.let { "${it.name}, initiative ${shown.initiative ?: "none"}" } ?: "no card")
                    },
                ) {
                    TableCard(card, width = 56.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        hero?.let { TeamMark(it.team, markSize = 8.dp) }
                        Spacer(Modifier.width(3.dp))
                        Text(table.hero(shown.heroId).name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    shown.initiative?.let {
                        Text("Initiative $it", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The reveal in one line, while the person answers. */
@Composable
private fun RevealLine(table: TableUi, reveal: List<RevealedCard>) {
    Text(
        "Revealed: " + reveal.joinToString(" · ") { "${table.hero(it.heroId).name} ${it.initiative ?: "–"}" },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The person's hand, small; tap a card to read it. */
@Composable
private fun YourHand(table: TableUi, onCard: (dev.quinton.goa.core.model.catalog.Card) -> Unit) {
    table.mine.forEach { heroId ->
        val hand = table.heroView(heroId)?.hand ?: return@forEach
        if (hand.isEmpty()) return@forEach
        Text(
            if (table.mine.size > 1) "${table.hero(heroId).name.uppercase()}'S HAND" else "YOUR HAND",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        CardRow(hand.map { CardView(it) }, table, onCard)
    }
}

@Composable
private fun Unavailable(why: NoGame, onDelete: () -> Unit, onNewGame: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val (title, text) = when (why) {
        NoGame.NONE -> "No game in progress" to "Set up a game against the computer to play on the real map."
        NoGame.UNREADABLE -> "This saved game can't be read" to
            "It may have been damaged, or saved by another version of the app. Delete it to start a new one."
        NoGame.INCOMPATIBLE -> "This game can't be continued" to
            "The rules engine has changed since it was saved, so it can't be rebuilt move for move. Delete it to start a new one."
        NoGame.MAP_MISSING -> "This game's map isn't in this build" to "Delete it to start a new one."
    }
    EmptyState(
        icon = Icons.Outlined.Hexagon,
        title = title,
        message = text,
        modifier = modifier,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (why == NoGame.NONE) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                    Button(onClick = onNewGame, modifier = Modifier.heightIn(min = 48.dp)) { Text("New game") }
                } else {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                    Button(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete it") }
                }
            }
        },
    )
}

private const val SHEET_HEIGHT = 0.9f
