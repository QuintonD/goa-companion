package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.feature.board.ui.ArtBoardPalette
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.drawFastTravelCell
import dev.quinton.goa.feature.board.ui.drawNumberChip
import dev.quinton.goa.feature.board.ui.drawRadiusCell
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawTargetCell
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the map shows for the question, each with its own shape and line as well as its colour. */
enum class MapKeyItem(val words: String) {
    /** A space or piece that answers: lit, white-edged. */
    CHOOSE("You can choose"),

    /** The number in a dark chip on a space a move reaches. */
    STEPS("Spaces to get there"),

    /** A card's reach (violet inner outline). */
    RANGE("In range"),

    /** The chosen space's path (a bronze line with a dot at each step). */
    PATH("Your path"),

    /** The sandbox's Movement: a lit space with the fewest spaces it needs in a chip. */
    MOVEMENT("Movement"),

    /** The sandbox's Fast Travel: bronze hatching. */
    FAST_TRAVEL("Fast Travel"),

    /** The sandbox's Radius: three dots. */
    RADIUS("Radius"),

    /** The sandbox's Measure: a bronze line. */
    MEASURE("Measure"),

    /** Where a piece should go (a waiting minion's spaces, a minion's way back): a dashed teal outline. */
    GO_HERE("Where it goes"),
}

/** The key's items for the question on the table: only what the map shows now. */
internal fun mapKeyItems(table: TableUi): List<MapKeyItem> {
    val decision = table.decision ?: return emptyList()
    if (!table.asking) return emptyList()
    val options = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()
    val from = (table.subject ?: decision.seat).let(table::at)
    val steps = table.spaceOptions.keys.any { hex -> table.pathTo(hex).any { it != from } }
    val path = (table.selected as? Choice.Space)?.let { table.pathTo(it.hex).any { hex -> hex != from } } == true
    return buildList {
        if (options) add(MapKeyItem.CHOOSE)
        if (steps) add(MapKeyItem.STEPS)
        if (decision.context.highlight.isNotEmpty()) add(MapKeyItem.RANGE)
        if (path) add(MapKeyItem.PATH)
    }
}

/**
 * The map's key while a question lights it (the board's own marks, a swatch each): "You can choose", "Spaces to get
 * there", "In range", "Your path". A small plate under the round at a glance and the turn track, where the map is
 * framed below it, so it never covers a piece. TalkBack hears it as one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapKey(
    items: List<MapKeyItem>,
    modifier: Modifier = Modifier,
    words: (MapKeyItem) -> String = { it.words },
) {
    if (items.isEmpty()) return
    // Map furniture: it grows with large text up to 1.3×, so it never takes the map's top half.
    val fontScale = LocalDensity.current.fontScale
    val cap = minOf(fontScale, KEY_MAX_SCALE) / fontScale
    PlateSurface(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Map key: " + items.joinToString(", ", transform = words)
        },
    ) {
        FlowRow(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeySwatch(item)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        words(item),
                        style = MaterialTheme.typography.labelMedium.let {
                            it.copy(fontSize = it.fontSize * cap, lineHeight = it.lineHeight * cap)
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeySwatch(item: MapKeyItem) {
    val measurer = rememberTextMeasurer()
    val palette = ArtBoardPalette
    Canvas(Modifier.size(scaledIconSize(20.dp, 1.3f))) {
        val c = center
        val r = size.minDimension / 2f
        when (item) {
            MapKeyItem.CHOOSE -> {
                val hex = hexagon(c, r * 0.95f)
                drawPath(hex, LitFill)
                drawPath(
                    hex,
                    Color.Black.copy(alpha = 0.6f),
                    style = Stroke(width = r * 0.34f, join = StrokeJoin.Round),
                )
                drawPath(hex, Color.White, style = Stroke(width = r * 0.16f, join = StrokeJoin.Round))
            }

            MapKeyItem.STEPS -> {
                drawCircle(ChipGround, radius = r * 0.92f, center = c)
                drawCircle(ChipEdge, radius = r * 0.92f, center = c, style = Stroke(width = r * 0.1f))
                val text = measurer.measure(
                    "2",
                    TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                drawText(text, topLeft = c - Offset(text.size.width / 2f, text.size.height / 2f))
            }

            // The map's own strokes at the swatch's size: a space's faint edge with the violet inner outline (what a
            // range cell is on the map, with or without a number in it), not a free-standing violet hexagon.
            MapKeyItem.RANGE -> {
                drawPath(hexagon(c, r * 0.95f), CellEdge, style = Stroke(width = r * 0.06f, join = StrokeJoin.Round))
                drawRangeCell(corners(c, r * 0.95f), c, r * 0.95f, palette)
            }

            MapKeyItem.PATH, MapKeyItem.MEASURE -> drawPathSwatch(r, palette.halo, palette.measure)

            // A lit space with the region's white edge and the map's own number chip (dark, cream rim).
            MapKeyItem.MOVEMENT -> {
                val hex = hexagon(c, r * 0.95f)
                drawPath(hex, LitFill)
                drawPath(hex, Color.White, style = Stroke(width = r * 0.14f, join = StrokeJoin.Round))
                val text = measurer.measure(
                    "2",
                    TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                )
                drawNumberChip(text, c, r * 1.2f)
            }

            MapKeyItem.FAST_TRAVEL -> drawFastTravelCell(corners(c, r * 0.95f), c, r * 0.95f, palette)

            MapKeyItem.RADIUS -> drawRadiusCell(corners(c, r * 0.95f), c, r * 0.95f, palette)

            MapKeyItem.GO_HERE -> drawTargetCell(corners(c, r * 0.95f), c, r * 0.95f, palette)
        }
    }
}

private fun DrawScope.drawPathSwatch(r: Float, halo: Color, line: Color) {
    val a = Offset(size.width * 0.08f, size.height * 0.75f)
    val b = Offset(size.width * 0.5f, size.height * 0.3f)
    val e = Offset(size.width * 0.92f, size.height * 0.62f)
    listOf(a to b, b to e).forEach { (from, to) ->
        drawLine(halo, from, to, strokeWidth = r * 0.36f, cap = StrokeCap.Round)
    }
    listOf(a to b, b to e).forEach { (from, to) ->
        drawLine(line, from, to, strokeWidth = r * 0.18f, cap = StrokeCap.Round)
    }
    drawCircle(line, radius = r * 0.18f, center = b)
}

/** A pointy-top hexagon's corners around [c], as the map's cells are drawn from. */
private fun corners(c: Offset, r: Float) = (0 until 6).map { i ->
    val a = (60.0 * i - 90.0) * PI / 180.0
    Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
}

/** A pointy-top hexagon around [c]. */
private fun hexagon(c: Offset, r: Float) = Path().apply {
    for (i in 0 until 6) {
        val a = (60.0 * i - 90.0) * PI / 180.0
        val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private const val KEY_MAX_SCALE = 1.3f
private val LitFill = Color(0xFFD9C9A3)
private val ChipGround = Color(0xFF0B1622)
private val ChipEdge = Color(0xFFF3EBD6)
private val CellEdge = Color(0x80F3EBD6)
