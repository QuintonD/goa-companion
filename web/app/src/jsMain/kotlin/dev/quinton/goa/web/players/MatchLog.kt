package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.label
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.MatchNav
import dev.quinton.goa.feature.players.match.MatchRowUi
import dev.quinton.goa.feature.players.match.RematchOption
import dev.quinton.goa.feature.players.match.Rematches
import dev.quinton.goa.feature.players.match.SeatUi
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.SectionHeader
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Match log grouped by day, newest first (MatchLogContent). */
@Composable
fun MatchLogContent(state: MatchLogUiState, presenter: PlayersPresenter) {
    val services = LocalServices.current
    var rematchFor by remember { mutableStateOf<MatchRowUi?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    // After saving in the editor, bring the new or edited match into view and outline it briefly.
    LaunchedEffect(state.loading, presenter.recentlySaved) {
        val id = presenter.recentlySaved ?: return@LaunchedEffect
        if (state.groups.none { g -> g.matches.any { it.id == id } }) return@LaunchedEffect
        presenter.recentlySaved = null
        flash = id
        delay(1)
        document.getElementById(
            "match-$id",
        )?.asDynamic()?.scrollIntoView(js("({ block: 'center', behavior: 'smooth' })"))
        delay(FLASH_MS)
        flash = null
    }
    rematchFor?.let { match ->
        RematchSheet(match.rematchOptions, onPick = { kind ->
            rematchFor = null
            rematch(services, match.id, kind)
        }, onDismiss = { rematchFor = null })
    }
    when {
        state.loading -> LoadingState()

        state.groups.isEmpty() -> EmptyState(
            "g_history",
            "No matches yet",
            "Log your games to build ratings, records and the meta. Drafts add pending matches here too.",
        ) { GoaButton("Log a match", { services.router.navigate(Route.MatchEdit()) }, icon = "g_add") }

        else -> Div({ classes("goa-w2-fab-clear") }) {
            state.groups.forEach { group ->
                SectionHeader(group.header)
                Div({ classes("goa-w2-match-list") }) {
                    group.matches.forEach { match ->
                        MatchCard(
                            match,
                            presenter = presenter,
                            highlighted = flash == match.id,
                            onRematch = { rematchFor = match }.takeIf { match.rematchOptions.isNotEmpty() },
                        )
                    }
                }
            }
        }
    }
}

private const val FLASH_MS = 2_000L

/**
 * A rematch from the log or a saved result, as the phone's `MatchLogViewModel.rematch`: "Draft again"
 * fills the draft setup with the same players and teams and opens the Draft tab; the other kinds
 * continue in Match setup on the Timer tab (the rematch is saved only when its timer starts).
 */
fun rematch(services: AppServices, matchId: String, kind: RematchKind?) {
    services.data.scope.launch {
        val source = services.data.group.matches.observeMatch(matchId).first() ?: return@launch
        if (kind == null) {
            Rematches.redraft(source, services.data.table.draftSetupPrefill)
            services.router.navigate(Route.Draft)
        } else {
            when (val nav = Rematches.start(source, kind)) {
                is MatchNav.OpenRematch -> services.router.navigate(
                    Route.MatchSetup(rematchOf = nav.sourceId, rematchKind = nav.kind.name),
                )

                else -> Unit
            }
        }
    }
}

/**
 * One match in a list (MatchCard). Without [presenter] (a player's page) there is no delete.
 * Completed matches offer [onRematch]; pending ones "Who won?" and "Resume" on the card.
 */
@Composable
fun MatchCard(
    match: MatchRowUi,
    presenter: PlayersPresenter?,
    highlighted: Boolean = false,
    onRematch: (() -> Unit)? = null,
) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val summary = listOfNotNull(
        match.mode.label(),
        match.winCondition?.label(),
        match.durationMin?.let { "$it min" },
        // Older matches stored "Single lane"/"Double lane" as the map: don't repeat the mode.
        match.map?.takeIf { it.isNotBlank() && !it.equals(match.mode.label(), ignoreCase = true) },
    ).joinToString(" · ")
    Div({
        id("match-${match.id}")
        classes("goa-card", "goa-w2-match")
        if (highlighted) classes("highlighted")
    }) {
        Div({ classes("goa-w2-match-head") }) {
            ResultChip(match.status, match.winner)
            Span({ classes("t-label-medium", "c-variant", "goa-w2-grow") }) { Text(match.time) }
            IconButton("more", "More options", onClick = { menu = true })
        }
        dev.quinton.goa.web.ui.RouteLink(Route.MatchEdit(match.id), {
            classes("goa-w2-match-body")
            attr("aria-label", "Edit match: ${describe(match)}")
        }) {
            if (summary.isNotEmpty()) P({ classes("t-body-medium", "c-variant", "goa-w2-tight") }) { Text(summary) }
            Div({ classes("goa-w2-teams") }) {
                TeamColumn(Team.RED, match.red, match.winner)
                TeamColumn(Team.BLUE, match.blue, match.winner)
            }
            if (match.notes.isNotBlank()) {
                P({ classes("t-body-small", "c-variant", "goa-w2-notes") }) { Text("“${match.notes}”") }
            }
        }
        if (match.status == MatchStatus.PENDING) {
            Div({ classes("goa-button-row", "goa-w2-start") }) {
                GoaButton("Who won?", {
                    services.router.navigate(Route.MatchEdit(match.id, result = true))
                }, ButtonKind.TONAL, "g_trophy")
                GoaButton("Resume: setup & timer", {
                    // A pending match continues in Match setup, then the timer (MatchNav.OpenSetup).
                    services.router.navigate(Route.MatchSetup(matchId = match.id))
                }, ButtonKind.OUTLINED, "g_play")
            }
        }
    }
    if (menu) {
        BottomSheet("Match", onDismiss = { menu = false }) {
            RowList {
                ListRow("Edit", leadingIcon = "g_edit", onClick = {
                    menu = false
                    services.router.navigate(Route.MatchEdit(match.id))
                })
                onRematch?.let { open ->
                    ListRow("Rematch", leadingIcon = "g_replay", onClick = {
                        menu = false
                        open()
                    })
                }
                if (presenter != null) {
                    ListRow("Delete", leadingIcon = "g_delete", onClick = {
                        menu = false
                        scope.launch { deleteWithUndo(services, presenter, match.id) }
                    })
                }
            }
        }
    }
}

