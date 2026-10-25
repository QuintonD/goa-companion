package dev.quinton.goa.feature.board.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Where every hex of a map sits on screen, in map units (1 = a hex's corner radius), with the map's
 * lane turned to [orientation] and [bottomTeam]'s Throne Zone at the bottom (portrait) or left
 * (landscape): the shared [HexLayout] (core:play, the web app's board draws from it too) in
 * Compose's geometry types.
 */
class MapLayout(map: BoardMap, val orientation: MapOrientation, val bottomTeam: Team) {
    private val hexLayout = HexLayout(map, orientation, bottomTeam)

    /** Rotation applied to the plain pointy-top layout, in degrees (a multiple of 30). */
    val rotationDegrees: Int get() = hexLayout.rotationDegrees

    val width: Float get() = hexLayout.width
    val height: Float get() = hexLayout.height

    /** Every hex drawn: the spaces, the printed terrain and the ring of water around the map (p.19). */
    val hexes: List<Hex> get() = hexLayout.hexes

    /** Centre of [hex] in map units. */
    fun center(hex: Hex): Offset = hexLayout.center(hex).offset

    /** The six corners of [hex], clockwise, in map units. */
    fun corners(hex: Hex): List<Offset> = hexLayout.corners(hex).map { it.offset }

    /** The edge [hex] shares with its neighbour in direction [direction], as two points in map units. */
    fun edge(hex: Hex, direction: Int): Pair<Offset, Offset> =
        hexLayout.edge(hex, direction).let { (a, b) -> a.offset to b.offset }

    /** The hex under [point] (map units), whether or not it is part of the map. */
    fun hexAt(point: Offset): Hex = hexLayout.hexAt(MapPoint(point.x, point.y))

    /** The rectangle (map units) around the centres of [hexes], [pad] units wider on every side. */
    fun bounds(hexes: Collection<Hex>, pad: Float = 1f): Rect? = hexLayout.bounds(hexes, pad)?.rect

    /**
     * What the board opens on (map units): the Lane between both teams' hero spawn areas, every
     * Lane's starting Battle Zone included.
     */
    fun laneRegion(map: BoardMap, geometry: BoardGeometry): Rect? = hexLayout.laneRegion(map, geometry)?.rect

    /** Distance from [point] to the centre of [hex], in map units. */
    fun distanceTo(point: Offset, hex: Hex): Float = (center(hex) - point).getDistance()

    companion object {
        /** Space around the outermost hexes, in map units. */
        const val PADDING = HexLayout.PADDING

        fun rotationFor(map: BoardMap, orientation: MapOrientation, bottomTeam: Team): Int =
            HexLayout.rotationFor(map, orientation, bottomTeam)
    }
}

private val MapPoint.offset: Offset get() = Offset(x, y)

private val MapRect.rect: Rect get() = Rect(left, top, right, bottom)
