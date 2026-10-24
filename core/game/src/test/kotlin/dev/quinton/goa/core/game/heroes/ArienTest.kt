package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Arien the Tidemaster: every card, clause by clause (text from `catalog/heroes/arien.json`). */
class ArienTest {
    private fun g(d: Driver) = Game(TestData.engine.data, d.s.state, emptyList(), RunMode.PROBE)

    // ---- Noble Blade (gold) / Ceremonial Blade (Handicap) ------------------------------------

    private fun blade(card: String = "arien-gold") = pos {
        clearMinions()
        hero("arien", Team.BLUE, Hex(1, -2)) { played(card) }
        hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-green-2b") } // Defense 3
        hero("xargatha", Team.RED, Hex(-1, -2)) { hand("xargatha-gold") } // adjacent to the target too
        minion(Team.RED, MinionType.MELEE, Hex(-1, -1)) // +1 to Wasp while adjacent; not adjacent to Arien
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        phase = PositionPhase.Resolving(next = "arien")
    }

    @Test
    fun `Noble Blade - Target a unit adjacent to you`() {
        val d = blade()
        d.action("arien", ActionType.PRIMARY)
        // Wasp is the only enemy unit adjacent to Arien: no choice.
        d.expect(DecisionKind.CONFIRM, "arien")
    }

    @Test
    fun `Noble Blade - Before the attack, you may move another unit adjacent to the target 1 space`() {
        val d = blade()
        d.action("arien", ActionType.PRIMARY)
        d.yes()
        // "another unit" never includes Arien: the minion or Xargatha.
        assertThat(d.units()).containsExactly("xargatha", d.s.state.minions.first { it.team == Team.RED }.id)
        val m = d.s.state.minions.first { it.team == Team.RED }.id
        d.unit(m)
        d.space(-1, 0) // no longer adjacent to Wasp
        // The move came before the defense: Wasp loses the +1 and is defeated (3 < 4).
        d.defend("wasp-green-2b")
        assertThat(d.texts()).contains("Wasp defends with Telekinesis: Defense 3 = 3. Not enough.")
        assertThat(d.at("wasp")).isNull()
    }

    @Test
    fun `Noble Blade - declining the move, the attack goes on as printed`() {
        val d = blade()
        d.action("arien", ActionType.PRIMARY)
        d.decline()
        d.defend("wasp-green-2b")
        assertThat(d.texts()).contains("Wasp defends with Telekinesis: Defense 3 + 1 (adjacent friendly melee minion) = 4. Defended.")
    }

