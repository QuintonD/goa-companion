package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.sync.PlayerMergeOutcome
import dev.quinton.goa.core.presentation.players.MergeCopy
import dev.quinton.goa.core.presentation.players.OpponentRow
import dev.quinton.goa.core.presentation.players.PlayerDetail
import dev.quinton.goa.core.presentation.players.PlayerDetailUiState
import dev.quinton.goa.core.presentation.players.PlayerLook
import dev.quinton.goa.core.presentation.players.RatingChartModel
import dev.quinton.goa.core.presentation.players.formatPercent
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.plural
import dev.quinton.goa.core.presentation.players.spokenRecord
import dev.quinton.goa.core.presentation.players.winLoss
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.TopAction
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/** A player's page, editing, archiving and "Merge into…" (the phone's PlayerDetailScreen). */
internal fun Route.playerRoutes(ctx: HubContext) {
    get("/players/{id}") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        if (call.redirectedToMergedPlayer(view, id) { PlayersPaths.player(it) }) return@get
        val state = PlayerDetail.build(id, view.players, view.matches, view.snapshot, view.catalog, view.zone)
        val player = state.player ?: return@get call.respondPlayerMissing()
        val me = ctx.meId(call, view.data)
        val canInvite = !player.archived && me != player.id
        val flash = ctx.takeFlash(call)
        val edit = TopAction("Edit player", PlayersPaths.editPlayer(id), Icons.EDIT)
        call.respondPage(playersPage(player.name, back = PlayersPaths.HOME, actions = listOf(edit))) {
            flashNotice(flash)
            playerDetail(state, view, canInvite)
        }
    }
    get("/players/{id}/edit") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        if (call.redirectedToMergedPlayer(view, id) { PlayersPaths.editPlayer(it) }) return@get
        val player = view.byId[id] ?: return@get call.respondPlayerMissing()
        call.respondPlayerForm(PlayerForm(player.name, player.color, player.avatarId), id)
    }
    post("/players/{id}/edit") { call.savePlayer(ctx, call.parameters["id"].orEmpty()) }
    post("/players/{id}/archive") {
        val id = call.parameters["id"].orEmpty()
        val archived = call.receiveParameters()["archived"] == "true"
        val view = PlayersData.of(ctx)
        if (view.byId[id] == null) return@post call.respondPlayerMissing()
        ctx.groupStore.archivePlayer(id, archived)
        call.respondRedirect(PlayersPaths.player(id))
    }
    get("/players/{id}/merge") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val state = PlayerDetail.build(id, view.players, view.matches, view.snapshot, view.catalog, view.zone)
        val player = state.player ?: return@get call.respondPlayerMissing()
        val target = call.request.queryParameters["into"]?.let { into ->
            state.mergeCandidates.firstOrNull { it.player.id == into }
        }
        call.respondPage(playersPage("Merge ${player.name} into…", back = PlayersPaths.player(id))) {
            if (target == null) {
                mergePicker(state)
            } else {
                card {
                    h2 { +"Merge into ${target.player.name}?" }
                    p {
                        +MergeCopy.confirmMessage(player.name, state.totalMatches, target.player.name, target.matches)
                    }
                    postForm(PlayersPaths.merge(id), "actions") {
                        input(type = InputType.hidden, name = "into") { value = target.player.id }
                        hubButton("Merge", ButtonStyle.DANGER)
                        a(PlayersPaths.player(id), classes = "btn btn-text") { +"Cancel" }
                    }
                }
            }
        }
    }
    post("/players/{id}/merge") {
        val id = call.parameters["id"].orEmpty()
        val into = call.receiveParameters()["into"].orEmpty()
        val view = PlayersData.of(ctx)
        val target = view.byId[into]?.takeIf { !it.archived && it.id != id }
        // Only an active player is offered ("Merge into…" lists active players, as on the phone).
        val outcome = if (target == null) PlayerMergeOutcome.NotPossible else ctx.groupStore.mergePlayers(id, into)
        when (outcome) {
            PlayerMergeOutcome.Merged -> {
                ctx.flash(call, "Merged into ${target?.name}. The phones follow at their next sync.")
                call.respondRedirect(PlayersPaths.player(into))
            }

            is PlayerMergeOutcome.PlayedTogether -> call.respondMergeRefused(
                MergeCopy.playedTogether(outcome.matches, view.byId, view.zone),
                back = PlayersPaths.player(id),
            )

            PlayerMergeOutcome.NotPossible -> {
                ctx.flash(call, "That merge isn't possible any more: one of the players is missing or already merged.")
                call.respondRedirect(PlayersPaths.player(id))
            }
        }
    }
}

