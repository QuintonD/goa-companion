package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.player.PlayerMatchUp
import dev.quinton.goa.core.presentation.players.MergeCopy
import dev.quinton.goa.core.presentation.players.PairSide
import dev.quinton.goa.core.presentation.players.SamePeople
import dev.quinton.goa.core.presentation.players.SamePeoplePair
import dev.quinton.goa.core.presentation.players.SamePeopleUiState
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.core.domain.sync.PlayerMergeOutcome
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.ZoneId
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.span

/**
 * "Same people?" (ADR-031): look-alike pairs worked out from the current data; "Same person" merges
 * the newer into the older (the usual merge and refusal), "Different people" is kept on this browser.
 */
internal fun Route.samePeopleRoutes(ctx: HubContext) {
    get(PlayersPaths.SAME_PEOPLE) {
        val state = ctx.samePeopleState(call)
        val zone = ctx.clock.zone
        val confirm = pairKey(call.request.queryParameters["a"], call.request.queryParameters["b"])
            ?.let { key -> state.pairs.firstOrNull { it.key == key } }
        val flash = ctx.takeFlash(call)
        call.respondPage(playersPage("Same people?", back = PlayersPaths.HOME)) {
            flashNotice(flash)
            if (confirm != null) {
                confirmMerge(confirm)
                return@respondPage
            }
            if (state.pairs.isEmpty()) {
                emptyState(SamePeople.EMPTY_TITLE, SamePeople.EMPTY_MESSAGE)
            } else {
                p("pl-hint") { +SamePeople.INTRO }
                state.pairs.forEach { pairCard(it, zone) }
            }
            state.askAgainText?.let { text ->
                postForm("${PlayersPaths.SAME_PEOPLE}/ask-again") { hubButton(text, ButtonStyle.TEXT) }
            }
        }
    }
    post("${PlayersPaths.SAME_PEOPLE}/different") {
        val params = call.receiveParameters()
        val key = pairKey(params["a"], params["b"]).orEmpty()
        val state = ctx.samePeopleState(call)
        if (state.pairs.any { it.key == key }) {
            ctx.deviceStore.update(ctx.device(call).id, PlayersSlots.DifferentPeople) { it.orEmpty() + key }
        }
        call.respondRedirect(PlayersPaths.SAME_PEOPLE)
    }
    post("${PlayersPaths.SAME_PEOPLE}/ask-again") {
        ctx.deviceStore.delete(ctx.device(call).id, PlayersSlots.DifferentPeople)
        call.respondRedirect(PlayersPaths.SAME_PEOPLE)
    }
    post("${PlayersPaths.SAME_PEOPLE}/same") {
        val params = call.receiveParameters()
        val key = pairKey(params["a"], params["b"]).orEmpty()
        val pair = ctx.samePeopleState(call).pairs.firstOrNull { it.key == key }
        if (pair == null) {
            // Gone already (merged elsewhere and synced meanwhile): the list is current again.
            call.respondRedirect(PlayersPaths.SAME_PEOPLE)
            return@post
        }
        val from = pair.other.player
        val into = pair.keep.player
        when (val outcome = ctx.groupStore.mergePlayers(from.id, into.id)) {
            PlayerMergeOutcome.Merged -> {
                ctx.flash(call, "Merged ${from.name} into ${into.name}. The phones follow at their next sync.")
                call.respondRedirect(PlayersPaths.SAME_PEOPLE)
            }

            is PlayerMergeOutcome.PlayedTogether -> {
                val view = PlayersData.of(ctx)
                call.respondMergeRefused(
                    MergeCopy.playedTogether(outcome.matches, view.byId, view.zone),
                    back = PlayersPaths.SAME_PEOPLE,
                )
            }

            PlayerMergeOutcome.NotPossible -> call.respondRedirect(PlayersPaths.SAME_PEOPLE)
        }
    }
}

/**
 * A pair travels as its two player ids (`a`, `b`): the pair key's separator is a newline, which
 * forms would turn into CRLF.
 */
private fun pairKey(a: String?, b: String?): String? =
    if (a.isNullOrEmpty() || b.isNullOrEmpty()) null else PlayerMatchUp.pairKey(a, b)

private fun FlowContent.pairInputs(pair: SamePeoplePair) {
    input(type = InputType.hidden, name = "a") { value = pair.keep.player.id }
    input(type = InputType.hidden, name = "b") { value = pair.other.player.id }
}

/** The pairs to review on this browser, from the current data. */
internal fun HubContext.samePeopleState(call: ApplicationCall): SamePeopleUiState {
    val view = PlayersData.of(this)
    val different = deviceStore.read(device(call).id, PlayersSlots.DifferentPeople).orEmpty()
    return SamePeopleUiState(
        loading = false,
        pairs = SamePeople.pairs(view.data.players(), view.matches, view.data.merges, different),
        differentCount = different.size,
    )
}

private fun FlowContent.pairCard(pair: SamePeoplePair, zone: ZoneId) {
    card("pl-pair") {
        side(pair.keep, zone)
        side(pair.other, zone)
        div("actions pl-end") {
            postForm("${PlayersPaths.SAME_PEOPLE}/different") {
                pairInputs(pair)
                // (hubButton's block runs after its label, too late for attributes.)
                button(type = ButtonType.submit, classes = "btn btn-outlined") {
                    attributes["aria-label"] =
                        "${pair.keep.player.name} and ${pair.other.player.name} are different people"
                    +"Different people"
                }
            }
            val query = "a=${PlayersPaths.segment(pair.keep.player.id)}&b=${PlayersPaths.segment(pair.other.player.id)}"
            a("${PlayersPaths.SAME_PEOPLE}?$query", classes = "btn btn-primary") {
                attributes["aria-label"] = "${pair.keep.player.name} and ${pair.other.player.name} are the same person"
                +"Same person"
            }
        }
    }
}

private fun FlowContent.side(side: PairSide, zone: ZoneId) {
    div("pl-row pl-row-flat") {
        playerAvatar(side.player)
        div("pl-row-text") {
            span("pl-row-title") { +side.player.name }
            span("row-subtitle") { +SamePeople.summary(side, zone) }
        }
    }
}

private fun FlowContent.confirmMerge(pair: SamePeoplePair) {
    val from = pair.other
    val into = pair.keep
    card {
        h2 { +"Merge ${from.player.name} into ${into.player.name}?" }
        p { +MergeCopy.confirmMessage(from.player.name, from.games, into.player.name, into.games) }
        postForm("${PlayersPaths.SAME_PEOPLE}/same", "actions") {
            pairInputs(pair)
            hubButton("Merge", ButtonStyle.DANGER)
            a(PlayersPaths.SAME_PEOPLE, classes = "btn btn-text") { +"Cancel" }
        }
    }
}
