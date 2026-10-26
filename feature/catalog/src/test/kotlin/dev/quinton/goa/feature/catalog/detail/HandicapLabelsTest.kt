package dev.quinton.goa.feature.catalog.detail

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.catalog.handicapBasis
import dev.quinton.goa.core.domain.catalog.handicapConflict
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.testing.TestCatalog
import org.junit.Test

/**
 * No Handicap card prints what it replaces (rulebook p.20 only says "weaker versions of the regular
 * basic cards (Usually Gold)"), so the app labels its frame colour as derived and never claims a
 * replacement; a non-gold frame against the 3-player "replaces a gold card" rule stays unresolved.
 */
class HandicapLabelsTest {
    private val hero = TestCatalog.hero(1)

    private fun card(id: String, color: CardColor) = Card(
        id = "${hero.id}-$id",
        heroId = hero.id,
        name = id,
        color = color,
    )

    private fun handicap(color: CardColor) = card("handicap", color)

    @Test
    fun `the basis is labelled as derived from the frame colour, never as a replacement`() {
        assertThat(handicap(CardColor.SILVER).handicapBasis())
            .isEqualTo("Weaker version of the Silver card (frame colour)")
        assertThat(handicap(CardColor.GOLD).handicapBasis()).doesNotContain("Replaces")
        assertThat(handicap(CardColor.OTHER).handicapBasis()).isNull()
    }

    @Test
    fun `a gold handicap has no conflict, a silver one with several golds is unresolved`() {
        assertThat(handicapConflict(hero, handicap(CardColor.GOLD))).isNull()
        val cards = hero.cards + card("gold-extra-1", CardColor.GOLD)
        val note = handicapConflict(hero.copy(cards = cards), handicap(CardColor.SILVER))
        assertThat(note).startsWith("Unresolved: rulebook p.20")
        assertThat(note).contains("silver frame and ${hero.name} has 2 gold cards")
    }
}
