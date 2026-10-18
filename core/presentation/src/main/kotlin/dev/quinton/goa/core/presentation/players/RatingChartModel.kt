package dev.quinton.goa.core.presentation.players

import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The player page's rating chart: the starting rating then the rating after each rated match,
 * the y range (padded and rounded to [STEP]), which points get a date label, and the spoken
 * summary. Drawn by Vico on the phone and as SVG on the hub.
 */
data class RatingChartModel(
    /** x = 0 is the starting rating, x = i the rating after the i-th rated match. */
    val values: List<Double>,
    /** When each match after the start was played (epoch ms); may be empty. */
    val dates: List<Long>,
    val low: Double,
    val high: Double,
    /** Every [labelSpacing]-th x gets a label under the axis. */
    val labelSpacing: Int,
    val summary: String,
) {
    /** "Start" for x = 0, else the match's short date ("12 Oct"), or "" without dates. */
    fun label(x: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        if (x == 0) "Start" else dates.getOrNull(x - 1)?.let { Dates.short(it, zone) } ?: ""

    /** One row of the chart's table alternative: when, the rating after it, and the change. */
    data class Row(val label: String, val rating: Double, val change: Double?)

    /** The chart as a table (web accessibility): "Start", then each match's day ("Sat 3 Oct 2026"). */
    fun rows(zone: ZoneId = ZoneId.systemDefault()): List<Row> = values.mapIndexed { x, value ->
        Row(
            label = if (x == 0) "Start" else dates.getOrNull(x - 1)?.let { Dates.day(it, zone) } ?: "Match $x",
            rating = value,
            change = if (x == 0) null else value - values[x - 1],
        )
    }

    /** The x positions that carry a label. */
    val labelled: List<Int> get() = values.indices.filter { it % labelSpacing == 0 }

    companion object {
        const val PADDING = 10.0
        const val STEP = 10.0

        /** About this many date labels fit under a phone-width chart. */
        const val AXIS_LABELS = 4

        /** Null when there is no rated match yet (the page shows an empty state). */
        fun of(history: List<Double>, initial: Double, dates: List<Long> = emptyList()): RatingChartModel? {
            if (history.isEmpty()) return null
            val values = listOf(initial) + history
            val summary =
                "Rating history over ${plural(
                    history.size,
                    "match",
                    "matches",
                )}: started at ${formatRating(initial)}, " +
                    "now ${formatRating(values.last())}, peak ${formatRating(values.max())}, " +
                    "low ${formatRating(values.min())}."
            return RatingChartModel(
                values = values,
                dates = dates,
                low = floor((values.min() - PADDING) / STEP) * STEP,
                high = ceil((values.max() + PADDING) / STEP) * STEP,
                labelSpacing = maxOf(1, (history.size + 1) / AXIS_LABELS),
                summary = summary,
            )
        }
    }
}
