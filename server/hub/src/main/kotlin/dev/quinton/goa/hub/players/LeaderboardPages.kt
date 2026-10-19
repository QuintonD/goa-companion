package dev.quinton.goa.hub.players

import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.players.Leaderboard
import dev.quinton.goa.core.presentation.players.LeaderboardRow
import dev.quinton.goa.core.presentation.players.MAX_NAME_LENGTH
import dev.quinton.goa.core.presentation.players.PlayerLook
import dev.quinton.goa.core.presentation.players.PlayersUiState
import dev.quinton.goa.core.presentation.players.SamePeople
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.plural
import dev.quinton.goa.core.presentation.players.winLoss
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.store.InvalidGroupDataException
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.PlayerPalette
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.textField
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
import kotlinx.html.span

/** The Players tab's first screen: the leaderboard, "Same people?", archived players, add player. */
internal fun Route.leaderboardRoutes(ctx: HubContext) {
    get(PlayersPaths.HOME) {
        val view = PlayersData.of(ctx)
        val showArchived = call.request.queryParameters["archived"] == "1"
        val different = ctx.deviceStore.read(ctx.device(call).id, PlayersSlots.DifferentPeople).orEmpty()
        val samePeople = SamePeople.pairs(
            view.data.players(),
            view.matches,
            view.data.merges,
            different,
        ).size
        val state = Leaderboard.build(view.players, view.snapshot, showArchived, ctx.meId(call, view.data))
            .copy(inGroup = true, samePeople = samePeople)
        val flash = ctx.takeFlash(call)
        call.respondPage(playersPage("Players")) {
            sectionTabs(PlayersSection.PLAYERS)
            flashNotice(flash)
            leaderboard(state)
        }
    }
    get(PlayersPaths.NEW_PLAYER) {
        val view = PlayersData.of(ctx)
        val look = PlayerForm(
            name = "",
            color = PlayerLook.newColor(view.players, PlayerPalette.swatches.map { it.first }),
            avatarId = PlayerLook.newCrest(view.players, PlayerPalette.CRESTS),
        )
        call.respondPlayerForm(look, playerId = null)
    }
    post(PlayersPaths.NEW_PLAYER) { call.savePlayer(ctx, playerId = null) }
}

private fun FlowContent.leaderboard(state: PlayersUiState) {
    if (state.isEmpty) {
        emptyState(
            "Gather your guards",
            "Add everyone in your play group. Ratings appear after the first logged match.",
            "Add player",
            PlayersPaths.NEW_PLAYER,
        )
        return
    }
    state.samePeopleText?.let { text ->
        a(PlayersPaths.SAME_PEOPLE, classes = "pl-nudge") {
            icon(Icons.GROUP)
            span("pl-nudge-text") { +text }
            span("pl-nudge-action") { +"Review" }
        }
    }
    if (state.ranked.isNotEmpty()) {
        div("pl-section-head") {
            sectionHeader("Leaderboard")
            span("pl-section-trailing") { +plural(state.ratedMatches, "rated match", "rated matches") }
        }
        div("card pl-list") {
            state.ranked.forEach { leaderboardRow(it) }
        }
    }
    if (state.unrated.isNotEmpty()) {
        sectionHeader("No games yet")
        div("card pl-list") {
            state.unrated.forEach { leaderboardRow(it) }
        }
    }
    if (state.archived.isNotEmpty()) {
        val label = if (state.showArchived) {
            "Hide archived players"
        } else {
            "Show archived players (${state.archived.size})"
        }
        a(
            if (state.showArchived) PlayersPaths.HOME else "${PlayersPaths.HOME}?archived=1",
            classes = "btn btn-text pl-left",
        ) {
            +label
        }
        if (state.showArchived) {
            div("card pl-list") {
                state.archived.forEach { leaderboardRow(it, archived = true) }
            }
        }
    }
    if (state.canFindDuplicates) {
        a(PlayersPaths.SAME_PEOPLE, classes = "btn btn-text pl-left") { +"Find duplicates" }
    }
    a(PlayersPaths.NEW_PLAYER, classes = "btn btn-primary pl-fab") {
        icon(Icons.ADD)
        span { +"Add player" }
    }
}

private fun FlowContent.leaderboardRow(row: LeaderboardRow, archived: Boolean = false) {
    val rating = row.rating
    a(PlayersPaths.player(row.player.id), classes = if (archived) "pl-row pl-archived" else "pl-row") {
        attributes["aria-label"] = Leaderboard.spoken(row, archived)
        span(rankCss(row.rank)) {
            attributes["aria-hidden"] = "true"
            +(row.rank?.toString() ?: "–")
        }
        playerAvatar(row.player)
        div("pl-row-text") {
            span("pl-row-title") {
                span("pl-name") { +row.player.name }
                if (row.isMe) pill("You", "pl-pill-you")
            }
            span("pl-row-meta") {
                if (rating.games > 0) {
                    span { +winLoss(rating.wins, rating.games) }
                    delta(row.trend, caption = "last ${row.trendGames}")
                    if (rating.provisional) provisionalPill()
                } else {
                    span { +(if (archived) "Archived" else "Log a match to get rated") }
                }
            }
        }
        if (rating.games > 0) {
            span("pl-elo") {
                span(if (row.rank == 1) "pl-elo-value pl-top" else "pl-elo-value") { +formatRating(rating.value) }
                span("pl-elo-caption") { +"Elo" }
            }
        } else {
            icon(Icons.CHEVRON, "row-chevron")
        }
    }
}

