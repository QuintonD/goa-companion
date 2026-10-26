package dev.quinton.goa.web.play.map

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The board drawn as SVG markup in map units (1 = a hex's corner radius), the phone's board look
 * (feature:board MapLayers, BoardGlyphs) in the web app's CSS colours (goa.css "play": --map-*).
 * The map itself and the overlays are markup strings set once per change (hundreds of hexes, no
 * per-hex component); pieces are components (BoardSvg) so they can glide from space to space.
 */

/** Original board glyphs on the 24-unit grid (design/board/glyph_*.svg, as the phone draws them). */
enum class Glyph(val path: String) {
    HERO("M3.5 17.5L3.5 7.5L8.3 11.6L12 4L15.7 11.6L20.5 7.5L20.5 17.5ZM3.5 19.2H20.5V21.5H3.5Z"),
    MELEE("M12 2L14.6 5.2V13.6H17.8V16H13.3V19.6L12 22L10.7 19.6V16H6.2V13.6H9.4V5.2Z"),
    RANGED("M20.5 3.5L19.3 12.1L16.3 9.1L7.4 18L6 16.6L14.9 7.7L11.9 4.7ZM3 21L3.6 16.4L7.6 20.4Z"),
    HEAVY("M12 2.5L20 5.5V11.5C20 16.4 16.6 19.8 12 21.5C7.4 19.8 4 16.4 4 11.5V5.5Z"),
    ;

    companion object {
        fun of(type: MinionType): Glyph = when (type) {
            MinionType.MELEE -> MELEE
            MinionType.RANGED -> RANGED
            MinionType.HEAVY -> HEAVY
        }

        fun of(kind: SpawnKind): Glyph = when (kind) {
            SpawnKind.HERO -> HERO
            SpawnKind.MELEE -> MELEE
            SpawnKind.RANGED -> RANGED
            SpawnKind.HEAVY -> HEAVY
        }
    }
}

/** A number for SVG markup: two decimals at most. */
internal fun n(value: Float): String {
    val r = (value * 100f).roundToInt()
    return if (r % 100 == 0) (r / 100).toString() else (r / 100.0).toString()
}

internal fun points(corners: List<MapPoint>): String = corners.joinToString(" ") { "${n(it.x)},${n(it.y)}" }

/** A hex's polygon, its corners pulled towards its centre by [scale]. */
internal fun hexPoints(layout: HexLayout, hex: Hex, scale: Float = 1f): String {
    val c = layout.center(hex)
    return points(layout.corners(hex).map { if (scale == 1f) it else c + (it - c) * scale })
}

/** [glyph] centred on [at], [size] map units square, with [cls] (its fill). */
internal fun glyph(glyph: Glyph, at: MapPoint, size: Float, cls: String): String {
    val s = size / GLYPH_GRID
    return "<path class=\"$cls\" d=\"${glyph.path}\" transform=\"translate(${n(
        at.x - size / 2f,
    )} ${n(at.y - size / 2f)}) " +
        "scale(${n4(s)})\"/>"
}

private fun n4(value: Float): String = ((value * 10_000f).roundToInt() / 10_000.0).toString()

/** The team's shape (never colour alone): a diamond for Red, a disc for Blue. */
internal fun teamMark(team: Team, at: MapPoint, size: Float, cls: String): String {
    val r = size / 2f
    return if (team == Team.RED) {
        "<polygon class=\"$cls\" points=\"${n(
            at.x,
        )},${n(at.y - r)} ${n(at.x + r)},${n(at.y)} ${n(at.x)},${n(at.y + r)} " +
            "${n(at.x - r)},${n(at.y)}\"/>"
    } else {
        "<circle class=\"$cls\" cx=\"${n(at.x)}\" cy=\"${n(at.y)}\" r=\"${n(r * 0.88f)}\"/>"
    }
}

private fun teamSlug(team: Team) = if (team == Team.RED) "red" else "blue"

/** The official Spawn point symbol's path (rulebook p.6), as the phone's OfficialArt. */
fun spawnArt(spawn: SpawnPoint): String = when (val minion = spawn.kind.minion) {
    null -> "official/board/spawn-hero-${teamSlug(spawn.team)}.webp"
    else -> "official/board/spawn-${minion.name.lowercase()}-${teamSlug(spawn.team)}.webp"
}

/**
 * The map itself: the water ring, Zone fills, the gaps between spaces, printed terrain, Zone
 * borders (the Battle Zones' in teal) and the Spawn points as markings (the official symbols faded,
 * a minion point's type in its team's outline). Built once per map, layout and Battle Zones.
 */
