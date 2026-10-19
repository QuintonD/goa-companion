package dev.quinton.goa.core.domain.board

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.board.BoardSetup.PushResult
import dev.quinton.goa.core.domain.board.Fixtures.hero
import dev.quinton.goa.core.domain.board.Fixtures.minion
import dev.quinton.goa.core.domain.board.Fixtures.spawn
import dev.quinton.goa.core.domain.board.Fixtures.strip
import dev.quinton.goa.core.domain.board.Fixtures.token
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BoardSetupTest {
    private var n = 0
    private val newId = { "id${n++}" }

    private val three =
        BoardLineup(red = listOf("wasp", "xargatha", "tigerclaw"), blue = listOf("arien", "brogan", "sabina"))

    private fun BoardState.minions() = pieces.filterIsInstance<MinionPiece>()

    private fun BoardState.heroes(team: Team) = pieces.filterIsInstance<HeroPiece>().filter { it.team == team }

    // --- Opening (p.6 steps 9-10, p.20) on every bundled map ---

    @Test
    fun `the opening puts one matching minion on every spawn point of each starting Battle Zone`() {
        BundledMaps.all.forEach { map ->
            val g = BoardGeometry.of(map)
            val opening = BoardSetup.opening(g, three, newId)
            val starting = map.lanes.map { it.startingBattleZone }.toSet()
            val spawnPoints = map.spaces.filter { it.zone in starting && it.spawn?.kind?.minion != null }
            assertWithMessage(map.id).that(opening.minions()).hasSize(spawnPoints.size)
            spawnPoints.forEach { space ->
                val m = opening.minions().single { it.at == space.hex }
                assertWithMessage("${map.id} ${space.hex}").that(m.team).isEqualTo(space.spawn!!.team)
                assertWithMessage("${map.id} ${space.hex}").that(m.type).isEqualTo(space.spawn!!.kind.minion)
            }
            assertWithMessage(map.id).that(opening.battleZones)
                .isEqualTo(map.lanes.associate { it.id to it.startingBattleZone })
            assertWithMessage(map.id).that(opening.pieces.mapNotNull { it.at }).containsNoDuplicates()
        }
    }

    @Test
    fun `single-lane openings have 4 Melee, 1 Ranged and 1 Heavy per team (p6)`() {
        BundledMaps.all.filter { it.lanes.size == 1 }.forEach { map ->
            val opening = BoardSetup.opening(BoardGeometry.of(map), three, newId)
            Team.entries.forEach { team ->
                val types = opening.minions().filter { it.team == team }.groupingBy { it.type }.eachCount()
                assertWithMessage("${map.id} $team").that(types)
                    .isEqualTo(mapOf(MinionType.MELEE to 4, MinionType.RANGED to 1, MinionType.HEAVY to 1))
            }
        }
    }

    @Test
    fun `double-lane openings fill both starting Battle Zones (p20)`() {
        BundledMaps.all.filter { it.lanes.size == 2 }.forEach { map ->
            val g = BoardGeometry.of(map)
            val opening = BoardSetup.opening(g, three, newId)
            map.lanes.forEach { lane ->
                val inZone = opening.minions().filter { g.zoneOf(it.at!!) == lane.startingBattleZone }
                Team.entries.forEach { team ->
                    assertWithMessage("${map.id} ${lane.id} $team").that(inZone.count { it.team == team }).isAtLeast(1)
                }
            }
        }
    }

    @Test
    fun `heroes stand on their team's hero spawn points in pick order`() {
        BundledMaps.all.forEach { map ->
            val g = BoardGeometry.of(map)
            val opening = BoardSetup.opening(g, three, newId)
            Team.entries.forEach { team ->
                val heroes = opening.heroes(team)
                val ids = if (team == Team.RED) three.red else three.blue
                assertWithMessage(map.id).that(heroes.map { it.heroId }).isEqualTo(ids)
                assertWithMessage(map.id).that(heroes.map { it.at }).isEqualTo(g.heroSpawns(team))
                assertWithMessage(map.id).that(heroes.all { g.zoneOf(it.at!!) == g.throneZone(team) }).isTrue()
            }
        }
    }

    @Test
    fun `more than three heroes per team go next to their team's occupied hero spawn points (p20)`() {
        val five = BoardLineup(red = (1..5).map { "r$it" }, blue = (1..5).map { "b$it" })
        BundledMaps.all.forEach { map ->
            val g = BoardGeometry.of(map)
            val opening = BoardSetup.opening(g, five, newId)
            assertWithMessage(map.id).that(opening.pieces.mapNotNull { it.at }).containsNoDuplicates()
            Team.entries.forEach { team ->
                val heroes = opening.heroes(team)
                assertWithMessage(map.id).that(heroes).hasSize(5)
                val spawns = g.heroSpawns(team)
                assertWithMessage(map.id).that(heroes.take(3).map { it.at }).isEqualTo(spawns)
                heroes.drop(3).forEach { h ->
                    val at = h.at
                    assertWithMessage("${map.id} ${h.heroId}").that(at).isNotNull()
                    assertWithMessage("${map.id} ${h.heroId}").that(g.isSpace(at!!)).isTrue()
                    assertWithMessage("${map.id} ${h.heroId}").that(spawns.any { HexGrid.adjacent(it, at) }).isTrue()
                }
            }
        }
    }

    @Test
    fun `heroes beyond the room next to the spawn points wait in the tray`() {
        val spawns = mapOf(Hex(0, 0) to spawn(Team.RED, SpawnKind.HERO))
        val map = strip(spawns = spawns)
        val g = BoardGeometry.of(map)
        val opening = BoardSetup.opening(g, BoardLineup(red = (1..4).map { "r$it" }), newId)
        val heroes = opening.heroes(Team.RED)
        assertThat(heroes.map { it.at }).containsExactly(Hex(0, 0), Hex(1, 0), Hex(0, 1), null).inOrder()
    }

    // --- Push the Lane (p.8 steps 2-3) ---

    private val fi: BoardMap get() = BundledMaps.map("forgotten-island")

    private fun pushed(result: PushResult): PushResult.Pushed {
        assertThat(result).isInstanceOf(PushResult.Pushed::class.java)
        return result as PushResult.Pushed
    }

    @Test
    fun `a push moves the Battle Zone towards the loser's Throne and respawns the new Zone`() {
        val g = BoardGeometry.of(fi)
        val opening = BoardSetup.opening(g, three, newId)
        val lane = fi.lanes.single()
        val start = lane.zones.indexOf(lane.startingBattleZone)
        listOf(Team.RED to lane.zones[start + 1], Team.BLUE to lane.zones[start - 1]).forEach { (winner, zone) ->
            val result = pushed(BoardSetup.pushLane(g, opening, lane.id, winner, newId))
            assertThat(result.state.battleZones).isEqualTo(mapOf(lane.id to zone))
            assertThat(result.waiting).isEmpty()
            val minions = result.state.minions()
            assertThat(minions.none { g.zoneOf(it.at!!) == lane.startingBattleZone }).isTrue()
            val spawns = g.minionSpawns(zone)
            assertThat(minions).hasSize(spawns.size)
            spawns.forEach { s -> assertThat(minions.single { it.at == s.hex }.team).isEqualTo(s.spawn!!.team) }
            // p.8: the defending team (the loser) has more spawn points in its Pre-Throne Zone.
            assertThat(minions.count { it.team == winner.opponent }).isGreaterThan(minions.count { it.team == winner })
            assertThat(result.state.heroes(Team.RED)).isEqualTo(opening.heroes(Team.RED))
        }
    }

    @Test
    fun `pushing into the enemy Throne Zone wins the game`() {
        val g = BoardGeometry.of(fi)
        val lane = fi.lanes.single().id
        val opening = BoardSetup.opening(g, three, newId)
        val once = pushed(BoardSetup.pushLane(g, opening, lane, Team.RED, newId)).state
        assertThat(BoardSetup.pushLane(g, once, lane, Team.RED, newId)).isEqualTo(PushResult.ThroneCaptured(Team.RED))
        val back = pushed(BoardSetup.pushLane(g, once, lane, Team.BLUE, newId)).state
        assertThat(back.battleZones.getValue(lane)).isEqualTo(fi.lanes.single().startingBattleZone)
        val blue = pushed(BoardSetup.pushLane(g, back, lane, Team.BLUE, newId)).state
        assertThat(BoardSetup.pushLane(g, blue, lane, Team.BLUE, newId)).isEqualTo(PushResult.ThroneCaptured(Team.BLUE))
    }

    @Test
    fun `a token on a spawn point is removed, any other obstacle sends the minion to the tray`() {
        val g = BoardGeometry.of(fi)
        val lane = fi.lanes.single()
        val next = lane.zones[lane.zones.indexOf(lane.startingBattleZone) + 1]
        val (first, second) = g.minionSpawns(next).take(2)
        val opening = BoardSetup.opening(g, three, newId)
        val tok = TokenPiece("tok", first.hex, "Mine")
        val blocker = hero(Team.RED, second.hex, "wasp-2")
        val result =
            pushed(
                BoardSetup.pushLane(g, opening.copy(pieces = opening.pieces + tok + blocker), lane.id, Team.RED, newId),
            )
        assertThat(result.state.pieces).doesNotContain(tok)
        assertThat(result.state.minions().single { it.at == first.hex }.type).isEqualTo(first.spawn!!.kind.minion)
        val waiting = result.waiting.single()
        assertThat(waiting.spawn).isEqualTo(second.hex)
        assertThat(waiting.piece.at).isNull()
        assertThat(waiting.piece.team).isEqualTo(second.spawn!!.team)
        assertThat(result.state.pieces).contains(waiting.piece)
        assertThat(result.state.pieces).contains(blocker)
        val nearest = BoardSetup.nearestEmpty(g, result.state, next, second.hex)
        assertThat(nearest).isNotEmpty()
        val occupied = result.state.pieces.mapNotNull { it.at }.toSet()
        assertThat(nearest.none { it in occupied }).isTrue()
        assertThat(nearest.all { g.zoneOf(it) == next }).isTrue()
        val d = nearest.map { HexGrid.distance(second.hex, it) }.distinct().single()
        val closer = g.zoneSpaces.getValue(next).filter { it !in occupied && HexGrid.distance(second.hex, it) < d }
        assertThat(closer).isEmpty()
    }

    @Test
    fun `on a double-lane map a push moves only its own lane`() {
        val map = BundledMaps.map("across-the-river")
        val g = BoardGeometry.of(map)
        val opening = BoardSetup.opening(g, three, newId)
        val (l1, l2) = map.lanes
        val result = pushed(BoardSetup.pushLane(g, opening, l1.id, Team.BLUE, newId)).state
        assertThat(result.battleZones.getValue(l2.id)).isEqualTo(l2.startingBattleZone)
        assertThat(result.battleZones.getValue(l1.id)).isEqualTo(l1.zones[l1.zones.indexOf(l1.startingBattleZone) - 1])
        val lane2Minions = { s: BoardState ->
            s.minions().filter { g.zoneOf(it.at!!) == l2.startingBattleZone }.toSet()
        }
        assertThat(lane2Minions(result)).isEqualTo(lane2Minions(opening))
    }

    @Test
    fun `every minion is bound to the Lane it spawned in (p20)`() {
        BundledMaps.all.forEach { map ->
            val g = BoardGeometry.of(map)
            val opening = BoardSetup.opening(g, three, newId)
            opening.minions().forEach { m ->
                val lane = map.lanes.single { it.id == m.lane }
                assertWithMessage("${map.id} ${m.at}").that(g.zoneOf(m.at!!)).isEqualTo(lane.startingBattleZone)
            }
        }
        val map = BundledMaps.map("across-the-river")
        val g = BoardGeometry.of(map)
        val l1 = map.lanes.first()
        val pushed = pushed(BoardSetup.pushLane(g, BoardSetup.opening(g, three, newId), l1.id, Team.RED, newId)).state
        val next = pushed.battleZones.getValue(l1.id)
        assertThat(pushed.minions().filter { g.zoneOf(it.at!!) == next }.map { it.lane }.distinct()).containsExactly(l1.id)
    }

    @Test
    fun `a push leaves the other Lane's minions in the previous Battle Zone`() {
        val map = BundledMaps.map("across-the-river")
        val g = BoardGeometry.of(map)
        val (l1, l2) = map.lanes
        val opening = BoardSetup.opening(g, three, newId)
        // A Lane 2 minion pushed into Lane 1's Battle Zone stays bound to Lane 2: Lane 1's push
        // doesn't remove it (it goes back to its own Battle Zone, p.18 / p.20).
        val stray = opening.minions().first { it.lane == l2.id }
        val spot = g.zoneSpaces.getValue(l1.startingBattleZone).first { hex -> opening.pieces.none { it.at == hex } }
        val moved = opening.copy(pieces = opening.pieces.map { if (it.id == stray.id) it.moveTo(spot) else it })
        val result = pushed(BoardSetup.pushLane(g, moved, l1.id, Team.RED, newId)).state
        assertThat(result.pieces.single { it.id == stray.id }.at).isEqualTo(spot)
        assertThat(result.minions().none { it.lane == l1.id && g.zoneOf(it.at!!) == l1.startingBattleZone }).isTrue()
        assertThat(BoardRules.returnToBattleZone(g, result, result.minions().single { it.id == stray.id }))
            .isNotEmpty()
    }

    @Test
    fun `the next push removes the previous Battle Zone's minions still waiting in the tray (p8 step 3)`() {
        val g = BoardGeometry.of(fi)
        val lane = fi.lanes.single()
        val next = lane.zones[lane.zones.indexOf(lane.startingBattleZone) + 1]
        val blocked = g.minionSpawns(next).first()
        val opening = BoardSetup.opening(g, three, newId)
        val blocker = hero(Team.RED, blocked.hex, "wasp-2")
        val first = pushed(BoardSetup.pushLane(g, opening.copy(pieces = opening.pieces + blocker), lane.id, Team.RED, newId))
        val waiting = first.waiting.single().piece
        assertThat(waiting.lane).isEqualTo(lane.id)
        assertThat(first.state.pieces).contains(waiting)
        // Blue wins the next push back: Zone `next` was the Battle Zone, so its waiting minion goes too.
        val second = pushed(BoardSetup.pushLane(g, first.state, lane.id, Team.BLUE, newId)).state
        assertThat(second.pieces.none { it.id == waiting.id }).isTrue()
        assertThat(second.pieces.none { it.at == null && it is MinionPiece }).isTrue()
        assertThat(second.pieces).contains(blocker)
    }

    @Test
    fun `waiting minions aim at the nearest empty spaces of their Lane's Battle Zone, one Spawn point each`() {
        val g = BoardGeometry.of(fi)
        val lane = fi.lanes.single()
        val next = lane.zones[lane.zones.indexOf(lane.startingBattleZone) + 1]
        val melee = g.minionSpawns(next).filter { it.spawn == spawn(Team.RED, SpawnKind.MELEE) }.take(2)
        check(melee.size == 2)
        val opening = BoardSetup.opening(g, three, newId)
        val blockers = melee.mapIndexed { i, s -> hero(Team.BLUE, s.hex, "blocker-$i") }
        val result = pushed(
            BoardSetup.pushLane(g, opening.copy(pieces = opening.pieces + blockers), lane.id, Team.RED, newId),
        )
        assertThat(result.waiting.map { it.spawn }).containsExactly(melee[0].hex, melee[1].hex).inOrder()
        result.waiting.forEach { w ->
            // Derived from the saved state alone (a reopened board), the same targets as right after the push.
            assertThat(BoardSetup.waitingTargets(g, result.state, w.piece))
                .isEqualTo(BoardSetup.nearestEmpty(g, result.state, next, w.spawn))
        }
        // A minion on the board, or one with no Lane on a two-lane map, has no targets.
        assertThat(BoardSetup.waitingTargets(g, result.state, result.state.minions().first { it.at != null })).isNull()
    }

    @Test
    fun `nearestEmpty is empty when the Zone is full and counts obstacles in the distance`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val full = BoardState(map.id, g.zoneSpaces.getValue("b").map { minion(Team.RED, it) })
        assertThat(BoardSetup.nearestEmpty(g, full, "b", Hex(7, 2))).isEmpty()
        val walled =
            BoardState(
                map.id,
                listOf(
                    token(Hex(6, 2)),
                    token(Hex(7, 1)),
                    token(Hex(7, 3)),
                    token(Hex(8, 1)),
                    token(Hex(8, 2)),
                    token(Hex(6, 3)),
                ),
            )
        assertThat(BoardSetup.nearestEmpty(g, walled, "b", Hex(7, 2))).containsExactly(Hex(7, 2))
        val taken = BoardState(map.id, listOf(minion(Team.BLUE, Hex(7, 2))) + walled.pieces)
        // Every neighbour of (7,2) is taken, so distance 2 wins even though no path leads there.
        val ring = BoardSetup.nearestEmpty(g, taken, "b", Hex(7, 2))
        assertThat(ring.all { HexGrid.distance(Hex(7, 2), it) == 2 }).isTrue()
    }

    @Test
    fun `bundled map Zone adjacency follows the printed borders`() {
        val g = BoardGeometry.of(fi)
        assertThat(g.adjacentZones.getValue("red-throne")).containsExactly("lane-1-1", "red-side")
        assertThat(
            g.adjacentZones.getValue("lane-1-2"),
        ).containsExactly("lane-1-1", "lane-1-3", "red-side", "blue-side")
        assertThat(g.adjacentZones.getValue("blue-throne")).containsExactly("lane-1-3", "blue-side")
    }
}
