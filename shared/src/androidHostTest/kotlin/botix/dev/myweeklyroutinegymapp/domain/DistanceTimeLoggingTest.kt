package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityVolumeUseCase
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

    private fun logger(db: GymDatabase) = LogDistanceTimeUseCase(
        workouts = WorkoutRepositoryImpl(db, dispatcher),
        activities = ActivityRepositoryImpl(db, dispatcher),
        clock = FixedClock(today),
    )

    private suspend fun activity(db: GymDatabase, name: String, type: MeasurementType): Long =
        ActivityRepositoryImpl(db, dispatcher).create(name, type, createdAt = 0L)

    @Test
    fun aRideIsStoredInMetresAndSeconds() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)

        // 20.5 km in 45 min, as the dialog would hand it over.
        val result = logger(database)(id, distanceMeters = 20_500, durationSeconds = 2_700)

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
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)
        logger(database)(id, distanceMeters = 20_500, durationSeconds = 2_700)

        // The exact query the Activities screen filters on: present means trained.
        val volume = ObserveActivityVolumeUseCase(WorkoutRepositoryImpl(database, dispatcher))(
            today,
            today,
        ).first().single()

        assertEquals(id, volume.activityId)
        assertTrue(volume.isDistanceTime)
        assertEquals(20_500L, volume.distanceMeters)
        assertEquals(2_700L, volume.durationSeconds)
        assertEquals(1L, volume.sessionCount)
        // No sets were logged, and the row must not pretend otherwise.
        assertEquals(0L, volume.effectiveSets)
    }

    @Test
    fun aStrengthActivityIsNotLoggableThisWay() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "Bench", MeasurementType.STRENGTH)

        val result = logger(database)(id, distanceMeters = 5_000, durationSeconds = 600)

        assertEquals(LogDistanceTimeResult.NotADistanceTimeActivity, result)
        // Refused before anything was written: a rejected log leaves no session
        // behind to show up as a phantom entry on the day.
        assertTrue(
            ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first().isEmpty(),
        )
    }

    @Test
    fun anEmptyFormIsRefused() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)

        assertEquals(
            LogDistanceTimeResult.NothingMeasured,
            logger(database)(id, distanceMeters = null, durationSeconds = null),
        )
        // Zero is "measured, and it was none", which is never what was meant.
        assertEquals(
            LogDistanceTimeResult.NothingMeasured,
            logger(database)(id, distanceMeters = 0, durationSeconds = 0),
        )
        assertTrue(
            ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first().isEmpty(),
        )
    }

    @Test
    fun oneMeasureAloneIsEnough() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "Piscina", MeasurementType.DISTANCE_TIME)

        // A swim timed but not measured: a real record, and the only one available
        // to anyone whose pool length they never counted.
        assertEquals(
            LogDistanceTimeResult.Logged,
            logger(database)(id, distanceMeters = null, durationSeconds = 1_800),
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
    fun loggingTheSameDayTwiceCorrectsItRatherThanStacking() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)
        val log = logger(database)

        log(id, distanceMeters = 20_500, durationSeconds = 2_700)
        log(id, distanceMeters = 21_000, durationSeconds = 2_760)

        val sessions = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today).first()
        // One session for the activity that day, matching the strength flow — the
        // second log is a correction, not a second ride.
        assertEquals(1, sessions.size)
        assertEquals(21_000L, sessions.single().distanceMeters)
        assertEquals(2_760L, sessions.single().durationSeconds)
    }

    @Test
    fun paceIsSecondsPerKilometre() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)
        logger(database)(id, distanceMeters = 20_000, durationSeconds = 3_600)

        val session = ObserveDayLogUseCase(WorkoutRepositoryImpl(database, dispatcher))(today)
            .first()
            .single()
        // 20 km in an hour: 180 s per km, i.e. 3:00 /km.
        assertEquals(180.0, session.paceSecondsPerKm)
    }

    @Test
    fun aRideOnAnotherDayDoesNotCountTowardsThisOne() = runTest(dispatcher) {
        val database = db()
        val id = activity(database, "bici 20k", MeasurementType.DISTANCE_TIME)
        logger(database)(id, distanceMeters = 20_500, durationSeconds = 2_700, epochDay = today - 1)

        // The day filter is the whole point of the strip: yesterday's ride must not
        // make the activity look trained today.
        assertTrue(
            ObserveActivityVolumeUseCase(WorkoutRepositoryImpl(database, dispatcher))(today, today)
                .first()
                .isEmpty(),
        )
    }
}
