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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import dev.quinton.goa.core.presentation.players.RatingChartModel
import kotlin.math.roundToInt

/**
 * Rating after each rated match (Vico line chart), starting from the initial rating. The whole
 * chart is one accessibility node with a spoken summary.
 */
@Composable
fun RatingChart(
    history: List<Double>,
    initial: Double,
    modifier: Modifier = Modifier,
    dates: List<Long> = emptyList(),
) {
    // Range, labels and the spoken summary are shared with the web page (core:presentation).
    val chart = remember(history, initial, dates) { RatingChartModel.of(history, initial, dates) } ?: return
    val model = remember(chart) { CartesianChartModel(LineCartesianLayerModel.build { series(chart.values) }) }
    val lineColor = MaterialTheme.colorScheme.secondary

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
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = chart.low, maxY = chart.high),
                ),
                startAxis = VerticalAxis.rememberStart(),
                // Dates under the line: x = 0 is the starting rating, x = i the i-th rated match.
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember(chart) {
                        CartesianValueFormatter { _, x, _ -> chart.label(x.roundToInt()) }
                    },
                    itemPlacer = remember(chart.labelSpacing) {
                        HorizontalAxis.ItemPlacer.aligned(spacing = { chart.labelSpacing })
                    },
                ),
            ),
            model = model,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clearAndSetSemantics { contentDescription = chart.summary },
        )
    }
}
