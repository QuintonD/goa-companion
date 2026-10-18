package dev.quinton.goa.core.domain.board

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.Fixtures.hero
import dev.quinton.goa.core.domain.board.Fixtures.minion
import dev.quinton.goa.core.domain.board.Fixtures.spawn
import dev.quinton.goa.core.domain.board.Fixtures.strip
import dev.quinton.goa.core.domain.board.Fixtures.token
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BoardRulesTest {
    private fun state(map: dev.quinton.goa.core.model.board.BoardMap, vararg pieces: BoardPiece) =
        BoardState(mapId = map.id, pieces = pieces.toList())

    // --- Movement (p.11, p.18 "Ignoring Obstacles") ---

    @Test
    fun `movement reaches every space up to N away on an open board, costs are distances`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val from = Hex(4, 2)
        val reach = BoardRules.movementReach(g, state(map), from, 2)
        assertThat(reach.keys).containsExactlyElementsIn(HexGrid.within(from, 2))
        reach.forEach { (hex, cost) -> assertThat(cost).isEqualTo(HexGrid.distance(from, hex)) }
        assertThat(BoardRules.movementReach(g, state(map), from, 0)).isEmpty()
    }

    @Test
    fun `units of either team block movement and are never a destination`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val wall = (0..4).map { r -> minion(if (r % 2 == 0) Team.RED else Team.BLUE, Hex(5, r)) }
        val mover = hero(Team.RED, Hex(4, 2))
        val reach = BoardRules.movementReach(g, state(map, mover, *wall.toTypedArray()), Hex(4, 2), 4)
        assertThat(reach.keys.none { it.q >= 5 }).isTrue()
        assertThat(reach.keys.none { hex -> wall.any { it.at == hex } }).isTrue()
    }

    @Test
    fun `tokens are obstacles too`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val reach = BoardRules.movementReach(g, state(map, token(Hex(5, 2))), Hex(4, 2), 1)
        assertThat(reach.keys).doesNotContain(Hex(5, 2))
        assertThat(reach).hasSize(5)
    }

    @Test
    fun `terrain forces a detour, the distance still counts it`() {
        val holes = (0..3).map { Hex(7, it) }.toSet()
        val map = strip(holes = holes)
        val g = BoardGeometry.of(map)
        val from = Hex(6, 1)
        val target = Hex(8, 1)
        assertThat(HexGrid.distance(from, target)).isEqualTo(2)
        assertThat(BoardRules.movementReach(g, state(map), from, 6)).doesNotContainKey(target)
        assertThat(BoardRules.movementReach(g, state(map), from, 7)[target]).isEqualTo(7)
        assertThat(BoardRules.movementReach(g, state(map), from, 2, ignoreObstacles = true)[target]).isEqualTo(2)
    }

    @Test
    fun `ignoring obstacles passes units and terrain but never ends on them`() {
        val map = strip(holes = setOf(Hex(6, 2)))
        val g = BoardGeometry.of(map)
        val blockers = listOf(minion(Team.BLUE, Hex(5, 2)), token(Hex(7, 2)))
        val s = state(map, *blockers.toTypedArray())
        val reach = BoardRules.movementReach(g, s, Hex(4, 2), 4, ignoreObstacles = true)
        assertThat(reach[Hex(8, 2)]).isEqualTo(4)
        assertThat(reach.keys).containsNoneOf(Hex(5, 2), Hex(6, 2), Hex(7, 2))
        assertThat(reach.keys.all { g.isSpace(it) }).isTrue()
    }

    @Test
    fun `ignoring obstacles crosses water outside the map (p19 terrain), plain movement can't`() {
        // A notch: (5,0)..(5,3) are not spaces of the map at all (water), only (5,4) connects.
        val notch = (0..3).map { Hex(5, it) }.toSet()
        val base = strip()
        val map = base.copy(id = "notch", spaces = base.spaces.filter { it.hex !in notch })
        val g = BoardGeometry.of(map)
        assertThat(g.isTerrain(Hex(5, 0))).isFalse()
        val plain = BoardRules.movementReach(g, state(map), Hex(4, 0), 2)
        assertThat(plain).doesNotContainKey(Hex(6, 0))
        val ignoring = BoardRules.movementReach(g, state(map), Hex(4, 0), 2, ignoreObstacles = true)
        assertThat(ignoring[Hex(6, 0)]).isEqualTo(2)
    }

    @Test
    fun `the moving unit's own space doesn't block and isn't a destination`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val mover = hero(Team.RED, Hex(4, 2))
        val reach = BoardRules.movementReach(g, state(map, mover), Hex(4, 2), 2)
        assertThat(reach).doesNotContainKey(Hex(4, 2))
        assertThat(reach).hasSize(18)
    }

    // --- Fast Travel (p.11) and Zone adjacency (p.17) ---

    @Test
    fun `fast travel reaches every empty space of the own and adjacent Zones`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val arien = hero(Team.RED, Hex(4, 2), "arien")
        val friend = minion(Team.RED, Hex(7, 2))
        val ft = BoardRules.fastTravel(g, state(map, arien, friend), arien)
        val expected = map.spaces.filter { it.zone in setOf("red-throne", "a", "b") }.map { it.hex } -
            setOf(Hex(4, 2), Hex(7, 2))
        assertThat(ft).containsExactlyElementsIn(expected)
        // Zone c is not adjacent to a (the Arien example: Zones that don't share a border).
        assertThat(ft.none { g.zoneOf(it) == "c" }).isTrue()
    }

    @Test
    fun `an enemy hero or minion in the destination Zone closes that Zone only`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val arien = hero(Team.RED, Hex(4, 2))
        val enemyMinion = minion(Team.BLUE, Hex(7, 0))
        val enemyHero = hero(Team.BLUE, Hex(1, 1))
        val ft = BoardRules.fastTravel(g, state(map, arien, enemyMinion, enemyHero), arien)
        assertThat(ft.map { g.zoneOf(it) }.toSet()).containsExactly("a")
    }

    @Test
    fun `an enemy in the Zone you travel from forbids fast travel`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val arien = hero(Team.RED, Hex(4, 2))
        assertThat(BoardRules.fastTravel(g, state(map, arien, minion(Team.BLUE, Hex(3, 0))), arien)).isEmpty()
        assertThat(BoardRules.fastTravel(g, state(map, arien.copy(at = null)), arien.copy(at = null))).isEmpty()
    }

    @Test
    fun `tokens and friendly units don't stop fast travel, they only fill their space`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val arien = hero(Team.RED, Hex(4, 2))
        val ft = BoardRules.fastTravel(g, state(map, arien, token(Hex(7, 1)), hero(Team.RED, Hex(8, 3))), arien)
        assertThat(ft).containsNoneOf(Hex(7, 1), Hex(8, 3))
        assertThat(ft).contains(Hex(7, 2))
    }

    @Test
    fun `Zones touching only through terrain are not adjacent`() {
        val holes = (0..4).map { Hex(6, it) }.toSet()
        val map = strip(holes = holes)
        val g = BoardGeometry.of(map)
        assertThat(g.adjacentZones.getValue("a")).containsExactly("red-throne")
        assertThat(g.adjacentZones.getValue("b")).containsExactly("c")
    }

    @Test
    fun `units and tokens don't change Zone adjacency (p11)`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val wall = (0..4).map { token(Hex(6, it)) }
        val arien = hero(Team.RED, Hex(4, 2))
        val ft = BoardRules.fastTravel(g, state(map, arien, *wall.toTypedArray()), arien)
        assertThat(ft).contains(Hex(8, 2))
    }

    // --- Range / Radius (p.9) ---

    @Test
    fun `within covers spaces and enclosed terrain up to N away, not the centre or off the map`() {
        val map = strip(holes = setOf(Hex(1, 1)))
        val g = BoardGeometry.of(map)
        assertThat(BoardRules.within(g, Hex(0, 0), 1)).containsExactly(Hex(1, 0), Hex(0, 1))
        assertThat(BoardRules.within(g, Hex(1, 2), 1)).contains(Hex(1, 1))
        // 36 hexes within 3, minus the 8 above and below the five rows.
        assertThat(BoardRules.within(g, Hex(4, 2), 3)).hasSize(28)
    }

    // --- Hero respawn (p.7) ---

    @Test
    fun `heroes respawn on an empty hero spawn point, else on the nearest empty space next to one`() {
        val spawns = listOf(Hex(0, 0), Hex(0, 2), Hex(0, 4)).associateWith { spawn(Team.RED, SpawnKind.HERO) }
        val map = strip(spawns = spawns)
        val g = BoardGeometry.of(map)
        assertThat(BoardRules.heroRespawn(g, state(map), Team.RED)).containsExactlyElementsIn(spawns.keys)
        val full = spawns.keys.map { hero(Team.RED, it) }
        val adjacent = setOf(Hex(1, 0), Hex(0, 1), Hex(1, 1), Hex(1, 2), Hex(0, 3), Hex(1, 3), Hex(1, 4))
        assertThat(
            BoardRules.heroRespawn(g, state(map, *full.toTypedArray()), Team.RED),
        ).containsExactlyElementsIn(adjacent)
        val packed = full + adjacent.map { minion(Team.BLUE, it) }
        val ring2 = BoardRules.heroRespawn(g, state(map, *packed.toTypedArray()), Team.RED)
        assertThat(ring2).isNotEmpty()
        assertThat(ring2.all { hex -> spawns.keys.minOf { HexGrid.distance(it, hex) } == 2 }).isTrue()
        assertThat(BoardRules.heroRespawn(g, state(map), Team.BLUE)).isEmpty()
    }

    // --- Minions outside of the Battle Zone (p.18) ---

    @Test
    fun `a minion outside the Battle Zone goes back by the shortest path of empty spaces`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val m = minion(Team.RED, Hex(4, 2))
        assertThat(BoardRules.returnToBattleZone(g, state(map, m), Hex(4, 2), "b"))
            .containsExactly(Hex(6, 0), Hex(6, 1), Hex(6, 2))
        assertThat(BoardRules.returnToBattleZone(g, state(map, m), Hex(4, 2), "a")).containsExactly(Hex(4, 2))
    }

    @Test
    fun `with no path it goes to the nearest empty space of the Battle Zone`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val m = minion(Team.RED, Hex(4, 2))
        val wall = (0..4).map { token(Hex(5, it)) } + listOf(minion(Team.BLUE, Hex(6, 0)), minion(Team.BLUE, Hex(6, 1)))
        assertThat(BoardRules.returnToBattleZone(g, state(map, m, *wall.toTypedArray()), Hex(4, 2), "b"))
            .containsExactly(Hex(6, 2))
    }

    // --- Push (p.18) ---

    @Test
    fun `a push moves the target straight away, as far as it can`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val target = minion(Team.BLUE, Hex(4, 2))
        assertThat(BoardRules.push(g, state(map, target), Hex(3, 2), Hex(4, 2), 2)).isEqualTo(Hex(6, 2))
        val blocked = state(map, target, token(Hex(6, 2)))
        assertThat(BoardRules.push(g, blocked, Hex(3, 2), Hex(4, 2), 2)).isEqualTo(Hex(5, 2))
        assertThat(
            BoardRules.push(g, state(map, target, token(Hex(5, 2))), Hex(3, 2), Hex(4, 2), 2),
        ).isEqualTo(Hex(4, 2))
        assertThat(BoardRules.push(g, state(map, target), Hex(3, 2), Hex(4, 2), 0)).isEqualTo(Hex(4, 2))
    }

    @Test
    fun `a push ignoring obstacles ends on the farthest empty space it reaches`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val s = state(map, minion(Team.BLUE, Hex(4, 2)), token(Hex(6, 2)))
        assertThat(BoardRules.push(g, s, Hex(3, 2), Hex(4, 2), 3, ignoreObstacles = true)).isEqualTo(Hex(7, 2))
        assertThat(BoardRules.push(g, s, Hex(3, 2), Hex(4, 2), 2, ignoreObstacles = true)).isEqualTo(Hex(5, 2))
    }

    @Test
    fun `a push stops at the board's edge and needs a straight line`() {
        val map = strip()
        val g = BoardGeometry.of(map)
        val s = state(map, minion(Team.BLUE, Hex(13, 2)))
        assertThat(BoardRules.push(g, s, Hex(12, 2), Hex(13, 2), 3)).isEqualTo(Hex(14, 2))
        assertThat(BoardRules.push(g, s, Hex(12, 1), Hex(13, 2), 3)).isEqualTo(Hex(13, 2))
        // Pushed from two spaces away along a line: still directly away.
        assertThat(BoardRules.push(g, s, Hex(11, 2), Hex(13, 2), 1)).isEqualTo(Hex(14, 2))
    }
}
