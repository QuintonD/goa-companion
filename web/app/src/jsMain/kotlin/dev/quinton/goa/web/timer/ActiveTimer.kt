package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.timer.ActiveUiState
import dev.quinton.goa.feature.timer.ui.timer.ChecklistItemUi
import dev.quinton.goa.feature.timer.ui.timer.ChecklistState
import dev.quinton.goa.feature.timer.ui.timer.RoundPipsUi
import dev.quinton.goa.feature.timer.ui.timer.RoundRowState
import dev.quinton.goa.feature.timer.ui.timer.RoundRowUi
import dev.quinton.goa.feature.timer.ui.timer.SeatUi
import dev.quinton.goa.feature.timer.ui.timer.ShotClockUi
import dev.quinton.goa.feature.timer.ui.timer.TimeOutsUi
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.HeroArt
import dev.quinton.goa.web.table.MenuItem
import dev.quinton.goa.web.table.MenuSheet
import dev.quinton.goa.web.table.TeamBadge
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/*
 * The running timer (the phone's ActiveTimer + RoundView): a full-screen table tool, the tab bar hidden
 * so nobody mis-taps beside Next. Step view (the big dial) or round view (the whole round on one list),
 * portrait or landscape (goa.css lays the same parts out in both). Big digits, Pause and Next at the
 * bottom edge, Previous phase up by the step, the screen kept awake (Screen Wake Lock) and the cues
 * sounded and flashed by TimerModel.
 */
@Composable
internal fun ActiveTimer(state: ActiveUiState, model: TimerModel) {
    val services = LocalServices.current
    val table = services.data.table
    var confirmEnd by remember { mutableStateOf(false) }
    var coinDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var timeOutSheet by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf<RoundRowUi?>(null) }
    KeepScreenOn(table.prefs.prefs.keepScreenOn)
    val round = state.layout == TimerLayout.ROUND
    // Landscape (wider than tall): the dial or the round list on the left, the rest on the right.
    val landscape = rememberMediaQuery("(min-aspect-ratio: 1/1)")
    val header: @Composable () -> Unit = {
        TimerHeader(state, model, onEnd = { confirmEnd = true }, onMenu = { menu = true })
        ScreenNote()
    }
    val rules: @Composable () -> Unit = {
        TableRulesBar(state, model, onTimeOut = { timeOutSheet = true })
    }
    val shot: @Composable () -> Unit = {
        state.shotClock?.let { ShotClockPanel(it, model) }
    }
    Div({
        classes("goa-table-timer", if (round) "round-view" else "step-view", if (landscape) "landscape" else "portrait")
        if (state.paused) classes("paused")
    }) {
        when {
            round && landscape -> {
                Div({ classes("goa-table-timer-left") }) {
                    RoundList(state, model, expandCurrent = false, onJump = {
                        jump =
                            it
                    })
                }
                Div({ classes("goa-table-timer-right") }) {
                    header()
                    Div({ classes("goa-table-timer-scroll") }) { RoundCurrent(state, model) }
                    rules()
                    shot()
                    TimerControls(state, model)
                }
            }

            round -> {
                header()
                RoundList(state, model, expandCurrent = true, onJump = { jump = it })
                rules()
                shot()
                TimerControls(state, model)
            }

            landscape -> {
                Div({ classes("goa-table-timer-left") }) {
                    Div({ classes("goa-table-timer-dial") }) {
                        if (state.checklist == null ||
                            state.timed
                        ) {
                            Dial(state, model)
                        } else {
                            ChecklistStepCard(state, model)
                        }
                    }
                    rules()
                    shot()
                }
                Div({ classes("goa-table-timer-right") }) {
                    header()
                    Div({ classes("goa-table-timer-scroll") }) {
                        RoundPips(state.pips, state.round)
                        PhaseHeading(state, model)
                        StepDetail(state)
                        state.seats?.let { ResolvingHeroes(it, model::heroResolves) }
                        state.checklist?.let {
                            EndOfRoundChecklist(it, showLevels = !state.timed, heroIds = state.heroIds)
                        }
                    }
                    TimerControls(state, model)
                }
            }

            else -> {
                header()
                Div({ classes("goa-table-timer-scroll") }) {
                    RoundPips(state.pips, state.round)
                    PhaseHeading(state, model)
                    Div({
                        classes("goa-table-timer-dial")
                        // The dial shrinks where more must fit under it: hero tiles (Action) or the checklist.
                        if (state.seats != null) classes("small")
                        if (state.checklist != null) classes("medium")
                    }) {
                        if (state.checklist == null ||
                            state.timed
                        ) {
                            Dial(state, model)
                        } else {
                            ChecklistStepCard(state, model)
                        }
                    }
                    StepDetail(state)
                    rules()
                    state.seats?.let { ResolvingHeroes(it, model::heroResolves) }
                    state.checklist?.let { EndOfRoundChecklist(it, showLevels = !state.timed, heroIds = state.heroIds) }
                }
                shot()
                TimerControls(state, model)
            }
        }
    }
    Flash(table.cues.flashes, table.cues.finalFlash)
    if (confirmEnd) {
        AlertDialog(
            "End the match?",
            onDismiss = { confirmEnd = false },
            buttons = {
                GoaButton("Keep playing", { confirmEnd = false }, ButtonKind.TEXT)
                GoaButton("End match", {
                    confirmEnd = false
                    model.endMatch()
                })
            },
        ) {
            Text(
                "A match can end mid-turn: the last push, a push into the Throne Zone, or a team's last " +
                    "Life counter. The timer stops and the duration is kept for the match log.",
            )
        }
    }
    if (coinDialog) {
        CoinSideDialog(state.tieBreaker, onPick = {
            coinDialog = false
            model.setTieBreaker(it)
        }, onDismiss = { coinDialog = false })
    }
    if (menu) {
        TimerMenu(state, model, onEnd = { confirmEnd = true }, onCoin = { coinDialog = true }, onDismiss = {
            menu =
                false
        })
    }
    state.timeOuts?.let { timeOuts ->
        if (timeOutSheet) TimeOutSheet(timeOuts, onTimeOut = model::timeOut, onDismiss = { timeOutSheet = false })
    }
    JumpDialog(state, jump, model, onDismiss = { jump = null })
}

