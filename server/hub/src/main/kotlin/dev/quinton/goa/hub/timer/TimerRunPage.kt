package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.timer.ActiveUiState
import dev.quinton.goa.core.presentation.timer.ChecklistState
import dev.quinton.goa.core.presentation.timer.MatchSetupText
import dev.quinton.goa.core.presentation.timer.RoundRowState
import dev.quinton.goa.core.presentation.timer.RoundRowUi
import dev.quinton.goa.core.presentation.timer.SeatUi
import dev.quinton.goa.core.presentation.timer.SummaryUiState
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HtmlBlockTag
import kotlinx.html.OL
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.progress
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul

/**
 * Everything the running timer page shows, at one instant: the phone's [ActiveUiState] (or the
 * summary once ended) from the shared [dev.quinton.goa.core.presentation.timer.TimerUiMapper], plus
 * the wall-clock instants `timer.js` counts from (it never counts ticks).
 */
internal class RunModel(
    val snapshot: TimerSnapshot,
    val active: ActiveUiState?,
    val summary: SummaryUiState?,
    val settings: AppSettings,
    val pushOn: Boolean,
    val alert: String?,
    val live: LiveClock,
    val ladder: LevelLadder?,
)

/** Wall-clock instants (epoch ms, the hub's clock) for the page's countdown. */
internal data class LiveClock(
    val serverNow: Long,
    val stepKey: String,
    /** When the current step's time was 0 (running only). */
    val zeroAt: Long?,
    /** Step length; null = untimed (counts up). */
    val durationMs: Long?,
    /** Where the running countdown reaches zero; null when paused, untimed or ended. */
    val endsAt: Long?,
    val matchZeroAt: Long,
    val shotZeroAt: Long?,
    val shotDurationMs: Long?,
    val cues: List<Int>,
    val warnMs: Long?,
    /** Live-region text for a step change: "Round 1 · Turn 2 of 4 · Play a card". */
    val announce: String,
)

internal object TimerRunPage {
    const val ROOT_ID = "timer-root"
    const val ANNOUNCE_ID = "timer-announce"

    /** The swappable root of the running timer (or its summary). */
    fun FlowContent.timerRoot(m: RunModel) {
        val a = m.active
        if (a == null) {
            summaryRoot(m)
            return
        }
        val layout = if (a.layout == TimerLayout.ROUND) "tr-round" else "tr-steps"
        val state = listOfNotNull(
            "is-paused".takeIf { a.paused },
            "is-warning".takeIf { a.warning && !a.paused },
            "is-overtime".takeIf { a.overtime },
            "is-untimed".takeIf { !a.timed },
        )
        form(
            action = TimerRoutes.CMD,
            method = FormMethod.post,
            classes = (listOf("tr", layout) + state).joinToString(" "),
        ) {
            id = ROOT_ID
            attributes["hx-post"] = TimerRoutes.CMD
            attributes["hx-target"] = "this"
            attributes["hx-swap"] = "outerHTML"
            liveAttributes(m)
            header(m, a)
            m.alert?.let { alert ->
                div("tr-alert") {
                    attributes["role"] = "alert"
                    icon(Icons.NOTIFICATIONS)
                    p { +alert }
                }
            }
            if (!m.pushOn) pushBanner(m.snapshot.sessionId)
            soundsHint(m)
            if (a.layout == TimerLayout.ROUND) roundBody(a) else stepBody(a)
            a.shotClock?.let { shotPanel(a) }
            controls(a)
            runDialogs(m, a)
        }
    }

    private fun HtmlBlockTag.liveAttributes(m: RunModel) {
        val l = m.live
        attributes["data-timer"] = ""
        attributes["data-status"] = m.snapshot.status.name.lowercase()
        attributes["data-server-now"] = l.serverNow.toString()
        attributes["data-step-key"] = l.stepKey
        l.zeroAt?.let { attributes["data-zero-at"] = it.toString() }
        l.durationMs?.let { attributes["data-duration"] = it.toString() }
        l.endsAt?.let { attributes["data-ends-at"] = it.toString() }
        attributes["data-match-zero-at"] = l.matchZeroAt.toString()
        l.shotZeroAt?.let { attributes["data-shot-zero-at"] = it.toString() }
        l.shotDurationMs?.let { attributes["data-shot-duration"] = it.toString() }
        attributes["data-cues"] = l.cues.joinToString(",")
        l.warnMs?.let { attributes["data-warn-ms"] = it.toString() }
        attributes["data-auto-advance"] = m.snapshot.preset.autoAdvance.toString()
        attributes["data-sounds"] = m.settings.soundsEnabled.toString()
        attributes["data-haptics"] = m.settings.hapticsEnabled.toString()
        attributes["data-announce"] = l.announce
        attributes["data-session"] = m.snapshot.sessionId
    }

