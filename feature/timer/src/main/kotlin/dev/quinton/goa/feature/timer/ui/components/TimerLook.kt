package dev.quinton.goa.feature.timer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TimerPaintings
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.rememberPaintingBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The running timer's own parts (ADR-053, mock-up 07): the dim painted sea, the rulebook's battlefield
 * along the foot, and metal buttons. It may be read across a table in a dim room: the art is veiled so
 * that the digits stay the brightest thing, and nothing here carries meaning.
 */

/**
 * The painted sea (the app's own, at full resolution), cover, under a radial navy veil, with the rulebook's
 * battlefield along the foot
 * when [silhouette] is set. Navy alone while the art loads and if it fails. Decorative.
 */
private fun Modifier.timerBackdrop(silhouette: Boolean): Modifier = composed {
    // The sea painted for the app at full resolution, a cut for each orientation (third_party/ASSETS.md).
    val config = LocalConfiguration.current
    val sea = rememberPaintingBitmap(TimerPaintings.sea(landscape = config.screenWidthDp > config.screenHeightDp))
    val field = if (silhouette) rememberOfficialBitmap(LookArt.SILHOUETTE) else null
    drawWithCache {
        val w = size.width
        val h = size.height
        val veil = Brush.radialGradient(
            0f to NAVY.copy(alpha = VEIL_CENTRE),
            1f to NAVY.copy(alpha = VEIL_EDGE),
            center = Offset(w / 2f, h * VEIL_Y),
            radius = max(w, h) * VEIL_REACH,
        )
        // Cover: the sea keeps its aspect and fills the screen, centred.
        val seaScale = sea?.let { max(w / it.width, h / it.height) } ?: 1f
        // The battlefield overhangs the sides a little; a short window (landscape) caps its height.
        val fieldScale = field?.let { min(w * FIELD_WIDTH / it.width, h * FIELD_MAX_HEIGHT / it.height) } ?: 1f
        val fieldSize = field?.let {
            IntSize((it.width * fieldScale).roundToInt(), (it.height * fieldScale).roundToInt())
        }
        val footHeight = FOOT_HEIGHT.toPx()
        val fieldBottom = h - (if (h > w) FIELD_FOOT.toPx() else 0f)
        val foot = Brush.verticalGradient(
            0f to NAVY.copy(alpha = 0f),
            FOOT_SOLID to NAVY,
            startY = h - footHeight,
            endY = h,
        )
        onDrawBehind {
            drawRect(NAVY)
            if (sea != null) {
                val drawn = IntSize((sea.width * seaScale).roundToInt(), (sea.height * seaScale).roundToInt())
                drawImage(
                    sea,
                    dstOffset = IntOffset(
                        ((w - drawn.width) / 2f).roundToInt(),
                        ((h - drawn.height) / 2f).roundToInt(),
                    ),
                    dstSize = drawn,
                    filterQuality = FilterQuality.Medium,
                )
                drawRect(veil)
            }
            if (field != null && fieldSize != null) {
                drawImage(
                    field,
                    dstOffset = IntOffset(
                        ((w - fieldSize.width) / 2f).roundToInt(),
                        (fieldBottom - fieldSize.height).roundToInt(),
                    ),
                    dstSize = fieldSize,
                    filterQuality = FilterQuality.Medium,
                    colorFilter = FIELD_BRIGHTEN,
                )
            }
            drawRect(foot, topLeft = Offset(0f, h - footHeight), size = Size(w, footHeight))
        }
    }
}

/** The running timer's ground, filling the screen, on a layer of its own so the ticking clock doesn't redraw it. */
@Composable
internal fun TimerBackdrop(silhouette: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().graphicsLayer {}.timerBackdrop(silhouette))
}

/** An accent edge on a plate: the current step, the selected option. Null draws none. */
internal fun Modifier.plateEdge(color: Color?, width: Dp = 2.dp, shape: Shape = PlateShape): Modifier =
    if (color == null) this else border(width, color, shape)

private val NAVY = Color(0xFF0B1622)

private const val VEIL_CENTRE = 0.5f
private const val VEIL_EDGE = 0.86f
private const val VEIL_Y = 0.4f
private const val VEIL_REACH = 0.62f
private const val FIELD_WIDTH = 1.146f
private const val FIELD_MAX_HEIGHT = 0.42f
private const val FOOT_SOLID = 0.45f
private val FIELD_FOOT = 70.dp
private val FOOT_HEIGHT = 110.dp

/** The silhouette is printed nearly black: lifted a little so it reads on the veiled sea. */
private const val FIELD_LIFT = 1.5f
private val FIELD_BRIGHTEN = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToScale(FIELD_LIFT, FIELD_LIFT, FIELD_LIFT, 1f) },
)

/** Teal on a plate's edge: next and ready (DESIGN.md); a picked plate is `GoaPlate(selected =)`, bronze. */
internal val PlateTeal = Color(0xFF3FC1B0)

internal val PlateError = Color(0xFFFF6B6B)
