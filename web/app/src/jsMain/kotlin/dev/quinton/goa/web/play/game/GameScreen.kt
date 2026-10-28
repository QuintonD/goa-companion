package dev.quinton.goa.web.play.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.HeroResult
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.PendingNudge
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.gamePeek
import dev.quinton.goa.feature.board.play.game.roundTitle
import dev.quinton.goa.feature.board.play.game.withoutWhen
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.cards.CardFocusHost
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.play.table.Announce
import dev.quinton.goa.web.play.table.DecisionPanel
import dev.quinton.goa.web.play.table.EventLog
import dev.quinton.goa.web.play.table.GameReviewSheet
import dev.quinton.goa.web.play.table.GameTableView
import dev.quinton.goa.web.play.table.HeroDetail
import dev.quinton.goa.web.play.table.HeroPortrait
import dev.quinton.goa.web.play.table.MomentCard
import dev.quinton.goa.web.play.table.Overline
import dev.quinton.goa.web.play.table.Quiet
import dev.quinton.goa.web.play.table.RecentEvents
import dev.quinton.goa.web.play.table.YourHand
import dev.quinton.goa.web.play.table.rememberTableCamera
import dev.quinton.goa.web.play.table.teamCss
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The game against the computer (#/play, PRODUCT §9): the table, the question or the computer's move
 * in the panel, the log, the game state, Take back and the Coach; then the game-over summary. With
 * nothing saved it offers New game; a save that can't be read or rebuilt is offered for deletion.
 */
@Composable
fun GameScreen() {
    val services = LocalServices.current
    val data = services.data
    val router = services.router
    val scope = rememberCoroutineScope()
    val controller = remember {
        GameController(
            engine = data.play.engine,
            coach = data.play.coach,
            saves = data.play.gameSave,
            catalog = { data.content.catalog.catalog.first() },
            maps = { data.play.boardMaps.maps.first() },
            clock = data.clock,
            scope = scope,
            onMessage = { services.messages.show(it) },
        )
    }
    LaunchedEffect(controller) { controller.start() }
    val state by controller.uiState.collectAsState()
    val focus = remember { CardFocusState() }
    val camera = rememberTableCamera()
    var sheet by remember { mutableStateOf<PlaySheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var panelShown by remember { mutableStateOf(true) }
    val play = (state as? GameUiState.Playing)?.play
    KeepAwake(play != null && play.over == null)
    val onAction: (GameAction) -> Unit = controller::onAction
    val newGame = {
        controller.discardFinished()
        val setup = controller.setup
        router.navigate(
            Route.NewGame(
                setup?.seats?.filter { it.team == Team.RED }?.map { it.heroId }.orEmpty(),
                setup?.seats?.filter { it.team == Team.BLUE }?.map { it.heroId }.orEmpty(),
            ),
            replace = true,
        )
    }
    when (val s = state) {
        GameUiState.Loading -> Screen("Play") { LoadingState("Setting up the table") }

        is GameUiState.Unavailable -> Screen("Play") {
            Unavailable(s.why, onDelete = {
                controller.deleteSaved()
            }, onNewGame = { router.navigate(Route.NewGame(), replace = true) })
        }

        is GameUiState.Playing -> {
            val over = s.play.over
            if (over != null) {
                Screen("Game over") {
                    GameOver(s.play, over, onAction, onNewGame = newGame, onDone = {
                        controller.discardFinished()
                        router.back()
                    }, onHero = { sheet = PlaySheet.Hero(it) })
                }
            } else {
                val view = s.play.table.view
                val fontScale = fontScale()
                GameTableView(
                    title = roundTitle(view.round, view.turn, window.innerWidth / fontScale),
                    actions = {
                        Button({
                            classes("goa-icon-button")
                            attr("type", "button")
                            attr("aria-label", "Take back")
                            attr("title", "Take back")
                            if (!s.play.canTakeBack || s.play.busy) attr("disabled", "")
                            onClick { onAction(GameAction.TakeBack) }
                        }) { Icon("undo") }
                        IconButton("article", "Game state", {
                            sheet = PlaySheet.Review()
                            onAction(GameAction.Review(ReviewAction.Open(true)))
                        })
                        IconButton("list", "Game log", { sheet = PlaySheet.Log })
                        IconButton("more", "More options", { menu = true })
                    },
                    table = s.play.table,
                    camera = camera,
                    focus = focus,
                    onTap = { tap ->
                        if (!controller.tap(tap) && tap is TableTap.Piece &&
                            s.play.table.heroView(tap.pieceId) != null
                        ) {
                            sheet = PlaySheet.Hero(tap.pieceId)
                        }
                    },
                    onHero = { sheet = PlaySheet.Hero(it) },
                    onFlip = { onAction(GameAction.Flip) },
                    panelShown = panelShown,
                    onPanelShown = { panelShown = it },
                    peek = gamePeek(s.play),
                    peekActions = {
                        if (s.play.status is TurnStatus.YourTurn && s.play.nudge == null) {
                            PeekConfirm(s.play.table, s.play.busy) { onAction(GameAction.Answer(it)) }
                        }
                    },
                ) { Panel(s.play, onAction, focus) }
            }
            Announce(s.play.announcement?.text)
            when (val open = sheet) {
                PlaySheet.Log -> BottomSheet("Game log", onDismiss = { sheet = null }) {
                    Quiet("Every event, newest at the bottom.")
                    EventLog(s.play.log)
                }

                // The table's Spellbook (Android) is the hero sheet's spellbook section here.
                is PlaySheet.Spellbook -> BottomSheet(
                    s.play.table.hero(open.heroId).name,
                    onDismiss = { sheet = null },
                ) {
                    Quiet("Open information (p.4)")
                    HeroDetail(s.play.table, open.heroId, focus)
                }

                is PlaySheet.Hero -> BottomSheet(s.play.table.hero(open.heroId).name, onDismiss = { sheet = null }) {
                    Quiet("Open information (p.4)")
                    HeroDetail(s.play.table, open.heroId, focus)
                }

                is PlaySheet.Review -> GameReviewSheet(
                    table = s.play.table,
                    review = s.play.review,
                    tab = open.tab,
                    onTab = { sheet = PlaySheet.Review(it) },
                    onAction = { onAction(GameAction.Review(it)) },
                    onDismiss = {
                        sheet = null
                        onAction(GameAction.Review(ReviewAction.Open(false)))
                    },
                    focus = focus,
                )

                null -> Unit
            }
            if (menu) GameMenu(s.play, onAction, onDismiss = { menu = false }, onEnd = { ending = true })
        }
    }
    CardFocusHost(focus)
    if (ending) {
        AlertDialog(
            "End this game?",
            onDismiss = { ending = false },
            buttons = {
                GoaButton("Cancel", { ending = false }, ButtonKind.TEXT)
                GoaButton("End game", {
                    ending = false
                    controller.deleteSaved { router.back() }
                }, ButtonKind.DANGER)
            },
        ) {
            Text(
                "The game is deleted from this browser. It is saved after every move, so you can also just " +
                    "leave it and resume later from Boards.",
            )
        }
    }
}

/** The root font size against 16 px: iOS text size (and the 200% check) scales it. */
private fun fontScale(): Float {
    val size =
        window.getComputedStyle(document.documentElement!!).fontSize.removeSuffix("px").toFloatOrNull() ?: BASE_FONT
    return (size / BASE_FONT).coerceAtLeast(1f)
}

private const val BASE_FONT = 16f

/** Keeps the screen on while a game is on the table (Screen Wake Lock, where the browser has it). */
@Composable
private fun KeepAwake(on: Boolean) {
    DisposableEffect(on) {
        var lock: dynamic = null
        val wakeLock = window.navigator.asDynamic().wakeLock
        if (on && wakeLock != undefined) {
            runCatching {
                wakeLock.request("screen").then({ l: dynamic -> lock = l }, { _: dynamic -> })
            }
        }
        onDispose { if (lock != null) runCatching { lock.release() } }
    }
}

/** Confirm in the hidden panel's peek bar: the space or piece tapped on the map answers. */
@Composable
fun PeekConfirm(table: TableUi, busy: Boolean, onAnswer: (Choice) -> Unit) {
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token } ?: return
    if (!table.asking || table.handDecision) return
    Button({
        classes("goa-button", "filled", "goa-peek-confirm")
        attr("type", "button")
        attr("aria-label", "Confirm · ${table.label(selected)}")
        if (busy) attr("disabled", "")
        onClick { onAnswer(selected) }
    }) { Text("Confirm") }
}

/**
 * The panel: a nudge, the moment the table is at, the question (or the computer's move, or the wait),
 * what happened lately, your hand (the turn's cards are on the track at the top, ADR-041).
 */
@Composable
private fun Panel(play: PlayUi, onAction: (GameAction) -> Unit, focus: CardFocusState) {
    val table = play.table
    val nudge = play.nudge
    val status = play.status
    table.moment.takeIf { nudge == null }?.let { MomentCard(it, table, focus) }
    when {
        nudge != null -> CoachNudge(nudge, onAction)

        status is TurnStatus.YourTurn -> {
            DecisionPanel(
                table,
                onSelect = { onAction(GameAction.Select(it)) },
                onAnswer = { onAction(GameAction.Answer(it)) },
                focus = focus,
                busy = play.busy,
            )
            if (play.busy && play.coach) CoachLooking()
        }

        status is TurnStatus.Computer -> ComputerMove(table, status, focus)

        status is TurnStatus.Waiting -> Waiting(table, status)

        else -> Unit
    }
    if (nudge == null) {
        RecentEvents(play.recent, if (status is TurnStatus.YourTurn) play.recentStrong else emptySet())
        if (!table.handDecision) YourHand(table, focus)
    }
}

@Composable
private fun CoachLooking() {
    P({ classes("t-body-small", "goa-quiet", "goa-busy-line") }) { Text("Coach is looking…") }
}

/** A gentle nudge before a clearly worse choice: never blocks, never chooses for you. */
@Composable
private fun CoachNudge(nudge: PendingNudge, onAction: (GameAction) -> Unit) {
    Div({
        classes("goa-nudge")
        attr("role", "status")
    }) {
        P({
            classes("t-title-small")
            attr("aria-live", "polite")
        }) {
            Icon("lightbulb", IconSize.SMALL)
            Text(" Coach: ${nudge.note.text}")
        }
        if (nudge.note.detail.isNotBlank()) P({ classes("t-body-medium") }) { Text(nudge.note.detail) }
        P({ classes("t-label-large") }) { Text("Your choice: ${nudge.choiceLabel}") }
        Div({ classes("goa-button-row", "split") }) {
            GoaButton("Choose anyway", { onAction(GameAction.ChooseAnyway) }, ButtonKind.OUTLINED)
            GoaButton("Rethink", { onAction(GameAction.Rethink) })
        }
    }
}

/** The computer choosing for one of its heroes ("thinking…" once it takes a while), with its card. */
@Composable
private fun ComputerMove(table: TableUi, status: TurnStatus.Computer, focus: CardFocusState) {
    val hero = table.heroView(status.heroId)
    val info = table.hero(status.heroId)
    val ai = hero?.controller as? Controller.Ai
    val card = table.sourceCard ?: hero?.played?.takeIf { !it.facedown }?.cardId?.let(table::card)
    Div({
        classes("goa-computer")
        attr("aria-live", "polite")
    }) {
        HeroPortrait(info, hero?.team, 40)
        Div({ classes("goa-computer-text") }) {
            P({ classes("t-overline", "goa-table-overline") }) {
                Text("Computer" + (ai?.let { " · ${AiStrengths.label(it)}" } ?: ""))
            }
            P({ classes("t-title-medium") }) {
                Text(if (status.thinking) "${info.name} is thinking…" else "${info.name} is choosing")
            }
            table.decision?.prompt?.let { P({ classes("t-body-small", "goa-quiet") }) { Text(it) } }
        }
        if (card != null && table.decision?.kind != DecisionKind.PLAY_CARD) {
            Button({
                classes("goa-thumb")
                attr("type", "button")
                attr("aria-label", "${info.name}'s card: ${spokenCard(card)}")
                onClick { focus.show(listOf(card), title = info.name) }
            }) { CardImage(card, 56) }
        }
    }
    if (status.thinking) Div({ classes("goa-progress", "indeterminate") }) { Div({ classes("goa-progress-bar") }) }
}

/** Simultaneous play (p.7): the person's card is down (theirs to see only), the others still choose. */
@Composable
private fun Waiting(table: TableUi, status: TurnStatus.Waiting) {
    Div({
        classes("goa-computer")
        attr("aria-live", "polite")
    }) {
        table.mine.mapNotNull(table::heroView).forEach { hero -> CardImage(table.card(hero.played?.cardId), 40) }
        Div({ classes("goa-computer-text") }) {
            P({ classes("t-title-medium") }) { Text("Your card is down") }
            P({ classes("t-body-small", "goa-quiet") }) {
                Text(
                    "Waiting for ${status.heroIds.joinToString(" and ") {
                        table.hero(it).name
                    }.ifEmpty { "the others" }} " +
                        "to play facedown. Cards are revealed together (p.7).",
                )
            }
        }
    }
}

