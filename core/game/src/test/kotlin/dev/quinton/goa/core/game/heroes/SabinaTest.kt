package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Sabina the Commander: every card, clause by clause (text from `catalog/heroes/sabina.json`), with the FAQ. */
class SabinaTest {
    // ---- Point Blank Shot (gold) / Bang (Handicap) ---------------------------------------------

    @Test
    fun `Point Blank Shot - target a unit in range, after the attack an adjacent target is pushed 1 space`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        val p = d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(p.context.rangedAttack).isTrue()
        assertThat(p.context.attackTotal).isEqualTo(2)
        d.card("wasp-blue-1")
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Point Blank Shot - FAQ - a range of 1 only reaches adjacent units`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-gold") }
            hero("wasp", Team.RED, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Bang - target a hero adjacent to you, after the attack push the target 1 space`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-handicap") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // adjacent, but not a hero
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
    }

    // ---- Back to Back (silver) -----------------------------------------------------------------

    @Test
    fun `Back to Back - swap with a friendly minion in radius`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-silver") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 2 away
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // an enemy minion is not offered
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away: outside Radius 2
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY) // a single friendly minion in radius: no question
        assertThat(d.at("sabina")).isEqualTo(Hex(3, -2))
        assertThat(d.s.state.minions.single { it.at == Hex(1, -2) }.team).isEqualTo(Team.BLUE)
    }

    // ---- Quickdraw / Gunslinger / Dead Shot (red) ----------------------------------------------

    @Test
    fun `Quickdraw, Gunslinger, Dead Shot - +3 (+4) Attack if the target played an attack card this turn`() {
        for ((card, base, bonus) in listOf(
            Triple("sabina-red-1", 2, 3),
            Triple("sabina-red-2a", 3, 3),
            Triple("sabina-red-3a", 3, 4),
        )) {
            for (attackCard in listOf(true, false)) {
                val d = pos {
                    clearMinions()
                    hero("sabina", Team.BLUE, Hex(1, -2)) { played(card) }
                    hero("wasp", Team.RED, Hex(-1, -2)) { played(if (attackCard) "wasp-red-1" else "wasp-blue-1") }
                    phase = PositionPhase.Resolving(next = "sabina")
                }
                d.action("sabina", ActionType.PRIMARY)
                val total = d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal
                assertThat(total).isEqualTo(if (attackCard) base + bonus else base)
            }
        }
    }

    @Test
    fun `Quickdraw - FAQ - the bonus counts a played attack card even if its hero held with it`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-red-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.HOLD) // resolved without attacking
        d.action("sabina", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(5)
    }

    // ---- Shootout / Bullet Hell (red alternates) -----------------------------------------------

    @Test
    fun `Shootout - if the target was adjacent, remove up to one enemy minion adjacent to you, no coins`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4)) // Blue keeps a minion: no Lane push
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1") // 5 against 3
        d.expect(DecisionKind.CHOOSE_UNIT, "sabina")
        assertThat(d.units()).hasSize(2)
        val m = d.s.state.minions.single { it.at == Hex(2, -2) }.id
        d.unit(m)
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.s.state.minions.any { it.at == Hex(1, -1) }).isTrue() // only one
        assertThat(d.hero("sabina").coins).isEqualTo(0)
    }

    @Test
    fun `Shootout - a target that was not adjacent removes nothing`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-red-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                hand("wasp-blue-1")
                played("wasp-gold")
            }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        assertThat(d.s.state.minions).hasSize(2)
    }

    @Test
    fun `Bullet Hell - remove up to two enemy minions adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-red-3b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4)) // Blue keeps a minion: no Lane push
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1") // 5 against 3
        d.unit(d.s.state.minions.single { it.at == Hex(2, -2) }.id)
        d.unit(d.s.state.minions.single { it.at == Hex(1, -1) }.id)
        assertThat(d.s.state.minions.filter { it.team == Team.RED }.map { it.at }).containsExactly(Hex(4, -5))
    }

    // ---- Troop Movement / Marching Orders / Path to Victory (green) ----------------------------

    @Test
    fun `Troop Movement - move a friendly minion in radius 1 space to a space in radius, may repeat once`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-green-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // an enemy minion is not offered
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "sabina")
        assertThat(d.spaces()).containsExactly(Hex(3, -3), Hex(2, -2), Hex(2, -1))
        d.space(3, -3)
        d.expect(DecisionKind.CONFIRM, "sabina")
        d.yes()
        d.space(2, -2)
        assertThat(d.s.state.minions.single { it.team == Team.BLUE }.at).isEqualTo(Hex(2, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM) // once only
    }

    @Test
    fun `Marching Orders reaches radius 3, Path to Victory may repeat up to two times`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-green-3a") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.space(3, -2)
        d.yes()
        d.space(2, -2)
        d.yes()
        d.space(1, -1)
        assertThat(d.s.state.minions.single().at).isEqualTo(Hex(1, -1))
        val e = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-green-2a") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        e.action("sabina", ActionType.PRIMARY)
        e.expect(DecisionKind.CHOOSE_SPACE, "sabina")
    }

    // ---- Close Support / Covering Fire (green alternates) --------------------------------------

    @Test
    fun `Close Support - an enemy hero in radius adjacent to your friendly minion discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-green-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1", "wasp-gold") }
            hero("xargatha", Team.RED, Hex(3, -2)) // in radius, but not next to a Blue minion
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp") // Wasp, the only such hero, chooses the card
        d.card("wasp-gold")
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Covering Fire - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-green-3b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand() }
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        assertThat(d.at("wasp")).isNull()
        assertThat(d.hero("sabina").coins).isEqualTo(1)
    }

    // ---- Listen Up / Roger Roger / Ready and Waiting (blue) ------------------------------------

    @Test
    fun `Listen Up - swap two minions in radius, of either team`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) { played("sabina-blue-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "sabina")
        val red = d.s.state.minions.single { it.at == Hex(0, -2) }.id
        d.unit(red) // the second is the only other minion in radius
        assertThat(d.at(red)).isEqualTo(Hex(2, -2))
        assertThat(d.s.state.minions.single { it.at == Hex(0, -2) }.team).isEqualTo(Team.BLUE)
    }

    @Test
    fun `Roger Roger cannot swap an immune heavy minion, Ready and Waiting ignores heavy minion immunity`() {
        for ((card, offered) in listOf("sabina-blue-2a" to false, "sabina-blue-3a" to true)) {
            val d = pos {
                clearMinions()
                hero("sabina", Team.BLUE, Hex(1, -2)) { played(card) }
                hero("wasp", Team.RED, Hex(-5, -4))
                minion(Team.RED, MinionType.HEAVY, Hex(0, -2)) // immune: a Red melee minion is in the Battle Zone
                minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
                minion(Team.RED, MinionType.MELEE, Hex(4, -5)) // 3 away
                phase = PositionPhase.Resolving(next = "sabina")
            }
            if (!offered) {
                assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
            } else {
                d.action("sabina", ActionType.PRIMARY)
                d.unit(d.s.state.minions.single { it.at == Hex(0, -2) }.id)
                assertThat(d.s.state.minions.single { it.at == Hex(2, -2) }.type).isEqualTo(MinionType.HEAVY)
            }
        }
    }

    // ---- Steady Advance / Unwavering Resolve (blue alternates) ---------------------------------

    @Test
    fun `Steady Advance - FAQ - two friendly minions in radius (an immune heavy counts), retrieve, move 1`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) {
                played("sabina-blue-2b")
                discard("sabina-red-1")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -2)) // immune (a Blue melee is in the Battle Zone)
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "sabina")
        d.card("sabina-red-1")
        assertThat(d.zoneOf("sabina", "sabina-red-1")).isEqualTo(Zone.HAND)
        d.expect(DecisionKind.CHOOSE_SPACE, "sabina")
        d.space(1, -1)
        assertThat(d.at("sabina")).isEqualTo(Hex(1, -1))
    }

    @Test
    fun `Steady Advance - fewer than two friendly minions in radius, no primary action`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) {
                played("sabina-blue-2b")
                discard("sabina-red-1")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "sabina")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Unwavering Resolve - if you retrieve, move up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) {
                played("sabina-blue-3b")
                discard("sabina-red-1")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        d.card("sabina-red-1")
        assertThat(d.spaces()).contains(Hex(3, -3)) // 2 spaces away
        d.space(3, -3)
        assertThat(d.at("sabina")).isEqualTo(Hex(3, -3))
    }

    // ---- Big Sodding Gun (Ultimate) ------------------------------------------------------------

    @Test
    fun `Big Sodding Gun - your basic attack has +2 Range and +2 Attack, other attacks do not`() {
        val d = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) {
                ultimate()
                played("sabina-gold")
            }
            hero("wasp", Team.RED, Hex(-2, -2)) { hand("wasp-blue-1") } // 3 away
            phase = PositionPhase.Resolving(next = "sabina")
        }
        d.action("sabina", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(4)
        val e = pos {
            clearMinions()
            hero("sabina", Team.BLUE, Hex(1, -2)) {
                ultimate()
                played("sabina-red-1")
            }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "sabina")
        }
        e.action("sabina", ActionType.PRIMARY)
        assertThat(e.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(2)
    }

    @Test
    fun `Big Sodding Gun - if you push an enemy hero, that hero discards a card, or is defeated`() {
        for (spare in listOf(true, false)) {
            val d = pos {
                clearMinions()
                hero("sabina", Team.BLUE, Hex(1, -2)) {
                    ultimate()
                    played("sabina-gold")
                }
                hero("wasp", Team.RED, Hex(0, -2)) {
                    if (spare) hand("wasp-blue-1", "wasp-gold") else hand("wasp-blue-1")
                }
                phase = PositionPhase.Resolving(next = "sabina")
            }
            d.action("sabina", ActionType.PRIMARY)
            d.defend("wasp-blue-1") // 5 against 4
            if (spare) {
                assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
                assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
            } else {
                assertThat(d.at("wasp")).isNull()
                assertThat(d.hero("sabina").coins).isEqualTo(1)
            }
        }
    }

    @Test
    fun `random games with Sabina keep every invariant`() {
        batch1RandomGames("sabina", games = 200, seed = 12)
    }
}
