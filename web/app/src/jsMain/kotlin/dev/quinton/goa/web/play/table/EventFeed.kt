package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.feature.board.play.table.FeedPacing
import dev.quinton.goa.feature.board.play.table.LogLine
import dev.quinton.goa.feature.board.play.table.feedSince
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * The table's feed (ADR-060): each new action, move, attack, defeat… pops in over the map as a notice,
 * the newest on top, and fades as newer ones come in until it leaves ([FeedPacing]). A line not to miss is
 * semibold with a bronze edge and stays longer. It lets clicks through to the map and is hidden from
 * screen readers: the screen's announcement already says what happened.
 */
@Composable
fun EventFeed(lines: List<LogLine>) {
    val notices = remember { mutableStateListOf<Notice>() }
    var seen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(lines) {
        if (seen?.let { lines.size < it } == true) notices.forEach { it.leaving = true }
        feedSince(lines, seen).takeLast(FeedPacing.MAX).forEach { notices += Notice(it.index, it.value) }
        seen = lines.size
        // Past the most at once, the oldest go.
        notices.filter { !it.leaving }.dropLast(FeedPacing.MAX).forEach { it.leaving = true }
    }
    Div({
        classes("goa-feed")
        attr("aria-hidden", "true")
    }) {
        val showing = notices.filter { !it.leaving }
        notices.asReversed().forEach { notice ->
            key(notice.index) {
                LaunchedEffect(Unit) {
                    delay(if (notice.line.strong) FeedPacing.STRONG_MS else FeedPacing.SHOW_MS)
                    notice.leaving = true
                }
                // Gone once it has faded out.
                if (notice.leaving) {
                    LaunchedEffect(Unit) {
                        delay(LEAVE_MS)
                        notices.remove(notice)
                    }
                }
                val opacity = if (notice.leaving) 0f else FeedPacing.alpha(showing.size - 1 - showing.indexOf(notice))
                Div({
                    classes("goa-feed-notice", "t-body-small")
                    if (notice.line.strong) classes("strong")
                    style { property("opacity", opacity.toString()) }
                }) { Text(notice.line.text) }
            }
        }
    }
}

/** One notice: its log line, and whether it is fading out. */
private class Notice(val index: Int, val line: LogLine) {
    var leaving by mutableStateOf(false)
}

/** The fade out (`.goa-feed-notice`'s transition in goa.css). */
private const val LEAVE_MS = 300L
