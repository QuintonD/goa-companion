package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Dodger the Warlock: every card, clause by clause (text from `catalog/heroes/dodger.json`), with the FAQ
 * and the rulebook's p.14 Vampiric Shield example. Forgotten Island: (1,-2) is next to the Red melee spawn
 * point (2,-3) only; (0,-3) has exactly two other spawn points within 2 spaces, (1,-4) and (2,-3).
 */
class DodgerTest {
    private fun game(d: Driver) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    private fun Driver.optionLabeled(part: String) =
        option(d.options.filterIsInstance<Choice.Option>().single { part in it.label }.index)

    // ---- Dread Razor (gold) / Rusty Razor (Handicap) -------------------------------------------

    @Test
    fun `Dread Razor - choose one, adjacent unit, or next to an empty spawn point a unit in range`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            hero("arien", Team.BLUE, Hex(3, -2)) // 2 away
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "dodger")
        d.option(1)
        assertThat(d.units()).containsExactly("brogan", "arien")
        d.unit("arien")
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.rangedAttack).isTrue()
    }

    @Test
    fun `Dread Razor - FAQ - a spawn point with something on it is not empty, so only the adjacent bullet`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("arien", Team.BLUE, Hex(3, -2))
            token("token_rock", Hex(2, -3))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        val e = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("arien", Team.BLUE, Hex(3, -2))
            hero("brogan", Team.BLUE, Hex(0, -2))
            token("token_rock", Hex(2, -3))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        e.action("dodger", ActionType.PRIMARY) // no choice: only the adjacent bullet, one target
        e.expect(DecisionKind.DEFEND, "brogan")
    }

    @Test
    fun `Rusty Razor - heroes only, adjacent or in range next to an empty spawn point`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-handicap") }
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent, not a hero
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    // ---- Death Trap (silver) -------------------------------------------------------------------

    @Test
    fun `Death Trap - an enemy hero in radius next to an empty spawn point in the battle zone discards a card`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-silver") }
            hero("arien", Team.BLUE, Hex(-1, -2)) // next to (0,-3), an empty spawn point
            hero("brogan", Team.BLUE, Hex(4, -2)) // next to (3,-1), covered by a token
            token("token_rock", Hex(3, -1))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    // ---- Littlefinger / Finger / Middlefinger of Death (red) -----------------------------------

    @Test
    fun `Littlefinger of Death - target a hero in range who has one or more cards in the discard`() {
        for (hasDiscard in listOf(true, false)) {
            val d = pos {
                clearMinions()
                hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-1") }
                hero("arien", Team.BLUE, Hex(-1, -2)) { if (hasDiscard) discard("arien-red-1") }
                hero("brogan", Team.BLUE, Hex(3, -2)) // in range, nothing in the discard
                phase = PositionPhase.Resolving(next = "dodger")
            }
            if (hasDiscard) {
                d.action("dodger", ActionType.PRIMARY)
                d.expect(DecisionKind.DEFEND, "arien")
            } else {
                assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
            }
        }
    }

    @Test
    fun `Finger of Death - Range 3, or a unit adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-2a") }
            hero("arien", Team.BLUE, Hex(-2, -2)) { discard("arien-red-1") } // 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "dodger")
        d.option(1)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Middlefinger of Death - choose both, on different targets, in either order`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-3a") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1", "brogan-gold") }
            hero("arien", Team.BLUE, Hex(-1, -2)) {
                discard("arien-red-1")
                hand("arien-blue-1")
            }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "dodger")
        assertThat(d.d.options).hasSize(4)
        d.optionLabeled("first the unit adjacent")
        d.defend("brogan-blue-1") // 5 against 5
        // Brogan now has a card in the discard, but the second target must be different.
        d.expect(DecisionKind.DEFEND, "arien")
        assertThat(
            d.s.events.filterIsInstance<GameEvent.Attacked>().map {
                it.targetId
            },
        ).containsExactly("brogan", "arien")
    }

    @Test
    fun `Middlefinger of Death - one hero fits both bullets, so both is not offered`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-3a") }
            hero("arien", Team.BLUE, Hex(2, -2)) { discard("arien-red-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "dodger")
        assertThat(d.d.options).hasSize(2)
    }

    // ---- Burning Skull / Blazing Skull (red alternates) ----------------------------------------

    @Test
    fun `Burning Skull - after the attack move up to 1 minion adjacent to you 1 space, not adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-2b") }
            hero("arien", Team.BLUE, Hex(-1, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1") // 5 against 3
        d.expect(DecisionKind.CHOOSE_UNIT, "dodger")
        d.unit(d.s.state.minions.single().id)
        assertThat(d.spaces()).containsExactly(Hex(0, -3), Hex(-1, -1))
        d.space(-1, -1)
        assertThat(d.s.state.minions.single().at).isEqualTo(Hex(-1, -1))
    }

    @Test
    fun `Blazing Skull - up to 2 minions, friendly or enemy, 1 space each`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-3b") }
            hero("arien", Team.BLUE, Hex(-1, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1") // 5 against 4
        d.unit(d.s.state.minions.single { it.team == Team.BLUE }.id)
        d.space(-1, -1)
        d.unit(d.s.state.minions.single { it.team == Team.RED }.id)
        d.space(3, -2)
        assertThat(d.s.state.minions.map { it.at }).containsExactly(Hex(-1, -1), Hex(3, -2))
    }

    // ---- Dark / Darker / Darkest Ritual (green) ------------------------------------------------

    @Test
    fun `Dark Ritual and Darker Ritual - 2 or more empty spawn points in radius, gain 1 (2) coins`() {
        for ((card, coins) in listOf("dodger-green-1" to 1, "dodger-green-2a" to 2)) {
            val d = pos {
                clearMinions()
                hero("dodger", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "dodger")
            }
            d.action("dodger", ActionType.PRIMARY)
            assertThat(d.hero("dodger").coins).isEqualTo(coins)
        }
    }

    @Test
    fun `Dark Ritual - FAQ - only spawn points of the current battle zone count`() {
        val d = pos {
            clearMinions()
            battleZone("lane-1", "lane-1-1") // the spawn points around (1,-2) are in lane-1-2
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-green-1") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Darkest Ritual - gain 2 coins, and if you have your Ultimate an Attack item`() {
        for (ultimate in listOf(true, false)) {
            val d = pos {
                clearMinions()
                hero("dodger", Team.RED, Hex(1, -2)) {
                    if (ultimate) ultimate()
                    played("dodger-green-3a")
                }
                hero("arien", Team.BLUE, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "dodger")
            }
            d.action("dodger", ActionType.PRIMARY)
            assertThat(d.hero("dodger").coins).isEqualTo(2)
            assertThat(d.hero("dodger").spareItems).isEqualTo(if (ultimate) listOf(Stat.ATTACK) else emptyList<Stat>())
            assertThat(game(d).cardValue("dodger", "dodger-gold", Stat.ATTACK)).isEqualTo(if (ultimate) 3 else 2)
        }
    }

    // ---- Necromancy / Necromastery (green alternates) ------------------------------------------

    @Test
    fun `Necromancy - FAQ - a removed minion of any type, onto an adjacent friendly spawn point`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-green-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "dodger")
        assertThat(d.d.options).hasSize(3) // melee, ranged and heavy are missing from the wave
        d.optionLabeled("ranged")
        // The only empty friendly spawn point adjacent is (2,-3), a melee one: no question.
        val m = d.s.state.minions.single { it.at == Hex(2, -3) }
        assertThat(m.type).isEqualTo(MinionType.RANGED)
        assertThat(m.team).isEqualTo(Team.RED)
    }

    @Test
    fun `Necromancy - FAQ - a full wave has no minion to respawn`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-green-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            // The whole Red wave of lane-1-2 (4 melee, 1 ranged, 1 heavy), none on its spawn point.
            for (h in listOf(Hex(3, -2), Hex(4, -2), Hex(3, -3), Hex(4, -3))) minion(Team.RED, MinionType.MELEE, h)
            minion(Team.RED, MinionType.RANGED, Hex(1, -1))
            minion(Team.RED, MinionType.HEAVY, Hex(0, -1))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Necromastery - friendly spawn points in radius (an item widens it), never enemy ones`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                played("dodger-green-3b")
                items("dodger-red-3b") // +1 Radius: 2
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.optionLabeled("melee")
        assertThat(d.spaces()).containsExactly(Hex(0, -3), Hex(2, -3), Hex(1, 0))
        d.space(1, 0)
        assertThat(d.s.state.minions.single().type).isEqualTo(MinionType.MELEE)
    }

    // ---- Shield of Decay / Vampiric Shield / Aegis of Doom (blue) ------------------------------

    @Test
    fun `p14 example - Vampiric Shield, 4 plus 2 for the two empty spawn points in radius, defends Mad Dash`() {
        // "This time Brogan chooses to attack Dodger with his Mad Dash. Dodger uses her Vampiric Shield card
        // to defend, with a defense value of only 4, which is not enough to defend Brogan's Attack of 6.
        // However, since Defense is the Primary action of Vampiric Shield, the card text is applied, giving
        // Dodger +2 Defense for the 2 empty minion spawn points within a radius of 2 spaces from her space."
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-red-1") }
            hero("dodger", Team.RED, Hex(0, -3)) { hand("dodger-blue-2a", "dodger-gold") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY) // 2 spaces west to (0,-2), then the only adjacent enemy
        assertThat(d.at("brogan")).isEqualTo(Hex(0, -2))
        val p = d.expect(DecisionKind.DEFEND, "dodger")
        assertThat(p.context.attackTotal).isEqualTo(6)
        assertThat(p.context.defensePreview["dodger-blue-2a"]).isEqualTo(6)
        assertThat(p.context.defensePreview["dodger-gold"]).isEqualTo(1) // secondary Defense: no text
        d.card("dodger-blue-2a")
        assertThat(d.texts()).contains("Dodger defends with Vampiric Shield: Defense 4 + 2 (card text) = 6. Defended.")
        assertThat(d.at("dodger")).isEqualTo(Hex(0, -3))
    }

    @Test
    fun `Vampiric Shield - FAQ - an occupied spawn point is not empty, one is not enough`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-red-1") }
            hero("dodger", Team.RED, Hex(0, -3)) { hand("dodger-blue-2a") }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4)) // on a spawn point, 1 from Dodger: -1 too
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "dodger").context.defensePreview["dodger-blue-2a"]).isEqualTo(3)
    }

    @Test
    fun `Shield of Decay +2, Aegis of Doom +4 with 2 or more empty spawn points in radius`() {
        for ((card, total) in listOf("dodger-blue-1" to 5, "dodger-blue-3a" to 8)) {
            val d = pos {
                clearMinions()
                hero("brogan", Team.BLUE, Hex(0, -2)) { played("brogan-gold") }
                hero("dodger", Team.RED, Hex(0, -3)) { hand(card) }
                phase = PositionPhase.Resolving(next = "brogan")
            }
            d.action("brogan", ActionType.PRIMARY)
            assertThat(d.expect(DecisionKind.DEFEND, "dodger").context.defensePreview[card]).isEqualTo(total)
        }
    }

    // ---- Weakness / Enfeeblement (blue alternates) ---------------------------------------------

    @Test
    fun `Weakness - this turn enemy heroes in radius have -4 Attack, and can still attack`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                played("dodger-blue-2b")
                hand("dodger-gold")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-gold") } // Attack 4
            hero("brogan", Team.BLUE, Hex(-4, -2)) // 5 away: outside Radius 4
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        assertThat(game(d).cardValue("brogan", "brogan-gold", Stat.ATTACK)).isEqualTo(4)
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "dodger").context.attackTotal).isEqualTo(0)
    }

    @Test
    fun `Enfeeblement - enemy heroes in radius have -6 Attack and cannot repeat actions`() {
        val d = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-blue-3b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-3a") } // Violent Torrent: "May repeat once"
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        assertThat(game(d).cardValue("arien", "arien-red-3a", Stat.ATTACK)).isEqualTo(1)
        d.action("arien", ActionType.PRIMARY)
        d.unit(d.s.state.minions.single { it.at == Hex(4, -2) }.id) // Attack 1 still defeats a minion
        assertThat(d.s.state.minions.count { it.team == Team.RED }).isEqualTo(1)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM) // no "Repeat?"
    }

    // ---- Tide of Darkness (Ultimate) -----------------------------------------------------------

    @Test
    fun `Tide of Darkness - every space counts as in the battle zone with a friendly minion spawn point`() {
        // Dark Ritual with the Battle Zone elsewhere: the empty spaces in radius count.
        val d = pos {
            clearMinions()
            battleZone("lane-1", "lane-1-1")
            hero("dodger", Team.RED, Hex(1, -2)) {
                ultimate()
                played("dodger-green-1")
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        assertThat(d.hero("dodger").coins).isEqualTo(1)
        // Dread Razor's second bullet: next to any empty space.
        val e = pos {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                ultimate()
                played("dodger-gold")
            }
            hero("arien", Team.BLUE, Hex(3, -2))
            token("token_rock", Hex(2, -3))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        e.action("dodger", ActionType.PRIMARY)
        e.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Tide of Darkness - Necromancy into any empty adjacent space, the minion then returns`() {
        val d = pos {
            clearMinions()
            battleZone("lane-1", "lane-1-1")
            hero("dodger", Team.RED, Hex(1, -2)) {
                ultimate()
                played("dodger-green-2b")
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.optionLabeled("melee")
        assertThat(d.spaces()).containsExactly(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))
        d.space(2, -2)
        // p.18: after the action it moves back to its Battle Zone (lane-1-1).
        while (d.pending?.kind == DecisionKind.MINION_RETURN) d.answer(d.d.options.first())
        val m = d.s.state.minions.single()
        assertThat(game(d).zoneOf(m.at!!)).isEqualTo("lane-1-1")
    }

    @Test
    fun `Tide of Darkness - Shield of Decay counts empty spaces while Dodger defends`() {
        val d = pos {
            clearMinions()
            battleZone("lane-1", "lane-1-1")
            hero("brogan", Team.BLUE, Hex(0, -2)) { played("brogan-gold") }
            hero("dodger", Team.RED, Hex(0, -3)) {
                ultimate()
                hand("dodger-blue-1")
            }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "dodger").context.defensePreview["dodger-blue-1"]).isEqualTo(5)
    }

    @Test
    fun `random games with Dodger keep every invariant`() {
        batch1RandomGames("dodger", games = 200, seed = 13)
    }
}
