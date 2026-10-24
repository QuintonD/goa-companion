package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Attack and Defense actions, rulebook pp.13–14 (and the p.12 Bolster example). */
class CombatTest {
    @Test
    fun `p13 - non-ranged attacks only target adjacent units, never friendly ones`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-green-1") } // friendly, adjacent
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-green-1") } // enemy, adjacent
            hero("xargatha", Team.RED, Hex(-1, -2)) { played("xargatha-green-1") } // enemy, 2 away
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT)
        assertThat(d.units()).containsExactly("wasp", d.s.state.minions.single().id)
    }

    @Test
    fun `p13 - ranged attacks target by the Range value, through obstacles (no line of sight, p9)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) {
                played("arien-red-2b")
                items()
            } // Rogue Wave, Range 2
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-green-1") } // 2 away, behind Brogan
            hero("brogan", Team.BLUE, Hex(0, -2)) { played("brogan-gold") }
            hero("xargatha", Team.RED, Hex(4, -2)) { played("xargatha-green-1") } // 3 away
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.d.kind).isEqualTo(DecisionKind.DEFEND) // the only target in range is chosen
        assertThat(d.d.seat).isEqualTo("wasp")
    }

    @Test
    fun `p13 attacking a minion - it is simply defeated, 2 coins for melee and ranged, 4 for heavy`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-5, -4)) { played("wasp-green-1") }
            minion(Team.RED, MinionType.HEAVY, Hex(0, -2))
            // Outside the Battle Zone: not counted for immunity.
            minion(Team.RED, MinionType.MELEE, Hex(-3, -2), lane = "lane-1")
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        // The heavy minion is the only Red minion in the Battle Zone: not immune.
        assertThat(d.hero("brogan").coins).isEqualTo(4)
        assertThat(d.texts()).contains("Red heavy minion is defeated by Brogan.")
    }

    @Test
    fun `p13 heavy minion immunity - immune while a friendly non-heavy minion is in the Battle Zone`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-5, -4)) { played("wasp-green-1") }
            minion(Team.RED, MinionType.HEAVY, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        // No other target: the attack can't start, so the Primary action isn't offered.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.s.view(null).minions.first { it.type == MinionType.HEAVY }.immune).isTrue()
    }

    @Test
    fun `p14 example - Brogan's Mad Dash on Wasp, Lift Up plus an adjacent friendly melee minion defends`() {
        // "Brogan … moves 2 spaces in a straight line and announces that he is attacking Wasp with an attack
        // total of 6. Wasp discards her Lift Up card, with a defense value of 5. The adjacent friendly melee
        // minion increases Wasp's defense total by +1, allowing her to successfully defend. Because Defense is
        // a Secondary action on Lift Up, its card text is ignored."
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(4, -2)) { played("brogan-red-1") }
            hero("wasp", Team.RED, Hex(1, -1)) { hand("wasp-blue-1", "wasp-green-1") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        val p = d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(p.context.attackTotal).isEqualTo(6)
        assertThat(p.context.minionModifier).isEqualTo(1)
        assertThat(p.context.defensePreview["wasp-blue-1"]).isEqualTo(6)
        d.card("wasp-blue-1")
        assertThat(d.texts()).contains("Brogan attacks Wasp: Attack 6.")
        assertThat(
            d.texts(),
        ).contains("Wasp defends with Lift Up: Defense 5 + 1 (adjacent friendly melee minion) = 6. Defended.")
        assertThat(d.at("wasp")).isEqualTo(Hex(1, -1))
        assertThat(d.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `p13 example - Mad Dash must move first, so the adjacent minion and the immune heavy cannot be targets`() {
        // "Brogan cannot target the melee minion next to him, because Mad Dash requires him to move 2 spaces in a
        // straight line before the attack. He cannot target the heavy minion … because the heavy minion is Immune,
        // but he can target either Dodger … or Wasp". p.14 goes on: "This time Brogan chooses to attack Dodger
        // with his Mad Dash. Dodger uses her Vampiric Shield card to defend … the card text is applied,
        // giving Dodger +2 Defense for the 2 empty minion spawn points within a radius of 2 spaces".
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-red-1") }
            hero("wasp", Team.RED, Hex(-1, -1)) { played("wasp-green-1") }
            hero("dodger", Team.RED, Hex(0, -3)) {
                played("dodger-green-1")
                hand("dodger-blue-2a", exactly = false)
            }
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // adjacent to Brogan at the start
            minion(Team.RED, MinionType.HEAVY, Hex(-1, -2)) // immune: a Red melee minion is in the Battle Zone
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        // The only end space 2 spaces away in a straight line next to an enemy is (0,-2), west.
        assertThat(d.at("brogan")).isEqualTo(Hex(0, -2))
        d.expect(DecisionKind.CHOOSE_UNIT)
        assertThat(d.units()).containsExactly("wasp", "dodger")
        d.unit("dodger")
        val p = d.expect(DecisionKind.DEFEND, "dodger")
        assertThat(p.context.attackTotal).isEqualTo(6)
        // The spawn points (1,-4) and (2,-3) are empty and 1 and 2 spaces from Dodger: +2 Defense. Here the
        // immune heavy minion (north-west of Brogan, so next to Dodger north-east of him) adds +1 too; the
        // p.14 picture is a different position (DodgerTest "p14 example" has the rulebook's total of 6).
        assertThat(p.context.defensePreview["dodger-blue-2a"]).isEqualTo(7)
        d.card("dodger-blue-2a")
        assertThat(d.texts()).contains(
            "Dodger defends with Vampiric Shield: Defense 4 + 2 (card text) + 1 (adjacent friendly heavy minion) = 7. Defended.",
        )
    }

    @Test
    fun `p14 - a primary Defense card's text applies, a secondary one's does not`() {
        // Arien's Aspiring Duelist (primary Defense): "Ignore all minion defense modifiers."
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-gold") } // Attack 4
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1", "arien-gold") }
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // -1 to Arien
            minion(Team.RED, MinionType.RANGED, Hex(4, -2)) // -1 to Arien (radius 2)
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        val p = d.expect(DecisionKind.DEFEND, "arien")
        assertThat(p.context.minionModifier).isEqualTo(-2)
        assertThat(p.context.defensePreview["arien-blue-1"]).isEqualTo(5) // modifiers ignored
        assertThat(p.context.defensePreview["arien-gold"]).isEqualTo(0) // 2 - 2
        d.card("arien-blue-1")
        assertThat(d.texts()).contains("Arien defends with Aspiring Duelist: Defense 5 = 5. Defended.")
    }

    @Test
    fun `p14 block - a Block card defends regardless of the value when its condition holds, else you are defeated`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-2b") } // ranged
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-1") } // Stop Projectiles: "Block a ranged attack."
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("wasp-green-1")
        assertThat(d.texts()).contains("Wasp defends with Stop Projectiles: Blocked.")
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `p14 block - the condition fails against a non-ranged attack and the defender is defeated`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("wasp-green-1")
        assertThat(d.texts()).contains("Wasp defends with Stop Projectiles: The Block fails.")
        assertThat(d.at("wasp")).isNull()
    }

    @Test
    fun `p13 when a hero is defeated - removed, card resolved, effects and markers gone, coins, life`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("arien", Team.BLUE, Hex(0, -2)) {
                played("arien-red-1")
                level = 3
            }
            hero("brogan", Team.BLUE, null) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 4
                played("wasp-gold")
                resolved(1, "wasp-silver")
                marker("marker_test", from = "arien")
                items("wasp-blue-2a", "wasp-red-2b", "wasp-green-2b")
                hand("wasp-blue-2b", "wasp-red-2a", "wasp-green-2a")
            }
            effect("wasp", "wasp-silver", Timing.THIS_TURN, ActionKind.SKILL, madeTurn = 2)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        val wasp = d.hero("wasp")
        assertThat(wasp.at).isNull()
        assertThat(wasp.card("wasp-gold")!!.zone).isEqualTo(Zone.RESOLVED) // "resolved, without performing an action"
        assertThat(wasp.card("wasp-gold")!!.slot).isEqualTo(2)
        assertThat(d.s.state.effects.none { it.owner == "wasp" }).isTrue()
        assertThat(wasp.markers).isEmpty()
        assertThat(d.hero("arien").coins).isEqualTo(4) // Wasp's level
        assertThat(d.hero("brogan").coins).isEqualTo(2) // assist = lowest Tier II (off the board too)
        assertThat(d.s.state.lifeCounters[Team.RED]).isEqualTo(2) // 4 - Tier II
        // Wasp did not act this turn.
        assertThat(d.d.seat).isEqualTo("brogan")
    }

    @Test
    fun `p13 - the team that spends its last Life counter loses at once, even mid-turn`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-red-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-gold") }
            lifeCounters(Team.RED, 1)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.pending).isNull()
        val v = d.s.view(null)
        assertThat(v.winner).isEqualTo(Team.BLUE)
        assertThat(v.winReason).isEqualTo(WinReason.LIFE_COUNTERS)
        assertThat(d.s.events.last()).isInstanceOf(GameEvent.GameOver::class.java)
    }

    @Test
    fun `p12 example - Bolster keeps a defeated friendly minion on the board, the attacker still gains the coins`() {
        // "Although the effect of Bolster can prevent the removal of the minion, Dodger would still gain 2 coins".
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(3, -5)) {
                played("brogan-green-2a")
                hand("brogan-silver")
            }
            hero("wasp", Team.RED, Hex(3, -3)) { played("wasp-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.action("wasp", ActionType.PRIMARY)
        // Wasp attacks the minion at (3,-4) (2 from Brogan): Brogan may discard his silver card.
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-silver")
        assertThat(d.s.state.minions.any { it.at == Hex(3, -4) }).isTrue()
        assertThat(d.hero("wasp").coins).isEqualTo(2)
        assertThat(d.zoneOf("brogan", "brogan-silver")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `p14 minion defense modifiers - adjacent friendly and enemy melee or heavy, enemy ranged within 2`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold") }
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -1)) // +1 (immune heavy counts: presence)
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // +1
            minion(Team.RED, MinionType.MELEE, Hex(3, -3)) // -1
            minion(Team.RED, MinionType.RANGED, Hex(4, -3)) // -1 (2 away)
            minion(Team.BLUE, MinionType.RANGED, Hex(2, -3)) // friendly ranged: no modifier
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.unit("arien")
        assertThat(d.expect(DecisionKind.DEFEND).context.minionModifier).isEqualTo(0)
    }
}
