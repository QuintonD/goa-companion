package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.initiativeOf
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Xargatha the Changed: every card, clause by clause (text from `catalog/heroes/xargatha.json`). */
class XargathaTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    // ---- Cleave (gold) / Desperate Cleave (Handicap) -------------------------------------------

    @Test
    fun `Cleave - After the attack may repeat once on a different enemy hero, even after a minion`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.unit(d.s.state.minions.first { it.at == Hex(0, -2) }.id)
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        // The repeat targets an enemy hero only: Arien, not the other minion.
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Desperate Cleave - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-handicap") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Siren's Call (silver) -----------------------------------------------------------------

    @Test
    fun `Sirens Call - an enemy unit not adjacent and in range is moved up to 3 spaces to a space adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-silver") }
            hero("arien", Team.BLUE, Hex(4, -2)) // 3 away
            hero("brogan", Team.BLUE, Hex(2, -2)) // adjacent: not a target
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        // Arien is the only target; Xargatha chooses where he ends, next to her.
        d.expect(DecisionKind.CHOOSE_SPACE, "xargatha")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(1)
        d.space(d.spaces().first())
        assertThat(HexGrid.distance(d.at("arien")!!, Hex(1, -2))).isEqualTo(1)
    }

    @Test
    fun `Sirens Call - "if able" - when no space next to Xargatha can be reached, the target stays`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-silver") }
            hero("arien", Team.BLUE, Hex(4, -5)) // 3 away
            // Every space next to Xargatha is taken.
            for (h in HexGrid.neighbors(Hex(1, -2)).filter { geo.isSpace(it) }) token("token_rock", h)
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -5))
        assertThat(d.zoneOf("xargatha", "xargatha-silver")).isEqualTo(Zone.RESOLVED)
    }

    // ---- Threatening Slash / Deadly Swipe / Lethal Spin (red) ---------------------------------

    @Test
    fun `Threatening Slash - +1 Attack for each other enemy unit adjacent to you, not counting the target`() {
        for ((card, per) in listOf("xargatha-red-1" to 1, "xargatha-red-2a" to 2, "xargatha-red-3a" to 3)) {
            val d = pos {
                clearMinions()
                hero("xargatha", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
                minion(Team.BLUE, MinionType.HEAVY, Hex(0, -2)) // immune, still present (counts)
                minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
                minion(Team.RED, MinionType.MELEE, Hex(0, -1)) // friendly: not counted
                phase = PositionPhase.Resolving(next = "xargatha")
            }
            d.action("xargatha", ActionType.PRIMARY)
            d.unit("arien")
            val printed = TestData.engine.data.card(card).values.attack!!
            assertThat(d.expect(DecisionKind.DEFEND).context.attackTotal).isEqualTo(printed + 2 * per)
        }
    }

    // ---- Long Thrust / Rapid Thrusts (red alternates) ------------------------------------------

    @Test
    fun `Long Thrust - Target a unit in range, +1 Range for each enemy unit adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-2b") } // Range 1+
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1)) // adjacent enemy: Range 2
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        assertThat(d.units()).contains("arien")
        val e = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-2b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        // No adjacent enemy: Range 1, Arien 2 away is out of range and nothing else is adjacent.
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Rapid Thrusts - May repeat once on a different enemy hero`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-3b") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(0, -2)) { hand("brogan-red-1") }
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.yes()
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    // ---- Charm / Control / Dominate (green) ----------------------------------------------------

    @Test
    fun `Charm - before or after movement, you may move an enemy ranged minion in radius up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-green-1") } // Movement 2, radius 2
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.RANGED, Hex(3, -2)) // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // melee: not for Charm
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "xargatha")
        assertThat(d.units()).containsExactly(d.s.state.minions.first { it.type == MinionType.RANGED }.id)
        d.decline() // not before…
        d.expect(DecisionKind.CHOOSE_SPACE, "xargatha") // her movement
        d.decline()
        d.expect(DecisionKind.CHOOSE_UNIT, "xargatha") // …after
        d.unit(d.s.state.minions.first { it.type == MinionType.RANGED }.id)
        d.space(4, -2)
    }

    @Test
    fun `Dominate - any enemy minion, ignoring heavy minion immunity`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-green-3a") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4)) // keeps the heavy immune
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        assertThat(d.units()).contains(d.s.state.minions.first { it.type == MinionType.HEAVY }.id)
    }

    // ---- Stone Gaze / Petrifying Stare / Turn into Statues (blue) ------------------------------

    @Test
    fun `Stone Gaze - Next turn, enemy heroes in radius count as terrain and cannot perform movement actions (FAQ)`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(-5, -4))
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-red-1") } // 2 away
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        // This turn: not yet. Brogan may move.
        assertThat(d.actions()).contains(ActionType.MOVEMENT)
        d.action("brogan", ActionType.HOLD)
        // Next turn.
        d.play("xargatha", "xargatha-gold")
        d.play("arien", "arien-gold")
        d.play("brogan", "brogan-blue-1")
        val g = Game(TestData.engine.data, d.s.state, emptyList(), RunMode.PROBE)
        assertThat(g.isTerrain(Hex(3, -2))).isTrue() // "count as both heroes and terrain"
        assertThat(g.isHero("brogan")).isTrue()
        d.action("xargatha", ActionType.HOLD)
        d.action("arien", ActionType.HOLD)
        // Brogan: no Movement action and no Fast Travel; his Skill (it moves him in card text) is still allowed.
        assertThat(d.actions()).containsExactly(ActionType.PRIMARY, ActionType.HOLD)
    }

    // ---- Constrict / Final Embrace (green alternates) ------------------------------------------

    @Test
    fun `Final Embrace - End of round, defeat an enemy melee or ranged minion adjacent (FAQ - never the heavy)`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("xargatha", Team.RED, Hex(1, -2)) { resolved(1, "xargatha-green-3b") }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -2))
            minion(Team.BLUE, MinionType.RANGED, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            effect("xargatha", "xargatha-green-3b", Timing.END_OF_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.s.state.minions.none { it.type == MinionType.RANGED }).isTrue()
        assertThat(d.texts()).contains("Blue ranged minion is defeated by Xargatha.")
    }

    // ---- Fresh Converts / Devoted Followers (blue alternates) ----------------------------------

    @Test
    fun `Fresh Converts - if you are adjacent to an enemy minion, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-blue-2b"); discard("xargatha-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) // an enemy hero is not a minion
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        assertThat(d.zoneOf("xargatha", "xargatha-gold")).isEqualTo(Zone.DISCARD)
        val e = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-blue-3b"); discard("xargatha-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) // Devoted Followers: any enemy unit
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        e.action("xargatha", ActionType.PRIMARY)
        e.card("xargatha-gold")
        assertThat(e.zoneOf("xargatha", "xargatha-gold")).isEqualTo(Zone.HAND)
    }

    // ---- Metamorphosis (Ultimate) --------------------------------------------------------------

    @Test
    fun `Metamorphosis - +1 Movement and +1 Initiative per adjacent enemy unit, and she may move through obstacles`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-red-1"); ultimate() } // Init 7, Movement 5
            hero("arien", Team.BLUE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        val g = Game(TestData.engine.data, d.s.state, emptyList(), RunMode.PROBE)
        assertThat(g.initiativeOf("xargatha")).isEqualTo(7 + 2)
        d.action("xargatha", ActionType.MOVEMENT)
        // Movement 5 + 2 = 7, through obstacles: (4,-2) is 3 spaces east past Arien.
        val far = d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }
        assertThat(far).isEqualTo(7)
        assertThat(d.spaces()).contains(Hex(3, -2))
    }
}