/** Whether [query] matches, kept up to date as the window turns or resizes. */
@Composable
internal fun rememberMediaQuery(query: String): Boolean {
    val list = remember(query) { kotlinx.browser.window.matchMedia(query) }
    var matches by remember(query) { mutableStateOf(list.matches) }
    DisposableEffect(list) {
        val listener: (org.w3c.dom.events.Event) -> Unit = { matches = list.matches }
        list.addEventListener("change", listener)
        onDispose { list.removeEventListener("change", listener) }
    }
    return matches
}

/** Screen Wake Lock while the running timer shows (and the person hasn't turned it off). */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val wakeLock = LocalServices.current.data.table.wakeLock
    DisposableEffect(enabled) {
        wakeLock.want(enabled)
        onDispose { wakeLock.want(false) }
    }
}

/**
 * Said once when the browser can't keep the screen on: a locked phone stops the page, so the timer
 * can't sound until it is unlocked (the phone app alerts on the lock screen; a web page can't).
 */
@Composable
private fun ScreenNote() {
    val table = LocalServices.current.data.table
    val prefs = table.prefs.prefs
    if (!prefs.keepScreenOn || prefs.screenNoteDismissed || table.wakeLock.state != WakeState.UNAVAILABLE) return
    Div({
        classes("goa-table-screen-note")
        attr("role", "status")
    }) {
        Icon("info", IconSize.SMALL)
        P({ classes("t-body-small") }) {
            Text(
                "This browser can't keep the screen on. Set Auto-Lock to Never while you play: " +
                    "a locked phone can't sound the timer.",
            )
        }
        IconButton("close", "Dismiss", onClick = { table.prefs.update { it.copy(screenNoteDismissed = true) } })
    }
}

/** The screen's edge flashes where the phone would vibrate (a cue). Keyed so each cue replays the animation. */
@Composable
internal fun Flash(count: Int, final: Boolean) {
    if (count == 0) return
    key(count) {
        Div({
            classes("goa-table-flash")
            if (final) classes("final")
            attr("aria-hidden", "true")
        })
    }
}

