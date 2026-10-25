package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * ADR-035 point 3: the AI never reads hidden cards. Two sessions that look the same to the deciding
 * seat but differ in the other team's hidden cards must get the same answer for the same seed, at
 * every strength 1–10 (STRATEGY.md §4.5 "No peeking"; ADR-039).
 */
class NoPeekingTest {
    private fun assertSameChoice(a: EngineSession, b: EngineSession) {
        val da = a.pending!!
        val db = b.pending!!
        assertThat(db.kind).isEqualTo(da.kind)
        assertThat(db.seat).isEqualTo(da.seat)
        assertThat(db.options).isEqualTo(da.options)
        // The test is only meaningful when the seat really sees the same game.
        assertThat(b.view(db.seat)).isEqualTo(a.view(da.seat))
        for (strength in AiStrength.MIN..AiStrength.MAX) {
            for (seed in 1L..3L) {
                val ca = AiPlayers.deterministic(strength, seed).choose(a, da)
                val cb = AiPlayers.deterministic(strength, seed).choose(b, db)
                assertThat(cb).isEqualTo(ca)
            }
        }
    }

    @Test
    fun `the facedown card an enemy played does not change the choice`() {
        val a = TestData.newGame()
        a.answer(0, Choice.Card("wasp-gold"))
        a.answer(1, Choice.Card("xargatha-red-1"))
        val b = TestData.newGame()
        b.answer(0, Choice.Card("wasp-blue-1"))
        b.answer(1, Choice.Card("xargatha-silver"))
        assertSameChoice(a, b)
    }

    /** Wasp at level 2: Red upgraded (Stinger item, Defense icon) or Blue upgraded (Defense icon too). */
    private fun upgraded(redUpgrade: Boolean, block: PositionBuilder.() -> Unit = {}): EngineSession =
        position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                if (redUpgrade) {
                    hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                    items("wasp-red-2b")
                } else {
                    hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                    items("wasp-blue-2a")
                }
            }
            hero("xargatha", Team.RED, Hex(-1, -1))
            block()
        }

    @Test
    fun `which colour an enemy upgraded is hidden when the item icons match`() {
        val a = upgraded(redUpgrade = true)
        val b = upgraded(redUpgrade = false)
        assertThat(a.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.HAND)
        assertThat(b.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.DECK)
        assertSameChoice(a, b)
    }

    @Test
    fun `hidden hands do not change a choice after the reveal`() {
        val build: PositionBuilder.() -> Unit = {
            phase = PositionPhase.Resolving(next = "arien")
        }
        val a = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played("wasp-silver")
                hand("wasp-gold", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                items("wasp-red-2b")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            build()
        }
        val b = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played("wasp-silver")
                hand("wasp-gold", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                items("wasp-blue-2a")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            build()
        }
        assertSameChoice(a, b)
    }

    @Test
    fun `the determinizer keeps every public fact and only consistent upgrade histories`() {
        val a = upgraded(redUpgrade = true)
        val kept = HashSet<String>()
        for (seed in 1L..60L) {
            val det = a.determinize(Team.BLUE, seed) as EngineSession
            assertThat(det.view("arien")).isEqualTo(a.view("arien"))
            val w = det.state.hero("wasp")!!
            val hand = w.cards.filter { it.zone == Zone.HAND }.map { it.id }
            val items = w.cards.filter { it.zone == Zone.ITEM }.map { it.id }
            assertThat(hand).hasSize(5)
            assertThat(items).hasSize(1)
            // Exactly one Tier II card in hand, of the colour whose other Tier II card is the item.
            val tierTwo = hand.filter { it.contains("-2") }
            assertThat(tierTwo).hasSize(1)
            assertThat(items.single().substringBeforeLast('-')).isEqualTo(tierTwo.single().substringBeforeLast('-'))
            assertThat(
                TestData.engine.data.card(items.single()).item,
            ).isEqualTo(dev.quinton.goa.core.model.catalog.Stat.DEFENSE)
            kept += tierTwo.single()
        }
        // Both histories with a Defense item occur: Red (keep 2a) and Blue (keep 2b).
        assertThat(kept).containsAtLeast("wasp-red-2a", "wasp-blue-2b")
    }
}
