package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.brynn.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Cutter the Sky Pirate: every card, clause by clause (text from `catalog/heroes/cutter.json`), with the FAQ.
 * Board notes (Forgotten Island): (-1,-2) is lane-1-2 and (-2,-2) lane-1-1, so a push west from (0,-2)
 * crosses into another Zone after 2 spaces.
 */
class CutterTest {
    private fun minionAt(d: Driver, h: Hex): String = d.s.state.minions.first { it.at == h }.id

    // ---- Walk the Plank (gold) / Few More Steps (Handicap) ---------------------------------------

    private fun plank(card: String, arienHand: List<String> = listOf("arien-gold", "arien-silver")) = pos {
        clearMinions()
        hero("cutter", Team.RED, Hex(1, -2)) { played(card) }
        hero("arien", Team.BLUE, Hex(0, -2)) { hand(*arienHand.toTypedArray()) }
        phase = PositionPhase.Resolving(next = "cutter")
    }

    @Test
    fun `Walk the Plank - push an adjacent enemy hero up to 4 into another zone, it discards a card or is defeated`() {
        val d = plank("cutter-gold")
        d.action("cutter", ActionType.PRIMARY) // no adjacent minion: the push is the only bullet
        val n = d.expect(DecisionKind.CHOOSE_NUMBER, "cutter")
        assertThat(n.options).containsExactly(
            Choice.Number(0),
            Choice.Number(1),
            Choice.Number(2),
            Choice.Number(3),
            Choice.Number(4),
        )
        d.number(3)
        assertThat(d.at("arien")).isEqualTo(Hex(-3, -2)) // lane-1-1
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Walk the Plank - a push that stays in the zone has no further effect`() {
        val d = plank("cutter-gold")
        d.action("cutter", ActionType.PRIMARY)
        d.number(1)
        assertThat(d.at("arien")).isEqualTo(Hex(-1, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    @Test
    fun `Walk the Plank - pushed into another zone with no card, the hero is defeated`() {
        val d = plank("cutter-gold", arienHand = emptyList())
        d.action("cutter", ActionType.PRIMARY)
        d.number(2)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("cutter").coins).isEqualTo(1)
    }

    @Test
    fun `Walk the Plank - or defeat a minion adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-gold") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1)) // friendly: never defeated
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "cutter")
        d.option(1)
        assertThat(d.s.state.minions.none { it.at == Hex(2, -2) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(1, -1) }).isTrue()
        assertThat(d.hero("cutter").coins).isEqualTo(2)
    }

    @Test
    fun `Few More Steps - push an adjacent enemy hero up to 3 spaces, no minion bullet`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-handicap") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold", "arien-silver") }
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.CHOOSE_NUMBER).options).hasSize(4) // 0..3
        d.number(3)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Walk the Plank - Bulwark - a hero that cannot be pushed is not offered`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-handicap") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold") }
            hero("brogan", Team.BLUE, Hex(-1, -3))
            effect("brogan", "brogan-silver", Timing.THIS_TURN, ActionKind.SKILL) // Bulwark, radius 4
            phase = PositionPhase.Resolving(next = "cutter")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Grappling Bolt (silver) ------------------------------------------------------------------

    @Test
    fun `Grappling Bolt - the first obstacle in each straight line in range, ignore immunity (FAQ - immune units)`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-silver") } // Range 5
            hero("arien", Team.BLUE, Hex(-5, -4))
            // An immune heavy minion (a blue melee minion is in the Battle Zone) 3 spaces west.
            minion(Team.BLUE, MinionType.HEAVY, Hex(-2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        // East: terrain (5,-2); north-east: terrain (5,-6); north: terrain (1,-3) adjacent; west: the heavy
        // minion; south: terrain (1,2). South-west: nothing within 5.
        assertThat(d.spaces()).containsExactly(Hex(5, -2), Hex(5, -6), Hex(1, -3), Hex(-2, -2), Hex(1, 2))
        d.space(-2, -2)
        assertThat(d.at("cutter")).isEqualTo(Hex(-1, -2)) // moved until adjacent
    }

    @Test
    fun `Grappling Bolt - FAQ - she can grapple onto the water around the map`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(2, -10)) { played("cutter-silver") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(2, -12))
        d.space(2, -12)
        assertThat(d.at("cutter")).isEqualTo(Hex(2, -11))
    }

    @Test
    fun `Grappling Bolt - no obstacles between you - a closer obstacle hides the ones behind it`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-silver") }
            hero("arien", Team.BLUE, Hex(3, -2)) // east, 2 away: the terrain at (5,-2) is behind him
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(3, -2))
        assertThat(d.spaces()).doesNotContain(Hex(5, -2))
        d.space(3, -2)
        assertThat(d.at("cutter")).isEqualTo(Hex(2, -2))
    }

    // ---- Daring Strike / Bold Thrust / Fearless Lunge (red) ---------------------------------------

    private fun lunge(card: String, cutterAt: Hex = Hex(1, -2)) = pos {
        clearMinions()
        hero("cutter", Team.RED, cutterAt) { played(card) }
        hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // adjacent to (1,-2)
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        phase = PositionPhase.Resolving(next = "cutter")
    }

    @Test
    fun `Daring Strike - before the attack move 1 space, target a hero in the direction of the move, +2 Attack`() {
        val d = lunge("cutter-red-1")
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "cutter")
        d.option(0)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(1)
        d.space(2, -2) // east: Arien is next in that direction
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(6)
    }

    @Test
    fun `Daring Strike - no hero in the direction of the move - the move happens, the attack does not`() {
        val d = lunge("cutter-red-1")
        d.action("cutter", ActionType.PRIMARY)
        d.option(0)
        d.space(1, -1) // south-east: nothing in front; Arien is adjacent but not in that direction
        assertThat(d.at("cutter")).isEqualTo(Hex(1, -1))
        assertThat(d.texts()).contains("Cutter's Daring Strike stops: no target.")
    }

    @Test
    fun `Daring Strike - or target a unit adjacent to you`() {
        val d = lunge("cutter-red-1")
        d.action("cutter", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.s.state.minions.none { it.at == Hex(0, -1) }).isTrue()
    }

    @Test
    fun `Bold Thrust - move 1 or 2 spaces in a straight line first`() {
        val d = lunge("cutter-red-2a", cutterAt = Hex(0, -2))
        d.action("cutter", ActionType.PRIMARY)
        d.option(0)
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..2)
            assertThat(HexGrid.inStraightLine(h, Hex(0, -2))).isTrue()
        }
        d.space(2, -2)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
    }

    @Test
    fun `Fearless Lunge - move 1, 2 or 3 spaces in a straight line`() {
        val d = lunge("cutter-red-3a", cutterAt = Hex(-1, -2))
        d.action("cutter", ActionType.PRIMARY)
        // Nothing adjacent to (-1,-2): only the move bullet, so no "Choose one" question.
        d.expect(DecisionKind.CHOOSE_SPACE, "cutter")
        assertThat(d.spaces()).contains(Hex(2, -2))
        d.space(2, -2)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
    }

    // ---- Evasive Shot / Tumble Shot (red alternates) ----------------------------------------------

    @Test
    fun `Evasive Shot - a unit in range and in a straight line, then move up to 2 in the opposite direction`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-red-2b") } // Range 2
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // east, in a straight line
            hero("brogan", Team.BLUE, Hex(3, -3)) // 2 away, not in a straight line
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY) // Arien is the only valid target
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "cutter")
        assertThat(d.spaces()).containsExactly(Hex(0, -2), Hex(-1, -2)) // west only
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.space(-1, -2)
        assertThat(d.at("cutter")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Tumble Shot - up to 3, also after a minion is defeated`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-red-3b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.spaces()).containsExactly(Hex(0, -2), Hex(-1, -2), Hex(-2, -2))
    }

    // ---- Brace for Impact / Ramming Speed / Crashland (green) -------------------------------------

    @Test
    fun `Brace for Impact - move 3 in a straight line ignoring obstacles next to an enemy hero, who discards`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-green-1") }
            hero("arien", Team.BLUE, Hex(4, -3)) { hand("arien-gold", "arien-silver") }
            token("token_rock", Hex(2, -2)) // obstacles on the way are ignored
            token("token_rock", Hex(3, -2))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.at("cutter")).isEqualTo(Hex(4, -2)) // the only end next to Arien
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Brace for Impact - with no such space there is no Primary action`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-green-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold") } // adjacent, but 3 away is needed
            phase = PositionPhase.Resolving(next = "cutter")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Ramming Speed - 3 or 4 spaces`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(0, -2)) { played("cutter-green-2a") }
            hero("arien", Team.BLUE, Hex(4, -3)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        // 3 or 4 east both end next to Arien.
        assertThat(d.spaces()).containsExactly(Hex(3, -2), Hex(4, -2))
        d.space(4, -2)
        assertThat(d.at("cutter")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Crashland - 3, 4 or 5 spaces`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(-1, -2)) { played("cutter-green-3a") }
            hero("arien", Team.BLUE, Hex(4, -3)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        // 4 and 5 east end next to Arien (3 east, (2,-2), does not).
        assertThat(d.spaces()).containsExactly(Hex(3, -2), Hex(4, -2))
        d.space(4, -2)
        assertThat(d.at("cutter")).isEqualTo(Hex(4, -2))
    }

    // ---- Bombardment / Barrage / Broadside (blue) ---------------------------------------------------

    @Test
    fun `Bombardment - an enemy hero in radius, next to another enemy unit and not next to you, discards`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-blue-1") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-gold") } // next to Arien, but next to Cutter
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien") // Arien is the only one: no target question
    }

    @Test
    fun `Bombardment - an enemy hero with no other enemy unit next to it is no target`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-blue-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold") }
            minion(Team.RED, MinionType.MELEE, Hex(4, -2)) // a red minion is not an enemy unit (of Cutter)
            phase = PositionPhase.Resolving(next = "cutter")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Barrage - radius 4, the other enemy unit may be a minion`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(0, -2)) { played("cutter-blue-2a") }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-silver") }
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Broadside - may repeat once on a different target`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-blue-3a") } // radius 4
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            hero("brogan", Team.BLUE, Hex(4, -3)) { hand("brogan-gold", "brogan-silver") } // next to Arien
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", "brogan")
        d.unit("arien")
        d.card("arien-gold")
        d.expect(DecisionKind.CONFIRM, "cutter")
        d.yes()
        d.expect(DecisionKind.CHOOSE_CARD, "brogan") // Arien excluded: Brogan without a question
    }

    // ---- Outmaneuver / Outsmart (green alternates) ----------------------------------------------------

    @Test
    fun `Outmaneuver - swap with an enemy minion in radius, you may move it up to 2`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-green-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // friendly: not offered
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        val m = d.s.state.minions.first { it.team == Team.BLUE }.id
        assertThat(d.at("cutter")).isEqualTo(Hex(3, -2))
        assertThat(d.s.state.minion(m)!!.at).isEqualTo(Hex(1, -2))
        d.expect(DecisionKind.CHOOSE_SPACE, "cutter")
        assertThat(d.d.context.subjectId).isEqualTo(m)
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(2)
        d.space(1, -1)
        assertThat(d.s.state.minion(m)!!.at).isEqualTo(Hex(1, -1))
    }

    @Test
    fun `Outsmart - up to 3`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-green-3b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(3)
    }

    // ---- X Marks the Spot / A Fistful of Coins (blue alternates) --------------------------------------

    private fun xMarks(card: String, coins: Int = 0) = pos {
        clearMinions()
        hero("cutter", Team.RED, Hex(1, -2)) {
            played(card)
            this.coins = coins
        }
        hero("arien", Team.BLUE, Hex(3, -2))
        phase = PositionPhase.Resolving(next = "cutter")
    }

    @Test
    fun `X Marks the Spot - the enemy hero chooses - Cutter places it in a space in radius`() {
        val d = xMarks("cutter-blue-2b")
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "arien")
        d.option(0)
        d.expect(DecisionKind.CHOOSE_SPACE, "cutter")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(1..3)
        assertThat(d.spaces()).doesNotContain(Hex(3, -2))
        d.space(-2, -2)
        assertThat(d.at("arien")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `X Marks the Spot - or Cutter gains 2 coins`() {
        val d = xMarks("cutter-blue-2b")
        d.action("cutter", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.hero("cutter").coins).isEqualTo(2)
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `X Marks the Spot - a hero that cannot be placed only offers the coins (no question)`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(1, -2)) { played("cutter-blue-2b") }
            hero("arien", Team.BLUE, Hex(3, -2))
            hero("brogan", Team.BLUE, Hex(4, -5))
            effect("brogan", "brogan-silver", Timing.THIS_TURN, ActionKind.SKILL) // Bulwark: Arien protected
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        d.unit("arien")
        assertThat(d.hero("cutter").coins).isEqualTo(2)
    }

    @Test
    fun `A Fistful of Coins - 3 coins, and with 13 or more Cutter alone wins the game`() {
        val d = xMarks("cutter-blue-3b", coins = 10)
        d.action("cutter", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.pending).isNull()
        assertThat(d.s.state.winner).isEqualTo(Team.RED)
        assertThat(d.s.state.winReason).isEqualTo(WinReason.CARD_TEXT)
        assertThat(d.hero("cutter").coins).isEqualTo(13)
    }

    @Test
    fun `A Fistful of Coins - under 13 coins the game goes on`() {
        val d = xMarks("cutter-blue-3b", coins = 9)
        d.action("cutter", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.s.state.winner).isNull()
        assertThat(d.hero("cutter").coins).isEqualTo(12)
    }

    // ---- Legend of the Skies (Ultimate) ------------------------------------------------------------

    private fun legend(turn: Int) = pos {
        clearMinions()
        this.turn = turn
        hero("cutter", Team.RED, Hex(1, -2)) {
            played("cutter-gold")
            if (turn > 1) resolved(turn - 1, "cutter-red-1")
            ultimate()
        }
        hero("arien", Team.BLUE, Hex(-5, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5)) // a red minion: no Lane push
        phase = PositionPhase.Resolving(next = "cutter")
    }

    @Test
    fun `Legend of the Skies - after the first primary action, may perform the primary of the previous slot card`() {
        val d = legend(turn = 2)
        d.action("cutter", ActionType.PRIMARY)
        d.unit(minionAt(d, Hex(2, -2))) // Walk the Plank: only the minion bullet (no enemy hero adjacent)
        d.expect(DecisionKind.CONFIRM, "cutter")
        d.yes()
        // Daring Strike (slot 1): choose one, the adjacent unit.
        d.expect(DecisionKind.CHOOSE_OPTION, "cutter")
        d.option(1)
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.hero("cutter").coins).isEqualTo(4)
        // The first time only: no further offer after the second primary action.
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
        assertThat(d.zoneOf("cutter", "cutter-red-1")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Legend of the Skies - on turn 1 there is no previous turn slot`() {
        val d = legend(turn = 1)
        d.action("cutter", ActionType.PRIMARY)
        d.unit(minionAt(d, Hex(2, -2)))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Legend of the Skies - not after a secondary action`() {
        val d = legend(turn = 2)
        d.action("cutter", ActionType.MOVEMENT)
        d.decline()
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    // ---- items -----------------------------------------------------------------------------------------

    @Test
    fun `Bombardment - a Radius item widens radius 3`() {
        val d = pos {
            clearMinions()
            hero("cutter", Team.RED, Hex(0, -2)) {
                played("cutter-blue-1")
                items("cutter-red-3b") // +1 Radius
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-silver") } // 4 away
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            phase = PositionPhase.Resolving(next = "cutter")
        }
        d.action("cutter", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    // ---- random games -----------------------------------------------------------------------------

    @Test
    fun `random legal games with Cutter keep every invariant and end`() {
        RandomGames.play("cutter", games = 150, seed = 4_002)
    }
}
