package dev.quinton.goa.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.quinton.goa.core.designsystem.R
import dev.quinton.goa.core.designsystem.theme.GoaFonts
import dev.quinton.goa.core.designsystem.theme.LocalGoaIsDark
import dev.quinton.goa.core.designsystem.theme.Spacing
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One place on a [Podium]: its [place] (1 to 3; tied places share a number, and so their step's height and metal),
 * its [label] on the step ("1", "=2"), what TalkBack says for it ([description]), what a tap does, and what stands
 * on the step (a crest or portrait, the name, the figure).
 */
class PodiumPlace(
    val place: Int,
    val label: String,
    val description: String,
    val onClick: (() -> Unit)? = null,
    val onClickLabel: String? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * The top of a leaderboard as a podium: up to three places side by side, the first in the middle on the highest
 * step, the second on its left, the third on its right (a short list reads as standings, not as half a screen of
 * navy). Steps are metal: gold for the first, steel for the second, copper for the third, each with its place in
 * Cinzel, so a place is never told by colour alone; a shared place stands on equal steps ("=2", "=2"). Each place
 * is one TalkBack node and one target. The caller keeps the full rows below it at large text, where three columns
 * don't fit.
 */
@Composable
fun Podium(places: List<PodiumPlace>, modifier: Modifier = Modifier) {
    val shown = places.take(PODIUM_PLACES)
    // Visual order: 2 · 1 · 3; TalkBack reads in rank order (traversal follows the list's own order below).
    val order = when (shown.size) {
        3 -> listOf(1, 0, 2)
        2 -> listOf(1, 0)
        else -> listOf(0)
    }
    Row(
        modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { index ->
            val place = shown[index]
            val click = place.onClick?.let {
                Modifier.clickable(onClickLabel = place.onClickLabel, role = Role.Button, onClick = it)
            } ?: Modifier
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .then(click)
                    .semantics(mergeDescendants = true) {
                        contentDescription = place.description
                        traversalIndex = index.toFloat()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xs).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = place.content,
                )
                Spacer(Modifier.height(Spacing.sm))
                PodiumStep(place.label, place.place)
            }
        }
    }
}

@Composable
private fun PodiumStep(label: String, place: Int) {
    val (metal, height) = when (place) {
        1 -> GOLD to 46.dp
        2 -> STEEL to 32.dp
        else -> COPPER to 22.dp
    }
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(metal.toList()))
            .border(1.dp, STEP_EDGE, shape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            label,
            color = STEP_INK,
            fontFamily = GoaFonts.display,
            fontWeight = FontWeight.Bold,
            fontSize = (if (place == 1) 20 else 16).sp,
            maxLines = 1,
            modifier = Modifier.padding(top = stepTextTop(height)),
        )
    }
}

/**
 * A place's small facts under its figure ("5 wins", "9 games"): on one line with dots between where the column has
 * room, else one fact a line, so a fact is never split ("5 wins · 9" over "games"). Read as one line by TalkBack (the
 * place's own description says it anyway).
 */
