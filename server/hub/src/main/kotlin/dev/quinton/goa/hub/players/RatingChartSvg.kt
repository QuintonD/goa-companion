package dev.quinton.goa.hub.players

import dev.quinton.goa.core.presentation.players.RatingChartModel
import dev.quinton.goa.core.presentation.players.formatDelta
import dev.quinton.goa.core.presentation.players.formatRating
import java.time.ZoneId
import java.util.Locale
import kotlinx.html.FlowContent
import kotlinx.html.caption
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe

/**
 * The player page's rating chart as inline SVG (the phone draws the same [RatingChartModel] with
 * Vico): the line over the padded range, a few y gridlines, date labels under it, and a table
 * alternative. No script, no inline style: colours come from classes in `players.css`.
 */
internal object RatingChartSvg {
    private const val WIDTH = 340.0
    private const val HEIGHT = 200.0
    private const val LEFT = 40.0
    private const val RIGHT = 8.0
    private const val TOP = 10.0
    private const val BOTTOM = 26.0
    private const val GRID_LINES = 4

    fun svg(chart: RatingChartModel, zone: ZoneId): String {
        val plotW = WIDTH - LEFT - RIGHT
        val plotH = HEIGHT - TOP - BOTTOM
        val n = chart.values.size
        fun x(i: Int) = LEFT + if (n == 1) plotW / 2 else plotW * i / (n - 1)
        fun y(v: Double) = TOP + plotH * (chart.high - v) / (chart.high - chart.low)
        val points = chart.values.mapIndexed { i, v -> "${num(x(i))},${num(y(v))}" }
        val area =
            "M${num(x(0))},${num(TOP + plotH)} L" + points.joinToString(" L") +
                " L${num(x(n - 1))},${num(TOP + plotH)} Z"
        return buildString {
            append("""<svg class="pl-chart" viewBox="0 0 ${num(WIDTH)} ${num(HEIGHT)}" role="img" aria-label="""")
            append(escape(chart.summary))
            append("\" focusable=\"false\">")
            for (g in 0..GRID_LINES) {
                val value = chart.low + (chart.high - chart.low) * g / GRID_LINES
                val gy = y(value)
                append(
                    """<line class="pl-chart-grid" x1="${num(
                        LEFT,
                    )}" x2="${num(WIDTH - RIGHT)}" y1="${num(gy)}" y2="${num(gy)}"/>""",
                )
                append("""<text class="pl-chart-axis" x="${num(LEFT - 6)}" y="${num(gy + 4)}" text-anchor="end">""")
                append(escape(formatRating(value)))
                append("</text>")
            }
            append("""<path class="pl-chart-area" d="$area"/>""")
            append("""<polyline class="pl-chart-line" points="${points.joinToString(" ")}"/>""")
            chart.labelled.forEach { i ->
                val label = chart.label(i, zone)
                if (label.isNotEmpty()) {
                    val anchor = when (i) {
                        0 -> "start"
                        n - 1 -> "end"
                        else -> "middle"
                    }
                    append(
                        """<text class="pl-chart-axis" x="${num(
                            x(i),
                        )}" y="${num(HEIGHT - 8)}" text-anchor="$anchor">""",
                    )
                    append(escape(label))
                    append("</text>")
                }
            }
            append("</svg>")
        }
    }

    private fun num(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}

/** The chart, then the same numbers as a table (collapsed) for screen readers and exact values. */
internal fun FlowContent.ratingChart(chart: RatingChartModel, zone: ZoneId) {
    div("pl-chart-box") {
        unsafe { +RatingChartSvg.svg(chart, zone) }
    }
    details("pl-chart-table") {
        summary { +"Rating history as a table" }
        table("pl-table") {
            caption("visually-hidden") { +chart.summary }
            thead {
                tr {
                    th {
                        attributes["scope"] = "col"
                        +"When"
                    }
                    th(classes = "num") {
                        attributes["scope"] = "col"
                        +"Rating"
                    }
                    th(classes = "num") {
                        attributes["scope"] = "col"
                        +"Change"
                    }
                }
            }
            tbody {
                chart.rows(zone).forEach { row ->
                    tr {
                        th {
                            attributes["scope"] = "row"
                            +row.label
                        }
                        td("num") { +formatRating(row.rating) }
                        td("num") { +(row.change?.let(::formatDelta) ?: "—") }
                    }
                }
            }
        }
    }
}
