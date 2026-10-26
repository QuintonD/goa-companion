package dev.quinton.goa.web.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.SeatAssignment
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.board.BoardUiState
import dev.quinton.goa.feature.draft.board.DraftBoardText
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.heroLabel
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.HeroArt
import dev.quinton.goa.web.table.TableFooter
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.PlayerAvatar
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

// ---------------------------------------------------------------- Completion

/**
 * Finished draft: arrange heroes on seats and pilots, then save and hand off to match setup. The
 * actions (Set up match, Log result later, Try on the board) stay pinned at the bottom.
 */
@Composable
internal fun CompletionContent(state: BoardUiState, session: DraftSession, model: DraftBoardModel) {
    val assignments = DraftEngine.assignments(session)
    Div({ classes("goa-table-stack", "goa-draft-completion", "goa-table-has-footer") }) {
        Div({ classes("goa-draft-complete-head") }) {
            Span({ classes("goa-table-medallion", "teal") }) { Icon("check") }
            H2({ classes("t-headline-small", "goa-table-tight") }) { Text("Draft complete") }
            P({
                classes("t-body-medium", "c-variant", "goa-table-center")
            }) { Text(DraftBoardText.completionHint(session)) }
        }
        for (team in listOf(session.teamA, session.teamB)) {
            key(team) { TeamResult(state, session, team, assignments.filter { it.seat.team == team }, model) }
        }
        if (session.bans.isNotEmpty()) {
            P({
                classes("t-body-small", "c-variant")
            }) { Text("Banned: " + session.bans.joinToString(", ") { state.heroes.info(it.heroId).name }) }
        }
    }
    CompletionActions(state, session, model)
}

/** Set up match and Log result later (both save a pending match), Try on the board, and Deal again when allowed. */
@Composable
private fun CompletionActions(state: BoardUiState, session: DraftSession, model: DraftBoardModel) {
    val router = LocalServices.current.router
    TableFooter("goa-draft-completion-actions") {
        GoaButton("Set up match", {
            model.finish(openMatchSetup = true)
        }, icon = "timer", enabled = !state.local.saving, block = true)
        Div({ classes("goa-table-footer-row") }) {
            GoaButton("Log result later", {
                model.finish(openMatchSetup = false)
            }, ButtonKind.OUTLINED, icon = "event_note", enabled = !state.local.saving)
            GoaButton("Try on the board", {
                // Pick order per team: the board's opening places heroes in that order (p.20).
                router.navigate(
                    Route.OpenBoard(DraftBoardText.lineUp(session, Team.RED), DraftBoardText.lineUp(session, Team.BLUE)),
                )
            }, ButtonKind.OUTLINED, icon = "hexagon")
        }
        if (session.canEditPool) {
            GoaButton(
                "Deal again",
                model::reroll,
                ButtonKind.OUTLINED,
                icon = "dice",
                block = true,
            )
        }
        P({
            classes("t-body-small", "c-variant", "goa-table-center")
        }) { Text("Set up match and Log result later save a pending match with these seats.") }
    }
}

@Composable
private fun TeamResult(
    state: BoardUiState,
    session: DraftSession,
    team: Team,
    seats: List<SeatAssignment>,
    model: DraftBoardModel,
) {
    Div({ classes("goa-card", "goa-draft-result", teamClass(team)) }) {
        Div({ classes("goa-table-heading-row") }) {
            H2({
                classes("t-title-large", "goa-table-tight", "goa-table-team-text", teamClass(team))
            }) { Text(teamLabel(team)) }
            Span({ classes("t-label-medium", "c-variant") }) {
                Text(
                    if (team ==
                        session.teamA
                    ) {
                        "Team A · Tie Breaker"
                    } else {
                        "Team B"
                    },
                )
            }
        }
        seats.forEach { assignment ->
            key(assignment.seat.id) { SeatRow(state, assignment, session.canSwapSeats, model) }
        }
    }
}