@Composable
fun PodiumFacts(facts: List<String>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val measurer = rememberTextMeasurer()
        // Each fact holds together (a no-break space inside it), whichever way the facts are laid out.
        val whole = facts.map { it.replace(' ', '\u00A0') }
        val line = whole.joinToString(" · ")
        val room = constraints.maxWidth
        val oneLine = remember(line, room, style) {
            !measurer.measure(line, style, maxLines = 1, constraints = Constraints(maxWidth = room)).hasVisualOverflow
        }
        Text(
            if (oneLine) line else whole.joinToString("\n"),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

private fun stepTextTop(height: Dp): Dp = if (height > 30.dp) 6.dp else 1.dp

/** At most this many places stand on the podium. */
const val PODIUM_PLACES = 3

private val GOLD = Triple(Color(0xFFF2D795), Color(0xFFC9A35B), Color(0xFF8E6A2C))
private val STEEL = Triple(Color(0xFFEEF2F5), Color(0xFFB7C1CA), Color(0xFF6E7B87))
private val COPPER = Triple(Color(0xFFEDB585), Color(0xFFC07A45), Color(0xFF7E4520))
private val STEP_EDGE = Color(0xFF15181C)
private val STEP_INK = Color(0xFF1B1712)

/**
 * The running timer's painted sea (third_party/ASSETS.md): one cut for portrait, one for landscape, each at full
 * resolution so the cover crop never upscales it. Decorative.
 */
object TimerPaintings {
    @DrawableRes
    fun sea(landscape: Boolean): Int = if (landscape) R.drawable.timer_sea_wide else R.drawable.timer_sea
}

/** The paintings a short screen can end in (`ScreenFoot`). */
enum class FootArt {
    /** The rulebook's broken-swords battlefield (`official/ui/silhouette.webp`): the running timer's own. */
    BATTLEFIELD,

    /** A hall of honour: banners in both teams' colours over the steps to a laurel. Leaderboards. */
    HALL,

    /** Two beacon towers across a strait, messenger birds between them. Group sync. */
    BEACONS,
}

/**
 * A painting faded into the canvas along the foot of a screen whose content is short, so the screen ends in the
 * game's world rather than in empty navy; each kind of screen has its own ([FootArt]: leaderboards the hall of
 * banners, sync the beacons, painted in the official style for the app, `third_party/ASSETS.md`). Put it first in
 * a full-screen [Box] (it draws under what follows). Decorative: TalkBack skips it. Nothing while the art loads.
 */
@Composable
fun ScreenFoot(art: FootArt, modifier: Modifier = Modifier) {
    val field = when (art) {
        FootArt.BATTLEFIELD -> rememberOfficialBitmap(LookArt.SILHOUETTE)
        FootArt.HALL -> rememberPaintingBitmap(R.drawable.foot_hall)
        FootArt.BEACONS -> rememberPaintingBitmap(R.drawable.foot_beacons)
    } ?: return
    val ground = MaterialTheme.colorScheme.background
    val dark = LocalGoaIsDark.current
    // The rulebook's silhouette is printed nearly black and gets lifted; the app's paintings are lit already.
    val official = art == FootArt.BATTLEFIELD
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        val scale = min(w * FOOT_WIDTH / field.width, h * FOOT_MAX_HEIGHT / field.height)
        val drawn = IntSize((field.width * scale).roundToInt(), (field.height * scale).roundToInt())
        val top = h - drawn.height
        drawImage(
            field,
            dstOffset = IntOffset(((w - drawn.width) / 2f).roundToInt(), top.roundToInt()),
            dstSize = drawn,
            alpha = when {
                !dark -> FOOT_ALPHA_LIGHT
                official -> FOOT_ALPHA_DARK
                else -> FOOT_ALPHA_PAINTED
            },
            filterQuality = FilterQuality.Medium,
            colorFilter = if (dark && official) FOOT_LIFT else null,
        )
        // The art rises out of the canvas: solid ground at its top edge, clear by its middle.
        drawRect(
            Brush.verticalGradient(
                0f to ground,
                FOOT_FADE to ground.copy(alpha = 0f),
                startY = top,
                endY = h,
            ),
            topLeft = Offset(0f, top),
            size = Size(w, drawn.height.toFloat()),
        )
    }
}

/**
 * One of the app's own paintings ([TimerPaintings], the foot paintings), decoded off the main thread once per
 * process; null while it decodes, and if it fails. It loads through the app's image loader, so whatever waits for
 * images to settle (a screenshot test) waits for the painting too.
 */
@Composable
fun rememberPaintingBitmap(@DrawableRes res: Int): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(res) { mutableStateOf(footBitmaps[res]) }
    if (bitmap == null) {
        LaunchedEffect(res) {
            bitmap = footBitmaps[res] ?: run {
                val app = context.applicationContext
                val request = ImageRequest.Builder(app).data(res).allowHardware(false).build()
                val result = SingletonImageLoader.get(app).execute(request)
                (result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()?.also { footBitmaps[res] = it }
            }
        }
    }
    return bitmap
}

private val footBitmaps = java.util.concurrent.ConcurrentHashMap<Int, ImageBitmap>()

private const val FOOT_WIDTH = 1.15f
private const val FOOT_MAX_HEIGHT = 0.36f
private const val FOOT_FADE = 0.55f
private const val FOOT_ALPHA_DARK = 0.6f
private const val FOOT_ALPHA_PAINTED = 0.85f
private const val FOOT_ALPHA_LIGHT = 0.16f

/** The silhouette is printed nearly black: lifted so it reads on the navy (as the timer lifts it). */
private val FOOT_LIFT = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(1.6f, 1.6f, 1.6f, 1f) })
