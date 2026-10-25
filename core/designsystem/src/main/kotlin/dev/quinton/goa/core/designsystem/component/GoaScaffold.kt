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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaExtendedColors
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
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
                        Text(
                            text = title,
                            style = GoaTextStyles.bannerTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
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
 * The rulebook's steel banner as a background: a steel-blue band, darker toward the bottom, with
 * a bright steel edge on top and a dark edge plus a bronze hairline below. Text and icons on it use
 * `GoaTheme.colors.onBanner` / `onBannerVariant` (checked in both themes, docs/DESIGN.md).
 */
@Composable
fun SteelBanner(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.steelBanner(GoaTheme.colors, MaterialTheme.colorScheme.primary), content = content)
}

/** [SteelBanner]'s background for a layout of the caller's own (pass `GoaTheme.colors` and primary). */
fun Modifier.steelBanner(colors: GoaExtendedColors, bronze: Color): Modifier = this.then(
    Modifier.drawWithCache {
        val band = Brush.verticalGradient(listOf(colors.bannerTop, colors.bannerBottom))
        val sheen = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to colors.bannerHighlight.copy(alpha = 0.10f),
            1f to Color.Transparent,
        )
        val rule = Brush.horizontalGradient(
            0f to bronze.copy(alpha = 0.15f),
            0.5f to bronze.copy(alpha = 0.75f),
            1f to bronze.copy(alpha = 0.15f),
        )
        val edge = 1.dp.toPx()
        onDrawBehind {
            drawRect(band)
            drawRect(sheen, size = Size(size.width, size.height / 2f))
            drawRect(colors.bannerHighlight.copy(alpha = 0.45f), size = Size(size.width, edge))
            drawRect(
                colors.bannerShadow,
                topLeft = Offset(0f, size.height - 2 * edge),
                size = Size(size.width, edge),
            )
            drawRect(rule, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
        }
    },
)

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
