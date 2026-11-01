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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaExtendedColors
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * Standard screen frame: the steel banner (the rulebook's page banner) holding the title in
 * engraved Cinzel caps, the back arrow when [onBack] is set and [actions], over the content. The
 * app shell owns the bottom bar and window insets, so content gets its own padding only.
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
                        BevelText(text = title, modifier = Modifier.semantics { heading() })
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
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
 * end, whatever the bar's size (the gradient only while it loads). Text and icons on it use
 * `GoaTheme.colors.onBanner` / `onBannerVariant`; keep them clear of the medallion ([BannerEndSpacer]).
 */
@Composable
fun SteelBanner(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.steelBanner(GoaTheme.colors, MaterialTheme.colorScheme.primary), content = content)
}

/** [SteelBanner]'s background for a layout of the caller's own (pass `GoaTheme.colors` and primary). */
fun Modifier.steelBanner(colors: GoaExtendedColors, bronze: Color): Modifier = composed {
    val art = rememberOfficialBitmap(LookArt.BANNER)
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
                val capPx = (art.height * BANNER_CAP_RATIO).toInt()
                val capW = (h * BANNER_CAP_RATIO).toInt()
                val bodyW = (size.width.toInt() - capW).coerceAtLeast(0)
                drawImage(
                    art,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(art.width - capPx, art.height),
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
            }
        }
    }
}

/** How wide the medallion is, in banner heights. */
private const val BANNER_CAP_RATIO = 1.35f

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
