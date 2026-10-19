package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.meta.WinConditionMix
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.presentation.players.MatchRowUi
import dev.quinton.goa.core.presentation.players.SeatUi
import dev.quinton.goa.core.presentation.players.formatDelta
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.core.presentation.players.resultLabel
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.TopAction
import dev.quinton.goa.hub.web.ui.TopActions
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.teamBadge
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/** The Players area's URLs. Other areas link to these; keep them stable. */
object PlayersPaths {
    const val HOME = "/players"
    const val LOG = "/players/log"
    const val META = "/players/meta"
    const val NEW_PLAYER = "/players/new"
    const val SAME_PEOPLE = "/players/same-people"
    const val STATS_PAGE = "/players/stats-page"
    const val NEW_MATCH = "/players/matches/new"
    const val UNDO = "/players/matches/undo"

    fun player(id: String) = "/players/${segment(id)}"

    fun editPlayer(id: String) = "${player(id)}/edit"

    fun merge(id: String) = "${player(id)}/merge"

    fun match(id: String) = "/players/matches/${segment(id)}"

    fun editMatch(id: String) = "${match(id)}/edit"

    fun result(id: String) = "${match(id)}/result"

    fun saved(id: String) = "${match(id)}/saved"

    fun rematch(id: String) = "${match(id)}/rematch"

    fun delete(id: String) = "${match(id)}/delete"

    /** The log, scrolled to (and outlining) the match just saved. */
    fun logAt(id: String) = "$LOG?saved=${segment(id)}#match-${anchor(id)}"

    /** The Group area's personal invite for a player (ADR-031). */
    fun invite(id: String) = "/group/invite?player=${segment(id)}"

    /** The Draft area, filled with a match's players to draft again. */
    fun redraft(id: String) = "/draft?rematch=${segment(id)}"

    /** The Timer area's Match setup for a rematch of [id] ([kind]: a `RematchKind` name). */
    fun timerRematch(id: String, kind: String) = "/timer/setup?rematch=${segment(id)}&kind=$kind"

    /** The Timer area's Match setup for a pending match ("Resume: setup & timer"). */
    fun timerResume(id: String) = "/timer/setup?match=${segment(id)}"

    fun segment(id: String): String = URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")

