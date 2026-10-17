package dev.quinton.goa.feature.players.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import dev.quinton.goa.feature.players.common.formatRating
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Rating after each rated match (Vico line chart), starting from the initial rating. The whole
 * chart is one accessibility node with a spoken summary.
 */
@Composable
fun RatingChart(history: List<Double>, initial: Double, modifier: Modifier = Modifier) {
    val values = remember(history, initial) { listOf(initial) + history }
    val model = remember(values) { CartesianChartModel(LineCartesianLayerModel.build { series(values) }) }
    val low = floor((values.min() - CHART_PADDING) / STEP) * STEP
    val high = ceil((values.max() + CHART_PADDING) / STEP) * STEP
    val lineColor = MaterialTheme.colorScheme.secondary
    val summary = "Rating history over ${history.size} matches: started at ${formatRating(initial)}, " +
        "now ${formatRating(values.last())}, peak ${formatRating(values.max())}, low ${formatRating(values.min())}."

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                Fill(Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.32f), Color.Transparent))),
                            ),
                        ),
                    ),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = low, maxY = high),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            model = model,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clearAndSetSemantics { contentDescription = summary },
        )
    }
}

private const val CHART_PADDING = 10.0
private const val STEP = 10.0
