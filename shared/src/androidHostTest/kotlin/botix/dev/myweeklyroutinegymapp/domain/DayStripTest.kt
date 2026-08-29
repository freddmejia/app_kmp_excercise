package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionUseCase
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
 * What the day strip needs from the domain.
 *
 * The rule that matters most is that moving along the strip is a *read*: nothing
 * about choosing a day may write. Creating a log is a separate, deliberate act, so
 * the day view lists what exists rather than conjuring a session per day visited.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayStripTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Pinned so ranges and "today" are stable regardless of when the suite runs. */
    private class FixedClock(private val day: Long) : Clock {
        override fun nowMillis(): Long = 0L
        override fun today(): Long = day
    }

    private val today = 20_686L

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun workouts(db: GymDatabase) = WorkoutRepositoryImpl(db, dispatcher)

    private suspend fun seedActivity(db: GymDatabase, name: String = "Gym"): Long =
        ActivityCategoryRepositoryImpl(db, dispatcher).create(name, MeasurementType.STRENGTH, createdAt = 0L)

    @Test
    fun aWeekStartsOnTheMondayContainingTheDay() {
        // 20686 is Friday 21 Aug 2026; that week opens on Monday the 17th.
        val monday = 20_682L
        assertEquals(monday, weekStartOf(today))
        // Every day of that week resolves to the same Monday, which is what keeps the
        // strip still while you pick within it.
        for (offset in 0..6) {
            assertEquals(monday, weekStartOf(monday + offset))
        }
        // ...and the next day opens the next week.
        assertEquals(monday + 7, weekStartOf(monday + 7))
    }

    @Test
    fun theWeekIsCorrectForDatesBeforeTheEpoch() {
        // Epoch day 0 was a Thursday, so its week began on the Monday before it —
        // day -3, a negative. Kotlin's % keeps the sign of the dividend, so a
        // truncating remainder would push these into the following week instead.
        assertEquals(-3L, weekStartOf(0L))
        assertEquals(-3L, weekStartOf(-1L))
        assertEquals(-3L, weekStartOf(-3L))
        assertEquals(-10L, weekStartOf(-4L))
    }

    @Test
    fun steppingAWeekKeepsTheWeekdayColumn() {
        // The arrows move seven days, not "to next Monday": pressing on a Friday has
        // to land on a Friday, or the column you were aiming at moves under you.
        assertEquals(weekStartOf(today) + 7, weekStartOf(today + 7))
        assertEquals(today + 7 - weekStartOf(today + 7), today - weekStartOf(today))
    }

    @Test
    fun theDayLogListsEverySessionOnThatDayWithItsTotals() = runTest(dispatcher) {
        val database = db()
        val gym = seedActivity(database, "Gym")
        val other = seedActivity(database, "Calisthenics")
        val catalog = MuscleCatalogRepositoryImpl(database, dispatcher)
        val chest = catalog.createGroup("Chest", 0L)
        val upper = catalog.createMuscle(chest, "Upper chest", 0L)
        val lower = catalog.createMuscle(chest, "Lower chest", 0L)

        val morning = workouts(database).startSession(gym, today, 1L)
        workouts(database).addSets(morning, upper, 3, 1)
        workouts(database).addSets(morning, lower, 2, 0)
        val evening = workouts(database).startSession(other, today, 2L)
        workouts(database).addSets(evening, upper, 4, 0)

        val log = ObserveDayLogUseCase(workouts(database))(today).first()

        // A day can hold more than one session — the single "current session" the
        // screen used to assume could not express this at all.
        assertEquals(listOf("Gym", "Calisthenics"), log.map { it.categoryName })
        val first = log.first()
        assertEquals(5L, first.effectiveSets)
        assertEquals(1L, first.warmupSets)
        assertEquals(2L, first.muscleCount)
    }

    @Test
    fun theDayMusclesNameWhatEachLogWorked() = runTest(dispatcher) {
        val database = db()
        val gym = seedActivity(database, "Gym")
        val catalog = MuscleCatalogRepositoryImpl(database, dispatcher)
        val chest = catalog.createGroup("Chest", 0L)
        val upper = catalog.createMuscle(chest, "Upper chest", 0L)
        val lower = catalog.createMuscle(chest, "Lower chest", 0L)

        val morning = workouts(database).startSession(gym, today, 1L)
        workouts(database).addSets(morning, upper, 3, 1)
        workouts(database).addSets(morning, lower, 2, 0)
        // A second Gym log on the same day, and an empty one.
        val evening = workouts(database).startSession(gym, today, 2L)
        workouts(database).addSets(evening, upper, 4, 0)
        val abandoned = workouts(database).startSession(gym, today, 3L)

        val muscles = ObserveDayMusclesUseCase(workouts(database))(today).first()

        // The names are what the day card lists, and with no name on a session they
        // are the only thing telling these three Gym logs apart.
        assertEquals(listOf("Lower chest", "Upper chest"), muscles[morning])
        assertEquals(listOf("Upper chest"), muscles[evening])
        // Absent rather than empty: the card renders "nothing checked yet" for this
        // one, which is a different line, not an empty row of pills.
        assertNull(muscles[abandoned])
    }

    @Test
    fun theDayMusclesIgnoreOtherDays() = runTest(dispatcher) {
        val database = db()
        val gym = seedActivity(database, "Gym")
        val catalog = MuscleCatalogRepositoryImpl(database, dispatcher)
        val chest = catalog.createGroup("Chest", 0L)
        val upper = catalog.createMuscle(chest, "Upper chest", 0L)

        val yesterday = workouts(database).startSession(gym, today - 1, 0L)
        workouts(database).addSets(yesterday, upper, 3, 0)

        assertTrue(ObserveDayMusclesUseCase(workouts(database))(today).first().isEmpty())
    }

    @Test
    fun theDayLogKeepsASessionWithNothingCheckedInIt() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        workouts(database).startSession(categoryId, today, 0L)

        val log = ObserveDayLogUseCase(workouts(database))(today).first()

        // A LEFT JOIN, deliberately. Hiding it would leave a row that cannot be
        // reopened or deleted, silently inflating every count that scans sessions.
        assertEquals(1, log.size)
        assertTrue(log.single().isEmpty)
        assertEquals(0L, log.single().effectiveSets)
    }

    @Test
    fun theDayLogShowsOnlyTheSelectedDay() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        workouts(database).startSession(categoryId, today, 0L)
        workouts(database).startSession(categoryId, today - 1, 0L)

        assertEquals(1, ObserveDayLogUseCase(workouts(database))(today).first().size)
        assertEquals(1, ObserveDayLogUseCase(workouts(database))(today - 1).first().size)
        assertTrue(ObserveDayLogUseCase(workouts(database))(today + 1).first().isEmpty())
    }

    @Test
    fun deletingALogTakesItsEntriesWithIt() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        val catalog = MuscleCatalogRepositoryImpl(database, dispatcher)
        val chest = catalog.createGroup("Chest", 0L)
        val upper = catalog.createMuscle(chest, "Upper chest", 0L)
        val sessionId = workouts(database).startSession(categoryId, today, 0L)
        workouts(database).addSets(sessionId, upper, 3, 0)

        DeleteSessionUseCase(workouts(database))(sessionId)

        assertTrue(ObserveDayLogUseCase(workouts(database))(today).first().isEmpty())
        // Explicit in the repository rather than left to ON DELETE CASCADE, which
        // needs a pragma this driver does not set. Orphaned entries would keep
        // counting toward per-muscle volume for a session that no longer exists.
        assertTrue(workouts(database).observeEntries(sessionId).first().isEmpty())
    }

    @Test
    fun deletingOneLogLeavesTheOthersOnThatDay() = runTest(dispatcher) {
        val database = db()
        val gym = seedActivity(database, "Gym")
        val other = seedActivity(database, "Calisthenics")
        val keep = workouts(database).startSession(gym, today, 1L)
        val drop = workouts(database).startSession(other, today, 2L)

        DeleteSessionUseCase(workouts(database))(drop)

        val log = ObserveDayLogUseCase(workouts(database))(today).first()
        assertEquals(listOf(keep), log.map { it.sessionId })
    }

    @Test
    fun sessionDaysMarksOnlyTheDaysActuallyTrained() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        workouts(database).startSession(categoryId, today, 0L)
        workouts(database).startSession(categoryId, today - 2, 0L)

        val marked = ObserveSessionDaysUseCase(workouts(database))(today - 3, today + 3).first()

        assertEquals(setOf(today, today - 2), marked)
    }

    @Test
    fun sessionDaysIgnoresSessionsOutsideTheVisibleWindow() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        workouts(database).startSession(categoryId, today, 0L)
        workouts(database).startSession(categoryId, today - 40, 0L)

        // The query follows the strip, so a session well outside it must not light up
        // a cell that happens to share a column position.
        assertEquals(setOf(today), ObserveSessionDaysUseCase(workouts(database))(today - 3, today + 3).first())
    }

    @Test
    fun sessionDaysCountsAnyActivityNotJustTheSelectedOne() = runTest(dispatcher) {
        val database = db()
        val gym = seedActivity(database, "Gym")
        val other = seedActivity(database, "Calisthenics")
        workouts(database).startSession(gym, today, 0L)
        workouts(database).startSession(other, today - 1, 0L)

        // The marker answers "did I train that day", which is the question you are
        // asking while scanning the strip for a day to open.
        assertEquals(setOf(today, today - 1), ObserveSessionDaysUseCase(workouts(database))(today - 3, today + 3).first())
    }

    @Test
    fun untrainedMusclesFollowTheDayBeingViewedNotToday() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        val catalog = MuscleCatalogRepositoryImpl(database, dispatcher)
        val chest = catalog.createGroup("Chest", 0L)
        val upper = catalog.createMuscle(chest, "Upper chest", 0L)
        catalog.createMuscle(chest, "Lower chest", 0L)

        // Trained 20 days ago: outside a window ending today, inside one ending then.
        val longAgo = today - 20
        val sessionId = workouts(database).startSession(categoryId, longAgo, 0L)
        workouts(database).addSets(sessionId, upper, 3, 0)

        val useCase = ObserveUntrainedMusclesUseCase(workouts(database), FixedClock(today))

        // Anchored to today, that session is ancient — both muscles look untrained.
        assertEquals(
            setOf("Upper chest", "Lower chest"),
            useCase().first().map { it.muscleName }.toSet(),
        )
        // Anchored to the day on screen, only the one never trained shows up.
        assertEquals(
            listOf("Lower chest"),
            useCase(endDay = longAgo).first().map { it.muscleName },
        )
    }

    @Test
    fun startingASessionUsesTheDayItWasGivenNotToday() = runTest(dispatcher) {
        val database = db()
        val categoryId = seedActivity(database)
        val past = today - 2

        StartSessionUseCase(
            workouts(database),
            ActivityCategoryRepositoryImpl(database, dispatcher),
            FixedClock(today),
        )(categoryId, past)

        val sessions = workouts(database).observeSessionsInRange(past - 5, today).first()
        assertEquals(listOf(past), sessions.map { it.epochDay })
    }
}
