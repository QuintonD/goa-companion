package dev.quinton.goa.core.domain.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import kotlinx.serialization.json.Json
import org.junit.Test

class DefeatExceptionsTest {
    private val json = Json { ignoreUnknownKeys = false }

    private fun shipped(id: String): Hero = json.decodeFromString(Hero.serializer(), bundledAsset("heroes/$id.json"))

    private fun card(id: String, text: String) = Card(
        id = id,
        heroId = "h",
        name = id,
        color = CardColor.RED,
        text = text,
    )

    @Test
    fun `cards naming Life counters or assist coins match, others do not`() {
        assertThat(DefeatExceptions.matches(card("a", "That hero spends 1 additional {life} Life counter."))).isTrue()
        assertThat(DefeatExceptions.matches(card("b", "Your team regains 1 spent {life}."))).isTrue()
        assertThat(DefeatExceptions.matches(card("c", "Your friendly heroes gain triple assist coins."))).isTrue()
        assertThat(DefeatExceptions.matches(card("d", "Gain 2 {coin} coins. Target a unit adjacent to you."))).isFalse()
        val reminder = card("e", "~(The enemy hero still gains the coins for defeating the minion.)")
        assertThat(DefeatExceptions.matches(reminder)).isFalse()
    }

    @Test
    fun `the shipped catalog's defeat exceptions are the five known cards`() {
        // Every hero whose card text mentions Life counters or assist coins, plus one that only
        // mentions coins for defeats (Brogan), which must not match.
        val heroes = listOf("bain", "ursafar", "gydion", "rowenna", "brogan", "arien").map(::shipped)
        val found = DefeatExceptions.find(heroes).map { it.card.id }
        assertThat(found).containsExactly(
            "bain-gold",
            "bain-handicap",
            "ursafar-red-3b",
            "gydion-spell-energy-drain",
            "rowenna-blue-3b",
        ).inOrder()
    }

    @Test
    fun `only the match's heroes are searched`() {
        val found = DefeatExceptions.find(listOf(shipped("arien"), shipped("ursafar")))
        assertThat(found.map { it.hero.id to it.card.name }).containsExactly("ursafar" to "Tear")
    }
}
