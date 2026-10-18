package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.BoardUiState
import dev.quinton.goa.core.presentation.draft.DraftBoard
import dev.quinton.goa.core.presentation.draft.PoolTile
import dev.quinton.goa.core.presentation.draft.PoolView
import dev.quinton.goa.core.presentation.draft.TeamView
import dev.quinton.goa.core.presentation.draft.TileState
import dev.quinton.goa.core.presentation.draft.heroLabel
import dev.quinton.goa.core.presentation.draft.info
import dev.quinton.goa.core.presentation.draft.label
import dev.quinton.goa.core.presentation.draft.teamGlyph
import dev.quinton.goa.core.presentation.draft.teamLabel
import dev.quinton.goa.core.presentation.draft.teamName
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.dialogButton
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.ul

private const val BOARD = DraftFeature.BOARD

/**
 * The pass-and-play board. A choice still hidden from the other team is never written into the
 * page: its tile looks free, its seat says "Hidden pick", and nothing else names it ([DraftBoard]).
 */
internal fun FlowContent.boardPage(state: BoardUiState, web: DraftBoardWeb, now: Long) {
    val board = state.board
    val players = state.playerDraft
    if (board == null && players == null) {
        emptyState(
            "No draft in progress",
            "Set up players, teams and a method to start one.",
            "Set up a draft",
            DraftFeature.PATH,
        )
        return
    }
    div("board") {
        attributes["data-board"] = ""
        toolbar(state)
        message(state)
        when {
            board != null && state.privacyGate -> privacyGate(board)
            board != null && board.isComplete -> completion(state, board)
            board != null -> heroBoard(state, board, web, now)
            players != null -> playerDraft(state, players)
        }
    }
    hubDialog("discard-board", "Discard this draft?") {
        p { +"Bans and picks so far are thrown away. Nothing is saved to the match log." }
        postForm(BOARD, "actions end") {
            button(type = ButtonType.button, classes = "btn btn-text") {
                attributes["data-dialog-close"] = ""
                +"Keep drafting"
            }
            opButton("discard", "Discard", "btn btn-danger")
        }
    }
}

private fun FlowContent.toolbar(state: BoardUiState) {
    div("board-toolbar") {
        postForm(BOARD) {
            opButton("undo", "Undo", "btn btn-outlined", enabled = state.canUndo && !state.privacyGate) {
                attributes["aria-label"] = "Undo last step"
                span("undo-glyph") {
                    attributes["aria-hidden"] = "true"
                    +"↶"
                }
            }
        }
        dialogButton("Discard", "discard-board", ButtonStyle.TEXT, Icons.DELETE)
    }
}

/** The last message (the phone's snackbar), with Redo after an undo. Never names a hidden choice. */
private fun FlowContent.message(state: BoardUiState) {
    val text = state.local.message ?: return
    postForm(BOARD, "board-message") {
        attributes["role"] = "status"
        p { +text }
        if (state.local.redo != null) opButton("redo", "Redo", "btn btn-tonal")
        opButton("dismiss", null, "icon-button") {
            attributes["aria-label"] = "Dismiss"
            icon(Icons.CLOSE)
        }
    }
}

// ---------------------------------------------------------------- Gate

private fun FlowContent.privacyGate(session: DraftSession) {
    val step = session.currentStep ?: return
    val text = DraftBoard.privacyText(session) ?: return
    section("gate ${teamCss(step.team)}") {
        span("gate-lock") {
            attributes["aria-hidden"] = "true"
            +"🔒"
        }
        h2 { +text.title }
        p { +text.detail }
        postForm(BOARD) {
            opButton(
                "reveal:${session.currentStepIndex}",
                "I'm ${teamName(step.team)} — show the board",
                "btn btn-team ${teamCss(step.team)}",
            )
        }
    }
}

// ---------------------------------------------------------------- Hero board

private fun FlowContent.heroBoard(state: BoardUiState, session: DraftSession, web: DraftBoardWeb, now: Long) {
    val step = session.currentStep ?: return
    val banner = DraftBoard.stepBanner(session, state::playerName) ?: return
    div("step-banner ${teamCss(step.team)}") {
        attributes["role"] = "status"
        div("step-text") {
            h2 { +banner.title }
            p { +banner.detail }
        }
        step.seconds?.let { seconds ->
            val started = web.stepStartedAt.takeIf {
                web.stepDraft == session.id &&
                    web.stepIndex == session.currentStepIndex
            }
            val elapsed = started?.let { ((now - it) / MILLIS).toInt() } ?: 0
            span("step-clock") {
                attributes["data-remaining"] = (seconds - elapsed).toString()
                +clock(seconds - elapsed)
            }
        }
    }
    sequenceStrip(DraftBoard.sequenceTokens(session), session.currentStepIndex)
    confirmPanel(state, session)
    if (session.canEditPool) {
        postForm(BOARD, "pool-edit") {
            div {
                p("pool-edit-title") { +"Pool of ${session.pool.size} dealt" }
                p("hint") { +"Before the first step you can re-roll it, or choose a hero and swap it out." }
            }
            opButton("reroll", "Re-roll", "btn btn-outlined")
        }
    }
    teamsPanel(DraftBoard.teams(session, state.heroes), state)
    pool(state, session)
}

