package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.SetupStage
import dev.quinton.goa.core.presentation.draft.SetupUiState
import dev.quinton.goa.core.presentation.draft.TeamMode
import dev.quinton.goa.core.presentation.draft.originLabel
import dev.quinton.goa.core.presentation.draft.plural
import dev.quinton.goa.core.presentation.draft.ratingLabel
import dev.quinton.goa.core.presentation.draft.teamGlyph
import dev.quinton.goa.core.presentation.draft.teamLabel
import dev.quinton.goa.core.presentation.draft.teamName
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.dialogButton
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import kotlin.math.roundToInt
import kotlinx.html.ButtonType
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

private const val SETUP = "${DraftFeature.PATH}/setup"

/** The draft setup wizard (players, teams, heroes, coin), as on the phone. */
internal fun FlowContent.setupPage(state: SetupUiState, notes: DraftNotes?) {
    notes?.message?.let { errorState(it) }
    notes?.savedMatchId?.let { matchId ->
        div("notice saved") {
            attributes["role"] = "status"
            icon(Icons.CHECK)
            p { +"Saved as a pending match. Log the result under Players › Matches." }
            div("actions") {
                linkButton("Who won?", DraftFeature.result(matchId), ButtonStyle.TONAL)
                linkButton("View matches", DraftFeature.MATCH_LOG, ButtonStyle.TEXT)
            }
        }
    }
    state.resume?.let { resume ->
        card("resume") {
            h2 { +(if (resume.complete) "Draft complete" else "Draft in progress") }
            p { +"${resume.title} · ${resume.detail}" }
            div("actions") {
                linkButton(if (resume.complete) "Open" else "Resume", DraftFeature.BOARD, ButtonStyle.PRIMARY)
                dialogButton("Discard", "discard-draft", ButtonStyle.TEXT)
            }
        }
        hubDialog("discard-draft", "Discard this draft?") {
            p {
                +(
                    if (resume.complete) {
                        "The finished draft is thrown away. Nothing is saved to the match log."
                    } else {
                        "Bans and picks so far are thrown away. Nothing is saved to the match log."
                    }
                    )
            }
            postForm(SETUP, "actions end") {
                button(type = ButtonType.button, classes = "btn btn-text") {
                    attributes["data-dialog-close"] = ""
                    +(if (resume.complete) "Keep it" else "Keep drafting")
                }
                opButton("discard", "Discard", "btn btn-danger")
            }
        }
    }
    stageIndicator(state.stage)
    if (state.stage == SetupStage.PLAYERS) quickAdd()
    postForm(SETUP, "stack setup-form") {
        id = "setup"
        when (state.stage) {
            SetupStage.PLAYERS -> playersStage(state)
            SetupStage.TEAMS -> teamsStage(state)
            SetupStage.HEROES -> heroesStage(state)
            SetupStage.COIN -> coinStage(state)
        }
        bottomBar(state)
    }
}

