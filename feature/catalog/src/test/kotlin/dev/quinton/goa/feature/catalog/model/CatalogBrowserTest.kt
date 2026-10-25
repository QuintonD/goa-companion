package dev.quinton.goa.feature.catalog.model

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.catalog.AttackProfile
import dev.quinton.goa.core.domain.catalog.AttackStyle
import dev.quinton.goa.core.domain.catalog.CatalogBrowser
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogSearchIndex
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.HeroListItem
import dev.quinton.goa.core.domain.catalog.MatchField
import dev.quinton.goa.core.domain.catalog.attackProfile
import dev.quinton.goa.core.domain.catalog.attackStyle
import dev.quinton.goa.core.domain.catalog.visibleProducts
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.testing.TestCatalog
import org.junit.Test

class CatalogBrowserTest {
    private val catalog = TestCatalog.catalog.copy(
        heroes = TestCatalog.heroes.map { hero ->
            // Hero 5's Attack cards are all ranged so the Ranged/Melee filter has both kinds.
            if (hero.id != "test-hero-5") {
                hero
            } else {
                hero.copy(
                    cards = hero.cards.map {
                        if (it.primary ==
                            PrimaryAction.ATTACK
                        ) {
                            it.copy(ranged = true)
                        } else {
                            it
                        }
                    },
                )
            }
        },
    )
    private val index = CatalogSearchIndex(catalog.heroes)

    private fun browse(
        query: String = "",
        filter: CatalogFilter = CatalogFilter(),
        sort: CatalogSort = CatalogSort.NAME,
        owned: Set<String> = emptySet(),
        ratings: Map<String, Rating> = emptyMap(),
        usage: Map<String, HeroUsage> = emptyMap(),
        source: Catalog = catalog,
    ) = CatalogBrowser.browse(source, index, query, filter, sort, owned, ratings, usage)

    private fun List<HeroListItem>.ids() = map { it.hero.id }

    @Test
    fun noFiltersShowsEveryHeroSortedByName() {
        val items = browse()
        assertThat(items).hasSize(12)
        assertThat(items.map { it.hero.name }).isInOrder(compareBy<String> { it.lowercase() })
        assertThat(items.first().productName).isEqualTo("Test Pack")
    }

    @Test
    fun ownedProductsLimitTheCatalogUnlessUnownedAreIncluded() {
        val owned = setOf("test-core")
        assertThat(browse(owned = owned).map { it.hero.product }.toSet()).containsExactly("test-core")
        assertThat(browse(owned = owned, filter = CatalogFilter(includeUnowned = true))).hasSize(12)
    }

    @Test
    fun complexityRoleAndProductFiltersCombine() {
        val byComplexity = browse(filter = CatalogFilter(complexities = setOf(1, 2)))
        assertThat(byComplexity.map { it.hero.complexity }.toSet()).containsExactly(1, 2)

        val roleA = TestCatalog.hero(1).roles.first()
        val roleB = TestCatalog.hero(2).roles.first()
        val byRole = browse(filter = CatalogFilter(roles = setOf(roleA, roleB)))
        assertThat(byRole.all { item -> item.hero.roles.any { it == roleA || it == roleB } }).isTrue()
        assertThat(byRole.ids()).containsAtLeast("test-hero-1", "test-hero-2")

        val combined = browse(filter = CatalogFilter(complexities = setOf(2), products = setOf("test-pack")))
        assertThat(combined.all { it.hero.complexity == 2 && it.hero.product == "test-pack" }).isTrue()
        assertThat(combined).isNotEmpty()
    }

    @Test
    fun attackStyleComesFromTheAttackCards() {
        assertThat(
            browse(filter = CatalogFilter(attackStyle = AttackStyle.RANGED)).ids(),
        ).containsExactly("test-hero-5")
        assertThat(browse(filter = CatalogFilter(attackStyle = AttackStyle.MELEE))).hasSize(11)
        val noAttacks = TestCatalog.hero(1).let { h ->
            h.copy(
                cards = h.cards.filter {
                    it.primary !=
                        PrimaryAction.ATTACK
                },
            )
        }
        assertThat(noAttacks.attackStyle()).isNull()
    }

