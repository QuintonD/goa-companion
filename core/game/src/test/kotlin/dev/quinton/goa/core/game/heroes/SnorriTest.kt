package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.SnorriHero.Rune
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Snorri the Runescribe: every card, clause by clause (text from `catalog/heroes/snorri.json`). */
class SnorriTest {
    /** Rune markers below Turn slots 1..4, in this order (as Inscribe the Runes leaves them). */
    private fun PositionBuilder.HeroBuilder.runes(vararg bySlot: Rune) = bySlot.forEachIndexed { i, r ->
        marker(r.kind, "snorri", persistent = true, data = mapOf("slot" to "${i + 1}"))
    }

    private val order = arrayOf(Rune.AXE, Rune.BIRD, Rune.ANVIL, Rune.HORN)

    /** The turn whose slot holds [rune] in [order]. */
    private fun turnOf(rune: Rune) = order.indexOf(rune) + 1

    private fun attacks(d: dev.quinton.goa.core.game.Driver) = d.s.events.filterIsInstance<GameEvent.Attacked>()

    private fun minionAt(d: dev.quinton.goa.core.game.Driver, h: Hex) = d.s.state.minions.first { it.at == h }.id

    // ---- Inscribe the Runes (silver) ---------------------------------------------------------

    @Test
    fun `Inscribe the Runes - give yourself 4 Rune markers, one below each turn slot, Snorri chooses`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("snorri", Team.BLUE, Hex(1, -2)) { played("snorri-silver") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        // Slot 1: four runes to choose from, then three, then two; the last one is forced.
        for (left in listOf(4, 3, 2)) {
            d.expect(DecisionKind.CHOOSE_OPTION, "snorri")
            assertThat(d.d.options).hasSize(left)
            // Horn below slot 1, then the first rune offered each time.
            d.answer(
                if (left ==
                    4
                ) {
                    d.d.options.first { (it as Choice.Option).label == "Horn" }
                } else {
                    d.d.options.first()
                },
            )
        }
        val runes = SnorriHero.placedRunes(
            dev.quinton.goa.core.game.engine.Game(
                ANY_HERO_ENGINE.data,
                d.s.state,
                emptyList(),
                dev.quinton.goa.core.game.engine.RunMode.PROBE,
            ),
            "snorri",
        )
        assertThat(runes.keys).containsExactly(1, 2, 3, 4)
        assertThat(runes.values.toSet()).hasSize(4)
        assertThat(runes[1]).isEqualTo(Rune.HORN)
        assertThat(d.hero("snorri").markers.all { it.persistent && it.from == "snorri" }).isTrue()
    }

    @Test
    fun `Inscribe the Runes - Rune markers are not removed at the end of round`() {
        val d = pos {
            clearMinions()
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                runes(*order)
                marker("marker_poison", from = "wasp")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.s.state.round).isEqualTo(2)
        val kinds = d.hero("snorri").markers.map { it.kind }
        assertThat(kinds).containsExactlyElementsIn(Rune.entries.map { it.kind })
    }

    @Test
    fun `Inscribe the Runes - Rune markers are not removed if you are defeated`() {
        val d = pos {
            clearMinions()
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                runes(*order)
                hand("snorri-red-1")
            }
            hero("wasp", Team.RED, Hex(2, -2)) { played("wasp-gold") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("snorri")).isNull()
        assertThat(d.hero("snorri").markers.map { it.kind }).containsExactlyElementsIn(Rune.entries.map { it.kind })
    }

    // ---- Rune Sigils (gold) --------------------------------------------------------------------

