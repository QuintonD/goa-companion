package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.Hex
import kotlin.math.abs

/** Hex-grid arithmetic in axial coordinates (pointy-top). Pure; no board knowledge. */
object HexGrid {
    /** The six directions (p.18 "Direction"), counter-clockwise from east. */
    val DIRECTIONS: List<Hex> = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))

    /**
     * Spaces between [a] and [b] counted along the shortest path, obstacles included (p.18
     * "Closer", "Farther"): the hex distance. Adjacent = 1.
     */
    fun distance(a: Hex, b: Hex): Int = (abs(a.q - b.q) + abs(a.r - b.r) + abs(a.s - b.s)) / 2

    fun neighbors(hex: Hex): List<Hex> = DIRECTIONS.map { hex + it }

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
     * The index in [DIRECTIONS] of the straight line (p.18 "Direction", "Straight Line") from [from]
     * through [to], or null when [to] is not on one of the six lines from [from] (or is [from]).
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

    /** The hexes from [from] (exclusive) to [to] (inclusive) when they lie on a straight line, else null. */
    fun straightLine(from: Hex, to: Hex): List<Hex>? {
        val dir = straightLineDirection(from, to) ?: return null
        val step = DIRECTIONS[dir]
        return (1..distance(from, to)).map { Hex(from.q + step.q * it, from.r + step.r * it) }
    }
}
