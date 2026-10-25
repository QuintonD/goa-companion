package dev.quinton.goa.core.domain.settings

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.ProductKind
import dev.quinton.goa.core.model.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductOwnershipTest {
    private fun hero(id: String, product: String) =
        Hero(id, id, "", product, printing = 2, cards = emptyList(), verified = true)

    private val catalog = Catalog(
        dataVersion = 1,
        printing = 2,
        products = listOf(
            Product("core", "Core", ProductKind.BASE, released = 2022),
            Product("devoted", "Devoted", ProductKind.HERO_PACK),
            Product("arcane", "Arcane", ProductKind.HERO_PACK, released = 2025),
        ),
        heroes = listOf(
            hero("a", "core"),
            hero("b", "core"),
            hero("c", "arcane"),
        ),
    )
    private val all = setOf("core", "devoted", "arcane")

    @Test
    fun noStoredSubsetOwnsEveryProductWithItsHeroCount() {
        val options = ProductOwnership.options(AppSettings(), catalog)

        assertTrue(ProductOwnership.ownsEverything(AppSettings()))
        assertEquals(listOf("core", "devoted", "arcane"), options.map { it.id })
        assertEquals(listOf(2, 0, 1), options.map { it.heroCount })
        assertTrue(options.all { it.owned })
    }

    @Test
    fun untickingFromEverythingStoresTheRest() {
        val next = ProductOwnership.toggle(AppSettings(), "devoted", all)

        assertEquals(setOf("core", "arcane"), next.ownedProducts)
        assertEquals(listOf(true, false, true), ProductOwnership.options(next, catalog).map { it.owned })
    }

    @Test
    fun tickingTheLastMissingProductStoresEverythingAgain() {
        val next = ProductOwnership.toggle(AppSettings(ownedProducts = setOf("core", "arcane")), "devoted", all)

        assertEquals(emptySet(), next.ownedProducts)
    }

    @Test
    fun theLastOwnedProductCantBeUnticked() {
        val one = AppSettings(ownedProducts = setOf("core"))

        assertEquals(one, ProductOwnership.toggle(one, "core", all))
    }

    @Test
    fun switchingEverythingOffKeepsEveryProductTicked() {
        assertEquals(all, ProductOwnership.setOwnsEverything(AppSettings(), false, all).ownedProducts)
        assertEquals(
            emptySet(),
            ProductOwnership.setOwnsEverything(AppSettings(ownedProducts = setOf("core")), true, all).ownedProducts,
        )
    }
}
