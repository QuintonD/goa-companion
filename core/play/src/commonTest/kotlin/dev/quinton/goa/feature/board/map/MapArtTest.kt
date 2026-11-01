package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.BoardZone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.board.readRepoFile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The painting sits under the hexes on every shipped map, and the light falls on the right spaces. */
class MapArtTest {
    private fun asset(path: String) = readRepoFile("app/src/main/assets/$path")

    private val maps: List<BoardMap> = CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
        CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
    }

    private fun assertNear(expected: MapPoint, actual: MapPoint, what: String) {
        assertTrue(
            abs(expected.x - actual.x) < TOLERANCE && abs(expected.y - actual.y) < TOLERANCE,
            "$what: expected $expected, was $actual",
        )
    }

    /** Portrait, landscape and flipped: a space's pixel lands on HexLayout.center of that space. */
    @Test
    fun placementAgreesWithTheLayoutInEveryOrientation() {
        assertEquals(4, maps.size)
        maps.forEach { map ->
            val art = assertNotNull(map.art, map.id)
            MapOrientation.entries.forEach { orientation ->
                Team.entries.forEach { bottom ->
                    val layout = HexLayout(map, orientation, bottom)
                    val placement = layout.artPlacement(art)
                    map.spaces.forEach { space ->
                        val pixel = art.pixelOf(space.hex)
                        assertNear(
                            layout.center(space.hex),
                            placement.toMap(pixel.x, pixel.y),
                            "${map.id} $orientation $bottom ${space.hex}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun cornersInPixelsAreTheLayoutsCorners() {
        val map = maps.first()
        val art = assertNotNull(map.art)
        val layout = HexLayout(map, MapOrientation.PORTRAIT, Team.RED)
        val placement = layout.artPlacement(art)
        val hex = map.spaces.first().hex
        val corners = layout.corners(hex)
        art.cornersInPixels(hex).forEachIndexed { i, pixel ->
            assertNear(corners[i], placement.toMap(pixel.x, pixel.y), "corner $i")
        }
    }

    /** The canvas past the picture is its edge as the dimmed layer draws it: worked by hand at brightness .63. */
    @Test
    fun theCanvasIsThePicturesEdgeDimmed() {
        val art = BoardMapArt("x", 1, 1, pxPerUnit = 1.0, originX = 0.0, originY = 0.0, sea = "#808080")
        assertEquals("#51" + "5151", art.canvasSea())
        val red = BoardMapArt("x", 1, 1, pxPerUnit = 1.0, originX = 0.0, originY = 0.0, sea = "#FF0000")
        // grey 54.2, halfway to 255 is 154.6, to 0 is 27.1; times .63.
        assertEquals("#61" + "1111", red.canvasSea())
    }

    /** "The whole map" is the land: the ring of water is left out, every space and rock is in. */
    @Test
    fun theExtentHoldsTheLandAndIsTighterThanTheLayout() {
        maps.forEach { map ->
            MapOrientation.entries.forEach { orientation ->
                val layout = HexLayout(map, orientation, Team.RED)
                val extent = layout.extent
                assertTrue(extent.width < layout.width && extent.height < layout.height, map.id)
                (map.spaces.map { it.hex } + map.terrain).flatMap(layout::corners).forEach { corner ->
                    assertTrue(
                        corner.x in extent.left..extent.right && corner.y in extent.top..extent.bottom,
                        "${map.id} $orientation $corner",
                    )
                }
            }
        }
    }

    /** A frame worked by hand: hex (0, 0) is the plain layout's origin, hex (0, 2) is 3 units below it. */
    @Test
    fun aPixelIsWhereTheModelSays() {
        val art = BoardMapArt("x", 100, 100, pxPerUnit = 10.0, originX = -5.0, originY = -5.0, sea = "#000000")
        assertEquals(MapPoint(50f, 50f), art.pixelOf(Hex(0, 0)))
        assertEquals(80f, art.pixelOf(Hex(0, 2)).y)
    }

    @Test
    fun rocksEnclosedByTheZoneAreLitAndNotOutlined() {
        val lit = MapLighting.zones(aroundARock(), listOf("mid"))
        assertTrue(ROCK in lit.hexes)
        assertTrue(lit.edges.none { (hex, d) -> hex + HexGrid.DIRECTIONS[d] == ROCK }, "no outline around the rock")
        assertTrue(lit.edges.none { (hex, _) -> hex == ROCK })
    }

    @Test
    fun aRockThatAlsoTouchesAnotherZoneStaysDark() {
        val map = aroundARock(gap = true)
        val lit = MapLighting.zones(map, listOf("mid"))
        assertFalse(ROCK in lit.hexes)
        assertTrue(lit.edges.any { (hex, d) -> hex + HexGrid.DIRECTIONS[d] == ROCK }, "the rock is outside the light")
    }

    /** Every Lane's Battle Zone is lit on a double-lane map; only those. */
    @Test
    fun doubleLaneLightsEachLanesBattleZone() {
        val map = maps.first { it.lanes.size == 2 }
        val zones = map.lanes.map { it.startingBattleZone }
        val lit = MapLighting.zones(map, zones)
        val geometry = BoardGeometry.of(map)
        zones.forEach { zone -> assertTrue(lit.hexes.containsAll(geometry.zoneSpaces.getValue(zone))) }
        assertTrue(map.spaces.filter { it.hex in lit.hexes }.all { it.zone in zones })
        assertTrue(lit.edges.isNotEmpty())
    }

    /** On every shipped map the outline runs only along the outer edge of the Zone's light. */
    @Test
    fun theOutlineFollowsTheOuterEdgeOnEveryMap() {
        maps.forEach { map ->
            map.lanes.forEach { lane ->
                val lit = MapLighting.zones(map, listOf(lane.startingBattleZone))
                lit.edges.forEach { (hex, d) ->
                    assertFalse(hex + HexGrid.DIRECTIONS[d] in lit.hexes, "${map.id} $hex $d")
                }
            }
        }
    }

    /**
     * A rock with the Zone "mid" all around it and a neighbour rock inside the ring too; [gap] gives one
     * of the ring's spaces to another Zone, so the rock is no longer enclosed by "mid" alone.
     */
    private fun aroundARock(gap: Boolean = false): BoardMap {
        val ring = HexGrid.DIRECTIONS.map { ROCK + it }
        val spaces = ring.mapIndexed { i, hex ->
            BoardSpace(hex.q, hex.r, if (gap && i == 0) "left" else "mid")
        }
        return BoardMap(
            id = "test",
            name = "Test",
            lanes = emptyList(),
            zones = listOf(BoardZone("mid", ZoneKind.LANE), BoardZone("left", ZoneKind.LANE)),
            spaces = spaces,
            terrain = listOf(ROCK),
            printing = 2,
            verified = false,
        )
    }

    private companion object {
        val ROCK = Hex(5, 5)
        const val TOLERANCE = 0.001f
    }
}
