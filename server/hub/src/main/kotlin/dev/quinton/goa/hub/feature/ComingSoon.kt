package dev.quinton.goa.hub.feature

import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.listRow
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** A placeholder page for an area not built yet: the shell with a short "Coming soon". */
class ComingSoon private constructor(
    private val path: String,
    private val title: String,
    private val tab: Tab?,
    private val back: String? = null,
    private val links: List<Pair<String, String>> = emptyList(),
) : HubFeature {
    override fun Route.routes(ctx: HubContext) {
        get(path) {
            call.respondPage(HubPage(title, tab, back = back)) {
                emptyState("Coming soon", "This part of the app isn't on the web yet. It works in the Android app.")
                links.forEach { (label, href) -> listRow(label, href = href) }
            }
        }
    }

    companion object {
        val Heroes = ComingSoon("/heroes", "Heroes", Tab.HEROES)
        val Timer = ComingSoon("/timer", "Timer", Tab.TIMER)
        val Draft = ComingSoon("/draft", "Draft", Tab.DRAFT)
        val Players = ComingSoon("/players", "Players", Tab.PLAYERS)
        val Rules = ComingSoon("/rules", "Rules", null, back = "/heroes")
        val Trivia = ComingSoon("/trivia", "Card trivia", null, back = "/heroes")
        val Group = ComingSoon("/group", "Group", null, back = "/settings")
        val Settings = ComingSoon(
            "/settings",
            "Settings",
            null,
            back = "/heroes",
            links = listOf("Group" to "/group", "Get the Android app" to AppDownloadFeature.PATH),
        )
    }
}
