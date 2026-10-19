package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.timer.PendingMatchItem
import dev.quinton.goa.core.presentation.timer.PresetOption
import dev.quinton.goa.core.presentation.timer.TimerPresets
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.core.presentation.timer.TonightUi
import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.sectionHeader
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.span

/** What the Timer home shows (the phone's start card, pending matches and presets). */
internal data class TimerHomeModel(
    val presets: List<PresetOption>,
    val selectedId: String,
    val tonight: TonightUi?,
    val seats: Int,
    val pending: List<PendingMatchItem>,
    val pushOn: Boolean,
) {
    val selected: PresetOption? get() = presets.firstOrNull { it.id == selectedId }
}

internal object TimerFormat {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val day = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    /** "5:06 PM" today, else "Oct 16, 2026, 5:06 PM": pending matches from one evening differ by time. */
    fun since(millis: Long, clock: HubClock): String {
        val at = Instant.ofEpochMilli(millis).atZone(clock.zone)
        return if (at.toLocalDate() == clock.today()) at.format(time) else "${at.format(day)}, ${at.format(time)}"
    }
}

internal object TimerHomePage {
    fun home(out: FlowContent, m: TimerHomeModel) = with(out) {
        p("tr-note") {
            +(
                "Timers are table rules. The rulebook sets no time limits; strategy talk is " +
                    "allowed from the end of one turn until cards are revealed."
                )
        }
        startCard(m)
        m.tonight?.let { tonightCard(it) }
        if (m.selected?.usesActionTimer == true) seatsCard(m.seats)
        card("tr-setup-link") {
            linkButton("Match setup", TimerRoutes.SETUP, ButtonStyle.TONAL)
            p("tr-muted") { +"Mode, counters to lay out and the Tie Breaker coin." }
        }
        if (m.pending.isNotEmpty()) {
            sectionHeader("Pending matches")
            m.pending.forEach { pendingRow(it) }
        }
        div("tr-section-head") {
            sectionHeader("Presets")
            a(TimerRoutes.PRESETS, classes = "btn btn-text") { +"Manage" }
        }
        m.presets.forEach { presetRow(it, it.id == m.selectedId) }
        pushCard(m.pushOn)
    }

    private fun FlowContent.startCard(m: TimerHomeModel) {
        card("tr-start") {
            span("tr-overline") { +"Phase timer" }
            h2 { +(m.selected?.name ?: "No preset") }
            m.selected?.let { p("tr-muted") { +it.summary } }
            // Start swaps the running timer into this page (htmx), so the tap that started it also
            // unlocks the chimes (browsers allow sound only after a tap on the page).
            postForm(TimerRoutes.START, classes = "tr-start-form") {
                attributes["hx-post"] = TimerRoutes.START
                attributes["hx-target"] = "body"
                attributes["hx-swap"] = "innerHTML"
                button(type = ButtonType.submit, classes = "btn btn-primary tr-start-button") {
                    attributes["data-unlock-audio"] = ""
                    icon("t-play_arrow")
                    span { +"Start timer" }
                }
            }
        }
    }

    private fun FlowContent.tonightCard(t: TonightUi) {
        card("tr-tonight") {
            div("tr-stepper-row") {
                div("tr-stepper-text") {
                    h3 { +"Turn length tonight" }
                    p("tr-muted") {
                        +(if (t.adjusted) "Preset: ${TimerText.durationLabel(t.presetSeconds)}" else "From the preset")
                    }
                }
                stepper(
                    action = "${TimerRoutes.HOME}/tonight",
                    name = "seconds",
                    value = t.seconds,
                    step = TimerPresets.TONIGHT_STEP_SECONDS,
                    min = TimerPresets.TONIGHT_MIN_SECONDS,
                    max = TimerPresets.TONIGHT_MAX_SECONDS,
                    shown = TimerText.durationLabel(t.seconds),
                    less = "15 seconds shorter",
                    more = "15 seconds longer",
                    spoken = "Turns ${TimerText.spokenDuration(t.seconds.toLong())} tonight",
                )
            }
            if (t.adjusted) {
                div("actions") {
                    p("tr-muted") { +"Just for tonight." }
                    postForm("${TimerRoutes.HOME}/tonight") {
                        input(type = InputType.hidden, name = "op") { value = "reset" }
                        button(type = ButtonType.submit, classes = ButtonStyle.TEXT.css) { +"Reset" }
                    }
                    postForm("${TimerRoutes.HOME}/tonight") {
                        input(type = InputType.hidden, name = "op") { value = "save" }
                        button(type = ButtonType.submit, classes = ButtonStyle.TONAL.css) { +"Save to preset" }
                    }
                }
            }
        }
    }

    private fun FlowContent.seatsCard(seats: Int) {
        card("tr-seats-card") {
            div("tr-stepper-row") {
                div("tr-stepper-text") {
                    h3 { +"Heroes at the table" }
                    p("tr-muted") { +"The Action timer runs once per hero each turn." }
                }
                stepper(
                    action = "${TimerRoutes.HOME}/seats",
                    name = "seats",
                    value = seats,
                    step = 1,
                    min = WebTimer.MIN_SEATS,
                    max = WebTimer.MAX_SEATS,
                    shown = seats.toString(),
                    less = "Fewer heroes",
                    more = "More heroes",
                    spoken = "$seats heroes",
                )
            }
        }
    }

