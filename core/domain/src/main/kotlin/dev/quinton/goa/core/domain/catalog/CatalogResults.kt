package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating

/** Options the filter sheet offers, derived from the loaded catalog and owned products. */
data class FilterOptions(
    val complexities: List<Int>,
    val roles: List<Role>,
    val products: List<Product>,
    val attackStyles: List<AttackStyle>,
    /** True when the group set owned products, so "Include heroes you don't own" is meaningful. */
    val hasOwnedProducts: Boolean,
)

/** What the Heroes tab shows for a search, filter and sort: the phone's and the web's. */
data class CatalogResults(
    /** The filter as applied (product picks no longer visible dropped). */
    val filter: CatalogFilter,
    val heroes: List<HeroListItem>,
    /** Heroes in scope before search and filters (owned products respected). */
    val totalInScope: Int,
    val options: FilterOptions,
    /** Cards matching the search, best first (empty without a query). */
    val cards: List<CardResult> = emptyList(),
    /** Heroes that would match if products the group doesn't own were included. */
    val hiddenByCollection: Int = 0,
) {
    val isFiltered: Boolean get() = heroes.size != totalInScope

    /** Cards answer the query better than any hero (e.g. a card phrase vs a hero's title). */
    val cardsFirst: Boolean get() = cardsAnswerFirst(cards, heroes)
}

/** True when the best card result is a better answer than every hero result. */
fun cardsAnswerFirst(cards: List<CardResult>, heroes: List<HeroListItem>): Boolean {
    val bestCard = cards.firstOrNull()?.quality ?: return false
    val bestHero = heroes.maxOfOrNull { it.hit?.quality ?: MatchQuality.PARTIAL } ?: return true
    return bestCard > bestHero
}

/**
 * The Heroes tab's results (`CatalogViewModel` on the phone): owned products, search, filters and
 * sort, the matching cards, and how many heroes the collection hides. Null for a catalog with no
 * heroes.
 */
@Suppress("LongParameterList")
fun catalogResults(
    catalog: Catalog,
    index: CatalogSearchIndex,
    owned: Set<String>,
    query: String,
    filter: CatalogFilter,
    sort: CatalogSort,
    ratings: Map<String, Rating> = emptyMap(),
    usage: Map<String, HeroUsage> = emptyMap(),
): CatalogResults? {
    if (catalog.heroes.isEmpty()) return null
    val products = visibleProducts(catalog, owned, filter.includeUnowned)
    // Drop product selections that are no longer visible (e.g. owned products changed).
    val visibleIds = products.mapTo(mutableSetOf()) { it.id }
    val applied = filter.copy(products = filter.products.intersect(visibleIds))
    val inScope = catalog.heroes.count { owned.isEmpty() || applied.includeUnowned || it.product in owned }
    val heroes = CatalogBrowser.browse(catalog, index, query, applied, sort, owned, ratings, usage)
    // Nothing found in the owned products: say so when other products would match.
    val hiddenByCollection = if (heroes.isEmpty() && owned.isNotEmpty() && !applied.includeUnowned) {
        CatalogBrowser.browse(catalog, index, query, applied.copy(includeUnowned = true), sort, owned).size
    } else {
        0
    }
    return CatalogResults(
        filter = applied,
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

/** The filters but "include unowned": what "Clear all" leaves. */
fun CatalogFilter.cleared(): CatalogFilter = CatalogFilter(includeUnowned = includeUnowned)

/** An applied filter shown as a removable chip; [remove] gives the filter without it. */
data class ActiveFilterChip(val key: String, val label: String, val remove: (CatalogFilter) -> CatalogFilter)

/** The applied filters as chips, in the filter sheet's order. */
fun activeFilterChips(filter: CatalogFilter, options: FilterOptions): List<ActiveFilterChip> = buildList {
    filter.complexities.sorted().forEach { level ->
        add(ActiveFilterChip("c$level", complexityLabel(level)) { it.copy(complexities = it.complexities - level) })
    }
    filter.roles.sortedBy { it.ordinal }.forEach { role ->
        add(ActiveFilterChip("r${role.name}", role.label) { it.copy(roles = it.roles - role) })
    }
    filter.attackStyle?.let { style -> add(ActiveFilterChip("a", style.label) { it.copy(attackStyle = null) }) }
    filter.products.forEach { id ->
        val name = options.products.firstOrNull { it.id == id }?.name ?: id
        add(ActiveFilterChip("p$id", name) { it.copy(products = it.products - id) })
    }
}

/** "24 heroes" or "5 of 24 heroes" over the results ([shown] of [totalInScope]). */
fun resultSummary(shown: Int, totalInScope: Int, query: String): String = when {
    query.isBlank() && shown == totalInScope -> plural(totalInScope, "hero", "heroes")
    else -> "$shown of ${plural(totalInScope, "hero", "heroes")}"
}

/** How the app derives the attack style (docs/DOMAIN.md); "Non-ranged" is the rulebook term (p.13). */
const val ATTACK_STYLE_NOTE =
    "Derived from the Ranged subtype on the hero's Attack cards: Ranged = more than half ranged, Mixed = some " +
        "but at most half ranged, Non-ranged = none ranged."

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
 * Complexity 1 of 4, Challenging. Tactician · Disabler. Core. Ranged. …".
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
 * What the result list shows, as a key: a change scrolls it back to the top (the best result first).
 * The query by its search tokens, so a change of case or punctuation that finds the same keeps the place.
 */
fun catalogResultsKey(query: String, filter: CatalogFilter, sort: CatalogSort): String =
    "${CatalogSearchIndex.tokenize(query).joinToString(" ")}|$filter|$sort"