    // ---- header --------------------------------------------------------------------------------

    private fun HtmlBlockTag.header(m: RunModel, a: ActiveUiState) {
        div("tr-head") {
            div("tr-round") {
                span("tr-round-label") { +"Round ${a.round}" }
                span("tr-preset") { +a.presetName }
            }
            a.tieBreaker?.let { coinChip(it) }
            div("tr-head-actions") {
                val round = a.layout == TimerLayout.ROUND
                commandIcon(
                    if (round) "layout:steps" else "layout:round",
                    if (round) "Show one step at a time" else "Show the whole round",
                    if (round) "t-view_day" else "t-view_agenda",
                )
                iconDialogButton("levels-dialog", "Levels and defeats", "t-military_tech")
                iconDialogButton("end-dialog", "End match", "t-flag")
                details("tr-menu") {
                    summary("icon-button") {
                        attributes["aria-label"] = "More options"
                        attributes["title"] = "More options"
                        icon("t-more_vert")
                    }
                    div("tr-menu-items") {
                        menuCommand("restart", "Restart phase")
                        menuCommand("skip-end", "Skip to end of round")
                        menuDialog(
                            "coin-dialog",
                            if (a.tieBreaker ==
                                null
                            ) {
                                "Track Tie Breaker coin…"
                            } else {
                                "Tie Breaker coin…"
                            },
                        )
                        menuCommand(
                            if (a.layout == TimerLayout.ROUND) "layout:steps" else "layout:round",
                            if (a.layout == TimerLayout.ROUND) "Step-by-step view" else "Whole-round view",
                        )
                        menuDialog("levels-dialog", "Levels & defeats")
                        a("/rules", classes = "tr-menu-item") { +"Rules" }
                        a("/settings", classes = "tr-menu-item") { +"Settings" }
                        menuDialog("end-dialog", "End match…")
                    }
                }
            }
            if (m.snapshot.preset.autoAdvance) span("visually-hidden") { +"Auto-advance is on." }
        }
    }

    private fun HtmlBlockTag.coinChip(team: Team) {
        val name = MatchSetupText.teamName(team)
        button(type = ButtonType.submit, classes = "tr-coin ${if (team == Team.RED) "team-red" else "team-blue"}") {
            attributes["name"] = "cmd"
            attributes["value"] = "flip-coin"
            attributes["aria-label"] = "Tie Breaker coin shows $name. Flip the coin"
            attributes["title"] = "Flip the coin (after a cross-team initiative tie)"
            span("team-mark") {
                attributes["aria-hidden"] = "true"
                +(if (team == Team.RED) "◆" else "●")
            }
            span { +name }
        }
    }

    private fun HtmlBlockTag.commandIcon(cmd: String, label: String, iconName: String) {
        button(type = ButtonType.submit, classes = "icon-button") {
            attributes["name"] = "cmd"
            attributes["value"] = cmd
            attributes["aria-label"] = label
            attributes["title"] = label
            icon(iconName)
        }
    }

    private fun HtmlBlockTag.iconDialogButton(dialogId: String, label: String, iconName: String) {
        button(type = ButtonType.button, classes = "icon-button") {
            attributes["data-dialog-open"] = dialogId
            attributes["aria-label"] = label
            attributes["title"] = label
            icon(iconName)
        }
    }

    private fun HtmlBlockTag.menuCommand(cmd: String, label: String) {
        button(type = ButtonType.submit, classes = "tr-menu-item") {
            attributes["name"] = "cmd"
            attributes["value"] = cmd
            +label
        }
    }

    private fun HtmlBlockTag.menuDialog(dialogId: String, label: String) {
        button(type = ButtonType.button, classes = "tr-menu-item") {
            attributes["data-dialog-open"] = dialogId
            +label
        }
    }