/** A merged-away player's URLs lead to the player they joined. */
internal suspend fun ApplicationCall.redirectedToMergedPlayer(
    view: PlayersData,
    id: String,
    path: (String) -> String,
): Boolean {
    val resolved = view.data.merges.resolve(id)
    if (resolved == id || view.byId[resolved] == null) return false
    respondRedirect(path(resolved))
    return true
}

/** "Can't merge": they played in the same match (the phone's PlayedTogetherDialog). */
internal suspend fun ApplicationCall.respondMergeRefused(matches: List<String>, back: String) {
    respondPage(playersPage(MergeCopy.REFUSED_TITLE, back = back), HttpStatusCode.Conflict) {
        div("error-state") {
            attributes["role"] = "alert"
            div("stack") {
                p { +MergeCopy.REFUSED_MESSAGE }
                ul { matches.forEach { li { +it } } }
            }
        }
        a(back, classes = "btn btn-tonal") { +"OK" }
    }
}

private fun FlowContent.mergePicker(state: PlayerDetailUiState) {
    val player = state.player ?: return
    if (state.mergeCandidates.isEmpty()) {
        emptyState("No one to merge into", "There is no other active player.")
        return
    }
    form(action = PlayersPaths.merge(player.id), method = FormMethod.get, classes = "stack") {
        fieldSet("pl-choices") {
            legend("visually-hidden") { +"Merge ${player.name} into" }
            state.mergeCandidates.forEachIndexed { i, candidate ->
                label("pl-choice") {
                    input(type = InputType.radio, name = "into") {
                        value = candidate.player.id
                        required = true
                        checked = i == 0 && state.mergeCandidates.size == 1
                    }
                    playerAvatar(candidate.player, "sm")
                    span("pl-choice-text") {
                        span("row-title") { +candidate.player.name }
                        span("row-subtitle") { +plural(candidate.matches, "match", "matches") }
                    }
                }
            }
        }
        div("actions") {
            hubButton("Next", ButtonStyle.PRIMARY)
            a(PlayersPaths.player(player.id), classes = "btn btn-text") { +"Cancel" }
        }
    }
}

private fun FlowContent.playerDetail(state: PlayerDetailUiState, view: PlayersData, canInvite: Boolean) {
    val player = state.player ?: return
    profileHead(state)
    state.lookAlike?.let { twin ->
        // Two people who look identical in every list (added on two phones): a nudge, never a change.
        a(PlayersPaths.editPlayer(player.id), classes = "pl-nudge") {
            playerAvatar(twin, "sm")
            span("pl-nudge-text") { +PlayerLook.lookAlikeHint(twin) }
            span("pl-nudge-action") { +"Edit" }
        }
    }
    sectionHeader("Rating history")
    card {
        val chart = RatingChartModel.of(state.history, state.initialRating, state.historyDates)
        if (chart == null) {
            div("pl-empty") {
                p("pl-empty-title") { +"No rated matches yet" }
                p { +"The chart starts with ${player.name}'s first completed match." }
            }
        } else {
            ratingChart(chart, view.zone)
            state.peak?.let {
                p("pl-hint") { +"Peak ${formatRating(it)} · start ${formatRating(state.initialRating)}" }
            }
        }
    }
    records(state)
    div("pl-section-head") {
        sectionHeader("Recent matches")
        if (state.totalMatches > state.recentMatches.size) {
            a(PlayersPaths.LOG, classes = "btn btn-text") { +"All ${state.totalMatches}" }
        }
    }
    if (state.recentMatches.isEmpty()) {
        p("pl-hint") { +"No matches logged with ${player.name} yet." }
    }
    state.recentMatches.forEach { matchCard(it, PlayersPaths.match(it.id), followUps = false) }
    manage(state, canInvite)
}