@Composable
private fun TimerHeader(state: ActiveUiState, model: TimerModel, onEnd: () -> Unit, onMenu: () -> Unit) {
    val router = LocalServices.current.router
    val roundView = state.layout == TimerLayout.ROUND
    Header({ classes("goa-topbar", "goa-table-timer-header") }) {
        Div({ classes("goa-topbar-row") }) {
            Div({ classes("goa-table-timer-title") }) {
                H1({
                    classes("goa-topbar-title", "t-banner-title")
                    tabIndex(-1)
                }) { Text("Round ${state.round}") }
                Span({
                    classes("goa-table-timer-subtitle", "t-body-small", "t-numeric")
                    attr("aria-label", "${state.presetName}, match time ${state.matchClock}")
                }) { Text("${state.presetName} · ${state.matchClock}") }
            }
            Div({ classes("goa-topbar-actions") }) {
                state.tieBreaker?.let {
                    TieBreakerChip(
                        it,
                        model::flipTieBreaker,
                        actionKey = "${state.round}:${state.title}:${state.paused}",
                    )
                }
                IconButton("levels", "Levels and defeats", onClick = {
                    router.navigate(Route.LevelLadder(state.heroIds))
                }, extraClass = "collapsible")
                IconButton(
                    if (roundView) "timer" else "view_round",
                    if (roundView) "Show one step at a time" else "Show the whole round",
                    onClick = { model.setTimerLayout(if (roundView) TimerLayout.STEPS else TimerLayout.ROUND) },
                    extraClass = "collapsible",
                )
                IconButton("flag", "End match", onClick = onEnd)
                IconButton("more", "More options", onClick = onMenu)
            }
        }
    }
}

@Composable
private fun TimerMenu(
    state: ActiveUiState,
    model: TimerModel,
    onEnd: () -> Unit,
    onCoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val services = LocalServices.current
    val table = services.data.table
    val roundView = state.layout == TimerLayout.ROUND
    val keepOn = table.prefs.prefs.keepScreenOn
    MenuSheet(
        "Timer",
        listOf(
            MenuItem("Restart phase", "replay", onClick = model::restart),
            MenuItem(
                "Skip to end of round",
                "skip_next",
                enabled = state.section != RoundSection.ROUND_END,
                onClick = model::skipToEndOfRound,
            ),
            MenuItem(
                if (state.tieBreaker ==
                    null
                ) {
                    "Track Tie Breaker coin…"
                } else {
                    "Tie Breaker coin…"
                },
                "coin",
                onClick = onCoin,
            ),
            MenuItem("End match…", "flag", onClick = onEnd),
            MenuItem(
                if (roundView) "Step-by-step view" else "Whole-round view",
                if (roundView) "timer" else "view_round",
                onClick = { model.setTimerLayout(if (roundView) TimerLayout.STEPS else TimerLayout.ROUND) },
            ),
            MenuItem("Levels & defeats", "levels", onClick = {
                services.router.navigate(Route.LevelLadder(state.heroIds))
            }),
            MenuItem(
                if (keepOn) "Let the screen sleep" else "Keep the screen on",
                "lock",
                onClick = { table.prefs.update { it.copy(keepScreenOn = !keepOn) } },
            ),
            MenuItem("Rules", "rules", onClick = { services.router.navigate(Route.Rules) }),
            MenuItem("Settings", "settings", onClick = { services.router.navigate(Route.Settings) }),
        ),
        onDismiss = onDismiss,
    )
}

/**
 * Coin side currently showing; tap when the coin is flipped at the table. Undo stays for 8 s or until
 * the next timer action ([actionKey] changes), whichever comes first.
 */
@Composable
private fun TieBreakerChip(team: Team, onFlip: () -> Unit, actionKey: Any) {
    var flips by remember { mutableStateOf(0) }
    var showUndo by remember { mutableStateOf(false) }
    LaunchedEffect(flips) {
        if (flips == 0) return@LaunchedEffect
        showUndo = true
        delay(COIN_UNDO_MS)
        showUndo = false
    }
    LaunchedEffect(actionKey) { showUndo = false }
    if (showUndo) {
        Button({
            type(ButtonType.Button)
            classes("goa-button", "text", "goa-table-on-banner")
            onClick {
                showUndo = false
                onFlip()
            }
        }) { Text("Undo") }
    }
    Button({
        type(ButtonType.Button)
        classes("goa-table-coin-chip", teamClass(team))
        attr("aria-label", "Tie Breaker coin shows ${teamName(team)}. Flip the coin")
        onClick {
            onFlip()
            flips++
        }
    }) { TeamBadge(team) }
}

private fun teamName(team: Team) = if (team == Team.RED) "Red" else "Blue"

