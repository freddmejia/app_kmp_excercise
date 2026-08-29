package botix.dev.myweeklyroutinegymapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
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
    private fun activities(db: GymDatabase) = ActivityRepositoryImpl(db, dispatcher)

    @Test
    fun categoryMapsToDomain() = runTest(dispatcher) {
        val repo = categories(db())
        val id = repo.create("Cardio", icon = "🏃", createdAt = 7L)

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

        repo.create("Running", createdAt = 0L)

        assertEquals(listOf("Running"), repo.observeActive().first().map { it.name })
    }

    @Test
    fun activeNamesAreUniqueButArchivedOnesAreExempt() = runTest(dispatcher) {
        val repo = categories(db())
        val first = repo.create("Cardio", createdAt = 0L)
        assertTrue(repo.isNameTaken("Cardio"))
        // Renaming a row to the name it already holds is not a clash with itself.
        assertFalse(repo.isNameTaken("Cardio", excludingId = first))

        repo.setArchived(first, true)
        assertFalse(repo.isNameTaken("Cardio"))
    }

    @Test
    fun usageCountsActivitiesAndStillListsEmptyCategories() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", createdAt = 0L)
        categories(database).create("Mobility", createdAt = 0L)
        activities(database).create("Running", MeasurementType.DISTANCE_TIME, categoryId = cardio, createdAt = 0L)
        activities(database).create("Cycling", MeasurementType.DISTANCE_TIME, categoryId = cardio, createdAt = 0L)

        val usage = categories(database).observeActiveWithUsage().first()

        assertEquals(listOf("Cardio", "Mobility"), usage.map { it.category.name })
        assertEquals(2L, usage.first { it.category.name == "Cardio" }.activityCount)
        // A LEFT JOIN, so the empty category still appears — it is the one you are
        // most likely to want to rename or delete.
        assertEquals(0L, usage.first { it.category.name == "Mobility" }.activityCount)
    }

    @Test
    fun deletingACategoryUncategorisesItsActivitiesInsteadOfBeingBlocked() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", createdAt = 0L)
        val running = activities(database).create(
            "Running",
            MeasurementType.DISTANCE_TIME,
            categoryId = cardio,
            createdAt = 0L,
        )

        val outcome = categories(database).delete(cardio)

        // The count is the point: it is read before the rows are detached, so the
        // screen can report what actually happened.
        assertEquals(1L, outcome.uncategorisedCount)
        assertTrue(categories(database).observeActive().first().isEmpty())
        // The activity survives; only its label is gone.
        val stored = activities(database).getById(running)!!
        assertEquals("Running", stored.name)
        assertNull(stored.categoryId)
    }

    @Test
    fun archivingACategoryAlsoDetachesItsActivities() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", createdAt = 0L)
        val running = activities(database).create(
            "Running",
            MeasurementType.DISTANCE_TIME,
            categoryId = cardio,
            createdAt = 0L,
        )

        categories(database).setArchived(cardio, true)

        // An archived category is absent from every picker. Leaving the activity
        // pointed at it would strand the row in a filter nothing can select.
        assertNull(activities(database).getById(running)!!.categoryId)
        assertEquals(1, categories(database).observeArchived().first().size)
    }

    @Test
    fun anActivityMovesBetweenCategoriesAndBackToNone() = runTest(dispatcher) {
        val database = db()
        val cardio = categories(database).create("Cardio", createdAt = 0L)
        val mobility = categories(database).create("Mobility", createdAt = 0L)
        val repo = activities(database)
        val id = repo.create("Stretching", MeasurementType.SIMPLE, categoryId = cardio, createdAt = 0L)

        repo.setCategory(id, mobility)
        assertEquals(mobility, repo.getById(id)!!.categoryId)

        repo.setCategory(id, null)
        assertNull(repo.getById(id)!!.categoryId)
    }

    @Test
    fun renamingACategoryKeepsItsActivitiesAttached() = runTest(dispatcher) {
        val database = db()
        val id = categories(database).create("Cardio", createdAt = 0L)
        val running = activities(database).create(
            "Running",
            MeasurementType.DISTANCE_TIME,
            categoryId = id,
            createdAt = 0L,
        )

        categories(database).updateIdentity(id, "Endurance", "🏃", null)

        // Referenced by id, never by name — the rename relabels rather than detaches.
        assertEquals(id, activities(database).getById(running)!!.categoryId)
        assertEquals("Endurance", categories(database).getById(id)!!.name)
    }
}