fun mapMarkup(map: BoardMap, layout: HexLayout, battleZones: Set<String>): String = buildString {
    val geometry = BoardGeometry.of(map)
    val laneIndex = map.lanes.flatMap { lane -> lane.zones.mapIndexed { i, zone -> zone to i } }.toMap()
    geometry.edge.forEach { append("<polygon class=\"m-water\" points=\"${hexPoints(layout, it)}\"/>") }
    map.spaces.forEach { space ->
        val zone = geometry.zones[space.zone]
        val fill = when {
            space.zone in battleZones -> "m-battle"
            zone?.kind == ZoneKind.THRONE -> "m-throne-${teamSlug(zone.team ?: Team.RED)}"
            zone?.kind == ZoneKind.SIDE -> "m-side"
            (laneIndex[space.zone] ?: 0) % 2 == 0 -> "m-lane-a"
            else -> "m-lane-b"
        }
        append("<polygon class=\"$fill m-space\" points=\"${hexPoints(layout, space.hex)}\"/>")
    }
    map.terrain.forEachIndexed { i, hex -> append(terrain(layout, hex, hex.q * 7 + hex.r * 13 + i)) }
    val borders = StringBuilder()
    val battle = StringBuilder()
    map.spaces.forEach { space ->
        HexGrid.DIRECTIONS.indices.forEach { d ->
            val neighbour = geometry.spaces[space.hex + HexGrid.DIRECTIONS[d]]
            if (neighbour == null || neighbour.zone != space.zone) {
                val (a, b) = layout.edge(space.hex, d)
                (if (space.zone in battleZones) battle else borders).append("M${n(a.x)} ${n(a.y)}L${n(b.x)} ${n(b.y)}")
            }
        }
    }
    append("<path class=\"m-border\" d=\"$borders\"/>")
    append("<path class=\"m-border-battle\" d=\"$battle\"/>")
    map.spaces.forEach { space -> space.spawn?.let { append(spawn(it, layout.center(space.hex))) } }
}

/** Printed terrain: a dark rock with a faceted top (original; no map art is used). */
private fun terrain(layout: HexLayout, hex: Hex, seed: Int): String {
    val c = layout.center(hex)
    val inner = (0 until ROCK_CORNERS).map { i ->
        val wobble = 0.5f + 0.12f * (((seed * 31 + i * 17) % ROCK_CORNERS) / 6f)
        val angle = (i * 360.0 / ROCK_CORNERS + seed % 40) * PI / 180.0
        MapPoint(c.x + wobble * cos(angle).toFloat(), c.y + wobble * sin(angle).toFloat())
    }
    val top = c + MapPoint(-0.08f, -0.12f)
    return "<polygon class=\"m-terrain\" points=\"${hexPoints(layout, hex)}\"/>" +
        "<polygon class=\"m-rock\" points=\"${points(inner)}\"/>" +
        "<path class=\"m-ridge\" d=\"M${n(top.x)} ${n(top.y)}L${n(inner[1].x)} ${n(inner[1].y)}" +
        "M${n(top.x)} ${n(top.y)}L${n(inner[4].x)} ${n(inner[4].y)}\"/>"
}

/**
 * A printed Spawn point: a marking under the pieces, never read as a unit (the official symbol
 * faded, smaller than a piece, so a piece standing on it covers it all).
 */
private fun spawn(spawn: SpawnPoint, at: MapPoint): String = buildString {
    val side = SPAWN_SIDE
    append(
        "<image class=\"m-spawn\" href=\"${spawnArt(
            spawn,
        )}\" x=\"${n(at.x - side / 2f)}\" y=\"${n(at.y - side / 2f)}\" " +
            "width=\"${n(side)}\" height=\"${n(side)}\"/>",
    )
    if (spawn.kind != SpawnKind.HERO) {
        val mark = at + MapPoint(MARK_OFFSET, MARK_OFFSET)
        val team = teamSlug(spawn.team)
        append(teamMark(spawn.team, mark, MARK_SIZE, "m-spawn-mark m-stroke-$team"))
        append(glyph(Glyph.of(spawn.kind), mark, MARK_SIZE * 0.6f, "m-fill-$team m-spawn-glyph"))
    }
}

/** What an overlay mark looks like (each with a pattern or number, never colour alone). */
enum class MarkStyle {
    /** Movement: a green fill and outline, the fewest spaces needed as a number. */
    MOVEMENT,

    /** Fast Travel: bronze hatching. */
    FAST_TRAVEL,

    /** Range (and a decision's highlighted spaces): an inner violet outline. */
    RANGE,

    /** Radius: three dots. */
    RADIUS,

    /** A space to go to or an option to choose: a dashed teal outline. */
    TARGET,

    /** The selected piece's space, or the chosen option: a bright outline on a dark halo. */
    SELECTION,

    /** The acting hero's space: a bronze outline. */
    ACTING,

    /** A puzzle's hint: a bronze ring of dashes outside the space. */
    HINT,

    /** The measured space: a bronze outline. */
    MEASURE,

    /** The space under a dragged piece. */
    DROP,
}

/** One overlay mark on [hex]; [number] is drawn in it (Movement's fewest spaces). */
data class HexMark(val hex: Hex, val style: MarkStyle, val number: Int? = null)

/** A line over the map: a move's path (dots at the steps), a measure ([dashed] when not straight). */
data class MapLine(val hexes: List<Hex>, val dashed: Boolean = false, val stepDots: Boolean = false)

