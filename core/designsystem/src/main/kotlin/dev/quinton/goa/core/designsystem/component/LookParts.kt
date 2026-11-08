package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.theme.DarkColors
import dev.quinton.goa.core.designsystem.theme.DarkExtended
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.LocalGoaColors
import dev.quinton.goa.core.designsystem.theme.LocalGoaIsDark
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.BoardStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/*
 * The game's own look (ADR-053): the parts the rulebook's pages and the cards are made of, as the
 * app's frames. Every part falls back to a flat colour while its image loads and if it fails, so a
 * screen is never unreadable without the art.
 */

/** The device's choice of how pieces and spaces look on the painted board; the app shell provides it. */
val LocalBoardStyle = staticCompositionLocalOf { BoardStyle.MINIATURES }

/** Paths of the look's graphics (`official/SOURCES.md`). */
object LookArt {
    const val BANNER = "official/ui/banner.webp"
    const val SLATE = "official/ui/slate.webp"
    const val TITLE_PLATE = "official/ui/title-plate.webp"
    const val COIN = "official/ui/coin.webp"
    const val DASHBOARD_FRAME = "official/ui/dashboard-frame.webp"
    const val SILHOUETTE = "official/ui/silhouette.webp"
    const val SEA = "official/ui/sea.webp"
    const val PUSH_DIAGRAM_1 = "official/rules/push-1.webp"
    const val PUSH_DIAGRAM_2 = "official/rules/push-2.webp"

    private const val CORNER_TL = "official/ui/corner-tl.webp"
    private const val CORNER_TR = "official/ui/corner-tr.webp"
    private const val CORNER_BL = "official/ui/corner-bl.webp"
    private const val CORNER_BR = "official/ui/corner-br.webp"

    fun corners() = listOf(CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR)

    fun textBox(color: PlateColor): String = "official/ui/textbox-${color.slug}.webp"

    fun ribbon(color: PlateColor): String = "official/ui/ribbon-${color.slug}.webp"

    fun lifeCounter(team: Team, spent: Boolean = false): String =
        "official/ui/life-${team.slug}${if (spent) "-spent" else ""}.webp"

    fun waveCounter(spent: Boolean = false): String = "official/ui/wave${if (spent) "-spent" else ""}.webp"

    fun actionIcon(action: LookAction): String = "official/ui/action-${action.slug}.webp"

    /** A minion's rulebook miniature. */
    fun miniature(type: MinionType, team: Team): String =
        "official/pieces/mini-${type.name.lowercase()}-${team.slug}.webp"

    /** A hero's dashboard art, where the rulebook prints it (Tigerclaw, p.16); others use their portrait. */
    fun dashboardArt(heroId: String): String? =
        if (heroId == DASHBOARD_HERO) "official/ui/dashboard-$DASHBOARD_HERO.webp" else null

    /** Every path above, for the bundled-asset check. */
    val all: List<String>
        get() = listOf(
            BANNER,
            SLATE,
            TITLE_PLATE,
            COIN,
            DASHBOARD_FRAME,
            SILHOUETTE,
            SEA,
            PUSH_DIAGRAM_1,
            PUSH_DIAGRAM_2,
        ) +
            corners() + PlateColor.entries.flatMap { listOf(textBox(it), ribbon(it)) } +
            Team.entries.flatMap { team ->
                listOf(lifeCounter(team), lifeCounter(team, spent = true)) +
                    MinionType.entries.map { miniature(it, team) }
            } +
            listOf(waveCounter(), waveCounter(spent = true)) + LookAction.entries.map(::actionIcon) +
            listOfNotNull(dashboardArt(DASHBOARD_HERO))

    private const val DASHBOARD_HERO = "tigerclaw"
    private val Team.slug: String get() = if (this == Team.RED) "red" else "blue"
}

/** The rulebook's action symbols. */
enum class LookAction(internal val slug: String) {
    ATTACK("attack"),
    SKILL("skill"),
    RANGE("range"),
    DEFENSE("defense"),
    MOVEMENT("movement"),
}