    /** "Lock-screen alerts are off" (the phone's alerts-off banner), with the subscribe control. */
    private fun HtmlBlockTag.pushBanner(sessionId: String) {
        div("tr-push-banner") {
            attributes["data-push-banner"] = sessionId
            attributes["hidden"] = "hidden" // timer.js shows it unless dismissed for this session.
            icon("t-notifications_off")
            p {
                +"Lock-screen alerts are off: a locked phone pauses this page."
            }
            button(type = ButtonType.button, classes = "btn btn-text") {
                attributes["data-push-subscribe"] = ""
                +"Turn on"
            }
            button(type = ButtonType.button, classes = "icon-button") {
                attributes["data-push-dismiss"] = ""
                attributes["aria-label"] = "Dismiss"
                icon(Icons.CLOSE)
            }
        }
    }

    /** Browsers play sound only after a tap on this page; timer.js shows this until one. */
    private fun HtmlBlockTag.soundsHint(m: RunModel) {
        if (!m.settings.soundsEnabled) return
        button(type = ButtonType.button, classes = "tr-sound-hint chip") {
            attributes["data-sound-unlock"] = ""
            attributes["hidden"] = "hidden"
            icon("t-volume_up")
            span { +"Tap to turn on the chimes" }
        }
    }

    // ---- step view -----------------------------------------------------------------------------

    private fun HtmlBlockTag.stepBody(a: ActiveUiState) {
        div("tr-body") {
            section("tr-dial") {
                pips(a)
                div("tr-step-row") {
                    span("tr-step-chip") { +a.context }
                    previousButton()
                }
                h2("tr-title") { +a.title }
                a.hint?.let { p("tr-hint") { +it } }
                clockButton(a, large = true)
                progressBar(a)
                p("tr-match") {
                    +"Match "
                    span("tabular") {
                        attributes["data-match-clock"] = ""
                        +a.matchClock
                    }
                }
            }
            section("tr-extras") {
                tableRules(a)
                a.seats?.let { seats(it, a) }
                a.checklist?.let { checklist(a) }
            }
        }
    }

    private fun HtmlBlockTag.pips(a: ActiveUiState) {
        val p = a.pips
        ol("tr-pips") {
            attributes["aria-label"] = "Round progress"
            if (p.hasRoundStart) {
                li(if (p.section == RoundSection.ROUND_START) "pip pip-start current" else "pip pip-start done") {
                    span("visually-hidden") { +"Round start" }
                }
            }
            for (turn in 1..p.turns) {
                val css = when {
                    p.section == RoundSection.ROUND_END -> "done"
                    p.section == RoundSection.ROUND_START -> ""
                    p.currentTurn == turn -> "current"
                    (p.currentTurn ?: 0) > turn -> "done"
                    else -> ""
                }
                li("pip $css".trim()) {
                    if (css == "current") attributes["aria-current"] = "step"
                    span("visually-hidden") { +"Turn $turn" }
                }
            }
            li(if (p.section == RoundSection.ROUND_END) "pip pip-end current" else "pip pip-end") {
                span("visually-hidden") { +"End of round" }
            }
        }
    }

    private fun HtmlBlockTag.previousButton() {
        button(type = ButtonType.submit, classes = "icon-button tr-prev") {
            attributes["name"] = "cmd"
            attributes["value"] = "back"
            attributes["aria-label"] = "Previous phase"
            attributes["title"] = "Previous phase"
            icon("t-skip_previous")
        }
    }

    private fun HtmlBlockTag.clockButton(a: ActiveUiState, large: Boolean) {
        button(type = ButtonType.submit, classes = if (large) "tr-clock" else "tr-clock tr-clock-row") {
            attributes["name"] = "cmd"
            attributes["value"] = "toggle"
            attributes["aria-label"] = "${if (a.paused) "Resume" else "Pause"}: ${a.spokenClock}"
            if (a.paused) span("tr-paused") { +"PAUSED" }
            span("tr-digits tabular") {
                attributes["data-clock"] = ""
                attributes["role"] = "timer"
                +a.clock
            }
            span("tr-clock-label") {
                attributes["data-clock-label"] = ""
                +clockLabel(a)
            }
        }
    }

    private fun clockLabel(a: ActiveUiState): String = when {
        a.paused -> "Tap to resume"
        a.overtime -> "Overtime"
        !a.timed -> "Elapsed · tap to pause"
        else -> "Tap to pause"
    }

    private fun HtmlBlockTag.progressBar(a: ActiveUiState) {
        if (!a.timed) return
        div("tr-progress") {
            attributes["aria-hidden"] = "true"
            // A <progress> needs no inline style (CSP); timer.js moves its value.
            progress {
                attributes["data-progress"] = ""
                attributes["max"] = PROGRESS_MAX.toString()
                attributes["value"] = (a.fraction * PROGRESS_MAX).toInt().toString()
            }
        }
    }