/** Asks which side shows instead of assuming one (rulebook p.7: the coin is flipped at setup). */
@Composable
private fun CoinSideDialog(current: Team?, onPick: (Team?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        "Tie Breaker coin",
        onDismiss = onDismiss,
        buttons = {
            if (current != null) GoaButton("Stop tracking", { onPick(null) }, ButtonKind.TEXT)
            GoaButton("Cancel", onDismiss, ButtonKind.TEXT)
        },
    ) {
        P { Text("Which team's symbol is showing on the coin?") }
        Div({ classes("goa-table-stack") }) {
            Team.entries.forEach { team ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-table-coin-choice", teamClass(team))
                    if (team == current) classes("selected")
                    onClick { onPick(team) }
                }) {
                    TeamBadge(team, showLabel = false)
                    Span({ classes("t-title-medium") }) { Text("${teamName(team)} shows") }
                }
            }
        }
    }
}

/**
 * Round progress: ◇ round start · one pip per turn · ▢ end of round. Current = filled + ring, done =
 * filled, upcoming = outline. Shapes differ so position reads without colour.
 */
@Composable
private fun RoundPips(pips: RoundPipsUi, round: Int) {
    val where = when (pips.section) {
        RoundSection.ROUND_START -> "Round start"
        RoundSection.TURN -> "Turn ${pips.currentTurn} of ${pips.turns}"
        RoundSection.ROUND_END -> "End of round"
    }
    Div({
        classes("goa-table-pips")
        attr("role", "img")
        attr("aria-label", "Round $round. $where")
    }) {
        fun stateOf(pip: RoundSection) = when {
            pips.section.ordinal > pip.ordinal -> "done"
            pips.section == pip -> "current"
            else -> "upcoming"
        }
        if (pips.hasRoundStart) Span({ classes("goa-table-pip", "start", stateOf(RoundSection.ROUND_START)) })
        for (turn in 1..pips.turns) {
            val pipState = when {
                pips.section == RoundSection.ROUND_START -> "upcoming"
                pips.section == RoundSection.ROUND_END -> "done"
                turn < (pips.currentTurn ?: 0) -> "done"
                turn == pips.currentTurn -> "current"
                else -> "upcoming"
            }
            Span({ classes("goa-table-pip", "turn", pipState) }) { Text(turn.toString()) }
        }
        Span({ classes("goa-table-pip", "end", stateOf(RoundSection.ROUND_END)) })
    }
}

/**
 * The step chip with "Previous phase" beside it, and the step title and hint. Previous sits up here,
 * away from the bottom edge (game night: a habitual tap at the bottom went back a phase).
 */
@Composable
private fun PhaseHeading(state: ActiveUiState, model: TimerModel) {
    Div({ classes("goa-table-phase-chip-row") }) {
        IconButton("skip_previous", "Previous phase", onClick = model::back, extraClass = "goa-table-previous")
        Span({ classes("goa-table-phase-chip", "t-label-large") }) { Text(state.context.uppercase()) }
        Span({ classes("goa-table-balance") })
    }
    H2({
        classes("goa-table-phase-title", "t-headline-small")
        attr("aria-live", "polite")
    }) { Text(state.title) }
    state.hint?.let { P({ classes("goa-table-hint", "t-body-medium", "c-variant") }) { Text(it) } }
}

/** The dial's tone: what colours the ring and digits. */
private fun tone(state: ActiveUiState): String = when {
    !state.timed -> "untimed"
    state.paused -> "paused"
    state.overtime -> "overtime"
    state.warning -> "warning"
    else -> "normal"
}

/**
 * Engraved countdown ring: a bronze bezel with minute ticks, a track and a teal arc for the time left.
 * Warning turns bronze, overtime red and pulsing (unless reduced motion); the label always says it in
 * words. Tap to pause or resume.
 */
@Composable
private fun Dial(state: ActiveUiState, model: TimerModel) {
    val status = when {
        state.paused -> "Tap to resume"
        state.overtime -> "Overtime"
        !state.timed -> "Elapsed · tap to pause"
        else -> "Tap to pause"
    }
    val sweep = when {
        !state.timed || state.overtime -> 1.0
        else -> state.fraction.toDouble().coerceIn(0.0, 1.0)
    }
    Button({
        type(ButtonType.Button)
        classes("goa-table-dial", tone(state))
        attr("aria-label", "${state.title}. ${state.spokenClock}. ${if (state.paused) "Paused" else "Running"}")
        onClick { model.togglePause() }
    }) {
        DialRing(sweep)
        Span({ classes("goa-table-dial-face") }) {
            if (state.paused) Span({ classes("goa-table-dial-paused") }) { Text("❚❚ PAUSED") }
            Span({
                classes("goa-table-dial-digits", "t-numeric")
                if (state.clock.length > LONG_CLOCK) classes("long")
            }) { Text(state.clock) }
            Span({ classes("goa-table-dial-status") }) { Text(status.uppercase()) }
        }
    }
}