    @Test
    fun `Rune Sigils - no rune active, only a unit adjacent to you can be targeted`() {
        val d = pos {
            clearMinions()
            hero("snorri", Team.BLUE, Hex(1, -2)) { played("snorri-gold") }
            hero("wasp", Team.RED, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // in range 2, not adjacent
            phase = PositionPhase.Resolving(next = "snorri")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Rune Sigils - bird, you may target a minion in range instead (not a hero)`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.BIRD)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(3, -2)) // a hero 2 away: not a target
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // a minion 2 away: a target
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // adjacent: a target
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "snorri")
        assertThat(d.units()).containsExactly(minionAt(d, Hex(-1, -2)), minionAt(d, Hex(2, -2)))
        d.unit(minionAt(d, Hex(-1, -2)))
        assertThat(d.hero("snorri").coins).isEqualTo(2)
    }

    @Test
    fun `Rune Sigils - axe, +3 Attack`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.d.context.attackTotal).isEqualTo(5) // 2 + 3
        assertThat(d.d.context.rangedAttack).isTrue() // the card's action is Ranged (p.9)
    }

    @Test
    fun `Rune Sigils - anvil, if you target a hero gain 3 coins, not for a minion`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        assertThat(d.hero("snorri").coins).isEqualTo(3)
        assertThat(attacks(d).single().total).isEqualTo(2)
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        assertThat(e.hero("snorri").coins).isEqualTo(2) // the minion's coins only
    }

    @Test
    fun `Rune Sigils - horn, repeat once on a different hero in range`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.HORN)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            hero("xargatha", Team.RED, Hex(-1, -2)) { hand("xargatha-blue-1") } // 2 away: in range
            minion(Team.RED, MinionType.MELEE, Hex(1, -1)) // adjacent minion: not for the repeat
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CONFIRM, "snorri")
        d.yes()
        // Only a different hero: Xargatha (2 away), not the adjacent minion nor Wasp again.
        d.expect(DecisionKind.DEFEND, "xargatha")
        assertThat(attacks(d).map { it.targetId }).containsExactly("wasp", "xargatha").inOrder()
    }

    @Test
    fun `Rune Sigils - Ultimate, Rune Mastery adds a second active rune chosen for the action`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-gold")
                runes(*order)
                ultimate()
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "snorri")
        // The three inactive runes (axe is the turn's rune).
        assertThat(d.d.options.map { (it as Choice.Option).label }).containsExactly("Bird", "Anvil", "Horn").inOrder()
        d.answer(d.d.options.first { (it as Choice.Option).label == "Anvil" })
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.d.context.attackTotal).isEqualTo(5) // axe still active
        assertThat(d.hero("snorri").coins).isEqualTo(3) // anvil counts too
    }

    // ---- Rune Shards (Handicap) ----------------------------------------------------------------

    @Test
    fun `Rune Shards - target a hero adjacent, axe +3, anvil gain 2 coins`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                handicap = true
                played("snorri-handicap")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // adjacent minion: not a target
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.d.context.attackTotal).isEqualTo(1)
        assertThat(d.hero("snorri").coins).isEqualTo(2)
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                handicap = true
                played("snorri-handicap")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        assertThat(e.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(4)
    }

    @Test
    fun `Rune Shards - horn, repeat once on a different hero in range`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.HORN)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                handicap = true
                played("snorri-handicap")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            hero("xargatha", Team.RED, Hex(-1, -2)) { hand("xargatha-blue-1") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        d.yes()
        d.expect(DecisionKind.DEFEND, "xargatha")
    }

    // ---- Runic Dagger / Hammer / Battleaxe (red) ---------------------------------------------

    @Test
    fun `Runic Dagger - after the attack, if the anvil rune is active, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-1")
                discard("snorri-blue-1")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "snorri")
        d.card("snorri-blue-1")
        assertThat(d.zoneOf("snorri", "snorri-blue-1")).isEqualTo(Zone.HAND)
        // Another rune: no retrieve.
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-1")
                discard("snorri-blue-1")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        assertThat(e.zoneOf("snorri", "snorri-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(e.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    @Test
    fun `Runic Hammer - before the attack, if the horn rune is active, you may move 1 space`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.HORN)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-2a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // 2 away
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "snorri")
        assertThat(d.d.options).contains(Choice.Decline("Don't move"))
        d.space(2, -2)
        assertThat(d.hero("snorri").coins).isEqualTo(2) // then the adjacent minion is attacked
        // Without the horn rune there is no move, so no target and no Primary action.
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-2a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Runic Battleaxe - axe, may repeat once on an enemy minion`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-3a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            hero("xargatha", Team.RED, Hex(0, -2)) { hand("xargatha-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.unit("wasp")
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CONFIRM, "snorri")
        d.yes()
        // The repeat targets an enemy minion only: Xargatha is not offered, the minion is attacked.
        assertThat(attacks(d)).hasSize(2)
        assertThat(d.s.state.minions.none { it.at == Hex(1, -1) }).isTrue()
        assertThat(d.at("xargatha")).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Runic Battleaxe - anvil, you may retrieve a discarded card, horn, may move 1 space first`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-3a")
                discard("snorri-blue-1")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "snorri")
        d.card("snorri-blue-1")
        assertThat(d.zoneOf("snorri", "snorri-blue-1")).isEqualTo(Zone.HAND)
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.HORN)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-3a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        e.expect(DecisionKind.CHOOSE_SPACE, "snorri")
    }

    // ---- Runecaster / Runeblaster (red) -------------------------------------------------------

    @Test
    fun `Runecaster - target a unit at maximum range only`() {
        val d = pos {
            clearMinions()
            hero("snorri", Team.BLUE, Hex(1, -2)) { played("snorri-red-2b") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -2)) // 3 away: maximum range
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // 2 away: not
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // adjacent: not
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        // A single valid target: the engine chooses it, the minion at maximum range is defeated.
        assertThat(attacks(d)).hasSize(1)
        assertThat(d.s.state.minions.none { it.at == Hex(4, -2) }).isTrue()
        assertThat(d.s.state.minions.count { it.at == Hex(3, -2) || it.at == Hex(2, -2) }).isEqualTo(2)
    }

    @Test
    fun `Runecaster - horn, after the attack move up to 2 spaces`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.HORN)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-2b")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "snorri")
        assertThat(d.spaces()).contains(Hex(3, -2))
        assertThat(d.spaces().all { dev.quinton.goa.core.domain.board.HexGrid.distance(it, Hex(1, -2)) <= 2 }).isTrue()
    }

    @Test
    fun `Runecaster - axe, an enemy hero who was adjacent to the target discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-2b")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(4, -3)) { hand() } // adjacent to the target, no cards
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.unit(minionAt(d, Hex(4, -2))) // Wasp is at maximum range too
        assertThat(d.at("wasp")).isNull() // no card to discard: defeated
        assertThat(d.hero("snorri").coins).isEqualTo(2 + 1) // minion + level 1 hero
    }

    @Test
    fun `Runeblaster - bird, target a unit in range, otherwise at maximum range`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.BIRD)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-3b")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        assertThat(d.units()).hasSize(2)
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-red-3b")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        assertThat(e.s.state.minions.none { it.at == Hex(4, -2) }).isTrue() // the one at maximum range
    }

    // ---- Oaths (green, Defense, Block) ---------------------------------------------------------

    private fun oath(card: String, rune: Rune?, attackCard: String, ultimate: Boolean = false, at: Hex = Hex(2, -2)) =
        pos {
            clearMinions()
            turn = rune?.let { turnOf(it) } ?: 1
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                hand(card)
                if (rune != null) runes(*order)
                if (ultimate) ultimate()
            }
            hero("wasp", Team.RED, at) { played(attackCard) }
            phase = PositionPhase.Resolving(next = "wasp")
        }

    private fun blocked(d: dev.quinton.goa.core.game.Driver) =
        d.s.events.filterIsInstance<GameEvent.Defended>().last().success

    @Test
    fun `Oath of Endurance - horn blocks a basic attack, not a non-basic one`() {
        val d = oath("snorri-green-1", Rune.HORN, "wasp-gold")
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-1")
        assertThat(blocked(d)).isTrue()
        val e = oath("snorri-green-1", Rune.HORN, "wasp-red-1")
        e.action("wasp", ActionType.PRIMARY)
        e.defend("snorri-green-1")
        assertThat(blocked(e)).isFalse()
        assertThat(e.at("snorri")).isNull()
    }

    @Test
    fun `Oath of Endurance - axe blocks a non-ranged attack, no rune active the block fails`() {
        val d = oath("snorri-green-1", Rune.AXE, "wasp-red-1")
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-1")
        assertThat(blocked(d)).isTrue()
        val e = oath("snorri-green-1", null, "wasp-gold")
        e.action("wasp", ActionType.PRIMARY)
        e.defend("snorri-green-1")
        assertThat(blocked(e)).isFalse()
    }

    @Test
    fun `Oath of Endurance - This Turn, you are immune to enemy actions`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.AXE)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                hand("snorri-green-1")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { played("wasp-gold") }
            hero("xargatha", Team.RED, Hex(0, -2)) { played("xargatha-gold") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-1")
        assertThat(blocked(d)).isTrue()
        // Xargatha acts next: Snorri, adjacent, can't be targeted.
        d.expect(DecisionKind.CHOOSE_ACTION, "xargatha")
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Oath of Fortitude - bird blocks a ranged attack`() {
        val d = oath("snorri-green-2a", Rune.BIRD, "wasp-red-2b", at = Hex(3, -3))
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-2a")
        assertThat(blocked(d)).isTrue()
        val e = oath("snorri-green-2a", Rune.HORN, "wasp-red-2b", at = Hex(3, -3))
        e.action("wasp", ActionType.PRIMARY)
        e.defend("snorri-green-2a")
        assertThat(blocked(e)).isFalse()
    }

    @Test
    fun `Oath of Perseverance - anvil blocks a non-basic attack`() {
        val d = oath("snorri-green-3a", Rune.ANVIL, "wasp-red-1")
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-3a")
        assertThat(blocked(d)).isTrue()
        val e = oath("snorri-green-3a", Rune.ANVIL, "wasp-gold")
        e.action("wasp", ActionType.PRIMARY)
        e.defend("snorri-green-3a")
        assertThat(blocked(e)).isFalse()
    }

    @Test
    fun `Oath of Perseverance - FAQ, choose one active rune when Rune Mastery gives two`() {
        val d = oath("snorri-green-3a", Rune.ANVIL, "wasp-red-1", ultimate = true)
        d.action("wasp", ActionType.PRIMARY)
        d.defend("snorri-green-3a")
        // Rune Mastery first (a Defense action is an action), then "Choose one active rune".
        d.expect(DecisionKind.CHOOSE_OPTION, "snorri")
        d.answer(d.d.options.first { (it as Choice.Option).label == "Horn" })
        d.expect(DecisionKind.CHOOSE_OPTION, "snorri")
        assertThat(d.d.options.map { (it as Choice.Option).label }).containsExactly("Anvil", "Horn")
        d.answer(d.d.options.first { (it as Choice.Option).label == "Horn" }) // horn: basic only
        assertThat(blocked(d)).isFalse()
    }

    // ---- Runetrap / Runebomb (green) ----------------------------------------------------------

    private fun trap(card: String, rune: Rune, ultimate: Boolean = false) = pos {
        clearMinions()
        turn = turnOf(rune)
        hero("snorri", Team.BLUE, Hex(1, -2)) {
            played(card)
            runes(*order)
            if (ultimate) ultimate()
        }
        hero("wasp", Team.RED, Hex(3, -2)) {
            played("wasp-red-1")
            hand("wasp-gold", "wasp-silver", "wasp-green-1", "wasp-blue-1")
        }
        phase = PositionPhase.Resolving(next = "snorri")
    }

    @Test
    fun `Runetrap - an enemy hero in radius discards a card of the active rune's colour`() {
        for ((rune, card) in listOf(
            Rune.HORN to "wasp-green-1",
            Rune.AXE to "wasp-silver",
            Rune.ANVIL to "wasp-blue-1",
        )) {
            val d = trap("snorri-green-2b", rune)
            d.action("snorri", ActionType.PRIMARY)
            com.google.common.truth.Truth.assertWithMessage(
                d.log(),
            ).that(d.zoneOf("wasp", card)).isEqualTo(Zone.DISCARD)
            assertThat(d.hero("wasp").cards.count { it.zone == Zone.DISCARD }).isEqualTo(1)
        }
    }

    @Test
    fun `Runetrap - the bird rune has no line, so no Primary action`() {
        val d = trap("snorri-green-2b", Rune.BIRD)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Runebomb - bird, a gold card`() {
        val d = trap("snorri-green-3b", Rune.BIRD)
        d.action("snorri", ActionType.PRIMARY)
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Runebomb - FAQ, choose one active rune, only with Rune Mastery`() {
        val d = trap("snorri-green-3b", Rune.BIRD, ultimate = true)
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "snorri") // Rune Mastery
        d.answer(d.d.options.first { (it as Choice.Option).label == "Anvil" })
        d.expect(DecisionKind.CHOOSE_OPTION, "snorri") // "Choose one active rune"
        assertThat(d.d.options.map { (it as Choice.Option).label }).containsExactly("Bird", "Anvil")
        d.answer(d.d.options.first { (it as Choice.Option).label == "Anvil" })
        assertThat(d.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.HAND)
    }

    // ---- Passages (blue, Movement) ------------------------------------------------------------

    private fun surrounded(card: String, rune: Rune) = pos {
        clearMinions()
        turn = turnOf(rune)
        hero("snorri", Team.BLUE, Hex(1, -2)) {
            played(card)
            runes(*order)
        }
        hero("wasp", Team.RED, Hex(-5, -4))
        for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) {
            minion(Team.RED, MinionType.MELEE, h)
        }
        phase = PositionPhase.Resolving(next = "snorri")
    }

    @Test
    fun `Safe Passage - bird, This turn you may ignore obstacles`() {
        val d = surrounded("snorri-blue-1", Rune.BIRD)
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "snorri")
        assertThat(d.spaces()).containsAtLeast(Hex(3, -2), Hex(-1, -2))
        val e = surrounded("snorri-blue-1", Rune.AXE)
        e.action("snorri", ActionType.PRIMARY)
        assertThat(e.at("snorri")).isEqualTo(Hex(1, -2)) // boxed in: no move
    }

    @Test
    fun `Hidden Passage - bird ignores obstacles, anvil immune to enemy actions this turn`() {
        val d = surrounded("snorri-blue-2a", Rune.BIRD)
        d.action("snorri", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(3, -2))
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-2a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { played("wasp-gold") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        e.decline() // stay next to Wasp
        e.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Deep Passage - horn, gain +2 Movement`() {
        fun reach(rune: Rune): Set<Hex> {
            val d = pos {
                clearMinions()
                turn = turnOf(rune)
                hero("snorri", Team.BLUE, Hex(-1, -2)) {
                    played("snorri-blue-3a")
                    runes(*order)
                }
                hero("wasp", Team.RED, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "snorri")
            }
            d.action("snorri", ActionType.PRIMARY)
            return d.spaces()
        }
        assertThat(reach(Rune.HORN)).contains(Hex(4, -2)) // 5 spaces
        assertThat(reach(Rune.AXE)).doesNotContain(Hex(4, -2))
    }

    @Test
    fun `Deep Passage - anvil, immune to enemy actions this turn`() {
        val e = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-3a")
                runes(*order)
            }
            hero("wasp", Team.RED, Hex(2, -2)) { played("wasp-gold") }
            phase = PositionPhase.Resolving(next = "snorri")
        }
        e.action("snorri", ActionType.PRIMARY)
        e.decline()
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Ancestral Boon / Grace (blue) ---------------------------------------------------------

    @Test
    fun `Ancestral Boon - axe, a friendly hero in radius may swap a resolved card with a card in hand`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-2b")
                runes(Rune.BIRD, Rune.AXE, Rune.ANVIL, Rune.HORN)
            }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                resolved(1, "arien-red-1")
                hand("arien-gold")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien") // the friend decides
        d.card("arien-red-1") // the only card in hand is swapped in
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Ancestral Boon - anvil, a friendly hero may retrieve all their discarded cards`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-2b")
                runes(*order)
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold") } // the other basic cards are discarded
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "arien")
        d.yes()
        assertThat(d.hero("arien").cards.count { it.zone == Zone.DISCARD }).isEqualTo(0)
        assertThat(d.hero("arien").cards.count { it.zone == Zone.HAND }).isEqualTo(5)
    }

    @Test
    fun `Ancestral Boon - no friendly hero in radius, no Primary action`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.ANVIL)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-2b")
                runes(*order)
            }
            hero("arien", Team.BLUE, Hex(-4, -2))
            hero("wasp", Team.RED, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Ancestral Grace - bird, swap one of their items with their card of the same tier and color`() {
        val d = pos {
            clearMinions()
            turn = turnOf(Rune.BIRD)
            hero("snorri", Team.BLUE, Hex(1, -2)) {
                played("snorri-blue-3b")
                runes(*order)
            }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                level = 2
                hand("arien-red-2a", "arien-gold")
                items("arien-red-2b")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "snorri")
        }
        d.action("snorri", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-red-2b")
        assertThat(d.zoneOf("arien", "arien-red-2b")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("arien", "arien-red-2a")).isEqualTo(Zone.ITEM)
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with Snorri run without errors`() {
        Batch7Games.run("snorri", games = 150)
    }
}

