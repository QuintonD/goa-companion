package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.play.table.namedExtras
import dev.quinton.goa.feature.board.play.table.quotedNames
import org.junit.Test

/**
 * The cards a card names (ADR-045 extended): the link from a Gydion card to its spells is its printed
 * text and the spell cards' names, so every quoted name on his cards must find its spell card (a miss
 * would silently leave a spell out of the level up), and no other hero's card names an extra card.
 */
class NamedCardsTest {
    private val gydion = bundledCatalog.heroes.first { it.id == "gydion" }

    private fun named(id: String) = namedExtras(gydion.cards.first { it.id == id }, gydion.extras).map { it.name }

    @Test
    fun `every name a Gydion card quotes is one of his spell cards`() {
        for (card in gydion.cards) {
            val quoted = quotedNames(card.text)
            assertWithMessage(card.id).that(namedExtras(card, gydion.extras)).hasSize(quoted.distinct().size)
        }
    }

    @Test
    fun `the spells of each Tier - Cantrip three, Tier I one, Tier II two, Tier III three, the Archwizard Wish`() {
        assertThat(named("gydion-gold")).containsExactly("Shocking Grasp", "Magic Missile", "Expeditious Retreat")
            .inOrder()
        assertThat(named("gydion-silver")).isEmpty()
        assertThat(named("gydion-red-1")).containsExactly("Burning Hands")
        assertThat(named("gydion-red-2a")).containsExactly("Burning Hands", "Fireball").inOrder()
        assertThat(named("gydion-red-2b")).containsExactly("Vampiric Touch", "Create Undead").inOrder()
        assertThat(named("gydion-red-3b")).containsExactly("Vampiric Touch", "Create Undead", "Energy Drain").inOrder()
        assertThat(named("gydion-purple-4")).containsExactly("Wish")
        for (card in gydion.cards.filter { it.tier != null && it.tier!! in 1..3 }) {
            assertWithMessage(card.id).that(named(card.id)).hasSize(card.tier!!)
        }
    }

    @Test
    fun `a name printed without the apostrophe still finds its card - Midas Touch`() {
        assertThat(named("gydion-blue-2b")).containsExactly("Midas' Touch", "Disintegrate").inOrder()
    }

    @Test
    fun `no other hero's card names an extra card`() {
        for (hero in bundledCatalog.heroes.filter { it.id != "gydion" }) {
            for (card in hero.allCards) {
                assertWithMessage(card.id).that(namedExtras(card, hero.extras)).isEmpty()
            }
        }
    }
}
