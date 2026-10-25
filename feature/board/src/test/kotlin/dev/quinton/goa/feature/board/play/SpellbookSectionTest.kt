package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.cardVerb
import dev.quinton.goa.feature.board.play.table.extrasGroups
import dev.quinton.goa.feature.board.play.table.extrasTitle
import dev.quinton.goa.feature.board.play.table.ultimateCard
import org.junit.Test

/**
 * What the hero sheet's spellbook section says (ADR-040), on the real engine's views: Gydion's player
 * sees the spells in his spellbook, anyone else a count ("Only Gydion can look at the cards in the
 * spellbook"); the cast spells are faceup to all; "Not yet added" only before Prepare Spells.
 */
class SpellbookSectionTest {
    private val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    private val spells = bundledCatalog.heroes.first { it.id == "gydion" }.extras.map { it.id }
    private val cast = listOf("gydion-spell-fireball", "gydion-spell-shield")

    private fun session(prepared: Boolean, ultimate: Boolean = false): EngineSession =
        position(engine, "forgotten-island") {
            clearMinions()
            hero("gydion", Team.RED, Hex(1, -2)) {
                if (ultimate) ultimate()
                if (prepared) {
                    extra(*(spells - cast.toSet()).toTypedArray(), tag = "spellbook")
                    extra(*cast.toTypedArray(), tag = "removed")
                }
            }
            hero("wasp", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(4, -5))
            hero("brogan", Team.BLUE, Hex(4, -2))
        }

    private fun table(s: EngineSession, mine: String) = TableUi(
        map = bundledMaps.first { it.id == "forgotten-island" },
        view = s.view(mine),
        heroes = bundledCatalog.boardHeroes(),
        cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id },
        mine = setOf(mine),
    )

    @Test
    fun `Gydion's player sees his spellbook, then the cast spells, and no box once prepared`() {
        val t = table(session(prepared = true), "gydion")
        val gydion = t.heroView("gydion")!!
        assertThat(extrasTitle(t, gydion)).isEqualTo("Spellbook")
        val groups = extrasGroups(t, gydion)
        assertThat(groups.map { it.title }).containsExactly("In your spellbook", "Cast · removed faceup").inOrder()
        assertThat(groups[0].count).isEqualTo(20)
        assertThat(groups[0].cards!!.map { it.id }).isEqualTo(spells - cast.toSet())
        assertThat(groups[1].cards!!.map { it.id }).isEqualTo(cast)
    }

    @Test
    fun `his teammate and the opponents see the spellbook facedown, with its count, and the cast spells`() {
        val s = session(prepared = true)
        for (seat in listOf("wasp", "arien")) {
            val t = table(s, seat)
            val groups = extrasGroups(t, t.heroView("gydion")!!)
            assertThat(groups[0].title).isEqualTo("In the spellbook")
            assertThat(groups[0].cards).isNull()
            assertThat(groups[0].count).isEqualTo(20)
            assertThat(groups[0].note).isEqualTo("Facedown: only Gydion can look at the cards in the spellbook.")
            assertThat(groups[1].cards!!.map { it.id }).isEqualTo(cast)
        }
    }

    @Test
    fun `before Prepare Spells - an empty spellbook and the 22 spell cards not yet added, for everyone`() {
        val s = session(prepared = false)
        val own = extrasGroups(table(s, "gydion"), s.view("gydion").heroes.first { it.heroId == "gydion" })
        assertThat(own.map { it.title }).containsExactly("In your spellbook", "Cast · removed faceup", "Not yet added")
            .inOrder()
        assertThat(own[0].note).isEqualTo("Empty until Prepare Spells adds the spell cards.")
        val t = table(s, "arien")
        val theirs = extrasGroups(t, t.heroView("gydion")!!)
        assertThat(theirs.last().cards!!.map { it.id }).isEqualTo(spells)
        // A hero without extras has no section.
        assertThat(extrasGroups(t, t.heroView("arien")!!)).isEmpty()
    }

    @Test
    fun `an unlocked Ultimate is shown as its card - The Archwizard, not the purple Wish spell`() {
        val t = table(session(prepared = true, ultimate = true), "arien")
        assertThat(ultimateCard(t, t.heroView("gydion")!!)?.id).isEqualTo("gydion-purple-4")
        assertThat(ultimateCard(t, t.heroView("arien")!!)).isNull()
    }

    @Test
    fun `the cast question's button says Cast, Find Familiar's says Remove`() {
        assertThat(cardVerb("Choose and cast one spell in the spellbook")).isEqualTo("Cast")
        assertThat(cardVerb("You may remove another spell card from the spellbook (1 of up to 3)")).isEqualTo("Remove")
        assertThat(cardVerb("You may discard a card")).isEqualTo("Discard")
    }
}
