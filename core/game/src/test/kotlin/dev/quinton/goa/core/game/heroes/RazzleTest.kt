package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch6.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Razzle the Ringmaster: every card, clause by clause, with her FAQ (text from `catalog/heroes/razzle.json`). */
class RazzleTest {
    /** Another one of Razzle on the board. */
    private fun PositionBuilder.double(id: String, at: Hex, team: Team = Team.RED) = piece(
        PieceState(
            id, RazzleHero.DOUBLE, "Razzle", at, owner = "razzle", team = team, token = false, unit = true,
            removeAtEndOfRound = false, heroBody = "razzle",
        ),
    )

    private fun doubles(d: Driver) = d.s.state.pieces.filter { it.heroBody == "razzle" && it.at != null }

    private fun minionAt(d: Driver, h: Hex) = d.s.state.minions.single { it.at == h }.id

    /** Answer the "which one of you performs the action?" question with the main figure. */
    private fun Driver.actWithMain() = apply {
        expect(DecisionKind.CHOOSE_UNIT, "razzle")
        unit("razzle")
    }

    // ---- Stunt Doubles (gold) / Stunted Doubles (Handicap) -------------------------------------

    @Test
    fun `Stunt Doubles - After the attack, spawn up to 3 more of you in radius`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-gold") } // radius 1
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.hero("razzle").coins).isEqualTo(2)
        d.expect(DecisionKind.CHOOSE_SPACE, "razzle")
        assertThat(d.d.options).contains(Choice.Decline()) // "up to"
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isEqualTo(1)
        d.space(0, -2)
        d.space(1, -1)
        d.space(2, -2)
        // Three more: the supply is empty, no further question.
        assertThat(doubles(d).map { it.at }).containsExactly(Hex(0, -2), Hex(1, -1), Hex(2, -2))
        val g = dev.quinton.goa.core.game.engine.Game(
            ANY_ENGINE.data,
            d.s.state,
            emptyList(),
            dev.quinton.goa.core.game.engine.RunMode.PROBE,
        )
        for (p in doubles(d)) {
            assertThat(g.isHero(p.id)).isTrue()
            assertThat(g.teamOf(p.id)).isEqualTo(Team.RED)
        }
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Stunt Doubles - only figures left in the supply can be spawned (p19 Spawn)`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-gold") }
            double("rzA", Hex(-1, -5))
            double("rzB", Hex(4, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.space(0, -2)
        assertThat(doubles(d)).hasSize(3)
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Stunted Doubles - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                handicap = true
                played("razzle-handicap")
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Stunted Doubles - after the attack on a hero, spawn more of you`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                handicap = true
                played("razzle-handicap")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "razzle")
        d.space(0, -2)
        d.decline()
        assertThat(doubles(d)).hasSize(1)
    }

    // ---- "the same hero, except when actions are performed" (FAQ) ------------------------------

    @Test
    fun `FAQ - during an enemy action another one of Razzle is a different enemy hero (Cleave repeats on it)`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-gold") } // Attack 4
            hero("razzle", Team.BLUE, Hex(2, -2)) { hand("razzle-red-2a", "razzle-red-3a") } // Defense 4
            double("rzA", Hex(0, -2), Team.BLUE)
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("razzle", "rzA")
        d.unit("razzle")
        d.defend("razzle-red-2a")
        d.expect(DecisionKind.CONFIRM, "xargatha")
        d.yes()
        // "On a different enemy hero": the other figure (no question, it is the only one left).
        d.expect(DecisionKind.DEFEND, "razzle")
        assertThat(d.d.context.subjectId).isEqualTo("rzA")
    }

    @Test
    fun `Stunt Doubles - If defeated, remove all of you`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.RED, Hex(1, -2)) { played("arien-red-1") } // Attack 6
            hero("razzle", Team.BLUE, Hex(4, -5)) { hand("razzle-silver") }
            double("rzA", Hex(2, -2), Team.BLUE) // the target
            double("rzB", Hex(-2, -2), Team.BLUE)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "razzle")
        d.defend("razzle-silver") // 1 + 2 (only Razzle at 4,-5 is within 3 of the attacked figure) = 3 < 6
        assertThat(d.at("razzle")).isNull()
        assertThat(doubles(d)).isEmpty()
        assertThat(d.hero("arien").coins).isEqualTo(1)
    }

    // ---- choosing the one of you that acts --------------------------------------------------------

    @Test
    fun `Razzle chooses which one of her performs the action, before choosing it`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-red-1") }
            double("rzA", Hex(2, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5)) // adjacent to rzA only
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.expect(DecisionKind.CHOOSE_UNIT, "razzle")
        assertThat(d.units()).containsExactly("razzle", "rzA")
        d.unit("rzA")
        assertThat(d.at("razzle")).isEqualTo(Hex(2, -5))
        assertThat(d.at("rzA")).isEqualTo(Hex(1, -2))
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.hero("razzle").coins).isEqualTo(2)
    }

    // ---- Crowd Control (silver) ------------------------------------------------------------------

    @Test
    fun `Crowd Control - as a defense, +2 Defense for each other one of you in radius`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.RED, Hex(1, -2)) { played("arien-red-1") } // Attack 6
            hero("razzle", Team.BLUE, Hex(2, -2)) { hand("razzle-silver") } // Defense 1, radius 3
            double("rzA", Hex(3, -4), Team.BLUE) // 2 away
            double("rzB", Hex(2, -1), Team.BLUE) // 1 away
            double("rzC", Hex(4, -3), Team.BLUE) // 2 away
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("razzle-silver")
        assertThat(d.at("razzle")).isEqualTo(Hex(2, -2))
        val defended = d.s.events.filterIsInstance<GameEvent.Defended>().single()
        assertThat(defended.total).isEqualTo(7)
        assertThat(defended.success).isTrue()
    }

    @Test
    fun `Crowd Control - as a skill, remove all other you in play`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-silver") }
            double("rzA", Hex(2, -5))
            double("rzB", Hex(-1, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.unit("rzA") // act with this one: the others are removed
        d.action("razzle", ActionType.PRIMARY)
        assertThat(doubles(d)).isEmpty()
        assertThat(d.at("razzle")).isEqualTo(Hex(2, -5))
    }

    @Test
    fun `Crowd Control - with no other one of you, the skill is not offered`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-silver") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Phantom Strike / Hit and Gone / Into Thin Air (red) --------------------------------------

    @Test
    fun `Phantom Strike - if more than one of you is in play, you may remove one of you`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-red-1") }
            double("rzA", Hex(-1, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "razzle")
        assertThat(d.units()).containsExactly("razzle", "rzA")
        assertThat(d.d.options).contains(Choice.Decline())
        d.unit("razzle") // the acting one: Razzle carries on as the other figure
        assertThat(d.at("razzle")).isEqualTo(Hex(-1, -5))
        assertThat(doubles(d)).isEmpty()
    }

    @Test
    fun `Phantom Strike - alone, nothing to remove`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-red-1") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.at("razzle")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Hit and Gone - may remove one or more of you, except the last one`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-red-2a") }
            double("rzA", Hex(3, -5))
            double("rzB", Hex(-1, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("razzle", "rzA", "rzB")
        d.unit("rzA")
        assertThat(d.units()).containsExactly("razzle", "rzB")
        d.unit("razzle")
        // One left: no further question.
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.at("razzle")).isEqualTo(Hex(-1, -5))
        assertThat(doubles(d)).isEmpty()
    }

    @Test
    fun `Into Thin Air - may remove all of you, then you are not defeated and respawn as normal`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-red-3a")
                hand("razzle-blue-1")
            }
            double("rzA", Hex(3, -5))
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-green-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        val life = d.s.state.lifeCounters[Team.RED]
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.unit("rzA")
        d.unit("razzle")
        assertThat(d.at("razzle")).isNull()
        assertThat(doubles(d)).isEmpty()
        assertThat(d.s.state.lifeCounters[Team.RED]).isEqualTo(life)
        assertThat(d.s.events.filterIsInstance<GameEvent.Defeated>().none { it.unitId == "razzle" }).isTrue()
        assertThat(d.hero("arien").coins).isEqualTo(0)
        // Next turn she respawns as normal.
        d.play("razzle", "razzle-blue-1")
        d.play("arien", "arien-green-1")
        d.expect(DecisionKind.RESPAWN, "razzle")
    }

    // ---- Rummage / Ransack (red alternates) -----------------------------------------------------

    @Test
    fun `Rummage - if another one of you is in radius, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-red-2b") // radius 3
                discard("razzle-blue-1")
            }
            double("rzA", Hex(3, -4)) // 2 away
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "razzle")
        assertThat(d.d.options).contains(Choice.Decline())
        d.card("razzle-blue-1")
        assertThat(d.zoneOf("razzle", "razzle-blue-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Rummage - no other one of you in radius, no retrieve`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-red-2b")
                discard("razzle-blue-1")
            }
            double("rzA", Hex(-5, -4)) // 6 away
            hero("arien", Team.BLUE, Hex(4, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("razzle", "razzle-blue-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Ransack - for each other one of you in radius, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-red-3b")
                discard("razzle-blue-1", "razzle-green-1", "razzle-silver")
            }
            double("rzA", Hex(3, -4))
            double("rzB", Hex(-1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.card("razzle-blue-1")
        d.card("razzle-green-1")
        // Two others in radius: two cards at most.
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("razzle", "razzle-silver")).isEqualTo(Zone.DISCARD)
    }

    // ---- Tightrope / High Wire / Wire Dancers (green) ---------------------------------------------

    @Test
    fun `Tightrope - After you move, you may move another one of you 1 space`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-green-1") } // Movement 2
            double("rzA", Hex(3, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.d.context.subjectId).isEqualTo("razzle")
        d.space(0, -2)
        d.expect(DecisionKind.CHOOSE_UNIT, "razzle")
        assertThat(d.d.options).contains(Choice.Decline())
        d.unit("rzA")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -5))).isEqualTo(1)
        d.space(3, -6)
        assertThat(d.at("razzle")).isEqualTo(Hex(0, -2))
        assertThat(d.at("rzA")).isEqualTo(Hex(3, -6))
    }

    @Test
    fun `High Wire and Wire Dancers - move another one of you up to 2 and up to 3 spaces`() {
        for ((card, n) in listOf("razzle-green-2a" to 2, "razzle-green-3a" to 3)) {
            val d = pos {
                clearMinions()
                hero("razzle", Team.RED, Hex(1, -2)) { played(card) }
                double("rzA", Hex(2, -5))
                hero("arien", Team.BLUE, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "razzle")
            }
            d.actWithMain()
            d.action("razzle", ActionType.PRIMARY)
            d.decline() // Razzle herself stays
            // The only other one is chosen without a question; "up to": it may stay.
            assertThat(d.d.context.subjectId).isEqualTo("rzA")
            assertThat(d.d.options).contains(Choice.Decline("Don't move"))
            assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(2, -5)) }).isEqualTo(n)
        }
    }

    // ---- Theatrics / Spectacle (green alternates) ---------------------------------------------------

    @Test
    fun `Theatrics - swap with a minion in range, friendly or enemy`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-green-2b") } // range 2
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // 3 away
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly(minionAt(d, Hex(3, -2)), minionAt(d, Hex(0, -2)))
        val red = minionAt(d, Hex(3, -2))
        d.unit(red)
        assertThat(d.at("razzle")).isEqualTo(Hex(3, -2))
        assertThat(d.at(red)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Spectacle - may repeat once by another one of you, targeting a different minion`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-green-3b") }
            double("rzA", Hex(2, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // in range of Razzle only
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5)) // in range of rzA only
            phase = PositionPhase.Resolving(next = "razzle")
        }
        val m1 = minionAt(d, Hex(2, -2))
        val m2 = minionAt(d, Hex(3, -5))
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.at("razzle")).isEqualTo(Hex(2, -2))
        d.expect(DecisionKind.CHOOSE_UNIT, "razzle")
        assertThat(d.units()).containsExactly("rzA")
        d.unit("rzA")
        // The other one of you swapped with the other minion.
        assertThat(d.at("razzle")).isEqualTo(Hex(3, -5))
        assertThat(d.at(m2)).isEqualTo(Hex(2, -5))
        assertThat(d.at(m1)).isEqualTo(Hex(1, -2))
        assertThat(d.at("rzA")).isEqualTo(Hex(2, -2))
    }

    // ---- Alleyoop / Group Performance / Team Spirit (blue) ---------------------------------------

    @Test
    fun `Alleyoop - swap with a friendly hero in range, then move another one of you up to 1 space`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-blue-1") } // range 3
            double("rzA", Hex(-1, -5)) // 5 away
            hero("wasp", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.at("razzle")).isEqualTo(Hex(3, -2))
        assertThat(d.at("wasp")).isEqualTo(Hex(1, -2))
        assertThat(d.d.context.subjectId).isEqualTo("rzA")
        assertThat(d.d.options).contains(Choice.Decline("Don't move"))
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(-1, -5))).isEqualTo(1)
    }

    @Test
    fun `Alleyoop - FAQ - another one of you counts as a friendly hero during your action`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-blue-1") }
            double("rzA", Hex(3, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.at("razzle")).isEqualTo(Hex(3, -2))
        assertThat(d.at("rzA")).isEqualTo(Hex(1, -2))
        assertThat(d.d.context.subjectId).isEqualTo("rzA")
    }

    @Test
    fun `Alleyoop - an enemy hero is not a friendly hero, no action`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-blue-1") }
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Group Performance and Team Spirit - range 4, then up to 2 and up to 3 spaces`() {
        for ((card, n) in listOf("razzle-blue-2a" to 2, "razzle-blue-3a" to 3)) {
            val d = pos {
                clearMinions()
                hero("razzle", Team.RED, Hex(1, -2)) { played(card) }
                double("rzA", Hex(2, -7)) // 5 away: not a partner
                hero("wasp", Team.RED, Hex(5, -5)) // 4 away
                hero("arien", Team.BLUE, Hex(-5, -4))
                phase = PositionPhase.Resolving(next = "razzle")
            }
            d.actWithMain()
            d.action("razzle", ActionType.PRIMARY)
            assertThat(d.at("razzle")).isEqualTo(Hex(5, -5))
            assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(2, -7)) }).isEqualTo(n)
        }
    }

    // ---- Magic Trick / Aaaand it's gone! (blue alternates) ---------------------------------------

    @Test
    fun `Magic Trick - push a unit adjacent up to 2, this one of you moves as many spaces the opposite way`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-blue-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_NUMBER, "razzle")
        d.number(2)
        assertThat(d.s.state.minions.any { it.at == Hex(4, -2) }).isTrue()
        assertThat(d.at("razzle")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Aaaand it's gone - up to 3, and Razzle moves only as far as the target was pushed`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-blue-3b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.d.options.filterIsInstance<Choice.Number>().map { it.value }).containsExactly(0, 1, 2, 3)
        d.number(3) // terrain at (5,-2): the minion moves 2
        assertThat(d.s.state.minions.any { it.at == Hex(4, -2) }).isTrue()
        assertThat(d.at("razzle")).isEqualTo(Hex(-1, -2))
    }

    // ---- Twin Strike (Ultimate) ------------------------------------------------------------------

    @Test
    fun `Twin Strike - after a basic attack, another one of you may repeat it once on a different unit`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-gold")
                ultimate()
            }
            double("rzA", Hex(2, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // next to Razzle
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5)) // next to rzA
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.decline() // spawn nobody
        d.expect(DecisionKind.CHOOSE_UNIT, "razzle")
        assertThat(d.units()).containsExactly("rzA")
        assertThat(d.d.options).contains(Choice.Decline("Don't repeat"))
        d.unit("rzA")
        d.decline() // the repeated Stunt Doubles may spawn again; spawn nobody
        assertThat(d.hero("razzle").coins).isEqualTo(4)
        assertThat(d.s.state.minions.none { it.at == Hex(3, -5) }).isTrue()
        assertThat(d.at("razzle")).isEqualTo(Hex(2, -5))
    }

    @Test
    fun `Twin Strike - not after a non-basic attack`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-red-1")
                ultimate()
            }
            double("rzA", Hex(2, -5))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.actWithMain()
        d.action("razzle", ActionType.PRIMARY)
        d.decline() // Phantom Strike: don't remove anyone
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    // ---- immunity and items ----------------------------------------------------------------------

    @Test
    fun `Stunt Doubles - a Radius item widens the spawn radius (p16)`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) {
                played("razzle-gold") // radius 1 + 1 (Team Spirit as an item)
                items("razzle-blue-3a")
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7))
            phase = PositionPhase.Resolving(next = "razzle")
        }
        d.action("razzle", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(2)
    }

    @Test
    fun `Theatrics - a heavy minion with friendly minions in the Battle Zone is immune (p13)`() {
        val d = pos {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2)) { played("razzle-green-2b") }
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(0, -2)) // in range
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // out of range: guards the heavy
            phase = PositionPhase.Resolving(next = "razzle")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Razzle keep every invariant`() {
        val stats = RandomGames.play("razzle", games = 250, seed = 63)
        println("RAZZLE random games: $stats")
    }

    private companion object {
        val ANY_ENGINE = dev.quinton.goa.core.game.ANY_HERO_ENGINE
    }
}