private fun rankCss(rank: Int?): String = when (rank) {
    1 -> "pl-rank pl-rank-1"
    2, 3 -> "pl-rank pl-rank-podium"
    else -> "pl-rank"
}

/** The player editor's fields as posted. */
internal data class PlayerForm(val name: String, val color: Long, val avatarId: Int)

/** "New player" / "Edit player": name, colour, crest (the phone's PlayerEditScreen). */
internal suspend fun ApplicationCall.respondPlayerForm(
    form: PlayerForm,
    playerId: String?,
    nameError: String? = null,
    problem: String? = null,
) {
    val back = playerId?.let { PlayersPaths.player(it) } ?: PlayersPaths.HOME
    val action = playerId?.let { PlayersPaths.editPlayer(it) } ?: PlayersPaths.NEW_PLAYER
    val preview = Player("preview", form.name.ifBlank { "?" }, form.color, form.avatarId, createdAt = 0)
    val status = if (nameError != null || problem != null) HttpStatusCode.UnprocessableEntity else HttpStatusCode.OK
    respondPage(playersPage(if (playerId == null) "New player" else "Edit player", back = back), status) {
        problem?.let { errorState(it) }
        postForm(action, "stack pl-player-form") {
            div("pl-preview") { playerAvatar(preview, "lg") }
            textField("name", "Name", form.name, required = true, error = nameError) {
                attributes["enterkeyhint"] = "done"
                maxLength = MAX_NAME_LENGTH.toString()
                attributes["autocapitalize"] = "words"
                attributes["autocomplete"] = "off"
            }
            if (nameError == null) p("pl-hint") { +"Up to $MAX_NAME_LENGTH characters." }
            fieldSet("pl-swatches") {
                legend("section-header") { +"Colour" }
                PlayerPalette.swatches.indices.forEach { i ->
                    label("pl-swatch pc-$i") {
                        input(type = InputType.radio, name = "color") {
                            value = i.toString()
                            checked = PlayerPalette.indexOf(form.color) == i
                        }
                        span("pl-swatch-dot") { attributes["aria-hidden"] = "true" }
                        span("visually-hidden") { +PlayerLookNames.colors[i] }
                    }
                }
            }
            fieldSet("pl-crests") {
                legend("section-header") { +"Crest" }
                repeat(PlayerPalette.CRESTS) { crest ->
                    label("pl-crest-choice") {
                        input(type = InputType.radio, name = "crest") {
                            value = crest.toString()
                            checked = crest == form.avatarId
                        }
                        playerAvatar(preview.copy(avatarId = crest), "md")
                        span("visually-hidden") { +PlayerLookNames.crests[crest] }
                    }
                }
            }
            p("pl-hint pl-center") { +"Colour and crest identify the player on the leaderboard and in match records." }
            hubButton("Save", ButtonStyle.PRIMARY)
        }
        a(back, classes = "btn btn-text") { +"Cancel" }
    }
}

/** POST of the player editor: the phone's rules (trimmed, unique name incl. archived players). */
internal suspend fun ApplicationCall.savePlayer(ctx: HubContext, playerId: String?) {
    val params = receiveParameters()
    val view = PlayersData.of(ctx)
    val original = playerId?.let { id -> view.data.document.players.firstOrNull { it.id == id } }
    if (playerId != null && original == null) {
        respondPlayerMissing()
        return
    }
    val name = PlayerLook.nameInput(params["name"].orEmpty()).trim()
    val color = params["color"]?.toIntOrNull()?.let { PlayerPalette.swatches.getOrNull(it)?.first }
        ?: original?.color
        ?: PlayerLook.newColor(view.players, PlayerPalette.swatches.map { it.first })
    val crest = params["crest"]?.toIntOrNull()?.takeIf { it in 0 until PlayerPalette.CRESTS }
        ?: original?.avatarId
        ?: PlayerLook.newCrest(view.players, PlayerPalette.CRESTS)
    val form = PlayerForm(name, color, crest)
    val error = PlayerLook.nameError(name, ctx.groupStore.findPlayerByName(name), playerId)
    if (error != null) {
        respondPlayerForm(form, playerId, nameError = error)
        return
    }
    val player = original?.copy(name = name, color = color, avatarId = crest) ?: Player(
        id = UUID.randomUUID().toString(),
        name = name,
        color = color,
        avatarId = crest,
        createdAt = ctx.clock.nowMillis(),
    )
    try {
        ctx.groupStore.upsertPlayer(player)
    } catch (e: InvalidGroupDataException) {
        respondPlayerForm(form, playerId, problem = e.message)
        return
    }
    respondRedirect(if (playerId == null) PlayersPaths.HOME else PlayersPaths.player(playerId))
}

/** No such player (players are never deleted; merged-away ones redirect): the hub's "Nothing here" page. */
internal suspend fun ApplicationCall.respondPlayerMissing() = respond(HttpStatusCode.NotFound)

/** Colour and crest names for screen readers (the phone's `PlayerColors` / `PlayerCrests`; keep in step). */
internal object PlayerLookNames {
    val colors = listOf("Tide teal", "Bronze", "Coral", "Sky", "Amethyst", "Kelp", "Rose", "Ember", "Pearl", "Lime")

    val crests = listOf(
        "Double chevron", "Lozenge", "Three bezants", "Four-pointed star", "Key", "Crescent",
        "Sun", "Waves", "Mount", "Cross pattée", "Saltire", "Tower",
        "Crown", "Anchor", "Leaf", "Hourglass", "Thunderbolt", "Quatrefoil",
        "Three rings", "Arrowhead", "Hexagon", "Pall", "Chequy", "Frost star",
    )
}