    @Test
    fun `Ceremonial Blade - Target a hero adjacent to you, you may move a minion adjacent to the target`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-handicap") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-green-2b") }
            hero("xargatha", Team.RED, Hex(-1, -2)) { hand("xargatha-gold") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // adjacent to Arien: not a hero, not a target
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.yes()
        // Only a minion adjacent to the target may be moved (not Xargatha): the one at (0,-1), so no choice.
        d.expect(DecisionKind.CHOOSE_SPACE, "arien")
        assertThat(d.d.context.subjectId).isEqualTo(d.s.state.minions.first { it.at == Hex(0, -1) }.id)
    }

    // ---- Spell Break (silver) -----------------------------------------------------------------

    @Test
    fun `Spell Break - This turn, enemy heroes in radius cannot perform skill actions, except on gold cards`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-silver") } // 13, radius 3
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-1") } // Lift Up: skill, in radius
            hero("xargatha", Team.RED, Hex(5, -5)) { played("xargatha-blue-1") } // Stone Gaze: skill, 4 away
            hero("misa", Team.RED, Hex(1, -4)) { hand("misa-gold", "misa-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.actions()).containsExactly(ActionType.MOVEMENT, ActionType.HOLD) // Wasp: no skill, movement still fine
        val g = g(d)
        // "except on gold cards": Misa's gold skill is allowed, her green skill is not.
        assertThat(g.actionAllowed("misa", "misa-gold", ActionKind.SKILL, true)).isTrue()
        assertThat(g.actionAllowed("misa", "misa-green-1", ActionKind.SKILL, true)).isFalse()
        d.action("wasp", ActionType.HOLD)
        // Xargatha is out of radius: her skill is allowed.
        assertThat(d.actions()).contains(ActionType.PRIMARY)
    }

    // ---- Dangerous Current / Raging Stream / Violent Torrent (red) ------------------------------

    private fun current(card: String, behind: Hex, victimHand: List<String> = listOf("xargatha-gold", "xargatha-red-1")) = pos {
        clearMinions()
        hero("arien", Team.BLUE, Hex(2, -2)) { played(card) }
        hero("wasp", Team.RED, Hex(1, -2)) { hand("wasp-blue-1") }
        hero("xargatha", Team.RED, behind) { hand(*victimHand.toTypedArray()) }
        hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-green-1") }
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        phase = PositionPhase.Resolving(next = "arien")
    }

    @Test
    fun `Dangerous Current - Up to 1 enemy hero in the 2 spaces behind the target discards a card`() {
        val d = current("arien-red-1", Hex(0, -2)) // 1 behind
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT)
        assertThat(d.units()).containsExactly("xargatha")
        d.unit("xargatha")
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha") // the victim chooses (p.10)
        d.card("xargatha-red-1")
        // Then the attack on the target.
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.zoneOf("xargatha", "xargatha-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Dangerous Current - "or is defeated" with no card to discard, and "Up to 1" may be nobody (FAQ)`() {
        val d = current("arien-red-1", Hex(-1, -2), victimHand = emptyList()) // 2 behind, empty hand
        d.action("arien", ActionType.PRIMARY)
        d.unit("xargatha")
        assertThat(d.at("xargatha")).isNull()
        assertThat(d.hero("arien").coins).isEqualTo(1) // Xargatha's level
        d.expect(DecisionKind.DEFEND, "wasp")

        val e = current("arien-red-1", Hex(-1, -2))
        e.action("arien", ActionType.PRIMARY)
        e.decline() // nobody: FAQ "No, this effect says Up to 1 enemy hero"
        e.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Dangerous Current reaches 2 spaces, Raging Stream 3, Violent Torrent 5`() {
        val d = current("arien-red-1", Hex(-2, -2)) // 3 behind
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp") // nobody eligible behind
        val e = current("arien-red-2a", Hex(-2, -2))
        e.action("arien", ActionType.PRIMARY)
        assertThat(e.units()).containsExactly("xargatha")
        val f = current("arien-red-3a", Hex(-3, -2)) // 4 behind, past terrain? (-3,-2) is a space of lane-1-1
        f.action("arien", ActionType.PRIMARY)
        assertThat(f.units()).containsExactly("xargatha")
    }

    @Test
    fun `Violent Torrent - May repeat once on a different unit`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-3a") }
            hero("wasp", Team.RED, Hex(1, -2)) { hand("wasp-blue-1") }
            hero("xargatha", Team.RED, Hex(3, -3)) { hand("xargatha-gold") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        // The repeat targets a different unit: Xargatha only (Wasp excluded), chosen automatically.
        d.expect(DecisionKind.DEFEND, "xargatha")
    }

    // ---- Rogue Wave / Tidal Blast (red alternates) ---------------------------------------------

    @Test
    fun `Rogue Wave - Target a unit in range, After the attack you may push an enemy unit adjacent to you up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") } // 2 away: in Range 2
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // adjacent to Arien
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        val m = d.s.state.minions.first { it.team == Team.RED }.id
        d.unit(m)
        d.expect(DecisionKind.CHOOSE_NUMBER)
        assertThat(d.d.options).hasSize(3) // 0, 1, 2
        d.number(2)
        assertThat(d.at(m)).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Tidal Blast - push up to 3 spaces`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-red-3b") }
            hero("wasp", Team.RED, Hex(-2, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.unit(d.s.state.minions.first { it.team == Team.RED }.id)
        assertThat(d.d.options).hasSize(4)
    }

    // ---- Liquid Leap / Magical Current / Stranger Tide (green) ---------------------------------

    @Test
    fun `Liquid Leap - p12 example - into a space in range without a spawn point and not adjacent to an empty one`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-green-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        val s = d.spaces()
        assertThat(s).containsAtLeast(Hex(4, -6), Hex(5, -5))
        assertThat(s).doesNotContain(Hex(3, -4)) // a spawn point
        assertThat(s).doesNotContain(Hex(4, -4)) // adjacent to the empty spawn point (3,-4)
        assertThat(s).doesNotContain(Hex(2, -6)) // adjacent to the empty spawn point (2,-7)
        for (h in s) assertThat(dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(3, -5), h)).isAtMost(2)
        d.space(4, -6)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -6))
    }

    @Test
    fun `Liquid Leap - a spawn point with a unit on it is not empty, so spaces next to it are fine`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-green-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(4, -4))
    }

    @Test
    fun `Magical Current reaches Range 3, Stranger Tide may land next to an empty spawn point`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-green-3a") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(4, -4))
        assertThat(d.spaces()).doesNotContain(Hex(3, -4))
        val e = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-green-2a") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "arien")
        }
        e.action("arien", ActionType.PRIMARY)
        assertThat(e.spaces().any { dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(3, -5), it) == 3 }).isTrue()
    }

    @Test
    fun `FAQ - Arien's green placement is not movement, so a hero who cannot perform movement actions may still use it`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(2, -5)) { resolved(1, "xargatha-blue-1") }
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-green-1") }
            effect("xargatha", "xargatha-blue-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).containsExactly(ActionType.PRIMARY, ActionType.HOLD) // petrified: no movement action
    }

    // ---- Arcane Whirlpool / Ebb and Flow (green alternates) ------------------------------------

    @Test
    fun `Arcane Whirlpool - Swap with an enemy minion in range (not movement)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-green-2b") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5)) // 3 away
            minion(Team.RED, MinionType.HEAVY, Hex(0, -1)) // immune: not a target
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // friendly: not a target
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        val m = d.s.state.minions.first { it.team == Team.RED && it.type == MinionType.MELEE }
        assertThat(d.at("arien")).isEqualTo(Hex(4, -5))
        assertThat(m.at).isEqualTo(Hex(1, -2))
        assertThat(d.texts().any { "swap places" in it }).isTrue()
    }

    @Test
    fun `Ebb and Flow - if the minion was adjacent, may repeat once`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-green-3b") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // adjacent
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.unit(d.s.state.minions.first { it.at == Hex(0, -2) }.id)
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        d.expect(DecisionKind.CHOOSE_UNIT)
    }

    // ---- Duelists (blue) -----------------------------------------------------------------------

    @Test
    fun `Expert Duelist - immune to attack actions of other enemy heroes this turn, not of this attacker`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-2a", "arien-gold"); played("arien-green-1") }
            hero("xargatha", Team.RED, Hex(2, -2)) { played("xargatha-gold") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("arien-blue-2a")
        assertThat(d.s.state.effects.map { it.cardId }).contains("arien-blue-2a")
        // Xargatha (another enemy hero) cannot target Arien: her Cleave has no target.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Master Duelist - the immunity lasts the round`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-3a", "arien-gold"); played("arien-green-1") }
            hero("xargatha", Team.RED, Hex(2, -2)) { played("xargatha-green-1") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("arien-blue-3a")
        d.action("xargatha", ActionType.HOLD)
        d.action("arien", ActionType.HOLD)
        assertThat(d.s.state.turn).isEqualTo(2)
        assertThat(d.s.state.effects.single().timing).isEqualTo(Timing.THIS_ROUND)
        val g = g(d)
        assertThat(g.canTargetForTest("arien", "xargatha")).isFalse()
        assertThat(g.canTargetForTest("arien", "wasp")).isTrue()
    }

    // ---- Slippery Ground / Deluge (blue alternates) --------------------------------------------

    @Test
    fun `Slippery Ground - adjacent enemy heroes cannot fast travel`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(-1, -2)) { played("arien-blue-2b") } // lane-1-2
            hero("wasp", Team.RED, Hex(-2, -2)) { played("wasp-blue-1") } // lane-1-1, adjacent
            phase = PositionPhase.Resolving(next = "wasp")
        }
        assertThat(d.actions()).contains(ActionType.FAST_TRAVEL) // before Arien's card
        val e = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(-1, -2)) { played("arien-blue-2b") }
            hero("wasp", Team.RED, Hex(-2, -2)) { played("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        e.action("arien", ActionType.PRIMARY)
        e.decline() // Arien doesn't move
        assertThat(e.actions()).doesNotContain(ActionType.FAST_TRAVEL)
        e.action("wasp", ActionType.MOVEMENT)
        for (h in e.spaces()) assertThat(dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(-2, -2), h)).isEqualTo(1)
    }

    @Test
    fun `Deluge - enemy heroes in radius (radius items count)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-blue-3b"); items("arien-green-3a") } // +1 Radius
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-1") } // 2 away
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.decline()
        d.action("wasp", ActionType.MOVEMENT)
        for (h in d.spaces()) assertThat(dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(-1, -2), h)).isEqualTo(1)
    }

    // ---- Living Tsunami (Ultimate) -------------------------------------------------------------

    @Test
    fun `Living Tsunami - once per turn, before performing an Attack action, you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-3a"); ultimate() }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") } // 2 away: not adjacent yet
            hero("xargatha", Team.RED, Hex(0, -1)) { hand("xargatha-gold") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "arien")
        d.space(1, -2)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.yes() // repeat on a different unit
        // Once per turn: no second move before the repeated attack.
        d.expect(DecisionKind.DEFEND, "xargatha")
    }

    private fun Game.canTargetForTest(target: String, attacker: String) = canTarget(target, attacker, ActionKind.ATTACK, null)
}