/**
 * The ring's SVG (bezel, 60 engraved ticks, track, arc) is drawn once; each tick of the clock only
 * moves the arc ([sweep], 0..1 of the circle).
 */
@Composable
private fun DialRing(sweep: Double) {
    var ring by remember { mutableStateOf<org.w3c.dom.Element?>(null) }
    Span({
        classes("goa-table-dial-ring")
        attr("aria-hidden", "true")
        ref { element ->
            element.innerHTML = RING_SVG
            ring = element
            onDispose { ring = null }
        }
    })
    SideEffect { ring?.querySelector(".arc")?.setAttribute("stroke-dasharray", "$sweep 1") }
}

private val RING_SVG: String = buildString {
    append("""<svg viewBox="0 0 200 200" focusable="false">""")
    append("""<circle class="bezel" cx="100" cy="100" r="98"/>""")
    for (i in 0 until TICKS) {
        val long = i % 5 == 0
        val a = i * 6.0 * kotlin.math.PI / 180.0
        val outer = 95.0
        val inner = outer - if (long) 7.0 else 3.0
        fun n(v: Double) = (kotlin.math.round(v * 100) / 100).toString()
        append(
            """<line class="${if (long) "tick-long" else "tick"}" x1="${n(100 + outer * kotlin.math.sin(a))}" """ +
                """y1="${n(100 - outer * kotlin.math.cos(a))}" x2="${n(100 + inner * kotlin.math.sin(a))}" """ +
                """y2="${n(100 - inner * kotlin.math.cos(a))}"/>""",
        )
    }
    append("""<circle class="track" cx="100" cy="100" r="$RING_R"/>""")
    append("""<circle class="arc" cx="100" cy="100" r="$RING_R" pathLength="1" stroke-dasharray="1 1"/>""")
    append("</svg>")
}

/** Untimed end-of-round step: a medallion with the step number and the rule text; tap = Next. */
@Composable
private fun ChecklistStepCard(state: ActiveUiState, model: TimerModel) {
    val current = state.checklist?.firstOrNull { it.state == ChecklistState.CURRENT }
    Button({
        type(ButtonType.Button)
        classes("goa-table-checklist-step")
        attr("aria-label", "${current?.title ?: state.title}. Step done, next")
        onClick { model.next() }
    }) {
        Span({ classes("goa-table-checklist-medallion") }) { Text(current?.number?.toString() ?: "✓") }
        current?.detail?.let { Span({ classes("t-body-large") }) { Text(it) } }
        Span({
            classes("t-title-small", "t-numeric", "c-variant")
        }) { Text("${state.clock} at this step · tap when done") }
    }
}

/** The rule text of a timed end-of-round step (Level Up: mandatory), and the ladder button on Level Up. */
@Composable
private fun StepDetail(state: ActiveUiState) {
    val detail = state.currentDetail?.takeIf { state.timed } ?: return
    P({ classes("goa-table-step-detail", "t-title-medium") }) { Text(detail) }
    if (state.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP_STEP } == true) {
        LevelLadderButton(state.heroIds, "Level costs & card Tiers")
    }
}

@Composable
private fun LevelLadderButton(heroIds: List<String>, label: String = "Levels") {
    val router = LocalServices.current.router
    GoaButton(label, { router.navigate(Route.LevelLadder(heroIds)) }, ButtonKind.OUTLINED, icon = "levels")
}

@Composable
private fun EndOfRoundChecklist(items: List<ChecklistItemUi>, showLevels: Boolean, heroIds: List<String>) {
    Div({ classes("goa-card", "flush", "goa-table-checklist") }) {
        H2({ classes("t-overline", "c-primary", "goa-table-checklist-head") }) { Text("END OF ROUND") }
        Ul({ classes("goa-list") }) {
            items.forEach { item ->
                Li({
                    classes("goa-table-checklist-row", item.state.name.lowercase())
                    attr(
                        "aria-label",
                        "${item.number}. ${item.title}" + (item.timerName?.let { ", $it" } ?: "") + ", " +
                            when (item.state) {
                                ChecklistState.DONE -> "done"
                                ChecklistState.CURRENT -> "current step"
                                ChecklistState.UPCOMING -> "upcoming"
                            },
                    )
                }) {
                    Span({
                        classes("goa-table-checklist-badge")
                        attr("aria-hidden", "true")
                    }) {
                        if (item.state ==
                            ChecklistState.DONE
                        ) {
                            Icon("check", IconSize.SMALL)
                        } else {
                            Text(item.number.toString())
                        }
                    }
                    Span({
                        classes("goa-table-row-text")
                        attr("aria-hidden", "true")
                    }) {
                        Span({ classes("t-title-small") }) { Text(item.title) }
                        item.timerName?.let { Span({ classes("t-body-small", "goa-table-secondary") }) { Text(it) } }
                    }
                    if (showLevels && item.state == ChecklistState.CURRENT &&
                        item.id == LEVEL_UP_STEP
                    ) {
                        LevelLadderButton(heroIds)
                    }
                }
            }
        }
    }
}

