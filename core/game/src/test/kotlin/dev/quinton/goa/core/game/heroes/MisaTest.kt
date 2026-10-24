package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Misa the Samurai: every card, clause by clause (text from `catalog/heroes/misa.json`; Misa has no FAQ). */
class MisaTest {
    private fun game(d: Driver) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    private fun Driver.optionLabeled(part: String) =
        option(d.options.filterIsInstance<Choice.Option>().single { part in it.label }.index)

    // ---- Watch How I Soar (gold) / Leaf on the Wind (Handicap) ---------------------------------

    @Test
    fun `Watch How I Soar - place yourself into a space at maximum range`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-gold") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY) // no minion adjacent: only the first bullet, no question
        d.expect(DecisionKind.CHOOSE_SPACE, "misa")
        val g = game(d)
        assertThat(d.spaces()).isNotEmpty()
        assertThat(d.spaces().all { g.distance(Hex(1, -2), it) == 4 }).isTrue()
        d.space(-3, -2)
        assertThat(d.at("misa")).isEqualTo(Hex(-3, -2))
    }

    @Test
    fun `Watch How I Soar - defeat a minion adjacent to you, never a friendly one`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-gold") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // friendly: never defeated
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "misa")
        assertThat(d.d.options).hasSize(2) // choose one: no "both" without the Ultimate
        d.option(1)
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(2, -2) }).isTrue()
        assertThat(d.hero("misa").coins).isEqualTo(2)
    }

    @Test
    fun `Leaf on the Wind - place yourself into a space at maximum range`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-handicap") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        val g = game(d)
        assertThat(d.spaces().all { g.distance(Hex(1, -2), it) == 4 }).isTrue()
        d.space(-3, -2)
        assertThat(d.at("misa")).isEqualTo(Hex(-3, -2))
    }

    // ---- Swoop In (silver) ---------------------------------------------------------------------

    @Test
    fun `Swoop In - place yourself in radius next to two or more enemy units, then you may retrieve`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) {
                played("misa-silver")
                discard("misa-red-1")
            }
            hero("wasp", Team.RED, Hex(-1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -1))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY) // (0,-2) is the only space next to both
        assertThat(d.at("misa")).isEqualTo(Hex(0, -2))
        d.expect(DecisionKind.CHOOSE_CARD, "misa")
        d.card("misa-red-1")
        assertThat(d.zoneOf("misa", "misa-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Swoop In - no space next to two enemy units, no primary action`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-silver") }
            hero("wasp", Team.RED, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "misa")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Challenge Accepted / Matter of Honor / Worthy Opponent (red) --------------------------

    @Test
    fun `Challenge Accepted, Matter of Honor, Worthy Opponent - after the attack, this turn +2 (+3, +5) Defense`() {
        for ((card, bonus) in listOf("misa-red-1" to 2, "misa-red-2a" to 3, "misa-red-3a" to 5)) {
            val d = pos {
                clearMinions()
                hero("misa", Team.BLUE, Hex(1, -2)) { played(card) }
                hero("wasp", Team.RED, Hex(0, -2)) {
                    played("wasp-gold") // the turn goes on after Misa's action
                    hand("wasp-blue-1", "wasp-red-3a")
                }
                phase = PositionPhase.Resolving(next = "misa")
            }
            d.action("misa", ActionType.PRIMARY)
            d.defend("wasp-red-3a") // Defense 7 against 5 or 6
            d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
            assertThat(game(d).cardValue("misa", "misa-silver", Stat.DEFENSE)).isEqualTo(3 + bonus)
            assertThat(d.s.state.effects.single { it.cardId == card }.timing).isEqualTo(Timing.THIS_TURN)
        }
    }

    // ---- Power Shot / Thunder Shot (red alternates) --------------------------------------------

    @Test
    fun `Power Shot - if the target was at maximum range, you may move it 1 space farther away`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-red-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1") } // 2 away: maximum range
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        d.defend("wasp-blue-1") // 5 against 3
        d.expect(DecisionKind.CHOOSE_SPACE, "misa")
        assertThat(d.spaces()).containsExactly(Hex(-2, -2), Hex(-1, -3))
        d.space(-2, -2)
        assertThat(d.at("wasp")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Power Shot - a target not at maximum range is not moved`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) {
                hand("wasp-blue-1")
                played("wasp-gold")
            }
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
    }

    @Test
    fun `Thunder Shot - if the target is not adjacent to you, you may move it 1 space farther away`() {
        for (adjacent in listOf(false, true)) {
            val d = pos {
                clearMinions()
                hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-red-3b") }
                hero("wasp", Team.RED, if (adjacent) Hex(0, -2) else Hex(-1, -2)) {
                    hand("wasp-red-3a")
                    played("wasp-gold")
                }
                phase = PositionPhase.Resolving(next = "misa")
            }
            d.action("misa", ActionType.PRIMARY)
            d.defend("wasp-red-3a") // 7 against 3
            if (adjacent) d.expect(DecisionKind.CHOOSE_ACTION, "wasp") else d.expect(DecisionKind.CHOOSE_SPACE, "misa")
        }
    }

    // ---- Focus / Discipline / Mastery (green) --------------------------------------------------

    @Test
    fun `Focus - next turn, before you perform a primary action, you may move 1 space`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("misa", Team.BLUE, Hex(1, -2)) {
                resolved(1, "misa-green-1")
                played("misa-red-1")
            }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-red-3a") } // 2 away: no target yet
            effect("misa", "misa-green-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY) // offered: the move comes first
        d.expect(DecisionKind.CHOOSE_SPACE, "misa")
        assertThat(d.spaces().all { game(d).distance(Hex(1, -2), it) == 1 }).isTrue()
        d.space(0, -2)
        d.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Focus - not on the turn it was played`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-red-1") }
            hero("wasp", Team.RED, Hex(-1, -2))
            effect("misa", "misa-green-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "misa")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Discipline and Mastery - next turn before a primary action move up to 2 (3) spaces`() {
        for ((card, n) in listOf("misa-green-2a" to 2, "misa-green-3a" to 3)) {
            val d = pos {
                clearMinions()
                turn = 2
                hero("misa", Team.BLUE, Hex(1, -2)) {
                    resolved(1, card)
                    played("misa-red-1")
                }
                hero("wasp", Team.RED, Hex(-5, -4))
                effect("misa", card, Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
                phase = PositionPhase.Resolving(next = "misa")
            }
            d.action("misa", ActionType.PRIMARY)
            d.expect(DecisionKind.CHOOSE_SPACE, "misa")
            val g = game(d)
            assertThat(d.spaces().maxOf { g.distance(Hex(1, -2), it) }).isEqualTo(n)
            assertThat(d.d.options.any { it is Choice.Decline }).isTrue() // "up to": 0 is allowed
        }
    }

    // ---- Living Tornado / Storm Spirit (green alternates) --------------------------------------

    @Test
    fun `Living Tornado - swap two units at maximum radius, of any team`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-green-2b") }
            hero("wasp", Team.RED, Hex(-2, -2)) // 3 away
            hero("brogan", Team.BLUE, Hex(4, -2)) // 3 away, friendly
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // 1 away: not at maximum radius
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("wasp", "brogan")
        d.unit("wasp")
        assertThat(d.at("wasp")).isEqualTo(Hex(4, -2))
        assertThat(d.at("brogan")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Storm Spirit - swap two units in radius at equal distance from you`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-green-3b") }
            hero("wasp", Team.RED, Hex(0, -2)) // 1 away
            hero("arien", Team.BLUE, Hex(-1, -2)) // 2 away, alone at that distance
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // 1 away
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        assertThat(d.units()).hasSize(2)
        assertThat(d.units()).doesNotContain("arien")
        d.unit("wasp")
        assertThat(d.at("wasp")).isEqualTo(Hex(2, -2))
    }

    // ---- Sudden Breeze / Gust of Wind / Crushing Squall (blue) ---------------------------------

    @Test
    fun `Sudden Breeze - up to 3 straight through obstacles, may place an enemy unit passed next to you`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) { played("misa-blue-1") }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("xargatha", Team.RED, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        assertThat(d.spaces()).doesNotContain(Hex(-3, -2)) // 4 away
        d.space(-2, -2) // through Wasp and Xargatha
        d.expect(DecisionKind.CHOOSE_UNIT, "misa")
        assertThat(d.units()).containsExactly("wasp", "xargatha")
        d.unit("wasp")
        d.space(-2, -3)
        assertThat(d.at("wasp")).isEqualTo(Hex(-2, -3))
        assertThat(d.at("misa")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Gust of Wind moves up to 4, Crushing Squall up to 5`() {
        for ((card, n) in listOf("misa-blue-2a" to 4, "misa-blue-3a" to 5)) {
            val d = pos {
                clearMinions()
                hero("misa", Team.BLUE, Hex(1, -2)) { played(card) }
                hero("wasp", Team.RED, Hex(0, -2))
                phase = PositionPhase.Resolving(next = "misa")
            }
            d.action("misa", ActionType.PRIMARY)
            assertThat(d.spaces().contains(Hex(-3, -2))).isTrue() // 4 west
            assertThat(d.spaces().contains(Hex(-4, -2))).isEqualTo(n == 5)
        }
    }

    // ---- Dash and Slash / Death from Above (blue alternates) -----------------------------------

    @Test
    fun `Dash and Slash, Death from Above - an enemy hero you moved through discards a card, if able`() {
        for (card in listOf("misa-blue-2b", "misa-blue-3b")) {
            val d = pos {
                clearMinions()
                hero("misa", Team.BLUE, Hex(1, -2)) { played(card) }
                hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-gold") }
                minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // a minion moved through: not a hero
                phase = PositionPhase.Resolving(next = "misa")
            }
            d.action("misa", ActionType.PRIMARY)
            d.space(-2, -2)
            d.expect(DecisionKind.CHOOSE_CARD, "wasp")
            d.card("wasp-gold")
            assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
        }
    }

    // ---- Power Overwhelming (Ultimate) ---------------------------------------------------------

    @Test
    fun `Power Overwhelming - choose two options in any order, placing yourself makes an adjacent enemy discard`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(1, -2)) {
                ultimate()
                played("misa-gold")
            }
            hero("wasp", Team.RED, Hex(-4, -2)) { hand("wasp-blue-1", "wasp-gold") } // 5 away
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        assertThat(d.d.options).hasSize(4)
        d.optionLabeled("defeat a minion adjacent to you, then place")
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        d.space(-3, -2) // 4 away, next to Wasp
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-gold")
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Power Overwhelming - APP - Fast Travel is placing yourself`() {
        val d = pos {
            clearMinions()
            hero("misa", Team.BLUE, Hex(6, 0)) {
                ultimate()
                played("misa-gold")
            }
            hero("wasp", Team.RED, Hex(4, -1)) { hand("wasp-blue-1", "wasp-gold") }
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.FAST_TRAVEL)
        d.space(5, -1) // blue-side, next to Wasp in lane-1-2
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
    }

    @Test
    fun `random games with Misa keep every invariant`() {
        batch1RandomGames("misa", games = 200, seed = 14)
    }

    // ---- audit E regressions --------------------------------------------------------------------

    @Test
    fun `Sudden Breeze - no space next to Misa is empty for the unit, so no placement (Takahide's blade)`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("misa", Team.RED, Hex(-1, -2)) { played("misa-blue-1") }
            hero("takahide", Team.RED, Hex(2, -3)) { resolved(1, "takahide-red-2b") }
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            turn = 2
            // Takahide's Spinning Blade: (2,-2), the only other free space next to (1,-2), is next to him, so it
            // counts as an obstacle for the enemy minion.
            effect("takahide", "takahide-red-2b", Timing.THIS_TURN, ActionKind.ATTACK)
            phase = PositionPhase.Resolving(next = "misa")
        }
        d.action("misa", ActionType.PRIMARY)
        d.space(1, -2) // through the minion
        assertThat(d.at("misa")).isEqualTo(Hex(1, -2))
        assertThat(d.at(m)).isEqualTo(Hex(0, -2))
        assertThat(d.d.kind).isNotEqualTo(DecisionKind.CHOOSE_UNIT) // no placement offered, no crash
    }
}
