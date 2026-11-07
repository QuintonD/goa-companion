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
 * gutter, and the edge that hides more fades out to a small steel chevron in a clear strip at the very edge (never
 * over content), so a chip cut at the edge reads as "more this way", never as a layout bug. The fade is a mask, so
 * it works over any ground (the canvas, a plate, the rules page, the map). A group of about six or fewer wraps
 * (`FlowRow`) instead of scrolling.
 */

/** How wide the fade at a row's hidden edge is. */
val EdgeFadeWidth: Dp = 32.dp

/** A sideways row's padding: its first and last item sit on the screen's gutter, clear of the fade. */
val EdgeFadeGutter: PaddingValues = PaddingValues(horizontal = Spacing.screen)

/**
 * Fades this row's content out at an edge where [state] can scroll further (the end, and the start once scrolled),
 * over [width], and with [cue] puts a small steel chevron beyond the fade, so the row says "more this way" on any
 * ground. The chevron never covers content: the outermost [CueZone] is masked clear and the chevron sits there,
 * the fade running in front of it. Put it before the scroll modifier (or on a `LazyRow` with its state). Follows the
 * layout direction.
 */
fun Modifier.horizontalEdgeFade(state: ScrollableState, width: Dp = EdgeFadeWidth, cue: Boolean = true): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val showCue = cue && size.height >= CueMinHeight.toPx()
            val zone = if (showCue) CueZone.toPx().coerceAtMost(size.width / 6f) else 0f
            val fade = width.toPx().coerceAtMost(size.width / 3f)
            val rtl = layoutDirection == LayoutDirection.Rtl
            // Read here, in the draw phase: the fade follows the scroll without recomposing the row.
            val hiddenEnd = state.canScrollForward
            val hiddenStart = state.canScrollBackward
            val right = if (rtl) hiddenStart else hiddenEnd
            val left = if (rtl) hiddenEnd else hiddenStart
            // Clear from the edge across the cue's zone, then the fade back to solid: the tile mode clamps.
            if (right) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startX = size.width - zone - fade,
                        endX = size.width - zone,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (left) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = zone, endX = zone + fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (showCue) {
                if (right) drawCue(pointsRight = true, zone)
                if (left) drawCue(pointsRight = false, zone)
            }
        }

/** The clear strip at a hidden edge that holds the chevron: nothing of the row shows under it. */
private val CueZone: Dp = 14.dp

/** A row lower than this gets the fade without the chevron. */
private val CueMinHeight: Dp = 24.dp

/**
 * A steel chevron in the clear strip at the row's right or left edge, centred on its height: a dark halo under a
 * light stroke, so it reads on the canvas, a plate, paper or the map.
 */
private fun DrawScope.drawCue(pointsRight: Boolean, zone: Float) {
    val cx = if (pointsRight) size.width - zone / 2f else zone / 2f
    val center = Offset(cx, size.height / 2f)
    val arm = CueArm.toPx()
    val dir = if (pointsRight) 1f else -1f
    val chevron = Path().apply {
        moveTo(center.x - dir * arm * 0.5f, center.y - arm)
        lineTo(center.x + dir * arm * 0.5f, center.y)
        lineTo(center.x - dir * arm * 0.5f, center.y + arm)
    }
    drawPath(chevron, CUE_HALO, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(chevron, CUE_INK, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Half the chevron's height. */
private val CueArm: Dp = 7.dp

// The steel of the map's floating controls: one look on the canvas, a plate, paper or the map.
private val CUE_HALO = Color(0xE027313A)
private val CUE_INK = Color(0xFFC9D2D8)

/**
 * Fades a scrolling column's content out at its foot while [state] can scroll further down, over [height]: a page
 * over a pinned bar ends in "more below", never in a line cut in half at the bar's edge. With a [ScrollState] the
 * fade is never taller than what is still below, so a page that ends just past the bar isn't dimmed over its last
 * line for a few pixels of padding.
 */
fun Modifier.bottomEdgeFade(state: ScrollableState, height: Dp = EdgeFadeWidth): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (state.canScrollForward) {
                val below = (state as? ScrollState)?.let { (it.maxValue - it.value).toFloat() } ?: Float.MAX_VALUE
                val fade = height.toPx().coerceAtMost(size.height / 3f).coerceAtMost(below)
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
