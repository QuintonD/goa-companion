package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaExtendedColors
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * Standard screen frame: the steel banner (the rulebook's page banner) holding the title in
 * engraved Cinzel caps, the back arrow when [onBack] is set and [actions], over the content. The
 * app shell owns the bottom bar and window insets, so content gets its own padding only.
 *
 * [actions]: at most two glyphs, each with a label (`BarActions` puts the rest in a labelled overflow menu;
 * `BarIconButton` for a single one). App-wide destinations (Rules, Group sync, Settings) live on More, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = GoaTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = {
            SteelBanner {
                TopAppBar(
                    title = {
                        BannerTitle(text = title, modifier = Modifier.semantics { heading() })
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            BarIconButton(label = "Back", onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        actions()
                        BannerEndSpacer()
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = colors.onBanner,
                        navigationIconContentColor = colors.onBanner,
                        actionIconContentColor = colors.onBannerVariant,
                    ),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/**
 * The rulebook's steel banner as a background: its band stretched across, its star medallion at the
 * end, whatever the bar's size (the gradient only while it loads). On a narrow phone or with large text
 * ([bannerCompact]) the band ends in the frame's plain steel end instead, so the medallion never takes room from
 * the title and actions. Text and icons on it use `GoaTheme.colors.onBanner` / `onBannerVariant`; keep them clear
 * of the end ([BannerEndSpacer]).
 */
@Composable
fun SteelBanner(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.steelBanner(GoaTheme.colors, MaterialTheme.colorScheme.primary), content = content)
}

/**
 * Whether bars are compact: a phone narrower than 380 dp or text at 1.5x and more. Compact bars end without the
 * medallion (ADR-058 keeps it in the regular bar).
 */
@Composable
@ReadOnlyComposable
fun bannerCompact(): Boolean = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP ||
    LocalDensity.current.fontScale >= COMPACT_FONT_SCALE

/** The room the banner's end takes at the end of a bar: the medallion, or the plain end of a compact bar. */
@Composable
@ReadOnlyComposable
fun bannerEndWidth(): Dp = if (bannerCompact()) BannerPlainEndWidth else BannerMedallionWidth

/** [SteelBanner]'s background for a layout of the caller's own (pass `GoaTheme.colors` and primary). */
fun Modifier.steelBanner(colors: GoaExtendedColors, bronze: Color): Modifier = composed {
    val art = rememberOfficialBitmap(LookArt.BANNER)
    val compact = bannerCompact()
    drawWithCache {
        val band = Brush.verticalGradient(listOf(colors.bannerTop, colors.bannerBottom))
        val rule = Brush.horizontalGradient(
            0f to bronze.copy(alpha = 0.15f),
            0.5f to bronze.copy(alpha = 0.75f),
            1f to bronze.copy(alpha = 0.15f),
        )
        val edge = 1.dp.toPx()
        onDrawBehind {
            if (art == null) {
                drawRect(band)
                drawRect(colors.bannerHighlight.copy(alpha = 0.45f), size = Size(size.width, edge))
                drawRect(rule, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
            } else {
                drawRect(colors.bannerBottom)
                val h = size.height.toInt()
                // The art's end: the medallion's cap, or (compact) only the frame's rounded steel end past it.
                val endRatio = if (compact) PLAIN_END_RATIO else BANNER_CAP_RATIO
                val capPx = (art.height * endRatio).toInt()
                val capW = (h * endRatio).toInt()
                val bodyW = (size.width.toInt() - capW).coerceAtLeast(0)
                val bodyPx = art.width - (art.height * BANNER_CAP_RATIO).toInt()
                drawImage(
                    art,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bodyPx, art.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(bodyW, h),
                    filterQuality = FilterQuality.Medium,
                )
                drawImage(
                    art,
                    srcOffset = IntOffset(art.width - capPx, 0),
                    srcSize = IntSize(capPx, art.height),
                    dstOffset = IntOffset(bodyW, 0),
                    dstSize = IntSize(capW, h),
                    filterQuality = FilterQuality.Medium,
                )
                if (compact) {
                    // The end's corners hold the edge of the medallion's ring and a stud: the band runs over them,
                    // up to the frame's inner edge, so the end reads as the frame closing the band.
                    val top = (art.height * PLAIN_INNER_TOP).toInt()
                    val bottom = (art.height * PLAIN_INNER_BOTTOM).toInt()
                    val innerPx = (art.height * PLAIN_INNER_RATIO).toInt()
                    val dstTop = (h * PLAIN_INNER_TOP).toInt()
                    drawImage(
                        art,
                        srcOffset = IntOffset(bodyPx, top),
                        srcSize = IntSize(innerPx, bottom - top),
                        dstOffset = IntOffset(bodyW, dstTop),
                        dstSize = IntSize((h * PLAIN_INNER_RATIO).toInt(), (h * PLAIN_INNER_BOTTOM).toInt() - dstTop),
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    // The medallion is the band's ornament, not a control: its disc is toned down into the band so
                    // it reads as part of the frame rather than a raised button.
                    val centre = Offset(bodyW + capW * MEDALLION_X, h * MEDALLION_Y)
                    val radius = h * MEDALLION_R
                    drawCircle(
                        Brush.radialGradient(
                            0f to colors.bannerBottom.copy(alpha = MEDALLION_DIM),
                            0.86f to colors.bannerBottom.copy(alpha = MEDALLION_DIM),
                            1f to Color.Transparent,
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                }
            }
        }
    }
}

/** How wide the medallion is, in banner heights. */
private const val BANNER_CAP_RATIO = 1.35f

/**
 * Compact bars: how wide the frame's rounded end is (with its corners), in banner heights, and the part inside the
 * frame that the band covers (its width, and its top and bottom as shares of the height).
 */
private const val PLAIN_END_RATIO = 0.208f
private const val PLAIN_INNER_RATIO = 0.117f
private const val PLAIN_INNER_TOP = 0.075f
private const val PLAIN_INNER_BOTTOM = 0.854f

/** Below this width, or from this text scale, bars are compact. */
private const val COMPACT_WIDTH_DP = 380
private const val COMPACT_FONT_SCALE = 1.5f

/** The medallion's centre in the cap (share of its width and height) and its radius in banner heights. */
private const val MEDALLION_X = 0.64f
private const val MEDALLION_Y = 0.46f
private const val MEDALLION_R = 0.4f

/** How far the medallion's disc is toned into the band. */
private const val MEDALLION_DIM = 0.42f

/** Hairline bronze rule that fades out at both ends; separates bars from content. */
@Composable
fun EngravedRule(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.2f to c.copy(alpha = 0.35f),
                    0.8f to c.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** Temporary body for screens not built yet. Must not ship (PRODUCT.md quality bar). */
@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    GoaScreen(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("$title — coming soon", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
