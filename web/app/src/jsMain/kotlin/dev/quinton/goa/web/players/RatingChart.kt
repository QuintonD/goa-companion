package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.plural
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import org.jetbrains.compose.web.dom.Div

/**
 * Rating after each rated match, starting from the initial rating (the phone's Vico chart): a line
 * in the secondary colour over a fading area, the scale on the left in steps of 10, dates under it
 * ("Start" first). One image for VoiceOver with a spoken summary. Drawn as inline SVG (crisp at
 * any zoom; numbers and dates only, escaped).
 */
@Composable
fun RatingChart(history: List<Double>, initial: Double, dates: List<Long>) {
    val values = listOf(initial) + history
    val summary =
        "Rating history over ${plural(history.size, "match", "matches")}: started at ${formatRating(initial)}, " +
            "now ${formatRating(values.last())}, peak ${formatRating(values.max())}, low ${formatRating(values.min())}."
    val svg = chartSvg(values, dates)
    Div({
        classes("goa-w2-chart")
        attr("role", "img")
        attr("aria-label", summary)
    }) {
        key(svg) {
            Div({
                classes("goa-w2-chart-svg")
                ref { element ->
                    element.innerHTML = svg
                    onDispose { }
                }
            })
        }
    }
}

/** The chart's SVG markup for [values] (initial rating first) and the match [dates]. */
internal fun chartSvg(values: List<Double>, dates: List<Long>): String {
    val low = floor((values.min() - PADDING) / STEP) * STEP
    val high = ceil((values.max() + PADDING) / STEP) * STEP
    val plotW = WIDTH - LEFT - RIGHT
    val plotH = HEIGHT - TOP - BOTTOM
    fun x(i: Int) = LEFT + if (values.size == 1) plotW / 2 else plotW * i / (values.size - 1)
    fun y(v: Double) = TOP + plotH * (high - v) / (high - low)
    val line = values.mapIndexed { i, v -> "${if (i == 0) "M" else "L"}${fmt(x(i))} ${fmt(y(v))}" }.joinToString(" ")
    val bottom = fmt(TOP + plotH)
    val area = "$line L${fmt(x(values.lastIndex))} $bottom L${fmt(x(0))} $bottom Z"
    val ticks = generateSequence(low) { it + STEP }.takeWhile { it <= high + 0.1 }.toList()
    val tickEvery = max(1, ceil(ticks.size / MAX_TICKS.toDouble()).toInt())
    val dateEvery = max(1, (values.size + 1) / AXIS_LABELS)
    return buildString {
        append("""<svg viewBox="0 0 $WIDTH $HEIGHT" aria-hidden="true" focusable="false">""")
        append("""<defs><linearGradient id="goa-w2-area" x1="0" y1="0" x2="0" y2="1">""")
        append(
            """<stop offset="0" class="goa-w2-chart-stop-top"/><stop offset="1" class="goa-w2-chart-stop-bottom"/>""",
        )
        append("</linearGradient></defs>")
        ticks.forEachIndexed { i, t ->
            if (i % tickEvery != 0) return@forEachIndexed
            append("""<path class="goa-w2-chart-grid" d="M$LEFT ${fmt(y(t))} H${WIDTH - RIGHT}"/>""")
            append("""<text class="goa-w2-chart-label" text-anchor="end" x="${LEFT - 6}" y="${fmt(y(t) + 4)}">""")
            append(formatRating(t)).append("</text>")
        }
        append("""<path d="$area" fill="url(#goa-w2-area)"/>""")
        append("""<path class="goa-w2-chart-line" d="$line"/>""")
        values.indices.filter { it % dateEvery == 0 }.forEach { i ->
            val label = if (i == 0) "Start" else dates.getOrNull(i - 1)?.let(WebDateLabels::short).orEmpty()
            val anchor = when (i) {
                0 -> "start"
                values.lastIndex -> "end"
                else -> "middle"
            }
            append("""<text class="goa-w2-chart-label" text-anchor="$anchor" x="${fmt(x(i))}" y="${HEIGHT - 6}">""")
            append(escape(label)).append("</text>")
        }
        append("</svg>")
    }
}

private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun fmt(v: Double): String = (round(v * 10) / 10).toString()

private const val WIDTH = 360.0
private const val HEIGHT = 200.0
private const val LEFT = 40.0
private const val RIGHT = 10.0
private const val TOP = 8.0
private const val BOTTOM = 24.0

// The phone's chart (RatingChart.kt): 10 points of padding, steps of 10, about 4 date labels.
private const val STEP = 10.0
private const val PADDING = 10.0
private const val MAX_TICKS = 5
private const val AXIS_LABELS = 4
