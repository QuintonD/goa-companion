package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.heroes.brynn.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Trinkets the Scavenger: every card, clause by clause (text from `catalog/heroes/trinkets.json`), with the FAQ. */
class TrinketsTest {
    private fun PositionBuilder.turret(at: Hex) = piece(
        PieceState(
            "turret",
            TrinketsHero.TURRET,
            "Turret",
            at,
            owner = "trinkets",
            token = false,
            removeAtEndOfRound = false,
        ),
    )

    private fun turrets(d: Driver) = d.s.state.pieces.filter { it.kind == TrinketsHero.TURRET && it.at != null }

    private fun minionAt(d: Driver, h: Hex): String = d.s.state.minions.first { it.at == h }.id

    private fun probe(d: Driver) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    // ---- Rapid Redeployment (gold) / Ramshackle Turret (Handicap); the Turret ---------------------

    @Test
    fun `Rapid Redeployment - move up to 3 spaces and place the Turret into a space adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-gold") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY) // no adjacent minion: only the first bullet
        d.expect(DecisionKind.CHOOSE_SPACE, "trinkets")
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(3)
        d.space(3, -2)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -2))).isEqualTo(1)
        d.space(4, -2)
        val t = turrets(d).single()
        assertThat(t.at).isEqualTo(Hex(4, -2))
        // FAQ: an obstacle that counts as terrain; not a unit and not a token.
        assertThat(t.token).isFalse()
        assertThat(t.unit).isFalse()
        assertThat(probe(d).isTerrain(Hex(4, -2))).isTrue()
    }

    @Test
    fun `Rapid Redeployment - FAQ - a single Turret - placing it again repositions it`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-gold") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            turret(Hex(4, -5))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.decline() // don't move
        d.space(2, -2)
        assertThat(turrets(d).map { it.at }).containsExactly(Hex(2, -2))
    }

    @Test
    fun `Rapid Redeployment - or defeat a minion adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-gold") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "trinkets")
        d.option(1)
        assertThat(d.hero("trinkets").coins).isEqualTo(2)
        assertThat(turrets(d)).isEmpty()
    }

    @Test
    fun `Ramshackle Turret - only the Turret, even next to a minion`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-handicap") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "trinkets") // the move: no "Choose one"
        d.decline()
        d.space(0, -2)
        assertThat(turrets(d).single().at).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `The Turret - not a token - stays at the end of round (tokens go) and cannot be cleared`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            turret(Hex(2, -2))
            token("token_rock", Hex(0, -2))
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(d.s.state.pieces.map { it.kind }).containsExactly(TrinketsHero.TURRET)
        assertThat(turrets(d).single().at).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `The Turret - Clear does not remove it`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-red-1") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            turret(Hex(2, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        assertThat(d.actions()).doesNotContain(ActionType.CLEAR)
    }

    @Test
    fun `The Turret - FAQ - it stays when Trinkets is defeated`() {
        val d = pos {
            clearMinions()
            hero("brynn", Team.BLUE, Hex(0, -2)) { played("brynn-green-3a") } // Deadfall Trap
            hero("trinkets", Team.RED, Hex(1, -2)) { hand() }
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "brynn")
        }
        d.action("brynn", ActionType.PRIMARY)
        assertThat(d.at("trinkets")).isNull()
        assertThat(turrets(d).single().at).isEqualTo(Hex(3, -2))
    }

    // ---- Salvage Parts (silver) ---------------------------------------------------------------------

    private fun salvage(withTurret: Boolean) = pos {
        clearMinions()
        hero("trinkets", Team.RED, Hex(1, -2)) {
            played("trinkets-silver")
            discard("trinkets-red-1")
        }
        hero("arien", Team.BLUE, Hex(-5, -4))
        if (withTurret) turret(Hex(4, -5))
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Salvage Parts - Choose one - place the Turret next to you`() {
        val d = salvage(withTurret = true)
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.CHOOSE_OPTION, "trinkets").options).hasSize(3)
        d.option(0)
        d.space(2, -2)
        assertThat(turrets(d).single().at).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Salvage Parts - remove the Turret, move up to 3 spaces`() {
        val d = salvage(withTurret = true)
        d.action("trinkets", ActionType.PRIMARY)
        d.option(1)
        assertThat(turrets(d)).isEmpty()
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(3)
    }

    @Test
    fun `Salvage Parts - remove the Turret, you may retrieve a discarded card`() {
        val d = salvage(withTurret = true)
        d.action("trinkets", ActionType.PRIMARY)
        d.option(2)
        assertThat(turrets(d)).isEmpty()
        d.expect(DecisionKind.CHOOSE_CARD, "trinkets")
        d.card("trinkets-red-1")
        assertThat(d.zoneOf("trinkets", "trinkets-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Salvage Parts - without a Turret only the placing bullet is possible`() {
        val d = salvage(withTurret = false)
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "trinkets")
        d.space(2, -2)
        assertThat(turrets(d).single().at).isEqualTo(Hex(2, -2))
    }

    // ---- Makeshift Minigun / Gatling Gun / Supercharged Cannon (red) --------------------------------

    private fun cannon(card: String, turretAt: Hex, ultimate: Boolean = false) = pos {
        clearMinions()
        hero("trinkets", Team.RED, Hex(1, -2)) {
            played(card)
            if (ultimate) ultimate()
        }
        hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 2 east, in a straight line
        hero("brogan", Team.BLUE, Hex(-1, -2)) { hand("brogan-blue-1") } // 2 west, far from the Turret
        turret(turretAt)
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Makeshift Minigun - a unit in range of both you and the Turret, +2 if in a line from both`() {
        val d = cannon("trinkets-red-1", turretAt = Hex(3, -3)) // Arien is next to the Turret
        d.action("trinkets", ActionType.PRIMARY)
        // Brogan is in Trinkets' range but 4 from the Turret: Arien is the only target.
        val p = d.expect(DecisionKind.DEFEND, "arien")
        assertThat(p.context.attackTotal).isEqualTo(5)
        assertThat(p.context.rangedAttack).isTrue()
    }

    @Test
    fun `Makeshift Minigun - not in a straight line from the Turret, no bonus`() {
        val d = cannon("trinkets-red-1", turretAt = Hex(4, -4)) // 2 from Arien, not in a line
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(3)
    }

    @Test
    fun `Makeshift Minigun - without the Turret there is no target`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Gatling Gun - Range 3`() {
        val d = cannon("trinkets-red-2a", turretAt = Hex(3, -5)) // 3 from Arien, in a line; 4 from Brogan
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(5)
    }

    @Test
    fun `Supercharged Cannon - +3 Attack in a line from both`() {
        val d = cannon("trinkets-red-3a", turretAt = Hex(3, -3))
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
    }

    @Test
    fun `Unlimited Firepower - +1 Range for her cards, from both her and the Turret`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(0, -2)) {
                played("trinkets-red-1") // Range 2 + 1
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 3 away
            turret(Hex(3, -5)) // 3 from Arien
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    // ---- Steam Discharge / Flame Belcher (red alternates) ---------------------------------------------

    private fun discharge(card: String) = pos {
        clearMinions()
        hero("trinkets", Team.RED, Hex(1, -2)) { played(card) } // Range 4
        hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-blue-1") } // next to the Turret
        hero("brogan", Team.BLUE, Hex(0, -2)) { hand("brogan-blue-1") } // in range, not next to the Turret
        turret(Hex(3, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // next to the Turret
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1)) // next to the Turret
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Steam Discharge - a unit in range adjacent to the Turret, may repeat once on a different enemy unit`() {
        val d = discharge("trinkets-red-2b")
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", minionAt(d, Hex(4, -2)), minionAt(d, Hex(2, -1)))
        d.unit(minionAt(d, Hex(4, -2)))
        d.expect(DecisionKind.CONFIRM, "trinkets")
        d.yes()
        assertThat(d.units()).containsExactly("arien", minionAt(d, Hex(2, -1)))
        d.unit(minionAt(d, Hex(2, -1)))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM) // once only
        assertThat(d.hero("trinkets").coins).isEqualTo(4)
    }

    @Test
    fun `Flame Belcher - may repeat up to two times on different enemy units`() {
        val d = discharge("trinkets-red-3b")
        d.action("trinkets", ActionType.PRIMARY)
        d.unit(minionAt(d, Hex(4, -2)))
        d.yes()
        d.unit(minionAt(d, Hex(2, -1)))
        d.expect(DecisionKind.CONFIRM, "trinkets")
        d.yes()
        d.expect(DecisionKind.DEFEND, "arien") // the last one left
    }

    @Test
    fun `Steam Discharge - a Range item adds to Range 4`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(-2, -2)) {
                played("trinkets-red-2b")
                items("trinkets-green-3b") // +1 Range
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 5 away, next to the Turret
            turret(Hex(3, -3))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    // ---- Early Prototype / Updated Design / Perfected Design (green) ----------------------------------

    private fun design(card: String, trinketsAt: Hex = Hex(1, -2)) = pos {
        clearMinions()
        hero("trinkets", Team.RED, trinketsAt) { played(card) } // radius 2
        hero("arien", Team.BLUE, Hex(4, -2)) // 1 from the Turret
        turret(Hex(3, -2)) // 2 from (1,-2)
        token("token_rock", Hex(3, -4)) // 2 from the Turret
        minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // 4 from the Turret: out of its radius
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Early Prototype - in radius of the Turret, swap with a unit or token in its radius, then remove it`() {
        val d = design("trinkets-green-1")
        d.action("trinkets", ActionType.PRIMARY)
        val rock = d.s.state.pieces.first { it.kind == "token_rock" }.id
        assertThat(d.units()).containsExactly("arien", rock)
        d.token(rock)
        assertThat(d.at("trinkets")).isEqualTo(Hex(3, -4))
        assertThat(d.at(rock)).isEqualTo(Hex(1, -2))
        assertThat(turrets(d)).isEmpty()
    }

    @Test
    fun `Early Prototype - not in radius of the Turret, nothing happens`() {
        val d = design("trinkets-green-1", trinketsAt = Hex(-1, -5))
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.at("trinkets")).isEqualTo(Hex(-1, -5))
        assertThat(turrets(d)).hasSize(1)
        assertThat(d.zoneOf("trinkets", "trinkets-green-1")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Updated Design - the swap, and the Turret stays`() {
        val d = design("trinkets-green-2a")
        d.action("trinkets", ActionType.PRIMARY)
        d.unit("arien")
        assertThat(d.at("trinkets")).isEqualTo(Hex(4, -2))
        assertThat(turrets(d)).hasSize(1)
    }

    @Test
    fun `Perfected Design - Choose one - or place yourself into a space in radius of the Turret`() {
        val d = design("trinkets-green-3a")
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "trinkets")
        d.option(1)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -2))).isIn(1..2)
        d.space(3, -1)
        assertThat(d.at("trinkets")).isEqualTo(Hex(3, -1))
    }

    // ---- Disruptor Jolt / Disruptor Pulse / Disruptor Grid (blue) -------------------------------------

    private fun disruptor(card: String, arienHand: List<String> = listOf("arien-gold", "arien-silver")) = pos {
        clearMinions()
        hero("trinkets", Team.RED, Hex(1, -2)) { played(card) } // 10 or 11
        hero("arien", Team.BLUE, Hex(4, -2)) {
            // next to the Turret
            played("arien-red-1") // 8
            hand(*arienHand.toTypedArray())
        }
        hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") } // 8, next to the Turret
        turret(Hex(3, -2))
        minion(Team.RED, MinionType.MELEE, Hex(4, -3)) // next to Arien and Brogan
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        tieBreaker = Team.BLUE
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Disruptor Jolt - before an enemy hero in radius of the Turret performs a primary action, it discards`() {
        val d = disruptor("trinkets-blue-1")
        d.action("trinkets", ActionType.PRIMARY)
        d.expect(DecisionKind.ACT_FIRST, "arien") // 8 and 8, Blue's choice
        d.unit("arien")
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        // The effect is deactivated: Brogan's primary action costs him nothing.
        assertThat(d.s.state.effects.none { it.owner == "trinkets" }).isTrue()
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    @Test
    fun `Disruptor Jolt - a secondary action is not a primary action`() {
        val d = disruptor("trinkets-blue-1")
        d.action("trinkets", ActionType.PRIMARY)
        d.unit("arien")
        d.action("arien", ActionType.MOVEMENT)
        d.expect(DecisionKind.CHOOSE_SPACE, "arien")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Disruptor Jolt - an enemy hero out of the Turret's radius is not affected, nor a friend`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-1") } // radius 3
            hero("arien", Team.BLUE, Hex(-2, -2)) {
                // 5 from the Turret
                played("arien-blue-2b")
                hand("arien-gold")
            }
            hero("xargatha", Team.RED, Hex(4, -2)) {
                // friendly, next to the Turret
                played("xargatha-blue-1")
                hand("xargatha-gold")
            }
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY) // Slippery Ground (10): no discard
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Disruptor Jolt - an immune enemy hero is not affected by the effect`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-1") }
            hero("hanu", Team.RED, Hex(-5, -4))
            hero("arien", Team.BLUE, Hex(4, -2)) {
                played("arien-blue-2b")
                hand("arien-gold")
            }
            turret(Hex(3, -2))
            // Hanu's Unexpected Journey: "This turn: That hero is immune."
            effect(
                "hanu",
                "hanu-blue-1",
                dev.quinton.goa.core.game.engine.Timing.THIS_TURN,
                dev.quinton.goa.core.game.engine.ActionKind.SKILL,
                data = mapOf("hero" to "arien"),
                key = "hanu.immune",
            )
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Disruptor Pulse - radius 4`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-2a") }
            hero("arien", Team.BLUE, Hex(-1, -2)) {
                // 4 from the Turret
                played("arien-blue-2b")
                hand("arien-gold", "arien-silver")
            }
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Disruptor Grid - discards a card, or is defeated (and the effect stays active)`() {
        val d = disruptor("trinkets-blue-3a", arienHand = emptyList())
        d.action("trinkets", ActionType.PRIMARY)
        d.unit("arien")
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        // Arien discarded nothing: the effect is still active for Brogan.
        d.action("brogan", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
    }

    // ---- Self-Destruct / Emergency Protocol (green alternates) -----------------------------------------

    private fun selfDestruct(card: String) = pos {
        clearMinions()
        hero("trinkets", Team.RED, Hex(-1, -2)) { played(card) } // radius 3
        hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-silver") }
        hero("brogan", Team.BLUE, Hex(2, -3)) { hand() }
        hero("wasp", Team.BLUE, Hex(-3, -1)) { hand("wasp-gold") } // far from the Turret
        turret(Hex(3, -2))
        phase = PositionPhase.Resolving(next = "trinkets")
    }

    @Test
    fun `Self-Destruct - up to two enemy heroes in radius of the Turret discard, if able, then remove the Turret`() {
        val d = selfDestruct("trinkets-green-2b")
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", "brogan")
        d.unit("arien")
        d.unit("brogan")
        d.card("arien-gold")
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -3)) // no card: nothing (if able)
        assertThat(turrets(d)).isEmpty()
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Emergency Protocol - discard a card, or are defeated`() {
        val d = selfDestruct("trinkets-green-3b")
        d.action("trinkets", ActionType.PRIMARY)
        d.unit("brogan")
        d.decline() // only one
        assertThat(d.at("brogan")).isNull()
        assertThat(turrets(d)).isEmpty()
    }

    @Test
    fun `Self-Destruct - without the Turret there is no Primary action`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(-1, -2)) { played("trinkets-green-2b") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Deployable Barrier / Deployable Bastion (blue alternates) -------------------------------------

    @Test
    fun `Deployable Barrier - up to 2 Barriers in radius, one next to the Turret, +1 Defense per adjacent Barrier`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) {
                played("trinkets-blue-2b") // 10, radius 3
                hand("trinkets-red-1") // Defense 5
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { hand("xargatha-red-1") } // a friendly hero
            hero("arien", Team.BLUE, Hex(1, -1)) {
                played("arien-red-1") // 8: attacks Trinkets next
                hand("arien-gold")
            }
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        // The first Barrier must be next to the Turret (and in radius of Trinkets).
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(h, Hex(3, -2))).isEqualTo(1)
            assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(1..3)
        }
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue() // "up to"
        d.space(2, -2)
        // The second anywhere in radius.
        assertThat(d.spaces()).contains(Hex(0, -2))
        d.space(0, -2)
        assertThat(d.s.state.pieces.count { it.kind == TrinketsHero.BARRIER }).isEqualTo(2)
        // Arien attacks Trinkets, next to both Barriers: Defense 5 + 2.
        d.action("arien", ActionType.PRIMARY)
        val p = d.expect(DecisionKind.DEFEND, "trinkets")
        assertThat(p.context.defensePreview["trinkets-red-1"]).isEqualTo(7)
    }

    @Test
    fun `Deployable Barrier - friendly heroes next to a Barrier gain the Defense too, enemies do not`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(4, -5)) { played("trinkets-blue-2b") }
            hero("xargatha", Team.RED, Hex(1, -2)) { hand("xargatha-red-1") } // Defense 6
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played("arien-red-1")
                hand("arien-blue-1") // Defense 5
            }
            turret(Hex(3, -3))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.space(2, -3) // next to the Turret, next to Xargatha and Arien
        d.decline()
        d.action("arien", ActionType.PRIMARY) // Dangerous Current on Xargatha
        assertThat(d.expect(DecisionKind.DEFEND, "xargatha").context.defensePreview["xargatha-red-1"]).isEqualTo(7)
    }

    @Test
    fun `Deployable Barrier - without the Turret no Barrier can be placed`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        assertThat(d.s.state.pieces).isEmpty()
    }

    @Test
    fun `Deployable Bastion - up to 3 Barriers`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-3b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.space(2, -2)
        d.space(0, -2)
        d.space(1, -1)
        assertThat(d.s.state.pieces.count { it.kind == TrinketsHero.BARRIER }).isEqualTo(3)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
    }

    // ---- Unlimited Firepower (Ultimate): Radius ------------------------------------------------------

    @Test
    fun `Unlimited Firepower - +1 Radius (Disruptor Jolt reaches 4 from the Turret)`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) {
                played("trinkets-blue-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(-1, -2)) {
                // 4 from the Turret
                played("arien-blue-2b")
                hand("arien-gold", "arien-silver")
            }
            turret(Hex(3, -2))
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        d.action("trinkets", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    // ---- FAQ: who targets ----------------------------------------------------------------------------

    @Test
    fun `FAQ - Trinkets is the one acting - Spell Break near her stops her skills even with the Turret far away`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-blue-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) // Spell Break in force: enemy heroes in radius 3
            turret(Hex(4, -6)) // far from Arien
            effect(
                "arien",
                "arien-silver",
                dev.quinton.goa.core.game.engine.Timing.THIS_TURN,
                dev.quinton.goa.core.game.engine.ActionKind.SKILL,
            )
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `FAQ - Spell Break does not stop the gold card`() {
        val d = pos {
            clearMinions()
            hero("trinkets", Team.RED, Hex(1, -2)) { played("trinkets-gold") }
            hero("arien", Team.BLUE, Hex(2, -2))
            effect(
                "arien",
                "arien-silver",
                dev.quinton.goa.core.game.engine.Timing.THIS_TURN,
                dev.quinton.goa.core.game.engine.ActionKind.SKILL,
            )
            phase = PositionPhase.Resolving(next = "trinkets")
        }
        assertThat(d.actions()).contains(ActionType.PRIMARY)
    }

    // ---- random games -----------------------------------------------------------------------------

    @Test
    fun `random legal games with Trinkets keep every invariant and end`() {
        RandomGames.play("trinkets", games = 150, seed = 4_004)
    }
}
