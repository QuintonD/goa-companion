package dev.quinton.goa.core.presentation.settings

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings

/** A product in Settings → Collection: its heroes count, and whether the group owns it. */
data class ProductOption(val id: String, val name: String, val released: Int?, val heroCount: Int, val owned: Boolean)

/**
 * Settings → Collection (owned products), shared by the phone's Settings and the hub's `/settings`.
 * An empty `ownedProducts` means every product; the pool is never empty.
 */
object OwnedProducts {
    /** No subset is stored: every product is owned. */
    fun ownsEverything(settings: AppSettings): Boolean = settings.ownedProducts.isEmpty()

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

    /** A product row's detail line, "2022 · 7 heroes"; null when there is nothing to say. */
    fun detail(option: ProductOption): String? = listOfNotNull(
        option.released?.toString(),
        if (option.heroCount > 0) "${option.heroCount} heroes" else null,
    ).joinToString(" · ").ifEmpty { null }

    /** The "I own every product" subtitle. */
    fun summary(options: List<ProductOption>, ownsEverything: Boolean): String = if (ownsEverything) {
        "All heroes are available in drafts and filters"
    } else {
        "${options.count { it.owned }} of ${options.size} products owned"
    }

    /**
     * "I own every product" on or off. Switching it off keeps every product ticked; the group then
     * unticks what it doesn't own (the pool is never empty).
     */
    fun setOwnsEverything(settings: AppSettings, all: Set<String>, everything: Boolean): AppSettings = when {
        everything -> settings.copy(ownedProducts = emptySet())
        else -> settings.copy(ownedProducts = all.ifEmpty { settings.ownedProducts })
    }

    /** Ticks or unticks one product: at least one stays owned; all of them ticked is "everything". */
    fun toggle(settings: AppSettings, all: Set<String>, id: String): AppSettings {
        val current = settings.ownedProducts.ifEmpty { all }
        val next = if (id in current) current - id else current + id
        return when {
            next.isEmpty() -> settings
            next.containsAll(all) -> settings.copy(ownedProducts = emptySet())
            else -> settings.copy(ownedProducts = next)
        }
    }

    /**
     * A submitted Collection form (the hub's `/settings` posts the switch and every ticked product),
     * with the phone's rules: switching "everything" off keeps every product ticked, the last
     * product can't be unticked, all of them ticked is "everything"; unknown ids are dropped.
     */
    fun fromForm(settings: AppSettings, all: Set<String>, everything: Boolean, ticked: Set<String>): AppSettings {
        val owned = ticked intersect all
        return when {
            everything -> setOwnsEverything(settings, all, true)
            ownsEverything(settings) -> setOwnsEverything(settings, all, false)
            owned.isEmpty() -> settings
            owned.containsAll(all) -> settings.copy(ownedProducts = emptySet())
            else -> settings.copy(ownedProducts = owned)
        }
    }
}
