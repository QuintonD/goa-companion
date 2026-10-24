package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Brogan the Destroyer: every card, clause by clause (text from `catalog/heroes/brogan.json`). */
class BroganTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    // ---- Onslaught (gold) / Leap Forward (Handicap) -------------------------------------------

    @Test
    fun `Onslaught - After the attack, move into the space it occupied (FAQ - mandatory when empty)`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.at("brogan")).isEqualTo(Hex(0, -2)) // no choice asked: it is mandatory
        assertThat(d.hero("brogan").coins).isEqualTo(2)
    }

    @Test
    fun `Onslaught - a target that defends keeps its space, so Brogan does not move`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        assertThat(d.at("brogan")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Leap Forward - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-handicap") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        // The adjacent minion is not a hero: no target, no Primary action.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Bulwark (silver) ----------------------------------------------------------------------

    @Test
    fun `Bulwark - You may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-silver"); discard("brogan-red-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-red-1")
        assertThat(d.zoneOf("brogan", "brogan-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Bulwark - This turn, you and friendly units in radius cannot be moved, pushed, swapped or placed by enemy heroes`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-silver") }
            hero("wasp", Team.RED, Hex(3, -5)) { played("wasp-green-2b") } // Telekinesis: place a unit not in a straight line
            hero("arien", Team.BLUE, Hex(0, -3)) // friendly, 1 from Brogan: protected
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4)) // friendly minion, 2 away: protected
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // Wasp's own minions: not protected
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.action("wasp", ActionType.PRIMARY)
        // Wasp may place only units Bulwark does not protect: her own team's minion, not Blue's.
        val ids = d.units()
        assertThat(ids).hasSize(2)
        assertThat(ids.all { d.s.state.minion(it)?.team == Team.RED }).isTrue()
    }

    // ---- Mad Dash / Bullrush / Furious Charge (red) -------------------------------------------

    @Test
    fun `Mad Dash - FAQ - charging next to an immune unit only, the attack fizzles`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(4, -2)) { played("brogan-red-1") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.HEAVY, Hex(1, -1)) // next to (2,-2), immune
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2))
        assertThat(d.texts()).contains("Brogan's Mad Dash stops: no target.")
    }

    @Test
    fun `Bullrush moves 2 or 3, Furious Charge 2 to 4 spaces in a straight line`() {
        for ((card, ends) in listOf("brogan-red-2a" to setOf(2, 3), "brogan-red-3a" to setOf(2, 3, 4))) {
            val d = pos {
                clearMinions()
                hero("brogan", Team.BLUE, Hex(4, -5)) { played(card) }
                hero("wasp", Team.RED, Hex(-5, -4))
                // Enemy minions next to every space of the line west: (3,-5)…(0,-5).
                for (q in -1..4) minion(Team.RED, MinionType.MELEE, Hex(q, -4).takeIf { q != 2 && q != 1 } ?: Hex(q, -6))
                phase = PositionPhase.Resolving(next = "brogan")
            }
            d.action("brogan", ActionType.PRIMARY)
            val west = d.spaces().filter { it.r == -5 && it.q < 4 }.map { 4 - it.q }.toSet()
            assertThat(west).isEqualTo(ends)
        }
    }

    // ---- Throwing Axe / Throwing Spear (red alternates) ---------------------------------------

    @Test
    fun `Throwing Axe - Choose one, target a unit adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION)
        d.option(0)
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.d.context.rangedAttack).isTrue() // the card's action is Ranged (p.9)
    }

    @Test
    fun `Throwing Axe - You may discard a card, if you do, target a unit in range`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-red-2b"); hand("brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY) // no adjacent target: only the second bullet
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        d.expect(DecisionKind.DEFEND, "wasp")
        val e = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-red-2b"); hand("brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        e.action("brogan", ActionType.PRIMARY)
        e.decline() // no discard: no attack
        assertThat(e.s.events.filterIsInstance<GameEvent.Attacked>()).isEmpty()
    }

    @Test
    fun `Throwing Spear - If you have a card in the discard, target a unit in range (even without discarding now)`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-red-3b"); hand("brogan-gold"); discard("brogan-silver") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.decline()
        d.expect(DecisionKind.DEFEND, "wasp")
    }

    // ---- Shield / Bolster / Fortify (green) ----------------------------------------------------

    @Test
    fun `Shield - radius 1 only, and the minion is defeated normally when Brogan declines`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(3, -5)) { played("brogan-green-1"); hand("brogan-silver") }
            hero("wasp", Team.RED, Hex(3, -3)) { played("wasp-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4)) // 1 from Brogan
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.action("wasp", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.decline()
        assertThat(d.s.state.minions.none { it.at == Hex(3, -4) }).isTrue()
    }

    @Test
    fun `Fortify - discard a basic card, gold included`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(3, -5)) { played("brogan-green-3a"); hand("brogan-gold") }
            hero("wasp", Team.RED, Hex(3, -3)) { played("wasp-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.action("wasp", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly("brogan-gold")
    }

    @Test
    fun `Shield - FAQ - does not work during the Minion Battle (minions are removed, not defeated)`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("brogan", Team.BLUE, Hex(3, -5)) { resolved(1, "brogan-green-2a"); hand("brogan-silver") }
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            effect("brogan", "brogan-green-2a", dev.quinton.goa.core.game.engine.Timing.THIS_ROUND, dev.quinton.goa.core.game.engine.ActionKind.SKILL, madeTurn = 1)
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.s.events.filterIsInstance<GameEvent.LanePushed>().single().winner).isEqualTo(Team.RED)
        assertThat(d.hero("brogan").card("brogan-silver")!!.zone).isEqualTo(Zone.HAND)
    }

    // ---- War Drummer / Master Skald (green alternates) -----------------------------------------

    @Test
    fun `War Drummer - a friendly hero in range gains 1 coin, 3 if any hero was defeated this round`() {
        for ((defeated, coins) in listOf(false to 1, true to 3)) {
            val d = pos {
                clearMinions()
                heroDefeatedThisRound = defeated
                hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-green-2b") }
                hero("arien", Team.BLUE, Hex(4, -5)) // 3 away, in Range 4
                hero("wasp", Team.RED, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "brogan")
            }
            d.action("brogan", ActionType.PRIMARY)
            assertThat(d.hero("arien").coins).isEqualTo(coins)
            assertThat(d.hero("brogan").coins).isEqualTo(0) // "friendly" never includes you
        }
    }

    @Test
    fun `Master Skald - 2 coins, 4 if any hero was defeated this round`() {
        val d = pos {
            clearMinions()
            heroDefeatedThisRound = true
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-green-3b") }
            hero("arien", Team.BLUE, Hex(4, -5))
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.hero("arien").coins).isEqualTo(4)
    }

    // ---- Brutal Jab / Mighty Punch / Savage Kick (blue) ----------------------------------------

    @Test
    fun `Brutal Jab - You may move 1 space, then push an enemy unit or a token adjacent to you up to 1 space`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-1") }
            token("token_rock", Hex(2, -1))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.space(0, -2) // move 1, now adjacent to Wasp: the only enemy unit or token adjacent
        d.expect(DecisionKind.CHOOSE_NUMBER)
        d.number(1)
        assertThat(d.at("wasp")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Mighty Punch pushes up to 2, Savage Kick moves up to 2 first`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-blue-3a") }
            hero("wasp", Team.RED, Hex(-2, -2)) { played("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(-1, -2)) // 2 spaces
        d.space(-1, -2)
        assertThat(d.d.options).hasSize(3)
        d.number(2)
        assertThat(d.at("wasp")).isEqualTo(Hex(-4, -2))
    }

    // ---- Shield Bash / Counterattack (blue alternates) -----------------------------------------

    @Test
    fun `Shield Bash - FAQ - an enemy hero adjacent who played an attack card this turn discards, even before attacking`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-blue-2b") } // 9
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-red-1"); hand("wasp-gold", "wasp-blue-1") } // 8, attack card
            hero("xargatha", Team.RED, Hex(2, -2)) { played("xargatha-green-1") } // adjacent, not an attack card
            phase = PositionPhase.Resolving()
        }
        d.action("brogan", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-gold")
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Counterattack - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-blue-3b") }
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-red-1"); hand() }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.at("wasp")).isNull()
        assertThat(d.hero("brogan").coins).isEqualTo(1)
    }

    // ---- One Man Army (Ultimate) ---------------------------------------------------------------

    @Test
    fun `One Man Army - during minion battle Brogan counts as a heavy minion`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("brogan", Team.BLUE, Hex(3, -5)) { ultimate() }
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            phase = PositionPhase.EndOfRound
        }
        // Blue 1 + Brogan = 2 against Red 2: no effect.
        assertThat(d.texts()).contains("Minion Battle: 2 against 2, no effect.")
    }

    @Test
    fun `One Man Army - if Brogan would be removed, his team loses the push instead`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("brogan", Team.BLUE, Hex(3, -5)) { ultimate() }
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(0, -5))
            minion(Team.RED, MinionType.MELEE, Hex(0, -4))
            phase = PositionPhase.EndOfRound
        }
        // Blue 2 (minion + Brogan) vs Red 4: Blue removes 2, the melee first, then Brogan (heavy last):
        // instead Blue loses the push; Brogan stays on the board.
        assertThat(d.texts()).contains("Brogan would be removed: Blue loses the push instead.")
        assertThat(d.s.events.filterIsInstance<GameEvent.LanePushed>().single().winner).isEqualTo(Team.RED)
        assertThat(d.at("brogan")).isEqualTo(Hex(3, -5))
    }
}
