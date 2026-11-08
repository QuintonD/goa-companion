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
import androidx.compose.ui.graphics.Brush
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
import dev.quinton.goa.core.designsystem.component.TitlePlateSize
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.titlePlate
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * The plate's art for a whole list, decoded once and handed to every tile: a tile drawn from these
 * has no image request of its own, so the frame is there the moment a tile scrolls in and scrolling
 * costs no more than one draw node per tile. Null while loading or if it fails (a flat plate). [light]: the light
 * theme's paper plate instead of the slate.
 */
@Immutable
internal class PlateArt(
    val slate: ImageBitmap?,
    val corners: List<ImageBitmap?>,
    val titlePlate: ImageBitmap?,
    val light: Boolean = false,
)

@Composable
internal fun rememberPlateArt(): PlateArt {
    val slate = rememberOfficialBitmap(LookArt.SLATE)
    val corners = LookArt.corners().map { rememberOfficialBitmap(it) }
    val titlePlate = rememberOfficialBitmap(LookArt.TITLE_PLATE)
    val light = !GoaTheme.isDark
    return remember(slate, corners, titlePlate, light) { PlateArt(slate, corners, titlePlate, light) }
}

internal val TileShape = RoundedCornerShape(7.dp)

/** The plate with its steel edge and teal brackets, in one draw node ([PlateArt]): the slate, or paper. */
internal fun Modifier.tilePlate(art: PlateArt): Modifier = drawWithCache {
    val outline = TileShape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { if (outline is Outline.Rounded) addRoundRect(outline.roundRect) }
    val texture = art.slate?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    val paper = Brush.verticalGradient(listOf(PAPER_TOP, PAPER_LOW))
    val edge = Stroke(width = 1.dp.toPx())
    val darkEdge = Stroke(width = 3.dp.toPx())
    val bracket = BRACKET.roundToPx()
    val inset = 2.dp.roundToPx()
    onDrawBehind {
        clipPath(clip) {
            if (art.light) {
                drawRect(paper)
                drawPath(clip, STEEL_LIGHT, style = darkEdge)
            } else {
                drawRect(PlateGround)
                if (texture != null) {
                    drawRect(texture)
                    drawRect(PLATE_SHADE)
                }
                drawPath(clip, PLATE_DARK_EDGE, style = darkEdge)
            }
        }
        drawPath(clip, if (art.light) STEEL_EDGE else PLATE_EDGE, style = edge)
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

/** On a grid tile: the mock-up's small plate; the tile shrinks a long name to fit it on one line. */
internal val SmallPlate = TitlePlateSize.Small

/** On the hero page: the design system's `TitlePlate`. */
internal val LargePlate = TitlePlateSize.Large

/**
 * The cards' parchment title plate from a shared bitmap, grown by what it holds. Takes any content,
 * where the design system's `TitlePlate` takes one string: the hero page sets name and title apart. The restrained
 * plate, without the gear: in the catalog a gear would pass for a control or a team mark (DESIGN.md "Ornaments").
 */
@Composable
internal fun SharedTitlePlate(
    plate: ImageBitmap?,
    size: TitlePlateSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.titlePlate(plate, size, GoaTheme.colors.parchment, gear = false),
        contentAlignment = Alignment.Center,
        content = content,
    )
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

// The light theme's paper plate, as the design system's slatePlate draws it.
private val PAPER_TOP = Color(0xFFFEFCF8)
private val PAPER_LOW = Color(0xFFF8F4EB)
private val STEEL_EDGE = Color(0xFF6F7C89)
private val STEEL_LIGHT = Color(0xFFB4BEC8)
