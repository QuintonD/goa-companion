package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardValues
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.Stat
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class CrossCheckAndOverridesTest {
    private val card = Card(
        id = "arien-red-2b",
        heroId = "arien",
        name = "Rogue Wave",
        color = CardColor.RED,
        tier = 2,
        initiative = 8,
        primary = PrimaryAction.ATTACK,
        values = CardValues(attack = 4, defense = 4, movement = 4, range = 2),
        ranged = true,
        item = Stat.DEFENSE,
        text = "Target a unit in range.\nAfter the attack: You may push an enemy unit adjacent to you up to 2 spaces.",
    )

    private val header = "ID,Initiative,Card Name,Tier,Color,Defense,Movement,Attack," +
        "Action Type,Range/Radius,Card Text,Item Icon\n"

    @Test
    fun `matching numbers produce no disagreement`() {
        val rows = Csv.parse(header + "R2B,8,Rogue Wave,II,Red,4,4,4,Attack - Ranged,2,x,Defense\n")
        val result = CrossCheck.numbers("arien", listOf(card), rows)
        assertThat(result.disagreements).isEmpty()
        assertThat(result.unmatched).isEmpty()
        assertThat(result.present).isTrue()
    }

    @Test
    fun `differing numbers are reported per field`() {
        val rows = Csv.parse(header + "R2B,9,Rogue Wave,II,Red,4,4,5,Attack - Ranged,2,x,Attack\n")
        val fields = CrossCheck.numbers("arien", listOf(card), rows).disagreements.map { it.field }
        assertThat(fields).containsExactly("initiative", "values.attack", "item")
    }

    @Test
    fun `printed plus signs are compared separately and range-radius item labels are tolerated`() {
        assertThat(CrossCheck.number("2+")).isEqualTo(2)
        assertThat(CrossCheck.number(null)).isNull()
        val rows = Csv.parse(header + "R2B,8,Rogue Wave,II,Red,4,4,4+,Attack - Ranged,2+,x,Defense\n")
        val plus = card.copy(values = card.values.copy(plus = listOf(Stat.ATTACK, Stat.RANGE)))
        assertThat(CrossCheck.numbers("arien", listOf(plus), rows).disagreements).isEmpty()
        val missing = CrossCheck.numbers("arien", listOf(card), rows).disagreements.single()
        assertThat(missing.field).isEqualTo("values.plus")
        assertThat(missing.other).isEqualTo("[Attack, Range/Radius]")
        val rangeItem = card.copy(item = Stat.RANGE)
        val radiusLabel = Csv.parse(header + "R2B,8,Rogue Wave,II,Red,4,4,4,Attack - Ranged,2,x,Radius\n")
        assertThat(CrossCheck.numbers("arien", listOf(rangeItem), radiusLabel).disagreements).isEmpty()
    }

    @Test
    fun `a block card matches the exclamation mark`() {
        val block = card.copy(
            primary = PrimaryAction.DEFENSE,
            values = CardValues(movement = 2),
            block = true,
            item = null,
        )
        val rows = Csv.parse(header + "G1,8,Rogue Wave,I,Green,!,2,,Defense,,x,\n")
        assertThat(CrossCheck.numbers("arien", listOf(block), rows).disagreements).isEmpty()
    }

    @Test
    fun `missing source and missing cards are reported`() {
        assertThat(CrossCheck.numbers("arien", listOf(card), null).present).isFalse()
        val other = Csv.parse(header + "R1,8,Something Else,I,Red,4,4,4,Attack,,x,\n")
        assertThat(CrossCheck.numbers("arien", listOf(card), other).unmatched).containsExactly("arien-red-2b")
    }

    @Test
    fun `text cross-check compares words and slot`() {
        val same = FaqCard(
            "ari-red-ii-rogue-wave",
            "Rogue Wave",
            "red",
            "attack - ranged",
            "II",
            "Target a unit in range.\nAfter the attack: You may push an enemy unit adjacent to you up to 2 spaces.",
            true,
        )
        assertThat(CrossCheck.texts("arien", listOf(card), listOf(same)).disagreements).isEmpty()
        val differs = same.copy(
            text = "Target a unit in range. After the attack: Push an enemy unit adjacent to you up to 2 spaces.",
        )
        assertThat(
            CrossCheck.texts("arien", listOf(card), listOf(differs)).disagreements.map {
                it.field
            },
        ).containsExactly("text")
        val wrongVariant = same.copy(alternative = false)
        assertThat(
            CrossCheck.texts("arien", listOf(card), listOf(wrongVariant)).disagreements.map {
                it.field
            },
        ).containsExactly("variant")
    }

    @Test
    fun `renamed cards are matched by slot and the name difference is reported`() {
        val renamed = FaqCard("x", "Rogue Waves", "red", "attack - ranged", "II", card.text, true)
        val result = CrossCheck.texts("arien", listOf(card), listOf(renamed))
        assertThat(result.unmatched).isEmpty()
        assertThat(result.disagreements.map { it.field }).containsExactly("name")
        assertThat(CrossCheck.nameKey("Déjà Vu")).isEqualTo(CrossCheck.nameKey("Deja Vu"))
    }

    private val hero = Hero(
        id = "hanu",
        name = "Hanu",
        title = "the Trickster",
        product = "renowned",
        complexity = 3,
        stats = HeroStats(
            attack = listOf(4, 4),
            defense = listOf(1, 1),
            initiative = listOf(8, 8),
            movement = listOf(8, 8),
        ),
        roles = listOf(Role.TACTICIAN, Role.SNIPER),
        printing = 2,
        cards = listOf(card),
        verified = false,
    )

    private fun overrides(json: String) = Json.decodeFromString(Overrides.serializer(), json)

    @Test
    fun `hero stat disagreements are found`() {
        val faq = FaqParser.heroes(
            """[{"id":"han","class":"The Trickster","name":"Hanu","expansion":"renowned","complexity":3,
            "roles":["Sniper","Tactician"],"stats":{"attack":4,"defense":1,"initiative":8,"movement":[7,8]},
            "cards":"cards/hanu.json"}]""",
        ).single()
        assertThat(CrossCheck.hero(hero, faq).map { it.field }).containsExactly("stats.movement")
    }

    @Test
    fun `overrides patch nested values one level deep and resolve the field`() {
        val o = overrides(
            """{"heroes":{"hanu":{"stats":{"movement":[7,8]},"reason":"r","evidence":"e"}},
                "cards":{"arien-red-2b":{"values":{"attack":5},"confirm":["text"],"reason":"r","evidence":"e"}}}""",
        )
        o.validate()
        val patchedHero = OverrideApplier.hero(hero, o.heroes["hanu"])
        assertThat(patchedHero.stats.movement).containsExactly(7, 8).inOrder()
        assertThat(patchedHero.stats.attack).containsExactly(4, 4).inOrder()
        val patchedCard = OverrideApplier.card(card, o.cards["arien-red-2b"])
        assertThat(patchedCard.values.attack).isEqualTo(5)
        assertThat(patchedCard.values.range).isEqualTo(2)
        assertThat(o.resolves("hanu", "stats.movement")).isTrue()
        assertThat(o.resolves("arien-red-2b", "values.attack")).isTrue()
        assertThat(o.resolves("arien-red-2b", "text")).isTrue()
        assertThat(o.resolves("arien-red-2b", "initiative")).isFalse()
    }

    @Test
    fun `confirm resolves the combined range-radius field`() {
        val o = overrides("""{"cards":{"c":{"confirm":["values.range/radius"],"reason":"r","evidence":"e"}}}""")
        assertThat(o.resolves("c", "values.range/radius")).isTrue()
    }

    @Test
    fun `overrides need a reason and evidence and may not change ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            overrides("""{"cards":{"c":{"text":"x","reason":"r"}}}""").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            overrides("""{"cards":{"c":{"id":"other","reason":"r","evidence":"e"}}}""").validate()
        }
    }
}
