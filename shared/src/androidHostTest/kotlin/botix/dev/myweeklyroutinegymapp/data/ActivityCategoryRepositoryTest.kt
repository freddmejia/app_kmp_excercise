package botix.dev.myweeklyroutinegymapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The category catalogue, and the one behaviour that separates it from every other
 * entity here: its delete is unrestricted, because it labels activities rather than
 * being referenced by history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityCategoryRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun categories(db: GymDatabase) = ActivityCategoryRepositoryImpl(db, dispatcher)
    private fun workouts(db: GymDatabase) = WorkoutRepositoryImpl(db, dispatcher)

    @Test
    fun categoryMapsToDomain() = runTest(dispatcher) {
        val repo = categories(db())
        val id = repo.create("Cardio", MeasurementType.STRENGTH, icon = "🏃", createdAt = 7L)

        val stored = repo.getById(id)!!
        assertEquals("Cardio", stored.name)
        assertEquals("🏃", stored.icon)
        assertNull(stored.color)
        assertFalse(stored.isArchived)
        assertEquals(7L, stored.createdAt)
    }

    @Test
    fun observedListReEmitsAfterAWrite() = runTest(dispatcher) {
        val repo = categories(db())
        assertTrue(repo.observeActive().first().isEmpty())

        repo.create("Running", MeasurementType.STRENGTH, createdAt = 0L)

        assertEquals(listOf("Running"), repo.observeActive().first().map { it.name })
    }

    @Test
    fun activeNamesAreUniqueButArchivedOnesAreExempt() = runTest(dispatcher) {
        val repo = categories(db())
        val first = repo.create("Cardio", MeasurementType.STRENGTH, createdAt = 0L)
        assertTrue(repo.isNameTaken("Cardio"))
        // Renaming a row to the name it already holds is not a clash with itself.
        assertFalse(repo.isNameTaken("Cardio", excludingId = first))

        repo.setArchived(first, true)
        assertFalse(repo.isNameTaken("Cardio"))
    }

    @Test
    fun usageCountsLogsAndStillListsEmptyCategories() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", MeasurementType.STRENGTH, createdAt = 0L)
        categories(database).create("Mobility", MeasurementType.STRENGTH, createdAt = 0L)
        workouts(database).startSession(cardio, epochDay = 20_000L, createdAt = 0L)
        workouts(database).startSession(cardio, epochDay = 20_001L, createdAt = 0L)

        val usage = categories(database).observeActiveWithUsage().first()

        assertEquals(listOf("Cardio", "Mobility"), usage.map { it.category.name })
        // Logs, not activities: the count followed the thing that actually references
        // a category once activities were removed.
        assertEquals(2L, usage.first { it.category.name == "Cardio" }.sessionCount)
        // A LEFT JOIN, so the empty category still appears — it is the one you are
        // most likely to want to rename or delete.
        assertEquals(0L, usage.first { it.category.name == "Mobility" }.sessionCount)
    }

    @Test
    fun deletingACategoryStripsTheLabelOffItsLogsInsteadOfBeingBlocked() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", MeasurementType.STRENGTH, createdAt = 0L)
        val session = workouts(database).startSession(cardio, epochDay = 20_000L, createdAt = 0L)
        workouts(database).setDistanceTime(session, distanceMeters = 8_400, durationSeconds = 2_640)

        val outcome = categories(database).delete(cardio)

        // The count is the point: it is read before the rows are detached, so the
        // screen can report what actually happened.
        assertEquals(1L, outcome.strippedCount)
        assertTrue(categories(database).observeActive().first().isEmpty())

        // The log survives with its measurements intact. This is the whole reason a
        // category delete is unrestricted where a muscle delete is not: nothing that
        // was *recorded* is lost, only the name on it.
        val stored = workouts(database).observeDaySummaries(20_000L).first().single()
        assertEquals(session, stored.sessionId)
        assertNull(stored.categoryId)
        assertNull(stored.categoryName)
        assertEquals(8_400L, stored.distanceMeters)
    }

    @Test
    fun archivingACategoryLeavesItsLogsAlone() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", MeasurementType.STRENGTH, createdAt = 0L)
        workouts(database).startSession(cardio, epochDay = 20_000L, createdAt = 0L)

        categories(database).setArchived(cardio, true)

        // It used to detach, because an archived category was a filter you could no
        // longer select and its activities would have been stranded in it. What points
        // at a category now is history, and tidying a picker is not a reason to rewrite
        // the record: the log keeps its label and simply cannot gain new siblings.
        val stored = workouts(database).observeDaySummaries(20_000L).first().single()
        assertEquals(cardio, stored.categoryId)
        assertEquals("Cardio", stored.categoryName)
        assertEquals(1, categories(database).observeArchived().first().size)
        assertTrue(categories(database).observeActive().first().isEmpty())
    }

    @Test
    fun renamingACategoryRelabelsItsLogs() = runTest(dispatcher) {
        val database = db()
        val id = categories(database).create("Cardio", MeasurementType.STRENGTH, createdAt = 0L)
        workouts(database).startSession(id, epochDay = 20_000L, createdAt = 0L)

        categories(database).updateIdentity(id, "Endurance", MeasurementType.STRENGTH, "🏃", null)

        // Referenced by id, never by name — the rename follows through to the log
        // rather than detaching it.
        val stored = workouts(database).observeDaySummaries(20_000L).first().single()
        assertEquals(id, stored.categoryId)
        assertEquals("Endurance", stored.categoryName)
    }
}
