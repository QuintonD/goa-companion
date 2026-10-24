package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Minion Battle and Pushing the Lane (p.8), minions outside the Battle Zone (p.18), respawn (p.19). */
class MinionsTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    /** Heroes parked in their Throne Zones, out of the way. */
    private fun dev.quinton.goa.core.game.position.PositionBuilder.parkHeroes() {
        hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
        hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
    }

    @Test
    fun `p8 example - 4 against 2, Blue removes 2, the Lane is pushed and Red respawns one minion fewer`() {
        val d = pos {
            clearMinions()
            parkHeroes()
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.RANGED, Hex(0, -3))
            minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
            phase = PositionPhase.EndOfRound
        }
        // Heavy last: the melee goes first (no choice), then the heavy.
        assertThat(d.texts()).contains("Minion Battle: Red 4, Blue 2. Blue removes 2.")
        val pushed = d.s.events.filterIsInstance<GameEvent.LanePushed>().single()
        assertThat(pushed.winner).isEqualTo(Team.RED)
        assertThat(d.s.state.battleZones["lane-1"]).isEqualTo("lane-1-3")
        assertThat(d.s.state.wavesLeft["lane-1"]).isEqualTo(2)
        val red = d.s.state.minions.filter { it.team == Team.RED }
        val blue = d.s.state.minions.filter { it.team == Team.BLUE }
        // "the defending team has more minion spawn points in their Pre-Throne Zone. One Red minion will
        // not be respawned."
        assertThat(red).hasSize(5)
        assertThat(blue).hasSize(6)
        assertThat((red + blue).all { geo.zoneOf(it.at!!) == "lane-1-3" }).isTrue()
    }

    @Test
    fun `p8 minion battle - the heavy minion must be removed last, the team picks the others`() {
        val d = pos {
            clearMinions()
            parkHeroes()
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
            phase = PositionPhase.EndOfRound
        }
        // Red 4, Blue 5: Red removes 1, a melee (the heavy is last): Red chooses which melee.
        val p = d.expect(DecisionKind.REMOVE_MINION)
        assertThat(p.team).isEqualTo(Team.RED)
        assertThat(d.units()).hasSize(3)
        assertThat(d.units().map { d.s.state.minion(it)!!.type }.toSet()).containsExactly(MinionType.MELEE)
    }

    @Test
    fun `p8 - removing more minions than you have removes them all, then the Lane is pushed`() {
        val d = pos {
            clearMinions()
            parkHeroes()
            minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.s.events.filterIsInstance<GameEvent.LanePushed>().single().winner).isEqualTo(Team.BLUE)
        assertThat(d.s.state.battleZones["lane-1"]).isEqualTo("lane-1-1")
    }

    @Test
    fun `p8 triggering a push - defeating the last enemy minion in the Battle Zone pushes at once, the turn goes on`() {
        val d = pos {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { played("wasp-green-1") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        val pushed = d.s.events.filterIsInstance<GameEvent.LanePushed>().single()
        assertThat(pushed.winner).isEqualTo(Team.BLUE)
        assertThat(d.s.state.battleZones["lane-1"]).isEqualTo("lane-1-1")
        // Onslaught's After the attack still applies: Brogan moves into the space it occupied.
        assertThat(d.at("brogan")).isEqualTo(Hex(0, -2))
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
    }

    @Test
    fun `p8 note - a Lane push is never triggered by moving a minion`() {
        // Xargatha's Siren's Call pulls the last Blue minion out of the Battle Zone: it is moved back, no push.
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(-2, -3)) { played("xargatha-silver") } // lane-1-1
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0]) { played("arien-green-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -4))
            minion(Team.RED, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE)
        d.space(d.spaces().first { geo.zoneOf(it) == "lane-1-1" })
        // After the action it goes back (Blue picks among equally short paths).
        if (d.d.kind == DecisionKind.MINION_RETURN) d.space(d.spaces().first())
        assertThat(d.s.events.filterIsInstance<GameEvent.LanePushed>()).isEmpty()
        val m = d.s.state.minions.first { it.team == Team.BLUE }
        assertThat(geo.zoneOf(m.at!!)).isEqualTo("lane-1-2") // back in its Battle Zone after the action
    }

    @Test
    fun `p8 step 1 - flipping the last Wave counter ends the game for the team that won the push`() {
        val d = pos {
            clearMinions()
            parkHeroes()
            wavesLeft("lane-1", 1)
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            phase = PositionPhase.EndOfRound
        }
        val v = d.s.view(null)
        assertThat(v.winner).isEqualTo(Team.RED)
        assertThat(v.winReason).isEqualTo(WinReason.LAST_PUSH)
        assertThat(d.pending).isNull()
    }

    @Test
    fun `p8 step 2 - pushing the Lane into the enemy Throne Zone wins`() {
        val d = pos {
            clearMinions()
            parkHeroes()
            battleZone("lane-1", "lane-1-3")
            minion(Team.RED, MinionType.MELEE, Hex(7, -10))
            minion(Team.RED, MinionType.MELEE, Hex(8, -9))
            minion(Team.BLUE, MinionType.MELEE, Hex(9, -11))
            phase = PositionPhase.EndOfRound
        }
        val v = d.s.view(null)
        assertThat(v.winner).isEqualTo(Team.RED)
        assertThat(v.winReason).isEqualTo(WinReason.THRONE)
    }

    @Test
    fun `p8 step 3 - a token on a Spawn point is removed, another obstacle sends the minion to the nearest space`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(7, -5)) // on a Blue melee Spawn point of lane-1-3
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            token("token_rock", Hex(8, -6)) // on a Blue melee Spawn point too
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            phase = PositionPhase.EndOfRound
        }
        // Blue places its blocked melee minion in the nearest empty space of the new Battle Zone.
        val p = d.expect(DecisionKind.MINION_RETURN)
        assertThat(p.team).isEqualTo(Team.BLUE)
        val best = d.spaces().map { dev.quinton.goa.core.domain.board.HexGrid.distance(it, Hex(7, -5)) }.toSet()
        assertThat(best).containsExactly(1)
        d.space(d.spaces().first())
        assertThat(d.s.state.pieces).isEmpty() // the rock was removed for the minion
        assertThat(d.s.state.minions.count { it.at == Hex(8, -6) }).isEqualTo(1)
    }

    @Test
    fun `p18 minions outside the Battle Zone go back by the shortest path after the action`() {
        // A Red minion stands in lane-1-1, next to the Battle Zone: after Wasp's action (a Hold) it moves back.
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { played("wasp-blue-1") }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0]) { played("arien-green-1") }
            minion(Team.RED, MinionType.MELEE, Hex(-2, -3)) // lane-1-1, 1 step from lane-1-2
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.HOLD)
        // Two Battle Zone spaces are 1 step away, (-1,-3) and (-1,-4): Red decides.
        val p = d.expect(DecisionKind.MINION_RETURN)
        assertThat(p.team).isEqualTo(Team.RED)
        assertThat(d.spaces()).containsExactly(Hex(-1, -3), Hex(-1, -4))
        d.space(-1, -3)
        val m = d.s.state.minions.first { it.team == Team.RED }
        assertThat(m.at).isEqualTo(Hex(-1, -3))
        assertThat(d.texts()).contains("Red melee minion returns to the Battle Zone.")
    }

    @Test
    fun `p20 double lane - minions are bound to their Lane, each Lane is pushed on its own`() {
        val d = pos(TestData.RIVER) {
            clearMinions()
            val rgeo = TestData.engine.data.geometry(TestData.RIVER)
            hero("wasp", Team.RED, rgeo.heroSpawns(Team.RED)[0])
            hero("arien", Team.BLUE, rgeo.heroSpawns(Team.BLUE)[0])
            val z1 = rgeo.zoneSpaces.getValue("lane-1-2").filter { rgeo.spaces.getValue(it).spawn == null }
            val z2 = rgeo.zoneSpaces.getValue("lane-2-3").filter { rgeo.spaces.getValue(it).spawn == null }
            minion(Team.RED, MinionType.MELEE, z1[0], lane = "lane-1")
            minion(Team.BLUE, MinionType.MELEE, z1[1], lane = "lane-1")
            minion(Team.BLUE, MinionType.MELEE, z1[2], lane = "lane-1")
            minion(Team.RED, MinionType.MELEE, z2[0], lane = "lane-2")
            minion(Team.BLUE, MinionType.MELEE, z2[1], lane = "lane-2")
            phase = PositionPhase.EndOfRound
        }
        val pushes = d.s.events.filterIsInstance<GameEvent.LanePushed>()
        assertThat(pushes.map { it.lane }).containsExactly("lane-1")
        assertThat(d.s.state.battleZones).containsExactly("lane-1", "lane-1-1", "lane-2", "lane-2-3")
        assertThat(d.s.state.wavesLeft).containsExactly("lane-1", 6, "lane-2", 7)
        assertThat(d.s.state.minions.filter { it.lane == "lane-2" }).hasSize(2)
    }
}
