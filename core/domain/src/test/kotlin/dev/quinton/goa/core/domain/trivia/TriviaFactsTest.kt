package dev.quinton.goa.core.domain.trivia

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.catalog.Catalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Every trivia question is a number the bundled catalog prints; nothing else is ever asked. */
class TriviaFactsTest {
    private val heroes = BundledCatalog.heroes
    private val catalog = BundledCatalog.catalog

    /** The raw JSON of a hero file, independent of the model's decoding. */
    private fun rawCards(heroId: String): List<JsonObject> = Json
        .parseToJsonElement(bundledAsset("heroes/$heroId.json"))
        .jsonObject.getValue("cards").jsonArray.map { it.jsonObject }

    private fun rawValue(card: JsonObject, stat: TriviaStat): Int? {
        val element = when (stat) {
            TriviaStat.INITIATIVE -> card["initiative"]
            else -> card["values"]?.jsonObject?.get(stat.key)
        }
        return element?.takeIf { it != JsonNull }?.jsonPrimitive?.int
    }

    private fun rawPlus(card: JsonObject): Set<String> =
        card["values"]?.jsonObject?.get("plus")?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()

    @Test
    fun `every deck number in the JSON is exactly one fact with that value, and nulls are never asked`() {
        assertThat(heroes).hasSize(BundledCatalog.index.heroes.size)
        heroes.forEach { hero ->
            val facts = TriviaFacts.of(hero).associateBy { it.cardId to it.stat }
            val raw = rawCards(hero.id)
            val rawIds = raw.map { it["id"]!!.jsonPrimitive.content }
            assertWithMessage(hero.id).that(rawIds).isEqualTo(hero.cards.map { it.id })
            raw.forEach { card ->
                val cardId = card["id"]!!.jsonPrimitive.content
                TriviaStat.entries.forEach { stat ->
                    val fact = facts[cardId to stat]
                    val expected = rawValue(card, stat)
                    if (expected == null) {
                        assertWithMessage("$cardId $stat").that(fact).isNull()
                    } else {
                        assertWithMessage("$cardId $stat").that(fact).isNotNull()
                        assertWithMessage("$cardId $stat").that(fact!!.value).isEqualTo(expected)
                        assertWithMessage("$cardId $stat plus").that(fact.plus).isEqualTo(stat.key in rawPlus(card))
                        assertWithMessage("$cardId hero").that(fact.heroId).isEqualTo(hero.id)
                    }
                }
            }
            val expectedCount = raw.sumOf { card -> TriviaStat.entries.count { rawValue(card, it) != null } }
            assertWithMessage(hero.id).that(facts).hasSize(expectedCount)
        }
    }

    @Test
    fun `the Handicap card and extras such as Gydion's spellbook never produce facts`() {
        val gydion = BundledCatalog.hero("gydion")
        assertThat(gydion.extras).isNotEmpty()
        heroes.forEach { hero ->
            val offDeck = (listOfNotNull(hero.handicap) + hero.extras).map { it.id }.toSet()
            val facts = TriviaFacts.of(hero)
            val cardIds = facts.map { it.cardId }.toSet()
            assertWithMessage(hero.id).that(cardIds).containsNoneIn(offDeck)
            assertWithMessage(hero.id).that(hero.cards.map { it.id }).containsAtLeastElementsIn(cardIds)
        }
        // Handicap cards print numbers (Initiative 10 etc.); still not a deck card.
        assertThat(heroes.mapNotNull { it.handicap }.filter { it.initiative != null }).isNotEmpty()
    }

    @Test
    fun `the plus flag matches values plus, and Initiative is never a plus`() {
        val all = TriviaFacts.of(catalog, heroes.map { it.id })
        assertThat(all.filter { it.plus }).isNotEmpty()
        assertThat(all.filter { it.stat == TriviaStat.INITIATIVE && it.plus }).isEmpty()
        val cards = heroes.flatMap { it.cards }.associateBy { it.id }
        all.forEach { fact ->
            assertWithMessage(fact.key).that(fact.plus).isEqualTo(fact.stat.isPlus(cards.getValue(fact.cardId)))
        }
        // Brynn's red Tier I prints Attack 4+.
        val brynn = all.first { it.cardId == "brynn-red-1" && it.stat == TriviaStat.ATTACK }
        assertThat(brynn.value).isEqualTo(4)
        assertThat(brynn.plus).isTrue()
    }

