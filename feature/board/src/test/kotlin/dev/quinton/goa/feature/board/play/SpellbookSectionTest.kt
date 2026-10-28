package dev.quinton.goa.feature.board.play

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.cardVerb
import dev.quinton.goa.feature.board.play.table.castWith
import dev.quinton.goa.feature.board.play.table.catalogSpellbook
import dev.quinton.goa.feature.board.play.table.extrasGroups
import dev.quinton.goa.feature.board.play.table.extrasTitle
import dev.quinton.goa.feature.board.play.table.gridColumns
import dev.quinton.goa.feature.board.play.table.namedSpells
import dev.quinton.goa.feature.board.play.table.offeredCards
import dev.quinton.goa.feature.board.play.table.spellTierTitle
import dev.quinton.goa.feature.board.play.table.spellbookSeats
import dev.quinton.goa.feature.board.play.table.spellbookUi
import dev.quinton.goa.feature.board.play.table.ultimateCard
import dev.quinton.goa.feature.board.play.table.unavailableCardLine
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

    // ---- The Spellbook at any point (extends ADR-040) ------------------------------------------

    /** Gydion resolves Cantrip next and chose Primary: "Choose and cast one spell in the spellbook". */
    private fun casting(removed: List<String> = listOf(RETREAT)): EngineSession =
        position(engine, "forgotten-island") {
            clearMinions()
            round = 2
            hero("gydion", Team.RED, Hex(1, -2)) {
                played("gydion-gold")
                extra(*(spells - removed.toSet()).toTypedArray(), tag = "spellbook")
                if (removed.isNotEmpty()) extra(*removed.toTypedArray(), tag = "removed")
            }
            hero("wasp", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // adjacent
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away
            phase = PositionPhase.Resolving(next = "gydion")
        }.also { s ->
            val d = s.pending!!
            s.answer(d.id, d.options.first { it is Choice.Action && it.action == ActionType.PRIMARY })
            check(s.pending?.kind == DecisionKind.CHOOSE_CARD)
        }

    private fun asked(s: EngineSession, mine: String) =
        table(s, mine).copy(decision = s.pending, asking = s.pending?.seat == mine)

    @Test
    fun `the table's Spellbook control is for the person's own Gydion only - not his teammate, not an opponent`() {
        val s = session(prepared = true)
        assertThat(table(s, "gydion").spellbookSeats).containsExactly("gydion")
        for (seat in listOf("wasp", "arien", "brogan")) assertThat(table(s, seat).spellbookSeats).isEmpty()
        // Pass-and-play: the person controls Gydion and Wasp, one control (Wasp has no spellbook).
        val both = table(s, "gydion").copy(mine = setOf("gydion", "wasp"))
        assertThat(both.spellbookSeats).containsExactly("gydion")
        assertThat(spellbookUi(table(s, "arien"), "arien")).isNull()
    }

    @Test
    fun `no peeking - the Spellbook sheet shows an opponent or a teammate the spellbook facedown, a count`() {
        val s = session(prepared = true)
        val own = spellbookUi(table(s, "gydion"), "gydion")!!
        assertThat(own.title).isEqualTo("Your spellbook · Gydion")
        // By Tier now (the hands-on pass): the same spells.
        assertThat(own.groups.first().cards!!.map { it.id }).containsExactlyElementsIn(spells - cast.toSet())
        for (seat in listOf("wasp", "arien")) {
            val ui = spellbookUi(table(s, seat), "gydion")!!
            assertThat(ui.title).isEqualTo("Gydion's spellbook")
            val book = ui.groups.first { it.place == "spellbook" }
            assertThat(book.note).isEqualTo("Facedown: only Gydion can look at the cards in the spellbook.")
            assertThat(book.cards).isNull()
            assertThat(book.count).isEqualTo(20)
            // Nothing of the spellbook's contents anywhere in the sheet.
            val shown = ui.groups.flatMap { it.cards.orEmpty() }.map { it.id } + ui.marks.keys
            assertThat(shown).containsNoneIn(spells - cast.toSet())
        }
    }

    @Test
    fun `the cast question - the sheet marks the spells offered and why the card's others are not`() {
        val s = casting()
        val ui = spellbookUi(asked(s, "gydion"), "gydion")!!
        assertThat(ui.marks.filterValues { it.offered }.keys).containsExactly(SHOCKING_GRASP, MISSILE)
        val retreat = ui.marks.getValue(RETREAT)
        assertThat(retreat.offered).isFalse()
        assertThat(retreat.text).isEqualTo("Not in the spellbook: removed faceup (Prepare Spells)")
        assertThat(ui.asking).isEqualTo(
            "Cantrip asks: Choose and cast one spell in the spellbook. 2 spells are offered; choose in the panel. " +
                "Expeditious Retreat: Not in the spellbook: removed faceup (Prepare Spells).",
        )
        assertThat(ui.askingCard?.id).isEqualTo("gydion-gold")
        // Not the person's question: no marks (the options would be another seat's).
        for (seat in listOf("arien", "wasp")) {
            val other = spellbookUi(asked(s, seat), "gydion")!!
            assertThat(other.marks).isEmpty()
            assertThat(other.asking).isNull()
        }
    }

    @Test
    fun `the cast question's panel shows the card's spell that can't be cast, dimmed, with the engine's reason`() {
        val t = asked(casting(), "gydion")
        val offered = offeredCards(t)
        assertThat(offered.map { it.card.id }).containsExactly(SHOCKING_GRASP, MISSILE, RETREAT).inOrder()
        assertThat(offered.last().choice).isNull()
        assertThat(unavailableCardLine(t, RETREAT)).isEqualTo("Not in the spellbook: removed faceup (Prepare Spells).")
    }

    @Test
    fun `the review's rewound board shows the spellbook as it was then, not the live one`() {
        val s = casting(removed = emptyList())
        s.answer(s.pending!!.id, Choice.Card(MISSILE))
        // Live: Magic Missile was cast (removed faceup).
        val live = spellbookUi(table(s, "gydion"), "gydion")!!
        assertThat(live.groups.first { it.place == "removed" }.cards!!.map { it.id }).containsExactly(MISSILE)
        // The board at the start of the turn: still in the spellbook.
        val then = table(s, "gydion").copy(view = s.positionAt(2, 1, setOf("gydion"))!!)
        val before = spellbookUi(then, "gydion")!!
        assertThat(before.groups.first { it.place == "spellbook" }.cards!!.map { it.id }).contains(MISSILE)
        assertThat(before.groups.first { it.place == "removed" }.count).isEqualTo(0)
        // And to an opponent, then as now, the spellbook is a count.
        val theirs = table(s, "arien").copy(view = s.positionAt(2, 1, setOf("arien"))!!)
        assertThat(spellbookUi(theirs, "gydion")!!.groups.first { it.place == "spellbook" }.cards).isNull()
    }

    @Test
    fun `before the game - the 22 spell cards by tier, as the catalog's hero page lists them`() {
        val heroes = bundledCatalog.boardHeroes()
        val ui = catalogSpellbook(heroes.getValue("gydion"))!!
        assertThat(ui.groups.map { "${it.title} ${it.count}" })
            .containsExactly("Gold (no tier) 3", "Tier I 3", "Tier II 9", "Tier III 6", "Ultimate 1").inOrder()
        assertThat(ui.groups.flatMap { it.cards!! }.map { it.id }).containsExactlyElementsIn(spells)
        assertThat(catalogSpellbook(heroes.getValue("arien"))).isNull()
    }

    // ---- After the hands-on pass (2026-10-26) -------------------------------------------------

    /** Gydion holds Cantrip, Lesser Evocation and Prepare Spells; Fireball and Shield are cast. */
    private fun holding(): EngineSession = position(engine, "forgotten-island") {
        clearMinions()
        hero("gydion", Team.RED, Hex(1, -2)) {
            hand("gydion-gold", "gydion-red-2a", "gydion-silver")
            extra(*(spells - cast.toSet()).toTypedArray(), tag = "spellbook")
            extra(*cast.toTypedArray(), tag = "removed")
        }
        hero("wasp", Team.RED, Hex(0, -5)) { hand("wasp-gold") }
        hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-gold") }
        hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold") }
    }

    @Test
    fun `the Spellbook sheet marks each spell in the book with the card in the hand that casts it`() {
        val ui = spellbookUi(table(holding(), "gydion"), "gydion")!!
        // Fireball is named too, but it was cast: not in the spellbook, no line.
        val expected = mapOf(
            SHOCKING_GRASP to "Cast with Cantrip",
            MISSILE to "Cast with Cantrip",
            RETREAT to "Cast with Cantrip",
            "gydion-spell-burning-hands" to "Cast with Lesser Evocation",
        )
        assertThat(ui.castWith).isEqualTo(expected)
    }

    @Test
    fun `no peeking - the hand's cast lines are for Gydion's own player only`() {
        val s = holding()
        for (seat in listOf("wasp", "arien", "brogan")) {
            assertThat(castWith(table(s, seat), "gydion")).isEmpty()
            assertThat(spellbookUi(table(s, seat), "gydion")!!.castWith).isEmpty()
        }
    }

    @Test
    fun `every spell a Gydion card quotes is one of his spell cards, as the engine casts them`() {
        val gydion = bundledCatalog.heroes.first { it.id == "gydion" }
        val book = gydion.extras
        val named = gydion.allCards.filter { it.heroId == "gydion" && it.id !in spells }
            .associate { it.id to namedSpells(it, book).map { s -> s.name } }
        // Every quoted name found (the cards print "Midas Touch", the spell card "Midas' Touch").
        gydion.allCards.filter { it.id !in spells }.forEach { card ->
            val quoted = Regex("[“\"]([^”\"]+)[”\"]").findAll(card.text).count()
            assertThat(namedSpells(card, book)).hasSize(quoted)
        }
        // As GydionHero registers them (core/game).
        assertThat(named["gydion-gold"]).containsExactly("Shocking Grasp", "Magic Missile", "Expeditious Retreat")
        assertThat(named["gydion-red-1"]).containsExactly("Burning Hands")
        assertThat(named["gydion-blue-2b"]).containsExactly("Midas' Touch", "Disintegrate")
        assertThat(named["gydion-green-3b"]).containsExactly("Find Familiar", "Dimension Door", "Cloud Kill")
        assertThat(named["gydion-handicap"]).containsExactly("Expeditious Retreat")
        assertThat(named["gydion-silver"]).isEmpty()
    }

    @Test
    fun `the live spellbook lists its spells by Tier, as the catalog does`() {
        val ui = spellbookUi(table(holding(), "gydion"), "gydion")!!
        assertThat(ui.byTier).isTrue()
        val book = ui.groups.first { it.place == "spellbook" }.cards!!
        assertThat(book.map { it.tier ?: 0 }).isInOrder()
        assertThat(book.map { it.id }).containsExactlyElementsIn(spells - cast.toSet())
        assertThat(spellTierTitle(null)).isEqualTo("Gold (no tier)")
        assertThat(spellTierTitle(2)).isEqualTo("Tier II")
        assertThat(spellTierTitle(4)).isEqualTo("Ultimate")
    }

    @Test
    fun `the spell grid fills the width and takes fewer columns with larger text`() {
        // An S25-sized phone's sheet (411 dp less the margins).
        assertThat(gridColumns(379.dp, 1f)).isEqualTo(3)
        assertThat(gridColumns(379.dp, 1.3f)).isEqualTo(2)
        assertThat(gridColumns(379.dp, 2f)).isEqualTo(2)
        assertThat(gridColumns(352.dp, 2f)).isEqualTo(2)
        assertThat(gridColumns(300.dp, 2f)).isEqualTo(1)
        // A landscape phone's sheet (re-check: three columns left a third of the width empty): six.
        assertThat(gridColumns(686.dp, 1f)).isEqualTo(6)
        assertThat(gridColumns(800.dp, 1f)).isEqualTo(6)
    }

    private companion object {
        const val SHOCKING_GRASP = "gydion-spell-shocking-grasp"
        const val MISSILE = "gydion-spell-magic-missile"
        const val RETREAT = "gydion-spell-expeditious-retreat"
    }
}
