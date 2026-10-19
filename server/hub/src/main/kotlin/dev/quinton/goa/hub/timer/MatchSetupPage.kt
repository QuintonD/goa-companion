package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.timer.CountersUi
import dev.quinton.goa.core.presentation.timer.MatchSetupText
import dev.quinton.goa.core.presentation.timer.MatchSetupUiState
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.chip
import dev.quinton.goa.hub.web.ui.chipRow
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.teamBadge
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/**
 * Match setup on the web (the phone's MatchSetupScreen): players and seat layout (or the seats of a
 * pending match from the Draft area), mode, the counters to lay out by heroes in play, the Tie
 * Breaker coin, the timer preset, and Start timer. Choices travel in the URL until Start.
 */
internal object MatchSetupPage {
    fun render(out: FlowContent, built: TimerFeature.SetupBuilt, q: SetupQuery) = with(out) {
        if (built.missing) {
            emptyState(
                "That match isn't here",
                "It may have been logged or discarded on another phone. Set up a new match instead.",
                "New match setup",
                TimerRoutes.SETUP,
            )
            return@with
        }
        val s = built.state
        s.linkedMatch?.let {
            notice(
                "${it.title}: Red ${it.redSeats} · Blue ${it.blueSeats}. " +
                    "Mode, Tie Breaker coin and duration are saved to this match.",
            )
        }
        sectionHeader("Players")
        playersCard(s, q)
        if (!s.playersLocked && s.layouts.size > 1) {
            sectionHeader("Teams")
            card {
                chipRow {
                    s.layouts.forEach { layout ->
                        chip(
                            "${layout.title} · ${layout.teams}${if (layout.official) "" else " · House rule"}",
                            selected = layout.id == s.selectedLayoutId,
                            href = q.copy(layout = layout.id).url(),
                        )
                    }
                }
                s.layouts.firstOrNull { it.id == s.selectedLayoutId }?.guidance?.let { p("tr-muted") { +it } }
            }
        }
        sectionHeader("Mode")
        modes(s, q)
        sectionHeader("Lay out")
        s.counters?.let { counters(it) }
        sectionHeader("Tie Breaker coin")
        coin(s, q, built.storedCoin)
        sectionHeader("Timer preset")
        card {
            chipRow {
                s.presets.forEach { preset ->
                    chip(
                        preset.name,
                        selected = preset.id == s.selectedPresetId,
                        href = q.copy(preset = preset.id).url(),
                    )
                }
            }
            s.presets.firstOrNull { it.id == s.selectedPresetId }?.let { p("tr-muted") { +it.summary } }
        }
        if (s.replacesRunningTimer) notice("A timer is already running; starting replaces it.")
        postForm(TimerRoutes.SETUP_START, classes = "tr-setup-start") {
            attributes["hx-post"] = TimerRoutes.SETUP_START
            attributes["hx-target"] = "body"
            attributes["hx-swap"] = "innerHTML"
            hidden(q)
            button(type = ButtonType.submit, classes = "btn btn-primary tr-start-button") {
                attributes["data-unlock-audio"] = ""
                icon("t-play_arrow")
                span { +"Start timer" }
            }
        }
        if (s.firstGameAdvice.isNotEmpty()) p("tr-muted") { +s.firstGameAdvice }
    }

    private fun HtmlBlockTag.hidden(q: SetupQuery) {
        q.fields().forEach { (name, value) -> input(type = InputType.hidden, name = name) { this.value = value } }
    }

    private fun FlowContent.playersCard(s: MatchSetupUiState, q: SetupQuery) {
        card("tr-players") {
            div("tr-stepper-row") {
                div("tr-stepper-text") {
                    h3 { +"${s.players} players" }
                    p("tr-muted") {
                        +when {
                            s.seatsSource != null -> s.seatsSource!!
                            s.recommended -> "Recommended count"
                            else -> "Supported: ${s.playerRange.first} to ${s.playerRange.last}"
                        }
                    }
                }
                if (!s.playersLocked) {
                    div("tr-stepper") {
                        stepLink(q, s.players - 1, s.players - 1 >= s.playerRange.first, "Fewer players", "t-remove")
                        span("tr-stepper-value tabular") {
                            attributes["aria-label"] = "${s.players} players"
                            +s.players.toString()
                        }
                        stepLink(q, s.players + 1, s.players + 1 <= s.playerRange.last, "More players", Icons.ADD)
                    }
                }
            }
            s.extendedNote?.let { p("tr-muted tr-small") { +it } }
        }
    }

    private fun HtmlBlockTag.stepLink(q: SetupQuery, players: Int, enabled: Boolean, label: String, iconName: String) {
        if (enabled) {
            a(q.copy(players = players, layout = null).url(), classes = "icon-button tr-stepper-button") {
                attributes["aria-label"] = label
                icon(iconName)
            }
        } else {
            span("icon-button tr-stepper-button is-disabled") {
                attributes["aria-hidden"] = "true"
                icon(iconName)
            }
        }
    }

