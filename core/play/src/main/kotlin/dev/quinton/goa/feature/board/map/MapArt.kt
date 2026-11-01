package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.Hex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
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

/**
 * Brightness and saturation outside the light (rulebook p.8), on the phone and the web alike: mock-up 01's
 * brightness of .58 on a picture the importer has already darkened by 8 %.
 */
const val DIM_BRIGHTNESS = 0.63f
const val DIM_SATURATION = 0.5f

/**
 * What the canvas past a painting shows: the picture's edge ([BoardMapArt.sea]) as the dimmed picture draws it,
 * so the picture has no visible edge however the map is turned. `#RRGGBB`.
 */
fun BoardMapArt.canvasSea(): String {
    val rgb = sea.removePrefix("#").toInt(HEX_RADIX)
    val r = (rgb shr RED_SHIFT) and MAX_BYTE
    val g = (rgb shr GREEN_SHIFT) and MAX_BYTE
    val b = rgb and MAX_BYTE
    val grey = LUMA_R * r + LUMA_G * g + LUMA_B * b
    fun dim(channel: Int): String {
        val value = ((grey + (channel - grey) * DIM_SATURATION) * DIM_BRIGHTNESS).roundToInt().coerceIn(0, MAX_BYTE)
        return value.toString(HEX_RADIX).padStart(2, '0').uppercase()
    }
    return "#${dim(r)}${dim(g)}${dim(b)}"
}

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
            if (start in seen) return@forEach
            val rock = connectedTerrain(geometry, start)
            seen += rock
            val closed = rock.all { hex -> neighbours(hex).all { it in rock || it in spaces } }
            if (closed) enclosed += rock
        }
        return enclosed
    }

    /** The terrain hexes joined to [start]: one rock on the mat. */
    private fun connectedTerrain(geometry: BoardGeometry, start: Hex): Set<Hex> {
        val rock = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            neighbours(queue.removeFirst()).filter(geometry::isTerrain).filter(rock::add).forEach(queue::addLast)
        }
        return rock
    }

    private fun neighbours(hex: Hex): List<Hex> = HexGrid.DIRECTIONS.map { hex + it }
}

private val SQRT3 = sqrt(3f)
private const val HEX_RADIX = 16
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val MAX_BYTE = 255

/** The same weights as the dimmed picture's on both platforms (the sRGB luma). */
private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
