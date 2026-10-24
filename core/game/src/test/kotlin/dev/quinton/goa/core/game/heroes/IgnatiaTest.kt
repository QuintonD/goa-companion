package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Ignatia the Mad: every card, clause by clause (text from `catalog/heroes/ignatia.json`). */
class IgnatiaTest {
    private val me = Hex(1, -2)

    /** Ignatia (Red) at (1,-2) with [card] played, the coin showing [coin]; minions far away on both teams. */
    private fun ig(
        card: String,
        coin: Team,
        equilibrium: Boolean = false,
        ultimate: Boolean = false,
        block: PositionBuilder.() -> Unit,
    ): Driver = pos {
        clearMinions()
        tieBreaker = coin
        hero("ignatia", Team.RED, me) {
            played(card)
            if (ultimate) ultimate()
        }
        if (equilibrium) effect("ignatia", "ignatia-silver", Timing.THIS_ROUND, ActionKind.SKILL)
        minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -7))
        block()
        phase = PositionPhase.Resolving(next = "ignatia")
    }

    private fun minionAt(d: Driver, h: Hex) = d.s.state.minions.first { it.at == h }.id

    private fun attacked(d: Driver) = d.s.events.filterIsInstance<GameEvent.Attacked>().map { it.targetId }

    private fun magma(d: Driver) = d.s.state.pieces.filter { it.kind == IgnatiaHero.MAGMA }.mapNotNull { it.at }.toSet()

    // ---- Chaos Bolt (gold) / Unreliable Jolt (Handicap) -----------------------------------------

    @Test
    fun `Chaos Bolt - blue coin, target a minion adjacent to you (not a hero)`() {
        val d = ig("ignatia-gold", Team.BLUE) {
            hero("arien", Team.BLUE, Hex(2, -2)) // adjacent hero: not on the blue side
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        assertThat(attacked(d)).hasSize(1) // the only valid target, chosen by the engine
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Chaos Bolt - orange coin, target a hero in range (not a minion)`() {
        val d = ig("ignatia-gold", Team.RED) {
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") } // 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent minion: not on the orange side
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
        assertThat(d.d.context.rangedAttack).isTrue()
    }

    @Test
    fun `Unreliable Jolt - blue a hero adjacent, orange a hero in range not adjacent`() {
        val blue = pos {
            clearMinions()
            tieBreaker = Team.BLUE
            hero("ignatia", Team.RED, me) {
                handicap = true
                played("ignatia-handicap")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "ignatia")
        }
        blue.action("ignatia", ActionType.PRIMARY)
        blue.expect(DecisionKind.DEFEND, "arien")
        val orange = pos {
            clearMinions()
            tieBreaker = Team.RED
            hero("ignatia", Team.RED, me) {
                handicap = true
                played("ignatia-handicap")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "ignatia")
        }
        orange.action("ignatia", ActionType.PRIMARY)
        orange.expect(DecisionKind.DEFEND, "brogan")
    }

    // ---- Equilibrium (silver) ------------------------------------------------------------------

    @Test
    fun `Equilibrium - This round, the primary action may apply either text regardless of the coin`() {
        val d = ig("ignatia-silver", Team.RED) {
            hero("arien", Team.BLUE, Hex(-4, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        val e = d.s.state.effects.single { it.cardId == "ignatia-silver" }
        assertThat(e.timing).isEqualTo(Timing.THIS_ROUND)
        // Later this round: the coin shows orange, yet Chaos Bolt may apply the blue text.
        val g = ig("ignatia-gold", Team.RED, equilibrium = true) {
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        g.action("ignatia", ActionType.PRIMARY)
        g.expect(DecisionKind.CHOOSE_OPTION, "ignatia")
        assertThat(g.d.options.map { (it as Choice.Option).label })
            .containsExactly("Blue: Target a minion adjacent to you", "Orange: Target a hero in range").inOrder()
        g.option(0)
        assertThat(g.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
    }

    @Test
    fun `Equilibrium - only a side whose text can be done is offered`() {
        val d = ig("ignatia-gold", Team.RED, equilibrium = true) {
            hero("arien", Team.BLUE, Hex(-4, -2)) // out of range: the orange text can't be done
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue() // blue applied, no question
    }

    @Test
    fun `Equilibrium - each repeat may apply either text too`() {
        val d = ig("ignatia-red-3b", Team.BLUE, equilibrium = true) {
            hero("arien", Team.BLUE, Hex(-6, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // adjacent
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -7)) // 5 away: maximum range
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "ignatia")
        d.option(0) // blue: adjacent, may repeat once on a minion
        d.unit(minionAt(d, Hex(2, -2)))
        d.yes()
        d.expect(DecisionKind.CHOOSE_OPTION, "ignatia") // the repeat chooses again
        d.option(1) // orange: the minion at maximum range
        assertThat(d.s.state.minions.none { it.at == Hex(1, -7) || it.at == Hex(2, -2) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(0, -2) }).isTrue()
    }

    // ---- Playing with Fire / Erratic Fireblast / Loosely-Aimed Firebolts (red) --------------------

    private fun fire(card: String, coin: Team): Driver = ig(card, coin) {
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // straight line, 2 away
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // not in a straight line, 2 away
    }

    @Test
    fun `Playing with Fire and Erratic Fireblast - blue targets off the straight line, orange on it`() {
        for (card in listOf("ignatia-red-1", "ignatia-red-2a")) {
            val blue = fire(card, Team.BLUE)
            blue.action("ignatia", ActionType.PRIMARY)
            assertThat(blue.s.state.minions.none { it.at == Hex(3, -3) }).isTrue()
            assertThat(blue.s.state.minions.any { it.at == Hex(3, -2) }).isTrue()
            val orange = fire(card, Team.RED)
            orange.action("ignatia", ActionType.PRIMARY)
            assertThat(orange.s.state.minions.none { it.at == Hex(3, -2) }).isTrue()
            assertThat(orange.s.state.minions.any { it.at == Hex(3, -3) }).isTrue()
        }
    }

    @Test
    fun `Loosely-Aimed Firebolts - orange, may repeat once on a different hero`() {
        val d = ig("ignatia-red-3a", Team.RED) {
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(1, -5)) { hand("brogan-blue-1") } // 3 away, straight line
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // a minion in line: not for the repeat
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CONFIRM, "ignatia")
        d.yes()
        d.expect(DecisionKind.DEFEND, "brogan") // the only different hero
        // Blue: no repeat.
        val e = ig("ignatia-red-3a", Team.BLUE) {
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(-1, -1)) { hand("brogan-blue-1") } // not in a straight line either
        }
        e.action("ignatia", ActionType.PRIMARY)
        e.unit("arien")
        e.defend("arien-blue-1")
        assertThat(e.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    // ---- Crack of Doom / Imminent Eruption (red) ------------------------------------------------

    @Test
    fun `Crack of Doom - blue a unit adjacent, orange a unit at maximum range`() {
        fun play(coin: Team): Driver {
            val d = ig("ignatia-red-2b", coin) {
                hero("arien", Team.BLUE, Hex(-6, -2)) // 7 away, out of the way
                minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // adjacent
                minion(Team.BLUE, MinionType.MELEE, Hex(1, -7)) // 5 away: maximum range
                minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away: neither
            }
            d.action("ignatia", ActionType.PRIMARY)
            return d
        }
        val blue = play(Team.BLUE)
        assertThat(blue.s.state.minions.none { it.at == Hex(2, -2) }).isTrue()
        assertThat(blue.s.state.minions.count { it.team == Team.BLUE }).isEqualTo(3)
        val orange = play(Team.RED)
        assertThat(orange.s.state.minions.none { it.at == Hex(1, -7) }).isTrue()
        assertThat(orange.s.state.minions.any { it.at == Hex(4, -2) }).isTrue()
    }

    @Test
    fun `Imminent Eruption - blue, may repeat once on a minion, orange no repeat`() {
        val d = ig("ignatia-red-3b", Team.BLUE) {
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(0, -2)) { hand("brogan-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.yes()
        // A minion only: Brogan (adjacent) is not offered, the minion is attacked.
        assertThat(d.s.state.minions.none { it.at == Hex(1, -1) }).isTrue()
        assertThat(attacked(d)).doesNotContain("brogan")
        val e = ig("ignatia-red-3b", Team.RED) {
            hero("arien", Team.BLUE, Hex(-6, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -7))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
        }
        e.action("ignatia", ActionType.PRIMARY)
        assertThat(e.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
        assertThat(e.s.state.minions.any { it.at == Hex(2, -2) }).isTrue()
    }

    // ---- Path of Ashes / Cinders / Flames (green) ------------------------------------------------

    @Test
    fun `Path of Ashes - blue, move up to 2 in a straight line, Magma in each space moved through or out of`() {
        val d = ig("ignatia-green-1", Team.BLUE) { hero("arien", Team.BLUE, Hex(-4, -2)) }
        d.action("ignatia", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "ignatia")
        assertThat(d.spaces()).doesNotContain(Hex(4, -2)) // 3 away
        assertThat(d.spaces()).doesNotContain(Hex(3, -3)) // not in a straight line
        d.space(3, -2)
        assertThat(d.at("ignatia")).isEqualTo(Hex(3, -2))
        assertThat(magma(d)).containsExactly(Hex(1, -2), Hex(2, -2)) // out of, through; not the end
    }

    @Test
    fun `Path of Ashes - orange, place up to 2 Magma tokens in radius`() {
        val d = ig("ignatia-green-1", Team.RED) { hero("arien", Team.BLUE, Hex(-4, -2)) }
        d.action("ignatia", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "ignatia")
        assertThat(d.spaces().all { HexGrid.distance(it, me) in 1..3 }).isTrue()
        d.space(2, -2)
        d.space(3, -2)
        assertThat(magma(d)).containsExactly(Hex(2, -2), Hex(3, -2))
        assertThat(d.at("ignatia")).isEqualTo(me)
    }

    @Test
    fun `Path of Cinders and Path of Flames - up to 3 and up to 4`() {
        for ((card, n) in listOf("ignatia-green-2a" to 3, "ignatia-green-3a" to 4)) {
            val d = ig(card, Team.RED) { hero("arien", Team.BLUE, Hex(-4, -2)) }
            d.action("ignatia", ActionType.PRIMARY)
            repeat(n) { d.space(d.spaces().first()) }
            assertThat(magma(d)).hasSize(n)
            val e = ig(card, Team.BLUE) { hero("arien", Team.BLUE, Hex(-6, -2)) }
            e.action("ignatia", ActionType.PRIMARY)
            assertThat(e.spaces()).contains(Hex(1 - n, -2))
            assertThat(e.spaces()).doesNotContain(Hex(-n, -2))
        }
    }

    // ---- Abrupt Combustion / Spontaneous Immolation / Violent Conflagration (blue) ---------------

    @Test
    fun `Abrupt Combustion - blue, an enemy hero in radius adjacent to a token or a minion discards`() {
        for (card in listOf("ignatia-blue-1", "ignatia-blue-2a")) {
            val d = ig(card, Team.BLUE) {
                hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
                hero("brogan", Team.BLUE, Hex(-1, -2)) { hand("brogan-gold") } // next to nothing
                token(IgnatiaHero.MAGMA, Hex(4, -2), owner = "ignatia")
            }
            d.action("ignatia", ActionType.PRIMARY)
            d.expect(DecisionKind.CHOOSE_CARD, "arien") // Arien is the only valid hero
            d.card("arien-gold")
            assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        }
    }

    @Test
    fun `Abrupt Combustion - orange, remove an enemy minion in radius adjacent to an enemy hero, no coins`() {
        val d = ig("ignatia-blue-1", Team.RED) {
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // next to Arien
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // next to no hero
        }
        d.action("ignatia", ActionType.PRIMARY)
        assertThat(d.s.state.minions.none { it.at == Hex(3, -3) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(-1, -2) }).isTrue()
        assertThat(d.hero("ignatia").coins).isEqualTo(0) // removed, not defeated
    }

    @Test
    fun `Spontaneous Immolation - orange, a heavy minion with friendly minions in the Battle Zone is immune`() {
        val d = ig("ignatia-blue-2a", Team.RED) {
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.HEAVY, Hex(3, -3)) // immune: the Blue melee minion guards it
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Violent Conflagration - blue discards a card or is defeated, orange defeats the minion (coins)`() {
        val d = ig("ignatia-blue-3a", Team.BLUE) {
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() }
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull() // no card: defeated
        val e = ig("ignatia-blue-3a", Team.RED) {
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -3))
        }
        e.action("ignatia", ActionType.PRIMARY)
        assertThat(e.s.state.minions.none { it.at == Hex(3, -3) }).isTrue()
        assertThat(e.hero("ignatia").coins).isEqualTo(2)
    }

    // ---- Unstable Portal / Chaos Gate (green) ---------------------------------------------------

    @Test
    fun `Unstable Portal - blue swap with a friendly unit in radius, orange with an enemy unit`() {
        val blue = ig("ignatia-green-2b", Team.BLUE) {
            hero("xargatha", Team.RED, Hex(4, -2))
            hero("arien", Team.BLUE, Hex(-1, -2))
        }
        blue.action("ignatia", ActionType.PRIMARY)
        assertThat(blue.at("ignatia")).isEqualTo(Hex(4, -2))
        assertThat(blue.at("xargatha")).isEqualTo(me)
        val orange = ig("ignatia-green-2b", Team.RED) {
            hero("xargatha", Team.RED, Hex(4, -2))
            hero("arien", Team.BLUE, Hex(-1, -2))
        }
        orange.action("ignatia", ActionType.PRIMARY)
        assertThat(orange.at("ignatia")).isEqualTo(Hex(-1, -2))
        assertThat(orange.at("arien")).isEqualTo(me)
    }

    @Test
    fun `Chaos Gate - blue, you may move that unit 1 space, orange, you may move 1 space`() {
        val blue = ig("ignatia-green-3b", Team.BLUE) {
            hero("xargatha", Team.RED, Hex(4, -2))
            hero("arien", Team.BLUE, Hex(-4, -2))
        }
        blue.action("ignatia", ActionType.PRIMARY)
        blue.expect(DecisionKind.CHOOSE_SPACE, "ignatia")
        assertThat(blue.d.context.subjectId).isEqualTo("xargatha")
        assertThat(blue.d.options).contains(Choice.Decline("Don't move"))
        blue.space(0, -2)
        assertThat(blue.at("xargatha")).isEqualTo(Hex(0, -2))
        val orange = ig("ignatia-green-3b", Team.RED) {
            hero("xargatha", Team.RED, Hex(-4, -2))
            hero("arien", Team.BLUE, Hex(4, -2))
        }
        orange.action("ignatia", ActionType.PRIMARY)
        orange.expect(DecisionKind.CHOOSE_SPACE, "ignatia")
        assertThat(orange.d.context.subjectId).isEqualTo("ignatia")
        orange.space(3, -2)
        assertThat(orange.at("ignatia")).isEqualTo(Hex(3, -2))
    }

    // ---- Searing Heat / Scorching Blaze (blue) ---------------------------------------------------

    @Test
    fun `Searing Heat - blue moves a friendly hero, orange an enemy hero, 2 spaces in a straight line`() {
        val blue = ig("ignatia-blue-2b", Team.BLUE) {
            hero("xargatha", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-1, -2))
        }
        blue.action("ignatia", ActionType.PRIMARY)
        blue.expect(DecisionKind.CHOOSE_SPACE, "ignatia")
        assertThat(blue.d.context.subjectId).isEqualTo("xargatha")
        for (h in blue.spaces()) {
            assertThat(HexGrid.distance(h, Hex(3, -2))).isEqualTo(2)
            assertThat(HexGrid.inStraightLine(h, Hex(3, -2))).isTrue()
        }
        assertThat(blue.d.options.none { it is Choice.Decline }).isTrue() // mandatory
        val orange = ig("ignatia-blue-2b", Team.RED) {
            hero("xargatha", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-1, -2))
        }
        orange.action("ignatia", ActionType.PRIMARY)
        assertThat(orange.d.context.subjectId).isEqualTo("arien")
    }

    @Test
    fun `Scorching Blaze - 2 or 3 spaces in a straight line`() {
        val d = ig("ignatia-blue-3b", Team.RED) {
            hero("arien", Team.BLUE, Hex(-1, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        assertThat(d.spaces().map { HexGrid.distance(it, Hex(-1, -2)) }.toSet()).containsExactly(2, 3)
    }

    // ---- Chaos Incarnate (Ultimate) --------------------------------------------------------------

    @Test
    fun `Chaos Incarnate - after a primary action, may flip the coin, then may perform it again`() {
        val d = ig("ignatia-gold", Team.BLUE, ultimate = true) {
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        d.action("ignatia", ActionType.PRIMARY) // blue: the adjacent minion
        d.expect(DecisionKind.CONFIRM, "ignatia")
        d.yes() // flip: orange now
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.RED)
        d.expect(DecisionKind.CONFIRM, "ignatia")
        d.yes()
        d.expect(DecisionKind.DEFEND, "arien") // orange text: a hero in range
        d.defend("arien-blue-1")
        // "The first time each turn": the second performance does not offer it again.
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Chaos Incarnate - flipping is optional, and the second performance is optional`() {
        val d = ig("ignatia-gold", Team.BLUE, ultimate = true) {
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.decline()
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
        val e = ig("ignatia-gold", Team.BLUE, ultimate = true) {
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        }
        e.action("ignatia", ActionType.PRIMARY)
        e.yes()
        e.decline() // flipped, but no second performance
        assertThat(e.s.state.tieBreaker).isEqualTo(Team.RED)
        assertThat(attacked(e)).hasSize(1)
    }

    @Test
    fun `Chaos Incarnate - choosing different targets excludes the first targets`() {
        // With Equilibrium both performances may apply the blue text: the second can't target Arien again.
        val d = ig("ignatia-red-1", Team.BLUE, equilibrium = true, ultimate = true) {
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-blue-1") } // not in a straight line
            hero("brogan", Team.BLUE, Hex(-1, -1)) { hand("brogan-blue-1") } // not in a straight line
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // in a straight line
        }
        d.action("ignatia", ActionType.PRIMARY)
        d.option(0)
        d.unit("arien")
        d.defend("arien-blue-1") // Defense 5 against Attack 3: Arien stays
        d.yes() // flip
        d.yes() // perform again
        d.option(0) // blue again
        d.expect(DecisionKind.DEFEND, "brogan") // Arien is excluded: Brogan is the only target
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with Ignatia run without errors`() {
        Batch7Games.run("ignatia", games = 150, seed = 11)
    }
}
