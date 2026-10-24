package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Silverarrow the Pathfinder: every card, clause by clause (text from `catalog/heroes/silverarrow.json`). */
class SilverarrowTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    private fun minionAt(d: dev.quinton.goa.core.game.Driver, h: Hex) = d.s.state.minions.firstOrNull { it.at == h }

    // ---- Shoot and Scoot (gold) / Keep the Distance (Handicap) ----------------------------------

    @Test
    fun `Shoot and Scoot - Target a unit at maximum range, not nearer`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-gold") } // Range 2
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // 1 away
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "silverarrow")
        assertThat(d.units().map { d.at(it) }).containsExactly(Hex(3, -2), Hex(-1, -2))
    }

    @Test
    fun `Shoot and Scoot - a Range item moves the maximum range`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) {
                played("silverarrow-gold")
                items("silverarrow-blue-3b") // +1 Range: maximum range 3
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 2 away: no longer at maximum range
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6)) // so no defeat pushes the Lane
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        assertThat(minionAt(d, Hex(4, -2))).isNull()
        assertThat(minionAt(d, Hex(3, -2))).isNotNull()
    }

    @Test
    fun `Shoot and Scoot - After the attack, you may fast travel to an adjacent zone, not within your own`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(-2, -2)) { played("silverarrow-gold") } // lane-1-1
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-blue-1") } // lane-1-2, 2 away
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        // lane-1-1's neighbours are lane-1-2 (Arien is there) and the red Throne.
        assertThat(d.spaces().map { geo.zoneOf(it) }.toSet()).containsExactly("red-throne")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        val to = d.spaces().first()
        d.space(to)
        assertThat(d.at("silverarrow")).isEqualTo(to)
    }

    @Test
    fun `Shoot and Scoot - FAQ - she may fast travel even when she cannot perform movement actions`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("silverarrow", Team.RED, Hex(-2, -2)) { played("silverarrow-gold") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-blue-1") }
            hero("xargatha", Team.BLUE, Hex(-1, -1)) // Stone Gaze from last turn, Silverarrow 2 away
            effect("xargatha", "xargatha-blue-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        assertThat(d.actions()).doesNotContain(ActionType.MOVEMENT) // petrified
        d.action("silverarrow", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        assertThat(d.spaces().map { geo.zoneOf(it) }.toSet()).containsExactly("red-throne")
    }

    @Test
    fun `Shoot and Scoot - an effect that forbids fast travel stops it`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(-2, -2)) { played("silverarrow-gold") }
            hero("brogan", Team.BLUE, Hex(0, -2)) { hand("brogan-gold") } // 2 away
            hero("arien", Team.BLUE, Hex(-1, -2)) // Deluge: adjacent enemy heroes cannot fast travel
            effect("arien", "arien-blue-3b", Timing.THIS_TURN, ActionKind.MOVEMENT)
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.defend("brogan-gold")
        assertThat(d.at("silverarrow")).isEqualTo(Hex(-2, -2))
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // the turn went on: no fast travel asked
    }

    @Test
    fun `Keep the Distance - Target a hero at maximum range`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) {
                handicap = true
                played("silverarrow-handicap")
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // 2 away, but a minion
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien") // the only valid target, chosen without a question
    }

    // ---- Trailblazer (silver) -------------------------------------------------------------------

    @Test
    fun `Trailblazer - You may fast travel, if able (the same zone included)`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-silver") }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        val zones = d.spaces().map { geo.zoneOf(it) }.toSet()
        assertThat(zones).containsAtLeast("lane-1-2", "lane-1-1", "red-side")
        d.space(-1, -5)
        assertThat(d.at("silverarrow")).isEqualTo(Hex(-1, -5))
    }

    @Test
    fun `Trailblazer - This round, friendly heroes in radius ignore obstacles on movement actions (FAQ)`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(3, -5)) { played("silverarrow-silver") } // radius 4
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-1") } // 3 away, boxed in
            hero("arien", Team.BLUE, Hex(7, -2))
            for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) {
                minion(Team.RED, MinionType.MELEE, h)
            }
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.decline() // no fast travel
        d.action("xargatha", ActionType.MOVEMENT)
        val spaces = d.spaces()
        assertThat(spaces).contains(Hex(3, -2)) // through the minion at (2,-2)
        assertThat(spaces).containsNoneOf(Hex(2, -2), Hex(1, -3)) // never ends on an obstacle
    }

    @Test
    fun `Trailblazer - enemy heroes are not helped`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(3, -5)) { played("silverarrow-silver") }
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-blue-1") } // boxed in by his own minions
            for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) {
                minion(Team.BLUE, MinionType.MELEE, h)
            }
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.action("arien", ActionType.MOVEMENT) // nowhere to go: no question
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Trailblazer - FAQ - ignoring obstacles does not change zone adjacency for fast travel`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(-1, -5)) { played("silverarrow-silver") }
            hero("xargatha", Team.RED, Hex(-3, -6)) { played("xargatha-red-1") } // red-side, 3 away
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.decline()
        d.action("xargatha", ActionType.FAST_TRAVEL)
        val zones = d.spaces().map { geo.zoneOf(it) }.toSet()
        assertThat(zones).containsExactly("red-side", "red-throne", "lane-1-2")
    }

    // ---- Snap Shot / Opportunity Shot / Clear Shot (red) ----------------------------------------

    private fun snapPosition(choice: Int) = pos {
        clearMinions()
        hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-red-1") } // Range 2
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // in range, alone
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // in range, next to a unit
        minion(Team.RED, MinionType.MELEE, Hex(-1, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1)) // adjacent to Silverarrow
        phase = PositionPhase.Resolving(next = "silverarrow")
    }.also { d ->
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "silverarrow")
        d.option(choice)
    }

    @Test
    fun `Snap Shot - first bullet - a unit in range not adjacent to any other unit (you included)`() {
        val d = snapPosition(0)
        // The only such unit is at (3,-2): the one at (-1,-2) has a neighbour, the one at (1,-1) is next to her.
        assertThat(minionAt(d, Hex(3, -2))).isNull()
        assertThat(minionAt(d, Hex(-1, -2))).isNotNull()
        assertThat(minionAt(d, Hex(1, -1))).isNotNull()
    }

    @Test
    fun `Snap Shot - second bullet - a unit adjacent to you`() {
        val d = snapPosition(1)
        assertThat(minionAt(d, Hex(1, -1))).isNull()
        assertThat(minionAt(d, Hex(3, -2))).isNotNull()
    }

    @Test
    fun `Opportunity Shot and Clear Shot - the same bullets with Range 3 and 4`() {
        for ((card, h) in listOf("silverarrow-red-2a" to Hex(4, -2), "silverarrow-red-3a" to Hex(-3, -2))) {
            val d = pos {
                clearMinions()
                hero("silverarrow", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(7, -2))
                minion(Team.BLUE, MinionType.MELEE, h) // alone, at the card's Range
                minion(Team.BLUE, MinionType.MELEE, Hex(1, -6)) // 4 away, next to a friend
                minion(Team.RED, MinionType.MELEE, Hex(0, -6))
                phase = PositionPhase.Resolving(next = "silverarrow")
            }
            d.action("silverarrow", ActionType.PRIMARY) // one possible bullet, one target: no question
            assertThat(minionAt(d, h)).isNull()
            assertThat(minionAt(d, Hex(1, -6))).isNotNull()
        }
    }

    // ---- Disorient (green) ----------------------------------------------------------------------

    @Test
    fun `Disorient - Move an enemy unit adjacent to you 1 space, if you do, you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-green-1") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(2, -2)) == 1 }).isTrue()
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue() // mandatory
        d.space(3, -2)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) == 1 }).isTrue()
        d.space(2, -2)
        assertThat(d.at("silverarrow")).isEqualTo(Hex(2, -2))
        assertThat(minionAt(d, Hex(3, -2))).isNotNull()
    }

    @Test
    fun `Disorient - no enemy unit adjacent, no Primary action`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-green-1") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // friendly
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Divert Attention / Lead Astray (green) -------------------------------------------------

    @Test
    fun `Divert Attention - Move an enemy unit up to 2, if you do, move up to that number in a straight line`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-green-2a") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue() // "up to"
        d.space(4, -2) // 2 spaces
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        val spaces = d.spaces()
        assertThat(spaces).contains(Hex(3, -2))
        assertThat(spaces).doesNotContain(Hex(2, -1)) // 2 away but not in a straight line
        assertThat(spaces.all { HexGrid.inStraightLine(Hex(1, -2), it) && HexGrid.distance(Hex(1, -2), it) <= 2 })
            .isTrue()
    }

    @Test
    fun `Divert Attention - if the unit is not moved, you do not move`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-green-2a") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.decline()
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.at("silverarrow")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Lead Astray - up to 3, then up to that number in a straight line`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-green-3a") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.space(5, -3) // 3 spaces away from (2,-2)
        assertThat(d.spaces()).contains(Hex(4, -2)) // 3 in a straight line
    }

    // ---- Fae Healing / Nature's Blessing (green alternates) -------------------------------------

    private fun healing(card: String) = pos {
        clearMinions()
        hero("silverarrow", Team.RED, Hex(1, -2)) {
            played(card)
            discard("silverarrow-red-1")
        }
        hero("xargatha", Team.RED, Hex(2, -2)) { hand("xargatha-gold") } // the rest is discarded
        hero("arien", Team.BLUE, Hex(3, -4)) { hand("arien-gold") }
        phase = PositionPhase.Resolving(next = "silverarrow")
    }

    @Test
    fun `Fae Healing - a hero in radius (friend or enemy, never you) may retrieve, if they do, they gain 1 coin`() {
        val d = healing("silverarrow-green-2b")
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "silverarrow")
        assertThat(d.units()).containsExactly("xargatha", "arien") // FAQ: not herself
        d.unit("xargatha")
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha")
        d.card("xargatha-red-1")
        assertThat(d.zoneOf("xargatha", "xargatha-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.hero("xargatha").coins).isEqualTo(1)
    }

    @Test
    fun `Fae Healing - a hero who does not retrieve gains nothing`() {
        val d = healing("silverarrow-green-2b")
        d.action("silverarrow", ActionType.PRIMARY)
        d.unit("arien")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.decline()
        assertThat(d.hero("arien").coins).isEqualTo(0)
    }

    @Test
    fun `Nature's Blessing - 2 coins`() {
        val d = healing("silverarrow-green-3b")
        d.action("silverarrow", ActionType.PRIMARY)
        d.unit("arien")
        d.card("arien-red-1")
        assertThat(d.hero("arien").coins).isEqualTo(2)
    }

    // ---- Grasping Roots / Entangling Vines / Grappling Branches (blue) ---------------------------

    @Test
    fun `Grasping Roots - This turn, enemy heroes in radius move at most 1 space (FAQ - checked at the start)`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") } // 2 away, Movement 3
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-blue-1") } // 3 away, Movement 2
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.action("arien", ActionType.MOVEMENT)
        assertThat(d.spaces().all { HexGrid.distance(Hex(3, -2), it) == 1 }).isTrue()
        d.decline()
        // Brogan starts outside the radius: he may move his full 2, even into the radius.
        d.action("brogan", ActionType.MOVEMENT)
        assertThat(d.spaces()).contains(Hex(2, -5))
        assertThat(d.spaces()).contains(Hex(3, -4)) // 2 from Silverarrow
    }

    @Test
    fun `Grasping Roots - enemy heroes in radius cannot fast travel`() {
        fun position(first: String) = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(-2, -2)) { played("silverarrow-blue-1") } // lane-1-1
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-blue-1") } // lane-1-2, 2 away
            phase = PositionPhase.Resolving(next = first)
        }
        assertThat(position("arien").actions()).contains(ActionType.FAST_TRAVEL)
        val d = position("silverarrow")
        d.action("silverarrow", ActionType.PRIMARY)
        assertThat(d.actions()).doesNotContain(ActionType.FAST_TRAVEL)
    }

    @Test
    fun `Entangling Vines and Grappling Branches - radius 3 and 4`() {
        for ((card, h) in listOf("silverarrow-blue-2a" to Hex(4, -2), "silverarrow-blue-3a" to Hex(1, -6))) {
            val d = pos {
                clearMinions()
                hero("silverarrow", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, h) { played("arien-blue-1") } // at the card's radius
                phase = PositionPhase.Resolving(next = "silverarrow")
            }
            d.action("silverarrow", ActionType.PRIMARY)
            d.action("arien", ActionType.MOVEMENT)
            assertThat(d.spaces().all { HexGrid.distance(h, it) == 1 }).isTrue()
        }
    }

    // ---- Warning Shot / Treetop Sentinel (blue alternates) --------------------------------------

    @Test
    fun `Warning Shot - End of turn, an enemy hero in radius discards a card, if able (you choose the hero)`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-blue-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -4)) { hand("brogan-gold") }
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        // Nothing happens until the end of the turn: the turn ends at once (no other card to resolve).
        d.expect(DecisionKind.CHOOSE_UNIT, "silverarrow")
        assertThat(d.units()).containsExactly("arien", "brogan")
        d.unit("arien")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Treetop Sentinel - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-blue-3b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("silverarrow").coins).isEqualTo(1)
    }

    // ---- Long Shot / Rain of Arrows (red alternates) --------------------------------------------

    @Test
    fun `Long Shot - Target a unit at maximum range (3)`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-red-2b") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 2
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        assertThat(minionAt(d, Hex(4, -2))).isNull()
        assertThat(minionAt(d, Hex(3, -2))).isNotNull()
    }

    private fun rain() = pos {
        clearMinions()
        hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-red-3b") } // Range 3
        hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
        hero("brogan", Team.BLUE, Hex(-2, -2)) { hand("brogan-gold") }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // 3 away
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // adjacent
        minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
        phase = PositionPhase.Resolving(next = "silverarrow")
    }

    @Test
    fun `Rain of Arrows - a hero targeted, repeat once on a different hero (mandatory), then may repeat on a minion`() {
        val d = rain()
        d.action("silverarrow", ActionType.PRIMARY)
        assertThat(d.units()).hasSize(3)
        d.unit("arien")
        d.defend("arien-blue-1")
        // No question: the repeat on a different hero is mandatory, and Brogan is the only one.
        d.defend("brogan-gold")
        d.expect(DecisionKind.CONFIRM, "silverarrow")
        d.yes()
        assertThat(minionAt(d, Hex(1, -5))).isNull()
        assertThat(minionAt(d, Hex(2, -2))).isNotNull()
    }

    @Test
    fun `Rain of Arrows - a minion targeted first, no repeat`() {
        val d = rain()
        d.action("silverarrow", ActionType.PRIMARY)
        d.answer(Choice.Unit(d.s.state.minions.first { it.at == Hex(1, -5) }.id))
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Rain of Arrows - no different hero at maximum range, so no repeat on a minion either`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-red-3b") }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(minionAt(d, Hex(1, -5))).isNotNull()
    }

    // ---- Wild Hunt (Ultimate) -------------------------------------------------------------------

    @Test
    fun `Wild Hunt - before an action, you may move 2 spaces in a straight line`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(-1, -5)) {
                played("silverarrow-red-2b") // Long Shot, Range 3
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-blue-1") } // 5 away
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        // RULINGS 4: the move before the action may make the attack possible.
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("silverarrow", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        assertThat(d.spaces().all { HexGrid.distance(Hex(-1, -5), it) == 2 && HexGrid.inStraightLine(Hex(-1, -5), it) })
            .isTrue()
        d.space(1, -5)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Wild Hunt - also before a Hold action`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) {
                played("silverarrow-gold")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.HOLD)
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        d.space(3, -2)
        assertThat(d.at("silverarrow")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Wild Hunt - also before a Defense action (p14 - defending is an action)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") } // Attack 6
            hero("silverarrow", Team.RED, Hex(2, -2)) {
                hand("silverarrow-red-3a") // Defense 6
                ultimate()
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("silverarrow-red-3a")
        d.expect(DecisionKind.CHOOSE_SPACE, "silverarrow")
        d.space(4, -2)
        assertThat(d.at("silverarrow")).isEqualTo(Hex(4, -2))
        assertThat(d.texts().last { it.startsWith("Silverarrow defends") }).contains("Defended")
    }

    // ---- immunity, durations, cancelling ---------------------------------------------------------

    @Test
    fun `Long Shot - heavy minion immunity - an immune heavy minion at maximum range is not a target`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-red-2b") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.HEAVY, Hex(4, -2)) // 3 away, immune (a melee friend is in the Zone)
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6)) // 4 away
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Trailblazer - This round - still in force on a later turn`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("silverarrow", Team.RED, Hex(3, -5)) { resolved(1, "silverarrow-silver") }
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-1") }
            hero("arien", Team.BLUE, Hex(7, -2))
            for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) {
                minion(Team.RED, MinionType.MELEE, h)
            }
            effect("silverarrow", "silverarrow-silver", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.MOVEMENT)
        assertThat(d.spaces()).contains(Hex(3, -2))
    }

    @Test
    fun `Grasping Roots - This turn - over at the next turn`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("silverarrow", Team.RED, Hex(1, -2)) { resolved(1, "silverarrow-blue-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") }
            effect("silverarrow", "silverarrow-blue-1", Timing.THIS_TURN, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.MOVEMENT)
        assertThat(d.spaces().any { HexGrid.distance(Hex(3, -2), it) == 3 }).isTrue()
    }

    @Test
    fun `Warning Shot - cancelled when Silverarrow is defeated before the end of the turn`() {
        val d = pos {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2)) { played("silverarrow-blue-2b") } // Initiative 10
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played("arien-red-1") // Initiative 8, Attack 6
                hand("arien-gold")
            }
            phase = PositionPhase.Resolving(next = "silverarrow")
        }
        d.action("silverarrow", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("silverarrow")).isNull()
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // end of turn: nothing to discard
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Silverarrow seated keep every invariant`() {
        dev.quinton.goa.core.game.heroes.batch2.Batch2Games.play("silverarrow", games = 300, seed = 31)
    }
}