/** The card frame's colours: its text box and ribbon come in these. */
enum class PlateColor(internal val slug: String) {
    GOLD("gold"),
    SILVER("silver"),
    RED("red"),
    GREEN("green"),
    BLUE("blue"),
    ;

    companion object {
        /** A card's frame colour; the Ultimate's purple and unknown colours use the neutral silver. */
        fun of(color: CardColor): PlateColor = when (color) {
            CardColor.GOLD -> GOLD
            CardColor.SILVER -> SILVER
            CardColor.RED -> RED
            CardColor.GREEN -> GREEN
            CardColor.BLUE -> BLUE
            CardColor.PURPLE, CardColor.OTHER -> SILVER
        }
    }
}

/**
 * What sits on a plate is themed as the plate: dark on the slate in the dark theme, ink on the light theme's paper.
 * Everything inside a [GoaPlate] or [GoaSheet] gets this already. Under [GoaDarkTheme] a plate stays the slate.
 */
@Composable
fun GoaPlateTheme(content: @Composable () -> Unit) {
    if (GoaTheme.isDark) {
        GoaDarkTheme(content)
    } else {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/**
 * Dark in both themes, by design: the timer, what floats over the painted map, the cards themselves. Plates under it
 * are the slate.
 */
@Composable
fun GoaDarkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGoaColors provides DarkExtended, LocalGoaIsDark provides true) {
        MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            CompositionLocalProvider(LocalContentColor provides DarkColors.onSurface, content = content)
        }
    }
}

/**
 * How much frame a plate carries (DESIGN.md "Framing levels"). [Primary] is the section plate (steel edge, teal
 * brackets): every grouped section, More, Settings and About included. [Secondary] is flat with a quiet hairline: one
 * choice of several side by side (`SelectablePlate`), never a section. [Well] is a recess: a plate inside a plate
 * becomes one, a tone with no edge, so frames never nest.
 */
enum class PlateStyle { Primary, Secondary, Well }

/** True inside a [GoaPlate]: a plate in it draws as a [PlateStyle.Well]. */
internal val LocalInPlate = staticCompositionLocalOf { false }

/**
 * The plate the cards and dashboards are printed on, as a background: in the dark theme the slate, in the light theme
 * white paper; a steel edge and, with [brackets], the small teal corner brackets. Pair it with [GoaPlateTheme] (or
 * use [GoaPlate]). Without [edge], just the ground, for parts that continue one another (a sheet's drag handle and
 * its body). The theme is read where it draws: under [GoaDarkTheme] it is the slate in both themes.
 */
fun Modifier.slatePlate(
    shape: Shape = PlateShape,
    brackets: Boolean = true,
    edge: Boolean = true,
    style: PlateStyle = PlateStyle.Primary,
): Modifier = this then SlatePlateElement(shape, brackets && style == PlateStyle.Primary, edge, style)

private data class SlatePlateElement(
    val shape: Shape,
    val brackets: Boolean,
    val edge: Boolean,
    val style: PlateStyle,
) : ModifierNodeElement<SlatePlateNode>() {
    override fun create() = SlatePlateNode(shape, brackets, edge, style)

    override fun update(node: SlatePlateNode) = node.update(shape, brackets, edge, style)

    override fun InspectorInfo.inspectableProperties() {
        name = "slatePlate"
    }
}

