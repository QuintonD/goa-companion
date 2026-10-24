package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch2.Batch2Games
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Whisper the Outcast: every card, clause by clause (text from `catalog/heroes/whisper.json`). */
class WhisperTest {
    private fun Driver.minionAt(h: Hex) = s.state.minions.firstOrNull { it.at == h }

    private fun Driver.labels() = d.options.filterIsInstance<Choice.Option>().map { it.label }

    // ---- Swift Justice (gold) / Vile Accusation (Handicap) --------------------------------------

    @Test
    fun `Swift Justice - FAQ - the defender moves its defense card's full Movement in a straight line`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-gold") } // Range 3
            hero("arien", Team.BLUE, Hex(4, -2)) // all cards in hand: empty discard
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY) // one bullet, one target: no question
        d.defend("arien-red-1") // Defense 6 against 2; Movement 4
        d.expect(DecisionKind.CHOOSE_SPACE, "arien") // that hero performs the movement action
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue()
        val start = Hex(4, -2)
        assertThat(d.spaces().all { HexGrid.distance(start, it) == 4 && HexGrid.inStraightLine(start, it) }).isTrue()
        d.space(4, -6)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -6))
    }

    @Test
    fun `Swift Justice - FAQ - no straight line of full length, the hero stays`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(0, -1)) { played("whisper-gold") }
            hero("arien", Team.BLUE, Hex(0, -4)) // 3 away, boxed in by his own minions
            for (h in listOf(Hex(1, -4), Hex(1, -5), Hex(0, -5), Hex(-1, -4), Hex(-1, -3), Hex(0, -3))) {
                minion(Team.BLUE, MinionType.MELEE, h)
            }
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        d.defend("arien-red-1")
        assertThat(d.at("arien")).isEqualTo(Hex(0, -4))
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Swift Justice - a defense card without a Movement value - no move`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-gold") }
            hero("arien", Team.BLUE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        d.defend("arien-silver") // Spell Break: Defense 3, no Movement icon
        assertThat(d.at("arien")).isEqualTo(Hex(4, -2))
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Swift Justice - it is a movement action - a movement limit below the full distance stops it`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-gold") }
            hero("silverarrow", Team.RED, Hex(4, -4)) { resolved(1, "silverarrow-blue-1") }
            hero("arien", Team.BLUE, Hex(4, -2)) // 2 from Silverarrow: Grasping Roots, at most 1 space
            effect("silverarrow", "silverarrow-blue-1", Timing.THIS_TURN, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        d.defend("arien-red-1")
        assertThat(d.at("arien")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Swift Justice - a hero with a card in the discard is not a first-bullet target`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-gold") }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-red-1") } // the rest is discarded
            phase = PositionPhase.Resolving(next = "whisper")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    private fun justice(card: String = "whisper-gold", ultimate: Boolean = false) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) {
            played(card)
            if (card == "whisper-handicap") handicap = true
            if (ultimate) ultimate()
        }
        hero("arien", Team.BLUE, if (card == "whisper-handicap") Hex(3, -2) else Hex(4, -2)) // at the Range
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1)) // adjacent
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Swift Justice - Choose one - second bullet - a unit adjacent to you`() {
        val d = justice()
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "whisper")
        assertThat(d.labels()).hasSize(2)
        d.option(1)
        assertThat(d.minionAt(Hex(1, -1))).isNull()
    }

    @Test
    fun `Vile Accusation - second bullet - a hero adjacent to you only`() {
        val d = justice("whisper-handicap")
        // The adjacent minion is not a hero: only the first bullet, without a question.
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Grim Reaper - basic actions - choose one, or both (Swift Justice)`() {
        val d = justice(ultimate = true)
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "whisper")
        assertThat(d.labels()).hasSize(4)
        d.option(2) // both: the hero with an empty discard first, then the adjacent unit
        d.defend("arien-silver")
        assertThat(d.minionAt(Hex(1, -1))).isNull()
    }

    // ---- Death Seeker (silver) ------------------------------------------------------------------

    private fun seeker(ultimate: Boolean = false, arienDiscards: Boolean = true) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) {
            played("whisper-silver") // radius 3
            discard("whisper-red-1")
            if (ultimate) ultimate()
        }
        hero("arien", Team.BLUE, Hex(2, -2)) {
            played("arien-red-1") // Initiative 8: acts after Whisper
            if (arienDiscards) hand("arien-gold")
        }
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Death Seeker - an enemy hero in radius with a discard - This turn, immune to enemy actions`() {
        val d = seeker()
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "whisper")
        d.option(0)
        // Arien's Dangerous Current needs an adjacent target: Whisper is immune, so there is none.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Death Seeker - or you may retrieve a discarded card`() {
        val d = seeker()
        d.action("whisper", ActionType.PRIMARY)
        d.option(1)
        d.card("whisper-red-1")
        assertThat(d.zoneOf("whisper", "whisper-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Death Seeker - no enemy hero in radius with a discard - nothing`() {
        val d = seeker(arienDiscards = false)
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
        assertThat(d.actions()).contains(ActionType.PRIMARY)
    }

    @Test
    fun `Death Seeker - immune to an enemy's Active effect too (Warning Shot at the end of the turn)`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-silver") }
            hero("silverarrow", Team.BLUE, Hex(3, -2)) {
                resolved(1, "silverarrow-blue-2b")
                hand("silverarrow-gold")
            }
            effect("silverarrow", "silverarrow-blue-2b", Timing.END_OF_TURN, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY) // nothing to retrieve: immunity, without a question
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // no discard at the end of the turn
    }

    @Test
    fun `Grim Reaper - Death Seeker - both`() {
        val d = seeker(ultimate = true)
        d.action("whisper", ActionType.PRIMARY)
        assertThat(d.labels()).hasSize(4)
        d.option(2) // immune, then retrieve
        d.card("whisper-red-1")
        assertThat(d.zoneOf("whisper", "whisper-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY) // Arien cannot attack him
    }

    // ---- Crimson Trail / Sanguine Path / Blood Pilgrimage (red) ---------------------------------

    private fun trail(card: String, blockOne: Boolean = false) = pos {
        clearMinions()
        // Two empty minion spawn points in radius 2 in the Battle Zone: (0,-6) and (1,-6).
        hero("whisper", Team.RED, Hex(-1, -5)) { played(card) }
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // 2 away: needs a move to be adjacent
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -6))
        minion(Team.RED, MinionType.MELEE, if (blockOne) Hex(1, -6) else Hex(3, -6))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Crimson Trail - you may move up to 1 space if an empty minion spawn point is in radius`() {
        val d = trail("whisper-red-1")
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "whisper")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        assertThat(d.spaces().all { HexGrid.distance(Hex(-1, -5), it) == 1 }).isTrue()
        d.space(0, -5)
        assertThat(d.minionAt(Hex(1, -5))).isNull()
    }

    @Test
    fun `Crimson Trail - spawn points outside the Battle Zone or occupied do not count`() {
        val d = pos {
            clearMinions()
            // In radius 2: lane-1-1 spawn points (not the Battle Zone) and (0,-3), occupied.
            hero("whisper", Team.RED, Hex(-2, -2)) { played("whisper-red-1") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(-3, -2), lane = "lane-1")
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY) // no move offered: the adjacent minion is attacked at once
        assertThat(d.minionAt(Hex(-3, -2))).isNull()
        assertThat(d.at("whisper")).isEqualTo(Hex(-2, -2))
    }

    @Test
    fun `Sanguine Path - up to 1 space for every empty minion spawn point, at most 2`() {
        val two = trail("whisper-red-2a")
        two.action("whisper", ActionType.PRIMARY)
        assertThat(two.spaces().maxOf { HexGrid.distance(Hex(-1, -5), it) }).isEqualTo(2)
        val one = trail("whisper-red-2a", blockOne = true)
        one.action("whisper", ActionType.PRIMARY)
        assertThat(one.spaces().maxOf { HexGrid.distance(Hex(-1, -5), it) }).isEqualTo(1)
    }

    @Test
    fun `Blood Pilgrimage - at most 3, but never more than the spawn points`() {
        val d = trail("whisper-red-3a")
        d.action("whisper", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(-1, -5), it) }).isEqualTo(2)
    }

    // ---- Blood Fury / Blood Frenzy (red alternates) ---------------------------------------------

    private fun fury(card: String, arienDiscards: Boolean = true) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) { played(card) } // radius 5
        hero("arien", Team.BLUE, Hex(4, -5)) { if (arienDiscards) hand("arien-gold") }
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Blood Fury - an enemy hero in radius has a discard - may repeat once on a different target`() {
        val d = fury("whisper-red-2b")
        d.action("whisper", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(2, -2))!!.id)
        d.expect(DecisionKind.CONFIRM, "whisper")
        d.yes()
        d.expect(DecisionKind.CHOOSE_UNIT, "whisper")
        assertThat(d.units().map { d.at(it) }.toSet()).containsExactly(Hex(0, -2), Hex(1, -1))
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // once only
    }

    @Test
    fun `Blood Fury - no enemy hero in radius with a discard - no repeat`() {
        val d = fury("whisper-red-2b", arienDiscards = false)
        d.action("whisper", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(2, -2))!!.id)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Blood Frenzy - repeat up to five times on different targets`() {
        val d = fury("whisper-red-3b")
        d.action("whisper", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(2, -2))!!.id)
        d.yes()
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        d.yes() // the last adjacent one: no question
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        d.expect(DecisionKind.CONFIRM, "whisper") // up to five: still asked
        d.decline()
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    // ---- Shadow Step / Shadow Walk / Creeping Shadow (green) -----------------------------------

    @Test
    fun `Shadow Step - place yourself into an empty minion spawn point in range in the battle zone`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(-1, -5)) { played("whisper-green-1") } // Range 3
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "whisper")
        // (-4,-2) is a minion spawn point 3 away too, but in lane-1-1, not the Battle Zone.
        assertThat(d.spaces()).containsExactly(Hex(0, -6), Hex(1, -6), Hex(1, -4), Hex(0, -3), Hex(2, -7))
        d.space(0, -3)
        assertThat(d.at("whisper")).isEqualTo(Hex(0, -3))
    }

    @Test
    fun `Shadow Walk - Range 4, none in range - no Primary action`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(-5, -1)) { played("whisper-green-2a") }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Creeping Shadow - or into a space in range adjacent to such a spawn point`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(-1, -5)) { played("whisper-green-3a") }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        assertThat(d.spaces()).containsAtLeast(Hex(0, -6), Hex(-1, -6), Hex(0, -5))
    }

    // ---- Cruel Twist / Sealed Fate (green alternates) -------------------------------------------

    private fun twist(card: String) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) { played(card) } // Range 4
        hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-gold") } // 3 away, a discard
        hero("brogan", Team.BLUE, Hex(-2, -2)) // 3 away, empty discard
        minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // adjacent (any unit)
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Cruel Twist - swap with a unit adjacent, or an enemy hero in range with a discard, then move up to 2`() {
        val d = twist("whisper-green-2b")
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "whisper")
        assertThat(d.units()).containsExactly("arien", d.minionAt(Hex(0, -2))!!.id)
        d.unit("arien")
        assertThat(d.at("whisper")).isEqualTo(Hex(4, -5))
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
        d.expect(DecisionKind.CHOOSE_SPACE, "whisper")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(4, -5), it) }).isEqualTo(2)
    }

    @Test
    fun `Sealed Fate - move up to 3`() {
        val d = twist("whisper-green-3b")
        d.action("whisper", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(0, -2), it) }).isEqualTo(3)
    }

    // ---- Seeds of Fear / Sprouts of Panic / Blooming Nightmare (blue) --------------------------

    private fun seeds(card: String) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) { played(card) }
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -3)) // on a spawn point
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // not on one
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Seeds of Fear - Target an enemy unit in radius occupying a spawn point, move it 1 space`() {
        val d = seeds("whisper-blue-1")
        d.action("whisper", ActionType.PRIMARY) // the only such unit: no question
        d.expect(DecisionKind.CHOOSE_SPACE, "whisper")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue()
        assertThat(d.spaces().all { HexGrid.distance(Hex(2, -3), it) == 1 }).isTrue()
    }

    @Test
    fun `Seeds of Fear - a hero spawn point is a spawn point too`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(6, -1)) { played("whisper-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(8, -2)) // on a hero spawn point, 2 away
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "whisper")
        assertThat(d.spaces().all { HexGrid.distance(Hex(8, -2), it) == 1 }).isTrue()
    }

    @Test
    fun `Sprouts of Panic and Blooming Nightmare - up to 2 and 3 spaces`() {
        for ((card, n) in listOf("whisper-blue-2a" to 2, "whisper-blue-3a" to 3)) {
            val d = seeds(card)
            d.action("whisper", ActionType.PRIMARY)
            assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
            assertThat(d.spaces().maxOf { HexGrid.distance(Hex(2, -3), it) }).isEqualTo(n)
        }
    }

    // ---- Lesser Evil / Greater Good (blue alternates) -------------------------------------------

    private fun evil(card: String, arienCards: Boolean = true) = pos {
        clearMinions()
        hero("whisper", Team.RED, Hex(1, -2)) { played(card) } // Range 3
        hero("arien", Team.BLUE, Hex(3, -2)) { if (arienCards) hand("arien-gold", "arien-silver") else hand() }
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent, the last non-heavy one
        minion(Team.BLUE, MinionType.HEAVY, Hex(1, -1)) // adjacent, immune while the melee is there
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    @Test
    fun `Lesser Evil - the enemy hero chooses - that hero discards a card, if able`() {
        val d = evil("whisper-blue-2b")
        d.action("whisper", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "arien")
        d.option(0)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Lesser Evil - or you may defeat a minion adjacent to you`() {
        val d = evil("whisper-blue-2b")
        d.action("whisper", ActionType.PRIMARY)
        d.option(1)
        d.expect(DecisionKind.CHOOSE_UNIT, "whisper")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.hero("whisper").coins).isEqualTo(2)
    }

    @Test
    fun `Lesser Evil - FAQ - any option may be chosen, even with no effect, and an enemy hero is required`() {
        val d = evil("whisper-blue-2b", arienCards = false)
        d.action("whisper", ActionType.PRIMARY)
        assertThat(d.labels()).hasSize(2)
        val e = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(1, -2)) { played("whisper-blue-2b") }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Greater Good - that hero discards a card, or is defeated`() {
        val d = evil("whisper-blue-3b", arienCards = false)
        d.action("whisper", ActionType.PRIMARY)
        d.option(0)
        assertThat(d.at("arien")).isNull()
    }

    @Test
    fun `Greater Good - FAQ - up to 3 minions chosen together, so not the last non-heavy and the heavy`() {
        val d = evil("whisper-blue-3b")
        d.action("whisper", ActionType.PRIMARY)
        d.option(1)
        d.expect(DecisionKind.CHOOSE_UNIT, "whisper")
        assertThat(d.units().map { d.at(it) }.toSet()).containsExactly(Hex(0, -2)) // the heavy is immune
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.minionAt(Hex(1, -1))).isNotNull()
    }

    // ---- durations and items --------------------------------------------------------------------

    @Test
    fun `Death Seeker - This turn - the immunity is over the next turn`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("whisper", Team.RED, Hex(1, -2)) { resolved(1, "whisper-silver") }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") }
            effect("whisper", "whisper-silver", Timing.THIS_TURN, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).contains(ActionType.PRIMARY)
    }

    @Test
    fun `Shadow Step - a Range item counts`() {
        val d = pos {
            clearMinions()
            hero("whisper", Team.RED, Hex(0, -2)) {
                played("whisper-green-1")
                items("whisper-red-3b") // +1 Range: 4
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "whisper")
        }
        d.action("whisper", ActionType.PRIMARY)
        assertThat(d.spaces()).containsAtLeast(Hex(0, -6), Hex(3, -1)) // 4 away
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Whisper seated keep every invariant`() {
        Batch2Games.play("whisper", games = 300, seed = 61)
    }
}
