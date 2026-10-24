package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Turn sequence, rulebook p.7. */
class TurnTest {
    @Test
    fun `p7 play one card - asked seat by seat, hidden from the others until the reveal`() {
        val d = dev.quinton.goa.core.game.Driver(TestData.newGame())
        d.expect(DecisionKind.PLAY_CARD, "wasp")
        assertThat(d.cards()).containsExactly("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-1")
        d.play("wasp", "wasp-red-1")
        val arienView = d.s.view("arien").heroes.first { it.heroId == "wasp" }
        assertThat(arienView.played!!.cardId).isNull()
        assertThat(arienView.played!!.facedown).isTrue()
        assertThat(arienView.hand).isNull()
        assertThat(arienView.handCount).isEqualTo(4)
        val own = d.s.view("wasp").heroes.first { it.heroId == "wasp" }
        assertThat(own.played!!.cardId).isEqualTo("wasp-red-1")
        // The event log never names a facedown card.
        assertThat(d.texts().none { "Shock" in it }).isTrue()
        d.play("xargatha", "xargatha-red-1").play("arien", "arien-red-1").play("brogan", "brogan-red-1")
        val revealed = d.s.view("arien").heroes.first { it.heroId == "wasp" }
        assertThat(revealed.played!!.cardId).isEqualTo("wasp-red-1")
        assertThat(
            d.s.events.filterIsInstance<GameEvent.CardsRevealed>().single().played["wasp"],
        ).isEqualTo("wasp-red-1")
    }

    @Test
    fun `p7 - you must play a card if able, and pass only with no cards in hand, even off the board`() {
        val d = pos {
            hero("wasp", Team.RED, null) { hand() }
            hero("arien", Team.BLUE, Hex(3, -5)) { hand("arien-gold") }
            phase = PositionPhase.PlayCards()
        }
        val p = d.expect(DecisionKind.PLAY_CARD, "wasp")
        assertThat(p.options).containsExactly(Choice.Pass)
        d.answer(Choice.Pass)
        val q = d.expect(DecisionKind.PLAY_CARD, "arien")
        assertThat(q.options).containsExactly(Choice.Card("arien-gold"))
    }