/**
 * Action timer: tap the hero who resolves now (highest unresolved initiative acts first). Naming the
 * slot's hero starts their full time; tapping another hero then moves on to them.
 */
@Composable
private fun ResolvingHeroes(seats: List<SeatUi>, onResolve: (Int) -> Unit) {
    Div({ classes("goa-table-seats-block") }) {
        P({ classes("t-body-small", "c-variant") }) { Text("Tap the hero who resolves now to start their time.") }
        Div({
            classes("goa-table-seats")
            style { property("--cols", seatColumns(seats.size).toString()) }
        }) {
            seats.forEach { seat ->
                val status = when {
                    seat.acting -> "resolving now"
                    seat.resolved -> "already resolved"
                    else -> "not resolved yet"
                }
                Button({
                    type(ButtonType.Button)
                    classes("goa-table-seat", teamClass(seat.team))
                    if (seat.acting) classes("acting")
                    if (seat.resolved) classes("resolved")
                    if (seat.resolved || seat.acting) disabled()
                    attr(
                        "aria-label",
                        "${seat.heroName}, ${teamName(seat.team)} team" + (seat.playerName?.let { ", $it" } ?: "") +
                            ", $status",
                    )
                    onClick { onResolve(seat.seat) }
                }) {
                    HeroArt(seat.heroId ?: "unknown", seat.portrait, seat.heroName, round = true)
                    Span({ classes("goa-table-seat-name", "t-label-medium") }) { Text(seat.heroName) }
                    Span({ classes("goa-table-seat-player") }) {
                        TeamBadge(seat.team, showLabel = false)
                        seat.playerName?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
                    }
                }
            }
        }
    }
}

/** Columns for the hero tiles: every seat (up to 10) fits in at most two rows. */
private fun seatColumns(count: Int): Int = when {
    count <= 4 -> count.coerceAtLeast(2)
    count <= 6 -> 3
    count <= 8 -> 4
    else -> 5
}

/**
 * Table rules: the "Hurry up" button and team time-outs. A running Hurry up is not here: it sits
 * just above the controls so it can't scroll out of sight.
 */
@Composable
private fun TableRulesBar(state: ActiveUiState, model: TimerModel, onTimeOut: () -> Unit) {
    val seconds = state.shotClockSeconds
    val startable = seconds != null && state.shotClock == null
    if (!startable && state.timeOuts == null) return
    Div({ classes("goa-table-rules-bar") }) {
        if (seconds != null && state.shotClock == null) {
            GoaButton(
                "Hurry up ${TimerText.durationLabel(seconds)}",
                model::startShotClock,
                ButtonKind.OUTLINED,
                icon = "bolt",
            )
        }
        state.timeOuts?.let { timeOuts ->
            GoaButton(
                "Time-out +${TimerText.durationLabel(timeOuts.seconds)}",
                onTimeOut,
                ButtonKind.OUTLINED,
                icon = "time_add",
                enabled = timeOuts.usable && timeOuts.redLeft + timeOuts.blueLeft > 0,
            )
        }
    }
}

@Composable
private fun TimeOutSheet(timeOuts: TimeOutsUi, onTimeOut: (Team) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Time-out +${TimerText.durationLabel(timeOuts.seconds)}", onDismiss) {
        Div({ classes("goa-table-menu") }) {
            listOf(Team.RED to timeOuts.redLeft, Team.BLUE to timeOuts.blueLeft).forEach { (team, left) ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-table-menu-item")
                    if (left <= 0) disabled()
                    onClick {
                        onDismiss()
                        onTimeOut(team)
                    }
                }) {
                    TeamBadge(team, showLabel = false)
                    Span({ classes("t-body-large") }) { Text("${teamName(team)} team · $left left this round") }
                }
            }
        }
    }
}

