package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
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
import dev.quinton.goa.core.model.player.Player
import java.io.File

/** The shipped methods file, read from the app's assets (methods are data, ADR-012). */
val bundledMethods: DraftMethodsFile by lazy {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    var found: File? = null
    while (dir != null && found == null) {
        found = File(dir, "app/src/main/assets/catalog/draft-methods.json").takeIf { it.exists() }
        dir = dir.parentFile
    }
    DraftJson.decodeMethods(requireNotNull(found) { "draft-methods.json not found" }.readText())
}

fun method(id: String) = bundledMethods.methods.first { it.id == id }

val library: MethodLibrary get() = MethodLibrary.of(bundledMethods, emptyList())

/**
 * Synthetic heroes ("Test Hero N"): never real game data, which only comes from the import
 * pipeline. Same shape as core:testing's `TestCatalog` (a JVM module can't use that Android one).
 */
object FakeCatalog {
    private val products = listOf(
        Product(id = "test-core", name = "Test Core", kind = ProductKind.BASE, released = 2022),
        Product(id = "test-pack", name = "Test Pack", kind = ProductKind.HERO_PACK, released = 2025),
    )

    fun hero(index: Int): Hero {
        val id = "test-hero-$index"
        val cards = buildList {
            add(card(id, "gold", CardColor.GOLD, null))
            add(card(id, "silver", CardColor.SILVER, null))
            for (color in listOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)) {
                add(card(id, "${color.name.lowercase()}-1", color, 1))
                add(card(id, "${color.name.lowercase()}-2a", color, 2))
            }
        }
        return Hero(
            id = id,
            name = "Test Hero $index",
            title = "the Placeholder",
            product = if (index % 2 == 0) "test-core" else "test-pack",
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
            verified = true,
        )
    }

    private fun card(heroId: String, suffix: String, color: CardColor, tier: Int?) = Card(
        id = "$heroId-$suffix",
        heroId = heroId,
        name = "Card ${suffix.uppercase()}",
        color = color,
        tier = tier,
        initiative = 5 + (tier ?: 0),
        primary = PrimaryAction.ATTACK,
        values = CardValues(attack = 3, defense = 2, movement = 2),
        text = "Placeholder text.",
    )

    fun catalog(count: Int = 24) = Catalog(
        dataVersion = 1,
        printing = 2,
        products = products,
        heroes = (1..count).map(::hero),
    )
}

fun player(id: String, name: String = id.replaceFirstChar { it.uppercase() }) = Player(id, name, 0xFF3FC1B0, 0, 0L)

/** Fixed ids, seeds and time. */
class FixedEnvironment(private val seed: Long = 11L, private val now: Long = 1_000L) : DraftEnvironment {
    private var next = 0

    override fun newId(): String = "id-${next++}"

    override fun newSeed(): Long = seed

    override fun nowMillis(): Long = now
}
