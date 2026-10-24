package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.boardState
import dev.quinton.goa.core.game.engine.fastTravelDestinations
import dev.quinton.goa.core.game.engine.push
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test
import kotlin.random.Random

/** Movement and Fast Travel, rulebook p.11; distances p.17–18. */
class MovementTest {
    @Test
    fun `p11 movement - up to N spaces through empty spaces only, friendly units block too`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-blue-1") } // secondary Movement 3
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -1)) { played("wasp-green-1") }
            token("token_rock", Hex(0, -3))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.MOVEMENT)
        val spaces = d.spaces()
        // Never into an obstacle: a friendly hero, an enemy hero, a token.
        assertThat(spaces).containsNoneOf(Hex(1, -2), Hex(-1, -1), Hex(0, -3))
        // (2,-2) is 2 steps away in a line but Brogan blocks that way; another path is 3 steps.
        assertThat(spaces).contains(Hex(2, -2))
        for (h in spaces) assertThat(d.d.context.paths["${h.q},${h.r}"]!!.size).isAtMost(3)
    }

    @Test
    fun `p11 and BoardRules agree - movement reach on random positions without effects`() {
        val rng = Random(7)
        val engine = TestData.engine
        repeat(200) {
            val s = TestData.newGame()
            val g = Game(engine.data, s.state.copy(), emptyList(), RunMode.PROBE)
            // Scatter the heroes on random empty spaces.
            val free = g.geo.spaces.keys.filter { g.occupant(it) == null }.shuffled(rng)
            g.state.heroes.forEachIndexed { i, h -> h.at = free[i] }
            g.touch()
            val hero = g.state.heroes[rng.nextInt(4)]
            val steps = rng.nextInt(1, 6)
            val mine = g.reach(MoveSpec(hero.id, 0, steps)).keys - hero.at!!
            val audited = BoardRules.movementReach(g.geo, g.boardState(), hero.at!!, steps).keys
            assertThat(mine).containsExactlyElementsIn(audited)
            val ft = g.fastTravelDestinations(hero.id).toSet()
            val auditedFt = BoardRules.fastTravel(g.geo, g.boardState(), HeroPiece(hero.id, hero.at, hero.id, hero.team))
            assertThat(ft).containsExactlyElementsIn(auditedFt)
            val straight = g.reach(MoveSpec(hero.id, 0, steps, straightLine = true)).keys - hero.at!!
            assertThat(straight).containsExactlyElementsIn(BoardRules.movementReach(g.geo, g.boardState(), hero.at!!, steps, straightLine = true).keys)
        }
    }

    @Test
    fun `p18 push and BoardRules agree on random positions`() {
        val rng = Random(11)
        val engine = TestData.engine
        repeat(300) {
            val s = TestData.newGame()
            val g = Game(engine.data, s.state.copy(), emptyList(), RunMode.PROBE)
            val free = g.geo.spaces.keys.filter { g.occupant(it) == null }.shuffled(rng)
            g.state.heroes.forEachIndexed { i, h -> h.at = free[i] }
            g.touch()
            val pusher = g.state.heroes[0]
            val target = g.state.minions[rng.nextInt(g.state.minions.size)]
            val n = rng.nextInt(0, 5)
            val ignore = rng.nextBoolean()
            val expected = BoardRules.push(g.geo, g.boardState(), pusher.at!!, target.at!!, n, ignore)
            val r = g.push(target.id, pusher.at!!, n, ignore, byHero = null)
            if (expected == null) {
                assertThat(r).isNull()
            } else if (ignore) {
                // Ignoring obstacles the engine stops at the edge of the drawn board ([APP] water one ring deep).
                assertThat(r!!.to).isNotNull()
            } else {
                assertThat(r!!.to).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `p11 fast travel - not into a Zone with enemies, not from one, only same or adjacent Zones`() {
        // Arien in Red's Pre-Throne Zone (lane-1-1) with no enemy there: the Battle Zone holds Red minions.
        val d = pos {
            hero("arien", Team.BLUE, Hex(-4, 3)) { played("arien-blue-1") }
            hero("wasp", Team.RED, Hex(8, -3)) { played("wasp-green-1") } // far away, in Blue's Throne Zone
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.FAST_TRAVEL)
        val g = Game(TestData.engine.data, d.s.state, emptyList(), RunMode.PROBE)
        val zones = d.spaces().map { g.geo.zoneOf(it) }.toSet()
        assertThat(zones).doesNotContain("lane-1-2") // the Battle Zone has enemy (Red) minions
        assertThat(zones).contains("lane-1-1")
        for (z in zones) assertThat(z == "lane-1-1" || z in g.geo.adjacentZones.getValue("lane-1-1")).isTrue()
    }

    @Test
    fun `p11 fast travel - impossible from a Zone with an enemy hero or minion`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(-4, 3)) { played("arien-blue-1") }
            hero("wasp", Team.RED, Hex(-3, 3)) { played("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).doesNotContain(ActionType.FAST_TRAVEL)
    }

    @Test
    fun `p11 movement in card text - an exact distance must be moved in full, or the action stops`() {
        // Brogan's Mad Dash: "Move 2 spaces in a straight line to a space adjacent to an enemy unit".
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(4, -2)) { played("brogan-red-1") }
            hero("wasp", Team.RED, Hex(1, -1)) { played("wasp-green-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        // Only the end space 2 spaces west, (2,-2), is next to Wasp; it is reached automatically.
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `p11 movement limits are checked at the start of the movement action`() {
        // Slippery Ground (Arien): adjacent enemy heroes "cannot … move more than 1 space with a movement action".
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-blue-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-red-1") } // Movement 4
            hero("xargatha", Team.RED, Hex(4, -5)) { played("xargatha-red-1") } // not adjacent
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.decline() // Arien does not move: Wasp stays adjacent
        d.action("wasp", ActionType.MOVEMENT)
        for (h in d.spaces()) assertThat(dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(0, -2), h)).isEqualTo(1)
        d.space(-1, -2)
        // Xargatha starts her movement away from Arien: no limit, even moving through his radius.
        d.action("xargatha", ActionType.MOVEMENT)
        assertThat(d.spaces().any { dev.quinton.goa.core.domain.board.HexGrid.distance(Hex(4, -5), it) > 1 }).isTrue()
    }
}