@Composable
private fun SeatRow(state: BoardUiState, assignment: SeatAssignment, swappable: Boolean, model: DraftBoardModel) {
    val seat = assignment.seat
    val hero = assignment.heroId?.let { state.heroes.info(it) }
    val selected = state.local.selectedSeat == seat.id
    val pilot = state.players[seat.pilotId]
    Div({
        classes("goa-draft-seat-row")
        if (selected) classes("selected")
    }) {
        Button({
            type(ButtonType.Button)
            classes("goa-draft-seat-main")
            attr(
                "aria-label",
                "${hero?.name ?: "Open seat"}, played by ${state.playerName(seat.pilotId)}" +
                    (if (seat.handicap) ", Handicap card" else "") +
                    if (selected) ". Selected — tap another hero on this team to swap" else "",
            )
            // Locked seats explain why (Player Draft, dealt hands) instead of doing nothing.
            onClick { model.selectSeat(seat.id) }
            if (!swappable) classes("locked")
        }) {
            if (hero !=
                null
            ) {
                HeroArt(hero.id, hero.portrait, hero.name, extraClass = "goa-draft-result-art")
            } else {
                Span({ classes("goa-draft-result-art") })
            }
            Span({ classes("goa-table-row-text") }) {
                Span({ classes("t-title-medium") }) { Text(hero?.name ?: "Open seat") }
                if (hero != null &&
                    hero.title.isNotEmpty()
                ) {
                    Span({ classes("t-body-small", "c-variant") }) { Text(hero.title) }
                }
                Span({ classes("goa-table-inline") }) {
                    Span({
                        classes("goa-table-team-text", teamClass(seat.team))
                        attr("aria-hidden", "true")
                    }) { Text(teamGlyph(seat.team)) }
                    PlayerAvatar(pilot, size = 24)
                    Span({ classes("t-label-large") }) { Text(state.playerName(seat.pilotId)) }
                }
            }
        }
        Chip("Handicap", seat.handicap, onClick = { model.toggleHandicap(seat.id) })
    }
}

// ---------------------------------------------------------------- Player Draft

/** The official Player Draft: heroes for every player first, then captains draft players. */
@Composable
internal fun PlayerDraftContent(
    state: BoardUiState,
    session: PlayerDraftSession,
    model: DraftBoardModel,
    onPeek: (String) -> Unit,
) {
    var drafting by remember { mutableStateOf<String?>(null) }
    Div({ classes("goa-table-stack", "goa-draft-player-draft") }) {
        PhaseBanner(state, session)
        when (session.phase) {
            PlayerDraftPhase.HEROES -> session.selection.players.forEach { playerId ->
                key(playerId) { HeroChoiceRow(state, session, playerId, model) }
            }

            PlayerDraftPhase.CAPTAINS, PlayerDraftPhase.DONE -> {
                CaptainTeams(state, session)
                H2({ classes("t-title-medium", "goa-table-tight") }) { Text("Still to draft") }
                session.captains.remaining.forEach { playerId ->
                    key(playerId) { PlayerCard(state, session, playerId) { drafting = playerId } }
                }
            }
        }
    }
    val pending = drafting
    val team = session.captains.nextTeam
    if (pending != null && team != null) {
        AlertDialog(
            "Draft ${state.playerName(pending)} to ${teamLabel(team)}?",
            onDismiss = { drafting = null },
            buttons = {
                GoaButton("Cancel", { drafting = null }, ButtonKind.TEXT)
                Button({
                    type(ButtonType.Button)
                    classes("goa-button", "goa-draft-team-button", teamClass(team))
                    onClick {
                        drafting = null
                        model.draftPlayer(pending)
                    }
                }) { Text("${teamGlyph(team)} Draft") }
            },
        ) { Text("They play ${heroLabel(state.heroes, session.selection.choices[pending])}.") }
    }
    state.local.choosingFor?.let { playerId -> HeroChooser(state, session, playerId, model, onPeek) }
}

@Composable
private fun PhaseBanner(state: BoardUiState, session: PlayerDraftSession) {
    val team = session.captains.nextTeam
    val (title, body) = DraftBoardText.playerDraftBanner(state, session)
    Div({
        classes("goa-draft-banner", "tall")
        classes(team?.let { teamClass(it) } ?: "neutral")
        attr("aria-live", "polite")
    }) {
        Div({ classes("goa-table-row-text") }) {
            P({ classes("goa-draft-banner-title", "goa-table-tight") }) { Text(title) }
            if (body.isNotEmpty()) P({ classes("t-body-medium", "c-variant", "goa-table-tight") }) { Text(body) }
        }
    }
}