private fun clock(remaining: Int): String {
    val over = remaining < 0
    val s = kotlin.math.abs(remaining)
    return (if (over) "+" else "") + "%d:%02d".format(s / SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE)
}

private fun FlowContent.confirmPanel(state: BoardUiState, session: DraftSession) {
    val heroId = state.local.confirm ?: return
    if (state.local.confirmStep != session.currentStepIndex) return
    val step = session.currentStep ?: return
    val hero = state.heroes.info(heroId)
    val ban = step.action == DraftAction.BAN
    section("confirm ${teamCss(step.team)}") {
        id = "confirm"
        attributes["aria-labelledby"] = "confirm-title"
        div("confirm-head") {
            portrait(hero, "hero-art large")
            div {
                h2 {
                    id = "confirm-title"
                    +(if (ban) "Ban ${hero.name}?" else "Pick ${hero.name}?")
                }
                if (hero.title.isNotEmpty()) p("hint") { +hero.title }
                complexity(hero.complexity)
                p { +DraftBoard.confirmText(session, state::playerName).orEmpty() }
            }
        }
        details("peek-details") {
            summary { +"Stats and starting cards" }
            heroPeek(hero)
        }
        postForm(BOARD, "actions") {
            opButton(
                "confirm",
                "${teamGlyph(step.team)} ${if (ban) "Ban" else "Pick"}",
                "btn btn-team ${teamCss(step.team)} grow",
            ) {
                attributes["autofocus"] = ""
            }
            opButton("cancel", "Cancel", "btn btn-text")
            if (session.canEditPool) opButton("swap-out:$heroId", "Swap out of the pool instead", "btn btn-text")
        }
    }
}

