package dev.quinton.goa.feature.catalog.detail

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.HeroLogSummary
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.catalog.viewer.CardViewerUiState
import org.junit.Test

/** One deck count everywhere: the deck's own cards, the Handicap card apart, extras on their own. */
class DeckCountTest {
    private val hero = TestCatalog.hero(1)

    private fun viewer(extras: List<Card> = emptyList()): CardViewerUiState.Ready {
        val h = hero.copy(extras = extras)
        return CardViewerUiState.Ready(h, DeckLayout.of(h).orderedCards, initialIndex = 0)
    }

    @Test
    fun viewerCountsTheDeckThenNamesTheHandicapCard() {
        val state = viewer()
        val deck = hero.cards.size
        assertThat(state.position(0)).isEqualTo("1 of $deck")
        assertThat(state.position(deck - 1)).isEqualTo("$deck of $deck")
        assertThat(state.position(deck)).isEqualTo("Handicap")
    }

    @Test
    fun viewerCountsExtrasApart() {
        val spells = (1..2).map {
            Card(id = "${hero.id}-spell-$it", heroId = hero.id, name = "Spell $it", color = CardColor.OTHER)
        }
        val state = viewer(spells)
        assertThat(state.position(hero.cards.size + 1)).isEqualTo("Spellbook 1 of 2")
        assertThat(state.position(hero.cards.size + 2)).isEqualTo("Spellbook 2 of 2")
    }

    @Test
    fun aTierShowsOnlyThatTiersCardsInEveryColour() {
        val layout = DeckLayout.of(hero)
        assertThat(deckTiers(layout)).containsExactly(1, 2, 3).inOrder()
        val two = layout.onlyTier(2)
        assertThat(two.groups.map { it.kind })
            .containsExactly(DeckGroupKind.RED, DeckGroupKind.GREEN, DeckGroupKind.BLUE).inOrder()
        assertThat(two.groups.flatMap { it.cards }.map { it.tier }.toSet()).containsExactly(2)
        assertThat(two.handicap).isNull()
        assertThat(layout.onlyTier(null)).isEqualTo(layout)
        assertThat(layout.groups.first().cards.map { it.color }).contains(CardColor.GOLD)
    }

    @Test
    fun aColourShowsOnlyThatPartAndTheCountNamesTheFilters() {
        val layout = DeckLayout.of(hero)
        val parts = deckParts(layout)
        val red = parts.first { it.label == DeckGroupKind.RED.title }
        val onlyRed = layout.onlyPart(red.key)
        assertThat(onlyRed.groups.map { it.kind }).containsExactly(DeckGroupKind.RED)
        assertThat(onlyRed.handicap).isNull()
        assertThat(onlyRed.shownCount()).isEqualTo(red.count)
        assertThat(layout.onlyPart(null)).isEqualTo(layout)
        // Tier and colour together, named in the count.
        val both = layout.onlyTier(2).onlyPart(red.key)
        val none = HeroPersonalStats(null, null, HeroLogSummary(0, 0, emptyList(), emptyList()))
        val state = HeroDetailUiState.Ready(hero, "Core", layout, none)
        assertThat(deckCount(state, both, 2, red.label)).isEqualTo("Red · Tier II · ${both.shownCount()} cards")
        assertThat(deckCount(state, layout, null, null)).startsWith("${hero.cards.size} cards")
    }
}
