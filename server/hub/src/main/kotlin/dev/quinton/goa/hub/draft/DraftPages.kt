package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.DraftAgain
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.MethodsUiState
import dev.quinton.goa.core.presentation.draft.PreviewLayout
import dev.quinton.goa.core.presentation.draft.teamLabel
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.TopActions
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.server.application.ApplicationCall
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

internal const val DRAFT_CSS = "draft/draft.css"
internal const val DRAFT_JS = "draft/draft.js"

/** The Draft area's GET pages. Each renders the current state; nothing here changes state. */
internal class DraftPages(private val ctx: HubContext, private val data: DraftData) {
    private fun page(title: String, back: String? = null, tab: Tab? = Tab.DRAFT, showTabs: Boolean = true) = HubPage(
        title = title,
        tab = tab,
        back = back,
        actions = if (back == null) TopActions.forTab(Tab.DRAFT) else emptyList(),
        showTabs = showTabs,
        scripts = listOf(DRAFT_JS),
        styles = listOf(DRAFT_CSS),
    )

    suspend fun setup(call: ApplicationCall) {
        val id = call.device.id
        val state = data.setup(id)
        val notes = ctx.deviceStore.read(id, DraftSlots.Notes)
        call.respondPage(page("Draft")) { setupPage(state, notes) }
    }

    /** "Draft again" from a match: shows the group, and drafts with it only when asked (a POST). */
    suspend fun rematch(call: ApplicationCall, matchId: String) {
        val match = ctx.groupStore.data().match(matchId)
        call.respondPage(page("Draft again", back = DraftFeature.PATH)) {
            if (match == null) {
                errorState("That match isn't in the group's log.", DraftFeature.PATH)
                return@respondPage
            }
            val group = DraftAgain.of(match)
            card("draft-again") {
                h2 { +"Draft again with the same group?" }
                p("hint") {
                    +(
                        "The draft opens with these players and teams; you pick new heroes. " +
                            "The method and pool stay as you last set them."
                        )
                }
                div("team-columns") {
                    for (team in Team.entries) {
                        val ids = if (team == Team.RED) group.red else group.blue
                        div("team-column ${teamCss(team)}") {
                            teamTitle(team)
                            ul("plain") {
                                ids.forEach { pid ->
                                    li("member") {
                                        val player = data.player(pid)
                                        playerAvatar(player, "sm")
                                        span { +(player?.name ?: "Unknown player") }
                                        if (pid == group.doublePilot) span("hint") { +" · two heroes" }
                                    }
                                }
                            }
                        }
                    }
                }
                if (group.handicap) p("hint") { +"Uneven teams: the larger team plays with Handicap cards." }
                postForm("${DraftFeature.PATH}/rematch", "actions") {
                    input(type = InputType.hidden, name = "match") { value = match.id }
                    button(type = ButtonType.submit, classes = "btn btn-primary") { +"Draft again" }
                    linkButton("Cancel", DraftFeature.PATH, ButtonStyle.TEXT)
                }
            }
        }
    }

    /** The custom pool: tick the heroes the draft may deal from. */
    suspend fun pool(call: ApplicationCall) {
        val id = call.device.id
        val form = data.form(id)
        val heroes = ctx.catalog.catalog.heroes
        call.respondPage(page("Custom pool", back = "${DraftFeature.PATH}#pool")) {
            postForm("${DraftFeature.PATH}/setup", "stack") {
                attributes["data-pool-form"] = ""
                div("pool-tools") {
                    span("pool-count") {
                        attributes["data-pool-count"] = ""
                        attributes["aria-live"] = "polite"
                        +"${form.customSelection.size} of ${heroes.size} chosen"
                    }
                    opButton("pool-all", "All", "btn btn-text")
                    opButton("pool-none", "None", "btn btn-text")
                }
                div("pool-grid choose-grid") {
                    heroes.forEach { hero ->
                        val info = data.heroes.getValue(hero.id)
                        label("pick-tile") {
                            input(type = InputType.checkBox, name = "hero") {
                                value = hero.id
                                checked = hero.id in form.customSelection
                            }
                            portrait(info)
                            span("tile-name") { +info.name }
                        }
                    }
                }
                div("sticky-actions") {
                    opButton("custom-pool", "Use these heroes", "btn btn-primary wide")
                }
            }
        }
    }

    suspend fun board(call: ApplicationCall) {
        val state = data.board(call.device.id)
        val web = ctx.deviceStore.read(call.device.id, DraftSlots.Board) ?: DraftBoardWeb()
        val title = state.board?.method?.name ?: state.playerDraft?.let { "Player Draft" } ?: "Draft"
        // The board is a table tool: no bottom tabs, as on the phone (ADR-020).
        call.respondPage(page(title, back = DraftFeature.PATH, showTabs = false)) {
            boardPage(state, web, ctx.clock.nowMillis())
        }
    }

    suspend fun methods(call: ApplicationCall) {
        val layout = PreviewLayout.fromKey(call.request.queryParameters["layout"]) ?: PreviewLayout.OPTIONS.first()
        val state = MethodsUiState.of(data.library(call.device.id), layout)
        call.respondPage(page("Draft methods", back = "${DraftFeature.PATH}#methods")) { methodsPage(state) }
    }

    suspend fun methodEditor(call: ApplicationCall, key: String, env: DraftEnvironment) {
        val id = call.device.id
        val library = data.library(id)
        if (key != DraftActions.NEW && library.find(key) == null) {
            call.respondPage(page("Edit method", back = DraftFeature.METHODS)) {
                emptyState("No such method", "It may have been deleted.", "All methods", DraftFeature.METHODS)
            }
            return
        }
        val state = DraftActions(ctx, data, env).editorState(id, key, env::newId)
        val title = if (state.isNew) "New method" else "Edit method"
        call.respondPage(page(title, back = DraftFeature.METHODS)) { methodEditor(key, state) }
    }
}

/** A list of the given team's players' names, for summaries. */
internal fun FlowContent.teamLine(team: Team, names: List<String>) {
    p("team-line ${teamCss(team)}") {
        +"${teamLabel(team)}: "
        span("team-line-names") { +names.joinToString(", ").ifEmpty { "—" } }
    }
}