    @Test
    fun `Takahide's extra gold cards are basic cards`() {
        val takahide = BundledCatalog.hero("takahide")
        val basic = TriviaFacts.of(takahide, groups = setOf(CardGroup.BASIC)).map { it.cardId }.toSet()
        assertThat(basic)
            .containsExactly("takahide-gold", "takahide-gold-extra-1", "takahide-gold-extra-2", "takahide-silver")
    }

    @Test
    fun `stat and group filters keep only what was asked for`() {
        heroes.forEach { hero ->
            val all = TriviaFacts.of(hero)
            CardGroup.entries.forEach { group ->
                val facts = TriviaFacts.of(hero, groups = setOf(group))
                assertWithMessage("${hero.id} $group").that(facts)
                    .isEqualTo(all.filter { f -> CardGroup.of(hero.cards.first { it.id == f.cardId }) == group })
            }
            TriviaStat.entries.forEach { stat ->
                val facts = TriviaFacts.of(hero, stats = setOf(stat))
                assertWithMessage("${hero.id} $stat").that(facts).isEqualTo(all.filter { it.stat == stat })
            }
            assertWithMessage(hero.id).that(TriviaFacts.of(hero, stats = emptySet())).isEmpty()
            assertWithMessage(hero.id).that(TriviaFacts.of(hero, groups = emptySet())).isEmpty()
        }
        val pair = TriviaFacts.of(
            catalog,
            listOf("wasp", "brogan", "wasp"),
            stats = setOf(TriviaStat.INITIATIVE),
            groups = setOf(CardGroup.TIER_1),
        )
        assertThat(pair.map { it.cardId }).containsExactly(
            "wasp-red-1",
            "wasp-green-1",
            "wasp-blue-1",
            "brogan-red-1",
            "brogan-green-1",
            "brogan-blue-1",
        ).inOrder()
        assertThat(TriviaFacts.of(catalog, listOf("no-such-hero"))).isEmpty()
    }

    @Test
    fun `Ultimates print no Initiative`() {
        val ultimates = TriviaFacts.of(catalog, heroes.map { it.id }, groups = setOf(CardGroup.ULTIMATE))
        assertThat(ultimates.filter { it.stat == TriviaStat.INITIATIVE }).isEmpty()
        heroes.flatMap { it.cards }.filter { it.tier == 4 }
            .forEach { assertWithMessage(it.id).that(it.initiative).isNull() }
    }

    @Test
    fun `value ranges are ascending, distinct and hold every fact value`() {
        val ranges = TriviaFacts.valueRange(catalog)
        assertThat(ranges.keys).containsExactlyElementsIn(TriviaStat.entries)
        val all = TriviaFacts.of(catalog, heroes.map { it.id })
        ranges.forEach { (stat, range) ->
            assertWithMessage("$stat").that(range).isNotEmpty()
            assertWithMessage("$stat").that(range).isInStrictOrder()
            val values = all.filter { it.stat == stat }.map { it.value }.distinct()
            assertWithMessage("$stat").that(range).containsExactlyElementsIn(values)
        }
        assertThat(ranges.getValue(TriviaStat.INITIATIVE)).containsExactlyElementsIn(1..13).inOrder()
        assertThat(TriviaFacts.valueRange(Catalog.EMPTY).values.all { it.isEmpty() }).isTrue()
    }

    @Test
    fun `fact keys are unique and stable`() {
        val all = TriviaFacts.of(catalog, heroes.map { it.id })
        assertThat(all.map { it.key }).containsNoDuplicates()
        all.forEach { assertThat(it.key).isEqualTo("${it.cardId}/${it.stat.key}") }
        assertThat(TriviaStat.entries.map { TriviaStat.fromKey(it.key) }).isEqualTo(TriviaStat.entries)
        assertThat(CardGroup.entries.map { CardGroup.fromKey(it.key) }).isEqualTo(CardGroup.entries)
        assertThat(TriviaStat.fromKey("speed")).isNull()
    }
}
