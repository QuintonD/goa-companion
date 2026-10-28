package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * A hero's game state as every other seat sees it (ADR-044, the hero sheet): rulebook p.4 "You can always
 * ask another player to read any of their unresolved cards, resolved cards, and discarded cards, as well as
 * visible information on their dashboards. Everything else is hidden information, including: Cards in the
 * deck. Cards in hand. The part of the card covered by your hero dashboard. Any facedown cards." p.16:
 * an item is tucked "so that only the item section of the card is visible"; "Your items must be clearly
 * visible to the opposing team." So every seat sees Wasp's item icons ([HeroView.itemStats]), but which
 * cards are tucked ([HeroView.items]), her hand and her facedown card are hers alone.
 */
class HeroStateViewTest {
    /** Everyone but Wasp's own seat: her teammate, the enemies, a spectator. */
    private val others = listOf("xargatha", "arien", "brogan", null)

    /**
     * Round 2, turn 3, Wasp's card played facedown. By default Wasp (level 3) kept Charged Boomerang and
     * Kinetic Repulse (on her Turn 1 and 2 slots), so Electrocute (+1 Initiative) and Control Gravity
     * (+1 Defense) are tucked under her dashboard.
     */
    private fun session(
        tucked: List<String> = listOf("wasp-red-2a", "wasp-blue-2a"),
        kept: List<String> = listOf("wasp-red-2b", "wasp-blue-2b"),
        hand: List<String> = listOf("wasp-gold"),
        level: Int = 3,
        phase: PositionPhase = PositionPhase.PlayCards(fromSeat = 1),
    ): EngineSession = pos {
        clearMinions()
        round = 2
        turn = 3
        hero("wasp", Team.RED, Hex(-1, -2)) {
            this.level = level
            items(*tucked.toTypedArray())
            kept.forEachIndexed { i, id -> resolved(i + 1, id) }
            played("wasp-silver")
            hand(*hand.toTypedArray())
        }
        hero("xargatha", Team.RED, Hex(-1, -1))
        hero("arien", Team.BLUE, Hex(2, -3))
        hero("brogan", Team.BLUE, Hex(3, -3))
        this.phase = phase
    }.s

    private fun GameView.wasp(): HeroView = heroes.first { it.heroId == "wasp" }

    @Test
    fun `before the reveal - every other seat sees Wasp's item icons, never her tucked cards, hand or facedown card`() {
        val s = session()
        val own = s.view("wasp").wasp()
        assertThat(own.items).containsExactly("wasp-red-2a", "wasp-blue-2a")
        assertThat(own.hand).containsExactly("wasp-gold")
        assertThat(own.played?.cardId).isEqualTo("wasp-silver")
        for (viewer in others) {
            val wasp = s.view(viewer).wasp()
            assertThat(wasp.itemStats).containsExactly(Stat.DEFENSE, Stat.INITIATIVE)
            assertThat(wasp.itemStats).isEqualTo(own.itemStats)
            assertThat(wasp.items).isEmpty()
            assertThat(wasp.hand).isNull()
            assertThat(wasp.handCount).isEqualTo(1)
            assertThat(wasp.played?.cardId).isNull()
            assertThat(wasp.played?.facedown).isTrue()
            // Open information: the resolved Turn slots and the level.
            assertThat(wasp.resolved.map { it.cardId }).containsExactly("wasp-red-2b", "wasp-blue-2b").inOrder()
            assertThat(wasp.level).isEqualTo(3)
        }
    }

    @Test
    fun `games that differ only in which card of a pair was tucked look the same to every other seat`() {
        // One upgrade each, the same icon (+1 Defense): Charged Boomerang tucked and Electrocute kept in
        // hand, or Control Gravity tucked and Kinetic Repulse kept. Hands of the same size, other cards.
        val a = session(
            tucked = listOf("wasp-red-2b"),
            kept = listOf("wasp-green-1"),
            hand = listOf("wasp-gold", "wasp-red-2a", "wasp-blue-1"),
            level = 2,
        )
        val b = session(
            tucked = listOf("wasp-blue-2a"),
            kept = listOf("wasp-green-1"),
            hand = listOf("wasp-gold", "wasp-red-1", "wasp-blue-2b"),
            level = 2,
        )
        for (viewer in others) {
            assertThat(b.view(viewer)).isEqualTo(a.view(viewer))
        }
        assertThat(a.view("wasp")).isNotEqualTo(b.view("wasp"))
    }

    @Test
    fun `after the reveal - the played card is faceup to everyone, the item icons still public, the rest hidden`() {
        val s = session(phase = PositionPhase.Resolving(next = "wasp"))
        for (viewer in others) {
            val wasp = s.view(viewer).wasp()
            assertThat(wasp.played?.cardId).isEqualTo("wasp-silver")
            assertThat(wasp.played?.facedown).isFalse()
            // Static Barrier prints initiative 13; the Electrocute item adds 1 (p.7 counts items).
            assertThat(wasp.initiative).isEqualTo(14)
            assertThat(wasp.itemStats).containsExactly(Stat.DEFENSE, Stat.INITIATIVE)
            assertThat(wasp.items).isEmpty()
            assertThat(wasp.hand).isNull()
        }
    }

    @Test
    fun `the review and the kept boards show the same item icons and hide the same cards`() {
        val s = session()
        for (viewer in others) {
            val viewers = setOfNotNull(viewer)
            val kept = s.positionAt(2, 3, viewers)!!.wasp()
            assertThat(kept.items).isEmpty()
            assertThat(kept.hand).isNull()
            assertThat(kept.itemStats).containsExactly(Stat.DEFENSE, Stat.INITIATIVE)
        }
    }

    @Test
    fun `Min's Perfect Self - the log names the item it gives, not the Tier II card tucked`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-green-3b")
                level = 5
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "min")
        d.option(1) // take a Tier II card as a permanent item
        d.expect(DecisionKind.CHOOSE_CARD, "min")
        d.card("min-blue-2b")
        val name = TestData.catalog.heroes.first { it.id == "min" }.cards.first { it.id == "min-blue-2b" }.name
        assertThat(d.texts().filter { name in it }).isEmpty()
        assertThat(d.texts()).contains("Min adds a Tier II card from the deck as an item: +1 Initiative.")
        // Arien sees the new icon, not the card.
        val min = d.s.view("arien").heroes.first { it.heroId == "min" }
        assertThat(min.items).isEmpty()
        assertThat(min.itemStats).containsExactly(Stat.INITIATIVE)
    }
}
