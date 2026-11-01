package dev.quinton.goa.feature.catalog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * The plate's art for a whole list, decoded once and handed to every tile: a tile drawn from these
 * has no image request of its own, so the frame is there the moment a tile scrolls in and scrolling
 * costs no more than one draw node per tile. Null while loading or if it fails (a flat plate).
 */
@Immutable
internal class PlateArt(val slate: ImageBitmap?, val corners: List<ImageBitmap?>, val titlePlate: ImageBitmap?)

@Composable
internal fun rememberPlateArt(): PlateArt {
    val slate = rememberOfficialBitmap(LookArt.SLATE)
    val corners = LookArt.corners().map { rememberOfficialBitmap(it) }
    val titlePlate = rememberOfficialBitmap(LookArt.TITLE_PLATE)
    return remember(slate, corners, titlePlate) { PlateArt(slate, corners, titlePlate) }
}

internal val TileShape = RoundedCornerShape(7.dp)

/** The slate plate with its steel edge and teal brackets, in one draw node ([PlateArt]). */
internal fun Modifier.tilePlate(art: PlateArt): Modifier = drawWithCache {
    val outline = TileShape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { if (outline is Outline.Rounded) addRoundRect(outline.roundRect) }
    val texture = art.slate?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    val edge = Stroke(width = 1.dp.toPx())
    val darkEdge = Stroke(width = 3.dp.toPx())
    val bracket = BRACKET.roundToPx()
    val inset = 2.dp.roundToPx()
    onDrawBehind {
        clipPath(clip) {
            drawRect(PlateGround)
            if (texture != null) {
                drawRect(texture)
                drawRect(PLATE_SHADE)
            }
            drawPath(clip, PLATE_DARK_EDGE, style = darkEdge)
        }
        drawPath(clip, PLATE_EDGE, style = edge)
        art.corners.forEachIndexed { i, image ->
            if (image == null) return@forEachIndexed
            val x = if (i % 2 == 0) inset else size.width.toInt() - bracket - inset
            val y = if (i < 2) inset else size.height.toInt() - bracket - inset
            drawImage(
                image,
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(bracket, bracket),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Corner and text insets of a title plate at some size, in dp. */
internal class PlateInsets(val start: Dp, val vertical: Dp, val end: Dp, val minHeight: Dp)

/** On a grid tile: the mock-up's small plate, so "Silverarrow" fits a third of a phone's width. */
internal val SmallPlate = PlateInsets(start = 8.dp, vertical = 5.dp, end = 18.dp, minHeight = 25.dp)

/** On the hero page: the same proportions as the design system's `TitlePlate`. */
internal val LargePlate = PlateInsets(start = 12.dp, vertical = 7.dp, end = 25.dp, minHeight = 34.dp)

/**
 * The cards' parchment title plate from a shared bitmap, grown by what it holds. Takes any content,
 * where the design system's `TitlePlate` takes one string: the hero page sets name and title apart.
 */
@Composable
internal fun SharedTitlePlate(
    plate: ImageBitmap?,
    insets: PlateInsets,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val parchment = GoaTheme.colors.parchment
    Box(
        modifier
            .defaultMinSize(minHeight = insets.minHeight)
            .drawWithCache {
                val dst = intArrayOf(
                    insets.start.roundToPx(),
                    insets.vertical.roundToPx(),
                    insets.end.roundToPx(),
                    insets.vertical.roundToPx(),
                )
                onDrawBehind {
                    if (plate != null) {
                        drawTitlePlate(plate, dst)
                    } else {
                        drawRoundRect(parchment, cornerRadius = CornerRadius(size.height / 2f))
                    }
                }
            }
            .padding(
                start = insets.start + 1.dp,
                end = insets.end + 1.dp,
                top = insets.vertical - 1.dp,
                bottom = insets.vertical - 1.dp,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The plate's ends kept and its middle stretched; [dst] is left, top, right, bottom in px. */
private fun DrawScope.drawTitlePlate(image: ImageBitmap, dst: IntArray) {
    val xs = intArrayOf(0, SLICE_START, image.width - SLICE_END, image.width)
    val ys = intArrayOf(0, SLICE_VERTICAL, image.height - SLICE_VERTICAL, image.height)
    val dx = intArrayOf(0, dst[0], size.width.toInt() - dst[2], size.width.toInt())
    val dy = intArrayOf(0, dst[1], size.height.toInt() - dst[3], size.height.toInt())
    for (row in 0..2) {
        for (col in 0..2) {
            val width = dx[col + 1] - dx[col]
            val height = dy[row + 1] - dy[row]
            if (width <= 0 || height <= 0) continue
            drawImage(
                image,
                srcOffset = IntOffset(xs[col], ys[row]),
                srcSize = IntSize(xs[col + 1] - xs[col], ys[row + 1] - ys[row]),
                dstOffset = IntOffset(dx[col], dy[row]),
                dstSize = IntSize(width, height),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Lays this out [overlap] higher and shorter, so it runs under what sits above it. */
internal fun Modifier.overlapUp(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val up = overlap.roundToPx()
    layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
}

private val BRACKET = 14.dp
private val PLATE_SHADE = Color(0x2E000000)
private val PLATE_EDGE = Color(0xFF5D6B70)
private val PLATE_DARK_EDGE = Color(0xFF15191B)

/** The title plate's slices in its source pixels (the same as the design system's). */
private const val SLICE_START = 16
private const val SLICE_END = 36
private const val SLICE_VERTICAL = 7