@Composable
private fun Unavailable(why: NoGame, onDelete: () -> Unit, onNewGame: () -> Unit) {
    val router = LocalServices.current.router
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
    EmptyState("board", title, text) {
        Div({ classes("goa-button-row") }) {
            GoaButton("Back", { router.back() }, ButtonKind.TEXT)
            if (why == NoGame.NONE) GoaButton("New game", onNewGame) else GoaButton("Delete it", onDelete)
        }
    }
}

/** The game's menu: Coach on/off, the computer's speed, Flip, End this game. */
@Composable
private fun GameMenu(play: PlayUi, onAction: (GameAction) -> Unit, onDismiss: () -> Unit, onEnd: () -> Unit) {
    BottomSheet("Game options", onDismiss = onDismiss) {
        Label(attrs = { classes("goa-switch-row") }) {
            Span({ classes("goa-switch-text") }) {
                Span({ classes("t-title-small") }) { Text("Coach") }
                Span({ classes("t-body-small", "goa-quiet") }) {
                    Text(if (play.coach) "Nudges before a clearly worse choice" else "Off")
                }
            }
            Input(InputType.Checkbox) {
                classes("goa-switch")
                checked(play.coach)
                onChange { onAction(GameAction.SetCoach(it.value)) }
            }
        }
        Overline("Computer speed")
        Div({
            classes("goa-segmented")
            attr("role", "radiogroup")
            attr("aria-label", "Computer speed")
        }) {
            AiPace.entries.forEach { pace ->
                Button({
                    classes("goa-segment")
                    if (pace == play.pace) classes("selected")
                    attr("type", "button")
                    attr("role", "radio")
                    attr("aria-checked", (pace == play.pace).toString())
                    onClick { onAction(GameAction.SetPace(pace)) }
                }) { Text(PlayText.pace(pace)) }
            }
        }
        Div({ classes("goa-menu-actions") }) {
            GoaButton("Flip the board", {
                onDismiss()
                onAction(GameAction.Flip)
            }, ButtonKind.TONAL, icon = "flip", block = true)
            GoaButton("End this game", {
                onDismiss()
                onEnd()
            }, ButtonKind.DANGER, icon = "flag", block = true)
        }
    }
}