    @Test
    fun attackStyleIgnoresTheGoldCardAlone() {
        // A sniper whose Gold card is a skill (or a melee attack) is still ranged; one ranged
        // Gold card among melee attacks is mixed, not ranged.
        val hero = TestCatalog.hero(1)
        fun with(rangedIds: Set<String>, goldPrimary: PrimaryAction = PrimaryAction.ATTACK) = hero.copy(
            cards = hero.cards.map {
                val card = if (it.color == CardColor.GOLD) it.copy(primary = goldPrimary) else it
                card.copy(ranged = card.primary == PrimaryAction.ATTACK && card.id in rangedIds)
            },
        )
        val colouredAttacks = setOf("test-hero-1-red-1", "test-hero-1-green-1", "test-hero-1-blue-1")
        assertThat(with(colouredAttacks, goldPrimary = PrimaryAction.SKILL).attackProfile())
            .isEqualTo(AttackProfile(ranged = 3, attacks = 3))
        assertThat(with(colouredAttacks, goldPrimary = PrimaryAction.SKILL).attackStyle()).isEqualTo(AttackStyle.RANGED)
        assertThat(with(colouredAttacks).attackStyle()).isEqualTo(AttackStyle.RANGED) // 3 of 4
        assertThat(with(setOf("test-hero-1-gold")).attackStyle()).isEqualTo(AttackStyle.MIXED) // 1 of 4
        assertThat(with(setOf("test-hero-1-gold", "test-hero-1-red-1")).attackStyle())
            .isEqualTo(AttackStyle.MIXED) // half is not more than half
        assertThat(with(emptySet()).attackStyle()).isEqualTo(AttackStyle.MELEE)
        assertThat(AttackProfile(ranged = 5, attacks = 6).pill).isEqualTo("Ranged attacks · 5 of 6")
        assertThat(AttackProfile(ranged = 0, attacks = 0).pill).isNull()
    }

    @Test
    fun activeCountIgnoresOwnedToggle() {
        assertThat(CatalogFilter(includeUnowned = true).activeCount).isEqualTo(0)
        assertThat(CatalogFilter(roles = setOf(Role.MELEE), attackStyle = AttackStyle.RANGED).activeCount).isEqualTo(2)
    }

    @Test
    fun sortsByComplexityThenName() {
        val items = browse(sort = CatalogSort.COMPLEXITY)
        assertThat(items.map { it.hero.complexity ?: 0 }).isInOrder()
    }

    @Test
    fun sortsByProductInCatalogOrder() {
        val items = browse(sort = CatalogSort.PRODUCT)
        assertThat(items.take(6).map { it.hero.product }.toSet()).containsExactly("test-core")
        assertThat(items.drop(6).map { it.hero.product }.toSet()).containsExactly("test-pack")
    }

    @Test
    fun sortsByEloWithUnratedHeroesLast() {
        val ratings = mapOf(
            "test-hero-3" to Rating(1310.0, games = 12, wins = 8, provisional = false),
            "test-hero-8" to Rating(1250.0, games = 4, wins = 3, provisional = true),
            "test-hero-1" to Rating(1200.0, games = 0, wins = 0, provisional = true),
        )
        val items = browse(sort = CatalogSort.ELO, ratings = ratings)
        assertThat(items.take(2).ids()).containsExactly("test-hero-3", "test-hero-8").inOrder()
        assertThat(items.first().rating?.value).isEqualTo(1310.0)
    }

    @Test
    fun sortsByWinRateThenGames() {
        fun usage(id: String, matches: Int, wins: Int) =
            id to
                HeroUsage(
                    id,
                    matches,
                    wins,
                    bans = 0,
                    pickRate = 0.5,
                    winRate = wins.toDouble() / matches,
                    banRate = 0.0,
                )
        val usage = mapOf(usage("test-hero-4", 4, 2), usage("test-hero-6", 10, 5), usage("test-hero-9", 3, 3))
        val items = browse(sort = CatalogSort.WIN_RATE, usage = usage)
        assertThat(items.take(3).ids()).containsExactly("test-hero-9", "test-hero-6", "test-hero-4").inOrder()
    }

    @Test
    fun searchOrdersByRelevanceAndKeepsTheHit() {
        val named = TestCatalog.hero(11).copy(name = "Tidecaller")
        val texted = TestCatalog.hero(2).let { h ->
            h.copy(cards = h.cards.mapIndexed { i, c -> if (i == 0) c.copy(text = "Call the tidecaller.") else c })
        }
        val source = catalog.copy(heroes = listOf(texted, named))
        val items = CatalogBrowser.browse(
            source,
            CatalogSearchIndex(source.heroes),
            "tidecaller",
            CatalogFilter(),
            CatalogSort.NAME,
            emptySet(),
        )
        assertThat(items.ids()).containsExactly(named.id, texted.id).inOrder()
        assertThat(items.last().hit?.field).isEqualTo(MatchField.CARD_TEXT)
        assertThat(browse().all { it.hit == null }).isTrue()
    }

    @Test
    fun visibleProductsRespectOwnership() {
        assertThat(visibleProducts(catalog, emptySet(), false)).hasSize(2)
        assertThat(visibleProducts(catalog, setOf("test-pack"), false).map { it.id }).containsExactly("test-pack")
        assertThat(visibleProducts(catalog, setOf("test-pack"), true)).hasSize(2)
    }

    @Test
    fun emptyCatalogBrowsesToNothing() {
        val items = CatalogBrowser.browse(
            Catalog.EMPTY,
            CatalogSearchIndex(emptyList()),
            "anything",
            CatalogFilter(),
            CatalogSort.NAME,
            emptySet(),
        )
        assertThat(items).isEmpty()
    }
}
