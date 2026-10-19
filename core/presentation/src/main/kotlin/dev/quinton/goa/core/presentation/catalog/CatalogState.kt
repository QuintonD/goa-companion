package dev.quinton.goa.core.presentation.catalog

import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating

enum class CatalogLayout { GRID, LIST }

/**
 * User-controlled view settings. On the phone filter and sort survive while the Heroes tab is on
 * the back stack (on the web they live in the URL); [layout] is saved in the app settings
 * (`AppSettings.catalogListView`) so the grid/list choice persists.
 */
data class CatalogControls(
    val layout: CatalogLayout = CatalogLayout.GRID,
    val filter: CatalogFilter = CatalogFilter(),
    val sort: CatalogSort = CatalogSort.NAME,
)

/** Options the filter sheet offers, derived from the loaded catalog and owned products. */
data class FilterOptions(
    val complexities: List<Int>,
    val roles: List<Role>,
    val products: List<Product>,
    val attackStyles: List<AttackStyle>,
    /** True when the group set owned products, so "Include heroes you don't own" is meaningful. */
    val hasOwnedProducts: Boolean,
)

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    /** Catalog failed to load or contains no heroes yet. */
    data class Unavailable(val error: Boolean) : CatalogUiState

    data class Ready(
        val controls: CatalogControls,
        val heroes: List<HeroListItem>,
        /** Heroes in scope before search and filters (owned products respected). */
        val totalInScope: Int,
        val options: FilterOptions,
        /** Cards matching the search, best first (empty without a query). */
        val cards: List<CardResult> = emptyList(),
        /** Heroes that would match if products the group doesn't own were included. */
        val hiddenByCollection: Int = 0,
    ) : CatalogUiState {
        val isFiltered: Boolean get() = heroes.size != totalInScope

        /** Cards answer the query better than any hero (e.g. a card phrase vs a hero's title). */
        val cardsFirst: Boolean
            get() {
                val bestCard = cards.firstOrNull()?.quality ?: return false
                val bestHero = heroes.maxOfOrNull { it.hit?.quality ?: MatchQuality.PARTIAL } ?: return true
                return bestCard > bestHero
            }

        /** "32 heroes", or "5 of 32 heroes" when searching or filtering. */
        fun resultSummary(query: String): String = when {
            query.isBlank() && !isFiltered -> plural(totalInScope, "hero", "heroes")
            else -> "${heroes.size} of ${plural(totalInScope, "hero", "heroes")}"
        }

        /** The empty state when nothing matches (no hero and no card). */
        fun noResults(query: String): NoResults {
            val hidden = hiddenByCollection
            if (hidden > 0) {
                return NoResults(
                    title = "Not in your collection",
                    message = "${plural(hidden, "hero", "heroes")} from products you don't own " +
                        (if (hidden == 1) "matches" else "match") + ". Your collection is set in Settings.",
                    action = "Show all products",
                    showAllProducts = true,
                )
            }
            val filtered = controls.filter.activeCount > 0
            return NoResults(
                title = "No heroes match",
                message = when {
                    query.isBlank() -> "No hero fits all of these filters. Remove one to widen the search."
                    filtered -> "Nothing matches “${query.trim()}” with these filters. Try another word or clear them."
                    else -> "Nothing matches “${query.trim()}”. Try another word, or fewer words."
                },
                action = if (filtered) "Clear search and filters" else "Clear search",
                showAllProducts = false,
            )
        }
    }

    /** No results: [action] clears the search (and filters), or with [showAllProducts] includes unowned products. */
    data class NoResults(val title: String, val message: String, val action: String, val showAllProducts: Boolean)

    companion object {
        /**
         * The Heroes tab for [query] and [controls]: owned products, search, filters, sort, card
         * results, and how many heroes another product would add. [ratings] and [usage] are the
         * derived hero stats (for the Elo and Win rate sorts).
         */
        @Suppress("LongParameterList")
        fun build(
            catalog: Catalog,
            index: CatalogSearchIndex,
            owned: Set<String>,
            query: String,
            controls: CatalogControls,
            ratings: Map<String, Rating> = emptyMap(),
            usage: Map<String, HeroUsage> = emptyMap(),
        ): CatalogUiState {
            if (catalog.heroes.isEmpty()) return Unavailable(error = false)
            val products = visibleProducts(catalog, owned, controls.filter.includeUnowned)
            // Drop product selections that are no longer visible (e.g. owned products changed).
            val visibleIds = products.mapTo(mutableSetOf()) { it.id }
            val keptProducts = controls.filter.products.intersect(visibleIds)
            val filter = controls.filter.copy(products = keptProducts)
            val inScope = catalog.heroes.count { owned.isEmpty() || filter.includeUnowned || it.product in owned }
            val heroes = CatalogBrowser.browse(
                catalog = catalog,
                index = index,
                query = query,
                filter = filter,
                sort = controls.sort,
                ownedProducts = owned,
                ratings = ratings,
                usage = usage,
            )
            // Nothing found in the owned products: say so when other products would match.
            val hiddenByCollection = if (heroes.isEmpty() && owned.isNotEmpty() && !filter.includeUnowned) {
                CatalogBrowser.browse(catalog, index, query, filter.copy(includeUnowned = true), controls.sort, owned)
                    .size
            } else {
                0
            }
            return Ready(
                controls = controls.copy(filter = filter),
                heroes = heroes,
                cards = CatalogBrowser.cards(index, query, heroes),
                hiddenByCollection = hiddenByCollection,
                totalInScope = inScope,
                options = FilterOptions(
                    complexities = catalog.heroes.mapNotNull { it.complexity }.distinct().sorted(),
                    roles = Role.entries.filter { role -> catalog.heroes.any { role in it.roles } },
                    products = products,
                    attackStyles = AttackStyle.entries,
                    hasOwnedProducts = owned.isNotEmpty(),
                ),
            )
        }
    }
}