    private fun HtmlBlockTag.tableRules(a: ActiveUiState) {
        val shot = a.shotClockSeconds
        val timeOuts = a.timeOuts
        if ((shot == null || a.shotClock != null) && timeOuts == null) return
        div("tr-rules") {
            if (shot != null && a.shotClock == null) {
                button(type = ButtonType.submit, classes = "btn btn-tonal") {
                    attributes["name"] = "cmd"
                    attributes["value"] = "shot-start"
                    icon("t-timer")
                    span { +"Hurry up ${TimerText.durationLabel(shot)}" }
                }
            }
            if (timeOuts != null) {
                Team.entries.forEach { team ->
                    val left = if (team == Team.RED) timeOuts.redLeft else timeOuts.blueLeft
                    button(type = ButtonType.submit, classes = "btn btn-outlined tr-timeout") {
                        attributes["name"] = "cmd"
                        attributes["value"] = "timeout:${team.name}"
                        if (!timeOuts.usable || left <= 0) attributes["disabled"] = "disabled"
                        teamMark(team)
                        span { +"Time-out +${TimerText.durationLabel(timeOuts.seconds)} · $left left" }
                    }
                }
            }
        }
    }

    private fun HtmlBlockTag.shotPanel(a: ActiveUiState) {
        val shot = a.shotClock ?: return
        div(
            if (shot.over) {
                "tr-shot is-over"
            } else if (shot.warning) {
                "tr-shot is-warning"
            } else {
                "tr-shot"
            },
        ) {
            attributes["data-shot"] = ""
            span("tr-shot-label") {
                attributes["data-shot-label"] = ""
                +(if (shot.over) "HURRY UP · TIME!" else "HURRY UP")
            }
            span("tr-shot-digits tabular") {
                attributes["data-shot-clock"] = ""
                attributes["role"] = "timer"
                attributes["aria-label"] = shot.spokenClock
                +shot.clock
            }
            commandIcon("shot-start", "Restart Hurry up", "t-replay")
            commandIcon("shot-cancel", "Stop Hurry up", Icons.CLOSE)
        }
    }

    private fun HtmlBlockTag.seats(seats: List<SeatUi>, a: ActiveUiState) {
        div("tr-seats") {
            p("tr-seats-title") { +"Who resolves now? Highest initiative acts first." }
            div("tr-seat-grid") {
                seats.forEach { seat ->
                    val css = listOfNotNull(
                        "tr-seat",
                        if (seat.team == Team.RED) "team-red" else "team-blue",
                        "is-acting".takeIf { seat.acting },
                        "is-resolved".takeIf { seat.resolved },
                    ).joinToString(" ")
                    button(type = ButtonType.submit, classes = css) {
                        attributes["name"] = "cmd"
                        attributes["value"] = "hero:${seat.seat}"
                        attributes["aria-pressed"] = seat.acting.toString()
                        seat.portrait?.let { img(src = HubCatalog.url(it), alt = "", classes = "tr-seat-portrait") }
                        span("tr-seat-text") {
                            span("tr-seat-hero") { +seat.heroName }
                            seat.playerName?.let { span("tr-seat-player") { +it } }
                            span("visually-hidden") {
                                +(if (seat.team == Team.RED) ", Red" else ", Blue")
                                if (seat.acting) +", resolving now"
                                if (seat.resolved) +", resolved"
                            }
                        }
                        if (seat.resolved) icon(Icons.CHECK, "tr-seat-done")
                    }
                }
            }
            p("tr-seats-note") { +"Hero ${(a.actionSlot ?: 0) + 1} of ${a.actionSlots} this turn." }
        }
    }

    private fun HtmlBlockTag.checklist(a: ActiveUiState) {
        val items = a.checklist ?: return
        val current = items.firstOrNull { it.state == ChecklistState.CURRENT }
        if (current?.id == LEVEL_UP && a.timed) levelsButton("Level costs & card Tiers")
        div("tr-checklist") {
            h3("tr-checklist-title") { +"End of round" }
            ol {
                items.forEach { item ->
                    li("tr-check is-${item.state.name.lowercase()}") {
                        if (item.state == ChecklistState.CURRENT) attributes["aria-current"] = "step"
                        span("tr-check-num") { +item.number.toString() }
                        div("tr-check-text") {
                            span("tr-check-title") { +item.title }
                            item.timerName?.let { span("tr-check-timer") { +it } }
                            if (item.state == ChecklistState.CURRENT) item.detail?.let { p("tr-check-detail") { +it } }
                            if (item.state == ChecklistState.CURRENT && item.id == LEVEL_UP && !a.timed) {
                                levelsButton("Levels")
                            }
                        }
                        if (item.state == ChecklistState.DONE) icon(Icons.CHECK, "tr-check-done")
                    }
                }
            }
        }
    }