/** The running "Hurry up" countdown, with Restart and Stop. */
@Composable
private fun ShotClockPanel(shot: ShotClockUi, model: TimerModel) {
    Div({
        classes("goa-table-shot")
        if (shot.over) classes("over")
        if (shot.warning) classes("warning")
    }) {
        Div({
            classes("goa-table-row-text")
            attr("aria-label", shot.spokenClock)
            if (shot.over) attr("role", "alert")
        }) {
            Span({
                classes("t-label-large", "goa-table-shot-label")
            }) { Text(if (shot.over) "HURRY UP · TIME!" else "HURRY UP") }
            Span({ classes("goa-table-shot-digits", "t-numeric") }) { Text(shot.clock) }
        }
        IconButton("replay", "Restart Hurry up", onClick = model::startShotClock)
        IconButton("close", "Stop Hurry up", onClick = model::cancelShotClock)
    }
}

/** Pause and Next. "Previous phase" is up by the step chip, away from the edge. */
@Composable
private fun TimerControls(state: ActiveUiState, model: TimerModel) {
    Div({ classes("goa-table-timer-controls") }) {
        Button({
            type(ButtonType.Button)
            classes("goa-table-pause")
            attr("aria-label", if (state.paused) "Resume" else "Pause")
            onClick { model.togglePause() }
        }) { Icon(if (state.paused) "play" else "pause") }
        Button({
            type(ButtonType.Button)
            classes("goa-table-next")
            onClick { model.next() }
        }) {
            Span { Text(state.nextLabel) }
            Icon("arrow_forward")
        }
    }
}

// ---------------------------------------------------------------- Round view (ADR-026)

/**
 * The round, grouped by section, one row per step; the current step expanded with its live clock.
 * When the step changes the current row is brought into view; tapping another row asks before jumping.
 */
@Composable
private fun RoundList(state: ActiveUiState, model: TimerModel, expandCurrent: Boolean, onJump: (RoundRowUi) -> Unit) {
    LaunchedEffect(state.round, state.stepIndex) {
        delay(SCROLL_SETTLE_MS)
        val current = kotlinx.browser.document.querySelector(".goa-table-round-row.current")
        val reduced = kotlinx.browser.window.matchMedia("(prefers-reduced-motion: reduce)").matches
        val options: dynamic = js("({})")
        options.block = "center"
        options.behavior = if (reduced) "auto" else "smooth"
        current?.asDynamic()?.scrollIntoView(options)
    }
    Div({ classes("goa-table-round-list") }) {
        var group: String? = null
        state.rows.forEach { row ->
            if (row.group != group) {
                group = row.group
                H2({ classes("goa-table-round-group", "t-overline") }) { Text(row.group.uppercase()) }
            }
            when {
                row.state == RoundRowState.CURRENT && expandCurrent -> CurrentRow(state, row, model)

                // Landscape: the current step is a row too (its clock is beside the list); tap = pause.
                row.state == RoundRowState.CURRENT -> RoundRow(row, state, onClick = model::togglePause)

                else -> RoundRow(row, state, onClick = { onJump(row) })
            }
        }
    }
}

/** A done or upcoming step: one line, tap to jump. */
@Composable
private fun RoundRow(row: RoundRowUi, state: ActiveUiState, onClick: () -> Unit) {
    val current = row.state == RoundRowState.CURRENT
    Button({
        type(ButtonType.Button)
        classes("goa-table-round-row", row.state.name.lowercase())
        if (current) classes(tone(state))
        attr(
            "aria-label",
            row.description(state.spokenClock) + when {
                !current -> ". Jump to this step"
                state.paused -> ". Paused. Resume"
                else -> ". Running. Pause"
            },
        )
        onClick { onClick() }
    }) {
        Span({
            classes("goa-table-round-badge")
            attr("aria-hidden", "true")
        }) {
            when {
                row.state == RoundRowState.DONE -> Icon("check", IconSize.SMALL)
                current -> Icon(if (state.paused) "pause" else "play", IconSize.SMALL)
            }
        }
        Span({
            classes("goa-table-row-text")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("t-body-large") }) { Text(row.title) }
            row.context?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        Span({
            classes("t-label-large", "t-numeric", if (current) "goa-table-plain" else "c-variant")
            attr("aria-hidden", "true")
        }) { Text(if (current) state.clock else row.length) }
    }
}

