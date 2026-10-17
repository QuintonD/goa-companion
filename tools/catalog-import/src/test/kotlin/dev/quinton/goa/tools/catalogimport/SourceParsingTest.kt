package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceParsingTest {
    private val statesTs = """
        export enum Pack { BASE = "BASE" }
        export const heroes = {
          "arien": new Hero("Arien", [[8, 8], [5, 6], [4, 4], [5, 6]], "the Tidemaster", 1, Pack.BASE, [Trait.TACTICIAN, Trait.DISABLER]),
          "widget": new Hero("Widget", [[5, 5], [4, 4], [4, 4], [5, 6]], "and Pyro", 3, Pack.WAYWARD, [Trait.HEALER]),
        }
        export const oldHeroes = {
          "arien": new OldHero("Arien", [8, 5, 4, 4, 2, 6, 5, 5], "the Tidemaster", 1),
        }
    """.trimIndent()

    @Test
    fun `states ts heroes are read with stats in attack defense initiative movement order`() {
        val heroes = SoaParser.heroes(statesTs)
        assertThat(heroes.map { it.id }).containsExactly("arien", "widget").inOrder()
        val arien = heroes.first()
        assertThat(arien.stats).containsExactly(listOf(8, 8), listOf(5, 6), listOf(4, 4), listOf(5, 6)).inOrder()
        assertThat(arien.title).isEqualTo("the Tidemaster")
        assertThat(arien.stars).isEqualTo(1)
        assertThat(arien.pack).isEqualTo("BASE")
        assertThat(arien.traits).containsExactly("TACTICIAN", "DISABLER").inOrder()
        assertThat(heroes[1].traits).containsExactly("HEALER")
    }

    @Test
    fun `hero cards parse and unknown fields are rejected`() {
        val json = """{"arien":[{"name":"Noble Blade","description":"x","color":"GOLD","initiative":11,
            "primaryAction":"ATTACK","primaryValue":4,"secondaryMovement":1,"secondaryDefense":2,"traits":["A"]}]}"""
        val cards = SoaParser.heroCards(json).getValue("arien")
        assertThat(cards.single().primaryValue).isEqualTo(4)
        assertThrows(SerializationException::class.java) {
            SoaParser.heroCards("""{"arien":[{"name":"n","description":"d","color":"GOLD","newField":1}]}""")
        }
    }

    @Test
    fun `faq stats accept a single number or a range`() {
        val hero = FaqParser.heroes(
            """[{"id":"ari","class":"The Tidemaster","name":"Arien","expansion":"core","complexity":1,
            "roles":["Tactician"],"stats":{"attack":8,"defense":[5,6]},"cards":"cards/arien.json"}]""",
        ).single()
        assertThat(hero.stat("attack")).containsExactly(8, 8).inOrder()
        assertThat(hero.stat("defense")).containsExactly(5, 6).inOrder()
        assertThat(hero.stat("movement")).isNull()
        assertThat(hero.title).isEqualTo("The Tidemaster")
    }

    @Test
    fun `mojibake is repaired`() {
        assertThat(FaqParser.fixMojibake("Arienâ€™s card â€” â€œquotedâ€\u009d")).isEqualTo("Arien’s card — “quoted”")
        assertThat(FaqParser.fixMojibake("clean text")).isEqualTo("clean text")
    }

    @Test
    fun `csv handles quotes, commas, escaped quotes and newlines`() {
        val csv = "ID,Card Name,Card Text,Item Icon\r\n" +
            "D,Noble Blade,\"Target a unit.\nBefore the attack: move, then \"\"strike\"\".\",\r\n" +
            "R1,Dangerous Current,Plain,Initiative\n"
        val rows = Csv.parse(csv)
        assertThat(rows).hasSize(2)
        assertThat(rows[0]["Card Text"]).isEqualTo("Target a unit.\nBefore the attack: move, then \"strike\".")
        assertThat(rows[0]["Item Icon"]).isNull()
        assertThat(rows[1]["Item Icon"]).isEqualTo("Initiative")
    }

    @Test
    fun `pinned sources build raw urls with encoded paths`() {
        val s = PinnedSource("id", "Name", "owner/repo", "main", "abc123", "2026-10-17", "use")
        assertThat(s.rawUrl("src/routes/[hero]/a b.json"))
            .isEqualTo("https://raw.githubusercontent.com/owner/repo/abc123/src/routes/%5Bhero%5D/a%20b.json")
    }
}
