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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.review.ReviewButton
import dev.quinton.goa.feature.board.play.review.ReviewOpenEffect
import dev.quinton.goa.feature.board.play.review.ReviewSheet
import dev.quinton.goa.feature.board.play.review.ReviewTab
import dev.quinton.goa.feature.board.play.table.DecisionPanel
import dev.quinton.goa.feature.board.play.table.EventLog
import dev.quinton.goa.feature.board.play.table.GameTable
import dev.quinton.goa.feature.board.play.table.HeroDetail
import dev.quinton.goa.feature.board.play.table.MomentCard
import dev.quinton.goa.feature.board.play.table.PeekConfirm
import dev.quinton.goa.feature.board.play.table.ResolutionFlowView
import dev.quinton.goa.feature.board.play.table.SpellbookSheet
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.YourHand
import dev.quinton.goa.feature.board.play.table.spellbookUi
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.ui.BoardScaffold
import dev.quinton.goa.feature.board.ui.cards.CardFocusHost
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState

/** The sheets the game opens (hoisted so screenshots can open one). */
sealed interface PlaySheet {
    data object Log : PlaySheet

    data class Hero(val heroId: String) : PlaySheet

    /** The game-state review (ADR-037). */
    data class Review(val tab: ReviewTab = ReviewTab.NOW) : PlaySheet

