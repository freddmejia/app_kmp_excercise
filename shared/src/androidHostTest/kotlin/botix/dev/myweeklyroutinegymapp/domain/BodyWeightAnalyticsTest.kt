package botix.dev.myweeklyroutinegymapp.domain

import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightPoint
import botix.dev.myweeklyroutinegymapp.domain.model.ChartMode
import botix.dev.myweeklyroutinegymapp.domain.model.WeightTrend
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.buildChart
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.monthlyAverage
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.movingAverage
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.slopeKgPerWeek
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyWeightAnalyticsTest {

    private fun points(vararg pairs: Pair<Long, Double>) =
        pairs.map { BodyWeightPoint(it.first, it.second) }

    private fun entries(vararg pairs: Pair<Long, Double>) =
        pairs.mapIndexed { index, pair -> BodyWeightEntry(index.toLong(), pair.first, pair.second) }

    private fun assertClose(expected: Double, actual: Double?, tolerance: Double = 1e-9) {
        assertTrue(actual != null && abs(expected - actual) < tolerance, "expected $expected, was $actual")
    }

    @Test
    fun movingAverageUsesACalendarWindowNotTheLastNEntries() {
        // Day 20 sits well past the 7-day window, so it must not be blended with the
        // readings from before the gap.
        val smoothed = movingAverage(points(0L to 80.0, 1L to 82.0, 20L to 70.0))

        assertEquals(3, smoothed.size)
        assertClose(80.0, smoothed[0].weightKg)
        assertClose(81.0, smoothed[1].weightKg)
        assertClose(70.0, smoothed[2].weightKg)
    }

    @Test
    fun movingAverageIncludesEveryReadingInsideTheWindow() {
        val smoothed = movingAverage(points(0L to 80.0, 3L to 84.0, 6L to 88.0))
        // Day 6's window is days 0..6, so all three count.
        assertClose(84.0, smoothed.last().weightKg)
    }

    @Test
    fun movingAverageOfNothingIsNothing() {
        assertTrue(movingAverage(emptyList()).isEmpty())
    }

    @Test
    fun slopeIsExpressedInKilogramsPerWeek() {
        // One kilo lost per week, sampled weekly.
        assertClose(-1.0, slopeKgPerWeek(points(0L to 80.0, 7L to 79.0, 14L to 78.0)))
    }

    @Test
    fun slopeIsNullWhenItCannotBeComputed() {
        assertNull(slopeKgPerWeek(emptyList()))
        assertNull(slopeKgPerWeek(points(5L to 80.0)))
        // Every reading on one day: the denominator would be zero.
        assertNull(slopeKgPerWeek(points(5L to 80.0, 5L to 81.0)))
    }

    @Test
    fun aFallingSeriesReportsFalling() {
        val chart = buildChart(entries(0L to 82.0, 7L to 81.0, 14L to 80.0), rangeDays = 30)

        assertEquals(WeightTrend.FALLING, chart.summary.trend)
        assertClose(80.0, chart.summary.currentKg)
        assertClose(-2.0, chart.summary.changeKg)
        assertTrue(chart.summary.ratePerWeekKg!! < 0)
        assertEquals(3, chart.summary.sampleCount)
        assertEquals(ChartMode.DAILY, chart.mode)
    }

    @Test
    fun noiseWithinTheThresholdReportsStableRatherThanADirection() {
        // Drifting by grams: real enough to have a sign, too small to mean anything.
        val chart = buildChart(entries(0L to 80.0, 7L to 80.01, 14L to 80.02), rangeDays = 30)
        assertEquals(WeightTrend.STABLE, chart.summary.trend)
    }

    @Test
    fun tooFewPointsReportsInsufficientDataNotStable() {
        val chart = buildChart(entries(0L to 80.0, 7L to 79.0), rangeDays = 30)

        // The distinction matters: "cannot tell yet" is not "holding steady".
        assertEquals(WeightTrend.INSUFFICIENT_DATA, chart.summary.trend)
        // The change is still known even though the trend is not.
        assertClose(-1.0, chart.summary.changeKg)
        assertClose(80.0, chart.summary.maxKg)
    }

    @Test
    fun aSinglePointHasNoChangeAndNoTrend() {
        val chart = buildChart(entries(3L to 77.5), rangeDays = 30)

        assertClose(77.5, chart.summary.currentKg)
        assertNull(chart.summary.changeKg)
        assertNull(chart.summary.changePercent)
        assertEquals(WeightTrend.INSUFFICIENT_DATA, chart.summary.trend)
        assertTrue(chart.smoothed.isEmpty())
    }

    @Test
    fun anEmptyRangeHasNoFiguresRatherThanZeroes() {
        val chart = buildChart(emptyList(), rangeDays = 30)

        assertTrue(chart.isEmpty)
        assertNull(chart.summary.currentKg)
        assertNull(chart.summary.minKg)
        assertNull(chart.summary.ratePerWeekKg)
        assertEquals(0, chart.summary.sampleCount)
        assertEquals(WeightTrend.INSUFFICIENT_DATA, chart.summary.trend)
    }

    @Test
    fun changePercentIsRelativeToTheFirstReading() {
        val chart = buildChart(entries(0L to 100.0, 7L to 98.0, 14L to 95.0), rangeDays = 30)
        assertClose(-5.0, chart.summary.changePercent)
    }

    @Test
    fun longRangesSwitchToMonthlyPoints() {
        // Two readings in January, one in February.
        val chart = buildChart(entries(0L to 80.0, 10L to 82.0, 40L to 78.0), rangeDays = 365)

        assertEquals(ChartMode.MONTHLY, chart.mode)
        assertEquals(2, chart.smoothed.size)
        assertClose(81.0, chart.smoothed.first().weightKg)
        assertClose(78.0, chart.smoothed.last().weightKg)
        // The raw series is untouched by the mode switch.
        assertEquals(3, chart.raw.size)
    }

    @Test
    fun monthlyAverageDatesEachPointToTheFirstOfItsMonth() {
        val monthly = monthlyAverage(points(35L to 80.0, 40L to 82.0))

        assertEquals(1, monthly.size)
        // 1970-02-01 is epoch day 31.
        assertEquals(31L, monthly.single().epochDay)
        assertClose(81.0, monthly.single().weightKg)
    }
}
