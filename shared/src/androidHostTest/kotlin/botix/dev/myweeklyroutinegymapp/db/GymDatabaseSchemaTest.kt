package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Exercises the schema against a real in-memory SQLite, so the integrity rules the
 * feature docs specify are verified rather than assumed.
 */
class GymDatabaseSchemaTest {

    /** Kept so a test can reach the connection for a PRAGMA; see the FK test below. */
    private val drivers = mutableMapOf<GymDatabase, JdbcSqliteDriver>()

    private fun newDb(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver).also { drivers[it] = driver }
    }

    private fun driverOf(db: GymDatabase): JdbcSqliteDriver = drivers.getValue(db)

    @Test
    fun measurementTypeRoundTripsAsEnum() {
        val db = newDb()
        db.activityCategoryQueries.insert("Running", "run", "#FF0000", 0L, MeasurementType.DISTANCE_TIME)
        val stored = db.activityCategoryQueries.selectAll().executeAsOne()
        assertEquals(MeasurementType.DISTANCE_TIME, stored.measurement_type)
        assertEquals(false, stored.is_archived)
    }

    @Test
    fun activeCategoryNamesAreUniqueButArchivedOnesAreExempt() {
        val db = newDb()
        db.activityCategoryQueries.insert("Cardio", null, null, 0L, MeasurementType.STRENGTH)
        assertFailsWith<Exception> { db.activityCategoryQueries.insert("Cardio", null, null, 0L, MeasurementType.STRENGTH) }

        db.activityCategoryQueries.setArchived(true, 1L)
        db.activityCategoryQueries.insert("Cardio", null, null, 1L, MeasurementType.STRENGTH)
        assertEquals(2, db.activityCategoryQueries.selectAll().executeAsList().size)
    }

    @Test
    fun bodyWeightKeepsOneRowPerDay() {
        val db = newDb()
        db.bodyWeightQueries.upsert(20_000L, 80.5, "fasted")
        db.bodyWeightQueries.upsert(20_000L, 81.0, "after dinner")
        val all = db.bodyWeightQueries.selectAll().executeAsList()
        assertEquals(1, all.size)
        assertEquals(81.0, all.single().weight_kg)
    }

    @Test
    fun sessionMuscleSumsInsteadOfDuplicating() {
        val db = newDb()
        db.activityCategoryQueries.insert("Gym", null, null, 0L, MeasurementType.STRENGTH)
        db.muscleCatalogQueries.insertGroup("Chest", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "Upper chest", 0L)
        db.workoutSessionQueries.insertSession(1L, 20_000L, null, 0L)

        db.workoutSessionQueries.insertEntry(1L, 1L, 3L, 1L)
        // Same muscle later the same day: add to the existing row, do not duplicate.
        db.workoutSessionQueries.addToEntry(2L, 1L, 1L, 1L)

        val entries = db.workoutSessionQueries.selectEntriesForSession(1L).executeAsList()
        assertEquals(1, entries.size)
        assertEquals(5L, entries.single().effective_sets)

        assertFailsWith<Exception> { db.workoutSessionQueries.insertEntry(1L, 1L, 2L, 0L) }
    }

    @Test
    fun effectiveSetsMustBeAtLeastOne() {
        val db = newDb()
        db.activityCategoryQueries.insert("Gym", null, null, 0L, MeasurementType.STRENGTH)
        db.muscleCatalogQueries.insertGroup("Back", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "General", 0L)
        db.workoutSessionQueries.insertSession(1L, 20_000L, null, 0L)
        assertFailsWith<Exception> { db.workoutSessionQueries.insertEntry(1L, 1L, 0L, 0L) }
    }

    @Test
    fun groupVolumeAggregatesAllItsLeaves() {
        val db = newDb()
        db.activityCategoryQueries.insert("Gym", null, null, 0L, MeasurementType.STRENGTH)
        db.muscleCatalogQueries.insertGroup("Chest", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "Upper chest", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "General", 0L)
        db.workoutSessionQueries.insertSession(1L, 20_000L, null, 0L)
        db.workoutSessionQueries.insertEntry(1L, 1L, 3L, 0L)
        db.workoutSessionQueries.insertEntry(1L, 2L, 2L, 0L)

        val perGroup = db.workoutSessionQueries.volumePerGroupInRange(19_000L, 21_000L).executeAsOne()
        assertEquals("Chest", perGroup.group_name)
        assertEquals(5L, perGroup.effective_sets)
    }

    @Test
    fun untrainedMusclesSurfaceWithZeroVolume() {
        val db = newDb()
        db.activityCategoryQueries.insert("Gym", null, null, 0L, MeasurementType.STRENGTH)
        db.muscleCatalogQueries.insertGroup("Legs", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "Quads", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "Hamstrings", 0L)
        db.workoutSessionQueries.insertSession(1L, 20_000L, null, 0L)
        db.workoutSessionQueries.insertEntry(1L, 1L, 4L, 0L)

        val untrained = db.workoutSessionQueries.untrainedMusclesInRange(19_000L, 21_000L).executeAsList()
        assertEquals(1, untrained.size)
        assertEquals("Hamstrings", untrained.single().muscle_name)
        assertEquals("Legs", untrained.single().group_name)
    }
}
