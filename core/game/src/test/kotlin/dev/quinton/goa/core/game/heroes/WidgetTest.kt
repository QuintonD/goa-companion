package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch6.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Widget the Dragonbonded, with Pyro: every card, clause by clause (text from `catalog/heroes/widget.json`). */
class WidgetTest {
    private val pyro = WidgetHero.PYRO

    private fun pyroAt(d: dev.quinton.goa.core.game.Driver): Hex? =
        d.s.state.pieces.firstOrNull { it.kind == pyro && it.at != null }?.at

    // ---- Fight as One (gold) / Follow Along (Handicap) -----------------------------------------

    @Test
    fun `Fight as One - after the attack, may perform a resolved skill card's primary on a different unit`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-gold")
                resolved(1, "widget-green-1") // Fiery Breath, Range 2 from Pyro
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1") } // the target, 2 from Pyro in a line
            hero("brogan", Team.BLUE, Hex(4, -4)) { hand("brogan-gold", "brogan-silver") } // 2 from Pyro, in a line
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.defend("arien-red-1") // Defense 6 against Attack 4
        val offer = d.expect(DecisionKind.CHOOSE_CARD, "widget")
        assertThat(d.cards()).containsExactly("widget-green-1")
        assertThat(offer.options).contains(Choice.Decline())
        d.card("widget-green-1")
        // "targeting a different unit": Arien (the attacked unit) is excluded, so Brogan discards.
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("widget", "widget-green-1")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Fight as One - a skill card that could only affect the attacked unit is not offered`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-gold")
                resolved(1, "widget-green-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1", "arien-gold") }
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.defend("arien-red-1")
        // Arien is the only enemy hero in Pyro's line: no "different unit", no question.
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Fight as One - only resolved skill cards, not discarded ones`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-gold")
                discard("widget-green-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1") }
            hero("brogan", Team.BLUE, Hex(4, -4)) { hand("brogan-gold", "brogan-silver") }
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.defend("arien-red-1")
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Follow Along - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-handicap")
                handicap = true
            }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "widget")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Follow Along - after the attack on a hero, may perform a resolved skill card on another unit`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-handicap")
                handicap = true
                resolved(1, "widget-green-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1") }
            hero("brogan", Team.BLUE, Hex(4, -4)) { hand("brogan-gold", "brogan-silver") }
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.defend("arien-red-1")
        d.expect(DecisionKind.CHOOSE_CARD, "widget")
        d.card("widget-green-1")
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
    }

    // ---- Dragon Bond (silver) ------------------------------------------------------------------

    @Test
    fun `Dragon Bond - with Pyro out of play only the place bullet is possible, in radius`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-silver") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            token("token_rock", Hex(2, -2))
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        // RULINGS 38: no bullet question, the place bullet is the only one that can start.
        d.expect(DecisionKind.CHOOSE_SPACE, "widget")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(1..2)
        assertThat(d.spaces()).doesNotContain(Hex(2, -2)) // a token's space is not empty
        d.space(3, -2)
        assertThat(pyroAt(d)).isEqualTo(Hex(3, -2))
        // "Pyro counts as a token, but is not removed at the end of round."
        val p = d.s.state.pieces.single { it.kind == pyro }
        assertThat(p.token).isTrue()
        assertThat(p.removeAtEndOfRound).isFalse()
    }

    @Test
    fun `Dragon Bond - Pyro in play, place it again into a space in radius`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-silver") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(4, -5), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "widget")
        d.option(0)
        d.space(0, -2)
        assertThat(pyroAt(d)).isEqualTo(Hex(0, -2))
        assertThat(d.s.state.pieces.count { it.kind == pyro }).isEqualTo(1)
    }

    @Test
    fun `Dragon Bond - if Pyro is in play, move both yourself and Pyro up to 2 spaces, in any order`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-silver") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(2, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.option(1)
        d.expect(DecisionKind.CHOOSE_OPTION, "widget") // who moves first
        d.option(1) // Pyro first
        assertThat(d.d.context.subjectId).isEqualTo(d.s.state.pieces.single { it.kind == pyro }.id)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(2, -2))).isAtMost(2)
        d.space(4, -2)
        // Widget's move now: the space Pyro left is free.
        assertThat(d.d.context.subjectId).isEqualTo("widget")
        assertThat(d.spaces()).contains(Hex(2, -2))
        d.space(3, -2)
        assertThat(d.at("widget")).isEqualTo(Hex(3, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Dragon Bond - Pyro stays at the end of round, other tokens are removed (p19)`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(2, -2), owner = "widget")
            token("token_rock", Hex(3, -2))
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(pyroAt(d)).isEqualTo(Hex(2, -2))
        assertThat(d.s.state.pieces.none { it.kind == "token_rock" }).isTrue()
    }

    @Test
    fun `Dragon Bond - Pyro counts as a token, so a Clear action removes it`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") }
            hero("widget", Team.RED, Hex(-1, -5))
            token(pyro, Hex(2, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.CLEAR)
        d.token(d.s.state.pieces.single { it.kind == pyro }.id)
        assertThat(pyroAt(d)).isNull()
    }

    // ---- Diversionary Strike / Attack / Assault (red) -----------------------------------------

    @Test
    fun `Diversionary Strike - After the attack, move Pyro up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-red-1") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(-1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        assertThat(d.hero("widget").coins).isEqualTo(2)
        d.expect(DecisionKind.CHOOSE_SPACE, "widget")
        assertThat(d.d.options).contains(Choice.Decline("Don't move")) // "up to"
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(-1, -2)) }).isEqualTo(2)
        d.space(0, -3)
        assertThat(pyroAt(d)).isEqualTo(Hex(0, -3))
    }

    @Test
    fun `Diversionary Strike - with Pyro out of play the text after the attack does nothing`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-red-1") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        assertThat(d.hero("widget").coins).isEqualTo(2)
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Diversionary Attack and Assault - Pyro moves up to 3 and up to 4 spaces`() {
        for ((card, n) in listOf("widget-red-2a" to 3, "widget-red-3a" to 4)) {
            val d = pos {
                clearMinions()
                hero("widget", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(-5, -4))
                minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
                minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
                minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
                token(pyro, Hex(1, -5), owner = "widget")
                phase = PositionPhase.Resolving(next = "widget")
            }
            d.action("widget", ActionType.PRIMARY)
            assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -5)) }).isEqualTo(n)
        }
    }

    // ---- Airborne Attack / Assault (red alternates) -------------------------------------------

    @Test
    fun `Airborne Attack - Before the attack, you may swap with Pyro in radius, then target a unit adjacent`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-red-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // adjacent to Pyro only
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(3, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        // No unit is adjacent to Widget, but the optional swap first makes the action possible (RULINGS 4).
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "widget")
        d.yes()
        assertThat(d.at("widget")).isEqualTo(Hex(3, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(1, -2))
        assertThat(d.s.state.minions.none { it.at == Hex(4, -2) }).isTrue()
        assertThat(d.hero("widget").coins).isEqualTo(2)
    }

    @Test
    fun `Airborne Attack - declining the swap with no adjacent target stops the action`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-red-2b") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(3, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.decline()
        assertThat(d.at("widget")).isEqualTo(Hex(1, -2))
        assertThat(d.texts().any { "stops" in it }).isTrue()
    }

    @Test
    fun `Airborne Attack - Pyro outside the radius is not offered`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -2)) { played("widget-red-2b") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(3, -2), owner = "widget") // 4 away
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        // No swap question: the adjacent minion is attacked straight away.
        assertThat(d.hero("widget").coins).isEqualTo(2)
    }

    @Test
    fun `Airborne Assault - After the attack, you may swap with Pyro in radius again`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-red-3b") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(3, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.yes()
        d.expect(DecisionKind.CONFIRM, "widget")
        d.yes()
        assertThat(d.at("widget")).isEqualTo(Hex(1, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(3, -2))
    }

    // ---- Fiery / Flaming / Scorching Breath (green) -------------------------------------------

    @Test
    fun `Fiery Breath - an enemy hero in range of Pyro and in a straight line from Pyro discards, if able`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-green-1") } // Range 2
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") } // 2 from Pyro, in a line
            hero("brogan", Team.BLUE, Hex(3, -3)) { hand("brogan-gold") } // 2 from Pyro, not in a line
            hero("xargatha", Team.BLUE, Hex(4, -2)) { hand("xargatha-gold") } // in a line, 3 from Pyro
            token(pyro, Hex(1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        // Only Arien qualifies (no target question); he chooses his card.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-silver")
        assertThat(d.zoneOf("arien", "arien-silver")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Fiery Breath - with Pyro out of play the action is not offered`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-green-1") }
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "widget")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Flaming Breath - range 3 from Pyro`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-green-2a") } // Range 3
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            hero("xargatha", Team.BLUE, Hex(4, -2)) { hand("xargatha-gold", "xargatha-silver") }
            token(pyro, Hex(1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "widget")
        assertThat(d.units()).containsExactly("arien", "xargatha")
        d.unit("xargatha")
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha")
    }

    @Test
    fun `Scorching Breath - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-green-3a") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() }
            token(pyro, Hex(1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("widget").coins).isEqualTo(1)
    }

    // ---- Nibble / Gnaw (green alternates) -------------------------------------------------------

    @Test
    fun `Nibble - remove an enemy minion in range adjacent to Pyro, then remove Pyro`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-green-2b") } // Range 3
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -1)) // adjacent to Pyro, 4 from Widget
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3)) // adjacent to Pyro, in range
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // in range, not adjacent to Pyro
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // friendly
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        // A single valid minion: removed without a question; no coins (p.19 "Remove").
        assertThat(d.s.state.minions.none { it.at == Hex(4, -3) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(4, -1) }).isTrue()
        assertThat(d.hero("widget").coins).isEqualTo(0)
        assertThat(pyroAt(d)).isNull()
    }

    @Test
    fun `Gnaw - defeat a minion in range adjacent to Pyro (coins), then remove Pyro`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-green-3b") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.BLUE, MinionType.RANGED, Hex(3, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // friendly, adjacent to Pyro: never defeated
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "widget")
        val friendly = d.s.state.minions.single { it.team == Team.RED }.id
        assertThat(d.units()).doesNotContain(friendly)
        assertThat(d.units()).hasSize(2)
        d.unit(d.s.state.minions.single { it.at == Hex(3, -1) }.id)
        assertThat(d.hero("widget").coins).isEqualTo(2)
        assertThat(pyroAt(d)).isNull()
    }

    // ---- All Aboard / Take-Off / Safe Landing (blue) ------------------------------------------

    @Test
    fun `All Aboard - swap Pyro in range with yourself or with a friendly hero in range`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-blue-1") } // Range 2
            hero("xargatha", Team.RED, Hex(2, -3)) // friendly, 1 away
            hero("wasp", Team.RED, Hex(4, -5)) // friendly, 3 away
            hero("arien", Team.BLUE, Hex(0, -2)) // enemy, adjacent
            token(pyro, Hex(3, -2), owner = "widget") // 2 away
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "widget")
        assertThat(d.units()).containsExactly("widget", "xargatha")
        d.unit("xargatha")
        assertThat(d.at("xargatha")).isEqualTo(Hex(3, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(2, -3))
    }

    @Test
    fun `All Aboard - Pyro out of range, no action`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-blue-1") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(4, -2), owner = "widget") // 3 away
            phase = PositionPhase.Resolving(next = "widget")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Take-Off - range 3`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-blue-2a") }
            hero("xargatha", Team.RED, Hex(2, -3))
            hero("wasp", Team.RED, Hex(4, -5)) // 3 away
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(4, -2), owner = "widget") // 3 away
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("widget", "xargatha", "wasp")
        d.unit("widget")
        assertThat(d.at("widget")).isEqualTo(Hex(4, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Safe Landing - you may move Pyro in range 1 space, then swap Pyro in range`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-blue-3a") } // Range 3
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "widget")
        assertThat(d.d.options).contains(Choice.Decline("Don't move"))
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(4, -2))).isEqualTo(1)
        d.space(3, -2)
        // Only Widget can swap: no question.
        assertThat(d.at("widget")).isEqualTo(Hex(3, -2))
        assertThat(pyroAt(d)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Safe Landing - moving Pyro out of range makes the swap impossible, the action stops`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-blue-3a") }
            hero("arien", Team.BLUE, Hex(-1, -5))
            token(pyro, Hex(4, -3), owner = "widget") // 3 away
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.space(5, -3) // 4 away
        assertThat(d.at("widget")).isEqualTo(Hex(1, -2))
        assertThat(d.texts().any { "stops" in it }).isTrue()
    }

    // ---- Drag Off / Carry Away (blue alternates) ------------------------------------------------

    @Test
    fun `Drag Off - move Pyro and an enemy unit adjacent to it 2 or 3 spaces each, in the same direction`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-blue-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // east of Pyro
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "widget")
        // East: the minion can only go 2 (terrain at 5,-2), so Pyro (behind it) can only go 2.
        assertThat(d.spaces()).contains(Hex(3, -2))
        assertThat(d.spaces()).doesNotContain(Hex(4, -2))
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(2..3)
        d.space(3, -2)
        assertThat(pyroAt(d)).isEqualTo(Hex(3, -2))
        assertThat(d.s.state.minions.any { it.at == Hex(4, -2) }).isTrue()
    }

    @Test
    fun `Drag Off - each may move a different distance of 2 or 3, in the same direction`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -2)) { played("widget-blue-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5)) // east of Pyro
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(2, -5), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        // Both go "up" (r decreasing) on parallel lines: Pyro 2, then the minion may go 2 or 3.
        d.space(2, -7)
        assertThat(d.spaces()).containsExactly(Hex(3, -7), Hex(3, -8))
        d.space(3, -8)
        assertThat(pyroAt(d)).isEqualTo(Hex(2, -7))
        assertThat(d.s.state.minions.any { it.at == Hex(3, -8) }).isTrue()
    }

    @Test
    fun `Carry Away - ignoring obstacles, the pair may pass a token in the way`() {
        fun build(card: String) = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played(card) }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            token(pyro, Hex(0, -2), owner = "widget")
            token("token_rock", Hex(2, -2))
            phase = PositionPhase.Resolving(next = "widget")
        }
        val drag = build("widget-blue-2b")
        drag.action("widget", ActionType.PRIMARY)
        assertThat(drag.spaces().none { it.r == -2 && it.q > 0 }).isTrue() // east is blocked
        val carry = build("widget-blue-3b")
        carry.action("widget", ActionType.PRIMARY)
        assertThat(carry.spaces()).contains(Hex(3, -2))
        carry.space(3, -2)
        // Pyro first (3 spaces, through the minion), then the minion 3 spaces through the rock and Pyro.
        assertThat(pyroAt(carry)).isEqualTo(Hex(3, -2))
        assertThat(carry.s.state.minions.any { it.at == Hex(4, -2) }).isTrue()
    }

    // ---- Dragon Knight (Ultimate) ---------------------------------------------------------------

    @Test
    fun `Dragon Knight - after a movement action, may perform the primary action of a faceup skill card`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("widget", Team.RED, Hex(-1, -5)) {
                played("widget-red-1")
                resolved(1, "widget-green-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -4)) { hand("arien-gold", "arien-silver") } // 2 from Pyro, in a line
            token(pyro, Hex(3, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.MOVEMENT)
        d.space(0, -5)
        d.expect(DecisionKind.CHOOSE_CARD, "widget")
        assertThat(d.cards()).containsExactly("widget-green-1") // the played card is an attack card
        d.card("widget-green-1")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Dragon Knight - faceup played, resolved and discarded skill cards count, facedown ones do not`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("widget", Team.RED, Hex(1, -2)) {
                played("widget-green-2a") // Flaming Breath: possible (Arien in Pyro's line)
                discard("widget-silver") // Dragon Bond, faceup in the discard
                resolved(1, "widget-blue-1", faceDown = true) // All Aboard, facedown: no type
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -4)) { hand("arien-gold", "arien-silver") }
            token(pyro, Hex(3, -2), owner = "widget") // in range 2 of Widget
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.MOVEMENT)
        d.space(0, -2)
        assertThat(d.cards()).containsExactly("widget-green-2a", "widget-silver")
        d.decline()
        assertThat(d.pending!!.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    @Test
    fun `Dragon Knight - Fast Travel is not a movement action`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -5)) {
                played("widget-red-1")
                discard("widget-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.FAST_TRAVEL)
        d.space(d.spaces().first())
        assertThat(d.pending!!.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    // ---- immunity and items ----------------------------------------------------------------------

    @Test
    fun `Fiery Breath - a Range item counts, from Pyro (p16)`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) {
                played("widget-green-1") // Range 2 + 1 (Airborne Assault as an item)
                items("widget-red-3b")
            }
            hero("xargatha", Team.BLUE, Hex(4, -2)) { hand("xargatha-gold", "xargatha-silver") } // 3 from Pyro
            token(pyro, Hex(1, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        d.action("widget", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha")
    }

    @Test
    fun `Fiery Breath - an enemy hero immune to Widget's actions can't be chosen (p18)`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(-1, -5)) { played("widget-green-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") } // in Pyro's line
            // Emmitt's Future Proof: friendly heroes in his radius are immune to enemy actions this turn.
            hero("emmitt", Team.BLUE, Hex(4, -5)) { resolved(1, "emmitt-green-3b") }
            token(pyro, Hex(1, -2), owner = "widget")
            effect("emmitt", "emmitt-green-3b", Timing.THIS_TURN, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "widget")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Gnaw - a heavy minion with friendly minions in the Battle Zone is immune (p13)`() {
        val d = pos {
            clearMinions()
            hero("widget", Team.RED, Hex(1, -2)) { played("widget-green-3b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(4, -3)) // adjacent to Pyro, in range
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // not adjacent to Pyro: guards the heavy
            minion(Team.RED, MinionType.MELEE, Hex(4, -7))
            token(pyro, Hex(4, -2), owner = "widget")
            phase = PositionPhase.Resolving(next = "widget")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Widget keep every invariant`() {
        val stats = RandomGames.play("widget", games = 250, seed = 61)
        println("WIDGET random games: $stats")
    }
}
