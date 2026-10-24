package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Wasp the Warmaiden: every card, clause by clause (text from `catalog/heroes/wasp.json`). */
class WaspTest {
    // ---- Magnetic Dagger (gold) / Crackling Dagger (Handicap) ---------------------------------

    @Test
    fun `Magnetic Dagger - This turn, enemy units in radius cannot be placed by themselves (FAQ - nor fast travel)`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // the target
            hero("arien", Team.BLUE, Hex(3, -4)) { played("arien-green-1") } // 2 away: in radius 3
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("brogan-blue-1")
        // Arien may not place himself with Liquid Leap; moving is still fine (FAQ).
        assertThat(d.actions()).containsExactly(ActionType.MOVEMENT, ActionType.HOLD)
    }

    @Test
    fun `Magnetic Dagger - FAQ - enemies from further away may still place themselves into the radius`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            // 4 away: outside radius 3; Stranger Tide, Range 3.
            hero("arien", Team.BLUE, Hex(4, -6)) { played("arien-green-3a") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("brogan-blue-1")
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.spaces().any { HexGrid.distance(it, Hex(1, -2)) <= 3 }).isTrue()
    }

    @Test
    fun `Crackling Dagger - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-handicap") }
            hero("arien", Team.BLUE, Hex(4, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Static Barrier (silver) ---------------------------------------------------------------

    @Test
    fun `Static Barrier - an enemy hero outside the radius cannot enter it, one inside cannot leave (FAQ)`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-silver") } // radius 2
            hero("brogan", Team.BLUE, Hex(4, -2)) { played("brogan-blue-1") } // 3 away: outside
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-blue-1") } // 1 away: inside
            phase = PositionPhase.Resolving()
        }
        d.action("wasp", ActionType.PRIMARY)
        // Arien (9) first: inside, stays inside.
        d.action("arien", ActionType.MOVEMENT)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isAtMost(2)
        d.decline()
        // Brogan (8): outside, may not enter.
        d.action("brogan", ActionType.MOVEMENT)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isGreaterThan(2)
    }

    @Test
    fun `Static Barrier - FAQ - a swap may cross the barrier`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-silver") }
            hero("arien", Team.BLUE, Hex(4, -2)) { played("arien-green-2b") } // outside
            minion(Team.RED, MinionType.MELEE, Hex(1, -1)) // inside, 3 from Arien
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving()
        }
        d.action("wasp", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(1, -1))
    }

    @Test
    fun `Static Barrier - only enemy heroes' actions are limited, never Wasp's team`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-silver") }
            hero("xargatha", Team.RED, Hex(4, -2)) { played("xargatha-red-1") } // friendly, outside
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving()
        }
        d.action("wasp", ActionType.PRIMARY)
        d.action("xargatha", ActionType.MOVEMENT)
        assertThat(d.spaces().any { HexGrid.distance(it, Hex(1, -2)) <= 2 }).isTrue()
    }

    // ---- Shock / Electrocute / Electroblast (red) ---------------------------------------------

    @Test
    fun `Shock - After the attack, an enemy hero in radius and not adjacent to you discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-1") } // radius 2
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // adjacent: the target
            hero("arien", Team.BLUE, Hex(-1, -2)) { hand("arien-gold", "arien-blue-1") } // 2 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("brogan-blue-1")
        // Brogan is adjacent, so only Arien qualifies; he chooses the card.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Electroblast - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-3a") } // radius 3
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") } // Defense 7 against Attack 6
            hero("arien", Team.BLUE, Hex(-2, -2)) { hand() } // 3 away, nothing in hand
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("wasp").coins).isEqualTo(1)
    }

    // ---- Charged Boomerang / Thunder Boomerang (red alternates) -------------------------------

    @Test
    fun `Charged Boomerang - Target a unit in range and not in a straight line (adjacent ones are)`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-2b") } // Range 3
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // adjacent: in a straight line
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-blue-1") } // (2,-1) away: not in a line
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // in a straight line, 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1)) // not in a line, 2 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        val targets = d.units()
        assertThat(targets).contains("arien")
        assertThat(targets).doesNotContain("brogan")
        assertThat(targets).hasSize(2)
    }

    @Test
    fun `Thunder Boomerang - If you targeted a hero, may repeat once on a different target`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-3b") }
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        // Arien excluded: the minion at (-1,-1) is the only other target not in a straight line.
        assertThat(d.s.state.minions.none { it.at == Hex(-1, -1) }).isTrue()
        assertThat(d.hero("wasp").coins).isEqualTo(2)
    }

    // ---- Stop / Deflect / Reflect Projectiles (green) ------------------------------------------

    @Test
    fun `Deflect Projectiles - if you block, an enemy hero in range other than the attacker discards, if able`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) {
                played("arien-red-2b")
                hand("arien-gold")
            } // ranged attacker
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-2a") }
            hero("brogan", Team.BLUE, Hex(-1, -3)) { hand("brogan-gold", "brogan-silver") } // 1 from Wasp
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("wasp-green-2a")
        // Arien (the attacker) may not be chosen: Brogan is the only other enemy hero in range.
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        assertThat(d.texts()).contains("Wasp defends with Deflect Projectiles: Blocked.")
    }

    @Test
    fun `Reflect Projectiles - the attacker may be the one who discards`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) {
                played("arien-red-2b")
                hand("arien-gold", "arien-silver")
            }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-3a") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("wasp-green-3a")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Deflect Projectiles - FAQ - against a non-ranged attack the block fails and nobody discards`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-2a") }
            hero("brogan", Team.BLUE, Hex(-1, -3)) { hand("brogan-gold") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.yes() // Noble Blade's optional move of Brogan (adjacent to the target)…
        d.space(d.spaces().first())
        d.defend("wasp-green-2a")
        assertThat(d.at("wasp")).isNull()
        assertThat(d.hero("brogan").card("brogan-gold")!!.zone).isEqualTo(Zone.HAND)
    }

    // ---- Lift Up / Control Gravity / Center of Mass (blue) -------------------------------------

    @Test
    fun `Lift Up - move a unit or token in radius 1 space at the same distance, may repeat on it once`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(-5, -4))
            token("token_rock", Hex(3, -2)) // 2 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        // FAQ: "the distance between you and the target must remain unchanged."
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(2)
        d.space(3, -3)
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(2)
        d.space(d.spaces().first())
        // Repeated once: no third move.
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Center of Mass - may repeat up to two times on the same target`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-blue-3a") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            token("token_rock", Hex(3, -2))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.space(3, -3)
        d.yes()
        d.space(d.spaces().first())
        d.expect(DecisionKind.CONFIRM)
        d.yes()
        // The third move has a single possible space here, so it is made without a question.
        if (d.d.kind == DecisionKind.CHOOSE_SPACE) d.space(d.spaces().first())
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
        assertThat(d.texts().count { "Rock moves 1 space" in it }).isEqualTo(3)
    }

    // ---- Telekinesis / Mass Telekinesis (green alternates) ------------------------------------

    @Test
    fun `Telekinesis - place a unit or a token in range, not in a straight line, into a space adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-green-2b") }
            hero("arien", Team.BLUE, Hex(3, -3)) // not in a straight line
            hero("brogan", Team.BLUE, Hex(3, -2)) // in a straight line
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE) // Arien is the only target: placed next to Wasp
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(1)
        d.space(0, -2)
        assertThat(d.at("arien")).isEqualTo(Hex(0, -2))
    }

    // ---- Kinetic Repulse / Kinetic Blast (blue alternates) ------------------------------------

    @Test
    fun `Kinetic Repulse - push up to 2 adjacent enemies 3 spaces, a hero stopped by an obstacle discards`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-blue-2b") }
            // Arien is pushed west the full 3 spaces; Brogan east, stopped by a rock.
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold", "arien-silver") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-gold", "brogan-silver") }
            token("token_rock", Hex(4, -2))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.unit("arien")
        d.unit("brogan")
        assertThat(d.at("arien")).isEqualTo(Hex(-3, -2))
        assertThat(d.at("brogan")).isEqualTo(Hex(3, -2))
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
    }

    @Test
    fun `Kinetic Blast - 3 or 4 spaces, the same distance for every target`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-blue-3b") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.unit("arien") // the only adjacent enemy: no second pick is asked
        d.expect(DecisionKind.CHOOSE_NUMBER)
        d.number(4)
        assertThat(d.at("arien")).isEqualTo(Hex(-4, -2))
    }

    // ---- High Voltage (Ultimate) ---------------------------------------------------------------

    @Test
    fun `High Voltage - after a basic skill, may defeat an enemy minion in radius, an adjacent hero discards`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) {
                played("wasp-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-gold", "arien-silver") }
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 2 away, adjacent to Arien
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "wasp")
        d.unit(d.s.state.minions.first { it.at == Hex(3, -2) }.id)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        assertThat(d.hero("wasp").coins).isEqualTo(2)
    }

    @Test
    fun `Electrocute - radius 3, an enemy hero not adjacent discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-2a") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            hero("arien", Team.BLUE, Hex(-2, -2)) { hand("arien-gold", "arien-silver") } // 3 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Stop Projectiles - Block a ranged attack`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND).context.defensePreview["wasp-green-1"]).isNull() // a Block
        d.defend("wasp-green-1")
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Mass Telekinesis - place a unit or token next to you, may repeat once`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-green-3b") }
            hero("arien", Team.BLUE, Hex(3, -3)) // not in a straight line
            token("token_rock", Hex(-1, -1)) // not in a straight line, 2 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.unit("arien")
        d.space(0, -2)
        d.yes()
        d.space(d.spaces().first())
        assertThat(HexGrid.distance(d.s.state.pieces.single().at!!, Hex(1, -2))).isEqualTo(1)
    }

    @Test
    fun `Control Gravity - radius 3`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-blue-2a") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            token("token_rock", Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(3)
    }
}