@Composable
private fun HeroChoiceRow(state: BoardUiState, session: PlayerDraftSession, playerId: String, model: DraftBoardModel) {
    val heroId = session.selection.choices[playerId]
    val captainOf = Team.entries.firstOrNull { session.captains.captainOf(it) == playerId }
    Div({ classes("goa-card", "goa-draft-choice-row") }) {
        PlayerAvatar(state.players[playerId])
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-medium") }) { Text(state.playerName(playerId)) }
            Span({
                classes("t-label-medium")
                if (captainOf != null) classes("goa-table-team-text", teamClass(captainOf)) else classes("c-variant")
            }) { Text(captainOf?.let { "${teamLabel(it)} captain" } ?: "Player") }
        }
        if (heroId != null) {
            Div({ classes("goa-draft-choice-hero") }) {
                Span({ classes("t-label-large") }) { Text(state.heroes.info(heroId).name) }
                if (!session.selection.auto) GoaButton("Change", { model.clearHero(playerId) }, ButtonKind.TEXT)
            }
            HeroArt(
                heroId,
                state.heroes.info(heroId).portrait,
                state.heroes.info(heroId).name,
                extraClass = "goa-draft-choice-art",
            )
        } else {
            Button({
                type(ButtonType.Button)
                classes("goa-button", "filled")
                if (session.selection.auto) disabled()
                onClick { model.chooseHeroFor(playerId) }
            }) { Text("Choose hero") }
        }
    }
}

@Composable
private fun CaptainTeams(state: BoardUiState, session: PlayerDraftSession) {
    Div({ classes("goa-draft-teams") }) {
        for (team in listOf(session.teamA, session.teamA.opponent)) {
            val members = if (team == Team.RED) session.captains.red else session.captains.blue
            Div({
                classes("goa-draft-team", teamClass(team))
                if (session.captains.nextTeam == team) classes("active")
            }) {
                Span({ classes("t-title-small", "goa-table-team-text", teamClass(team)) }) {
                    Text(teamLabel(team) + if (team == session.teamA) " · A" else " · B")
                }
                members.forEach { id ->
                    Div({ classes("goa-draft-seat-line") }) {
                        PlayerAvatar(state.players[id], size = 28)
                        Div({ classes("goa-table-row-text") }) {
                            Span({ classes("t-body-medium", "goa-table-ellipsis") }) { Text(state.playerName(id)) }
                            Span({ classes("t-label-medium", "c-variant", "goa-table-ellipsis") }) {
                                Text(heroLabel(state.heroes, session.selection.choices[id]))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(state: BoardUiState, session: PlayerDraftSession, playerId: String, onClick: () -> Unit) {
    val heroId = session.selection.choices[playerId]
    Button({
        type(ButtonType.Button)
        classes("goa-card", "goa-draft-choice-row", "clickable")
        attr("aria-label", "${state.playerName(playerId)}, plays ${heroLabel(state.heroes, heroId)}")
        onClick { onClick() }
    }) {
        PlayerAvatar(state.players[playerId])
        Span({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-medium") }) { Text(state.playerName(playerId)) }
            Span({ classes("t-body-small", "c-variant") }) { Text(heroLabel(state.heroes, heroId)) }
        }
        if (heroId !=
            null
        ) {
            HeroArt(
                heroId,
                state.heroes.info(heroId).portrait,
                state.heroes.info(heroId).name,
                extraClass = "goa-draft-choice-art",
            )
        }
        Icon("chevron_right", IconSize.SMALL)
    }
}

@Composable
private fun HeroChooser(
    state: BoardUiState,
    session: PlayerDraftSession,
    playerId: String,
    model: DraftBoardModel,
    onPeek: (String) -> Unit,
) {
    val available = session.selection.available(playerId).toSet()
    val shown = session.selection.hands[playerId] ?: session.selection.pool
    BottomSheet("Hero for ${state.playerName(playerId)}", onDismiss = { model.chooseHeroFor(null) }) {
        Div({ classes("goa-draft-grid", "wide") }) {
            shown.forEach { heroId ->
                val free = heroId in available
                key(heroId) {
                    HeroTile(
                        hero = state.heroes.info(heroId),
                        state = if (free) TileState.Available else TileState.Inactive,
                        onClick = if (free) ({ model.assignHero(heroId) }) else null,
                        onPeek = { onPeek(heroId) },
                    )
                }
            }
        }
    }
}
