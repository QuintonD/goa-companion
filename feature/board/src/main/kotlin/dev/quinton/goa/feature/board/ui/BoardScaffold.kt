package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.steelBanner
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * The frame of a board screen (the sandbox, a game, a puzzle): [GoaScreen]'s look with a compact
 * title bar, so the map gets the height. 48 dp with an 18 sp title in portrait, 40 dp with a 16 sp
 * title in landscape, where height is scarcest; it grows only when large text needs it. A title too
 * long for one line ("Arien, Brogan, Xargatha vs Wasp, Tigerclaw, Sabina") takes two lines at a
 * smaller size (13 sp) inside the same height; TalkBack hears it whole. Icons keep
 * their 48 dp touch targets, overlapping the bar's visible height. The content keeps clear of a
 * display cutout the app shell left (landscape, the camera side).
 *
 * [GoaScreen]: dev.quinton.goa.core.designsystem.component.GoaScreen
 */
@Composable
internal fun BoardScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BoardTopBar(title, onBack, actions) },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        // The shell has applied the system bars; what is left is a cutout at a side.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        content = content,
    )
}

/** True when the window is wider than tall: the board screens' landscape layout. */
@Composable
internal fun isLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

@Composable
private fun BoardTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val landscape = isLandscape()
    val base = MaterialTheme.typography.titleLarge
    val bar = if (landscape) LANDSCAPE_BAR else PORTRAIT_BAR
    val oneLine = if (landscape) {
        base.copy(fontSize = 16.sp, lineHeight = 20.sp)
    } else {
        base.copy(fontSize = 18.sp, lineHeight = 24.sp)
    }
    val small = base.copy(fontSize = 13.sp, lineHeight = 16.sp)
    val density = LocalDensity.current
    // Too long for one line: two smaller ones, when they fit the bar (not with large text, where
    // the bar would have to grow; the title is cut there and TalkBack still hears it whole).
    val twoFit = with(density) { small.lineHeight.toDp() } * 2 + TWO_LINE_AIR <= bar
    var tooLong by remember(title, landscape) { mutableStateOf(false) }
    val twoLines = tooLong && twoFit
    val style = if (twoLines) small else oneLine
    val lines = if (twoLines) 2 else 1
    // Large text may need more than the bar's height: one line of the title plus a little air.
    val textHeight = with(density) { oneLine.lineHeight.toDp() } + TITLE_AIR
    val height: Dp = maxOf(bar, textHeight)
    // GoaScreen's steel banner (the rulebook's page banner), compact.
    Column(
        Modifier
            .steelBanner(GoaTheme.colors, MaterialTheme.colorScheme.primary)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides GoaTheme.colors.onBanner) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = title,
                    style = style,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!tooLong && it.hasVisualOverflow) tooLong = true },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 4.dp)
                        .semantics { heading() },
                )
            }
            CompositionLocalProvider(LocalContentColor provides GoaTheme.colors.onBannerVariant) {
                actions()
            }
        }
    }
}

private val PORTRAIT_BAR = 48.dp
private val LANDSCAPE_BAR = 40.dp
private val TITLE_AIR = 8.dp
private val TWO_LINE_AIR = 4.dp