/** Deletes the match and offers "Undo" ("Match deleted. Ratings updated."). */
suspend fun deleteWithUndo(services: AppServices, presenter: PlayersPresenter, matchId: String) {
    val match = services.data.group.matches.observeMatch(matchId).first() ?: return
    presenter.delete(match)
    services.messages.show("Match deleted. Ratings updated.", "Undo") {
        services.data.scope.launch { presenter.undoDelete() }
    }
}

private fun describe(match: MatchRowUi): String {
    fun side(seats: List<SeatUi>) = seats.joinToString { "${it.heroName} (${it.playerName})" }
    return "${match.time}, Red: ${side(match.red)}; Blue: ${side(match.blue)}"
}

@Composable
private fun TeamColumn(team: Team, seats: List<SeatUi>, winner: Team?) {
    Div({ classes("goa-w2-team-col") }) {
        Div({ classes("goa-w2-team-head") }) {
            TeamBadge(team)
            if (winner == team) Span({ classes("t-label-medium", "c-variant") }) { Text(" · Winner") }
        }
        seats.forEach { SeatLine(it) }
    }
}

@Composable
private fun SeatLine(seat: SeatUi) {
    val description = buildString {
        append("${seat.heroName}, piloted by ${seat.playerName}")
        if (seat.handicap) append(", handicap")
        if (seat.doublePilot) append(", double pilot")
        seat.delta?.let { append(", rating ${formatDelta(it)}") }
    }
    Div({
        classes("goa-w2-seat")
        attr("aria-label", description)
    }) {
        HeroThumb(seat.heroName, seat.portrait, 32)
        Div({
            classes("goa-w2-seat-text")
            attr("aria-hidden", "true")
        }) {
            Div({ classes("goa-w2-seat-hero") }) {
                Span({ classes("t-body-medium", "goa-w2-ellipsis", "goa-w2-grow") }) { Text(seat.heroName) }
                DeltaIndicator(seat.delta, label = "rating")
            }
            Div({ classes("goa-w2-seat-pilot") }) {
                Span({ classes("t-label-medium", "c-variant", "goa-w2-ellipsis") }) { Text(seat.playerName) }
                if (seat.handicap) Pill("H")
                if (seat.doublePilot) Pill("×2")
            }
        }
    }
}

/** "Rematch": the same group again (RematchSheet). */
@Composable
fun RematchSheet(options: List<RematchOption>, onPick: (RematchKind?) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Rematch", onDismiss = onDismiss) {
        P({ classes("t-body-medium", "c-variant", "goa-w2-sheet-note") }) {
            Text("Same group again. Match setup opens with these seats; flip the Tie Breaker coin there.")
        }
        RowList {
            options.forEach { option ->
                val unavailable = option.unavailable
                if (unavailable == null) {
                    ListRow(option.title, subtitle = option.body, leadingIcon = "g_replay", onClick = {
                        onPick(option.kind)
                    })
                } else {
                    ListRow(option.title, subtitle = unavailable, leadingIcon = "g_replay")
                }
            }
        }
    }
}
