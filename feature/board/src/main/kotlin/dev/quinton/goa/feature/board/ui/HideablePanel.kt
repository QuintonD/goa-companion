package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.map.MapViewport

/**
 * What a hidden panel still says, in one line: "◆ Arien · Choose an action for Dangerous Current".
 * [label] (a hero's name, in [team]'s colour with its shape) leads [text]; [attention] marks a
 * question that waits for the person (a teal dot and a teal border).
 */
@Immutable
data class PanelPeek(
    val text: String,
    val label: String? = null,
    val team: Team? = null,
    val attention: Boolean = false,
)

/** How much of the map the panel covers, px: its height at the bottom, its width at the end. */
@Immutable
data class PanelCover(val bottom: Int = 0, val end: Int = 0)

/**
 * The board screens' panel (the sandbox's selection, a game's question, a puzzle), shared so all
 * three hide the same way. Shown: at the bottom in portrait (at most [maxShare] of the height) or
 * at the end in landscape ([sideWidth] wide), under a strip with a drag handle and a Hide button
 * (drag it down, or to the side in landscape). Hidden: a slim peek bar at the bottom in portrait,
 * a small pill at the bottom end in landscape, with [peek]'s one line, [peekTrailing] (e.g. a
 * Confirm button) and Show; tap or drag it up to show the panel again. [onCover] reports what the
 * map must keep clear of.
 */
@Composable
internal fun BoxWithConstraintsScope.HideablePanel(
    landscape: Boolean,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    peek: PanelPeek,
    sideWidth: Dp,
    maxShare: Float,
    onCover: (PanelCover) -> Unit,
    peekLeading: (@Composable () -> Unit)? = null,
    peekTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!hidden) {
        val modifier = if (landscape) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(sideWidth)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * maxShare)
        }
        Surface(
            modifier = modifier.onSizeChanged {
                onCover(if (landscape) PanelCover(end = it.width) else PanelCover(bottom = it.height))
            },
            shape = if (landscape) {
                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            },
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            shadowElevation = 6.dp,
        ) {
            Column {
                PanelStrip(landscape, onHide = { onHiddenChange(true) })
                content()
            }
        }
    } else {
        // Portrait: a bar across the bottom. Landscape: a narrow card at the bottom end; the map is
        // limited by its height there, so keeping clear of the card's column costs it nothing.
        val modifier = if (landscape) {
            Modifier
                .align(Alignment.BottomEnd)
                .onSizeChanged { onCover(PanelCover(end = it.width)) }
                .padding(Spacing.sm)
                .width(minOf(sideWidth, RAIL_WIDTH))
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { onCover(PanelCover(bottom = it.height)) }
        }
        PeekBar(
            peek = peek,
            landscape = landscape,
            onShow = { onHiddenChange(false) },
            leading = peekLeading,
            trailing = peekTrailing,
            modifier = modifier,
        )
    }
}

/** The top of a shown panel: a drag handle and Hide. */
@Composable
private fun PanelStrip(landscape: Boolean, onHide: () -> Unit) {
    val hide by rememberUpdatedState(onHide)
    val threshold = with(LocalDensity.current) { DRAG_TO_TOGGLE.toPx() }
    // Toward the end (right in LTR) hides a side panel.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var dragged by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { dragged += it }
    Box(
        Modifier
            .fillMaxWidth()
            .height(STRIP)
            .draggable(
                drag,
                orientation = if (landscape) Orientation.Horizontal else Orientation.Vertical,
                onDragStarted = { dragged = 0f },
                onDragStopped = { if (dragged * (if (landscape) sign else 1f) > threshold) hide() },
            ),
    ) {
        if (!landscape) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape),
            )
        }
        // A 48 dp touch target overlapping the strip's 40 dp.
        IconButton(onClick = onHide, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.xs)) {
            Icon(
                if (landscape) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore,
                contentDescription = "Hide panel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The hidden panel: what it holds and Show. Portrait: one line across the bottom. Landscape: a
 * narrow card, the text on up to three lines over the buttons.
 */
@Composable
private fun PeekBar(
    peek: PanelPeek,
    landscape: Boolean,
    onShow: () -> Unit,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    val show by rememberUpdatedState(onShow)
    val threshold = with(LocalDensity.current) { DRAG_TO_TOGGLE.toPx() }
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var dragged by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { dragged += it }
    val accent = MaterialTheme.colorScheme.secondary
    Surface(
        modifier = modifier.draggable(
            drag,
            orientation = if (landscape) Orientation.Horizontal else Orientation.Vertical,
            onDragStarted = { dragged = 0f },
            // Up (or toward the start in landscape) shows the panel.
            onDragStopped = { if (-dragged * (if (landscape) sign else 1f) > threshold) show() },
        ),
        shape = if (landscape) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (peek.attention) 1.5.dp else 1.dp,
            if (peek.attention) accent else GoaTheme.colors.engravedBorder,
        ),
        shadowElevation = 6.dp,
    ) {
        val showButton = @Composable {
            IconButton(onClick = onShow) {
                Icon(
                    if (landscape) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.Outlined.ExpandLess,
                    contentDescription = "Show panel",
                )
            }
        }
        if (landscape) {
            Column(Modifier.padding(Spacing.xs)) {
                PeekText(peek, leading, maxLines = RAIL_LINES, onShow = onShow, modifier = Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailing?.invoke()
                    showButton()
                }
            }
        } else {
            Row(
                Modifier
                    .heightIn(min = PEEK_HEIGHT)
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeekText(peek, leading, maxLines = 1, onShow = onShow, modifier = Modifier.weight(1f))
                trailing?.invoke()
                showButton()
            }
        }
    }
}

/**
 * The peek's words: a teal tap mark when the question waits for the person, [leading] (the
 * sandbox's piece), the team's shape, the label in its colour, then the text. A tap shows the
 * panel; TalkBack hears a new question while the panel is hidden.
 */
@Composable
private fun PeekText(
    peek: PanelPeek,
    leading: (@Composable () -> Unit)?,
    maxLines: Int,
    onShow: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Show panel", onClick = onShow)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (peek.attention) {
            Icon(
                Icons.Outlined.TouchApp,
                contentDescription = "Your move",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(scaledIconSize(20.dp, 1.4f)),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        leading?.let {
            it()
            Spacer(Modifier.width(Spacing.sm))
        }
        peek.team?.let {
            TeamMark(it, markSize = scaledIconSize(9.dp))
            Spacer(Modifier.width(4.dp))
        }
        val labelColor = peek.team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.primary
        Text(
            buildAnnotatedString {
                peek.label?.let { label ->
                    withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(label) }
                    if (peek.text.isNotEmpty()) append(" · ")
                }
                append(peek.text)
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * While the panel is [hidden], the whole map in the larger view: fitted once when the panel hides
 * (and again as what floats over the map settles), unless the person has moved the view since it
 * was last framed; then their view stays and Fit is one tap away.
 */
@Composable
internal fun FitWhileHidden(viewport: MapViewport, hidden: Boolean) {
    LaunchedEffect(hidden, viewport.unit, viewport.bottomInset, viewport.endInset, viewport.topInset) {
        if (hidden && viewport.unit > 0f && !viewport.userMoved) viewport.animateFit()
    }
}

private val STRIP = 40.dp
private val PEEK_HEIGHT = 56.dp
private val DRAG_TO_TOGGLE = 32.dp

/** The landscape peek card's width (at most the side panel's). */
private val RAIL_WIDTH = 248.dp
private const val RAIL_LINES = 3
