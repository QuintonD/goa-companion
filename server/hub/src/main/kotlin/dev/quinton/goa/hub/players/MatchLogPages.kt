package dev.quinton.goa.hub.players

import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.presentation.players.MatchEditor
import dev.quinton.goa.core.presentation.players.MatchLog
import dev.quinton.goa.core.presentation.players.MatchLog.toRow
import dev.quinton.goa.core.presentation.players.MatchRowUi
import dev.quinton.goa.core.presentation.players.PlayerChangeUi
import dev.quinton.goa.core.presentation.players.SavedResultUi
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.teamBadge
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.span

/** How many matches the log shows at first; "Show older matches" adds as many again. */
private const val LOG_PAGE = 60

/** The match log (Players → Matches). */
internal fun Route.matchLogRoutes(ctx: HubContext) {
    get(PlayersPaths.LOG) {
        val view = PlayersData.of(ctx)
        val shown = call.request.queryParameters["show"]?.toIntOrNull()?.coerceAtLeast(LOG_PAGE) ?: LOG_PAGE
        val saved = call.request.queryParameters["saved"]
        val all = view.matches
        val groups = MatchLog.group(
            all.take(shown),
            view.byId,
            view.catalog,
            view.snapshot.ratings.matchChanges,
            view.today,
            view.zone,
        )
        val undo = ctx.deviceStore.read(ctx.device(call).id, PlayersSlots.Undo)
            ?.takeIf { ctx.clock.nowMillis() - it.deletedAt <= PlayersSlots.UNDO_WINDOW_MS }
            ?.takeIf { view.data.document.matches.none { m -> m.id == it.match.id } }
        val flash = ctx.takeFlash(call)
        call.respondPage(playersPage("Players")) {
            sectionTabs(PlayersSection.MATCHES)
            flashNotice(flash)
            if (undo != null) {
                div("notice pl-undo") {
                    attributes["role"] = "status"
                    p { +"Match deleted." }
                    postForm(PlayersPaths.UNDO) { hubButton("Undo", ButtonStyle.TEXT) }
                }
            }
            if (groups.isEmpty()) {
                emptyState(
                    "No matches yet",
                    "Log your games to build ratings, records and the meta. Drafts add pending matches here too.",
                    "Log a match",
                    PlayersPaths.NEW_MATCH,
                )
                return@respondPage
            }
            groups.forEach { group ->
                sectionHeader(group.header)
                group.matches.forEach { row ->
                    matchCard(row, PlayersPaths.match(row.id), highlighted = row.id == saved)
                }
            }
            if (all.size > shown) {
                a("${PlayersPaths.LOG}?show=${shown + LOG_PAGE}", classes = "btn btn-tonal") {
                    +"Show older matches (${all.size - shown} more)"
                }
            }
            a(PlayersPaths.NEW_MATCH, classes = "btn btn-primary pl-fab") {
                icon(Icons.ADD)
                span { +"Log match" }
            }
        }
    }
}

/** A match's page, delete with undo, Rematch, and "Result saved". */
internal fun Route.matchRoutes(ctx: HubContext) {
    get("/players/matches/{id}") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val match = view.match(id) ?: return@get call.respondMatchGone(view, id)
        val row = match.toRow(view.byId, view.catalog, view.snapshot.ratings.matchChanges[id], view.zone)
        val flash = ctx.takeFlash(call)
        call.respondPage(playersPage("Match", back = PlayersPaths.LOG)) {
            flashNotice(flash)
            p("pl-hint") { +dev.quinton.goa.core.presentation.players.Dates.day(match.playedAt, view.zone) }
            matchCard(row, href = null)
            if (match.status == MatchStatus.PENDING && match.draftId != null) {
                p("pl-hint") { +"Created from a draft. Fill in the result when the game ends." }
            }
            div("actions") {
                linkButton("Edit", PlayersPaths.editMatch(id), ButtonStyle.TONAL, Icons.EDIT)
                if (row.rematchOptions.isNotEmpty()) linkButton("Rematch", PlayersPaths.rematch(id), ButtonStyle.TONAL)
                linkButton("Delete", PlayersPaths.delete(id), ButtonStyle.DANGER, Icons.DELETE)
            }
        }
    }
    get("/players/matches/{id}/delete") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val match = view.match(id) ?: return@get call.respondMatchGone(view, id)
        val discard = match.status == MatchStatus.PENDING
        val row = match.toRow(view.byId, view.catalog, view.snapshot.ratings.matchChanges[id], view.zone)
        call.respondPage(playersPage(if (discard) "Discard match" else "Delete match", back = PlayersPaths.match(id))) {
            card {
                h2 { +(if (discard) "Discard this pending match?" else "Delete this match?") }
                p { +"All ratings are recalculated without it. You can undo from the match log." }
                postForm(PlayersPaths.delete(id), "actions") {
                    hubButton(if (discard) "Discard" else "Delete", ButtonStyle.DANGER)
                    a(PlayersPaths.match(id), classes = "btn btn-text") { +"Cancel" }
                }
            }
            matchCard(row, href = null, followUps = false)
        }
    }
    post("/players/matches/{id}/delete") {
        val id = call.parameters["id"].orEmpty()
        val stored = ctx.groupStore.deleteMatch(id)
        if (stored == null) {
            ctx.flash(call, "That match was already deleted (on another phone or browser).")
        } else {
            ctx.deviceStore.write(ctx.device(call).id, PlayersSlots.Undo, DeletedMatch(stored, ctx.clock.nowMillis()))
        }
        call.respondRedirect(PlayersPaths.LOG)
    }
    post(PlayersPaths.UNDO) {
        val deviceId = ctx.device(call).id
        var undo: DeletedMatch? = null
        ctx.deviceStore.update(deviceId, PlayersSlots.Undo) {
            undo = it
            null
        }
        val match = undo?.takeIf { ctx.clock.nowMillis() - it.deletedAt <= PlayersSlots.UNDO_WINDOW_MS }?.match
        // As the phone's undo: written again, stamped after its tombstone, unless it exists again.
        if (match == null || !ctx.groupStore.restoreMatch(match)) {
            call.respondRedirect(PlayersPaths.LOG)
            return@post
        }
        call.respondRedirect(PlayersPaths.logAt(match.id))
    }
    get("/players/matches/{id}/rematch") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val match = view.match(id) ?: return@get call.respondMatchGone(view, id)
        val row = match.toRow(view.byId, view.catalog, null, view.zone)
        call.respondPage(playersPage("Rematch", back = PlayersPaths.match(id))) {
            rematchChoices(row)
        }
    }
    get("/players/matches/{id}/saved") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val match = view.match(id) ?: return@get call.respondMatchGone(view, id)
        if (match.status != MatchStatus.COMPLETED) {
            call.respondRedirect(PlayersPaths.match(id))
            return@get
        }
        val saved = MatchEditor.savedSummary(match, view.players, view.snapshot.ratings)
        call.respondPage(playersPage("Result saved", back = PlayersPaths.LOG)) {
            resultSaved(saved)
        }
    }
}

