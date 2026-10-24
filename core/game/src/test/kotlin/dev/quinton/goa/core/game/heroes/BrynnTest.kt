package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
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
 * Brynn the Seeker: every card, clause by clause (text from `catalog/heroes/brynn.json`), with the FAQ.
 *
 * Board notes (Forgotten Island, lane-1-2): (1,-3) is terrain, so a hero at (1,-2) or (0,-2) already
 * has one obstacle next to it; rocks (plain tokens) add the others.
 */
class BrynnTest {
    private fun minionAt(
        d: dev.quinton.goa.core.game.Driver,
        h: Hex,
    ): String = d.s.state.minions.first { it.at == h }.id

    // ---- Familiar Ground (gold) / Home Turf (Handicap) ------------------------------------------

    @Test
    fun `Familiar Ground - Choose one, a unit adjacent or a hero in range adjacent to 3 or more obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(4, -2)) { played("brynn-gold") } // Range 3
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") } // 3 away
            // Arien's obstacles: the terrain at (1,-3), a friendly minion and a rock.
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            token("token_rock", Hex(0, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // adjacent to Brynn
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "brynn")
        d.option(1)
        // Only Arien qualifies (the minions are not heroes): no target question. A ranged attack (RULINGS 19).
        val defend = d.expect(DecisionKind.DEFEND, "arien")
        assertThat(defend.context.attackTotal).isEqualTo(2)
        assertThat(defend.context.rangedAttack).isTrue()
    }

    @Test
    fun `Familiar Ground - a hero with only 2 obstacles next to it is not offered, the adjacent unit is`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(4, -2)) { played("brynn-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // terrain + this minion: 2 obstacles
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        // One bullet possible: no "Choose one" question; the single adjacent minion is the target.
        assertThat(d.s.state.minions.none { it.at == Hex(3, -2) }).isTrue()
        assertThat(d.hero("brynn").coins).isEqualTo(2)
    }

    @Test
    fun `Home Turf - Target a hero adjacent to you, an adjacent minion is no target`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(4, -2)) { played("brynn-handicap") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // adjacent, but not a hero
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Home Turf - a hero in range adjacent to 3 or more obstacles (Range 2)`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(3, -2)) { played("brynn-handicap") } // Range 2
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            token("token_rock", Hex(0, -1))
            token("token_rock", Hex(0, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(2)
    }

    // ---- Decoy (silver) -------------------------------------------------------------------------

    @Test
    fun `Decoy - choose up to two times on different targets, a minion then a hero adjacent to 3 obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -5)) { played("brynn-silver") } // radius 4
            hero("arien", Team.BLUE, Hex(3, -3)) // 4 away
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(3, -2))
            token("token_rock", Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        val first = d.expect(DecisionKind.CHOOSE_OPTION, "brynn")
        assertThat(first.options.filterIsInstance<Choice.Option>()).hasSize(2)
        assertThat(first.options.any { it is Choice.Decline }).isTrue() // "up to"
        d.option(0) // the minion (the only enemy minion in radius: no unit question)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(2, -5))).isEqualTo(1)
        d.space(2, -6)
        // Second time: the moved minion is no longer a valid (different) target, so only the hero bullet.
        val second = d.expect(DecisionKind.CHOOSE_OPTION, "brynn")
        assertThat(second.options.filterIsInstance<Choice.Option>()).hasSize(1)
        d.option(0)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -3))).isEqualTo(1)
        d.space(3, -4)
        assertThat(d.at("arien")).isEqualTo(Hex(3, -4))
        assertThat(d.s.state.minions.first { it.team == Team.BLUE }.at).isEqualTo(Hex(2, -6))
    }

    @Test
    fun `Decoy - an enemy hero with fewer than 3 obstacles next to it is not offered, and none at all may be chosen`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -5)) { played("brynn-silver") }
            hero("arien", Team.BLUE, Hex(3, -3))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.CHOOSE_OPTION).options.filterIsInstance<Choice.Option>()).hasSize(1)
        d.decline()
        assertThat(d.at("arien")).isEqualTo(Hex(3, -3))
        assertThat(d.zoneOf("brynn", "brynn-silver")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Decoy - with no enemy minion or qualifying hero in radius there is no Primary action`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -5)) { played("brynn-silver") }
            hero("arien", Team.BLUE, Hex(3, -3))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- High Ground / Elevated Ambush / Peak Precision (red) ------------------------------------

    @Test
    fun `High Ground - plus 2 attack when the target is a hero adjacent to 3 or more obstacles (Brynn counts)`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-1") } // Attack 4
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            // Arien's obstacles: Brynn herself and two rocks.
            token("token_rock", Hex(3, -2))
            token("token_rock", Hex(2, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(6)
    }

    @Test
    fun `High Ground - no bonus with 2 obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            token("token_rock", Hex(3, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(4)
    }

    @Test
    fun `Elevated Ambush - Attack 5, plus 2 against a hero adjacent to 3 or more obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-2a") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            token("token_rock", Hex(3, -2))
            token("token_rock", Hex(2, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
    }

    @Test
    fun `Peak Precision - plus 2 and After the attack you may retrieve this card`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-3a") } // Attack 5
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-2a") } // Defense 6 + 0
            token("token_rock", Hex(3, -2))
            token("token_rock", Hex(2, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
        d.defend("arien-blue-2a") // 6 < 7: Arien is defeated
        assertThat(d.at("arien")).isNull()
        d.expect(DecisionKind.CONFIRM, "brynn")
        d.yes()
        assertThat(d.zoneOf("brynn", "brynn-red-3a")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Peak Precision - against a minion there is no bonus and no retrieve`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-3a") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
        assertThat(d.zoneOf("brynn", "brynn-red-3a")).isEqualTo(Zone.RESOLVED)
    }

    // ---- Split Attack / Split Throw (red alternates) ---------------------------------------------

    @Test
    fun `Split Attack - a hero next to 3 obstacles targeted, may repeat once on a different unit adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-2b") } // Range 2
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
            token("token_rock", Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(3, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent to Brynn
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4)) // in range, not adjacent
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", minionAt(d, Hex(0, -2)), minionAt(d, Hex(1, -4)))
        d.unit("arien")
        d.defend("arien-blue-1") // Defense 5 vs 3
        d.expect(DecisionKind.CONFIRM, "brynn")
        d.yes()
        // Only the adjacent minion may be the repeat's target: chosen without a question.
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(1, -4) }).isTrue()
    }

    @Test
    fun `Split Attack - no repeat after targeting a hero with fewer obstacles or a minion`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-2b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Split Throw - the repeat may target any different unit in range`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-red-3b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
            token("token_rock", Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(3, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        d.yes()
        assertThat(d.units()).containsExactly(minionAt(d, Hex(0, -2)), minionAt(d, Hex(1, -4)))
    }

    // ---- Bear Trap / Log Trap / Deadfall Trap (green) --------------------------------------------

    @Test
    fun `Bear Trap - Choose one, the hero in radius adjacent to 3 obstacles discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-1") } // radius 3
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold") } // adjacent
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold", "brogan-silver") } // 3 away
            token("token_rock", Hex(4, -3)) // with the terrain at (5,-2) and the rock at (4,-1): 3
            token("token_rock", Hex(4, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "brynn")
        d.option(1)
        d.expect(DecisionKind.CHOOSE_CARD, "brogan") // Brogan, the only one adjacent to 3 obstacles, chooses
        d.card("brogan-silver")
        assertThat(d.zoneOf("brogan", "brogan-silver")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Log Trap - an adjacent enemy hero with no card discards nothing (if able)`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-2a") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
        assertThat(d.zoneOf("brynn", "brynn-green-2a")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Deadfall Trap - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-3a") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("brynn").coins).isEqualTo(1)
    }

    @Test
    fun `Bear Trap - no enemy hero adjacent or adjacent to 3 obstacles, no Primary action`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "brynn")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Tread Lightly / Cover Tracks / Hide Traces (blue) ----------------------------------------

    @Test
    fun `Tread Lightly - swap with any adjacent unit, or an enemy hero in radius adjacent to 3 obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(3, -2)) // 2 away, 3 obstacles
            hero("brogan", Team.BLUE, Hex(-1, -2)) // 2 away, open ground
            token("token_rock", Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(3, -3))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // a friendly unit adjacent
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        val friend = minionAt(d, Hex(0, -2))
        assertThat(d.units()).containsExactly(friend, "arien")
        d.unit("arien")
        assertThat(d.at("brynn")).isEqualTo(Hex(3, -2))
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Cover Tracks - after the swap you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-2a") }
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.at("brynn")).isEqualTo(Hex(2, -2))
        d.expect(DecisionKind.CHOOSE_SPACE, "brynn")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(2, -2))).isEqualTo(1)
        d.decline()
        assertThat(d.at("brynn")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Hide Traces - after the swap move up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-3a") }
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.spaces().any { HexGrid.distance(it, Hex(2, -2)) == 2 }).isTrue()
        d.space(4, -2)
        assertThat(d.at("brynn")).isEqualTo(Hex(4, -2))
    }

    // ---- True Grit / Die Hard (green alternates) ---------------------------------------------------

    @Test
    fun `True Grit - you may retrieve a discarded attack card, then move up to 3 if an enemy hero qualifies`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) {
                played("brynn-green-2b") // radius 4
                discard("brynn-red-1", "brynn-blue-1")
            }
            hero("arien", Team.BLUE, Hex(4, -2)) // 3 obstacles: terrain (5,-2) and two rocks
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(4, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "brynn")
        assertThat(d.cards()).containsExactly("brynn-red-1") // only the attack card
        d.card("brynn-red-1")
        assertThat(d.zoneOf("brynn", "brynn-red-1")).isEqualTo(Zone.HAND)
        d.expect(DecisionKind.CHOOSE_SPACE, "brynn")
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(3)
    }

    @Test
    fun `True Grit - no qualifying enemy hero in radius, no move`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-2b") }
            hero("arien", Team.BLUE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
        assertThat(d.at("brynn")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Die Hard - move up to 4 spaces`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-3b") }
            hero("arien", Team.BLUE, Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(4, -1))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(4)
    }

    @Test
    fun `True Grit - FAQ - an immune hero never counts as adjacent to 3 obstacles (a status check targets)`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-green-2b") }
            hero("hanu", Team.RED, Hex(-5, -4))
            hero("arien", Team.BLUE, Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(4, -1))
            // Hanu's Unexpected Journey: "This turn: That hero is immune."
            effect(
                "hanu",
                "hanu-blue-1",
                Timing.THIS_TURN,
                ActionKind.SKILL,
                data = mapOf("hero" to "arien"),
                key = "hanu.immune",
            )
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
        assertThat(d.at("brynn")).isEqualTo(Hex(1, -2))
    }

    // ---- Mountain Guide / Expedition Leader (blue alternates) -------------------------------------

    @Test
    fun `Mountain Guide - may move an adjacent friendly unit, then a different one in radius if a hero qualifies`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-2b") } // radius 4
            hero("xargatha", Team.RED, Hex(0, -2)) // adjacent friend
            hero("arien", Team.BLUE, Hex(4, -2))
            token("token_rock", Hex(4, -3))
            token("token_rock", Hex(4, -1))
            minion(Team.RED, MinionType.MELEE, Hex(1, -5)) // a friendly unit 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "brynn")
        assertThat(d.units()).containsExactly("xargatha") // the minion is not adjacent
        d.unit("xargatha")
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(0, -2)) }).isEqualTo(2)
        d.space(-1, -3)
        // A different friendly unit in radius: the minion (Xargatha already moved). Mandatory once it applies.
        d.expect(DecisionKind.CHOOSE_SPACE, "brynn")
        assertThat(d.d.context.subjectId).isEqualTo(minionAt(d, Hex(1, -5)))
        d.space(2, -6)
        assertThat(d.s.state.minions.first { it.team == Team.RED }.at).isEqualTo(Hex(2, -6))
    }

    @Test
    fun `Mountain Guide - without a qualifying enemy hero only the optional first move happens`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-2b") }
            hero("xargatha", Team.RED, Hex(0, -2))
            hero("arien", Team.BLUE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.decline()
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
        assertThat(d.at("xargatha")).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Expedition Leader - up to 3 spaces`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) { played("brynn-blue-3b") }
            hero("xargatha", Team.RED, Hex(0, -2))
            hero("arien", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.unit("xargatha")
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(0, -2)) }).isEqualTo(3)
    }

    // ---- Over the Top (Ultimate) ---------------------------------------------------------------------

    @Test
    fun `Over the Top - while Brynn acts every enemy hero counts as adjacent to 3 or more obstacles`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(1, -2)) {
                played("brynn-red-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // only Brynn next to him
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(6)
    }

    @Test
    fun `Over the Top - Familiar Ground reaches any enemy hero in range, never a minion by the second bullet`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(4, -2)) {
                played("brynn-gold")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-blue-1") } // 3 away, open ground
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // 2 away: not adjacent, a minion
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        // Bullet 1 has no target (nothing adjacent): bullet 2 alone, Arien its only target.
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Decoy - a Radius item widens its radius 4`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.RED, Hex(-1, -2)) {
                played("brynn-silver")
                items("brynn-red-3a") // +1 Radius
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 5 away
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        d.option(0)
        assertThat(d.d.context.subjectId).isEqualTo(minionAt(d, Hex(4, -2)))
    }

    // ---- random games -----------------------------------------------------------------------------

    @Test
    fun `random legal games with Brynn keep every invariant and end`() {
        RandomGames.play("brynn", games = 150, seed = 4_001)
    }
}
