package botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight

import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightPoint
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightSummary
import botix.dev.myweeklyroutinegymapp.domain.model.ChartMode
import botix.dev.myweeklyroutinegymapp.domain.model.WeightTrend
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Pure functions behind the body-weight chart.
 *
 * Kept free of repositories and coroutines so the arithmetic can be tested directly
 * with lists — the interesting failure modes here are gaps, single points and
 * ranges of one day, none of which need a database to reproduce.
 */

/** Trailing window for the moving average, in days. */
internal const val SMOOTHING_WINDOW_DAYS: Long = 7

/**
 * Below this many measurements a smoothed line is drawn from too little to mean
 * anything, so no trend is claimed.
 */
internal const val MIN_POINTS_FOR_TREND: Int = 3

/** Ranges longer than this switch to one point per month. */
internal const val MONTHLY_MODE_THRESHOLD_DAYS: Long = 90

/**
 * A slope flatter than this counts as holding steady. Daily fluctuation swamps
 * anything smaller, so reporting it as a direction would be false precision.
 */
internal const val STABLE_SLOPE_KG_PER_WEEK: Double = 0.1

/**
 * Trailing moving average over calendar days, not over the previous N *entries*.
 *
 * The difference matters when there are gaps: after a two-week break, averaging the
 * last 7 entries would silently blend in readings from before the break and present
 * the result as current.
 */
internal fun movingAverage(
    points: List<BodyWeightPoint>,
    windowDays: Long = SMOOTHING_WINDOW_DAYS,
): List<BodyWeightPoint> {
    if (points.isEmpty()) return emptyList()
    val ordered = points.sortedBy { it.epochDay }
    return ordered.map { point ->
        val windowStart = point.epochDay - (windowDays - 1)
        val inWindow = ordered.filter { it.epochDay in windowStart..point.epochDay }
        BodyWeightPoint(
            epochDay = point.epochDay,
            weightKg = inWindow.sumOf { it.weightKg } / inWindow.size,
        )
    }
}

/**
 * One average per calendar month, dated to the first day of that month.
 *
 * Used for long ranges, where thousands of daily points are less legible than twelve
 * monthly ones.
 */
internal fun monthlyAverage(points: List<BodyWeightPoint>): List<BodyWeightPoint> {
    if (points.isEmpty()) return emptyList()
    return points
        .groupBy { point ->
            val date = LocalDate.fromEpochDays(point.epochDay)
            LocalDate(date.year, date.month, 1)
        }
        .map { (month, monthPoints) ->
            BodyWeightPoint(
                epochDay = month.toEpochDays(),
                weightKg = monthPoints.sumOf { it.weightKg } / monthPoints.size,
            )
        }
        .sortedBy { it.epochDay }
}

/**
 * Least-squares slope of the smoothed series, expressed in kg per week.
 *
 * Regression over every point rather than simply last-minus-first: the endpoints are
 * exactly where a stray reading does the most damage to the answer.
 *
 * Returns null when there are too few points, or when they all fall on one day and
 * the slope would be a division by zero.
 */
internal fun slopeKgPerWeek(points: List<BodyWeightPoint>): Double? {
    if (points.size < 2) return null
    val meanDay = points.sumOf { it.epochDay.toDouble() } / points.size
    val meanWeight = points.sumOf { it.weightKg } / points.size
    var numerator = 0.0
    var denominator = 0.0
    for (point in points) {
        val dayDelta = point.epochDay - meanDay
        numerator += dayDelta * (point.weightKg - meanWeight)
        denominator += dayDelta * dayDelta
    }
    if (denominator == 0.0) return null
    return (numerator / denominator) * 7.0
}

/** Builds the summary block: current, change, trend, rate and extremes. */
internal fun summarise(
    raw: List<BodyWeightPoint>,
    smoothed: List<BodyWeightPoint>,
): BodyWeightSummary {
    if (raw.isEmpty()) return BodyWeightChart.empty().summary

    val ordered = raw.sortedBy { it.epochDay }
    val first = ordered.first()
    val last = ordered.last()
    val changeKg = if (ordered.size < 2) null else last.weightKg - first.weightKg

    val slope = if (ordered.size < MIN_POINTS_FOR_TREND) null else slopeKgPerWeek(smoothed)
    val trend = when {
        slope == null -> WeightTrend.INSUFFICIENT_DATA
        abs(slope) < STABLE_SLOPE_KG_PER_WEEK -> WeightTrend.STABLE
        slope > 0 -> WeightTrend.RISING
        else -> WeightTrend.FALLING
    }

    // Rate comes from the regression, not from change ÷ weeks: over a range whose
    // endpoints happen to be noisy, the two disagree and the regression is the
    // honest one.
    val ratePerWeek = slope

    return BodyWeightSummary(
        currentKg = last.weightKg,
        changeKg = changeKg,
        changePercent = changeKg?.let { delta ->
            if (first.weightKg == 0.0) null else delta / first.weightKg * 100.0
        },
        trend = trend,
        ratePerWeekKg = ratePerWeek,
        minKg = ordered.minOf { it.weightKg },
        maxKg = ordered.maxOf { it.weightKg },
        sampleCount = ordered.size,
    )
}

/**
 * Assembles the chart for a range.
 *
 * [rangeDays] drives the daily/monthly switch and is the *requested* span, not the
 * span actually covered by measurements — asking for a year with two readings in it
 * is still a long-range question.
 */
internal fun buildChart(
    entries: List<BodyWeightEntry>,
    rangeDays: Long,
): BodyWeightChart {
    val raw = entries
        .sortedBy { it.epochDay }
        .map { BodyWeightPoint(it.epochDay, it.weightKg) }
    if (raw.isEmpty()) {
        return BodyWeightChart.empty(
            if (rangeDays > MONTHLY_MODE_THRESHOLD_DAYS) ChartMode.MONTHLY else ChartMode.DAILY,
        )
    }

    val mode = if (rangeDays > MONTHLY_MODE_THRESHOLD_DAYS) ChartMode.MONTHLY else ChartMode.DAILY
    val smoothed = when (mode) {
        ChartMode.DAILY -> if (raw.size >= MIN_POINTS_FOR_TREND) movingAverage(raw) else emptyList()
        ChartMode.MONTHLY -> monthlyAverage(raw)
    }

    return BodyWeightChart(
        raw = raw,
        smoothed = smoothed,
        // Trend is read from the smoothed series where one exists; with too few
        // points to smooth, fall back to the raw series so a two-point range still
        // reports its change even though it reports no trend.
        summary = summarise(raw, smoothed.ifEmpty { raw }),
        mode = mode,
    )
}