/** A node, not a `composed` modifier: a screen of plates in a list adds nothing to its composition. */
private class SlatePlateNode(
    private var shape: Shape,
    private var brackets: Boolean,
    private var edge: Boolean,
    private var style: PlateStyle,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode {
    private var slate: ImageBitmap? = null
    private var corners: List<ImageBitmap?> = emptyList()
    private val cache = delegate(CacheDrawModifierNode { buildDrawing() })

    override fun onAttach() = takeArt()

    fun update(shape: Shape, brackets: Boolean, edge: Boolean, style: PlateStyle) {
        val art = brackets != this.brackets
        this.shape = shape
        this.brackets = brackets
        this.edge = edge
        this.style = style
        if (art) takeArt()
        cache.invalidateDrawCache()
    }

    /** What is decoded already draws from the first frame; the rest draws when it comes. */
    private fun takeArt() {
        val paths = if (brackets) LookArt.corners() else emptyList()
        slate = OfficialBitmaps.peek(LookArt.SLATE)
        corners = paths.map(OfficialBitmaps::peek)
        if (slate != null && corners.none { it == null }) return
        val context = currentValueOf(LocalContext)
        coroutineScope.launch {
            slate = OfficialBitmaps.load(context, LookArt.SLATE)
            corners = paths.map { path -> async { OfficialBitmaps.load(context, path) } }.awaitAll()
            cache.invalidateDrawCache()
        }
    }

    private fun CacheDrawScope.buildDrawing(): DrawResult {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val ground = PlateGroundArt(
            clip = clip,
            texture = slate?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) },
            outer = Stroke(width = 1.dp.toPx()),
            inner = Stroke(width = 3.dp.toPx()),
            edge = edge,
            style = style,
        )
        val bracket = 16.dp.toPx()
        val inset = 2.dp.toPx()
        val corners = if (style == PlateStyle.Primary) corners else emptyList()
        // Read where it draws: the theme a plate sits in decides slate or paper.
        val dark = currentValueOf(LocalGoaIsDark)
        return onDrawBehind {
            when {
                style == PlateStyle.Well -> ground.drawWell(this, dark)
                dark -> ground.drawSlate(this)
                else -> ground.drawPaper(this)
            }
            corners.forEachIndexed { i, image ->
                if (image == null) return@forEachIndexed
                val x = if (i % 2 == 0) inset else size.width - bracket - inset
                val y = if (i < 2) inset else size.height - bracket - inset
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(x.toInt(), y.toInt()),
                    dstSize = IntSize(bracket.toInt(), bracket.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

/** A plate's ground in each look: the slate, the light theme's paper, a well. */
private class PlateGroundArt(
    private val clip: Path,
    private val texture: Brush?,
    private val outer: Stroke,
    private val inner: Stroke,
    private val edge: Boolean,
    private val style: PlateStyle,
) {
    private val primary = style == PlateStyle.Primary
    private val paper = Brush.verticalGradient(listOf(PAPER_TOP, PAPER_LOW))

    fun drawWell(scope: DrawScope, dark: Boolean) = with(scope) {
        drawPath(clip, if (dark) WELL_DARK else WELL_LIGHT)
        if (edge) drawPath(clip, if (dark) WELL_DARK_EDGE else WELL_LIGHT_EDGE, style = outer)
    }

    fun drawSlate(scope: DrawScope) = with(scope) {
        clipPath(clip) {
            drawRect(PLATE_GROUND)
            if (texture != null) {
                drawRect(texture)
                drawRect(if (primary) PLATE_SHADE else SECONDARY_SHADE)
            }
            if (edge && primary) drawPath(clip, PLATE_DARK_EDGE, style = inner)
        }
        if (edge) drawPath(clip, if (primary) PLATE_EDGE else SECONDARY_EDGE, style = outer)
    }

    fun drawPaper(scope: DrawScope) = with(scope) {
        clipPath(clip) {
            if (primary) drawRect(paper) else drawRect(PAPER_SECONDARY)
            if (edge && primary) drawPath(clip, STEEL_LIGHT, style = inner)
        }
        if (edge) drawPath(clip, if (primary) STEEL_EDGE else PAPER_HAIRLINE, style = outer)
    }
}

/**
 * A plate holding [content], themed as the plate. With [onClick] the whole plate is one button. [style] picks the
 * frame ([PlateStyle]); a plate inside another plate is always a [PlateStyle.Well]. With [selected] it is one choice
 * of several: a quiet plate, bronze-framed when selected ([selectionFrame]); the caller adds its `selectable`. A
 * choice inside a section's plate is a row of it instead: full width, no ground or edge of its own, selected by the
 * wash and a leading bronze bar ([selectionRow]; DESIGN.md "Framing levels").
 */
@Composable
fun GoaPlate(
    modifier: Modifier = Modifier,
    shape: Shape = PlateShape,
    brackets: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    style: PlateStyle = PlateStyle.Primary,
    selected: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
    val inPlate = LocalInPlate.current
    val drawn = when {
        inPlate -> PlateStyle.Well
        selected != null -> PlateStyle.Secondary
        else -> style
    }
    val row = inPlate && selected != null
    GoaPlateTheme {
        val frame = when {
            selected == null -> Modifier
            row -> Modifier.selectionRow(selected)
            else -> Modifier.selectionFrame(selected, shape)
        }
        // Framing levels (DESIGN.md): one frame per section. A plate inside a plate is a recess of tone with no edge
        // of its own; what separates inside a plate is spacing and a hairline (PlateRowDivider), never a second frame.
        val ground = when {
            row -> Modifier
            inPlate -> Modifier.slatePlate(shape, brackets = false, edge = false, style = PlateStyle.Well)
            else -> Modifier.slatePlate(shape, brackets, style = drawn)
        }
        CompositionLocalProvider(LocalInPlate provides true) {
            Column(
                modifier.then(ground).then(frame).then(click).padding(contentPadding),
                content = content,
            )
        }
    }
}

/**
 * A bottom sheet on the plate: the slate, or paper in the light theme. Use this rather than `ModalBottomSheet`, so a
 * sheet looks the same everywhere and its contents are themed as a plate. [scrimColor] dims what is behind it: a sheet
 * over a busy screen (the game table) dims it more, so what shows above the sheet reads as behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    GoaPlateTheme {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            scrimColor = scrimColor,
            shape = SheetShape,
            containerColor = GoaTheme.colors.plate,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // The plate under the drag handle too, with no edge between them: no seam below the handle.
            dragHandle = { GoaSheetHandle() },
        ) {
            Box(Modifier.fillMaxWidth().slatePlate(RoundedCornerShape(0.dp), brackets = false, edge = false)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding(), content = content)
            }
        }
    }
}

/**
 * A [GoaSheet]'s drag handle on the plate. Exposed so a sheet's screenshot, which draws its content without the
 * sheet's window, shows the handle the player sees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().slatePlate(RoundedCornerShape(0.dp), brackets = false, edge = false),
        contentAlignment = Alignment.Center,
    ) { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Draws [image] into [size], the corners kept and the edges stretched; insets in source pixels and in px. */
private fun DrawScope.drawNineSlice(image: ImageBitmap, src: SliceInsets, dst: SliceInsets, srcTop: Int = 0) {
    val xs = intArrayOf(0, src.left, image.width - src.right, image.width)
    val ys = intArrayOf(srcTop, src.top, image.height - src.bottom, image.height)
    val dx = intArrayOf(0, dst.left, size.width.toInt() - dst.right, size.width.toInt())
    val dy = intArrayOf(0, dst.top, size.height.toInt() - dst.bottom, size.height.toInt())
    for (row in 0..2) {
        for (col in 0..2) {
            val sw = xs[col + 1] - xs[col]
            val sh = ys[row + 1] - ys[row]
            val w = dx[col + 1] - dx[col]
            val h = dy[row + 1] - dy[row]
            if (minOf(sw, sh, w, h) <= 0) continue
            drawImage(
                image,
                srcOffset = IntOffset(xs[col], ys[row]),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(dx[col], dy[row]),
                dstSize = IntSize(w, h),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private data class SliceInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * A title plate's proportions: the parchment's rounded ends ([cap] wide, [vertical] rims) grow with what the plate
 * holds; the gear is [gear] across whatever the plate's height, so it stays round, and the words keep clear of it.
 */
@Immutable
class TitlePlateSize(val minHeight: Dp, val cap: Dp, val vertical: Dp, val gear: Dp, val start: Dp) {
    companion object {
        /** A screen's key title: a question, a phase, a hero's name. */
        val Large = TitlePlateSize(minHeight = 34.dp, cap = 12.dp, vertical = 7.dp, gear = 34.dp, start = 16.dp)

        /** On a grid tile. */
        val Small = TitlePlateSize(minHeight = 25.dp, cap = 9.dp, vertical = 5.dp, gear = 25.dp, start = 9.dp)
    }
}

/**
 * The cards' parchment title plate behind what this holds, with the gear at its end, and the padding that keeps the
 * words off the gear. [plate] is the title plate's art (`rememberOfficialBitmap(LookArt.TITLE_PLATE)`), null while
 * it loads: then the parchment is flat. For a plate with more than one string in it; else use [TitlePlate].
 * Without [gear] the pill ends in its own rounded end. The parchment plate is for a screen's one heading; the gear
 * only for a phase or an outcome heading (a timer phase, a pick, "Match over", a decision), never on a page title, a
 * hero's name in a sheet or a catalog tile, where it would pass for a control or a team mark.
 */
fun Modifier.titlePlate(
    plate: ImageBitmap?,
    size: TitlePlateSize = TitlePlateSize.Large,
    fallback: Color,
    gear: Boolean = true,
): Modifier =
    defaultMinSize(minHeight = size.minHeight)
        .drawWithCache {
            val gearPx = size.gear.toPx()
            val center = Offset(this.size.width - gearPx / 2f, this.size.height / 2f)
            // The parchment runs a little past the gear's middle, under it, as on the cards.
            val pillEnd = if (gear) center.x + GEAR_OVERLAP * gearPx else this.size.width
            val cap = size.cap.roundToPx()
            val rim = size.vertical.roundToPx()
            val cog = if (gear) GearArt(center, gearPx / 2f) else null
            onDrawBehind {
                if (plate != null) {
                    drawPill(plate, pillEnd.toInt(), cap, rim)
                } else {
                    drawRoundRect(
                        fallback,
                        size = androidx.compose.ui.geometry.Size(pillEnd, this.size.height),
                        cornerRadius = CornerRadius(minOf(this.size.height, size.minHeight.toPx()) / 2f),
                    )
                }
                cog?.draw(this)
            }
        }
        .padding(
            start = size.start,
            end = if (gear) size.gear + GEAR_GAP else size.start,
            top = size.vertical - 1.dp,
            bottom =
                size.vertical - 1.dp,
        )

/**
 * The cards' parchment title plate (the pill with the gear at its end), holding a title: a hero's
 * name, what a question asks, a timer phase. Grows with its text; the gear keeps its size and the words keep
 * clear of it. [heading] marks it for TalkBack; [gear] false for the restrained plate (see [titlePlate]).
 */
@Composable
fun TitlePlate(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.plateTitle,
    heading: Boolean = true,
    maxLines: Int = 2,
    gear: Boolean = true,
) {
    val plate = rememberOfficialBitmap(LookArt.TITLE_PLATE)
    val colors = GoaTheme.colors
    Box(
        modifier.titlePlate(plate, TitlePlateSize.Large, colors.parchment, gear),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = style,
            color = colors.onParchment,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = if (heading) Modifier.semantics { heading() } else Modifier,
        )
    }
}

/**
 * The title plate's parchment from its art, without the art's own gear: the left end, the middle stretched, and the
 * left end again mirrored as the right one, so the gear can be drawn at its own size over the plate's end.
 */
private fun DrawScope.drawPill(image: ImageBitmap, end: Int, cap: Int, rim: Int) {
    val h = size.height.toInt()
    val ys = intArrayOf(0, TITLE_SLICE.top, image.height - TITLE_SLICE.bottom, image.height)
    val dy = intArrayOf(0, rim, h - rim, h)
    val midSrc = IntOffset(TITLE_SLICE.left, TITLE_PILL_MIDDLE)
    fun columns(srcX0: Int, srcX1: Int, dstX0: Int, dstX1: Int) {
        for (row in 0..2) {
            val sh = ys[row + 1] - ys[row]
            val dh = dy[row + 1] - dy[row]
            if (sh <= 0 || dh <= 0 || dstX1 <= dstX0) continue
            drawImage(
                image,
                srcOffset = IntOffset(srcX0, ys[row]),
                srcSize = IntSize(srcX1 - srcX0, sh),
                dstOffset = IntOffset(dstX0, dy[row]),
                dstSize = IntSize(dstX1 - dstX0, dh),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
    columns(0, TITLE_SLICE.left, 0, cap)
    columns(midSrc.x, midSrc.y, cap, end - cap)
    // The right end: the left one mirrored in place.
    withTransform({ scale(-1f, 1f, pivot = Offset(end - cap / 2f, 0f)) }) {
        columns(0, TITLE_SLICE.left, end - cap, end)
    }
}

/**
 * The cards' gear, drawn as vectors so it is crisp at any size: steel teeth lit from the upper left, a recessed lip,
 * the dark face where a card prints its tier, and a bronze hub. Built once per size ([drawWithCache]).
 */
private class GearArt(private val middle: Offset, private val r: Float) {
    private val teeth = Path().apply {
        val root = r * GEAR_ROOT
        for (i in 0..GEAR_STEPS) {
            val theta = 2.0 * PI * i / GEAR_STEPS
            val lobe = 0.5 + 0.5 * cos(GEAR_TEETH * theta)
            val t = ((lobe - 0.1) / 0.82).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            val rho = root + (r - root) * t.toFloat()
            val x = middle.x + rho * sin(theta).toFloat()
            val y = middle.y - rho * cos(theta).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val shadow = Path().apply { addPath(teeth, Offset(0f, r * 0.10f)) }
    private val steel = Brush.linearGradient(
        0f to Color(0xFFE6ECEF),
        0.45f to Color(0xFF9BA6AD),
        1f to Color(0xFF465057),
        start = middle + Offset(-r, -r),
        end = middle + Offset(r, r),
    )
    private val lip = Brush.linearGradient(
        listOf(Color(0xFF30373C), Color(0xFFB9C3C9)),
        start = middle + Offset(-r, -r),
        end = middle + Offset(r, r),
    )
    private val face = Brush.radialGradient(
        0f to Color(0xFF4E5860),
        0.55f to Color(0xFF22282C),
        1f to Color(0xFF111416),
        center = middle + Offset(-r * 0.22f, -r * 0.26f),
        radius = r * 0.9f,
    )
    private val hub = Brush.linearGradient(
        listOf(Color(0xFFF2D998), Color(0xFFC9A35B), Color(0xFF7E5D24)),
        start = middle + Offset(-r * 0.22f, -r * 0.22f),
        end = middle + Offset(r * 0.22f, r * 0.22f),
    )
    private val line = maxOf(1f, r * 0.05f)

    fun draw(scope: DrawScope) = with(scope) {
        drawPath(shadow, GEAR_SHADOW)
        drawPath(teeth, steel)
        drawPath(teeth, GEAR_INK, style = Stroke(line))
        drawCircle(lip, radius = r * 0.76f, center = middle)
        drawCircle(face, radius = r * 0.64f, center = middle)
        drawCircle(GEAR_INK, radius = r * 0.64f, center = middle, style = Stroke(line))
        // The light catching the upper left of the teeth and the face.
        drawArc(
            Color.White.copy(alpha = 0.7f),
            startAngle = 195f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = middle - Offset(r * 0.8f, r * 0.8f),
            size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
            style = Stroke(line),
        )
        drawCircle(hub, radius = r * 0.22f, center = middle)
        drawCircle(GEAR_INK, radius = r * 0.22f, center = middle, style = Stroke(line * 0.8f))
        drawCircle(
            Color.White.copy(alpha = 0.55f),
            radius = r * 0.06f,
            center = middle + Offset(-r * 0.08f, -r * 0.08f),
        )
    }
}

/**
 * The cards' parchment text box in a frame colour, with [cap] on its coloured tab: a question's text,
 * printed text. Content sits on parchment, dark on light in both themes.
 */
@Composable
fun CardTextBox(
    color: PlateColor,
    modifier: Modifier = Modifier,
    cap: String? = null,
    /** The [cap] is a heading TalkBack can jump to (a box that stands on its own: a nudge). */
    capHeading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val box = rememberOfficialBitmap(LookArt.textBox(color))
    val colors = GoaTheme.colors
    val darkCap = color == PlateColor.GOLD || color == PlateColor.SILVER
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier.drawWithCache {
            val top = if (cap != null) 20.dp else 6.dp
            val dst = SliceInsets(11.dp.roundToPx(), top.roundToPx(), 11.dp.roundToPx(), 9.dp.roundToPx())
            onDrawBehind {
                if (box != null) {
                    // No word for the tab: no tab, the box starts at its rim.
                    drawNineSlice(box, BOX_SLICE, dst, srcTop = if (cap != null) 0 else BOX_TAB_ROWS)
                } else {
                    drawRoundRect(colors.parchment, cornerRadius = CornerRadius(8.dp.toPx()))
                }
            }
        },
    ) {
        if (cap != null) {
            Text(
                cap,
                style = GoaTextStyles.plateCap.copy(
                    // The tab doesn't grow: its word stops at 1.4× instead of running over the text.
                    fontSize = GoaTextStyles.plateCap.fontSize * (minOf(fontScale, CAP_MAX_SCALE) / fontScale),
                    color = if (darkCap) colors.onParchment else Color.White,
                    shadow = if (darkCap) null else CAP_SHADOW,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .then(if (capHeading) Modifier.semantics { heading() } else Modifier),
            )
        }
        CompositionLocalProvider(LocalContentColor provides colors.onParchment) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (cap != null) 22.dp else 10.dp,
                    bottom = 10.dp,
                ),
                content = content,
            )
        }
    }
}

/** A card frame's colour ribbon (the band the printed values hang on): marks a card colour's section head. */
@Composable
fun ColourRibbon(color: PlateColor, modifier: Modifier = Modifier.size(width = 12.dp, height = 24.dp)) {
    OfficialIcon(LookArt.ribbon(color), modifier.clearAndSetSemantics {})
}

/** A team's official Life counter (rulebook p.5). */
@Composable
fun LifeCounterIcon(team: Team, modifier: Modifier = Modifier, spent: Boolean = false, size: Dp = 26.dp) {
    OfficialIcon(LookArt.lifeCounter(team, spent), modifier.size(size).clearAndSetSemantics {})
}

/** The official Wave counter (rulebook p.5). */
@Composable
fun WaveCounterIcon(modifier: Modifier = Modifier, spent: Boolean = false, size: Dp = 26.dp) {
    OfficialIcon(LookArt.waveCounter(spent), modifier.size(size).clearAndSetSemantics {})
}

/** The bronze coin (rulebook p.5). */
@Composable
fun CoinIcon(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    OfficialIcon(LookArt.COIN, modifier.size(size).clearAndSetSemantics {})
}

/** A rulebook action symbol. */
@Composable
fun ActionIcon(action: LookAction, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    OfficialIcon(LookArt.actionIcon(action), modifier.size(size).clearAndSetSemantics {})
}

/**
 * A title as the rulebook sets its page banners: white metal with a dark outline. For display sizes on
 * the banner and over art, never for body text.
 */
@Composable
fun BevelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.bannerTitle,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Box(modifier) {
        Text(
            text,
            style = style.copy(
                color = BEVEL_OUTLINE,
                drawStyle = Stroke(width = (style.fontSize.value * BEVEL_STROKE).coerceAtLeast(2f) * 2f),
            ),
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text,
            style = style.copy(brush = BEVEL_FILL),
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

/** Room the banner's medallion takes at the end of a regular top bar: actions stay clear of it. */
val BannerMedallionWidth: Dp = 58.dp

/** Room the plain steel end of a compact bar takes ([bannerCompact]). */
val BannerPlainEndWidth: Dp = 16.dp

/** A label over the map or art: a [BevelText] that doesn't take a line of its own. */
@Composable
fun BevelLabel(text: String, modifier: Modifier = Modifier, fontSize: Int = 17) {
    BevelText(
        text,
        modifier,
        style = GoaTextStyles.plateTitle.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 3).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

/** A spacer as wide as the banner's end: its medallion, or a compact bar's plain end ([bannerEndWidth]). */
@Composable
fun BannerEndSpacer() {
    Spacer(Modifier.width(bannerEndWidth()))
}

val PlateShape = RoundedCornerShape(8.dp)
private val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

private val PLATE_GROUND = Color(0xFF1D2124)
private val PLATE_SHADE = Color(0x2E000000)
private val PLATE_EDGE = Color(0xFF5D6B70)
private val PLATE_DARK_EDGE = Color(0xFF15191B)

// The secondary plate on the slate: shaded deeper, a quiet edge. The well: a recess on a plate.
private val SECONDARY_SHADE = Color(0x52000000)
private val SECONDARY_EDGE = Color(0xFF353D42)
private val WELL_DARK = Color(0x47000000)
private val WELL_DARK_EDGE = Color(0x14FFFFFF)

// The light theme's plate: the rulebook's white page with a steel edge (ADR-053).
private val PAPER_TOP = Color(0xFFFEFCF8)
private val PAPER_LOW = Color(0xFFF8F4EB)
private val PAPER_SECONDARY = Color(0xFFF7F3EA)
private val PAPER_HAIRLINE = Color(0xFFD3C9B6)
private val STEEL_EDGE = Color(0xFF6F7C89)
private val STEEL_LIGHT = Color(0xFFB4BEC8)
private val WELL_LIGHT = Color(0x0F5A4A2A)
private val WELL_LIGHT_EDGE = Color(0x1F5A4A2A)

// The gear on the title plate: its size is its own (TitlePlateSize.gear), never the plate's height.
private val GEAR_SHADOW = Color(0x59000000)
private val GEAR_INK = Color(0xFF15191B)
private const val GEAR_ROOT = 0.84f
private const val GEAR_TEETH = 12
private const val GEAR_STEPS = 216

/** How far past the gear's middle the parchment runs under it, as a share of the gear. */
private const val GEAR_OVERLAP = 0.06f
private val GEAR_GAP = 4.dp

/** The title plate's straight middle in its art, clear of the art's own gear. */
private const val TITLE_PILL_MIDDLE = 340

// The redrawn parts' slices (design/look/render_parts.py): corners and middle scale alike at a plate's minimum
// height, so its gear stays round.
private val TITLE_SLICE = SliceInsets(52, 31, 132, 31)

/**
 * All the colours share one geometry. The top inset takes in the tab and the rim under it, so a tall box doesn't
 * stretch the rim over its text.
 */
private val BOX_SLICE = SliceInsets(48, 87, 48, 39)

/** Where a box's rim starts under its tab, in source pixels. */
private const val BOX_TAB_ROWS = 60
private val CAP_SHADOW = Shadow(Color(0xB0000000), Offset(0f, 1f), 2f)
private val BEVEL_OUTLINE = Color(0xFF05090D)
private val BEVEL_FILL = Brush.verticalGradient(
    0.42f to Color.White,
    0.58f to Color(0xFFA9B4BF),
    0.78f to Color.White,
)
private const val BEVEL_STROKE = 0.14f
private const val CAP_MAX_SCALE = 1.4f

/**
 * The slate's flat tone, for what is dark in both themes and can't draw the slate (the map's backing, a card's frame).
 * On a themed plate use `GoaTheme.colors.plate`.
 */
val PlateGround: Color get() = PLATE_GROUND