private fun FlowContent.teamsPanel(teams: List<TeamView>, state: BoardUiState) {
    div("teams-panel") {
        teams.forEach { team ->
            div(listOfNotNull("team-panel", teamCss(team.team), "active".takeIf { team.active }).joinToString(" ")) {
                div("team-panel-head") {
                    teamTitle(team.team)
                    span("team-slot") {
                        attributes["aria-label"] = if (team.isTeamA) "Team A" else "Team B"
                        +(if (team.isTeamA) "A" else "B")
                    }
                }
                ul("seats") {
                    team.seats.forEach { seat ->
                        li("seat") {
                            when {
                                seat.hidden -> concealedArt(team.team)
                                seat.heroId != null -> portrait(state.heroes.info(seat.heroId!!), "hero-art small")
                                else -> span("hero-art small open") { attributes["aria-hidden"] = "true" }
                            }
                            span("seat-text") {
                                span("seat-hero") {
                                    +when {
                                        seat.hidden -> "Hidden pick"
                                        seat.heroId != null -> state.heroes.info(seat.heroId!!).name
                                        else -> "Open seat"
                                    }
                                }
                                span("seat-pilot") {
                                    span("team-mark") {
                                        attributes["aria-hidden"] = "true"
                                        +teamGlyph(team.team)
                                    }
                                    +(" " + state.playerName(seat.pilotId) + if (seat.handicap) " · H" else "")
                                }
                            }
                        }
                    }
                }
                if (team.roles.isNotEmpty()) ul("roles") { team.roles.forEach { li("role") { +it.label } } }
                if (team.bans.isNotEmpty()) {
                    ul("bans") {
                        team.bans.forEach { ban ->
                            li("ban") { +(ban.heroId?.let { "⊘ ${state.heroes.info(it).name}" } ?: "⊘ Hidden ban") }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.pool(state: BoardUiState, session: DraftSession) {
    val view = PoolView.of(session)
    section("pool-section") {
        attributes["aria-label"] = "Hero pool"
        div("pool-head") {
            h3 { +"Pool · ${view.left} left" }
            div("pool-filters") {
                attributes["data-pool-filters"] = ""
                attributes["hidden"] = ""
                input(type = InputType.search, classes = "pool-search") {
                    attributes["data-pool-search"] = ""
                    attributes["aria-label"] = "Search heroes"
                    placeholder = "Search"
                }
                val roles = session.pool.flatMap { state.heroes.info(it).roles }.toSet()
                div("chip-row") {
                    Role.entries.filter { it in roles }.forEach { role ->
                        button(type = ButtonType.button, classes = "chip") {
                            attributes["data-role-filter"] = role.name
                            attributes["aria-pressed"] = "false"
                            +role.label
                        }
                    }
                }
            }
        }
        view.handOwner?.let { p("hint") { +"${state.playerName(it)}'s hand first; other hands are dimmed." } }
        postForm(BOARD, "pool-grid") {
            (view.open + view.otherHands).forEach { tile -> poolTile(state, tile) }
        }
        if (view.taken.isNotEmpty()) {
            h3("taken-title") { +"Taken" }
            ul("taken") {
                view.taken.forEach { tile ->
                    val hero = state.heroes.info(tile.heroId)
                    val (team, verb) = when (val s = tile.state) {
                        is TileState.Banned -> s.team to "banned"
                        is TileState.Picked -> s.team to "picked"
                        else -> null to ""
                    }
                    li("taken-chip ${team?.let(::teamCss).orEmpty()} $verb") {
                        span("team-mark") {
                            attributes["aria-hidden"] = "true"
                            +(team?.let(::teamGlyph) ?: "")
                        }
                        span { +" ${hero.name}" }
                        span("visually-hidden") { +", $verb by ${team?.let(::teamName).orEmpty()}" }
                    }
                }
            }
        }
    }
}

private fun FlowContent.poolTile(state: BoardUiState, tile: PoolTile) {
    val hero = state.heroes.info(tile.heroId)
    val inactive = tile.state == TileState.Inactive
    val op = if (inactive) "unavailable:${tile.heroId}" else "choose:${tile.heroId}"
    opButton(op, null, if (inactive) "hero-tile inactive" else "hero-tile") {
        attributes["data-name"] = hero.name.lowercase()
        attributes["data-roles"] = hero.roles.joinToString(" ") { it.name }
        if (inactive) attributes["aria-description"] = "dealt to another player"
        portrait(hero)
        span("tile-name") { +hero.name }
    }
}

// ---------------------------------------------------------------- Completion

private fun FlowContent.completion(state: BoardUiState, session: DraftSession) {
    val assignments = DraftEngine.assignments(session)
    section("complete") {
        div("complete-head") {
            span("complete-mark") {
                attributes["aria-hidden"] = "true"
                +"✓"
            }
            h2 { +"Draft complete" }
            p("hint") { +DraftBoard.completionHint(session) }
        }
        postForm(BOARD, "stack") {
            id = "seats"
            for (team in listOf(session.teamA, session.teamB)) {
                card("team-result ${teamCss(team)}") {
                    div("team-panel-head") {
                        teamTitle(team)
                        span("hint") { +(if (team == session.teamA) "Team A · Tie Breaker" else "Team B") }
                    }
                    assignments.filter { it.seat.team == team }.forEach { (seat, heroId) ->
                        val hero = heroId?.let { state.heroes.info(it) }
                        val selected = state.local.selectedSeat == seat.id
                        val pilot = state.players[seat.pilotId]
                        div(if (selected) "seat-row selected" else "seat-row") {
                            opButton("seat:${seat.id}", null, "seat-button", enabled = session.canSwapSeats) {
                                attributes["aria-pressed"] = selected.toString()
                                attributes["aria-label"] =
                                    "${hero?.name ?: "Open seat"}, played by ${state.playerName(seat.pilotId)}" +
                                    (if (seat.handicap) ", Handicap card" else "") +
                                    (if (selected) ". Selected: choose another hero on this team to swap" else "")
                                portrait(hero, "hero-art medium")
                                span("seat-text") {
                                    span("seat-hero") { +(hero?.name ?: "Open seat") }
                                    if (hero != null && hero.title.isNotEmpty()) span("hint") { +hero.title }
                                    span("seat-pilot") {
                                        span("team-mark") {
                                            attributes["aria-hidden"] = "true"
                                            +teamGlyph(seat.team)
                                        }
                                        playerAvatar(pilot, "sm")
                                        span { +state.playerName(seat.pilotId) }
                                    }
                                }
                            }
                            opChip("handicap:${seat.id}", "Handicap", seat.handicap)
                        }
                    }
                }
            }
        }
        if (session.bans.isNotEmpty()) {
            p("hint") { +("Banned: " + session.bans.joinToString(", ") { state.heroes.info(it.heroId).name }) }
        }
        postForm(BOARD, "stack finish") {
            opButton("finish:timer", "Set up match", "btn btn-primary wide")
            opButton("finish:result", "Who won?", "btn btn-tonal wide")
            opButton("finish:later", "Save and decide later", "btn btn-outlined wide")
            if (session.canEditPool) opButton("reroll", "Deal again", "btn btn-outlined wide")
            p("hint center") { +"Each saves a pending match to the log with these seats." }
        }
    }
}

// ---------------------------------------------------------------- Player Draft

private fun FlowContent.playerDraft(state: BoardUiState, session: PlayerDraftSession) {
    val team = session.captains.nextTeam
    val banner = DraftBoard.playerDraftBanner(session, state::playerName)
    div("step-banner ${team?.let(::teamCss) ?: "neutral"}") {
        attributes["role"] = "status"
        div("step-text") {
            h2 { +banner.title }
            if (banner.detail.isNotEmpty()) p { +banner.detail }
        }
    }
    when (session.phase) {
        PlayerDraftPhase.HEROES -> {
            heroChooser(state, session)
            postForm(BOARD, "choice-list") {
                session.selection.players.forEach { playerId ->
                    val heroId = session.selection.choices[playerId]
                    val captainOf = Team.entries.firstOrNull { session.captains.captainOf(it) == playerId }
                    div("choice player-hero") {
                        playerAvatar(state.players[playerId], "md")
                        span("choice-text") {
                            span("choice-title") { +state.playerName(playerId) }
                            span("choice-subtitle ${captainOf?.let(::teamCss).orEmpty()}") {
                                +(captainOf?.let { "${teamLabel(it)} captain" } ?: "Player")
                            }
                        }
                        if (heroId != null) {
                            portrait(state.heroes.info(heroId), "hero-art small")
                            span("chosen") { +state.heroes.info(heroId).name }
                            if (!session.selection.auto) opButton("clear-hero:$playerId", "Change", "btn btn-text")
                        } else {
                            opButton("choose-for:$playerId", "Choose hero", "btn btn-primary")
                        }
                    }
                }
            }
        }

        PlayerDraftPhase.CAPTAINS, PlayerDraftPhase.DONE -> {
            div("team-columns") {
                for (side in listOf(session.teamA, session.teamA.opponent)) {
                    val members = if (side == Team.RED) session.captains.red else session.captains.blue
                    div(
                        listOfNotNull(
                            "team-column",
                            teamCss(side),
                            "active".takeIf {
                                side == team
                            },
                        ).joinToString(" "),
                    ) {
                        teamTitle(side, if (side == session.teamA) " · A" else " · B")
                        ul("plain") {
                            members.forEach { id ->
                                li("member") {
                                    playerAvatar(state.players[id], "sm")
                                    span {
                                        +state.playerName(id)
                                        span("hint") { +" · ${heroLabel(state.heroes, session.selection.choices[id])}" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            h3 { +"Still to draft" }
            div("choice-list") {
                session.captains.remaining.forEach { playerId ->
                    val heroId = session.selection.choices[playerId]
                    val dialog = "draft-$playerId"
                    button(type = ButtonType.button, classes = "choice") {
                        attributes["data-dialog-open"] = dialog
                        attributes["aria-label"] =
                            "${state.playerName(playerId)}, plays ${heroLabel(state.heroes, heroId)}"
                        playerAvatar(state.players[playerId], "md")
                        span("choice-text") {
                            span("choice-title") { +state.playerName(playerId) }
                            span("choice-subtitle") { +heroLabel(state.heroes, heroId) }
                        }
                        portrait(heroId?.let { state.heroes.info(it) }, "hero-art small")
                    }
                    if (team != null) {
                        hubDialog(dialog, "Draft ${state.playerName(playerId)} to ${teamLabel(team)}?") {
                            p { +"They play ${heroLabel(state.heroes, heroId)}." }
                            postForm(BOARD, "actions end") {
                                button(type = ButtonType.button, classes = "btn btn-text") {
                                    attributes["data-dialog-close"] = ""
                                    +"Cancel"
                                }
                                opButton(
                                    "draft-player:$playerId",
                                    "${teamGlyph(team)} Draft",
                                    "btn btn-team ${teamCss(team)}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Choosing a hero for one player (the phone's bottom sheet), shown above the list. */
private fun FlowContent.heroChooser(state: BoardUiState, session: PlayerDraftSession) {
    val playerId = state.local.choosingFor ?: return
    if (playerId !in session.selection.players) return
    val available = session.selection.available(playerId).toSet()
    val shown = session.selection.hands[playerId] ?: session.selection.pool
    section("chooser") {
        id = "chooser"
        div("card-head") {
            h3 { +"Hero for ${state.playerName(playerId)}" }
            postForm(BOARD) { opButton("choose-for:", "Cancel", "btn btn-text") }
        }
        postForm(BOARD, "pool-grid") {
            shown.forEach { heroId ->
                val hero = state.heroes.info(heroId)
                val free = heroId in available
                opButton("assign:$heroId", null, if (free) "hero-tile" else "hero-tile inactive", enabled = free) {
                    portrait(hero)
                    span("tile-name") { +hero.name }
                }
            }
        }
    }
}

private const val MILLIS = 1_000L
private const val SECONDS_PER_MINUTE = 60
