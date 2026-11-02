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
 * Plates stay dark in both themes, like the steel banner and the cards themselves, so what sits on
 * one is themed dark. Everything inside a [GoaPlate] or [GoaSheet] gets this already.
 */
@Composable
fun GoaPlateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGoaColors provides DarkExtended, LocalGoaIsDark provides true) {
        MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            CompositionLocalProvider(LocalContentColor provides DarkColors.onSurface, content = content)
        }
    }
}

/**
 * The dark plate the cards and dashboards are printed on, as a background: the slate, a steel edge
 * and, with [brackets], the small teal corner brackets. Pair it with [GoaPlateTheme] (or use
 * [GoaPlate]): its text must be themed dark in the light theme too. Without [edge], just the slate, for
 * parts that continue one another (a sheet's drag handle and its body).
 */
fun Modifier.slatePlate(shape: Shape = PlateShape, brackets: Boolean = true, edge: Boolean = true): Modifier =
    this then SlatePlateElement(shape, brackets, edge)

private data class SlatePlateElement(val shape: Shape, val brackets: Boolean, val edge: Boolean) :
    ModifierNodeElement<SlatePlateNode>() {
    override fun create() = SlatePlateNode(shape, brackets, edge)

    override fun update(node: SlatePlateNode) = node.update(shape, brackets, edge)

    override fun InspectorInfo.inspectableProperties() {
        name = "slatePlate"
    }
}

/** A node, not a `composed` modifier: a screen of plates in a list adds nothing to its composition. */
private class SlatePlateNode(private var shape: Shape, private var brackets: Boolean, private var edge: Boolean) :
    DelegatingNode(),
    CompositionLocalConsumerModifierNode {
    private var slate: ImageBitmap? = null
    private var corners: List<ImageBitmap?> = emptyList()
    private val cache = delegate(CacheDrawModifierNode { buildDrawing() })

    override fun onAttach() = takeArt()

    fun update(shape: Shape, brackets: Boolean, edge: Boolean) {
        val art = brackets != this.brackets
        this.shape = shape
        this.brackets = brackets
        this.edge = edge
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
        val texture = slate?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
        val outer = Stroke(width = 1.dp.toPx())
        val inner = Stroke(width = 3.dp.toPx())
        val bracket = 16.dp.toPx()
        val inset = 2.dp.toPx()
        val edge = edge
        val corners = corners
        return onDrawBehind {
            clipPath(clip) {
                drawRect(PLATE_GROUND)
                if (texture != null) {
                    drawRect(texture)
                    drawRect(PLATE_SHADE)
                }
                if (edge) drawPath(clip, PLATE_DARK_EDGE, style = inner)
            }
            if (edge) drawPath(clip, PLATE_EDGE, style = outer)
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

/** A dark plate holding [content], themed dark. With [onClick] the whole plate is one button. */
@Composable
fun GoaPlate(
    modifier: Modifier = Modifier,
    shape: Shape = PlateShape,
    brackets: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
    GoaPlateTheme {
        Column(modifier.slatePlate(shape, brackets).then(click).padding(contentPadding), content = content)
    }
}

/**
 * A bottom sheet on the dark plate. Use this rather than `ModalBottomSheet`, so a sheet looks the same
 * everywhere and its contents are themed dark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    GoaPlateTheme {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = SheetShape,
            containerColor = PLATE_GROUND,
            contentColor = DarkColors.onSurface,
            // The slate under the drag handle too, with no edge between them: no seam below the handle.
            dragHandle = {
                Box(
                    Modifier.fillMaxWidth().slatePlate(RoundedCornerShape(0.dp), brackets = false, edge = false),
                    contentAlignment = Alignment.Center,
                ) { BottomSheetDefaults.DragHandle(color = DarkColors.onSurfaceVariant) }
            },
        ) {
            Box(Modifier.fillMaxWidth().slatePlate(RoundedCornerShape(0.dp), brackets = false, edge = false)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding(), content = content)
            }
        }
    }
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
 * The cards' parchment title plate (the pill with the gear at its end), holding a title: a hero's
 * name, what a question asks, a timer phase. Grows with its text; [heading] marks it for TalkBack.
 */
@Composable
fun TitlePlate(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.plateTitle,
    heading: Boolean = true,
    maxLines: Int = 2,
) {
    val plate = rememberOfficialBitmap(LookArt.TITLE_PLATE)
    val colors = GoaTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = 34.dp)
            .drawWithCache {
                val dst = SliceInsets(12.dp.roundToPx(), 7.dp.roundToPx(), 30.dp.roundToPx(), 7.dp.roundToPx())
                onDrawBehind {
                    if (plate != null) {
                        drawNineSlice(plate, TITLE_SLICE, dst)
                    } else {
                        drawRoundRect(
                            colors.parchment,
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                }
            }
            .padding(start = 16.dp, end = 30.dp, top = 6.dp, bottom = 6.dp),
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
 * The cards' parchment text box in a frame colour, with [cap] on its coloured tab: a question's text,
 * printed text. Content sits on parchment, dark on light in both themes.
 */
@Composable
fun CardTextBox(
    color: PlateColor,
    modifier: Modifier = Modifier,
    cap: String? = null,
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
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 1.dp),
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

/** Room the banner's medallion takes at the end of a top bar: actions stay clear of it. */
val BannerMedallionWidth: Dp = 58.dp

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

/** A spacer as wide as the banner's medallion. */
@Composable
fun BannerEndSpacer() {
    Spacer(Modifier.width(BannerMedallionWidth))
}

val PlateShape = RoundedCornerShape(8.dp)
private val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

private val PLATE_GROUND = Color(0xFF1D2124)
private val PLATE_SHADE = Color(0x2E000000)
private val PLATE_EDGE = Color(0xFF5D6B70)
private val PLATE_DARK_EDGE = Color(0xFF15191B)

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

/** The plate's flat tone, for what can't draw the slate (a canvas's backing, a fallback). */
val PlateGround: Color get() = PLATE_GROUND
