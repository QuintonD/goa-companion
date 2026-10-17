package dev.quinton.goa.feature.catalog.model

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.testing.TestCatalog
import org.junit.Test

class DeckLayoutTest {
    private val hero = TestCatalog.hero(1)

    @Test
    fun groupsFollowRulebookOrder() {
        val layout = DeckLayout.of(hero)
        assertThat(layout.groups.map { it.kind }).containsExactly(
            DeckGroupKind.BASIC,
            DeckGroupKind.RED,
            DeckGroupKind.GREEN,
            DeckGroupKind.BLUE,
            DeckGroupKind.ULTIMATE,
        ).inOrder()
    }

    @Test
    fun basicIsGoldThenSilverAndColoursHaveTiersOneToThree() {
        val layout = DeckLayout.of(hero)
        assertThat(layout.groups.first().cards.map { it.color }).containsExactly(CardColor.GOLD, CardColor.SILVER).inOrder()
        val red = layout.groups.first { it.kind == DeckGroupKind.RED }
        assertThat(red.rows.map { it.tier }).containsExactly(1, 2, 3).inOrder()
        assertThat(red.rows.map { it.cards.size }).containsExactly(1, 2, 2).inOrder()
    }

    @Test
    fun orderedCardsKeepsEveryCardOnceWithHandicapAndExtrasLast() {
        val extra = Card(id = "x-1", heroId = hero.id, name = "Spell", color = CardColor.OTHER)
        val layout = DeckLayout.of(hero.copy(extras = listOf(extra)))
        val ordered = layout.orderedCards
        assertThat(ordered).hasSize(hero.cards.size + 2)
        assertThat(ordered.map { it.id }.toSet()).hasSize(ordered.size)
        assertThat(ordered.takeLast(2).map { it.id }).containsExactly(hero.handicap?.id, "x-1").inOrder()
    }

    @Test
    fun unexpectedCardsLandInOtherInsteadOfBeingDropped() {
        val odd = Card(id = "odd", heroId = hero.id, name = "Odd", color = CardColor.OTHER)
        val untiered = Card(id = "red-untiered", heroId = hero.id, name = "Untiered", color = CardColor.RED)
        val layout = DeckLayout.of(hero.copy(cards = hero.cards + odd + untiered, handicap = null))
        assertThat(layout.groups.last().kind).isEqualTo(DeckGroupKind.OTHER)
        val red = layout.groups.first { it.kind == DeckGroupKind.RED }
        assertThat(red.rows.first().tier).isNull()
        assertThat(layout.orderedCards).hasSize(hero.cards.size + 2)
    }

    @Test
    fun tierNumeralsAreRoman() {
        assertThat((1..4).map(::tierNumeral)).containsExactly("I", "II", "III", "IV").inOrder()
    }
}
