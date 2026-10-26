package dev.quinton.goa.core.domain.settings

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings

/** One product in Settings > Collection, with how many catalog heroes it brings. */
data class ProductOption(val id: String, val name: String, val released: Int?, val heroCount: Int, val owned: Boolean)

/**
 * The group's collection (Settings > Collection), shared by the phone and the web app (ADR-043).
 * [AppSettings.ownedProducts] empty means "every product"; the stored subset is never empty, and a
 * subset that covers every product is stored as "every product" again.
 */
object ProductOwnership {
    /** True when no subset is stored (empty selection = every product). */
    fun ownsEverything(settings: AppSettings): Boolean = settings.ownedProducts.isEmpty()

    /** Every catalog product in catalog order, marked owned or not, with its hero count. */
    fun options(settings: AppSettings, catalog: Catalog): List<ProductOption> {
        val counts = catalog.heroes.groupingBy { it.product }.eachCount()
        val everything = ownsEverything(settings)
        return catalog.products.map { p ->
            ProductOption(
                id = p.id,
                name = p.name,
                released = p.released,
                heroCount = counts[p.id] ?: 0,
                owned = everything || p.id in settings.ownedProducts,
            )
        }
    }

    /**
     * "I own every product". Switching it off keeps every product ticked ([allProducts]); the group
     * then unticks what it doesn't own (the pool is never empty).
     */
    fun setOwnsEverything(settings: AppSettings, everything: Boolean, allProducts: Set<String>): AppSettings = when {
        everything -> settings.copy(ownedProducts = emptySet())
        else -> settings.copy(ownedProducts = allProducts.ifEmpty { settings.ownedProducts })
    }

    /** Ticks or unticks one product; the last owned product can't be unticked. */
    fun toggle(settings: AppSettings, productId: String, allProducts: Set<String>): AppSettings {
        val current = settings.ownedProducts.ifEmpty { allProducts }
        val next = if (productId in current) current - productId else current + productId
        return when {
            next.isEmpty() -> settings
            next.containsAll(allProducts) -> settings.copy(ownedProducts = emptySet())
            else -> settings.copy(ownedProducts = next)
        }
    }
}
