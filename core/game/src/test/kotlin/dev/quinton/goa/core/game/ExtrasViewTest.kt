package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.heroes.gydion.Spellbook
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BOX
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MAGIC_MISSILE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.REMOVED
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SPELLBOOK
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUNBURST
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * A hero's extra cards in the views (`HeroView.extras`, ADR-040): Gydion's spell cards by place.
 * Prepare Spells (gydion-silver): "Add all spell cards to the spellbook. Only Gydion can look at the
 * cards in the spellbook. As each spell is cast, remove it from the spellbook faceup." So the spells in
 * the spellbook are named to Gydion's seat only (not to his teammate either, p.4 "Hidden Information":
 * teammates' hands are hidden too); the removed spells and the ones not added yet are public.
 */
class ExtrasViewTest {
    /** Everyone but Gydion's own seat: his teammate, the enemies, a spectator. */
    private val others = listOf("wasp", "arien", "brogan", null)

    private fun session(block: PositionBuilder.HeroBuilder.() -> Unit, phase: PositionPhase? = null): EngineSession =
        pos {
            clearMinions()
            hero("gydion", Team.RED, Hex(1, -2)) {
                played("gydion-gold")
                block()
            }
            hero("wasp", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(4, -5))
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") }
            phase?.let { this.phase = it }
        }.s

    private fun GameView.gydion(): HeroView = heroes.first { it.heroId == "gydion" }

    private fun HeroView.place(name: String): ExtraPlaceView = extras.first { it.place == name }

    /** The view without what every seat may see of the extras (the open places' cards). */
    private fun GameView.withoutOpenExtras(): GameView = copy(
        heroes = heroes.map { h -> h.copy(extras = h.extras.filterNot { it.open }) },
    )

    @Test
    fun `a new game - the spellbook is empty and all 22 spell cards are in the box, faceup to everyone`() {
        val s = TestData.engine.newGame(
            GameSetup(
                TestData.FOREST,
                listOf(
                    SeatSetup("gydion", Team.RED, Controller.Human),
                    SeatSetup("arien", Team.BLUE, Controller.Human),
                ),
                tieBreaker = Team.RED,
            ),
        )
        for (seat in listOf("gydion", "arien", null)) {
            val g = s.view(seat).gydion()
            assertThat(g.extras.map { it.place }).containsExactly(SPELLBOOK, REMOVED, BOX).inOrder()
            assertThat(g.place(SPELLBOOK).count).isEqualTo(0)
            assertThat(g.place(REMOVED).cardIds).isEmpty()
            assertThat(g.place(BOX).cardIds).containsExactlyElementsIn(Spellbook.ALL).inOrder()
            assertThat(g.place(BOX).open).isTrue()
        }
        assertThat(s.view("gydion").gydion().place(SPELLBOOK).cardIds).isEmpty()
        assertThat(s.view("arien").gydion().place(SPELLBOOK).cardIds).isNull()
        // A hero without extras lists none.
        assertThat(s.view("arien").heroes.first { it.heroId == "arien" }.extras).isEmpty()
    }

    @Test
    fun `the spellbook names its spells to Gydion's seat only, the removed ones to everyone`() {
        val s = session({
            extra(*(Spellbook.ALL - SUNBURST - MAGIC_MISSILE).toTypedArray(), tag = SPELLBOOK)
            extra(SUNBURST, MAGIC_MISSILE, tag = REMOVED)
        })
        val own = s.view("gydion").gydion()
        assertThat(own.place(SPELLBOOK).cardIds).containsExactlyElementsIn(Spellbook.ALL - SUNBURST - MAGIC_MISSILE)
            .inOrder()
        // In the hero JSON's order, whatever order they were removed in.
        assertThat(own.place(REMOVED).cardIds).containsExactly(MAGIC_MISSILE, SUNBURST).inOrder()
        assertThat(own.place(BOX).count).isEqualTo(0)
        for (seat in others) {
            val g = s.view(seat).gydion()
            assertThat(g.place(SPELLBOOK).cardIds).isNull()
            assertThat(g.place(SPELLBOOK).count).isEqualTo(20)
            assertThat(g.place(REMOVED).cardIds).containsExactly(MAGIC_MISSILE, SUNBURST).inOrder()
        }
    }

    @Test
    fun `no peeking - two games differing only in the spellbook's spells look the same to every other seat`() {
        val first = Spellbook.ALL.take(5)
        val second = Spellbook.ALL.drop(5).take(5)
        val a = session({
            extra(*first.toTypedArray(), tag = SPELLBOOK)
            extra(*(Spellbook.ALL - first).toTypedArray(), tag = BOX)
        })
        val b = session({
            extra(*second.toTypedArray(), tag = SPELLBOOK)
            extra(*(Spellbook.ALL - second).toTypedArray(), tag = BOX)
        })
        assertThat(a.view("gydion").gydion().place(SPELLBOOK).cardIds).containsExactlyElementsIn(first)
        assertThat(b.view("gydion").gydion().place(SPELLBOOK).cardIds).containsExactlyElementsIn(second)
        for (seat in others) {
            // The box is open (the same 22 public spell cards): beyond it, nothing differs.
            assertThat(b.view(seat).withoutOpenExtras()).isEqualTo(a.view(seat).withoutOpenExtras())
        }
        // The game-state review keeps the same visibility (ADR-037 builds it through the same views).
        for (viewers in listOf(setOf("wasp"), setOf("arien", "brogan"), emptySet())) {
            val boardA = a.positionAt(1, 1, viewers)!!.withoutOpenExtras()
            val boardB = b.positionAt(1, 1, viewers)!!.withoutOpenExtras()
            assertThat(boardB).isEqualTo(boardA)
            assertThat(boardA.gydion().place(SPELLBOOK).cardIds).isNull()
        }
        assertThat(a.positionAt(1, 1, setOf("gydion"))!!.gydion().place(SPELLBOOK).cardIds)
            .containsExactlyElementsIn(first)
    }

    @Test
    fun `a cast spell is removed faceup - every seat sees it at once, and the count drops`() {
        val s = session(
            { extra(*Spellbook.ALL.toTypedArray(), tag = SPELLBOOK) },
            PositionPhase.Resolving(next = "gydion"),
        )
        val d = Driver(s)
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "gydion")
        d.card(MAGIC_MISSILE)
        for (seat in others + "gydion") {
            val g = s.view(seat).gydion()
            assertThat(g.place(REMOVED).cardIds).containsExactly(MAGIC_MISSILE)
            assertThat(g.place(SPELLBOOK).count).isEqualTo(21)
            assertThat(g.counters[Spellbook.COUNT_IN_BOOK]).isEqualTo(21)
        }
        assertThat(s.view("gydion").gydion().place(SPELLBOOK).cardIds).doesNotContain(MAGIC_MISSILE)
    }
}