/** The filters cleared, keeping "Include products you don't own". */
fun CatalogFilter.cleared(): CatalogFilter = CatalogFilter(includeUnowned = includeUnowned)

/** One active filter shown as a removable chip under the search field. */
data class ActiveFilter(val key: String, val label: String, val remove: (CatalogFilter) -> CatalogFilter)

/** The active filters as chips, in the filter sheet's order. */
fun activeFilters(filter: CatalogFilter, options: FilterOptions): List<ActiveFilter> = buildList {
    filter.complexities.sorted().forEach { level ->
        add(ActiveFilter("c$level", complexityLabel(level)) { it.copy(complexities = it.complexities - level) })
    }
    filter.roles.sortedBy { it.ordinal }.forEach { role ->
        add(ActiveFilter("r${role.name}", role.label) { it.copy(roles = it.roles - role) })
    }
    filter.attackStyle?.let { style -> add(ActiveFilter("a", style.label) { it.copy(attackStyle = null) }) }
    filter.products.forEach { id ->
        val name = options.products.firstOrNull { it.id == id }?.name ?: id
        add(ActiveFilter("p$id", name) { it.copy(products = it.products - id) })
    }
}

/** How the app derives the attack style (docs/DOMAIN.md); "Non-ranged" is the rulebook term (p.13). */
const val ATTACK_STYLE_NOTE =
    "Derived from the Ranged subtype on the hero's Attack cards: Ranged = more than half ranged, Mixed = some " +
        "but at most half ranged, Non-ranged = none ranged."

/** The filter sheet's button: "Show 5 heroes", or "No heroes match". */
fun showHeroesLabel(resultCount: Int): String =
    if (resultCount == 0) "No heroes match" else "Show ${plural(resultCount, "hero", "heroes")}"

/** The search field's hint. */
const val CATALOG_SEARCH_HINT = "Search heroes & cards"

/** Official roles only ("Disabler · Pusher"); ranged is shown separately so it never reads as a role. */
fun HeroListItem.traitsLine(): String = hero.roles.joinToString(" · ") { it.label }

/** "Ranged" when most of the hero's Attack cards are ranged — an app-derived trait, not a role. */
val HeroListItem.rangedLabel: String?
    get() = if (attackStyle == AttackStyle.RANGED) "Ranged" else null

/** Secondary line shown when sorting by a stat: "Elo 1268 · 9 games" or "64% wins · 11 games". */
fun HeroListItem.metricLine(sort: CatalogSort): String? = when (sort) {
    CatalogSort.ELO -> rating?.takeIf { it.games > 0 }
        ?.let { "Elo ${elo(it.value)} · ${plural(it.games, "game")}${if (it.provisional) " · provisional" else ""}" }
        ?: "No rated games"

    CatalogSort.WIN_RATE -> usage?.takeIf { it.matches > 0 }
        ?.let { "${percent(it.winRate)} wins · ${plural(it.matches, "game")}" }
        ?: "No games logged"

    else -> null
}

/** Why a search matched when it wasn't the name: "Card: Tidal Blast". */
fun HeroListItem.matchHint(): String? {
    val hit = hit ?: return null
    return when (hit.field) {
        MatchField.CARD_NAME -> hit.card?.let { "Card: ${it.name}" }
        MatchField.CARD_TEXT -> hit.card?.let { "Card text: ${it.name}" }
        MatchField.ALIAS -> "Matches an alias"
        else -> null
    }
}

/**
 * What a screen reader says for a tile or row, in reading order: "Arien, the Tidemaster.
 * Complexity 1 of 4, Challenging. Tactician · Disabler. Core. Ranged. …". Set on the tile itself
 * so a tile cut off at the edge of the screen (its texts scrolled out of view) is still labelled.
 */
fun HeroListItem.spokenLabel(sort: CatalogSort): String = listOfNotNull(
    "${hero.name}, ${hero.title}",
    hero.complexity?.let { c -> "Complexity $c of 4" + (complexityName(c)?.let { ", $it" } ?: "") },
    traitsLine().ifEmpty { null },
    productName,
    rangedLabel,
    if (hero.verified) null else "Unverified data",
    metricLine(sort),
    matchHint(),
).joinToString(". ")

/**
 * The paragraph of card [text] (markup) that contains the query, so the result shows the
 * words that matched; the first paragraph when the match is in the card's name. A lead-in such
 * as "Choose one —" says nothing on its own, so it comes with the paragraphs after it (the
 * options; the row shows as many lines as fit).
 */
fun matchingParagraph(text: String, query: String): String {
    val paragraphs = text.split('\n').filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) return ""
    val tokens = CatalogSearchIndex.tokenize(query)
    val plain = paragraphs.map { CatalogSearchIndex.normalize(CardText.plain(it)).trim() }
    val phrase = tokens.joinToString(" ")
    val index = if (tokens.isEmpty()) {
        0
    } else {
        plain.indexOfFirst { phrase in it }.takeIf { it >= 0 }
            ?: plain.indexOfFirst { p -> tokens.any { it in p } }.takeIf { it >= 0 }
            ?: 0
    }
    val leadIn = plain[index].endsWith('—') || plain[index].endsWith(':') || plain[index].endsWith("--")
    return if (leadIn && index < paragraphs.lastIndex) {
        paragraphs.subList(index, paragraphs.size).joinToString("\n")
    } else {
        paragraphs[index]
    }
}
