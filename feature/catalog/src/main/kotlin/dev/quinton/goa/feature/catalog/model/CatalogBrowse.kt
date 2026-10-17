package dev.quinton.goa.feature.catalog.model

import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating

enum class CatalogSort(val label: String) {
    NAME("Name"),
    COMPLEXITY("Complexity"),
    PRODUCT("Product"),
    ELO("Elo"),
    WIN_RATE("Win rate"),
}

/** Attack style, taken from the hero's basic Gold (attack) card. */
enum class AttackStyle(val label: String) {
    RANGED("Ranged"),
    MELEE("Melee"),
}

/**
 * Catalog filters. Within a facet the options are OR-ed, across facets AND-ed; an empty set
 * means "any". [includeUnowned] only matters when the group has chosen owned products.
 */
data class CatalogFilter(
    val complexities: Set<Int> = emptySet(),
    val roles: Set<Role> = emptySet(),
    val products: Set<String> = emptySet(),
    val attackStyle: AttackStyle? = null,
    val includeUnowned: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(complexities.isNotEmpty(), roles.isNotEmpty(), products.isNotEmpty(), attackStyle != null)
            .count { it }
}

/** One row/tile in the catalog, with everything the UI needs precomputed. */
data class HeroListItem(
    val hero: Hero,
    val productName: String,
    val attackStyle: AttackStyle?,
    val rating: Rating?,
    val usage: HeroUsage?,
    val hit: SearchHit?,
)

/** Products the user can filter by: the owned ones, or all when none are set or unowned are shown. */
fun visibleProducts(catalog: Catalog, owned: Set<String>, includeUnowned: Boolean): List<Product> =
    if (owned.isEmpty() || includeUnowned) catalog.products else catalog.products.filter { it.id in owned }

/**
 * Ranged when the basic Gold card is ranged, melee when it isn't, null when the hero has no
 * Gold card in the data (unknown — never guessed).
 */
fun Hero.attackStyle(): AttackStyle? = cards.firstOrNull { it.color == CardColor.GOLD }
    ?.let { if (it.ranged) AttackStyle.RANGED else AttackStyle.MELEE }

/** Search → owned products → filters → sort. Pure; covered by unit tests. */
object CatalogBrowser {
    @Suppress("LongParameterList")
    fun browse(
        catalog: Catalog,
        index: CatalogSearchIndex,
        query: String,
        filter: CatalogFilter,
        sort: CatalogSort,
        ownedProducts: Set<String>,
        ratings: Map<String, Rating> = emptyMap(),
        usage: Map<String, HeroUsage> = emptyMap(),
    ): List<HeroListItem> {
        val searching = CatalogSearchIndex.tokenize(query).isNotEmpty()
        val productOrder = catalog.products.withIndex().associate { (i, p) -> p.id to i }
        val restrictToOwned = ownedProducts.isNotEmpty() && !filter.includeUnowned
        val items = index.search(query).mapNotNull { hit ->
            val hero = catalog.hero(hit.heroId) ?: return@mapNotNull null
            val style = hero.attackStyle()
            val keep = (!restrictToOwned || hero.product in ownedProducts) &&
                (filter.complexities.isEmpty() || hero.complexity in filter.complexities) &&
                (filter.roles.isEmpty() || hero.roles.any { it in filter.roles }) &&
                (filter.products.isEmpty() || hero.product in filter.products) &&
                (filter.attackStyle == null || style == filter.attackStyle)
            if (!keep) return@mapNotNull null
            HeroListItem(
                hero = hero,
                productName = catalog.product(hero.product)?.name ?: hero.product,
                attackStyle = style,
                rating = ratings[hero.id],
                usage = usage[hero.id],
                hit = hit.takeIf { searching },
            )
        }
        val bySort = comparator(sort, productOrder)
        val ordered = if (searching) compareByDescending<HeroListItem> { it.hit?.score ?: 0 }.then(bySort) else bySort
        return items.sortedWith(ordered)
    }

    private val byName: Comparator<HeroListItem> = compareBy<HeroListItem> {
        it.hero.name.lowercase()
    }.thenBy { it.hero.id }

    private fun comparator(sort: CatalogSort, productOrder: Map<String, Int>): Comparator<HeroListItem> = when (sort) {
        CatalogSort.NAME -> byName

        CatalogSort.COMPLEXITY ->
            compareBy<HeroListItem, Int?>(nullsLast()) { it.hero.complexity }.then(byName)

        CatalogSort.PRODUCT ->
            compareBy<HeroListItem> { productOrder[it.hero.product] ?: Int.MAX_VALUE }.then(byName)

        CatalogSort.ELO ->
            compareBy<HeroListItem, Double?>(nullsLast()) { item ->
                item.rating?.takeIf { it.games > 0 }?.value?.let { -it }
            }
                .then(byName)

        CatalogSort.WIN_RATE ->
            compareBy<HeroListItem, Double?>(nullsLast()) { item ->
                item.usage?.takeIf { it.matches > 0 }?.winRate?.let { -it }
            }
                .thenByDescending { it.usage?.matches ?: 0 }
                .then(byName)
    }
}
