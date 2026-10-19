package dev.quinton.goa.core.presentation.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
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

/** How a hero's attack cards target (app-derived, not an official trait; see [AttackProfile]). */
enum class AttackStyle(val label: String) {
    RANGED("Ranged attacks"),
    MIXED("Mixed attacks"),

    /** No ranged attack card: the rulebook calls these "Non-Ranged Attacks" (p.13). */
    MELEE("Non-ranged attacks"),
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

/** One card in the search results; tapping it opens the card viewer. */
data class CardResult(val card: Card, val hero: Hero, val quality: MatchQuality, val isHandicap: Boolean = false)

/** Products the user can filter by: the owned ones, or all when none are set or unowned are shown. */
fun visibleProducts(catalog: Catalog, owned: Set<String>, includeUnowned: Boolean): List<Product> =
    if (owned.isEmpty() || includeUnowned) catalog.products else catalog.products.filter { it.id in owned }

/** How many of a hero's deck Attack cards print the Ranged subtype ("Attack - Ranged"). */
data class AttackProfile(val ranged: Int, val attacks: Int) {
    /**
     * Ranged when more than half of the attack cards are ranged, non-ranged when none is, Mixed
     * otherwise; null without attack cards (unknown, never guessed). docs/DOMAIN.md explains why.
     */
    val style: AttackStyle?
        get() = when {
            attacks == 0 -> null
            ranged == 0 -> AttackStyle.MELEE
            ranged * 2 > attacks -> AttackStyle.RANGED
            else -> AttackStyle.MIXED
        }

    /** "Ranged attacks", "Ranged attacks · 5 of 6", "Non-ranged attacks", "Mixed attacks · 2 of 6 ranged". */
    val pill: String?
        get() = when (style) {
            null -> null
            AttackStyle.MELEE -> "Non-ranged attacks"
            AttackStyle.RANGED -> if (ranged == attacks) "Ranged attacks" else "Ranged attacks · $ranged of $attacks"
            AttackStyle.MIXED -> "Mixed attacks · $ranged of $attacks ranged"
        }
}

/**
 * Counts the hero's deck cards (basic, extra basic, coloured, Ultimate; not the Handicap card
 * or spellbook pages) whose primary action is Attack, and how many of them are ranged.
 */
fun Hero.attackProfile(): AttackProfile {
    val attacks = cards.filter { it.primary == PrimaryAction.ATTACK }
    return AttackProfile(ranged = attacks.count { it.ranged }, attacks = attacks.size)
}

fun Hero.attackStyle(): AttackStyle? = attackProfile().style

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
        val ordered = if (searching) {
            compareByDescending<HeroListItem> { it.hit?.quality?.ordinal ?: 0 }
                .thenByDescending { it.hit?.score ?: 0 }
                .then(bySort)
        } else {
            bySort
        }
        return items.sortedWith(ordered)
    }

    /**
     * Cards matching [query] (all of them, best first, see [CatalogSearchIndex.searchCards]),
     * limited to the heroes in [heroes] so filters and owned products apply to cards too.
     */
    fun cards(index: CatalogSearchIndex, query: String, heroes: List<HeroListItem>): List<CardResult> {
        val visible = heroes.associateBy { it.hero.id }
        return index.searchCards(query).mapNotNull { hit ->
            val item = visible[hit.heroId] ?: return@mapNotNull null
            CardResult(hit.card, item.hero, hit.quality, isHandicap = item.hero.handicap?.id == hit.card.id)
        }
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
