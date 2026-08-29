package botix.dev.myweeklyroutinegymapp.domain.model

/** A plotted value: a day and the weight read for it. */
data class BodyWeightPoint(
    val epochDay: Long,
    val weightKg: Double,
)

/**
 * Direction of travel, read from the smoothed series rather than the raw one.
 *
 * [INSUFFICIENT_DATA] is a distinct state, not a synonym for [STABLE]: "I cannot
 * tell yet" and "you are holding steady" mean opposite things to someone cutting.
 */
enum class WeightTrend {
    RISING,
    FALLING,
    STABLE,
    INSUFFICIENT_DATA,
}

/**
 * How the series is drawn. Daily points become unreadable over a long window, so
 * long ranges collapse to one value per month.
 */
enum class ChartMode {
    DAILY,
    MONTHLY,
}

/**
 * The derived metrics for a range. All computed, never stored — so they cannot drift
 * out of sync with the measurements behind them.
 *
 * Every figure is nullable because an empty range genuinely has no current weight;
 * returning 0.0 would render as a real reading of zero.
 */
data class BodyWeightSummary(
    val currentKg: Double?,
    val changeKg: Double?,
    val changePercent: Double?,
    val trend: WeightTrend,
    /** kg per week, the figure that tells you whether a deficit is working. */
    val ratePerWeekKg: Double?,
    val minKg: Double?,
    val maxKg: Double?,
    val sampleCount: Int,
)

/**
 * Everything the body-weight screen renders for one range.
 *
 * [raw] and [smoothed] are separate series on purpose: the spec wants the raw points
 * drawn faintly behind the moving average, because day-to-day noise (water, food,
 * sleep) makes a raw line actively misleading.
 */
data class BodyWeightChart(
    val raw: List<BodyWeightPoint>,
    val smoothed: List<BodyWeightPoint>,
    val summary: BodyWeightSummary,
    val mode: ChartMode,
) {
    val isEmpty: Boolean get() = raw.isEmpty()

    companion object {
        fun empty(mode: ChartMode = ChartMode.DAILY): BodyWeightChart = BodyWeightChart(
            raw = emptyList(),
            smoothed = emptyList(),
            summary = BodyWeightSummary(
                currentKg = null,
                changeKg = null,
                changePercent = null,
                trend = WeightTrend.INSUFFICIENT_DATA,
                ratePerWeekKg = null,
                minKg = null,
                maxKg = null,
                sampleCount = 0,
            ),
            mode = mode,
        )
    }
}
