package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Readings dated after today.
 *
 * The day strip made future days reachable, and every range here is built from
 * `today`. That combination could write a row no screen would ever show — one the
 * user could neither see nor delete. Both halves are pinned below: the reads never
 * hide data, and the write refuses the day in the first place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightFutureDayTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Friday 21 Aug 2026. */
    private val today = 20_686L

    private class FixedClock(private val day: Long) : Clock {
        override fun nowMillis(): Long = 0L
        override fun today(): Long = day
    }

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun repo(db: GymDatabase) = BodyWeightRepositoryImpl(db, dispatcher)

    @Test
    fun loggingAFutureDayIsRefused() = runTest(dispatcher) {
        val database = db()
        val log = LogBodyWeightUseCase(repo(database), FixedClock(today))

        assertEquals(LogBodyWeightResult.FutureDay, log(80.0, today + 1))

        // Refused, not clamped to today: silently moving the reading to a day the
        // user did not pick would be worse than saying no.
        assertNull(repo(database).getByDay(today))
        assertNull(repo(database).getByDay(today + 1))
    }

    @Test
    fun todayAndThePastAreStillAccepted() = runTest(dispatcher) {
        val database = db()
        val log = LogBodyWeightUseCase(repo(database), FixedClock(today))

        assertEquals(LogBodyWeightResult.Logged, log(80.0, today))
        assertEquals(LogBodyWeightResult.Logged, log(79.0, today - 30))

        assertEquals(80.0, repo(database).getByDay(today)?.weightKg)
        assertEquals(79.0, repo(database).getByDay(today - 30)?.weightKg)
    }

    @Test
    fun anExistingFutureReadingStillAppearsInTheHistory() = runTest(dispatcher) {
        val database = db()
        // Written straight to the repository: rows like this already exist on devices
        // from before the guard, and they must not be stranded.
        repo(database).upsert(today + 1, 89.0, null)
        repo(database).upsert(today + 9, 90.0, null)

        val history = ObserveBodyWeightHistoryUseCase(repo(database), FixedClock(today))(
            BodyWeightRangePreset.LAST_30_DAYS,
        ).first()

        assertEquals(listOf(today + 9, today + 1), history.map { it.epochDay })
    }

    @Test
    fun anExistingFutureReadingStillAppearsInTheChart() = runTest(dispatcher) {
        val database = db()
        repo(database).upsert(today + 1, 89.0, null)

        val chart = ObserveBodyWeightChartUseCase(repo(database), FixedClock(today))(
            BodyWeightRangePreset.LAST_30_DAYS,
        ).first()

        assertEquals(1, chart.raw.size)
        assertEquals(89.0, chart.summary.currentKg)
    }

    @Test
    fun theWindowStillStartsWhereThePresetSaysItDoes() = runTest(dispatcher) {
        val database = db()
        repo(database).upsert(today, 80.0, null)
        // Comfortably outside a 30-day lookback.
        repo(database).upsert(today - 100, 70.0, null)

        val history = ObserveBodyWeightHistoryUseCase(repo(database), FixedClock(today))(
            BodyWeightRangePreset.LAST_30_DAYS,
        ).first()

        // Extending the far end must not have loosened the near one: the preset is
        // still a lookback, and the old reading stays out of a 30-day window.
        assertEquals(listOf(today), history.map { it.epochDay })
    }

    @Test
    fun withNoFutureReadingTheWindowEndsAtTodayAsBefore() = runTest(dispatcher) {
        val database = db()
        repo(database).upsert(today - 1, 80.0, null)

        val history = ObserveBodyWeightHistoryUseCase(repo(database), FixedClock(today))(
            BodyWeightRangePreset.LAST_30_DAYS,
        ).first()

        assertTrue(history.isNotEmpty())
        assertEquals(listOf(today - 1), history.map { it.epochDay })
    }
}
