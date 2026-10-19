package dev.quinton.goa.feature.board.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** How the map is turned on screen: the Lane runs up the screen in portrait, left to right in landscape. */
enum class MapOrientation { PORTRAIT, LANDSCAPE }

/**
 * Where every hex of a map sits on screen, in map units (1 = a hex's corner radius), with the map's
 * lane turned to [orientation] and [bottomTeam]'s Throne Zone at the bottom (portrait) or left
 * (landscape). Pure geometry: pointy-top axial hexes, rotated in 30° steps so hexes stay regular.
 * Coordinates start at (0, 0) at the top left of the map's bounds (plus [PADDING]).
 */
class MapLayout(map: BoardMap, val orientation: MapOrientation, val bottomTeam: Team) {
    /** Rotation applied to the plain pointy-top layout, in degrees (a multiple of 30). */
    val rotationDegrees: Int

    private val cosR: Float
    private val sinR: Float
    private val originX: Float
    private val originY: Float

    val width: Float
    val height: Float

    /** Every hex drawn: the spaces, the printed terrain and the ring of water around the map (p.19). */
    val hexes: List<Hex> = map.spaces.map { it.hex } + map.terrain + BoardGeometry.of(map).edge

    init {
        rotationDegrees = rotationFor(map, orientation, bottomTeam)
        val radians = rotationDegrees * PI / 180.0
        cosR = cos(radians).toFloat()
        sinR = sin(radians).toFloat()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        hexes.forEach { hex ->
            rawCorners(hex).forEach { (x, y) ->
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        if (hexes.isEmpty()) {
            minX = 0f
            minY = 0f
            maxX = 1f
            maxY = 1f
        }
        originX = minX - PADDING
        originY = minY - PADDING
        width = maxX - minX + 2 * PADDING
        height = maxY - minY + 2 * PADDING
    }

    /** Centre of [hex] in map units. */
    fun center(hex: Hex): Offset {
        val (x, y) = rotate(axialX(hex), axialY(hex))
        return Offset(x - originX, y - originY)
    }

    /** The six corners of [hex], clockwise, in map units. */
    fun corners(hex: Hex): List<Offset> = rawCorners(hex).map { (x, y) -> Offset(x - originX, y - originY) }

    /**
     * The edge [hex] shares with its neighbour in [HexGrid.DIRECTIONS] [direction], as two points
     * in map units.
     */
    fun edge(hex: Hex, direction: Int): Pair<Offset, Offset> {
        val c = center(hex)
        val neighbour = center(hex + HexGrid.DIRECTIONS[direction])
        // The shared edge is perpendicular to the line between centres, half a hex out.
        val mid = (c + neighbour) / 2f
        val dir = neighbour - c
        val length = dir.getDistance().takeIf { it > 0f } ?: 1f
        val perpendicular = Offset(-dir.y / length, dir.x / length) * (HALF_EDGE)
        return (mid + perpendicular) to (mid - perpendicular)
    }

    /** The hex under [point] (map units), whether or not it is part of the map. */
    fun hexAt(point: Offset): Hex {
        val (x, y) = unrotate(point.x + originX, point.y + originY)
        // Inverse of the pointy-top layout, then cube rounding.
        val q = (SQRT3 / 3f * x - y / 3f)
        val r = (2f / 3f * y)
        return roundHex(q, r)
    }

    /** The rectangle (map units) around the centres of [hexes], [pad] units wider on every side. */
    fun bounds(hexes: Collection<Hex>, pad: Float = 1f): Rect? {
        if (hexes.isEmpty()) return null
        val centres = hexes.map(::center)
        return Rect(
            centres.minOf { it.x } - pad,
            centres.minOf { it.y } - pad,
            centres.maxOf { it.x } + pad,
            centres.maxOf { it.y } + pad,
        )
    }

    /**
     * What the board opens on (map units): the Lane between both teams' hero spawn areas, every
     * Lane's starting Battle Zone included.
     */
    fun laneRegion(map: BoardMap, geometry: BoardGeometry): Rect? {
        val hexes = Team.entries.flatMap(geometry::heroSpawns) +
            map.lanes.flatMap { geometry.zoneSpaces[it.startingBattleZone].orEmpty() }
        return bounds(hexes, pad = LANE_PAD)
    }

    /** Distance from [point] to the centre of [hex], in map units. */
    fun distanceTo(point: Offset, hex: Hex): Float = (center(hex) - point).getDistance()

    private fun axialX(hex: Hex): Float = SQRT3 * (hex.q + hex.r / 2f)

    private fun axialY(hex: Hex): Float = 1.5f * hex.r

    private fun rawCorners(hex: Hex): List<Pair<Float, Float>> {
        val cx = axialX(hex)
        val cy = axialY(hex)
        return (0 until 6).map { i ->
            val angle = (60.0 * i - 30.0) * PI / 180.0
            rotate(cx + cos(angle).toFloat(), cy + sin(angle).toFloat())
        }
    }

    private fun rotate(x: Float, y: Float): Pair<Float, Float> = (x * cosR - y * sinR) to (x * sinR + y * cosR)

    private fun unrotate(x: Float, y: Float): Pair<Float, Float> = (x * cosR + y * sinR) to (-x * sinR + y * cosR)

    companion object {
        private val SQRT3 = sqrt(3f)

        /** Half the length of a hex edge (corner radius 1). */
        private const val HALF_EDGE = 0.5f

        /** Space around the outermost hexes, in map units. */
        const val PADDING = 0.6f

        /** Room around [laneRegion]'s hexes: a hero piece on a spawn point is drawn whole. */
        private const val LANE_PAD = 1.2f

        /**
         * The rotation that points the line from [bottomTeam]'s Throne Zone to the other team's up
         * the screen (portrait) or to the right (landscape), snapped to 30° steps.
         */
        fun rotationFor(map: BoardMap, orientation: MapOrientation, bottomTeam: Team): Int {
            val geometry = BoardGeometry.of(map)
            val from = zoneCentre(map, geometry, bottomTeam)
            val to = zoneCentre(map, geometry, bottomTeam.opponent)
            val target = if (orientation == MapOrientation.PORTRAIT) -90.0 else 0.0
            if (from == null || to == null || from == to) return if (orientation == MapOrientation.PORTRAIT) -90 else 0
            val angle = atan2((to.second - from.second).toDouble(), (to.first - from.first).toDouble()) * 180.0 / PI
            val snapped = ((target - angle) / 30.0).roundToInt() * 30
            return ((snapped % 360) + 360) % 360
        }

        private fun zoneCentre(map: BoardMap, geometry: BoardGeometry, team: Team): Pair<Float, Float>? {
            val zone = map.zones.firstOrNull { it.kind == ZoneKind.THRONE && it.team == team }?.id ?: return null
            val hexes = geometry.zoneSpaces[zone].orEmpty().ifEmpty { return null }
            val x = hexes.map { SQRT3 * (it.q + it.r / 2f) }.average().toFloat()
            val y = hexes.map { 1.5f * it.r }.average().toFloat()
            return x to y
        }

        private fun roundHex(q: Float, r: Float): Hex {
            val s = -q - r
            var rq = q.roundToInt()
            var rr = r.roundToInt()
            val rs = s.roundToInt()
            val dq = abs(rq - q)
            val dr = abs(rr - r)
            val ds = abs(rs - s)
            if (dq > dr && dq > ds) {
                rq = -rr - rs
            } else if (dr > ds) {
                rr = -rq - rs
            }
            return Hex(rq, rr)
        }
    }
}
