package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.catalog.AttackStyle
import dev.quinton.goa.core.presentation.catalog.CardViewerUiState
import dev.quinton.goa.core.presentation.catalog.CatalogControls
import dev.quinton.goa.core.presentation.catalog.CatalogFilter
import dev.quinton.goa.core.presentation.catalog.CatalogLayout
import dev.quinton.goa.core.presentation.catalog.CatalogSearchIndex
import dev.quinton.goa.core.presentation.catalog.CatalogSort
import dev.quinton.goa.core.presentation.catalog.CatalogUiState
import dev.quinton.goa.core.presentation.catalog.HeroDetailUiState
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.tidyUrl
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.TopActions
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.respondFragment
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Heroes (ADR-030 §8): the catalog grid with search and filters, a hero's page, and the card
 * viewer. Everything shown is built by `:core:presentation` (the phone's own builders); filters and
 * sort live in the URL, the grid/list choice in the browser's settings (`catalogListView`).
 */
object HeroesFeature : HubFeature {
    const val PATH = "/heroes"

    override fun Route.routes(ctx: HubContext) {
        get(PATH) { call.catalogPage(ctx) }
        post("$PATH/view") { call.setView(ctx) }
        get("$PATH/{heroId}") { call.heroPage(ctx, call.parameters["heroId"].orEmpty()) }
        get("$PATH/{heroId}/cards/{cardId}") {
            call.cardViewer(ctx, call.parameters["heroId"].orEmpty(), call.parameters["cardId"].orEmpty())
        }
    }

    /** Pages that show catalog pieces load these styles. */
    val styles = listOf(CATALOG_CSS)

    @Volatile
    private var indexed: Pair<Catalog, CatalogSearchIndex>? = null

    /** The search index for [catalog], built once (the catalog is loaded once per process). */
    fun index(catalog: Catalog): CatalogSearchIndex {
        indexed?.let { (c, index) -> if (c === catalog) return index }
        return CatalogSearchIndex(catalog.heroes).also { indexed = catalog to it }
    }

    private fun ApplicationCall.settings(ctx: HubContext): AppSettings =
        ctx.deviceStore.read(device.id, SharedSlots.Settings) ?: AppSettings()

    private suspend fun ApplicationCall.catalogPage(ctx: HubContext) {
        val settings = settings(ctx)
        val request = CatalogRequest.parse(request.queryParameters)
        val searching = isHtmx && this.request.headers["HX-Target"] == "results"
        // `?q=&a=&sort=name` from the form reads `/heroes` in the address bar (and in a shared link).
        if ((searching || !isHtmx) && tidyUrl(request.href())) return
        val catalog = ctx.catalog.catalog
        val layout = if (settings.catalogListView) CatalogLayout.LIST else CatalogLayout.GRID
        val controls = CatalogControls(layout, request.filter, request.sort)
        // Elo and Win rate sorts need the derived hero stats; the others don't replay the log.
        val needsStats = request.sort == CatalogSort.ELO || request.sort == CatalogSort.WIN_RATE
        val records = if (needsStats) HeroRecords.current(ctx.groupStore, catalog) else null
        val state = CatalogUiState.build(
            catalog = catalog,
            index = index(catalog),
            owned = settings.ownedProducts,
            query = request.query,
            controls = controls,
            ratings = records?.heroRatings.orEmpty(),
            usage = records?.heroUsage.orEmpty(),
        )
        if (searching) {
            respondFragment {
                catalogResults(state, request)
                oobCounts(state)
            }
            return
        }
        respondPage(
            HubPage("Heroes", Tab.HEROES, styles = styles, actions = TopActions.forTab(Tab.HEROES)),
        ) {
            when (state) {
                is CatalogUiState.Ready -> catalogContent(state, request)

                is CatalogUiState.Unavailable -> emptyState(
                    if (state.error) "Couldn't load the heroes" else "No heroes yet",
                    "Hero data isn't bundled with this hub yet.",
                )

                CatalogUiState.Loading -> Unit
            }
        }
    }

    /** Grid or list: saved like the phone's `catalogListView` (only that field changes). */
    private suspend fun ApplicationCall.setView(ctx: HubContext) {
        val form = receiveParameters()
        val list = form["view"] == "list"
        ctx.deviceStore.update(device.id, SharedSlots.Settings) { (it ?: AppSettings()).copy(catalogListView = list) }
        val back = form["back"]?.takeIf { it.startsWith("$PATH?") || it == PATH } ?: PATH
        respondRedirect(back)
    }

    private suspend fun ApplicationCall.heroPage(ctx: HubContext, heroId: String) {
        val catalog = ctx.catalog.catalog
        val stats = if (catalog.hero(heroId) !=
            null
        ) {
            HeroRecords.current(ctx.groupStore, catalog).stats(heroId)
        } else {
            null
        }
        val state = stats?.let { HeroDetailUiState.of(catalog, heroId, it) } ?: HeroDetailUiState.NotFound(heroId)
        if (state !is HeroDetailUiState.Ready) {
            respondPage(HubPage("Unknown hero", back = PATH, styles = styles), HttpStatusCode.NotFound) {
                emptyState("Unknown hero", "This hero isn't in the catalog bundled with this hub.", "All heroes", PATH)
            }
            return
        }
        respondPage(HubPage(state.hero.name, back = PATH, styles = styles)) { heroDetail(state) }
    }

    private suspend fun ApplicationCall.cardViewer(ctx: HubContext, heroId: String, cardId: String) {
        val state = CardViewerUiState.of(ctx.catalog.catalog, heroId, cardId)
        val ready = state as? CardViewerUiState.Ready
        if (ready == null || ready.cards.none { it.id == cardId }) {
            respondPage(HubPage("Card", back = "$PATH/$heroId", styles = styles), HttpStatusCode.NotFound) {
                emptyState(
                    "Card not found",
                    "This card isn't in the catalog bundled with this hub.",
                    "Back",
                    "$PATH/$heroId",
                )
            }
            return
        }
        val current = ready.cards[ready.initialIndex]
        respondPage(
            HubPage(current.name, back = "$PATH/$heroId", styles = styles, scripts = listOf(VIEWER_JS)),
        ) { cardViewerContent(ready) }
    }

    private const val VIEWER_JS = "heroes/viewer.js"
}

/** The Heroes tab's URL state: search, filters, sort. */
internal data class CatalogRequest(
    val query: String,
    val filter: CatalogFilter,
    val sort: CatalogSort,
) {
    /** This state as a `/heroes?…` URL, with any part replaced. */
    fun href(query: String = this.query, filter: CatalogFilter = this.filter, sort: CatalogSort = this.sort): String {
        val params = Parameters.build {
            if (query.isNotBlank()) append("q", query)
            filter.complexities.sorted().forEach { append("c", it.toString()) }
            filter.roles.sortedBy { it.ordinal }.forEach { append("r", it.key) }
            filter.products.sorted().forEach { append("p", it) }
            filter.attackStyle?.let { append("a", it.key) }
            if (filter.includeUnowned) append("all", "1")
            if (sort != CatalogSort.NAME) append("sort", sort.key)
        }
        val encoded = params.formUrlEncode()
        return if (encoded.isEmpty()) HeroesFeature.PATH else "${HeroesFeature.PATH}?$encoded"
    }

    companion object {
        fun parse(params: Parameters): CatalogRequest = CatalogRequest(
            query = params["q"].orEmpty().take(MAX_QUERY),
            filter = CatalogFilter(
                complexities = params.getAll("c").orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
                roles = params.getAll("r").orEmpty().mapNotNull { key ->
                    Role.entries.firstOrNull { it.key == key }
                }.toSet(),
                products = params.getAll("p").orEmpty().filter { it.isNotBlank() }.toSet(),
                attackStyle = params["a"]?.let { key -> AttackStyle.entries.firstOrNull { it.key == key } },
                includeUnowned = params["all"] == "1",
            ),
            sort = params["sort"]?.let { key -> CatalogSort.entries.firstOrNull { it.key == key } } ?: CatalogSort.NAME,
        )

        private const val MAX_QUERY = 200
    }
}

internal val Role.key: String get() = name.lowercase()
internal val AttackStyle.key: String get() = name.lowercase()
internal val CatalogSort.key: String get() = name.lowercase()