/**
 * The current step, expanded: title with "Previous phase", the live clock with its progress (tap to
 * pause), the rules hint and what the step needs (Level Up rule and ladder, the Action seat chooser).
 */
@Composable
private fun CurrentRow(state: ActiveUiState, row: RoundRowUi, model: TimerModel) {
    Div({ classes("goa-table-round-row", "current", "expanded", tone(state)) }) { RoundStepBody(state, row, model) }
}

/** Landscape round view: the current step beside the list. */
@Composable
private fun RoundCurrent(state: ActiveUiState, model: TimerModel) {
    val row = state.rows.firstOrNull { it.state == RoundRowState.CURRENT } ?: return
    RoundStepBody(state, row, model)
}

@Composable
private fun RoundStepBody(state: ActiveUiState, row: RoundRowUi, model: TimerModel) {
    Div({ classes("goa-table-round-title") }) {
        Div({
            classes("goa-table-row-text")
            attr("aria-live", "polite")
        }) {
            Span({ classes("t-title-large") }) { Text(row.title) }
            row.context?.let { Span({ classes("t-body-medium", "c-variant") }) { Text(it) } }
        }
        IconButton("skip_previous", "Previous phase", onClick = model::back, extraClass = "goa-table-previous")
    }
    RoundClock(state, row, model)
    state.hint?.let { P({ classes("t-body-medium", "c-variant", "goa-table-tight") }) { Text(it) } }
    state.currentDetail?.let { P({ classes("goa-table-step-detail", "t-title-medium") }) { Text(it) } }
    if (state.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP_STEP } == true) {
        LevelLadderButton(state.heroIds, "Level costs & card Tiers")
    }
    state.seats?.let { ResolvingHeroes(it, model::heroResolves) }
}

/** Live clock of the current step with its progress bar; tap toggles pause. */
@Composable
private fun RoundClock(state: ActiveUiState, row: RoundRowUi, model: TimerModel) {
    val status = when {
        state.paused -> "Paused · tap to resume"
        state.overtime -> "Overtime"
        !state.timed -> "Elapsed · tap to pause"
        else -> "Tap to pause"
    }
    Button({
        type(ButtonType.Button)
        classes("goa-table-round-clock", tone(state))
        attr("aria-label", "${row.description(state.spokenClock)}. ${if (state.paused) "Paused" else "Running"}")
        onClick { model.togglePause() }
    }) {
        Span({ classes("goa-table-round-clock-line") }) {
            Span({ classes("goa-table-round-digits", "t-numeric") }) { Text(state.clock) }
            Span({ classes("goa-table-row-text") }) {
                Span({ classes("t-label-large", "goa-table-round-status") }) { Text(status.uppercase()) }
                if (state.timed) {
                    Span({
                        classes("t-body-small", "t-numeric", "c-variant")
                    }) { Text("of ${row.length}") }
                }
            }
        }
        if (state.timed) {
            Span({ classes("goa-table-round-progress") }) {
                Span({
                    style { property("width", "${(if (state.overtime) 1.0 else state.fraction.toDouble()) * 100}%") }
                })
            }
        }
    }
}

/** "Jump to Turn 2 · Play a card?": a mis-tap is cheap to undo, but never silent. */
@Composable
private fun JumpDialog(state: ActiveUiState, target: RoundRowUi?, model: TimerModel, onDismiss: () -> Unit) {
    // A new round makes the target meaningless (the controller ignores it too).
    LaunchedEffect(state.round) { onDismiss() }
    if (target == null) return
    val from = state.rows.firstOrNull { it.state == RoundRowState.CURRENT }?.jumpName ?: state.title
    AlertDialog(
        "Jump to ${target.jumpName}?",
        onDismiss = onDismiss,
        buttons = {
            GoaButton("Cancel", onDismiss, ButtonKind.TEXT)
            GoaButton("Jump", {
                onDismiss()
                model.jumpTo(state.round, target.index)
            })
        },
    ) {
        Text(
            "It starts fresh. Previous phase within ${TimerEngine.UNDO_WINDOW_MS / MILLIS_PER_SECOND} seconds " +
                "returns to $from with its time.",
        )
    }
}

/** End-of-round id of the Level Up step (match-setup.json). */
private const val LEVEL_UP_STEP = "level_up"
private const val COIN_UNDO_MS = 8_000L
private const val TICKS = 60
private const val RING_R = 74
private const val LONG_CLOCK = 5
private const val MILLIS_PER_SECOND = 1_000L
private const val SCROLL_SETTLE_MS = 60L