    private fun HtmlBlockTag.levelsButton(label: String) {
        button(type = ButtonType.button, classes = "btn btn-tonal tr-levels") {
            attributes["data-dialog-open"] = "levels-dialog"
            icon("t-military_tech")
            span { +label }
        }
    }

    // ---- round view ----------------------------------------------------------------------------

    private fun HtmlBlockTag.roundBody(a: ActiveUiState) {
        div("tr-body") {
            ol("tr-rows") {
                var group: String? = null
                a.rows.forEach { row ->
                    if (row.group != group) {
                        group = row.group
                        li("tr-group") { h3 { +row.group } }
                    }
                    if (row.state == RoundRowState.CURRENT) currentRow(a, row) else otherRow(a, row)
                }
            }
        }
    }

    private fun OL.currentRow(a: ActiveUiState, row: RoundRowUi) {
        li("tr-row is-current") {
            attributes["data-current-row"] = ""
            attributes["aria-current"] = "step"
            div("tr-row-head") {
                div("tr-row-text") {
                    span("tr-row-title") { +a.title }
                    span("tr-row-context") { +(row.context ?: a.context) }
                }
                previousButton()
            }
            a.hint?.let { p("tr-hint") { +it } }
            clockButton(a, large = false)
            progressBar(a)
            p("tr-match") {
                +"Match "
                span("tabular") {
                    attributes["data-match-clock"] = ""
                    +a.matchClock
                }
            }
            tableRules(a)
            a.currentDetail?.let { p("tr-check-detail") { +it } }
            if (a.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP } == true) {
                levelsButton("Level costs & card Tiers")
            }
            a.seats?.let { seats(it, a) }
        }
    }

    private fun OL.otherRow(a: ActiveUiState, row: RoundRowUi) {
        val done = row.state == RoundRowState.DONE
        li(if (done) "tr-row is-done" else "tr-row is-upcoming") {
            button(type = ButtonType.button, classes = "tr-row-button") {
                attributes["data-jump"] = "jump:${a.round}:${row.index}"
                attributes["data-jump-name"] = row.jumpName
                attributes["aria-label"] = "${row.description("")}. Jump to this step"
                span("tr-row-mark") {
                    attributes["aria-hidden"] = "true"
                    if (done) icon(Icons.CHECK)
                }
                span("tr-row-text") {
                    span("tr-row-title") { +row.title }
                    row.context?.let { span("tr-row-context") { +it } }
                }
                span("tr-row-length tabular") { +row.length }
            }
        }
    }

    // ---- controls and dialogs --------------------------------------------------------------------

    private fun HtmlBlockTag.controls(a: ActiveUiState) {
        div("tr-controls") {
            button(type = ButtonType.submit, classes = "btn tr-pause") {
                attributes["name"] = "cmd"
                attributes["value"] = "toggle"
                icon(if (a.paused) "t-play_arrow" else "t-pause")
                span { +(if (a.paused) "Resume" else "Pause") }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary tr-next") {
                attributes["name"] = "cmd"
                attributes["value"] = "next"
                attributes["data-unlock-audio"] = ""
                span { +a.nextLabel }
                icon("t-skip_next")
            }
        }
    }

    const val LEVEL_UP = "level_up"
    private const val PROGRESS_MAX = 1000
    const val MILLIS = 1000L
}

/** What the running-timer bar needs. */
internal data class BarModel(
    val ui: dev.quinton.goa.core.presentation.timer.RunningTimerUi,
    val serverNow: Long,
    val zeroAt: Long?,
    val durationMs: Long?,
)

/** A team's mark alone (◆ Red / ● Blue), named for screen readers: never colour only. */
internal fun FlowContent.teamMark(team: Team) {
    val (css, mark, label) = if (team == Team.RED) Triple("team-red", "◆", "Red") else Triple("team-blue", "●", "Blue")
    span("team-badge $css") {
        attributes["role"] = "img"
        attributes["aria-label"] = label
        span("team-mark") {
            attributes["aria-hidden"] = "true"
            +mark
        }
    }
}
