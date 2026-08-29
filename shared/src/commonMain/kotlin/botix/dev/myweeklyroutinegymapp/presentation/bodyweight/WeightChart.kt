package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.chart_one_measurement
import org.jetbrains.compose.resources.stringResource
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightPoint
import botix.dev.myweeklyroutinegymapp.presentation.preview.PREVIEW_TODAY
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewChart

/**
 * The two-layer chart the spec calls for: raw daily readings drawn faintly, with the
 * smoothed series as the line that actually carries the reading.
 *
 * Body weight swings daily on water, food and sleep, so a line joining raw points
 * implies a precision that is not there. The faint dots keep the real data visible
 * without letting it dominate.
 */
@Composable
fun WeightChart(
    chart: BodyWeightChart,
    modifier: Modifier = Modifier,
) {
    if (chart.raw.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.onSurfaceVariant

    // A single reading has no line to draw, and no span to scale against.
    if (chart.raw.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.chart_one_measurement),
                style = MaterialTheme.typography.bodySmall,
                color = pointColor,
            )
        }
        return
    }

    val series = chart.smoothed.ifEmpty { chart.raw }
    val allPoints = chart.raw + series
    val minDay = allPoints.minOf { it.epochDay }
    val maxDay = allPoints.maxOf { it.epochDay }
    val minWeight = allPoints.minOf { it.weightKg }
    val maxWeight = allPoints.maxOf { it.weightKg }

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val daySpan = (maxDay - minDay).coerceAtLeast(1L).toFloat()
        // A flat series would divide by zero; padding it centres the line instead.
        val weightSpan = (maxWeight - minWeight).takeIf { it > 0.0 } ?: 1.0
        val padding = 8f

        fun project(point: BodyWeightPoint): Offset {
            val x = (point.epochDay - minDay) / daySpan * (size.width - padding * 2) + padding
            val ratio = ((point.weightKg - minWeight) / weightSpan).toFloat()
            // Y is inverted: canvas origin is top-left, but heavier should sit higher.
            val y = (1f - ratio) * (size.height - padding * 2) + padding
            return Offset(x, y)
        }

        chart.raw.forEach { point ->
            drawCircle(color = pointColor, radius = 3f, center = project(point), alpha = 0.35f)
        }

        val path = Path()
        series.sortedBy { it.epochDay }.forEachIndexed { index, point ->
            val offset = project(point)
            // Gaps are bridged rather than broken: a missing day is a day not
            // weighed, not a discontinuity in body weight.
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
    }
}

// --- Previews ---------------------------------------------------------------

/**
 * The two layers, which is the whole point of this component: the faint raw dots have
 * to be visible enough to show the daily swing and quiet enough that the smoothed line
 * is what the eye follows. That balance is a matter of alpha against the scheme's own
 * background, so it is checked in both.
 */
@PreviewLightDark
@Composable
private fun WeightChartPreview() {
    PreviewSurface {
        WeightChart(chart = previewChart, modifier = Modifier.padding(16.dp))
    }
}

/** One reading: no span to scale against, so the chart says so instead of drawing. */
@Preview(name = "Single measurement")
@Composable
private fun WeightChartSinglePreview() {
    PreviewSurface {
        WeightChart(
            chart = previewChart.copy(
                raw = listOf(BodyWeightPoint(epochDay = PREVIEW_TODAY, weightKg = 81.4)),
                smoothed = emptyList(),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * A flat series. The span would be zero and the projection would divide by it; the
 * padded span centres the line instead, and this is where that shows.
 */
@Preview(name = "Flat series")
@Composable
private fun WeightChartFlatPreview() {
    val flat = (0..9).map {
        BodyWeightPoint(epochDay = PREVIEW_TODAY - (9 - it) * 2L, weightKg = 80.0)
    }
    PreviewSurface {
        WeightChart(
            chart = previewChart.copy(raw = flat, smoothed = flat),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Empty draws nothing at all, so this preview is deliberately a blank box. */
@Preview(name = "Empty", heightDp = 80)
@Composable
private fun WeightChartEmptyPreview() {
    PreviewSurface {
        WeightChart(chart = BodyWeightChart.empty(), modifier = Modifier.padding(16.dp))
    }
}
