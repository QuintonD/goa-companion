package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.Hex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The painted map (ADR-053): where the painting lies under a HexLayout, and which spaces are in the
 * light. Shared by the phone's board and the web app's.
 */

/**
 * Where a painting lies on a [HexLayout]: from image pixels to map units, the 2 × 3 matrix of CSS's
 * `matrix(a, b, c, d, e, f)`, so `x = a·px + c·py + e`, `y = b·px + d·py + f`. It carries the layout's
 * rotation and origin, so the printed spaces sit under their hexes in any orientation and after a Flip.
 */
data class ArtPlacement(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    /** The image pixel ([px], [py]) in map units. */
    fun toMap(px: Float, py: Float): MapPoint = MapPoint(a * px + c * py + e, b * px + d * py + f)
}

/** The centre of [hex] in the painting's pixels. */
fun BoardMapArt.pixelOf(hex: Hex): MapPoint = pixelAt(SQRT3 * (hex.q + hex.r / 2f), 1.5f * hex.r)

/** The six corners of [hex] in the painting's pixels: the shape that lights a space in the image's own frame. */
fun BoardMapArt.cornersInPixels(hex: Hex): List<MapPoint> {
    val x = SQRT3 * (hex.q + hex.r / 2f)
    val y = 1.5f * hex.r
    return (0 until 6).map { i ->
        val angle = (60.0 * i - 30.0) * PI / 180.0
        pixelAt(x + cos(angle).toFloat(), y + sin(angle).toFloat())
    }
}

private fun BoardMapArt.pixelAt(x: Float, y: Float): MapPoint =
    MapPoint(((x - originX) * pxPerUnit).toFloat(), ((y - originY) * pxPerUnit).toFloat())

/** Brightness and saturation outside the light (mock-up 01, rulebook p.8), on the phone and the web alike. */
const val DIM_BRIGHTNESS = 0.58f
const val DIM_SATURATION = 0.5f

/** The picture's edge fades into [BoardMapArt.sea] over this many map units: its edge colours vary too much to match. */
const val ART_FEATHER = 1f

/**
 * A lit part of the map (rulebook p.8): [hexes] in the light, and [edges], the sides on its outer edge
 * as a hex and a direction of [HexGrid.DIRECTIONS].
 */
data class LitArea(val hexes: Set<Hex>, val edges: List<Pair<Hex, Int>>) {
    operator fun plus(other: LitArea) = LitArea(hexes + other.hexes, edges + other.edges)

    companion object {
        val EMPTY = LitArea(emptySet(), emptyList())
    }
}

/** What is in the light, for the base map and the overlays alike. */
object MapLighting {
    /** Each of [zones] lit on its own, so a double-lane map outlines each Lane's Battle Zone. */
    fun zones(map: BoardMap, zones: Collection<String>): LitArea {
        val geometry = BoardGeometry.of(map)
        return zones.distinct().fold(LitArea.EMPTY) { lit, zone ->
            lit + spaces(map, geometry.zoneSpaces[zone].orEmpty().toSet())
        }
    }

    /**
     * [spaces] in the light, with any rock they entirely enclose: such a rock belongs to the lit
     * ground, so no outline runs around it. A rock that also touches anything else stays dark.
     */
    fun spaces(map: BoardMap, spaces: Set<Hex>): LitArea {
        if (spaces.isEmpty()) return LitArea.EMPTY
        val lit = spaces + enclosedTerrain(BoardGeometry.of(map), spaces)
        val edges = lit.flatMap { hex ->
            HexGrid.DIRECTIONS.indices.filter { hex + HexGrid.DIRECTIONS[it] !in lit }.map { hex to it }
        }
        return LitArea(lit, edges)
    }

    private fun enclosedTerrain(geometry: BoardGeometry, spaces: Set<Hex>): Set<Hex> {
        val seen = HashSet<Hex>()
        val enclosed = HashSet<Hex>()
        spaces.flatMap(::neighbours).filter(geometry::isTerrain).forEach { start ->
            if (!seen.add(start)) return@forEach
            val group = mutableListOf(start)
            var closed = true
            var i = 0
            while (i < group.size) {
                neighbours(group[i++]).forEach { n ->
                    when {
                        geometry.isTerrain(n) -> if (seen.add(n)) group += n
                        n !in spaces -> closed = false
                    }
                }
            }
            if (closed) enclosed += group
        }
        return enclosed
    }

    private fun neighbours(hex: Hex): List<Hex> = HexGrid.DIRECTIONS.map { hex + it }
}

private val SQRT3 = sqrt(3f)