private fun FlowContent.stageIndicator(current: SetupStage) {
    postForm(SETUP) {
        nav("stages") {
            attributes["aria-label"] = "Setup steps"
            ol {
                SetupStage.entries.forEach { stage ->
                    val done = stage.ordinal < current.ordinal
                    li(
                        listOfNotNull(
                            "stage",
                            "done".takeIf {
                                done
                            },
                            "current".takeIf { stage == current },
                        ).joinToString(" "),
                    ) {
                        if (stage == current) attributes["aria-current"] = "step"
                        if (done) {
                            opButton("stage:${stage.name}", null, "stage-button") {
                                span("stage-number") { +"${stage.ordinal + 1}" }
                                span("stage-title") { +stage.shortTitle }
                            }
                        } else {
                            span("stage-button") {
                                span("stage-number") { +"${stage.ordinal + 1}" }
                                span("stage-title") { +stage.shortTitle }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.quickAdd() {
    postForm(SETUP, "quick-add") {
        id = "quick-add"
        div("field") {
            label {
                htmlFor = "f-quick-name"
                +"Quick add a player"
            }
            div("inline-field") {
                input(type = InputType.text, name = "name") {
                    id = "f-quick-name"
                    maxLength = "40"
                    attributes["autocomplete"] = "off"
                    attributes["enterkeyhint"] = "done"
                }
                opButton("quick-add", null, "btn btn-tonal icon-only") {
                    attributes["aria-label"] = "Add player"
                    icon(Icons.ADD)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Players

private fun FORM.playersStage(state: SetupUiState) {
    section("stage-body") {
        if (state.players.isEmpty()) {
            emptyState(
                "Who's playing?",
                "Add the people at the table above, or create full profiles in Players.",
                "Players",
                "/players",
            )
            return@section
        }
        div("count-row") {
            span("count") { +"${state.playerCount} selected · 2–${SetupUiState.MAX_PLAYERS} players" }
            if (state.form.lastGame && state.form.selected.isNotEmpty()) opButton("clear", "Clear", "btn btn-text")
        }
        state.lastGameNote?.let { p("note") { +it } }
        div("choice-list") {
            state.players.forEach { option ->
                val selected = option.player.id in state.form.selected
                opButton(
                    "toggle:${option.player.id}",
                    null,
                    if (selected) "choice player selected" else "choice player",
                ) {
                    id = "player-${option.player.id}"
                    attributes["aria-pressed"] = selected.toString()
                    playerAvatar(option.player, "md")
                    span("choice-text") {
                        span("choice-title") { +option.player.name }
                        span("choice-subtitle") { +ratingLabel(option.rating) }
                    }
                    span(if (selected) "tick on" else "tick") { attributes["aria-hidden"] = "true" }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Teams

private fun FORM.teamsStage(state: SetupUiState) {
    val form = state.form
    section("stage-body") {
        div("segmented") {
            attributes["role"] = "group"
            attributes["aria-label"] = "How teams are made"
            TeamMode.entries.forEach { mode ->
                val on = form.teamMode == mode
                opButton("team-mode:${mode.name}", mode.label, if (on) "segment selected" else "segment") {
                    attributes["aria-pressed"] = on.toString()
                    if (mode == TeamMode.BALANCED) attributes["aria-label"] = "Balance by Elo"
                }
            }
        }
        if (form.teamMode == TeamMode.PLAYER_DRAFT) playerDraftSetup(state) else teamColumns(state)
        if (form.teamMode != TeamMode.PLAYER_DRAFT) seatLayout(state)
    }
}

private fun FlowContent.teamColumns(state: SetupUiState) {
    val form = state.form
    val balanced = form.teamMode == TeamMode.BALANCED
    card("teams") {
        id = "teams"
        div("card-head") {
            div {
                h3 { +(if (balanced) "Balanced by Elo" else "Tap a player to switch teams") }
                val subtitle = form.balanceDifference?.let { "Average Elo differs by ${it.roundToInt()}" }
                    ?: if (balanced) "Lock players to keep them on a team, then re-balance" else null
                subtitle?.let { p("hint") { +it } }
            }
            if (balanced) opButton("balance", "Re-balance", "btn btn-tonal")
        }
        div("team-columns") {
            for (team in Team.entries) {
                val ids = if (team == Team.RED) form.red else form.blue
                val seats = state.seats.count { it.team == team }
                div("team-column ${teamCss(team)}") {
                    teamTitle(team)
                    p("hint") { +"${plural(ids.size, "player")} · ${plural(seats, "hero", "heroes")}" }
                    ids.forEach { pid ->
                        val option = state.player(pid)
                        val name = option?.player?.name ?: "Unknown"
                        val locked = pid in form.locked
                        div("member-row") {
                            opButton("move:$pid", null, "member ${teamCss(team)}", enabled = !locked) {
                                attributes["aria-label"] = "$name, ${teamName(team)} team" +
                                    if (locked) ", locked" else ", tap to move to ${teamName(team.opponent)}"
                                playerAvatar(option?.player, "sm")
                                span("member-name") { +name }
                            }
                            if (balanced) {
                                opButton("lock:$pid", null, if (locked) "icon-toggle on" else "icon-toggle") {
                                    attributes["aria-pressed"] = locked.toString()
                                    attributes["aria-label"] =
                                        if (locked) "Unlock $name" else "Lock $name to ${teamName(team)}"
                                    span("lock-glyph") {
                                        attributes["aria-hidden"] = "true"
                                        +(if (locked) "🔒" else "🔓")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.seatLayout(state: SetupUiState) {
    val form = state.form
    val n = state.playerCount
    when {
        n == 2 -> infoCard("Two players", "Each player controls two heroes (rulebook), so the draft fills 2 v 2 seats.")

        n == 3 -> {
            val solo = (if (form.red.size == 1) form.red else form.blue).firstOrNull()
            val name = solo?.let { state.player(it)?.player?.name } ?: "The solo player"
            infoCard(
                "Official 3-player setup",
                "$name controls two heroes and replaces a gold card of one of them with its Handicap card. " +
                    "Put your most experienced player alone.",
            )
        }

        SeatLayouts.offersOddChoice(n) -> card("seats") {
            id = "seats"
            h3 { +"Odd player count" }
            p("hint") { +"How should the teams be evened out?" }
            choiceCard(
                op = "odd-mode:${OddSeatMode.HANDICAP.name}",
                title = "Handicap",
                badge = "Rulebook",
                body = "Teams stay uneven. Every hero on the larger team swaps a basic card for its Handicap card.",
                selected = form.oddMode == OddSeatMode.HANDICAP,
            )
            choiceCard(
                op = "odd-mode:${OddSeatMode.DOUBLE_PILOT.name}",
                title = "Double pilot",
                badge = "House rule",
                body = "One player on the smaller team controls two heroes, so both teams draft the same " +
                    "number of heroes.",
                selected = form.oddMode == OddSeatMode.DOUBLE_PILOT,
            )
            if (form.oddMode == OddSeatMode.DOUBLE_PILOT) {
                val smaller = if (form.red.size < form.blue.size) form.red else form.blue
                p("label") { +"Who pilots two heroes?" }
                div("chip-row") {
                    smaller.forEach { pid ->
                        opChip(
                            "double-pilot:$pid",
                            state.player(pid)?.player?.name ?: "Unknown",
                            pid == state.seatedDoublePilot,
                        )
                    }
                }
            }
        }

        else -> Unit
    }
}

private fun FlowContent.playerDraftSetup(state: SetupUiState) {
    val form = state.form
    infoCard(
        "Official Player Draft",
        "The two most experienced players are Captains. First every player gets a hero with the method you " +
            "choose next. Then the Captains draft players one at a time, Team A's captain first. Everyone plays " +
            "their pre-selected hero." +
            if (state.playerCount % 2 ==
                1
            ) {
                " With an odd count, the larger team's heroes use their Handicap cards."
            } else {
                ""
            },
    )
    div("stack") {
        id = "captains"
        for (team in Team.entries) {
            card("captain ${teamCss(team)}") {
                h3("team-heading ${teamCss(team)}") { +"${teamLabel(team)} captain" }
                div("chip-row") {
                    form.selected.forEach { pid ->
                        val chosen = if (team == Team.RED) form.redCaptain == pid else form.blueCaptain == pid
                        opChip("captain:${team.name}:$pid", state.player(pid)?.player?.name ?: "Unknown", chosen)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Heroes

private fun FORM.heroesStage(state: SetupUiState) {
    val form = state.form
    section("stage-body") {
        card("methods") {
            id = "methods"
            div("card-head") {
                div {
                    h3 { +"Method" }
                    p("hint") { +"How heroes are chosen" }
                }
                a(DraftFeature.METHODS, classes = "btn btn-text") { +"Manage" }
            }
            state.methods.forEach { option ->
                val selected = option.method.id == form.methodId
                choiceCard(
                    op = "method:${option.method.id}",
                    title = option.method.name,
                    badge = originLabel(option.method.origin),
                    body = option.unavailable ?: option.method.summary,
                    selected = selected,
                    warning = option.unavailable != null,
                )
                if (selected && option.preview.isNotEmpty()) sequenceStrip(option.preview)
            }
        }
        card("pool") {
            id = "pool"
            h3 { +"Hero pool" }
            p("hint") { +"${plural(state.candidateCount, "hero", "heroes")} eligible" }
            state.poolNote?.let { p("warning-text") { +it } }
            div("segmented") {
                attributes["role"] = "group"
                attributes["aria-label"] = "Hero pool"
                PoolSource.entries.forEach { source ->
                    val on = form.poolSource == source
                    val label = when (source) {
                        PoolSource.ALL -> "All"
                        PoolSource.OWNED -> "Owned"
                        PoolSource.CUSTOM -> "Custom"
                    }
                    opButton("pool:${source.name}", label, if (on) "segment selected" else "segment") {
                        attributes["aria-pressed"] = on.toString()
                    }
                }
            }
            when (form.poolSource) {
                PoolSource.ALL -> p("hint") { +"Every hero in the catalog." }

                PoolSource.OWNED -> p("hint") {
                    +"Heroes from the products you own (${state.ownedProductsLabel}, set in Settings)."
                }

                PoolSource.CUSTOM -> a("${DraftFeature.PATH}/pool", classes = "btn btn-outlined") {
                    +"Choose heroes (${form.customSelection.size})"
                }
            }
            div("divider") {}
            p("label") { +"Least experienced player" }
            p("hint") { +"Removes heroes above their complexity (rulebook)." }
            div("chip-row") {
                opChip("complexity:", "Any", form.maxComplexity == null)
                for (stars in 1..DraftActions.MAX_STARS) {
                    opChip(
                        "complexity:$stars",
                        "${"★".repeat(stars)} ${ComplexityFilter.name(stars)}",
                        form.maxComplexity == stars,
                    ) {
                        attributes["aria-label"] = "Up to $stars stars, ${ComplexityFilter.name(stars)}"
                    }
                }
            }
        }
        card("timer") {
            id = "timer"
            div("card-head") {
                div {
                    h3 { +"Step timer" }
                    p("hint") { +"Optional countdown on every ban and pick" }
                }
                opButton(
                    "step-timer:${if (form.stepTimer) "off" else "on"}",
                    null,
                    if (form.stepTimer) "switch on" else "switch",
                ) {
                    attributes["role"] = "switch"
                    attributes["aria-checked"] = form.stepTimer.toString()
                    attributes["aria-label"] = "Step timer"
                    span("switch-thumb") { attributes["aria-hidden"] = "true" }
                }
            }
            if (form.stepTimer) {
                div("chip-row") {
                    DraftActions.STEP_SECONDS.forEach { s -> opChip("step-seconds:$s", "${s}s", form.stepSeconds == s) }
                }
            } else {
                p("hint") { +"Off — no countdown. Overtime is never enforced." }
            }
        }
    }
}

// ---------------------------------------------------------------- Coin

private fun FORM.coinStage(state: SetupUiState) {
    val form = state.form
    val teamA = form.teamA
    section("stage-body coin-stage") {
        p("lead center") { +"The team showing on the Tie Breaker coin is Team A. Team A starts the draft." }
        div("coin ${teamA?.let(::teamCss) ?: "unflipped"}${if (form.flipCount % 2 == 1) " flip-odd" else ""}") {
            attributes["aria-hidden"] = "true"
            span("coin-face") { +(teamA?.let { teamGlyph(it) } ?: "?") }
        }
        p("coin-result ${teamA?.let(::teamCss) ?: ""}") {
            attributes["role"] = "status"
            +(if (teamA == null) "Not flipped yet" else "${teamLabel(teamA)} is Team A")
        }
        div("center") { opButton("flip", if (teamA == null) "Flip the coin" else "Flip again", "btn btn-primary") }
        p("label center") { +"Flipped the real coin?" }
        div("chip-row center") {
            Team.entries.forEach { team ->
                opChip("team-a:${team.name}", "${teamLabel(team)} shows", teamA == team, teamCss(team))
            }
        }
        summaryCard(state)
    }
}

private fun FlowContent.summaryCard(state: SetupUiState) {
    val method = state.selectedMethod ?: return
    card("summary") {
        h3 { +method.method.name }
        p("hint") { +method.method.summary }
        if (state.form.teamMode == TeamMode.PLAYER_DRAFT) {
            p("hint") { +"Heroes first, then captains draft players." }
        } else {
            sequenceStrip(method.preview)
            div("team-columns") {
                for (team in Team.entries) {
                    div("team-column ${teamCss(team)}") {
                        teamTitle(team)
                        state.seats.filter { it.team == team }.forEach { seat ->
                            p {
                                +(
                                    (state.player(seat.pilotId.orEmpty())?.player?.name ?: "—") +
                                        if (seat.handicap) " · Handicap" else ""
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Shared

private fun FORM.bottomBar(state: SetupUiState) {
    div("bottom-bar") {
        state.blocker?.let {
            p("blocker") {
                attributes["role"] = "status"
                +it
            }
        }
        div("actions") {
            if (state.stage != SetupStage.PLAYERS) opButton("back", "Back", "btn btn-outlined")
            if (state.stage == SetupStage.COIN) {
                val label = if (state.form.teamMode == TeamMode.PLAYER_DRAFT) "Start Player Draft" else "Start draft"
                opButton("start", label, "btn btn-primary grow", enabled = state.blocker == null)
            } else {
                val label = "Next: ${SetupStage.entries[state.stage.ordinal + 1].title}"
                opButton("next", label, "btn btn-primary grow", enabled = state.blocker == null)
            }
        }
    }
}

internal fun FlowContent.opChip(
    op: String,
    label: String,
    selected: Boolean,
    extra: String? = null,
    block: kotlinx.html.BUTTON.() -> Unit = {},
) {
    opButton(op, null, listOfNotNull("chip", "selected".takeIf { selected }, extra).joinToString(" ")) {
        attributes["aria-pressed"] = selected.toString()
        block()
        if (selected) icon(Icons.CHECK)
        span { +label }
    }
}

@Suppress("LongParameterList")
private fun FlowContent.choiceCard(
    op: String,
    title: String,
    badge: String?,
    body: String,
    selected: Boolean,
    warning: Boolean = false,
) {
    opButton(
        op,
        null,
        listOfNotNull(
            "choice",
            "selected".takeIf {
                selected
            },
            "unavailable".takeIf { warning },
        ).joinToString(" "),
    ) {
        attributes["aria-pressed"] = selected.toString()
        span("choice-text") {
            span("choice-title") {
                +title
                badge?.let { span("badge") { +it } }
            }
            span(if (warning) "choice-subtitle warning-text" else "choice-subtitle") { +body }
        }
        span(if (selected) "radio on" else "radio") { attributes["aria-hidden"] = "true" }
    }
}

private fun FlowContent.infoCard(title: String, body: String) {
    div("info-card") {
        h3 { +title }
        p { +body }
    }
}
