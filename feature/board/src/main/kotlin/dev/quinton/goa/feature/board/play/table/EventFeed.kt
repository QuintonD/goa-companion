package dev.quinton.goa.feature.board.play.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The table's feed (ADR-065): each new action, move, attack, defeat… pops in over the map as a notice,
 * the newest on top, and fades as newer ones come in until it leaves ([FeedPacing]). A line not to miss is
 * semibold with a bronze edge and stays longer. It never takes a tap (the map under it keeps them) and
 * TalkBack skips it: the screen's announcement already says what happened.
 */
@Composable
fun EventFeed(lines: List<LogLine>, modifier: Modifier = Modifier) {
    val notices = remember { mutableStateListOf<Notice>() }
    var seen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(lines) {
        if (seen?.let { lines.size < it } == true) notices.forEach { it.shown.targetState = false }
        feedSince(lines, seen).takeLast(FeedPacing.MAX).forEach { notices += Notice(it.index, it.value) }
        seen = lines.size
        // Past the most at once, the oldest go.
        notices.filter { it.shown.targetState }.dropLast(FeedPacing.MAX).forEach { it.shown.targetState = false }
    }
    Column(
        modifier.widthIn(max = FEED_WIDTH).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val showing = notices.filter { it.shown.targetState }
        notices.asReversed().forEach { notice ->
            key(notice.index) {
                LaunchedEffect(Unit) {
                    delay(if (notice.line.strong) FeedPacing.STRONG_MS else FeedPacing.SHOW_MS)
                    notice.shown.targetState = false
                }
                // Gone once it has faded out.
                if (notice.shown.isIdle && !notice.shown.currentState && !notice.shown.targetState) {
                    LaunchedEffect(Unit) { notices.remove(notice) }
                }
                val place = (showing.size - 1 - showing.indexOf(notice)).takeIf { notice in showing }
                val alpha by animateFloatAsState(
                    place?.let(FeedPacing::alpha) ?: FeedPacing.alpha(FeedPacing.MAX),
                    Motion.standard(Motion.LONG_MS),
                    label = "feed alpha",
                )
                AnimatedVisibility(
                    notice.shown,
                    enter = fadeIn(Motion.standard()) + expandVertically(Motion.standard()) +
                        slideInVertically(Motion.standard()) { -it / 2 },
                    exit = fadeOut(Motion.standard(Motion.LONG_MS)) + shrinkVertically(Motion.standard(Motion.LONG_MS)),
                ) {
                    FeedNotice(notice.line, Modifier.alpha(alpha))
                }
            }
        }
    }
}

@Composable
private fun FeedNotice(line: LogLine, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Text(
        line.text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (line.strong) FontWeight.SemiBold else null,
        color = scheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(scheme.surfaceContainerHigh.copy(alpha = SURFACE_ALPHA), shape)
            .border(
                if (line.strong) 1.5.dp else 1.dp,
                if (line.strong) scheme.primary else scheme.outlineVariant,
                shape,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

/** One notice: its log line, and whether it is (to be) on screen. */
private class Notice(val index: Int, val line: LogLine) {
    val shown = MutableTransitionState(false).apply { targetState = true }
}

private val FEED_WIDTH = 300.dp
private const val SURFACE_ALPHA = 0.94f