/**
 * Game over: who won and how, in the rulebook's words; how each hero did; take backs and nudges; the
 * coach's turning points; Rematch (same setup, a new coin flip), New game, Done.
 */
@Composable
private fun GameOver(
    play: PlayUi,
    over: GameOverUi,
    onAction: (GameAction) -> Unit,
    onNewGame: () -> Unit,
    onDone: () -> Unit,
    onHero: (String) -> Unit,
) {
    Div({ classes("goa-game-over") }) {
        Div({ classes("goa-over-card", teamCss(over.winner)) }) {
            Icon("swords", IconSize.LARGE)
            H2({ classes("t-headline-small") }) { Text(if (over.youWon) "You won" else "The computer won") }
            P({ classes("t-title-medium", "goa-over-winner", teamCss(over.winner)) }) {
                TeamMark(over.winner)
                Text(" ${BoardText.team(over.winner)} wins · ${PlayText.winTitle(over.reason)}")
            }
            P({ classes("t-body-medium") }) { Text(PlayText.winReason(over.reason, over.winner)) }
            P({ classes("t-label-large", "goa-quiet") }) {
                Text(
                    listOf(
                        BoardText.count(over.rounds, "round"),
                        BoardText.count(over.takeBacks, "take back"),
                        if (over.coach) BoardText.count(over.nudges, "coach nudge") else "Coach off",
                    ).joinToString(" · "),
                )
            }
        }
        Overline("How each hero did")
        Team.entries.forEach { team ->
            over.heroes.filter { it.team == team }.forEach { HeroResultRow(play, it, onHero) }
        }
        if (over.coach) {
            Overline("Coach · turning points")
            val points = over.turningPoints
            when {
                points == null -> P({
                    classes("t-body-medium", "goa-busy-line")
                }) { Text("The coach is going through the game…") }

                points.isEmpty() -> Quiet("No single moment decided this one.")

                else -> points.forEach { note ->
                    Div({ classes("goa-nudge") }) {
                        val at = listOfNotNull(
                            note.round?.let {
                                "Round $it"
                            },
                            note.turn?.let { "Turn $it" },
                        ).joinToString(" · ")
                        if (at.isNotEmpty()) P({ classes("t-overline") }) { Text(at) }
                        P({
                            classes("t-title-small")
                        }) { Text(if (at.isNotEmpty()) withoutWhen(note.text) else note.text) }
                        if (note.detail.isNotBlank()) P({ classes("t-body-medium") }) { Text(note.detail) }
                    }
                }
            }
        }
        Div({ classes("goa-over-actions") }) {
            GoaButton("Rematch", { onAction(GameAction.Rematch) }, block = true)
            Quiet("Same heroes and seats; the app flips the Tie Breaker coin again.")
            GoaButton("New game", onNewGame, ButtonKind.OUTLINED, block = true)
            GoaButton("Done", onDone, ButtonKind.TEXT, block = true)
        }
    }
}

@Composable
private fun HeroResultRow(play: PlayUi, result: HeroResult, onHero: (String) -> Unit) {
    val info = play.table.hero(result.heroId)
    val who = if (result.controller == Controller.Human) "You" else PlayText.controller(result.controller)
    val facts = listOf(
        "Level ${result.level}",
        BoardText.count(result.heroDefeats, "hero defeated", "heroes defeated"),
        BoardText.count(result.minionDefeats, "minion defeated", "minions defeated"),
        "defeated ${BoardText.count(result.defeated, "time")}",
        BoardText.count(result.coins, "coin") + " gained",
    )
    Button({
        classes("goa-result-row")
        attr("type", "button")
        attr("aria-label", "${info.name}, ${BoardText.team(result.team)}, $who. " + facts.joinToString(", "))
        onClick { onHero(result.heroId) }
    }) {
        HeroPortrait(info, result.team, 40)
        Span({ classes("goa-result-text") }) {
            Span({ classes("t-title-small") }) { Text("${info.name} · $who") }
            Span({ classes("t-body-small", "goa-quiet") }) { Text(facts.joinToString(" · ")) }
        }
    }
}