/** Avatar, name, rank and pills, the rating; then games, record, win rate, double-pilot games. */
private fun FlowContent.profileHead(state: PlayerDetailUiState) {
    val player = state.player ?: return
    val rating = state.rating
    div("pl-profile") {
        playerAvatar(player, "lg")
        div("pl-profile-text") {
            h2("pl-profile-name") { +player.name }
            div("pl-row-meta") {
                state.rank?.let { span { +"Rank $it of ${state.rankedPlayers}" } }
                if (rating.games > 0 && rating.provisional) provisionalPill()
                if (player.archived) pill("Archived")
            }
        }
        div("pl-elo pl-elo-big") {
            span("pl-elo-value pl-top") { +(if (rating.games > 0) formatRating(rating.value) else "—") }
            span("pl-elo-caption") { +"Elo" }
        }
    }
    statTiles(
        listOf(
            rating.games.toString() to "Games",
            (if (rating.games > 0) winLoss(rating.wins, rating.games) else "—") to "Won–lost",
            (if (rating.games > 0) formatPercent(rating.wins.toDouble() / rating.games) else "—") to "Win rate",
            state.doublePilotGames.toString() to "Double pilot",
        ),
    )
}

/** Best heroes (or most played), how the player wins, head to head and teammates. */
private fun FlowContent.records(state: PlayerDetailUiState) {
    val player = state.player ?: return
    if (state.heroes.isNotEmpty()) {
        sectionHeader(state.heroesTitle)
        div("card pl-list") {
            state.heroes.forEach { hero ->
                div("pl-row") {
                    heroPortrait(hero.portrait)
                    div("pl-row-text") {
                        span("pl-row-title") { +hero.name }
                        srOnly(spokenRecord(hero.record.wins, hero.record.losses))
                    }
                    span("pl-elo") {
                        span("pl-elo-value") { +formatPercent(hero.record.winRate) }
                        span("pl-elo-caption") { +winLoss(hero.record.wins, hero.record.games) }
                    }
                }
            }
        }
    }
    if (state.winConditions.total > 0) {
        sectionHeader("How ${player.name} wins")
        card { winConditionBars(state.winConditions) }
    }
    if (state.rivals.isNotEmpty()) {
        sectionHeader("Head to head")
        opponents(state.rivals, versus = true)
    }
    if (state.teammates.isNotEmpty()) {
        sectionHeader("Teammates")
        opponents(state.teammates, versus = false)
    }
}

/** Invite, archive or restore, merge into… */
private fun FlowContent.manage(state: PlayerDetailUiState, canInvite: Boolean) {
    val player = state.player ?: return
    div("pl-manage") {
        if (canInvite) {
            linkButton("Invite ${player.name}", PlayersPaths.invite(player.id), ButtonStyle.TONAL, Icons.SHARE)
            p("pl-hint") { +"A personal invite: their phone or browser joins the group as ${player.name}." }
        }
        postForm("${PlayersPaths.player(player.id)}/archive") {
            input(type = InputType.hidden, name = "archived") { value = (!player.archived).toString() }
            hubButton(if (player.archived) "Restore player" else "Archive player", ButtonStyle.OUTLINED)
        }
        p("pl-hint") {
            +"Archived players are hidden from the leaderboard and pickers. Their matches and ratings stay in the log."
        }
        if (state.mergeCandidates.isNotEmpty()) {
            linkButton("Merge into…", PlayersPaths.merge(player.id), ButtonStyle.OUTLINED)
            p("pl-hint") { +MergeCopy.MERGE_HELP }
        }
    }
}

private fun FlowContent.opponents(rows: List<OpponentRow>, versus: Boolean) {
    div("card pl-list") {
        rows.forEach { row ->
            div("pl-row pl-row-compact") {
                val record = spokenRecord(row.record.wins, row.record.losses)
                srOnly("${if (versus) "Against" else "With"} ${row.name}: $record")
                row.player?.let { playerAvatar(it, "sm") }
                span("pl-row-title pl-grow") {
                    attributes["aria-hidden"] = "true"
                    +"${if (versus) "vs" else "with"} ${row.name}"
                }
                span("pl-figure") {
                    attributes["aria-hidden"] = "true"
                    +"${winLoss(row.record.wins, row.record.games)} · ${formatPercent(row.record.winRate)}"
                }
            }
        }
    }
}
