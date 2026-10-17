package dev.quinton.goa.core.domain.catalog

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import kotlinx.serialization.json.Json
import org.junit.Test

/** Rulebook p.6: every hero starts with five Action cards: Gold, Silver and three Tier I. */
class StartingCardsTest {
    private val json = Json { ignoreUnknownKeys = false }

    private val heroes: List<Hero> by lazy {
        val index = json.decodeFromString(CatalogIndex.serializer(), bundledAsset("index.json"))
        index.heroes.map { json.decodeFromString(Hero.serializer(), bundledAsset("heroes/$it.json")) }
    }

    @Test
    fun `Takahide starts with Float like a Butterfly, Bushido and his three Tier I cards`() {
        val takahide = heroes.first { it.id == "takahide" }
        val start = StartingCards.of(takahide)
        assertThat(start).hasSize(5)
        assertThat(start.map { it.id }).containsAtLeast("takahide-gold", "takahide-silver")
        assertThat(start.map { it.id }).containsNoneOf("takahide-gold-extra-1", "takahide-gold-extra-2")
        assertThat(StartingCards.startZone(takahide.cards.first { it.id == "takahide-gold-extra-1" }))
            .isEqualTo(StartZone.DECK)
        assertThat(StartingCards.startZone(takahide.cards.first { it.id == "takahide-gold" }))
            .isEqualTo(StartZone.HAND)
    }

    @Test
    fun `every shipped hero starts with Gold, Silver, then Tier I Red, Green and Blue`() {
        assertThat(heroes).isNotEmpty()
        heroes.forEach { hero ->
            val start = StartingCards.of(hero)
            assertWithMessage(hero.id).that(start.map { it.color })
                .containsExactly(CardColor.GOLD, CardColor.SILVER, CardColor.RED, CardColor.GREEN, CardColor.BLUE)
                .inOrder()
            assertWithMessage(hero.id).that(start.map { it.tier }).containsExactly(null, null, 1, 1, 1).inOrder()
        }
    }
}
