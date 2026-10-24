package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.printedValue
import dev.quinton.goa.core.game.heroes.brynn.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Hanu the Trickster: every card, clause by clause (text from `catalog/heroes/hanu.json`), with the FAQ. */
class HanuTest {
    private fun minionAt(d: Driver, h: Hex): String = d.s.state.minions.first { it.at == h }.id

    private fun probe(d: Driver) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    // ---- Fight and Flight (gold) / Fight or Flight (Handicap) ------------------------------------

    @Test
    fun `Fight and Flight - if the target is not defeated, after the attack move 3 spaces in a straight line`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // Defense 5 against Attack 2
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue() // "If able": mandatory when possible
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(3)
            assertThat(HexGrid.inStraightLine(h, Hex(1, -2))).isTrue()
        }
        d.space(-2, -2)
        assertThat(d.at("hanu")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Fight and Flight - a defeated target means no move`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-gold") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.hero("hanu").coins).isEqualTo(2)
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Fight or Flight - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-handicap") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Hurry up! (silver) ------------------------------------------------------------------------

    @Test
    fun `Hurry up - set the printed Initiative of an unresolved card of a hero in range to 11`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-silver") } // 12, Range 4
            hero("xargatha", Team.RED, Hex(-1, -2)) { played("xargatha-blue-1") } // 9
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") } // 8
            hero("brogan", Team.BLUE, Hex(1, -4)) // in range, but no unresolved card
            hero("wasp", Team.RED, Hex(-5, -4)) { played("wasp-red-1") } // 8, out of range
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("xargatha", "arien")
        d.unit("arien")
        // Arien (now 11) acts before Xargatha (9).
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
        assertThat(d.s.view(null).initiativeOrder.first()).isEqualTo("arien")
        d.answer(Choice.Action(ActionType.HOLD))
        // "until it is resolved": the resolved card has its own printed value again.
        assertThat(probe(d).printedValue("arien", "arien-red-1", Stat.INITIATIVE)).isEqualTo(8)
        d.expect(DecisionKind.CHOOSE_ACTION, "xargatha")
    }

    @Test
    fun `Hurry up - a lower card can be raised above a higher one (tie breakers change too)`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-silver") }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-3a") } // 11
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") } // 4
            tieBreaker = Team.BLUE
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.unit("arien")
        // 11 against 11 across teams: the coin (Blue) decides, so Arien acts first.
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    // ---- The Ultimate Trick (Ultimate) -------------------------------------------------------------

    @Test
    fun `The Ultimate Trick - Hanu chooses the next action of the hero he targets with Hurry up, and how it is done`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) {
                played("hanu-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") }
            hero("xargatha", Team.RED, Hex(4, -2)) {
                played("xargatha-blue-1")
                hand("xargatha-red-1")
            }
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.unit("arien")
        // Arien's action is chosen by Hanu…
        d.expect(DecisionKind.CHOOSE_ACTION, "hanu")
        d.answer(Choice.Action(ActionType.PRIMARY))
        // … and how it is performed (Dangerous Current's target is adjacent: Xargatha only, so the defense is
        // asked at once) — but the defender's choice stays Xargatha's.
        d.expect(DecisionKind.DEFEND, "xargatha")
        d.defend("xargatha-red-1")
        // After that action Arien is his own again: Xargatha acts, then next turn Arien plays his own card.
        d.expect(DecisionKind.CHOOSE_ACTION, "xargatha")
        d.answer(Choice.Action(ActionType.HOLD))
        d.expect(DecisionKind.PLAY_CARD, "hanu")
        assertThat(d.hero("hanu").vars).doesNotContainKey("hanu.controls")
    }

    @Test
    fun `The Ultimate Trick - Hanu also chooses where the hero moves`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) {
                played("hanu-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_ACTION, "hanu")
        d.answer(Choice.Action(ActionType.MOVEMENT))
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        d.space(4, -4)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -4))
    }

    @Test
    fun `The Ultimate Trick - a choice another hero's Defense text gives the hero stays its own`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) {
                played("hanu-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                played("arien-red-2b") // Rogue Wave, Range 2
                hand("arien-gold", "arien-silver")
            }
            hero("wasp", Team.RED, Hex(3, -4)) { hand("wasp-green-3a") } // Reflect Projectiles
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY) // Arien is the only hero in range with an unresolved card
        d.expect(DecisionKind.CHOOSE_ACTION, "hanu")
        d.answer(Choice.Action(ActionType.PRIMARY))
        d.expect(DecisionKind.CHOOSE_UNIT, "hanu") // Hanu picks Arien's target
        d.unit("wasp")
        d.defend("wasp-green-3a")
        // Reflect Projectiles: "an enemy hero in range discards a card" — Arien chooses his own card.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Hurry up - without the Ultimate the hero chooses its own action`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-silver") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    // ---- Helping Hand / Even the Odds / Outnumber (red) ----------------------------------------------

    private fun helping(card: String) = pos {
        clearMinions()
        hero("hanu", Team.RED, Hex(1, -2)) { played(card) }
        hero("xargatha", Team.RED, Hex(3, -3)) // the friendly hero
        hero("arien", Team.BLUE, Hex(4, -3)) { hand("arien-blue-1") } // 3 away, next to Xargatha
        hero("brogan", Team.BLUE, Hex(1, -5)) { hand("brogan-blue-1") } // 3 away, not next to Xargatha
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent to Hanu
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -4)) // 3 away, next to Xargatha
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // not adjacent to Hanu or Xargatha
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    @Test
    fun `Helping Hand - Choose one, a hero in range adjacent to your friendly hero (ranged attack)`() {
        val d = helping("hanu-red-1")
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "hanu")
        d.option(1)
        // Arien is the only hero in range next to Xargatha: no target question.
        val p = d.expect(DecisionKind.DEFEND, "arien")
        assertThat(p.context.attackTotal).isEqualTo(3)
        assertThat(p.context.rangedAttack).isTrue()
    }

    @Test
    fun `Helping Hand - or a unit adjacent to you`() {
        val d = helping("hanu-red-1")
        d.action("hanu", ActionType.PRIMARY)
        d.option(0)
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
    }

    @Test
    fun `Even the Odds - Range 4 and Attack 4`() {
        val d = helping("hanu-red-2a")
        d.action("hanu", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(4)
    }

    @Test
    fun `Outnumber - the second bullet targets a minion in range adjacent to your friendly hero`() {
        val d = helping("hanu-red-2b")
        d.action("hanu", ActionType.PRIMARY)
        d.option(1)
        // The minion next to Xargatha is the only one: defeated at once (Arien is a hero).
        assertThat(d.s.state.minions.none { it.at == Hex(4, -4) }).isTrue()
        assertThat(d.at("arien")).isEqualTo(Hex(4, -3))
    }

    @Test
    fun `Trusted Sidekick - choose one, or both, in any order`() {
        val d = helping("hanu-red-3a")
        d.action("hanu", ActionType.PRIMARY)
        d.option(0) // the adjacent minion first
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        d.expect(DecisionKind.CONFIRM, "hanu")
        d.yes()
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Trusted Sidekick - a hero adjacent to you is not a target of the second bullet`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-red-3a") }
            hero("xargatha", Team.RED, Hex(2, -3))
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // next to both
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.defend("arien-blue-1") // only the first bullet was possible: no "Choose one" question
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Pile On - both bullets, the second on a minion adjacent to your friendly hero`() {
        val d = helping("hanu-red-3b")
        d.action("hanu", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.s.state.minions.none { it.at == Hex(4, -4) }).isTrue()
        d.yes()
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.hero("hanu").coins).isEqualTo(4)
    }

    // ---- Monkey Trick / Monkey Twist / Monkey Business (green) ----------------------------------------

    @Test
    fun `Monkey Trick - swap two friendly units in radius (never Hanu himself)`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-1") } // radius 1
            hero("xargatha", Team.RED, Hex(2, -2))
            hero("arien", Team.BLUE, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        val m = minionAt(d, Hex(0, -2))
        assertThat(d.units()).containsExactly("xargatha", m)
        d.unit("xargatha")
        assertThat(d.at("xargatha")).isEqualTo(Hex(0, -2))
        assertThat(d.s.state.minion(m)!!.at).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Monkey Trick - one friendly unit in radius is not enough`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-1") }
            hero("xargatha", Team.RED, Hex(2, -2))
            hero("arien", Team.BLUE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // 2 away: outside radius 1
            phase = PositionPhase.Resolving(next = "hanu")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Monkey Twist - radius 2`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-2a") }
            hero("xargatha", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.unit("xargatha")
        assertThat(d.at("xargatha")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Monkey Business - then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-3a") }
            hero("xargatha", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.unit("xargatha")
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.space(1, -1)
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -1))
    }

    // ---- Hear Nothing / See Nothing (green alternates) ---------------------------------------------------

    @Test
    fun `Hear Nothing - swap with an enemy hero in radius`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(4, -2))
            hero("xargatha", Team.RED, Hex(2, -2)) // friendly: not a target
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.at("hanu")).isEqualTo(Hex(4, -2))
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `See Nothing - then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-3b") }
            hero("arien", Team.BLUE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(4, -2))).isEqualTo(1)
    }

    // ---- Unexpected Journey / There and Back Again / Safe Travels (blue) -----------------------------

    @Test
    fun `Unexpected Journey - swap, that hero is immune this turn, and swaps back at the end of turn`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-blue-1") } // 10, radius 2
            hero("arien", Team.BLUE, Hex(3, -2))
            hero("xargatha", Team.RED, Hex(0, -2)) { played("xargatha-red-1") } // 7
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
        // Arien, now next to Xargatha, is immune: her attack has no target.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        d.answer(Choice.Action(ActionType.HOLD))
        // End of turn: back again.
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -2))
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `There and Back Again - radius 3`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-blue-2a") }
            hero("arien", Team.BLUE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -2)) // swapped and swapped back by the end of the turn
        assertThat(d.texts().count { "Hanu and Arien swap places" in it }).isEqualTo(2)
    }

    @Test
    fun `Safe Travels - End of turn swap back, then you may move 1 space (FAQ - still immune then)`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-blue-3a") }
            hero("arien", Team.BLUE, Hex(4, -2))
            hero("xargatha", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -2))
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        // Common FAQ: "a unit that is immune during “this turn” is still immune when end of turn resolves".
        assertThat(probe(d).canTarget("arien", "xargatha", ActionKind.ATTACK, null)).isFalse()
        d.space(1, -1)
        assertThat(d.at("hanu")).isEqualTo(Hex(1, -1))
        // Next turn Arien is no longer immune.
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(probe(d).canTarget("arien", "xargatha", ActionKind.ATTACK, null)).isTrue()
    }

    // ---- This Way! / That Way! (blue alternates) ---------------------------------------------------------

    @Test
    fun `This Way - a friendly hero chooses the distance, Hanu the direction, both move it`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-blue-2b") } // radius 3
            hero("xargatha", Team.RED, Hex(1, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            token("token_rock", Hex(2, -5)) // blocks Xargatha to the east
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        val n = d.expect(DecisionKind.CHOOSE_NUMBER, "xargatha")
        assertThat(n.options).containsExactly(Choice.Number(1), Choice.Number(2))
        d.number(1)
        d.expect(DecisionKind.CHOOSE_SPACE, "hanu")
        // East is blocked for Xargatha and north for Hanu (terrain at (1,-3)): neither direction is offered.
        assertThat(d.spaces()).containsExactly(Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))
        d.space(0, -2)
        assertThat(d.at("hanu")).isEqualTo(Hex(0, -2))
        assertThat(d.at("xargatha")).isEqualTo(Hex(0, -5))
    }

    @Test
    fun `This Way - in the same line the one in front moves first`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(0, -2)) { played("hanu-blue-2b") }
            hero("xargatha", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.number(2)
        assertThat(d.spaces()).contains(Hex(2, -2))
        d.space(2, -2)
        assertThat(d.at("hanu")).isEqualTo(Hex(2, -2))
        assertThat(d.at("xargatha")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `This Way - both the full distance, or neither one moves`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(-1, -1)) { played("hanu-blue-2b") }
            hero("xargatha", Team.RED, Hex(1, -2)) // boxed in: terrain (1,-3) and five rocks
            hero("arien", Team.BLUE, Hex(-5, -4))
            for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) token("token_rock", h)
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.number(1)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
        assertThat(d.at("hanu")).isEqualTo(Hex(-1, -1))
        assertThat(d.at("xargatha")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `That Way - a distance of 1, 2 or 3`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-blue-3b") }
            hero("xargatha", Team.RED, Hex(1, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.CHOOSE_NUMBER, "xargatha").options)
            .containsExactly(Choice.Number(1), Choice.Number(2), Choice.Number(3))
    }

    // ---- immunity, cancellation, items -------------------------------------------------------------

    @Test
    fun `Unexpected Journey - Hanu defeated, his Active effects end - no swap back, no immunity`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) {
                played("hanu-blue-1")
                hand()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // 8: attacks next
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        assertThat(d.at("hanu")).isEqualTo(Hex(2, -2))
        d.action("arien", ActionType.PRIMARY) // Dangerous Current on Hanu, who has no card to defend
        assertThat(d.at("hanu")).isNull()
        d.expect(DecisionKind.PLAY_CARD) // the turn ended: no swap back
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
        assertThat(d.texts().count { "swap places" in it }).isEqualTo(1)
    }

    @Test
    fun `Hear Nothing - an immune enemy hero cannot be swapped with`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) { played("hanu-green-2b") }
            hero("arien", Team.BLUE, Hex(3, -2))
            effect(
                "hanu",
                "hanu-blue-1",
                Timing.THIS_TURN,
                ActionKind.SKILL,
                data = mapOf("hero" to "arien"),
                key = "hanu.immune",
            )
            phase = PositionPhase.Resolving(next = "hanu")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Hurry up - a Range item adds to its Range 4`() {
        val d = pos {
            clearMinions()
            hero("hanu", Team.RED, Hex(1, -2)) {
                played("hanu-silver")
                items("hanu-green-3b") // +1 Range
            }
            hero("arien", Team.BLUE, Hex(-4, -2)) { played("arien-red-1") } // 5 away
            phase = PositionPhase.Resolving(next = "hanu")
        }
        d.action("hanu", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_ACTION, "arien") // the only hero in range 5: chosen without a question
        assertThat(probe(d).printedValue("arien", "arien-red-1", Stat.INITIATIVE)).isEqualTo(11)
    }

    // ---- random games -----------------------------------------------------------------------------

    @Test
    fun `random legal games with Hanu keep every invariant and end`() {
        RandomGames.play("hanu", games = 150, seed = 4_003)
    }
}
