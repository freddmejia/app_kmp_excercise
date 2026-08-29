package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetSessionDistanceTimeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveCategoryVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Logging an activity measured in distance and time.
 *
 * This is the flow that did not exist: a DISTANCE_TIME activity could be created and
 * then never logged, so it sat under "Not trained on this day" forever no matter what
 * the user had actually done. The tests below pin the three things that made it
 * possible to get wrong — that the row is written at all, that it is *found* by the
 * same query the Activities screen filters on, and that the units survive the trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DistanceTimeLoggingTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Tue 25 Aug 2026. */
    private val today = 20_690L

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private class FixedClock(private val day: Long) : Clock {
        override fun today(): Long = day
        override fun nowMillis(): Long = 0L
    }

    private fun starter(db: GymDatabase) = StartSessionUseCase(
        workouts = WorkoutRepositoryImpl(db, dispatcher),
        categories = ActivityCategoryRepositoryImpl(db, dispatcher),
        clock = FixedClock(today),
    )

    private fun setter(db: GymDatabase) =
        SetSessionDistanceTimeUseCase(WorkoutRepositoryImpl(db, dispatcher))

    private suspend fun category(db: GymDatabase, name: String, type: MeasurementType): Long =
        ActivityCategoryRepositoryImpl(db, dispatcher).create(name, type, createdAt = 0L)

    /**
     * The two steps the flow actually takes: New log creates the row, the form fills
     * it in. They were one call while a distance log created its own session; they are
     * two now, so that reopening a ride to fix a number corrects it rather than adding
     * a second ride to the day.
     */
    private suspend fun logRide(
        db: GymDatabase,
        categoryId: Long,
        distanceMeters: Long?,
        durationSeconds: Long?,
    ): LogDistanceTimeResult {
        val started = starter(db)(categoryId, today) as StartSessionResult.Started
        return setter(db)(started.sessionId, distanceMeters, durationSeconds)
    }

    @Test
    fun aRideIsStoredInMetresAndSeconds() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)

        // 20.5 km in 45 min, as the form would hand it over.
        val result = logRide(database, id, distanceMeters = 20_500, durationSeconds = 2_700)

        assertEquals(LogDistanceTimeResult.Logged, result)
        val session = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today)
            .first()
            .single()
        assertEquals(20_500L, session.distanceMeters)
        assertEquals(2_700L, session.durationSeconds)
        // The measures are what makes it a distance session; no muscle was checked.
        assertTrue(session.isDistanceTime)
        assertEquals(0L, session.muscleCount)
    }

    @Test
    fun aLoggedRideCountsAsTrainedOnThatDay() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)
        logRide(database, id, distanceMeters = 20_500, durationSeconds = 2_700)

        // The exact query a week view filters on: present means trained.
        val volume = ObserveCategoryVolumeUseCase(WorkoutRepositoryImpl(database, dispatcher))(
            today,
            today,
        ).first().single()

        assertEquals(id, volume.categoryId)
        assertTrue(volume.isDistanceTime)
        assertEquals(20_500L, volume.distanceMeters)
        assertEquals(2_700L, volume.durationSeconds)
        assertEquals(1L, volume.sessionCount)
        // No sets were logged, and the row must not pretend otherwise.
        assertEquals(0L, volume.effectiveSets)
    }

    @Test
    fun aRideWithNeitherMeasureIsRefusedAndWritesNothing() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)
        val started = starter(database)(id, today) as StartSessionResult.Started

        val result = setter(database)(started.sessionId, null, null)

        assertEquals(LogDistanceTimeResult.NothingMeasured, result)
        // The row exists — New log created it, deliberately and visibly — but it holds
        // no measurements, which is what an abandoned form should leave behind.
        val session = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today)
            .first()
            .single()
        assertNull(session.distanceMeters)
        assertNull(session.durationSeconds)
    }

    @Test
    fun anEmptyFormIsRefused() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)

        assertEquals(
            LogDistanceTimeResult.NothingMeasured,
            logRide(database, id, distanceMeters = null, durationSeconds = null),
        )
        // Zero is "measured, and it was none", which is never what was meant.
        assertEquals(
            LogDistanceTimeResult.NothingMeasured,
            logRide(database, id, distanceMeters = 0, durationSeconds = 0),
        )
        // The rows exist — logRide starts one each time, as New log does — and both
        // are empty. What "refused" protects is the *measurements*: nothing was
        // written into either, so neither counts as a ride that happened.
        val sessions = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first()
        assertTrue(sessions.all { it.distanceMeters == null && it.durationSeconds == null })
    }

    @Test
    fun oneMeasureAloneIsEnough() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Piscina", MeasurementType.DISTANCE_TIME)

        // A swim timed but not measured: a real record, and the only one available
        // to anyone whose pool length they never counted.
        assertEquals(
            LogDistanceTimeResult.Logged,
            logRide(database, id, distanceMeters = null, durationSeconds = 1_800),
        )

        val session = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today)
            .first()
            .single()
        assertNull(session.distanceMeters)
        assertEquals(1_800L, session.durationSeconds)
        assertTrue(session.isDistanceTime)
        // Pace needs both halves; with one it is absent rather than infinite.
        assertNull(session.paceSecondsPerKm)
    }

    @Test
    fun twoRidesOnOneDayAreTwoLogs() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)

        logRide(database, id, distanceMeters = 20_500, durationSeconds = 2_700)
        logRide(database, id, distanceMeters = 21_000, durationSeconds = 2_760)

        val sessions = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first()
        // It used to be one row corrected twice, because a distance log found-or-created
        // by (activity, day). A morning ride and an evening ride are two rides, so New
        // log makes a second row; correcting one means reopening *it*, which writes by
        // session id and cannot touch the other.
        assertEquals(2, sessions.size)
        assertEquals(listOf(20_500L, 21_000L), sessions.map { it.distanceMeters })
    }

    @Test
    fun reopeningARideCorrectsItRatherThanAddingOne() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)
        val started = starter(database)(id, today) as StartSessionResult.Started
        setter(database)(started.sessionId, 20_500, 2_700)

        setter(database)(started.sessionId, 21_000, 2_760)

        val sessions = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first()
        assertEquals(1, sessions.size)
        assertEquals(21_000L, sessions.single().distanceMeters)
        assertEquals(2_760L, sessions.single().durationSeconds)
    }

    @Test
    fun paceIsSecondsPerKilometre() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)
        logRide(database, id, distanceMeters = 20_000, durationSeconds = 3_600)

        val session = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today)
            .first()
            .single()
        // 20 km in an hour: 180 s per km, i.e. 3:00 /km.
        assertEquals(180.0, session.paceSecondsPerKm)
    }

    @Test
    fun aRideOnAnotherDayDoesNotCountTowardsThisOne() = runTest(dispatcher) {
        val database = db()
        val id = category(database, "Bicycle", MeasurementType.DISTANCE_TIME)
        // Yesterday, which needs the session started on that day rather than today's.
        val yesterday = starter(database)(id, today - 1) as StartSessionResult.Started
        setter(database)(yesterday.sessionId, 20_500, 2_700)

        // The day filter is the whole point of the strip: yesterday's ride must not
        // make the activity look trained today.
        assertTrue(
            ObserveCategoryVolumeUseCase(WorkoutRepositoryImpl(database, dispatcher))(today, today)
                .first()
                .isEmpty(),
        )
    }
}
