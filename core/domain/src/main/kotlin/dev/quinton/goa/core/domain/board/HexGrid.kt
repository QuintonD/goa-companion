package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.Hex
import kotlin.math.abs

/**
 * Hex-grid arithmetic in axial coordinates (pointy-top). Pure; no board knowledge. The printed
 * grid covers the whole board, water and rocks included (p.19 "Terrain"), so every hex counts
 * as a space for distances.
 */
object HexGrid {
    /** The six directions (p.17 "Direction"), counter-clockwise from east. */
    val DIRECTIONS: List<Hex> = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))

    /**
     * The distance between two spaces in spaces, counted along the shortest path of spaces
     * "including spaces with obstacles" (p.17 "Closer", "Farther / Farthest"; p.18 "Nearest"):
     * the hex distance. Adjacent = 1 (p.9: "a Range/Radius value of 1 ... can only affect adjacent
     * targets"); the same space = 0.
     */
    fun distance(a: Hex, b: Hex): Int = (abs(a.q - b.q) + abs(a.r - b.r) + abs(a.s - b.s)) / 2

    fun neighbors(hex: Hex): List<Hex> = DIRECTIONS.map { hex + it }

    /** p.17 "Adjacent": the two spaces share a border. */
    fun adjacent(a: Hex, b: Hex): Boolean = distance(a, b) == 1

    /** Every hex at distance 1..[radius] from [center] (not [center] itself). */
    fun within(center: Hex, radius: Int): List<Hex> {
        if (radius <= 0) return emptyList()
        val out = ArrayList<Hex>()
        for (dq in -radius..radius) {
            for (dr in maxOf(-radius, -dq - radius)..minOf(radius, -dq + radius)) {
                if (dq != 0 || dr != 0) out += Hex(center.q + dq, center.r + dr)
            }
        }
        return out
    }

    /**
     * The index in [DIRECTIONS] of the straight line (p.17 "Direction", p.19 "Straight Line") from
     * [from] through [to], or null when [to] is not on one of the six lines from [from] (or is [from]).
     */
    fun straightLineDirection(from: Hex, to: Hex): Int? {
        if (from == to) return null
        val dq = to.q - from.q
        val dr = to.r - from.r
        val ds = to.s - from.s
        if (dq != 0 && dr != 0 && ds != 0) return null
        val n = distance(from, to)
        val unit = Hex(dq / n, dr / n)
        return DIRECTIONS.indexOf(unit).takeIf { it >= 0 }
    }

    /**
     * p.19 "Straight Line": "Two units are in a straight line if they are adjacent, or if you can
     * draw a straight line of spaces through spaces occupied by both of those units." A single
     * space counts as a straight line, so a space is in a straight line with itself.
     */
    fun inStraightLine(a: Hex, b: Hex): Boolean = a == b || straightLineDirection(a, b) != null

    /** The hexes from [from] (exclusive) to [to] (inclusive) when they lie on a straight line, else null. */
    fun straightLine(from: Hex, to: Hex): List<Hex>? {
        val dir = straightLineDirection(from, to) ?: return null
        val step = DIRECTIONS[dir]
        return (1..distance(from, to)).map { Hex(from.q + step.q * it, from.r + step.r * it) }
    }
}
