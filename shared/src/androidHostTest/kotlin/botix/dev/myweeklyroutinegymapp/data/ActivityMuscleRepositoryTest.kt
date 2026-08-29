package botix.dev.myweeklyroutinegymapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityMuscleRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The optional activity -> muscle link.
 *
 * The behaviours worth pinning down are the ones that make it *optional*: an empty
 * set has to mean "no opinion" rather than "targets nothing", and the link must never
 * outlive the muscle it points at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityMuscleRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun links(db: GymDatabase) = ActivityMuscleRepositoryImpl(db, dispatcher)
    private fun activities(db: GymDatabase) = ActivityRepositoryImpl(db, dispatcher)
    private fun muscles(db: GymDatabase) = MuscleCatalogRepositoryImpl(db, dispatcher)
    private fun workouts(db: GymDatabase) = WorkoutRepositoryImpl(db, dispatcher)

    /** A strength activity plus a Chest group with two leaves. */
    private suspend fun seed(db: GymDatabase): Triple<Long, Long, Long> {
        val activityId = activities(db).create("Gym", MeasurementType.STRENGTH, createdAt = 0L)
        val groupId = muscles(db).createGroup("Chest", 0L)
        val upper = muscles(db).createMuscle(groupId, "Upper chest", 0L)
        val lower = muscles(db).createMuscle(groupId, "Lower chest", 0L)
        return Triple(activityId, upper, lower)
    }

    @Test
    fun linkingIsReflectedInBothTheScopedPickerAndTheLinkMap() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)

        links(database).setMusclesFor(activityId, setOf(upper))

        assertEquals(listOf("Upper chest"), links(database).observeMusclesFor(activityId).first().map { it.muscleName })
        assertEquals(mapOf(activityId to setOf(upper)), links(database).observeAllLinks().first())
    }

    @Test
    fun settingReplacesTheWholeSelectionRatherThanAddingToIt() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, lower) = seed(database)
        val repo = links(database)

        repo.setMusclesFor(activityId, setOf(upper, lower))
        assertEquals(2, repo.observeMusclesFor(activityId).first().size)

        repo.setMusclesFor(activityId, setOf(lower))

        assertEquals(listOf("Lower chest"), repo.observeMusclesFor(activityId).first().map { it.muscleName })
    }

    @Test
    fun anEmptySetClearsTheLinkAndIsNotAnError() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)
        val repo = links(database)
        repo.setMusclesFor(activityId, setOf(upper))

        repo.setMusclesFor(activityId, emptySet())

        // No links at all, which callers read as "no opinion" and answer with the
        // whole catalogue — not as an activity that targets nothing.
        assertTrue(repo.observeMusclesFor(activityId).first().isEmpty())
        assertTrue(repo.observeAllLinks().first().isEmpty())
    }

    @Test
    fun linkingTheSameMuscleTwiceIsIdempotent() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)

        links(database).setMusclesFor(activityId, setOf(upper))
        links(database).setMusclesFor(activityId, setOf(upper))

        // The composite primary key plus INSERT OR IGNORE, rather than a duplicate
        // row that would double the "targets N muscles" count.
        assertEquals(1, links(database).observeMusclesFor(activityId).first().size)
    }

    @Test
    fun theScopedPickerHidesArchivedMusclesButTheLinkSurvives() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, lower) = seed(database)
        links(database).setMusclesFor(activityId, setOf(upper, lower))

        muscles(database).setMuscleArchived(upper, true)

        // Out of the picker, since archiving is what removes a muscle from logging...
        assertEquals(listOf("Lower chest"), links(database).observeMusclesFor(activityId).first().map { it.muscleName })
        // ...but still linked, because archiving is reversible and the selection form
        // saves what it shows: dropping it here would delete it on the next save.
        assertEquals(setOf(upper, lower), links(database).observeAllLinks().first()[activityId])
    }

    @Test
    fun deletingAMuscleDropsTheLinksPointingAtIt() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, lower) = seed(database)
        links(database).setMusclesFor(activityId, setOf(upper, lower))

        muscles(database).deleteMuscle(upper)

        // Explicit cleanup in the same transaction, not the FK's ON DELETE CASCADE:
        // that action only fires while PRAGMA foreign_keys is on, which the JDBC
        // driver leaves off.
        assertEquals(setOf(lower), links(database).observeAllLinks().first()[activityId])
    }

    @Test
    fun deletingAGroupDropsTheLinksToEveryLeafInIt() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, lower) = seed(database)
        val catalog: MuscleCatalogRepository = muscles(database)
        val groupId = catalog.observeActiveGroups().first().single().id
        links(database).setMusclesFor(activityId, setOf(upper, lower))

        catalog.deleteGroup(groupId)

        assertTrue(links(database).observeAllLinks().first().isEmpty())
    }

    @Test
    fun deletingAnActivityDropsItsLinks() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)
        links(database).setMusclesFor(activityId, setOf(upper))

        activities(database).delete(activityId)

        assertTrue(links(database).observeAllLinks().first().isEmpty())
    }

    @Test
    fun theLinkDoesNotRestrictWhatCanActuallyBeLogged() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, lower) = seed(database)
        links(database).setMusclesFor(activityId, setOf(upper))
        val sessionId = workouts(database).startOrResumeSession(activityId, 20_000L, 0L)

        // A muscle the activity does not link is still loggable. The link is a picker
        // default; enforcing it in the data layer would make an unplanned exercise
        // impossible to record without editing the activity first.
        workouts(database).addSets(sessionId, lower, 3L, 0L)

        val entries = workouts(database).observeEntries(sessionId).first()
        assertEquals(listOf("Lower chest"), entries.map { it.muscleName })
    }
}
