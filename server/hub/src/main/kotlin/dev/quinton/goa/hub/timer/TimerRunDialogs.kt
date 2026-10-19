package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.timer.ActiveUiState
import dev.quinton.goa.core.presentation.timer.MatchSetupText
import dev.quinton.goa.core.presentation.timer.RoundRowState
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HtmlBlockTag
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span

/*
 * The running timer's dialogs (end match, Tie Breaker coin, jump), its summary once the
 * match is over, and the "timer running" bar for the other pages.
 */

internal fun HtmlBlockTag.runDialogs(a: ActiveUiState) {
    hubDialog("end-dialog", "End the match?") {
        p {
            +(
                "A match can end mid-turn: the last push, a push into the Throne Zone, or a team's last " +
                    "Life counter. The timer stops and the duration is kept for the match log."
                )
        }
        div("actions") {
            button(type = ButtonType.submit, classes = ButtonStyle.DANGER.css) {
                attributes["name"] = "cmd"
                attributes["value"] = "end"
                +"End match"
            }
            button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                attributes["data-dialog-close"] = ""
                +"Keep playing"
            }
        }
    }
    hubDialog("coin-dialog", "Tie Breaker coin") {
        p { +"Which team's symbol is showing on the coin?" }
        div("actions") {
            Team.entries.forEach { team ->
                button(
                    type = ButtonType.submit,
                    classes = "btn btn-outlined ${if (team == Team.RED) "team-red" else "team-blue"}",
                ) {
                    attributes["name"] = "cmd"
                    attributes["value"] = "coin:${team.name}"
                    if (a.tieBreaker == team) attributes["aria-pressed"] = "true"
                    +"${MatchSetupText.teamName(team)} shows"
                }
            }
            if (a.tieBreaker != null) {
                button(type = ButtonType.submit, classes = ButtonStyle.TEXT.css) {
                    attributes["name"] = "cmd"
                    attributes["value"] = "coin:none"
                    +"Stop tracking"
                }
            }
        }
    }
    if (a.layout == TimerLayout.ROUND) jumpDialog(a)
}

/** "Jump to Turn 2 · Play a card?": a mis-tap is cheap to undo, but never silent (timer.js fills in the target). */
private fun HtmlBlockTag.jumpDialog(a: ActiveUiState) {
    val from = a.rows.firstOrNull { it.state == RoundRowState.CURRENT }?.jumpName ?: a.title
    hubDialog("jump-dialog", "Jump?") {
        p {
            +(
                "It starts fresh. Previous phase within ${TimerEngine.UNDO_WINDOW_MS / TimerRunPage.MILLIS} seconds " +
                    "returns to $from with its time."
                )
        }
        div("actions") {
            button(type = ButtonType.submit, classes = ButtonStyle.PRIMARY.css) {
                attributes["name"] = "cmd"
                attributes["value"] = ""
                attributes["data-jump-confirm"] = ""
                +"Jump"
            }
            button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                attributes["data-dialog-close"] = ""
                +"Cancel"
            }
        }
    }
}

// ---- summary ---------------------------------------------------------------------------------

internal fun FlowContent.summaryRoot(m: RunModel) {
    val s = m.summary ?: return
    form(action = TimerRoutes.CMD, method = FormMethod.post, classes = "tr tr-summary") {
        id = TimerRunPage.ROOT_ID
        attributes["data-timer"] = ""
        attributes["data-status"] = TimerStatus.ENDED.name.lowercase()
        attributes["data-server-now"] = m.live.serverNow.toString()
        attributes["data-step-key"] = m.live.stepKey
        attributes["data-announce"] = "Match over. ${s.duration}."
        card("tr-summary-card") {
            h2 { +"Match over" }
            dl("facts") {
                dt { +"Duration" }
                dd { +s.duration }
                dt { +"Rounds" }
                dd { +s.rounds.toString() }
                dt { +"Timer" }
                dd { +s.presetName }
                s.mode?.let {
                    dt { +"Mode" }
                    dd { +TimerText.modeLabel(it) }
                }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary tr-log") {
                attributes["name"] = "cmd"
                attributes["value"] = "log"
                +"Log result"
            }
            p("tr-summary-note") {
                +(
                    if (s.hasMatch) {
                        "The duration is saved to the match. Log the winner and win condition next."
                    } else {
                        "Log result opens a new match with this duration filled in."
                    }
                    )
            }
            if (s.hasMatch) {
                button(type = ButtonType.submit, classes = ButtonStyle.TEXT.css) {
                    attributes["name"] = "cmd"
                    attributes["value"] = "clear"
                    +"Close, log later"
                }
            } else {
                button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                    attributes["data-dialog-open"] = "discard-dialog"
                    +"Discard"
                }
            }
        }
        hubDialog("discard-dialog", "Discard this match?") {
            p { +"The ${s.duration} of play won't be logged anywhere." }
            div("actions") {
                button(type = ButtonType.submit, classes = ButtonStyle.PRIMARY.css) {
                    attributes["name"] = "cmd"
                    attributes["value"] = "log"
                    +"Log result"
                }
                button(type = ButtonType.submit, classes = ButtonStyle.DANGER.css) {
                    attributes["name"] = "cmd"
                    attributes["value"] = "clear"
                    +"Discard"
                }
            }
        }
    }
}

/** The other pages' "timer running" bar (the phone's RunningTimerBar). */
internal fun FlowContent.runningBar(m: BarModel) {
    a(
        TimerRoutes.RUN,
        classes = if (m.ui.paused) {
            "timer-bar is-paused"
        } else if (m.ui.overtime) {
            "timer-bar is-overtime"
        } else {
            "timer-bar"
        },
    ) {
        id = "timer-bar"
        attributes["data-timer-bar"] = ""
        attributes["data-status"] = if (m.ui.paused) "paused" else "running"
        attributes["data-server-now"] = m.serverNow.toString()
        m.zeroAt?.let { attributes["data-zero-at"] = it.toString() }
        m.durationMs?.let { attributes["data-duration"] = it.toString() }
        attributes["aria-label"] = m.ui.description
        icon(if (m.ui.paused) "t-pause" else "t-timer")
        span("timer-bar-text") {
            span("timer-bar-phase") { +m.ui.phase }
            span("timer-bar-context") { +m.ui.context }
        }
        span("timer-bar-clock tabular") {
            attributes["data-clock"] = ""
            +m.ui.clock
        }
    }
}