/** Seeded random-legal games seating one hero against the reference heroes, with invariant checks. */
internal object Batch7Games {
    private val reference = listOf("arien", "brogan", "wasp", "xargatha")

    fun run(hero: String, games: Int, seed: Long = 7) {
        val rng = kotlin.random.Random(seed)
        repeat(games) { i ->
            val others = reference.shuffled(rng)
            val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
            val heroTeam = if (rng.nextBoolean()) Team.RED else Team.BLUE
            fun seat(
                id: String,
                team: Team,
            ) = dev.quinton.goa.core.game.SeatSetup(id, team, dev.quinton.goa.core.game.Controller.Human)
            val seats = when (i % 3) {
                0 -> listOf(seat(hero, heroTeam), seat(others[0], heroTeam.opponent))

                1 -> listOf(
                    seat(hero, heroTeam),
                    seat(others[0], heroTeam),
                    seat(others[1], heroTeam.opponent),
                    seat(others[2], heroTeam.opponent),
                )

                else -> listOf(
                    seat(others[0], heroTeam.opponent),
                    seat(hero, heroTeam),
                    seat(others[1], heroTeam.opponent),
                    seat(others[2], heroTeam),
                )
            }.sortedBy { if (it.team == Team.RED) 0 else 1 }
            val map = if (i % 5 ==
                4
            ) {
                dev.quinton.goa.core.game.TestData.RIVER
            } else {
                dev.quinton.goa.core.game.TestData.FOREST
            }
            val setup = dev.quinton.goa.core.game.GameSetup(map, seats, tieBreaker = coin)
            val s = ANY_HERO_ENGINE.newGame(setup) as dev.quinton.goa.core.game.engine.EngineSession
            val player = dev.quinton.goa.core.game.RandomPlayer(seed * 1000 + i)
            var n = 0
            while (true) {
                val d = s.pending ?: break
                check(d.options.isNotEmpty()) { "No options: $d" }
                check(d.options.distinct().size == d.options.size) { "Duplicate options: $d" }
                val g = dev.quinton.goa.core.game.engine.Game(
                    ANY_HERO_ENGINE.data,
                    s.state,
                    emptyList(),
                    dev.quinton.goa.core.game.engine.RunMode.PROBE,
                )
                g.occupancy()
                for (h in s.state.heroes) {
                    h.at?.let { check(g.geo.isSpace(it)) { "${h.id} off the map at $it" } }
                    check(h.coins >= 0)
                    check(h.cards.count { it.zone == Zone.PLAYED } <= 1)
                }
                val r = s.answer(d.id, player.choose(d))
                check(r is dev.quinton.goa.core.game.AnswerResult.Accepted) { "Rejected $r for $d" }
                n++
                check(n < 30_000) { "Game $i did not end: $setup" }
            }
            check(s.state.winner != null) { "Game $i ended without a winner" }
        }
    }
}