    @Test
    fun `p7 - the highest initiative acts first, then the next highest`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-silver") } // 13
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-silver") } // 3
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") } // 4
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-red-1") } // 7
            phase = PositionPhase.Resolving()
        }
        assertThat(d.s.view(null).initiativeOrder).containsExactly("wasp", "brogan", "arien", "xargatha").inOrder()
        d.action("wasp", ActionType.HOLD)
        d.action("brogan", ActionType.HOLD)
        d.action("arien", ActionType.HOLD)
        d.action("xargatha", ActionType.HOLD)
        assertThat(d.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `p7 tied initiative across teams - the coin's team acts first and only that player flips the coin`() {
        val d = pos {
            clearMinions()
            tieBreaker = Team.BLUE
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") } // 8
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") } // 8
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-blue-1") } // 8
            hero("xargatha", Team.RED, Hex(1, -5)) { played("xargatha-red-1") } // 7
            phase = PositionPhase.Resolving()
        }
        // Three tied at 8 across teams: one of the Blue players (coin) acts first; Blue decides which.
        val p = d.expect(DecisionKind.ACT_FIRST)
        assertThat(p.team).isEqualTo(Team.BLUE)
        assertThat(d.units()).containsExactly("arien", "brogan")
        d.unit("brogan")
        d.action("brogan", ActionType.HOLD)
        // Brogan acted first after a cross-team tie, so he flips: Red shows.
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.RED)
        assertThat(d.texts()).contains("Brogan flips the Tie Breaker coin: Red shows.")
        // Remaining tie Wasp (Red) vs Arien (Blue): Red now shows, Wasp acts and flips.
        d.action("wasp", ActionType.HOLD)
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
        d.action("arien", ActionType.HOLD)
        // Arien was not tied with anyone any more: no flip.
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
        d.action("xargatha", ActionType.HOLD)
    }

    @Test
    fun `p7 tied initiative within a team - the team decides and the coin is not flipped`() {
        val d = pos {
            clearMinions()
            tieBreaker = Team.RED
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") } // 8
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") } // 8 ...
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-blue-1") } // 8
            hero("xargatha", Team.RED, Hex(1, -5)) { played("xargatha-green-1") } // 5
            phase = PositionPhase.Resolving()
        }
        // Coin Red: Wasp (the only tied Red) acts first and flips to Blue.
        d.action("wasp", ActionType.HOLD)
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
        // Now only Blue heroes are tied: they decide, no flip.
        d.expect(DecisionKind.ACT_FIRST)
        d.unit("arien")
        d.action("arien", ActionType.HOLD)
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
        d.action("brogan", ActionType.HOLD)
        assertThat(d.s.state.tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun `p7 - the initiative is checked again after every resolution`() {
        // Xargatha's Ultimate: +1 Initiative for each enemy unit adjacent to her (p.16 ability).
        val d = pos {
            clearMinions()
            tieBreaker = Team.BLUE
            hero("xargatha", Team.RED, Hex(1, -5)) {
                played("xargatha-green-1")
                ultimate()
            } // 5 + 1 (minion) = 6
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-green-1") } // 6: tied, Blue's coin
            hero("arien", Team.BLUE, Hex(3, -5)) { played("arien-red-1") } // 8
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            phase = PositionPhase.Resolving()
        }
        assertThat(d.s.view(null).initiativeOrder).containsExactly("arien", "brogan", "xargatha").inOrder()
        d.action("arien", ActionType.MOVEMENT)
        d.space(2, -5) // adjacent to Xargatha: 2 adjacent enemies, initiative 7
        assertThat(d.s.view(null).initiativeOrder).containsExactly("xargatha", "brogan").inOrder()
        d.expect(DecisionKind.CHOOSE_ACTION, "xargatha")
    }

    @Test
    fun `p7 hero respawn - an off-board hero may respawn on an empty hero spawn point before acting, or stay off`() {
        val geo = TestData.engine.data.geometry(TestData.FOREST)
        val spawns = geo.heroSpawns(Team.RED)
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, null) { played("wasp-red-1") }
            hero("xargatha", Team.RED, spawns[0]) { played("xargatha-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving()
        }
        val p = d.expect(DecisionKind.RESPAWN, "wasp")
        assertThat(d.spaces()).containsExactlyElementsIn(spawns.drop(1))
        assertThat(p.options).contains(Choice.Decline("Don't respawn"))
        d.space(spawns[1])
        assertThat(d.at("wasp")).isEqualTo(spawns[1])
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
    }

    @Test
    fun `p7 hero respawn - choosing not to respawn resolves the card without an action`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, null) { played("wasp-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving()
        }
        d.expect(DecisionKind.RESPAWN, "wasp")
        d.decline()
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.RESOLVED)
        assertThat(d.at("wasp")).isNull()
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `p7 hero respawn - with no empty spawn point, the nearest empty space adjacent to one`() {
        val geo = TestData.engine.data.geometry(TestData.FOREST)
        val spawns = geo.heroSpawns(Team.RED)
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, null) { played("wasp-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") }
            spawns.forEach { token("token_rock", it) }
            phase = PositionPhase.Resolving()
        }
        d.expect(DecisionKind.RESPAWN, "wasp")
        for (h in d.spaces()) {
            assertThat(
                spawns.any {
                    geo.isSpace(h) &&
                        dev.quinton.goa.core.domain.board.HexGrid.adjacent(it, h)
                },
            ).isTrue()
        }
        assertThat(d.spaces()).isNotEmpty()
    }

    @Test
    fun `p7 p14 - a played Defense card offers its secondary actions and Hold, not the Defense action`() {
        val d = pos {
            clearMinions()
            // Aspiring Duelist: Defense, secondary Movement.
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") }
            hero("wasp", Team.RED, Hex(-5, -4)) { played("wasp-red-1") } // Red Throne Zone: Arien may fast travel
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).containsExactly(ActionType.MOVEMENT, ActionType.FAST_TRAVEL, ActionType.HOLD)
    }

    @Test
    fun `p13 clear - replaces the Attack action and is offered only with adjacent tokens`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -5)) { played("wasp-red-1") }
            token("token_rock", Hex(4, -2))
            token("token_rock", Hex(3, -3))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        assertThat(d.actions()).contains(ActionType.CLEAR)
        d.action("brogan", ActionType.CLEAR)
        d.expect(DecisionKind.CHOOSE_TOKEN)
        d.answer(d.d.options.first { it is Choice.Token })
        d.answer(d.d.options.first { it is Choice.Token })
        assertThat(d.s.state.pieces).isEmpty()
    }

    @Test
    fun `p7 resolve your card - it goes to the current Turn slot`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-gold") }
            hero("wasp", Team.RED, Hex(-1, -5)) { played("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.HOLD)
        val c = d.hero("arien").card("arien-gold")!!
        assertThat(c.zone).isEqualTo(Zone.RESOLVED)
        assertThat(c.slot).isEqualTo(3)
        assertThat(d.s.view("wasp").heroes.first { it.heroId == "arien" }.resolved.single().slot).isEqualTo(3)
    }

    @Test
    fun `p7 turn ends - This Turn effects are cancelled`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-silver") }
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.s.state.effects).hasSize(1)
        d.action("wasp", ActionType.HOLD)
        assertThat(d.s.state.effects).isEmpty()
        assertThat(d.s.state.turn).isEqualTo(2)
    }
}