    /** An id usable in an HTML id / fragment. */
    fun anchor(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

/** The three sections of the Players tab, as on the phone. */
enum class PlayersSection(val label: String, val path: String) {
    PLAYERS("Players", PlayersPaths.HOME),
    MATCHES("Matches", PlayersPaths.LOG),
    META("Meta", PlayersPaths.META),
}

private val GroupAction = TopAction("Group", "/group", dev.quinton.goa.hub.web.ui.Icons.GROUP)

/** A page of the Players area: its stylesheet and script, the tab highlighted. */
fun playersPage(
    title: String,
    back: String? = null,
    actions: List<TopAction> = if (back == null) listOf(GroupAction, TopActions.Settings) else emptyList(),
    showTabs: Boolean = true,
) = HubPage(
    title = title,
    tab = Tab.PLAYERS,
    back = back,
    actions = actions,
    showTabs = showTabs,
    scripts = listOf("players/players.js"),
    styles = listOf("players/players.css"),
)

/** Players · Matches · Meta. */
fun FlowContent.sectionTabs(current: PlayersSection) {
    nav("pl-tabs") {
        attributes["aria-label"] = "Players sections"
        PlayersSection.entries.forEach { section ->
            a(section.path, classes = if (section == current) "pl-tab selected" else "pl-tab") {
                if (section == current) attributes["aria-current"] = "page"
                +section.label
            }
        }
    }
}

/** The phone's snackbar: a one-time message after an action. */
fun FlowContent.flashNotice(message: String?) {
    if (message != null) notice(message)
}

/** Text for screen readers only (the shell's `visually-hidden`). */
fun FlowContent.srOnly(text: String) {
    span("visually-hidden") { +text }
}

/** "↗ +24" with what it covers; colour plus arrow, never colour alone. */
fun FlowContent.delta(value: Double?, label: String = "rating change", caption: String? = null) {
    if (value == null) return
    val rounded = value.roundToInt()
    val (arrow, css) = when {
        rounded > 0 -> "↗" to "up"
        rounded < 0 -> "↘" to "down"
        else -> "→" to "flat"
    }
    span("pl-delta $css") {
        span {
            attributes["aria-hidden"] = "true"
            +"$arrow ${formatDelta(value)}"
            caption?.let { span("pl-caption") { +" $it" } }
        }
        srOnly("$label ${formatDelta(value)}" + (caption?.let { ", $it" } ?: ""))
    }
}

/** A small outlined pill ("Provisional", "×2", "H", "You"). */
fun FlowContent.pill(text: String, modifier: String? = null, spoken: String? = null) {
    span(listOfNotNull("pl-pill", modifier).joinToString(" ")) {
        if (spoken == null) {
            +text
        } else {
            span {
                attributes["aria-hidden"] = "true"
                +text
            }
            srOnly(spoken)
        }
    }
}

fun FlowContent.provisionalPill() = pill("Provisional", "pl-pill-teal")

/** A hero's portrait from the bundled catalog, or a "?" for an unknown hero. Decorative. */
fun FlowContent.heroPortrait(portrait: String?, size: String = "md") {
    if (portrait != null) {
        val src = HubCatalog.thumbUrl(portrait, HubCatalog.THUMB_SMALL)
        img(src = src, alt = "", classes = "pl-portrait pl-portrait-$size") {
            attributes["loading"] = "lazy"
            attributes["decoding"] = "async"
        }
    } else {
        span("pl-portrait pl-portrait-$size pl-portrait-unknown") {
            attributes["aria-hidden"] = "true"
            +"?"
        }
    }
}

/** The result chip: "◆ Red won", "● Blue won", "Pending", "Abandoned", "No winner". */
fun FlowContent.resultChip(status: MatchStatus, winner: Team?) {
    val css = when {
        status == MatchStatus.COMPLETED && winner == Team.RED -> "red"
        status == MatchStatus.COMPLETED && winner == Team.BLUE -> "blue"
        status == MatchStatus.PENDING -> "pending"
        status == MatchStatus.COMPLETED -> "error"
        else -> "muted"
    }
    span("pl-result pl-result-$css") { +resultLabel(status, winner) }
}

/** Big numbers over captions (the phone's StatRow). */
fun FlowContent.statTiles(tiles: List<Pair<String, String>>) {
    div("pl-stats") {
        tiles.forEach { (value, caption) ->
            div("pl-stat") {
                span("pl-stat-value") { +value }
                span("pl-stat-caption") { +caption }
            }
        }
    }
}

/** Share of wins per condition, as labelled bars ("Not recorded" when nothing was logged). */
fun FlowContent.winConditionBars(mix: WinConditionMix) {
    ul("pl-bars") {
        (WinCondition.entries + listOf(null)).forEach { condition ->
            val count = mix.counts[condition] ?: 0
            if (count == 0 && condition == null) return@forEach
            val name = condition?.label() ?: "Not recorded"
            val share = ((mix.share(condition) ?: 0.0) * BAR_STEPS).roundToInt().coerceIn(0, BAR_STEPS)
            li("pl-bar-row") {
                srOnly("$name, $count of ${mix.total}")
                span("pl-bar-name") {
                    attributes["aria-hidden"] = "true"
                    +name
                }
                span("pl-bar") {
                    attributes["aria-hidden"] = "true"
                    span("pl-bar-fill w$share")
                }
                span("pl-bar-count") {
                    attributes["aria-hidden"] = "true"
                    +"$count"
                }
            }
        }
    }
}

/** Bars are drawn in 5 % steps (classes `w0` … `w20`): no inline styles under the CSP. */
const val BAR_STEPS = 20

/**
 * One match as in the log: result, time, the summary line, both teams seat by seat (a double pilot
 * on each of their seats, their rating change once), notes, and for a pending match "Who won?" and
 * "Resume: setup & timer". [href]: the whole head links to the match.
 */
fun FlowContent.matchCard(row: MatchRowUi, href: String?, highlighted: Boolean = false, followUps: Boolean = true) {
    div(if (highlighted) "card pl-match pl-highlight" else "card pl-match") {
        id = "match-${PlayersPaths.anchor(row.id)}"
        val head: FlowContent.() -> Unit = {
            div("pl-match-top") {
                resultChip(row.status, row.winner)
                span("pl-match-time") { +row.time }
            }
            if (row.summary.isNotEmpty()) p("pl-match-summary") { +row.summary }
        }
        if (href != null) {
            a(href, classes = "pl-match-head") { head() }
        } else {
            div("pl-match-head") { head() }
        }
        div("pl-teams") {
            Team.entries.forEach { team -> teamColumn(team, row.seats(team), row.winner) }
        }
        if (row.notes.isNotBlank()) p("pl-notes") { +"“${row.notes}”" }
        if (followUps && row.status == MatchStatus.PENDING) {
            div("actions") {
                a(PlayersPaths.result(row.id), classes = "btn btn-tonal") { +"Who won?" }
                a(PlayersPaths.timerResume(row.id), classes = "btn btn-outlined") { +"Resume: setup & timer" }
            }
        }
    }
}

private fun FlowContent.teamColumn(team: Team, seats: List<SeatUi>, winner: Team?) {
    div("pl-team") {
        h3("pl-team-head") {
            teamBadge(team)
            if (winner == team) span("pl-winner") { +" · Winner" }
        }
        ul("pl-seats") {
            seats.forEach { seat -> li("pl-seat") { seatLine(seat) } }
        }
    }
}

private fun FlowContent.seatLine(seat: SeatUi) {
    heroPortrait(seat.portrait, "sm")
    div("pl-seat-text") {
        span("pl-seat-hero") {
            span("pl-seat-hero-name") { +seat.heroName }
            delta(seat.delta, label = "rating")
        }
        span("pl-seat-pilot") {
            span("pl-seat-pilot-name") { +seat.playerName }
            if (seat.handicap) pill("H", spoken = "handicap")
            if (seat.doublePilot) pill("×2", spoken = "double pilot")
        }
    }
}