    /** − value + with 48 px targets; each button posts the new value. */
    @Suppress("LongParameterList")
    fun HtmlBlockTag.stepper(
        action: String,
        name: String,
        value: Int,
        step: Int,
        min: Int,
        max: Int,
        shown: String,
        less: String,
        more: String,
        spoken: String,
    ) {
        div("tr-stepper") {
            postForm(action) {
                input(type = InputType.hidden, name = name) { this.value = (value - step).toString() }
                button(type = ButtonType.submit, classes = "icon-button tr-stepper-button") {
                    attributes["aria-label"] = less
                    if (value - step < min) attributes["disabled"] = "disabled"
                    icon("t-remove")
                }
            }
            span("tr-stepper-value tabular") {
                attributes["aria-label"] = spoken
                +shown
            }
            postForm(action) {
                input(type = InputType.hidden, name = name) { this.value = (value + step).toString() }
                button(type = ButtonType.submit, classes = "icon-button tr-stepper-button") {
                    attributes["aria-label"] = more
                    if (value + step > max) attributes["disabled"] = "disabled"
                    icon(Icons.ADD)
                }
            }
        }
    }

    private fun FlowContent.pendingRow(item: PendingMatchItem) {
        card("tr-pending") {
            div("tr-pending-head") {
                div {
                    h3 { +item.title }
                    p("tr-muted") { +item.subtitle }
                }
                a("${TimerRoutes.SETUP}?match=${item.id}", classes = "btn btn-tonal") { +"Set up" }
            }
            item.red?.let { teamLine(Team.RED, it) }
            item.blue?.let { teamLine(Team.BLUE, it) }
            val dialogId = "discard-${item.id.filter { it.isLetterOrDigit() }}"
            button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                attributes["data-dialog-open"] = dialogId
                +"Discard"
            }
            hubDialog(dialogId, "Discard this pending match?") {
                p { +"It leaves this list and the match log. Matches with a result aren't affected." }
                div("actions") {
                    postForm("${TimerRoutes.HOME}/pending/${item.id}/discard") {
                        button(type = ButtonType.submit, classes = ButtonStyle.DANGER.css) { +"Discard" }
                    }
                    button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                        attributes["data-dialog-close"] = ""
                        +"Keep"
                    }
                }
            }
        }
    }

    private fun HtmlBlockTag.teamLine(team: Team, heroes: String) {
        p("tr-team-line") {
            teamMark(team)
            span { +heroes }
        }
    }

    private fun FlowContent.presetRow(preset: PresetOption, selected: Boolean) {
        postForm("${TimerRoutes.HOME}/select", classes = "tr-preset-form") {
            input(type = InputType.hidden, name = "preset") { value = preset.id }
            button(type = ButtonType.submit, classes = if (selected) "row tr-preset is-selected" else "row tr-preset") {
                attributes["aria-pressed"] = selected.toString()
                span("tr-radio") {
                    attributes["aria-hidden"] = "true"
                    if (selected) icon(Icons.CHECK)
                }
                span("row-text") {
                    span("row-title") { +preset.name }
                    span("row-subtitle") { +preset.summary }
                }
            }
        }
    }

    /**
     * "Lock-screen alerts": Web Push for this browser, asked from a tap (`timer.js` + the core's
     * `push.js`). iOS allows it only in the app added to the Home Screen (16.4 or later).
     */
    fun FlowContent.pushCard(on: Boolean) {
        card("tr-push") {
            attributes["data-push-card"] = ""
            attributes["data-push-on"] = on.toString()
            h3 { +"Lock-screen alerts" }
            p("tr-muted") {
                attributes["data-push-state"] = ""
                +(
                    if (on) {
                        "On for this browser: when a phase ends, a notification reaches you even with the phone locked."
                    } else {
                        "Off. A locked phone pauses this page, so the chime can't play; a notification can."
                    }
                    )
            }
            p("tr-muted tr-small") {
                +(
                    "On iPhone this needs the app on the Home Screen (Share → Add to Home Screen, iOS 16.4 or " +
                        "later), opened from there."
                    )
            }
            div("actions") {
                if (on) {
                    button(type = ButtonType.button, classes = ButtonStyle.TONAL.css) {
                        attributes["data-push-test"] = ""
                        +"Send a test"
                    }
                    button(type = ButtonType.button, classes = ButtonStyle.TEXT.css) {
                        attributes["data-push-unsubscribe"] = ""
                        +"Turn off"
                    }
                } else {
                    button(type = ButtonType.button, classes = ButtonStyle.PRIMARY.css) {
                        attributes["data-push-subscribe"] = ""
                        icon(Icons.NOTIFICATIONS)
                        span { +"Turn on" }
                    }
                }
            }
        }
    }
}
