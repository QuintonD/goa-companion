package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing

/*
 * Every row that scrolls sideways says so the same way (DESIGN.md "Sideways rows"): it starts on the screen's
 * gutter, and the edge that hides more fades out under a small steel chevron, so a chip cut at the edge reads as
 * "more this way", never as a layout bug. The fade is a mask, so it works over any ground (the canvas, a plate, the
 * rules page, the map). A group of about six or fewer wraps (`FlowRow`) instead of scrolling.
 */

/** How wide the fade at a row's hidden edge is. */
val EdgeFadeWidth: Dp = 32.dp

/** A sideways row's padding: its first and last item sit on the screen's gutter, clear of the fade. */
val EdgeFadeGutter: PaddingValues = PaddingValues(horizontal = Spacing.screen)

/**
 * Fades this row's content out at an edge where [state] can scroll further (the end, and the start once scrolled),
 * over [width], and with [cue] puts a small steel chevron in the fade, so the row says "more this way" on any ground.
 * Put it before the scroll modifier (or on a `LazyRow` with its state). Follows the layout direction.
 */
fun Modifier.horizontalEdgeFade(state: ScrollableState, width: Dp = EdgeFadeWidth, cue: Boolean = true): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = width.toPx().coerceAtMost(size.width / 3f)
            val rtl = layoutDirection == LayoutDirection.Rtl
            // Read here, in the draw phase: the fade follows the scroll without recomposing the row.
            val hiddenEnd = state.canScrollForward
            val hiddenStart = state.canScrollBackward
            val right = if (rtl) hiddenStart else hiddenEnd
            val left = if (rtl) hiddenEnd else hiddenStart
            if (right) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (left) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (cue && size.height >= CueSize.toPx()) {
                if (right) drawCue(pointsRight = true)
                if (left) drawCue(pointsRight = false)
            }
        }

/** The cue's disc: small enough to sit in the fade, big enough to read as "more this way". */
private val CueSize: Dp = 24.dp

/** A steel disc with a chevron, at the row's right or left edge, centred on its height. */
private fun DrawScope.drawCue(pointsRight: Boolean) {
    val r = CueSize.toPx() / 2f
    val inset = 2.dp.toPx()
    val cx = if (pointsRight) size.width - r - inset else r + inset
    val center = Offset(cx, size.height / 2f)
    drawCircle(CUE_DISC, r, center)
    drawCircle(CUE_EDGE, r, center, style = Stroke(1.dp.toPx()))
    val arm = r * 0.36f
    val dir = if (pointsRight) 1f else -1f
    val chevron = Path().apply {
        moveTo(center.x - dir * arm * 0.55f, center.y - arm)
        lineTo(center.x + dir * arm * 0.55f, center.y)
        lineTo(center.x - dir * arm * 0.55f, center.y + arm)
    }
    drawPath(chevron, CUE_INK, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// The steel of the map's floating controls: one look on the canvas, a plate, paper or the map.
private val CUE_DISC = Color(0xF027313A)
private val CUE_EDGE = Color(0xFF9AA7AF)
private val CUE_INK = Color(0xFFE8E2D6)

/**
 * Fades a scrolling column's content out at its foot while [state] can scroll further down, over [height]: a page
 * over a pinned bar ends in "more below", never in a line cut in half at the bar's edge.
 */
fun Modifier.bottomEdgeFade(state: ScrollableState, height: Dp = EdgeFadeWidth): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (state.canScrollForward) {
                val fade = height.toPx().coerceAtMost(size.height / 3f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/**
 * A `Row` that scrolls sideways the app's way: the [horizontalEdgeFade] at its hidden edges, and [gutter] padding
 * inside the scroll so its first and last items line up with the screen's content and can scroll clear of the fade.
 */
fun Modifier.fadingHorizontalScroll(state: ScrollState, gutter: Dp = Spacing.screen): Modifier =
    horizontalEdgeFade(state).horizontalScroll(state).padding(horizontal = gutter)

/**
 * Lets a sideways row inside a padded column run to the column's edges: widens it by [gutter] on each side (the
 * parent's padding). Give the row [gutter] as its content padding, so its first item still sits on the gutter and
 * a cut item is cut at the screen's edge, under the fade, never mid-column.
 */
fun Modifier.bleedHorizontally(gutter: Dp = Spacing.screen): Modifier = layout { measurable, constraints ->
    val side = gutter.roundToPx()
    val bounded = constraints.hasBoundedWidth
    val wide = if (bounded) {
        constraints.copy(minWidth = constraints.minWidth + 2 * side, maxWidth = constraints.maxWidth + 2 * side)
    } else {
        constraints
    }
    val placeable = measurable.measure(wide)
    val width = if (bounded) placeable.width - 2 * side else placeable.width
    layout(width, placeable.height) { placeable.place(if (bounded) -side else 0, 0) }
}