    /** A hero's spellbook (Gydion, ADR-040): from the table's Spellbook control or the hero sheet. */
    data class Spellbook(val heroId: String) : PlaySheet
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
    initialPanelHidden: Boolean = false,
) {
    val play = (state as? GameUiState.Playing)?.play
    var sheet by remember { mutableStateOf(initialSheet) }
    var menu by remember { mutableStateOf(false) }
    var ending by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val viewport = remember { MapViewport() }
    val focus = rememberCardFocusState()
    KeepScreenOn(play != null && play.over == null)
    val message = play?.message
    LaunchedEffect(play?.messageId) {
        if (message != null) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
            onAction(GameAction.MessageShown(play.messageId))
        }
    }
    // The title shortens to fit beside the three actions: on a phone in portrait "R2 · Turn 3",
    // with large text "R2 · T3" (the HUD and TalkBack say it in full).
    val room = LocalConfiguration.current.screenWidthDp / androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoardScaffold(
        title = when {
            play == null -> "Play"
            play.over != null -> "Game over"
            else -> roundTitle(play.table.view.round, play.table.view.turn, room)
        },
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (play != null && play.over == null) {
                IconButton(onClick = { onAction(GameAction.TakeBack) }, enabled = play.canTakeBack && !play.busy) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Take back")
                }
                ReviewButton(onClick = { sheet = PlaySheet.Review() })
                IconButton(onClick = { sheet = PlaySheet.Log }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Game log")
                }
                Box {
                    IconButton(onClick = {
                        menu = true
                    }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                    GameMenu(play, expanded = menu, onDismiss = { menu = false }, onAction = onAction, onEnd = {
                        ending =
                            true
                    })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                GameUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is GameUiState.Unavailable -> Unavailable(
                    state.why,
                    onDelete,
                    onNewGame,
                    onBack,
                    Modifier.align(Alignment.Center),
                )

                is GameUiState.Playing -> {
                    val over = state.play.over
                    if (over != null) {
                        GameOverContent(state.play, over, onAction, onNewGame, onDone, onHero = {
                            sheet =
                                PlaySheet.Hero(it)
                        })
                    } else {
                        GameTable(
                            table = state.play.table,
                            viewport = viewport,
                            onTap = { tap ->
                                if (!onTap(tap) && tap is TableTap.Piece &&
                                    state.play.table.heroView(tap.pieceId) != null
                                ) {
                                    sheet = PlaySheet.Hero(tap.pieceId)
                                }
                            },
                            onHero = { sheet = PlaySheet.Hero(it) },
                            onFlip = { onAction(GameAction.Flip) },
                            peek = gamePeek(state.play),
                            peekTrailing = {
                                if (state.play.status is TurnStatus.YourTurn && state.play.nudge == null) {
                                    PeekConfirm(state.play.table, state.play.busy) { onAction(GameAction.Answer(it)) }
                                }
                            },
                            initialPanelHidden = initialPanelHidden,
                            focus = focus,
                            onSpellbook = { sheet = PlaySheet.Spellbook(it) },
                        ) {
                            Panel(state.play, onAction, focus)
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
    ReviewOpenEffect(sheet) { onAction(GameAction.Review(it)) }
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
                    HeroDetail(play.table, open.heroId, focus, onSpellbook = { sheet = PlaySheet.Spellbook(it) })
                }
            }

            is PlaySheet.Review -> ReviewSheet(play.table, play.review, open, {
                sheet = it
            }, { onAction(GameAction.Review(it)) }, focus)

            is PlaySheet.Spellbook -> spellbookUi(play.table, open.heroId)?.let { ui ->
                SpellbookSheet(ui, focus, onDismiss = { sheet = null })
            }

            null -> Unit
        }
    }
    CardFocusHost(focus)
    if (ending) {
        ConfirmDialog(
            title = "End this game?",
            message =
                "The game is deleted from this phone. It is saved after every move, so you can also just leave it " +
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
                    if (pace ==
                        play.pace
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Chosen")
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
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
            leadingIcon = {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = {
                onDismiss()
                onEnd()
            },
        )
    }
}

/**
 * The panel's content: a nudge, the moment the table is at (the reveal, a turn or round over), the
 * question, the computer's move or the wait, what happened that the board doesn't show, your hand.
 * When the question is the person's own, it comes first and the moment after it.
 * The turn's cards themselves are on the turn track at the top (ADR-041).
 */
@Composable
private fun ColumnScope.Panel(play: PlayUi, onAction: (GameAction) -> Unit, focus: CardFocusState) {
    val table = play.table
    val nudge = play.nudge
    val moment = table.moment.takeIf { nudge == null }
    // The person's own question comes before the moment (the reveal, a turn or the round ending): at
    // large text the round's summary filled the panel and the question's Confirm was below the fold.
    val questionFirst = play.status is TurnStatus.YourTurn
    if (moment != null && !questionFirst) MomentCard(moment, table, focus = focus)
    when {
        nudge != null -> CoachNudge(nudge, onAction)

        play.status is TurnStatus.YourTurn -> {
            DecisionPanel(
                table,
                onSelect = { onAction(GameAction.Select(it)) },
                onAnswer = { onAction(GameAction.Answer(it)) },
                focus = focus,
                busy = play.busy,
            )
            if (play.busy && play.coach) CoachLooking()
        }

        play.status is TurnStatus.Computer -> ComputerMove(table, play.status, focus)

        play.status is TurnStatus.Waiting -> Waiting(table, play.status)

        else -> Unit
    }
    if (moment != null && questionFirst) MomentCard(moment, table, focus = focus)
    // What happened since the person last chose, one entry per card resolved (ADR-046).
    if (nudge == null) ResolutionFlowView(play.flow, table, focus, Modifier.padding(top = Spacing.xs))
    if (!table.handDecision && nudge == null) YourHand(table, focus)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                },
            ) {
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
                Text(
                    nudge.note.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                Button(onClick = {
                    onAction(GameAction.Rethink)
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
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
        Text(
            "Coach is looking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The computer is choosing for one of its heroes ("thinking…" once it takes a while), with the card
 * it is resolving beside it (tap to read it).
 */
@Composable
private fun ComputerMove(table: TableUi, status: TurnStatus.Computer, focus: CardFocusState) {
    val hero = table.heroView(status.heroId)
    val info = table.hero(status.heroId)
    val computer = hero?.controller as? Controller.Ai
    val card = table.sourceCard ?: hero?.played?.takeIf { !it.facedown }?.cardId?.let(table::card)
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeroPortrait(
            status.heroId,
            info.portrait,
            null,
            Modifier.size(40.dp).border(
                2.dp,
                hero?.team?.let {
                    teamColor(it)
                } ?: GoaTheme.colors.engravedBorder,
                CircleShape,
            ),
            shape = CircleShape,
            framed = false,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(
                "COMPUTER${computer?.let { " · ${PlayText.strength(it).uppercase()}" }.orEmpty()}",
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
        if (card != null && table.decision?.kind != DecisionKind.PLAY_CARD) {
            Spacer(Modifier.width(Spacing.sm))
            Surface(
                onClick = { focus.show(listOf(card), title = info.name) },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .cardFocusOrigin(focus, "computer/${card.id}")
                    .clearAndSetSemantics {
                        contentDescription = "${info.name}'s card: ${spokenCard(card)}"
                        role = Role.Button
                        onClick(label = "Read the card") {
                            focus.show(listOf(card), title = info.name)
                            true
                        }
                    },
            ) { TableCard(card, width = 56.dp) }
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
                "Waiting for ${status.heroIds.joinToString(" and ") {
                    table.hero(it).name
                }.ifEmpty { "the others" }} " +
                    "to play facedown. Cards are revealed together (p.7).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun Unavailable(
    why: NoGame,
    onDelete: () -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val (title, text) = when (why) {
        NoGame.NONE -> "No game in progress" to "Set up a game against the computer to play on the real map."

        NoGame.UNREADABLE ->
            "This saved game can't be read" to
                "It may have been damaged, or saved by another version of the app. Delete it to start a new one."

        NoGame.INCOMPATIBLE ->
            "This game can't be continued" to
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

/** The game's title for [room] dp of width at font scale 1. */
internal fun roundTitle(round: Int, turn: Int, room: Float): String = when {
    room >= FULL_TITLE_DP -> "Round $round · Turn $turn"
    room >= SHORT_TITLE_DP -> "R$round · Turn $turn"
    else -> "R$round · T$turn"
}

private const val SHEET_HEIGHT = 0.9f
private const val FULL_TITLE_DP = 400f
private const val SHORT_TITLE_DP = 300f
