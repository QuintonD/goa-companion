package dev.quinton.goa.core.presentation.catalog

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.presentation.TestCatalog
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
        val basicColors = layout.groups.first().cards.map { it.color }
        assertThat(basicColors).containsExactly(CardColor.GOLD, CardColor.SILVER).inOrder()
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
    fun extrasAreGroupedByTierAndTheViewerFollowsThatOrder() {
        fun spell(name: String, color: CardColor, tier: Int?) =
            Card(id = "${hero.id}-spell-$name", heroId = hero.id, name = name, color = color, tier = tier)
        val extras = listOf(
            spell("cantrip", CardColor.GOLD, null),
            spell("red-one", CardColor.RED, 1),
            spell("red-two", CardColor.RED, 2),
            spell("green-one", CardColor.GREEN, 1),
            spell("wish", CardColor.PURPLE, 4),
        )
        val layout = DeckLayout.of(hero.copy(extras = extras))
        assertThat(layout.extrasAreSpellbook).isTrue()
        assertThat(layout.extraRows.map { it.tier }).containsExactly(null, 1, 2, 4).inOrder()
        assertThat(layout.extraRows[1].cards.map { it.name }).containsExactly("red-one", "green-one").inOrder()
        assertThat(layout.orderedCards.takeLast(5).map { it.name })
            .containsExactly("cantrip", "red-one", "green-one", "red-two", "wish").inOrder()
    }

    @Test
    fun tierNumeralsAreRoman() {
        assertThat((1..4).map(::tierNumeral)).containsExactly("I", "II", "III", "IV").inOrder()
    }
}