    private fun FlowContent.modes(s: MatchSetupUiState, q: SetupQuery) {
        div("tr-modes") {
            s.modes.forEach { mode ->
                val selected = mode.mode == s.selectedMode
                val css = listOfNotNull(
                    "tr-mode",
                    "is-selected".takeIf {
                        selected
                    },
                    "is-blocked".takeIf { !mode.available },
                )
                    .joinToString(" ")
                val body: HtmlBlockTag.() -> Unit = {
                    span("tr-mode-title") {
                        +mode.title
                        mode.requiredLabel?.let { span("tr-badge") { +it } }
                    }
                    span("tr-muted") { +(mode.reason ?: "${mode.waves} · ${mode.life}") }
                }
                if (mode.available) {
                    a(q.copy(mode = mode.mode).url(), classes = css) {
                        if (selected) attributes["aria-current"] = "true"
                        body()
                    }
                } else {
                    div(css) {
                        attributes["aria-disabled"] = "true"
                        body()
                    }
                }
            }
        }
    }

    private fun FlowContent.counters(c: CountersUi) {
        card("tr-counters") {
            h3 { +"Wave counters" }
            repeat(c.lanes) { lane ->
                p("tr-count") {
                    if (c.lanes > 1) span("tr-count-label") { +"Lane ${lane + 1}" }
                    tokens(c.wavesPerLane, "tr-token-wave")
                    span("tr-count-number") { +"${c.wavesPerLane} Wave counters" }
                }
            }
            h3 { +"Life counters per team" }
            val life = c.lifePerTeam
            if (life == null) {
                p("tr-house-note") { +MatchSetupText.lifeNotSpecified(c) }
            } else {
                Team.entries.forEach { team ->
                    p("tr-count") {
                        teamBadge(team)
                        tokens(life, if (team == Team.RED) "tr-token-red" else "tr-token-blue")
                        span("tr-count-number") { +"$life Life counters" }
                    }
                }
            }
            c.houseRuleNote?.let { p("tr-house-note") { +it } }
            p("tr-muted") { +MatchSetupText.seatsLine(c) }
            c.modeNote?.let { note ->
                h3 { +note.title }
                ul("tr-points") { note.points.forEach { li { +it } } }
            }
        }
    }

    private fun HtmlBlockTag.tokens(count: Int, css: String) {
        span("tr-tokens") {
            attributes["aria-hidden"] = "true"
            repeat(count.coerceAtMost(MAX_TOKENS)) { span("tr-token $css") }
        }
    }

    private fun FlowContent.coin(s: MatchSetupUiState, q: SetupQuery, storedCoin: Team?) {
        card("tr-coin-card") {
            val coin = s.tieBreaker
            div(
                if (coin ==
                    null
                ) {
                    "tr-coin-face"
                } else {
                    "tr-coin-face ${if (coin == Team.RED) "team-red" else "team-blue"}"
                },
            ) {
                attributes["aria-hidden"] = "true"
                +(
                    when (coin) {
                        Team.RED -> "◆"
                        Team.BLUE -> "●"
                        null -> "?"
                    }
                    )
            }
            p("tr-coin-message") {
                attributes["role"] = "status"
                +MatchSetupText.coinMessage(s)
            }
            coin?.let { p("tr-muted") { +MatchSetupText.coinAdvantage(it) } }
            when {
                // The coin was flipped once already (rulebook p.5): correct a mistake, don't flip again.
                s.coinPlaced && !s.correctingCoin ->
                    a(q.copy(correcting = true).url(), classes = ButtonStyle.OUTLINED.css) { +"Correct the coin" }

                s.coinPlaced -> sideChips(s, q, "Which side is showing?", storedCoin)

                else -> {
                    postForm(TimerRoutes.SETUP_FLIP) {
                        hidden(q)
                        button(type = ButtonType.submit, classes = ButtonStyle.OUTLINED.css) {
                            +(if (coin == null) "Flip coin" else "Flip again")
                        }
                    }
                    sideChips(s, q, "Flipped the real coin?", storedCoin)
                }
            }
            if (s.tieBreakerRule.isNotEmpty()) {
                h3 { +"How the coin is used" }
                ul("tr-points") { s.tieBreakerRule.forEach { li { +it } } }
            }
        }
    }

    /** Red/Blue shows. Picking the side the placed coin already shows is no correction: it keeps that coin. */
    private fun HtmlBlockTag.sideChips(s: MatchSetupUiState, q: SetupQuery, label: String, storedCoin: Team?) {
        p("tr-muted") { +label }
        val placed = storedCoin
        chipRow {
            Team.entries.forEach { team ->
                chip(
                    "${MatchSetupText.teamName(team)} shows",
                    selected = s.tieBreaker == team,
                    href = q.copy(coin = team.takeIf { it != placed }).url(),
                )
            }
        }
    }

    private const val MAX_TOKENS = 12
}