/** A match that isn't there: deleted meanwhile (a tombstone), or never existed. */
internal suspend fun ApplicationCall.respondMatchGone(view: PlayersData, id: String) {
    if (!view.wasDeleted(id)) {
        respond(HttpStatusCode.NotFound) // the hub's "Nothing here" page
        return
    }
    respondPage(playersPage("Match", back = PlayersPaths.LOG), HttpStatusCode.Gone) {
        emptyState(
            "This match was deleted",
            "It was deleted on a phone or in a browser.",
            "Match log",
            PlayersPaths.LOG,
        )
    }
}

/** The Rematch sheet: same seats, swap heroes, swap sides (Match setup, Timer area) or draft again. */
private fun FlowContent.rematchChoices(row: MatchRowUi) {
    p("pl-hint") { +"Same group again. Match setup opens with these seats; flip the Tie Breaker coin there." }
    if (row.rematchOptions.isEmpty()) {
        notice("Only a completed match can be played again.")
        return
    }
    div("card pl-list") {
        row.rematchOptions.forEach { option ->
            val href = option.kind?.let { PlayersPaths.timerRematch(row.id, it.name) } ?: PlayersPaths.redraft(row.id)
            if (option.unavailable == null) {
                a(href, classes = "pl-row") {
                    div("pl-row-text") {
                        span("pl-row-title") { +option.title }
                        span("row-subtitle") { +option.body }
                    }
                    icon(Icons.CHEVRON, "row-chevron")
                }
            } else {
                div("pl-row pl-disabled") {
                    attributes["aria-disabled"] = "true"
                    div("pl-row-text") {
                        span("pl-row-title") { +option.title }
                        span("row-subtitle") { +option.unavailable.orEmpty() }
                    }
                }
            }
        }
    }
}

/** After a new result: who won, each player's change and rank, who leads now, Rematch and Done. */
private fun FlowContent.resultSaved(state: SavedResultUi) {
    val match = state.match
    match.winner?.let { winner ->
        p("pl-won") {
            teamBadge(winner)
            span { +(" won" + (match.winCondition?.let { " · ${it.label()}" } ?: "")) }
        }
    }
    sectionHeader("Rating changes")
    div("card pl-list") {
        state.players.forEach { line -> changeRow(line, state.ratingsReady) }
    }
    val leaderName = state.leaderName
    val leaderRating = state.leaderRating
    if (state.ratingsReady && leaderName != null && leaderRating != null) {
        p("pl-leader") { +"On top now: $leaderName · ${formatRating(leaderRating.value)} Elo" }
    }
    div("stack") {
        linkButton("Rematch", PlayersPaths.rematch(match.id), ButtonStyle.PRIMARY)
        linkButton("Done", PlayersPaths.logAt(match.id), ButtonStyle.OUTLINED)
    }
}

private fun FlowContent.changeRow(line: PlayerChangeUi, ready: Boolean) {
    div("pl-row") {
        line.player?.let { playerAvatar(it, "sm") }
        div("pl-row-text") {
            span("pl-row-title") { +line.name }
            span("pl-row-meta") {
                span {
                    +buildString {
                        append(if (line.won) "Won" else "Lost")
                        if (ready) line.rank?.let { append(" · #$it") }
                    }
                }
                if (ready && line.rating.provisional) provisionalPill()
            }
        }
        if (ready) {
            span("pl-elo") {
                span("pl-elo-value") { +formatRating(line.rating.value) }
                delta(line.delta, label = "rating")
            }
        }
    }
}

/** For the editor's hidden fields: a checkbox-free hidden input. */
internal fun FlowContent.hidden(name: String, value: String) {
    input(type = InputType.hidden, name = name) { this.value = value }
}
