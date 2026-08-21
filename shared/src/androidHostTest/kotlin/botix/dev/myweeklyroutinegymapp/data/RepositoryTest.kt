package botix.dev.myweeklyroutinegymapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-level behaviour: mapping, the integrity rules the specs describe, and
 * the transactional paths that exist because minSdk 24 rules out UPSERT.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun activities(db: GymDatabase) = ActivityRepositoryImpl(db, dispatcher)
    private fun muscles(db: GymDatabase) = MuscleCatalogRepositoryImpl(db, dispatcher)
    private fun workouts(db: GymDatabase) = WorkoutRepositoryImpl(db, dispatcher)
    private fun bodyWeight(db: GymDatabase) = BodyWeightRepositoryImpl(db, dispatcher)

    /** Seeds a strength activity with one group and one leaf; returns activity to muscle id. */
    private suspend fun seedStrength(db: GymDatabase): Pair<Long, Long> {
        val activityId = activities(db).create("Gym", MeasurementType.STRENGTH, createdAt = 0L)
        val groupId = muscles(db).createGroup("Chest", 0L)
        val muscleId = muscles(db).createMuscle(groupId, "Upper chest", 0L)
        return activityId to muscleId
    }

    @Test
    fun activityMapsToDomainWithItsEnum() = runTest(dispatcher) {
        val repo = activities(db())
        val id = repo.create("Running", MeasurementType.DISTANCE_TIME, icon = "run", createdAt = 7L)

        val stored = repo.getById(id)!!
        assertEquals("Running", stored.name)
        assertEquals(MeasurementType.DISTANCE_TIME, stored.measurementType)
        assertEquals("run", stored.icon)
        assertNull(stored.color)
        assertEquals(false, stored.isArchived)
        assertEquals(7L, stored.createdAt)
    }

    @Test
    fun observedListReEmitsAfterAWrite() = runTest(dispatcher) {
        val repo = activities(db())
        assertTrue(repo.observeActive().first().isEmpty())

        repo.create("Gym", MeasurementType.STRENGTH, createdAt = 0L)

        // The point of the Flow: no manual invalidation between write and read.
        assertEquals(listOf("Gym"), repo.observeActive().first().map { it.name })
    }

    @Test
    fun archivingRemovesFromActiveButKeepsTheRow() = runTest(dispatcher) {
        val repo = activities(db())
        val id = repo.create("Gym", MeasurementType.STRENGTH, createdAt = 0L)

        repo.setArchived(id, true)

        assertTrue(repo.observeActive().first().isEmpty())
        assertEquals(1, repo.observeArchived().first().size)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun deleteIsBlockedOnceTheActivityHasSessions() = runTest(dispatcher) {
        val database = db()
        val (activityId, _) = seedStrength(database)
        val repo = activities(database)

        val spare = repo.create("Spare", MeasurementType.SIMPLE, createdAt = 0L)
        assertIs<DeleteOutcome.Deleted>(repo.delete(spare))

        workouts(database).startOrResumeSession(activityId, epochDay = 20_000L, createdAt = 0L)

        val blocked = repo.delete(activityId)
        assertIs<DeleteOutcome.BlockedByHistory>(blocked)
        assertEquals(1L, blocked.referenceCount)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun measurementTypeLocksAfterTheFirstSession() = runTest(dispatcher) {
        val database = db()
        val (activityId, _) = seedStrength(database)
        val repo = activities(database)

        assertIs<MeasurementTypeChange.Changed>(
            repo.changeMeasurementType(activityId, MeasurementType.SIMPLE),
        )

        workouts(database).startOrResumeSession(activityId, epochDay = 20_000L, createdAt = 0L)

        val blocked = repo.changeMeasurementType(activityId, MeasurementType.DISTANCE_TIME)
        assertIs<MeasurementTypeChange.BlockedByHistory>(blocked)
        assertEquals(MeasurementType.SIMPLE, repo.getById(activityId)!!.measurementType)
    }

    @Test
    fun startOrResumeReturnsTheSameSessionForTheSameDay() = runTest(dispatcher) {
        val database = db()
        val (activityId, _) = seedStrength(database)
        val repo = workouts(database)

        val first = repo.startOrResumeSession(activityId, 20_000L, 0L)
        val second = repo.startOrResumeSession(activityId, 20_000L, 0L)
        val otherDay = repo.startOrResumeSession(activityId, 20_001L, 0L)

        assertEquals(first, second)
        assertTrue(otherDay != first)
        assertEquals(2, repo.observeSessionsInRange(19_000L, 21_000L).first().size)
    }

    @Test
    fun addSetsSumsInsteadOfCreatingASecondEntry() = runTest(dispatcher) {
        val database = db()
        val (activityId, muscleId) = seedStrength(database)
        val repo = workouts(database)
        val sessionId = repo.startOrResumeSession(activityId, 20_000L, 0L)

        repo.addSets(sessionId, muscleId, effectiveSets = 3, warmupSets = 1)
        repo.addSets(sessionId, muscleId, effectiveSets = 2, warmupSets = 1)

        val entries = repo.observeEntries(sessionId).first()
        assertEquals(1, entries.size)
        assertEquals(5L, entries.single().effectiveSets)
        assertEquals("Upper chest", entries.single().muscleName)
        assertEquals("Chest", entries.single().groupName)

        val totals = repo.observeTotals(sessionId).first()
        assertEquals(5L, totals.effectiveSets)
        assertEquals(2L, totals.warmupSets)
        assertEquals(0.4, totals.warmupRatio)
    }

    @Test
    fun setSetsOverwritesWhereAddSetsAccumulates() = runTest(dispatcher) {
        val database = db()
        val (activityId, muscleId) = seedStrength(database)
        val repo = workouts(database)
        val sessionId = repo.startOrResumeSession(activityId, 20_000L, 0L)

        repo.addSets(sessionId, muscleId, 3, 0)
        repo.setSets(sessionId, muscleId, 2, 1)

        assertEquals(2L, repo.observeEntries(sessionId).first().single().effectiveSets)
    }

    @Test
    fun uncheckingAMuscleRemovesItsEntry() = runTest(dispatcher) {
        val database = db()
        val (activityId, muscleId) = seedStrength(database)
        val repo = workouts(database)
        val sessionId = repo.startOrResumeSession(activityId, 20_000L, 0L)

        repo.addSets(sessionId, muscleId, 3, 0)
        repo.removeMuscle(sessionId, muscleId)

        assertTrue(repo.observeEntries(sessionId).first().isEmpty())
        assertEquals(0L, repo.observeTotals(sessionId).first().effectiveSets)
        assertNull(repo.observeTotals(sessionId).first().warmupRatio)
    }

    @Test
    fun groupVolumeAggregatesLeavesAndUntrainedSurfacesTheRest() = runTest(dispatcher) {
        val database = db()
        val (activityId, upperChest) = seedStrength(database)
        val catalog = muscles(database)
        val chestId = catalog.observeActiveGroups().first().single().id
        val general = catalog.createMuscle(chestId, "General", 0L)
        val legs = catalog.createGroup("Legs", 0L)
        catalog.createMuscle(legs, "Quads", 0L)

        val repo = workouts(database)
        val sessionId = repo.startOrResumeSession(activityId, 20_000L, 0L)
        repo.addSets(sessionId, upperChest, 3, 0)
        repo.addSets(sessionId, general, 2, 0)

        val perGroup = repo.observeVolumePerGroup(19_000L, 21_000L).first()
        assertEquals(1, perGroup.size)
        assertEquals("Chest", perGroup.single().groupName)
        assertEquals(5L, perGroup.single().effectiveSets)

        val untrained = repo.observeUntrainedMuscles(19_000L, 21_000L).first()
        assertEquals(listOf("Quads"), untrained.map { it.muscleName })
    }

    @Test
    fun muscleDeletionIsBlockedByHistoryButArchivingIsNot() = runTest(dispatcher) {
        val database = db()
        val (activityId, muscleId) = seedStrength(database)
        val catalog = muscles(database)
        val repo = workouts(database)
        val sessionId = repo.startOrResumeSession(activityId, 20_000L, 0L)
        repo.addSets(sessionId, muscleId, 3, 0)

        assertIs<DeleteOutcome.BlockedByHistory>(catalog.deleteMuscle(muscleId))

        // Deleting the parent group is blocked too, through its leaves.
        val chestId = catalog.observeActiveGroups().first().single().id
        assertIs<DeleteOutcome.BlockedByHistory>(catalog.deleteGroup(chestId))

        catalog.setMuscleArchived(muscleId, true)
        assertTrue(catalog.observePicker().first().isEmpty())

        // Archived, but its historical volume is untouched.
        assertEquals(3L, repo.observeVolumePerMuscle(19_000L, 21_000L).first().single().effectiveSets)
    }

    @Test
    fun bodyWeightKeepsOneEntryPerDayAndReportsItsRange() = runTest(dispatcher) {
        val repo = bodyWeight(db())
        repo.upsert(20_000L, 80.5, "fasted")
        repo.upsert(20_000L, 81.0, null)
        repo.upsert(20_001L, 79.0, null)

        val all = repo.observeAll().first()
        assertEquals(2, all.size)
        assertEquals(79.0, repo.observeLatest().first()!!.weightKg)
        assertEquals(81.0, repo.getByDay(20_000L)!!.weightKg)
        assertNull(repo.getByDay(20_000L)!!.note)

        val stats = repo.observeRangeStats(19_000L, 21_000L).first()
        assertEquals(79.0, stats.minKg)
        assertEquals(81.0, stats.maxKg)
        assertTrue(stats.hasTrend)
    }

    @Test
    fun anEmptyBodyWeightRangeHasNoExtremesRatherThanZero() = runTest(dispatcher) {
        val stats = bodyWeight(db()).observeRangeStats(0L, 10L).first()
        assertNull(stats.minKg)
        assertNull(stats.maxKg)
        assertEquals(0L, stats.sampleCount)
        assertTrue(!stats.hasTrend)
    }
}
