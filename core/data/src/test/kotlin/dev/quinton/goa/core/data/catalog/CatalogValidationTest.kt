package dev.quinton.goa.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * Validates the bundled catalog (docs/DOMAIN.md "Hero JSON"): strict schema, permanent unique
 * IDs, existing asset files, the 32-hero roster, deck composition and the `verified` rule.
 */
class CatalogValidationTest {
    private val catalog: Catalog by lazy { CatalogLoader.load(RepoAssets.assets) }
    private val heroes: List<Hero> get() = catalog.heroes

    private fun allCards(): List<Card> = heroes.flatMap { it.allCards }

    @Test
    fun `every catalog file parses with unknown keys rejected`() {
        val heroDir = RepoAssets.file("catalog/heroes")
        val files = heroDir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertThat(files).isNotEmpty()
        files.forEach { file ->
            // Strict decoding throws on any key the model doesn't know.
            CatalogLoader.json.decodeFromString(Hero.serializer(), file.readText())
        }
        CatalogLoader.readIndex(RepoAssets.assets)
    }

    @Test
    fun `strict parser rejects an unknown key`() {
        val text = RepoAssets.file("catalog/heroes/arien.json").readText()
        val withExtra = text.replaceFirst("{", "{\n  \"unexpected\": 1,")
        val result = runCatching { CatalogLoader.json.decodeFromString(Hero.serializer(), withExtra) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `index lists exactly the hero files and all 32 heroes`() {
        val index = CatalogLoader.readIndex(RepoAssets.assets)
        val files = RepoAssets.file("catalog/heroes").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { it.nameWithoutExtension }.toSet()
        assertThat(index.heroes.toSet()).isEqualTo(files)
        assertThat(index.heroes).containsNoDuplicates()
        assertThat(index.heroes).containsExactlyElementsIn(ROSTER.keys)
        assertThat(index.printing).isEqualTo(2)
        assertThat(index.dataVersion).isAtLeast(1)
    }

    @Test
    fun `roster matches docs - product, complexity and title`() {
        heroes.forEach { hero ->
            val (product, complexity, title) = ROSTER.getValue(hero.id)
            assertWithMessage(hero.id).that(hero.product).isEqualTo(product)
            assertWithMessage(hero.id).that(hero.complexity).isEqualTo(complexity)
            assertWithMessage(hero.id).that(hero.title).isEqualTo(title)
            assertWithMessage(hero.id).that(hero.printing).isEqualTo(2)
        }
    }

    @Test
    fun `products are known and referenced products exist`() {
        val ids = catalog.products.map { it.id }
        assertThat(ids).containsExactly("core", "devoted", "defiant", "renowned", "arcane", "wayward").inOrder()
        heroes.forEach { assertWithMessage(it.id).that(ids).contains(it.product) }
        catalog.products.forEach { assertWithMessage(it.id).that(it.bggId).isNotNull() }
    }

    @Test
    fun `hero and card ids are unique, kebab-case and owned by their hero`() {
        val ids = allCards().map { it.id }
        assertThat(ids).containsNoDuplicates()
        heroes.forEach { hero ->
            assertThat(hero.id).matches(KEBAB)
            hero.allCards.forEach { card ->
                assertWithMessage(card.id).that(card.id).matches(KEBAB)
                assertWithMessage(card.id).that(card.heroId).isEqualTo(hero.id)
                assertWithMessage(card.id).that(card.id).startsWith(hero.id + "-")
            }
        }
    }

    @Test
    fun `standard deck is 18 cards plus a handicap unless listed in deckExceptions`() {
        heroes.forEach { hero ->
            val expected = standardDeckIds(hero.id) + DECK_EXCEPTIONS[hero.id]?.extraDeckIds.orEmpty()
            assertWithMessage(hero.id).that(hero.cards.map { it.id }).containsExactlyElementsIn(expected)
            assertWithMessage(hero.id).that(hero.handicap?.id).isEqualTo("${hero.id}-handicap")
            assertWithMessage(hero.id).that(hero.extras.size).isEqualTo(DECK_EXCEPTIONS[hero.id]?.extras ?: 0)
        }
        assertThat(heroes.first { it.id == "takahide" }.let { it.cards.size + 1 }).isEqualTo(21)
        assertThat(heroes.first { it.id == "gydion" }.extras).hasSize(22)
    }

    @Test
    fun `card colour and tier agree with the card id`() {
        heroes.forEach { hero ->
            hero.cards.forEach { card ->
                val suffix = card.id.removePrefix(hero.id + "-")
                when {
                    suffix == "gold" || suffix.startsWith("gold-extra-") -> {
                        assertWithMessage(card.id).that(card.color).isEqualTo(CardColor.GOLD)
                        assertWithMessage(card.id).that(card.tier).isNull()
                    }

                    suffix == "silver" -> {
                        assertWithMessage(card.id).that(card.color).isEqualTo(CardColor.SILVER)
                        assertWithMessage(card.id).that(card.tier).isNull()
                    }

                    suffix == "purple-4" -> {
                        assertWithMessage(card.id).that(card.color).isEqualTo(CardColor.PURPLE)
                        assertWithMessage(card.id).that(card.tier).isEqualTo(4)
                    }

                    else -> {
                        val (colour, tier) = suffix.split('-')
                        assertWithMessage(card.id).that(card.color.name.lowercase()).isEqualTo(colour)
                        assertWithMessage(card.id).that(card.tier).isEqualTo(tier.first().digitToInt())
                    }
                }
            }
            // Handicap cards keep their printed frame: a weaker version of a basic card (p.20).
            assertWithMessage(hero.id).that(hero.handicap?.color).isAnyOf(CardColor.GOLD, CardColor.SILVER)
            assertWithMessage(hero.id).that(hero.handicap?.tier).isNull()
            // Spellbook pages keep their printed colour too (gold cantrips … purple Wish).
            hero.extras.forEach { assertWithMessage(it.id).that(it.color).isNotEqualTo(CardColor.OTHER) }
        }
        assertThat(heroes.mapNotNull { it.handicap }.filter { it.color == CardColor.SILVER }.map { it.id })
            .containsExactly("takahide-handicap")
        heroes.first { it.id == "gydion" }.extras.groupingBy { it.color }.eachCount().let { counts ->
            assertThat(counts).containsExactly(
                CardColor.GOLD, 3, CardColor.RED, 6, CardColor.GREEN, 6, CardColor.BLUE, 6, CardColor.PURPLE, 1,
            )
        }
    }

    @Test
    fun `values are plausible and consistent with the printed actions`() {
        val spells = heroes.flatMap { it.extras }.map { it.id }.toSet()
        allCards().forEach { card ->
            val v = card.values
            listOfNotNull(v.attack, v.defense, v.movement, v.range, v.radius).forEach {
                assertWithMessage(card.id).that(it).isIn(0..12)
            }
            // A printed `+` belongs to a printed value.
            assertWithMessage("${card.id} plus").that(v.plus).containsNoDuplicates()
            v.plus.forEach { assertWithMessage("${card.id} plus $it").that(v.get(it)).isNotNull() }
            card.initiative?.let { assertWithMessage(card.id).that(it).isIn(1..13) }
            if (card.block) assertWithMessage(card.id).that(card.primary).isEqualTo(PrimaryAction.DEFENSE)
            if (card.ranged) assertWithMessage(card.id).that(v.range).isNotNull()
            if (v.range != null) assertWithMessage(card.id).that(v.radius).isNull()
            if (card.primary == PrimaryAction.ATTACK) assertWithMessage(card.id).that(v.attack).isNotNull()
            // Spellbook pages are not deck cards: no item, no initiative.
            val deck = card.color in COLOURED && card.tier != null && card.id !in spells
            if (deck && card.tier!! >= 2 && card.tier!! <= 3) {
                assertWithMessage("${card.id} item").that(card.item).isNotNull()
            } else {
                assertWithMessage("${card.id} item").that(card.item).isNull()
            }
            if (card.color != CardColor.PURPLE && card.id !in spells) {
                assertWithMessage("${card.id} initiative").that(card.initiative).isNotNull()
            }
        }
    }

    @Test
    fun `hero profile stats, roles and complexity are within the official ranges`() {
        heroes.forEach { hero ->
            listOf(hero.stats.attack, hero.stats.defense, hero.stats.initiative, hero.stats.movement).forEach { stat ->
                assertWithMessage(hero.id).that(stat).isNotNull()
                val (base, max) = stat!!
                assertWithMessage(hero.id).that(stat).hasSize(2)
                assertWithMessage(hero.id).that(base).isIn(1..8)
                assertWithMessage(hero.id).that(max).isIn(base..8)
            }
            assertWithMessage(hero.id).that(hero.roles.size).isIn(2..6)
            assertWithMessage(hero.id).that(hero.roles).containsNoDuplicates()
            assertWithMessage(hero.id).that(hero.complexity).isIn(1..4)
        }
    }

    @Test
    fun `card text uses only the app markup and known icons`() {
        allCards().forEach { card ->
            assertWithMessage(card.id).that(card.text).isNotEmpty()
            assertWithMessage(card.id).that(card.text).doesNotContain("::")
            assertWithMessage(card.id).that(card.text).doesNotContain("\n\n")
            assertWithMessage(card.id).that(card.text.count { it == '(' }).isEqualTo(card.text.count { it == ')' })
            val segments = CardText.flatten(CardText.parse(card.text))
            segments.filterIsInstance<CardText.Segment.Icon>().forEach {
                assertWithMessage("${card.id} icon").that(CardText.ICONS).contains(it.name)
            }
            // Every `**` is a balanced bold marker within one paragraph, never literal text.
            segments.filterIsInstance<CardText.Segment.Text>().forEach {
                assertWithMessage("${card.id} bold").that(it.value).doesNotContain(CardText.STRONG)
            }
            segments.filterIsInstance<CardText.Segment.Reminder>().forEach {
                assertWithMessage("${card.id} reminder").that(it.value).doesNotContain(CardText.STRONG)
            }
        }
    }

    @Test
    fun `every referenced asset exists and every bundled image is referenced`() {
        val referenced = heroes.flatMap { hero -> listOfNotNull(hero.portrait) + hero.allCards.mapNotNull { it.image } }
        referenced.forEach { path ->
            assertWithMessage(path).that(RepoAssets.file(path).isFile).isTrue()
            assertWithMessage(path).that(path).endsWith(".webp")
        }
        val bundled = listOf("official/cards", "official/heroes").flatMap { dir ->
            RepoAssets.file(dir).walkTopDown().filter { it.isFile && it.extension == "webp" }
                .map { it.relativeTo(RepoAssets.assetsDir).invariantSeparatorsPath }.toList()
        }
        assertThat(bundled).containsExactlyElementsIn(referenced)
        assertThat(RepoAssets.file("official/SOURCES.md").isFile).isTrue()
    }

    @Test
    fun `every hero has a portrait and every card an image`() {
        heroes.forEach { hero ->
            assertWithMessage(hero.id).that(hero.portrait).isEqualTo("official/heroes/${hero.id}/portrait.webp")
            hero.allCards.forEach { assertWithMessage(it.id).that(it.image).isEqualTo("official/cards/${it.id}.webp") }
        }
    }

    @Test
    fun `official images stay within the size budget`() {
        val total = RepoAssets.file("official").walkTopDown().filter { it.isFile }.sumOf(File::length)
        assertThat(total).isLessThan(80L * 1024 * 1024)
    }

    @Test
    fun `verified heroes were cross-checked against both sources or the card images`() {
        val overrides = Json.parseToJsonElement(
            File(RepoAssets.root, "tools/catalog-import/overrides.json").readText(),
        ).jsonObject
        val imageChecked = (overrides["imageChecks"] as? JsonObject)?.keys.orEmpty()
        heroes.filter { it.verified }.forEach { hero ->
            val sources = hero.sources.map { it.name }
            assertWithMessage("${hero.id} text source").that(sources).contains("GoA2 FAQ")
            val numbersChecked = "colefin8/guards-of-atlantis-ii-data" in sources || hero.id in imageChecked
            assertWithMessage("${hero.id} number check").that(numbersChecked).isTrue()
        }
    }

    @Test
    fun `printed plus values and bold keywords read from the card images survive the import`() {
        fun card(id: String) = allCards().single { it.id == id }
        // Card scans read 2026-10-17: Range 1+, Defense 3+ (Radius 2), Movement 2+.
        assertThat(card("xargatha-red-2b").values.plus).containsExactly(Stat.RANGE)
        assertThat(card("dodger-blue-1").values.plus).containsExactly(Stat.DEFENSE)
        assertThat(card("bain-green-2b").values.plus).containsExactly(Stat.MOVEMENT)
        assertThat(card("xargatha-blue-1").text).startsWith("**Next turn:** ")
    }

    /**
     * Rulebook p.14: a "!" Defense card blocks "by fulfilling a Block condition in the card text".
     * The printed "!" is kept as data; the one "!" card without a condition is Takahide's Handicap
     * (scan: "( You are defeated. )"), which the app does not label Block.
     */
    @Test
    fun `every printed Block card states a Block condition except Takahide's Handicap`() {
        val block = allCards().filter { it.block }
        val condition = Regex("\\bblock\\b", RegexOption.IGNORE_CASE)
        val withoutCondition = block.filterNot { condition in CardText.plain(it.text) }
        assertThat(block).hasSize(18)
        assertThat(withoutCondition.map { it.id }).containsExactly("takahide-handicap")
        assertThat(withoutCondition.single().text).isEqualTo("~(You are defeated.)")
    }

    @Test
    fun `heroes come back in index order and lookups work`() {
        val index = CatalogLoader.readIndex(RepoAssets.assets)
        assertThat(heroes.map { it.id }).isEqualTo(index.heroes)
        assertThat(catalog.hero("arien")?.name).isEqualTo("Arien")
        assertThat(catalog.product("core")?.name).isEqualTo("Core")
    }

    private data class DeckException(val extraDeckIds: List<String> = emptyList(), val extras: Int = 0)

    private companion object {
        const val KEBAB = "[a-z0-9]+(-[a-z0-9]+)*"
        val COLOURED = setOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)

        /** docs/DOMAIN.md: Takahide has 21 cards; Gydion has a 22-card spellbook. */
        val DECK_EXCEPTIONS = mapOf(
            "takahide" to DeckException(extraDeckIds = listOf("takahide-gold-extra-1", "takahide-gold-extra-2")),
            "gydion" to DeckException(extras = 22),
        )

        fun standardDeckIds(hero: String): List<String> = buildList {
            add("$hero-gold")
            add("$hero-silver")
            for (colour in listOf("red", "green", "blue")) {
                add("$hero-$colour-1")
                for (tier in 2..3) for (v in listOf("a", "b")) add("$hero-$colour-$tier$v")
            }
            add("$hero-purple-4")
        }

        /** docs/DOMAIN.md roster: id → (product, complexity, title). */
        val ROSTER: Map<String, Triple<String, Int, String>> = mapOf(
            "arien" to Triple("core", 1, "the Tidemaster"),
            "brogan" to Triple("core", 1, "the Destroyer"),
            "tigerclaw" to Triple("core", 1, "the Cutpurse"),
            "wasp" to Triple("core", 1, "the Warmaiden"),
            "sabina" to Triple("core", 1, "the Commander"),
            "xargatha" to Triple("core", 1, "the Changed"),
            "dodger" to Triple("core", 1, "the Warlock"),
            "misa" to Triple("devoted", 2, "the Samurai"),
            "whisper" to Triple("devoted", 2, "the Outcast"),
            "ursafar" to Triple("devoted", 2, "the Savage"),
            "silverarrow" to Triple("devoted", 2, "the Pathfinder"),
            "tali" to Triple("devoted", 3, "the Spiritcaller"),
            "garrus" to Triple("defiant", 2, "the Gladiator"),
            "bain" to Triple("defiant", 2, "the Bounty Hunter"),
            "cutter" to Triple("defiant", 3, "the Sky Pirate"),
            "trinkets" to Triple("defiant", 3, "the Scavenger"),
            "nebkher" to Triple("defiant", 4, "the Harbinger"),
            "min" to Triple("renowned", 2, "the Dragonmonk"),
            "swift" to Triple("renowned", 3, "the Sharpshooter"),
            "wuk" to Triple("renowned", 3, "the Grovekeeper"),
            "hanu" to Triple("renowned", 3, "the Trickster"),
            "ignatia" to Triple("renowned", 4, "the Mad"),
            "rowenna" to Triple("arcane", 2, "the Vanguard"),
            "mrak" to Triple("arcane", 3, "the Rockshaper"),
            "snorri" to Triple("arcane", 4, "the Runescribe"),
            "razzle" to Triple("arcane", 4, "the Ringmaster"),
            "gydion" to Triple("arcane", 4, "the Archwizard"),
            "brynn" to Triple("wayward", 3, "the Seeker"),
            "mortimer" to Triple("wayward", 3, "the Awakener"),
            "widget" to Triple("wayward", 3, "the Dragonbonded"),
            "takahide" to Triple("wayward", 4, "the Warlord"),
            "emmitt" to Triple("wayward", 4, "the Traveler"),
        )
    }
}
