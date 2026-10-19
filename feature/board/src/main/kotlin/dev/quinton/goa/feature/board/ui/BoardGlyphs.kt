package dev.quinton.goa.feature.board.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.match.Team
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Original board glyphs on the 24-unit grid. Masters: `design/board/glyph_*.svg` (keep the path
 * data here identical). Hero = crown, melee = sword, ranged = arrow, heavy = shield.
 */
enum class BoardGlyph(pathData: String) {
    HERO("M3.5 17.5L3.5 7.5L8.3 11.6L12 4L15.7 11.6L20.5 7.5L20.5 17.5ZM3.5 19.2H20.5V21.5H3.5Z"),
    MELEE("M12 2L14.6 5.2V13.6H17.8V16H13.3V19.6L12 22L10.7 19.6V16H6.2V13.6H9.4V5.2Z"),
    RANGED("M20.5 3.5L19.3 12.1L16.3 9.1L7.4 18L6 16.6L14.9 7.7L11.9 4.7ZM3 21L3.6 16.4L7.6 20.4Z"),
    HEAVY("M12 2.5L20 5.5V11.5C20 16.4 16.6 19.8 12 21.5C7.4 19.8 4 16.4 4 11.5V5.5Z"),
    ;

    val path: Path by lazy { PathParser().parsePathString(pathData).toPath() }

    companion object {
        fun of(type: MinionType): BoardGlyph = when (type) {
            MinionType.MELEE -> MELEE
            MinionType.RANGED -> RANGED
            MinionType.HEAVY -> HEAVY
        }

        fun of(kind: SpawnKind): BoardGlyph = when (kind) {
            SpawnKind.HERO -> HERO
            SpawnKind.MELEE -> MELEE
            SpawnKind.RANGED -> RANGED
            SpawnKind.HEAVY -> HEAVY
        }
    }
}

private const val GLYPH_GRID = 24f
private val SQRT3 = sqrt(3f)

/** [glyph] centred on [center], [size] px square, filled with [color]. */
fun DrawScope.drawGlyph(glyph: BoardGlyph, center: Offset, size: Float, color: Color) {
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        scale(size / GLYPH_GRID, size / GLYPH_GRID, pivot = Offset.Zero)
    }) {
        drawPath(glyph.path, color)
    }
}

/** The team's shape (docs/DESIGN.md: never colour alone): a diamond for Red, a disc for Blue. */
fun DrawScope.drawTeamMark(team: Team, center: Offset, size: Float, color: Color, halo: Color? = null) {
    val r = size / 2f
    if (team == Team.RED) {
        val path = Path().apply {
            moveTo(center.x, center.y - r)
            lineTo(center.x + r, center.y)
            lineTo(center.x, center.y + r)
            lineTo(center.x - r, center.y)
            close()
        }
        if (halo != null) drawPath(path, halo, style = Stroke(width = size * 0.28f, join = StrokeJoin.Round))
        drawPath(path, color)
    } else {
        if (halo != null) drawCircle(halo, radius = r * 0.88f + size * 0.14f, center = center)
        drawCircle(color, radius = r * 0.88f, center = center)
    }
}

