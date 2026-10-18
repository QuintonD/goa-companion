package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardValues
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.ProductKind
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.SecondaryAction

/**
 * Synthetic fixture for tests (a copy of core:testing's, which is an Android module). Names and
 * values are deliberately fake ("Test Hero N") — never real game data, which only comes from the
 * import pipeline.
 */
object TestCatalog {
    val products = listOf(
        Product(id = "test-core", name = "Test Core", kind = ProductKind.BASE, released = 2022),
        Product(id = "test-pack", name = "Test Pack", kind = ProductKind.HERO_PACK, released = 2025),
    )

    fun hero(index: Int, product: String = if (index % 2 == 0) "test-core" else "test-pack"): Hero {
        val id = "test-hero-$index"
        val cards = buildList {
            add(card(id, "gold", CardColor.GOLD, null, PrimaryAction.ATTACK))
            add(card(id, "silver", CardColor.SILVER, null, PrimaryAction.SKILL))
            for (color in listOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)) {
                add(card(id, "${color.name.lowercase()}-1", color, 1, PrimaryAction.ATTACK))
                for (tier in 2..3) {
                    for (variant in listOf("a", "b")) {
                        add(card(id, "${color.name.lowercase()}-$tier$variant", color, tier, PrimaryAction.SKILL))
                    }
                }
            }
            add(card(id, "purple-4", CardColor.PURPLE, 4, PrimaryAction.SKILL))
        }
        return Hero(
            id = id,
            name = "Test Hero $index",
            title = "the Placeholder",
            product = product,
            complexity = (index % 4) + 1,
            stats = HeroStats(
                attack = listOf(3, 6),
                defense = listOf(4, 7),
                initiative = listOf(2, 5),
                movement = listOf(3, 4),
            ),
            roles = listOf(Role.entries[index % Role.entries.size], Role.entries[(index + 3) % Role.entries.size]),
            printing = 2,
            cards = cards,
            handicap = card(id, "handicap", CardColor.OTHER, null, PrimaryAction.SKILL),
            verified = index % 3 != 0,
        )
    }

    private fun card(heroId: String, suffix: String, color: CardColor, tier: Int?, primary: PrimaryAction) = Card(
        id = "$heroId-$suffix",
        heroId = heroId,
        name = "Card ${suffix.uppercase()}",
        color = color,
        tier = tier,
        initiative = 5 + (tier ?: 0),
        primary = primary,
        secondary = listOf(SecondaryAction.MOVEMENT, SecondaryAction.DEFENSE),
        values = CardValues(attack = 3, defense = 2, movement = 2, range = if (tier == 2) 2 else null),
        ranged = tier == 2,
        text = "Placeholder text with an {attack} icon. ~(Reminder text.)",
    )

    val heroes: List<Hero> = (1..12).map { hero(it) }

    val catalog = Catalog(dataVersion = 1, printing = 2, products = products, heroes = heroes)
}
