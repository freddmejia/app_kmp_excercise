package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveCategoryVolumeUseCase
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Per-category volume over a week: how much of each kind of training there was.
 *
 * It was per-*activity* until activities were removed (6.sqm). The load-bearing rule
 * did not change with them: *absence is the filter* — a category with no session in
 * the range is simply not in the result, so a caller narrows on membership rather than
 * on a second query or a zero row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryWeekVolumeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Friday 21 Aug 2026; its week runs Mon 17 – Sun 23. */
    private val today = 20_686L
    private val weekStart = weekStartOf(today)
    private val weekEnd = weekStart + WEEK - 1

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun workouts(db: GymDatabase) = WorkoutRepositoryImpl(db, dispatcher)
    private fun volume(db: GymDatabase) = ObserveCategoryVolumeUseCase(workouts(db))

    private suspend fun category(db: GymDatabase, name: String): Long =
        ActivityCategoryRepositoryImpl(db, dispatcher)
            .create(name, MeasurementType.STRENGTH, createdAt = 0L)

    private suspend fun muscle(db: GymDatabase, group: String, leaf: String): Long {
        val catalog = MuscleCatalogRepositoryImpl(db, dispatcher)
        val groupId = catalog.createGroup(group, 0L)
        return catalog.createMuscle(groupId, leaf, 0L)
    }

    @Test
    fun anUntrainedCategoryIsAbsentRatherThanZero() = runTest(dispatcher) {
        val database = db()
        category(database, "Gym")

        // Absence is what the screen filters on, so a zero-filled row here would put
        // every untrained category back into a list that is meant to exclude them.
        assertTrue(volume(database)(weekStart, weekEnd).first().isEmpty())
    }

    @Test
    fun volumeSumsEveryEntryOfEverySessionInTheWeek() = runTest(dispatcher) {
        val database = db()
        val gym = category(database, "Gym")
        val upper = muscle(database, "Chest", "Upper chest")

        val monday = workouts(database).startSession(gym, weekStart, 0L)
        workouts(database).addSets(monday, upper, 3, 1)
        val wednesday = workouts(database).startSession(gym, weekStart + 2, 0L)
        workouts(database).addSets(wednesday, upper, 2, 0)

        val row = volume(database)(weekStart, weekEnd).first().single()

        assertEquals(gym, row.categoryId)
        assertEquals(5L, row.effectiveSets)
        assertEquals(1L, row.warmupSets)
        assertEquals(2L, row.distinctDays)
        assertEquals(2L, row.sessionCount)
    }

    @Test
    fun twoSessionsOnOneDayCountAsOneDayTrained() = runTest(dispatcher) {
        val database = db()
        val gym = category(database, "Gym")
        val other = category(database, "Calisthenics")
        val upper = muscle(database, "Chest", "Upper chest")

        // Two sessions, same day, same activity is impossible — startOrResume reuses
        // the row — so the two-sessions-one-day case needs a second activity to be
        // expressible at all. distinctDays must still read 1 for each.
        val a = workouts(database).startSession(gym, weekStart, 0L)
        val b = workouts(database).startSession(other, weekStart, 0L)
        workouts(database).addSets(a, upper, 3, 0)
        workouts(database).addSets(b, upper, 4, 0)

        val rows = volume(database)(weekStart, weekEnd).first().associateBy { it.categoryId }

        assertEquals(1L, rows.getValue(gym).distinctDays)
        assertEquals(1L, rows.getValue(other).distinctDays)
        assertEquals(3L, rows.getValue(gym).effectiveSets)
        assertEquals(4L, rows.getValue(other).effectiveSets)
    }

    @Test
    fun sessionsOutsideTheWeekDoNotLeakIn() = runTest(dispatcher) {
        val database = db()
        val gym = category(database, "Gym")
        val upper = muscle(database, "Chest", "Upper chest")

        val inside = workouts(database).startSession(gym, weekEnd, 0L)
        workouts(database).addSets(inside, upper, 2, 0)
        // The day after the week closes, and the day before it opens.
        val after = workouts(database).startSession(gym, weekEnd + 1, 0L)
        workouts(database).addSets(after, upper, 9, 0)
        val before = workouts(database).startSession(gym, weekStart - 1, 0L)
        workouts(database).addSets(before, upper, 7, 0)

        val row = volume(database)(weekStart, weekEnd).first().single()

        // BETWEEN is inclusive at both ends, so the boundary days belong to the week
        // and only the neighbours are excluded.
        assertEquals(2L, row.effectiveSets)
        assertEquals(1L, row.distinctDays)
    }

    @Test
    fun aStartedButEmptySessionStillCountsAsTrainedThatDay() = runTest(dispatcher) {
        val database = db()
        val gym = category(database, "Gym")
        workouts(database).startSession(gym, weekStart, 0L)

        val row = volume(database)(weekStart, weekEnd).first().single()

        // A LEFT JOIN, so the session is counted even with nothing checked. The
        // activity genuinely has a log that week; hiding it would leave a row the
        // week view could not reach.
        assertEquals(0L, row.effectiveSets)
        assertEquals(1L, row.sessionCount)
        assertEquals(1L, row.distinctDays)
    }

    @Test
    fun eachActivityIsSummedSeparately() = runTest(dispatcher) {
        val database = db()
        val gym = category(database, "Gym")
        val other = category(database, "Calisthenics")
        category(database, "Never trained")
        val upper = muscle(database, "Chest", "Upper chest")

        workouts(database).startSession(gym, weekStart, 0L).also {
            workouts(database).addSets(it, upper, 3, 0)
        }
        workouts(database).startSession(other, weekStart + 1, 0L).also {
            workouts(database).addSets(it, upper, 5, 0)
        }

        val rows = volume(database)(weekStart, weekEnd).first().associateBy { it.categoryId }

        assertEquals(2, rows.size)
        assertEquals(3L, rows.getValue(gym).effectiveSets)
        assertEquals(5L, rows.getValue(other).effectiveSets)
        assertNull(rows[category(database, "Also never")])
    }
}