/** The overlays, in the phone's drawing order (radius, range, Fast Travel, movement, targets, …). */
fun marksMarkup(layout: HexLayout, marks: List<HexMark>, lines: List<MapLine>): String = buildString {
    val byStyle = marks.groupBy { it.style }
    fun each(style: MarkStyle, draw: (HexMark) -> Unit) = byStyle[style].orEmpty().forEach(draw)
    each(MarkStyle.RADIUS) { m ->
        val c = layout.center(m.hex)
        append("<polygon class=\"o-radius-fill\" points=\"${hexPoints(layout, m.hex, 0.92f)}\"/>")
        listOf(-90f, 30f, 150f).forEach { deg ->
            val a = deg * PI.toFloat() / 180f
            append(
                "<circle class=\"o-radius-dot\" cx=\"${n(
                    c.x + cos(a) * 0.42f,
                )}\" cy=\"${n(c.y + sin(a) * 0.42f)}\" r=\"0.09\"/>",
            )
        }
    }
    each(MarkStyle.RANGE) { m ->
        append("<polygon class=\"o-range-fill\" points=\"${hexPoints(layout, m.hex, 0.92f)}\"/>")
        append("<polygon class=\"o-range\" points=\"${hexPoints(layout, m.hex, 0.7f)}\"/>")
    }
    each(MarkStyle.FAST_TRAVEL) { m ->
        append("<polygon class=\"o-fast\" points=\"${hexPoints(layout, m.hex, 0.92f)}\"/>")
    }
    each(MarkStyle.MOVEMENT) { m ->
        val c = layout.center(m.hex)
        append("<polygon class=\"o-move\" points=\"${hexPoints(layout, m.hex, 0.9f)}\"/>")
        m.number?.let {
            append("<text class=\"o-move-n\" transform=\"translate(${n(c.x)} ${n(c.y)}) scale(0.078)\">$it</text>")
        }
    }
    each(MarkStyle.TARGET) { m ->
        append("<polygon class=\"o-target-fill\" points=\"${hexPoints(layout, m.hex, 0.92f)}\"/>")
        append("<polygon class=\"o-target\" points=\"${hexPoints(layout, m.hex, 0.82f)}\"/>")
    }
    each(MarkStyle.DROP) { m -> append("<polygon class=\"o-drop\" points=\"${hexPoints(layout, m.hex, 0.96f)}\"/>") }
    each(MarkStyle.ACTING) { m ->
        append("<polygon class=\"o-halo-wide\" points=\"${hexPoints(layout, m.hex, 0.98f)}\"/>")
        append("<polygon class=\"o-acting\" points=\"${hexPoints(layout, m.hex, 0.98f)}\"/>")
    }
    lines.forEach { line -> append(lineMarkup(layout, line)) }
    each(MarkStyle.HINT) { m ->
        append("<polygon class=\"o-halo-hint\" points=\"${hexPoints(layout, m.hex)}\"/>")
        append("<polygon class=\"o-hint\" points=\"${hexPoints(layout, m.hex)}\"/>")
    }
    each(MarkStyle.SELECTION) { m ->
        append("<polygon class=\"o-halo-sel\" points=\"${hexPoints(layout, m.hex)}\"/>")
        append("<polygon class=\"o-sel\" points=\"${hexPoints(layout, m.hex)}\"/>")
    }
    each(MarkStyle.MEASURE) { m -> append("<polygon class=\"o-measure\" points=\"${hexPoints(layout, m.hex)}\"/>") }
}

private fun lineMarkup(layout: HexLayout, line: MapLine): String {
    if (line.hexes.size < 2) return ""
    val d = line.hexes.map(layout::center).mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${n(p.x)} ${n(p.y)}" }
        .joinToString("")
    val dots = if (line.stepDots) {
        line.hexes.drop(1).dropLast(1).joinToString("") { hex ->
            val c = layout.center(hex)
            "<circle class=\"o-line-dot\" cx=\"${n(c.x)}\" cy=\"${n(c.y)}\" r=\"0.12\"/>"
        }
    } else {
        ""
    }
    val dashed = if (line.dashed) " dashed" else ""
    return "<path class=\"o-line-halo\" d=\"$d\"/><path class=\"o-line$dashed\" d=\"$d\"/>$dots"
}

/** The shared definitions: the round clip of a hero's portrait, Fast Travel's hatching. */
const val MAP_DEFS: String =
    "<clipPath id=\"goa-piece-clip\" clipPathUnits=\"objectBoundingBox\">" +
        "<circle cx=\"0.5\" cy=\"0.5\" r=\"0.5\"/></clipPath>" +
        "<pattern id=\"goa-hatch\" patternUnits=\"userSpaceOnUse\" width=\"0.36\" height=\"0.36\" " +
        "patternTransform=\"rotate(45)\"><rect class=\"o-hatch-bg\" width=\"0.36\" height=\"0.36\"/>" +
        "<line class=\"o-hatch\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"0.36\"/></pattern>"

private const val GLYPH_GRID = 24f
private const val ROCK_CORNERS = 7
private const val SPAWN_SIDE = 1.0f
private const val MARK_OFFSET = 0.3f
private const val MARK_SIZE = 0.34f
