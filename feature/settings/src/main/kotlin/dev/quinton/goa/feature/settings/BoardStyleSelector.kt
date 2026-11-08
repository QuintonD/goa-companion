package dev.quinton.goa.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.selectionFrame
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.BoardStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal val BoardStyle.label: String
    get() = when (this) {
        BoardStyle.MINIATURES -> "Miniatures"
        BoardStyle.GRID_AND_DISCS -> "Grid and discs"
    }

/** The two board styles as radio tiles, each with a piece of the painted map; stacked at large text. */
@Composable
internal fun BoardStyleSelector(selected: BoardStyle, onSelect: (BoardStyle) -> Unit) {
    val ground = rememberPreviewGround()
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BoardStyle.entries.forEach { BoardStyleTile(it, ground, it == selected, onSelect, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.selectableGroup().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BoardStyle.entries.forEach { BoardStyleTile(it, ground, it == selected, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BoardStyleTile(
    style: BoardStyle,
    ground: ImageBitmap?,
    selected: Boolean,
    onSelect: (BoardStyle) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .selectionFrame(selected, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(style) }),
    ) {
        BoardStylePreview(
            style,
            ground,
            Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).clip(shape).clearAndSetSemantics {
            },
        )
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(style.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * The small piece of the painting both tiles show, cut once. Software, for the canvas, and past the memory
 * cache, so the 2048 px painting isn't kept here and doesn't replace the board's hardware copy there.
 */
@Composable
private fun rememberPreviewGround(): ImageBitmap? {
    val context = LocalContext.current
    val ground by produceState<ImageBitmap?>(null) {
        val request = ImageRequest.Builder(context)
            .data(assetUrl(PREVIEW_MAP))
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val painting = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        value = painting?.let {
            Bitmap.createBitmap(it, PREVIEW_CROP_X, PREVIEW_CROP_Y, PREVIEW_CROP_WIDTH, PREVIEW_CROP_HEIGHT)
                .asImageBitmap()
        }
    }
    return ground
}

/** A corner of the painted map with two pieces on it, the way that style shows them. */
@Composable
private fun BoardStylePreview(style: BoardStyle, ground: ImageBitmap?, modifier: Modifier) {
    Box(modifier.background(PlateGround)) {
        Canvas(Modifier.fillMaxSize()) {
            ground?.let { image ->
                // The sand and the hexes' faint star pattern, cropped to the tile's shape.
                val height = (image.width * size.height / size.width).toInt().coerceAtMost(image.height)
                drawImage(
                    image,
                    srcSize = IntSize(image.width, height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
            if (style == BoardStyle.GRID_AND_DISCS) drawHexGrid(HEX_RADIUS.toPx())
        }
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (style) {
                BoardStyle.MINIATURES -> {
                    Miniature(MinionType.HEAVY, Team.RED, 54.dp)
                    Miniature(MinionType.RANGED, Team.BLUE, 46.dp)
                }

                BoardStyle.GRID_AND_DISCS -> {
                    Disc(Team.RED)
                    Disc(Team.BLUE)
                }
            }
        }
    }
}

@Composable
private fun Miniature(type: MinionType, team: Team, height: Dp) {
    OfficialIcon(LookArt.miniature(type, team), Modifier.size(height))
}

@Composable
private fun Disc(team: Team) {
    Box(Modifier.size(38.dp)) {
        Box(
            Modifier
                .size(32.dp)
                .background(teamColor(team), CircleShape)
                .border(1.5.dp, DISC_EDGE, CircleShape),
        )
        TeamMark(team, Modifier.align(Alignment.BottomEnd), markSize = 13.dp)
    }
}

private fun DrawScope.drawHexGrid(radius: Float) {
    val hex = Path().apply {
        for (corner in 0 until HEX_CORNERS) {
            val angle = Math.toRadians(HEX_CORNER_DEGREES * corner)
            val x = radius * cos(angle).toFloat()
            val y = radius * sin(angle).toFloat()
            if (corner == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val stepX = radius * 1.5f
    val stepY = radius * sqrt(3f)
    val line = Stroke(width = 1.dp.toPx())
    for (column in 0..(size.width / stepX).toInt() + 1) {
        val shift = if (column % 2 == 0) 0f else stepY / 2f
        for (row in 0..(size.height / stepY).toInt() + 1) {
            val placed = Path().apply { addPath(hex, Offset(column * stepX, row * stepY + shift)) }
            drawPath(placed, GRID_LINE, style = line)
        }
    }
}

private const val PREVIEW_MAP = "official/maps/forgotten-island.webp"
private const val PREVIEW_CROP_X = 880
private const val PREVIEW_CROP_Y = 940
private const val PREVIEW_CROP_WIDTH = 360
private const val PREVIEW_CROP_HEIGHT = 240
private const val HEX_CORNERS = 6
private const val HEX_CORNER_DEGREES = 60.0
private val HEX_RADIUS = 20.dp
private val PREVIEW_HEIGHT = 84.dp
private val GRID_LINE = Color(0xCC15191B)
private val DISC_EDGE = Color(0xFF0B1622)
