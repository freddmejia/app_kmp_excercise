package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightPoint

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
                "One measurement — no line yet.",
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
