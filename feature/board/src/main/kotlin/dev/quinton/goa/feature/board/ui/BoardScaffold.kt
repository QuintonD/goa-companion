package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BannerEndSpacer
import dev.quinton.goa.core.designsystem.component.BevelText
import dev.quinton.goa.core.designsystem.component.steelBanner
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/**
 * The frame of a board screen (the sandbox, a game, a puzzle): [GoaScreen]'s look with a compact
 * title bar, so the map gets the height. 48 dp with an 18 sp title in portrait, 40 dp with a 16 sp
 * title in landscape, where height is scarcest; it grows only when large text needs it. A title too
 * long for one line ("Arien, Brogan, Xargatha vs Wasp, Tigerclaw, Sabina") takes two lines at a
 * smaller size (13 sp) inside the same height; TalkBack hears it whole. A [subtitle] (the sandbox's map) is
 * a small line of app text under a title that takes one line (the bar then 52 dp, more with large text, so the line
 * clears the banner's bevelled edge). With [shortTitles] ("2 v 2 · Forgotten Island", "2 v 2 board") a title too
 * long for one line is never set small or cut: the first short title that fits one line takes its place, and under
 * it the whole title in Inter where it fits whole, else [lineup] (the heroes' portraits), else the subtitle. Icons keep
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
    subtitle: String? = null,
    shortTitles: List<String> = emptyList(),
    lineup: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BoardTopBar(title, subtitle, shortTitles, lineup, onBack, actions) },
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
private fun BoardTopBar(
    title: String,
    subtitle: String?,
    shortTitles: List<String>,
    lineup: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val landscape = isLandscape()
    val base = GoaTextStyles.bannerTitle
    val bar = if (landscape) LANDSCAPE_BAR else PORTRAIT_BAR
    // The bar's width doesn't grow: past 1.25× a bevelled title only gets cut, and TalkBack hears it whole anyway.
    val scale = LocalDensity.current.fontScale
    val capped = minOf(scale, TITLE_MAX_SCALE) / scale
    val oneLine = if (landscape) {
        base.copy(fontSize = 16.sp * capped, lineHeight = 20.sp * capped)
    } else {
        base.copy(fontSize = 18.sp * capped, lineHeight = 24.sp * capped)
    }
    val small = base.copy(fontSize = 13.sp * capped, lineHeight = 16.sp * capped)
    val sub = MaterialTheme.typography.bodySmall.let {
        it.copy(fontSize = it.fontSize * capped, lineHeight = it.lineHeight * capped)
    }
    val density = LocalDensity.current
    // Too long for one line: two smaller ones, when they fit the bar.
    val twoFit = with(density) { small.lineHeight.toDp() } * 2 + TWO_LINE_AIR <= bar
    // Large text may need more than the bar's height: one line of the title plus a little air. A bar that can put
    // a short title over the line-up grows to hold both, and keeps them off the banner's bevelled lower edge.
    val textHeight = with(density) { oneLine.lineHeight.toDp() } + TITLE_AIR
    val twoLineHeight = with(density) { oneLine.lineHeight.toDp() + sub.lineHeight.toDp() } + TITLE_AIR + EDGE_AIR
    val height: Dp = maxOf(bar, if (shortTitles.isNotEmpty() || subtitle != null) twoLineHeight else textHeight)
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
                BoxWithConstraints(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)) {
                    val measurer = rememberTextMeasurer()
                    val room = constraints.maxWidth
                    fun fits(text: String, style: TextStyle) =
                        !measurer.measure(text, style, maxLines = 1, constraints = Constraints(maxWidth = room))
                            .hasVisualOverflow
                    val tooLong = remember(title, room, oneLine) { !fits(title, oneLine) }
                    val short = remember(title, shortTitles, room, oneLine) {
                        if (tooLong) shortTitles.firstOrNull { fits(it, oneLine) } else null
                    }
                    if (short != null) {
                        // The short title, and the whole one under it where it fits whole.
                        // The line-up in words: Inter, tracking closed, a step at a time down to the banner's
                        // smallest line before the portraits stand in for the names.
                        val tight = sub.copy(letterSpacing = 0.sp)
                        val under = remember(title, subtitle, room, sub) {
                            underSizes(tight, capped).firstOrNull { fits(title, it) }?.let { title to it }
                                ?: subtitle?.let { it to sub }?.takeIf { (t, st) -> fits(t, st) }
                        }
                        Column(
                            Modifier.padding(bottom = EDGE_AIR).semantics(mergeDescendants = true) {
                                heading()
                                // TalkBack hears the whole title even where only the short one shows.
                                if (under?.first != title) contentDescription = title
                            },
                        ) {
                            BevelText(text = short, style = oneLine, maxLines = 1)
                            when {
                                under != null && under.first == title -> Text(
                                    under.first,
                                    style = under.second,
                                    color = GoaTheme.colors.onBannerVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // The line-up as its heroes' portraits, when its words don't fit.
                                lineup != null -> lineup()

                                under != null -> Text(
                                    under.first,
                                    style = under.second,
                                    color = GoaTheme.colors.onBannerVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else if (subtitle != null && !tooLong) {
                        Column(Modifier.padding(bottom = EDGE_AIR).semantics(mergeDescendants = true) { heading() }) {
                            BevelText(text = title, style = oneLine, maxLines = 1)
                            Text(
                                subtitle,
                                style = sub,
                                color = GoaTheme.colors.onBannerVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        val twoLines = tooLong && twoFit
                        BevelText(
                            text = title,
                            style = if (twoLines) small else oneLine,
                            maxLines = if (twoLines) 2 else 1,
                            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                        )
                    }
                }
            }
            CompositionLocalProvider(LocalContentColor provides GoaTheme.colors.onBannerVariant) {
                actions()
            }
            BannerEndSpacer()
        }
    }
}

private val PORTRAIT_BAR = 48.dp

/** The smallest the line under the short title gets (at 1×) before the line-up shows as portraits. */
private val UNDER_MIN = 12.sp

/** The line under the short title from its own size down to [UNDER_MIN] (scaled as the bar's text is), 1 sp a step. */
private fun underSizes(style: TextStyle, capped: Float): List<TextStyle> {
    val top = style.fontSize.value
    val floor = UNDER_MIN.value * capped
    val ratio = style.lineHeight.value / top
    return generateSequence(top) { it - capped }
        .takeWhile { it >= floor - 0.01f }
        .map { size -> style.copy(fontSize = size.sp, lineHeight = (size * ratio).sp) }
        .toList()
        .ifEmpty { listOf(style) }
}

/** Room kept above the banner's bevelled lower edge under a second line. */
private val EDGE_AIR = 4.dp
private val LANDSCAPE_BAR = 40.dp
private val TITLE_AIR = 8.dp
private const val TITLE_MAX_SCALE = 1.25f
private val TWO_LINE_AIR = 4.dp
