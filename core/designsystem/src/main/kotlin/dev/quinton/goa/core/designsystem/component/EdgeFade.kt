package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing

/*
 * Every row that scrolls sideways says so the same way (DESIGN.md "Sideways rows"): it starts on the screen's
 * gutter, and the edge that hides more fades out, so a chip cut at the edge reads as "more this way", never as a
 * layout bug. The fade is a mask, so it works over any ground (the canvas, a plate, the rules page, the map).
 */

/** How wide the fade at a row's hidden edge is. */
val EdgeFadeWidth: Dp = 32.dp

/** A sideways row's padding: its first and last item sit on the screen's gutter, clear of the fade. */
val EdgeFadeGutter: PaddingValues = PaddingValues(horizontal = Spacing.screen)

/**
 * Fades this row's content out at an edge where [state] can scroll further (the end, and the start once scrolled),
 * over [width]. Put it before the scroll modifier (or on a `LazyRow` with its state). Follows the layout direction.
 */
fun Modifier.horizontalEdgeFade(state: ScrollableState, width: Dp = EdgeFadeWidth): Modifier =
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
        }

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