/** A hex polygon from its corners. */
fun hexPath(corners: List<Offset>, scale: Float = 1f, center: Offset? = null): Path = Path().apply {
    corners.forEachIndexed { i, corner ->
        val p = if (center != null && scale != 1f) center + (corner - center) * scale else corner
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** The six corners of a hex of corner radius [radius] around [center] (pointy-top, turned [rotation]°). */
fun hexCorners(center: Offset, radius: Float, rotation: Float = 0f): List<Offset> = (0 until 6).map { i ->
    val angle = (60.0 * i - 30.0 + rotation) * PI / 180.0
    Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}

/**
 * A printed Spawn point: the team's shape as a thin outline, the kind's glyph inside, in the team's
 * colour ([u] = px per hex radius).
 */
fun DrawScope.drawSpawn(spawn: SpawnPoint, center: Offset, u: Float, palette: BoardPalette) {
    val color = palette.team(spawn.team)
    val r = u * 0.56f
    val stroke = Stroke(width = u * 0.07f, join = StrokeJoin.Round)
    if (spawn.team == Team.RED) {
        val path = Path().apply {
            moveTo(center.x, center.y - r)
            lineTo(center.x + r, center.y)
            lineTo(center.x, center.y + r)
            lineTo(center.x - r, center.y)
            close()
        }
        drawPath(path, color.copy(alpha = 0.16f))
        drawPath(path, color.copy(alpha = 0.85f), style = stroke)
    } else {
        drawCircle(color.copy(alpha = 0.16f), radius = r * 0.86f, center = center)
        drawCircle(color.copy(alpha = 0.85f), radius = r * 0.86f, center = center, style = stroke)
    }
    drawGlyph(BoardGlyph.of(spawn.kind), center, u * 0.58f, color)
}

/** Printed terrain: a dark rock with faceted top, original (no map art is used). */
fun DrawScope.drawTerrain(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette, seed: Int) {
    drawPath(hexPath(corners), palette.terrain)
    // A faceted boulder: an irregular inner polygon whose corners wobble by a stable seed.
    val inner = (0 until 7).map { i ->
        val wobble = 0.5f + 0.12f * (((seed * 31 + i * 17) % 7) / 6f)
        val angle = (i * 360.0 / 7 + seed % 40) * PI / 180.0
        Offset(center.x + u * wobble * cos(angle).toFloat(), center.y + u * wobble * sin(angle).toFloat())
    }
    val rock = Path().apply {
        inner.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
    drawPath(rock, palette.terrainFacet)
    val ridge = Stroke(width = u * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(rock, palette.terrainEdge, style = ridge)
    // Two facet lines from the boulder's top to its rim.
    val top = center + Offset(-u * 0.08f, -u * 0.12f)
    drawLine(palette.terrainEdge, top, inner[1], strokeWidth = ridge.width, cap = StrokeCap.Round)
    drawLine(palette.terrainEdge, top, inner[4], strokeWidth = ridge.width, cap = StrokeCap.Round)
}

// ---- Overlay cues (each with a pattern, never colour alone) ------------------------------

/** Movement: a green fill and outline, and the fewest spaces needed as a number. */
fun DrawScope.drawMovementCell(corners: List<Offset>, center: Offset, palette: BoardPalette) {
    drawPath(hexPath(corners, 0.9f, center), palette.movement.copy(alpha = 0.34f))
    drawPath(
        hexPath(corners, 0.9f, center),
        palette.movement,
        style = Stroke(
            width =
                (corners[0] - center).getDistance() * 0.06f,
        ),
    )
}

/** Fast Travel: diagonal bronze hatching. */
fun DrawScope.drawFastTravelCell(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette) {
    val path = hexPath(corners, 0.92f, center)
    drawPath(path, palette.fastTravel.copy(alpha = 0.12f))
    clipPath(path) {
        val step = u * 0.36f
        var x = -u * 2f
        while (x < u * 2f) {
            drawLine(
                palette.fastTravel.copy(alpha = 0.85f),
                center + Offset(x - u, -u),
                center + Offset(x + u, u),
                strokeWidth = u * 0.07f,
            )
            x += step
        }
    }
}

/** Range: an inner outline. */
fun DrawScope.drawRangeCell(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette) {
    drawPath(hexPath(corners, 0.92f, center), palette.range.copy(alpha = 0.12f))
    drawPath(hexPath(corners, 0.7f, center), palette.range, style = Stroke(width = u * 0.1f, join = StrokeJoin.Round))
}

/** Radius: three dots. */
fun DrawScope.drawRadiusCell(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette) {
    drawPath(hexPath(corners, 0.92f, center), palette.radius.copy(alpha = 0.08f))
    val d = u * 0.42f
    listOf(-90f, 30f, 150f).forEach { deg ->
        val a = deg * PI.toFloat() / 180f
        drawCircle(
            palette.radius.copy(alpha = 0.8f),
            radius = u * 0.09f,
            center =
                center + Offset(cos(a) * d, sin(a) * d),
        )
    }
}

/** A space a piece should go to (a waiting minion, a minion back to its Battle Zone): a dashed teal outline. */
fun DrawScope.drawTargetCell(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette) {
    drawPath(hexPath(corners, 0.92f, center), palette.selection.copy(alpha = 0.22f))
    drawPath(
        hexPath(corners, 0.82f, center),
        palette.selection,
        style = Stroke(
            width = u * 0.12f,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.28f, u * 0.18f)),
        ),
    )
}

/** The selected piece's space: a bright outline on a dark halo (reads over any overlay). */
fun DrawScope.drawSelectionCell(corners: List<Offset>, center: Offset, u: Float, palette: BoardPalette) {
    drawPath(hexPath(corners, 1.0f, center), palette.halo, style = Stroke(width = u * 0.3f, join = StrokeJoin.Round))
    drawPath(hexPath(corners, 1.0f, center), palette.label, style = Stroke(width = u * 0.14f, join = StrokeJoin.Round))
}

/** The water around the map (p.19 terrain): a faint hex of the printed grid, drawn below everything. */
fun DrawScope.drawWaterCell(corners: List<Offset>, palette: BoardPalette) {
    val path = hexPath(corners)
    drawPath(path, palette.water)
    drawPath(path, palette.waterLine, style = Stroke(width = (corners[0] - corners[3]).getDistance() * 0.02f))
}

/** A legend swatch: one space of [fill] with [cue] drawn on it, filling the draw area. */
fun DrawScope.drawSwatch(fill: Color, gap: Color, cue: DrawScope.(List<Offset>, Offset, Float) -> Unit) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val u = minOf(size.width / SQRT3, size.height / 2f) * 0.98f
    val corners = hexCorners(center, u)
    drawPath(hexPath(corners), fill)
    drawPath(hexPath(corners), gap, style = Stroke(width = u * 0.08f))
    translate(0f, 0f) { cue(corners, center, u) }
}
