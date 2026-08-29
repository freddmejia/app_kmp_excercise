package botix.dev.myweeklyroutinegymapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SeededGroup
import botix.dev.myweeklyroutinegymapp.domain.model.SeededMuscle
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleSeedSource
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SyncMuscleSeedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalogue the app ships.
 *
 * The interesting cases are all about *not* doing something: not duplicating on the
 * second launch, not resurrecting what the user deleted, not renaming what the user
 * named. A seed that only gets the first launch right is worse than no seed at all,
 * because it fights the person using it on every one after that.
 *
 * The real names come from string resources, which need a loaded resource bundle;
 * these use a fixture so the *rules* can be tested on a host JVM. What the fixture
 * cannot check — that every key in [ResourceMuscleSeed] resolves — is covered by
 * running the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MuscleSeedTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        // The production drivers turn this on; the JDBC one does not. Without it the
        // group-delete path would pass here and fail on a device.
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return createDatabase(driver)
    }

    private fun muscles(db: GymDatabase): MuscleCatalogRepository =
        MuscleCatalogRepositoryImpl(db, dispatcher)

    /** Two groups, three leaves — the shape of the real catalogue, none of its bulk. */
    private fun english() = listOf(
        SeededGroup(
            key = "chest",
            name = "Chest",
            leaves = listOf(
                SeededMuscle("chest.general", "General"),
                SeededMuscle("chest.upper", "Upper chest"),
            ),
        ),
        SeededGroup(
            key = "back",
            name = "Back",
            leaves = listOf(SeededMuscle("back.general", "General")),
        ),
    )

    /** The same catalogue after the device language changed. Same keys, new names. */
    private fun spanish() = listOf(
        SeededGroup(
            key = "chest",
            name = "Pecho",
            leaves = listOf(
                SeededMuscle("chest.general", "General"),
                SeededMuscle("chest.upper", "Pecho superior"),
            ),
        ),
        SeededGroup(
            key = "back",
            name = "Espalda",
            leaves = listOf(SeededMuscle("back.general", "General")),
        ),
    )

    /** Same shape as the other suites' fakes: nothing here depends on the day. */
    private class FixedClock(private val millis: Long) : Clock {
        override fun nowMillis(): Long = millis
        override fun today(): Long = 20_000L
    }

    @Test
    fun anEmptyCatalogueGetsTheWholeSeed() = runTest(dispatcher) {
        val repo = muscles(db())

        val result = repo.syncSeed(english(), now = 7L)

        assertEquals(2, result.groupsAdded)
        assertEquals(3, result.musclesAdded)
        assertEquals(0, result.namesUpdated)

        val groups = repo.observeGroups().first()
        assertEquals(listOf("Back", "Chest"), groups.map { it.name })
        // Ordered by name, as every catalogue query is.
        assertEquals(listOf("General", "General", "Upper chest"), repo.observeMuscles().first().map { it.name })
        assertEquals(7L, groups.first().createdAt)
    }

    @Test
    fun everyLeafLandsInItsOwnGroup() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)

        val chest = repo.observeGroups().first().first { it.name == "Chest" }
        assertEquals(
            listOf("General", "Upper chest"),
            repo.observeMusclesInGroup(chest.id).first().map { it.name },
        )
    }

    @Test
    fun aSecondLaunchChangesNothing() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)

        val again = repo.syncSeed(english(), now = 1L)

        // Not merely "no duplicates" — no writes at all, which is what keeps every
        // muscle Flow in the app from re-emitting on each start.
        assertTrue(!again.changedAnything)
        assertEquals(2, repo.observeGroups().first().size)
        assertEquals(3, repo.observeMuscles().first().size)
    }

    @Test
    fun changingLanguageRenamesTheShippedRowsInPlace() = runTest(dispatcher) {
        val db = db()
        val repo = muscles(db)
        repo.syncSeed(english(), now = 0L)
        val idsBefore = repo.observeMuscles().first().associate { it.name to it.id }

        val result = repo.syncSeed(spanish(), now = 1L)

        // Renamed, never re-inserted: the ids are the same rows, so every session
        // logged in English still points at them.
        assertEquals(0, result.groupsAdded)
        assertEquals(0, result.musclesAdded)
        assertEquals(3, result.namesUpdated) // Chest, Back, Upper chest — "General" was already right
        assertEquals(
            listOf("Espalda", "Pecho"),
            repo.observeGroups().first().map { it.name },
        )
        val upper = repo.observeMuscles().first().first { it.name == "Pecho superior" }
        assertEquals(idsBefore["Upper chest"], upper.id)
    }

    @Test
    fun aRenamedMuscleIsNeverRenamedAgain() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)
        val upper = repo.observeMuscles().first().first { it.name == "Upper chest" }

        repo.renameMuscle(upper.id, "Clavicular")

        // The rename is what hands the row over: no key, so nothing claims it back.
        assertNull(repo.getMuscle(upper.id)!!.seedKey)

        repo.syncSeed(spanish(), now = 1L)

        assertEquals("Clavicular", repo.getMuscle(upper.id)!!.name)
        // And it is not duplicated by a seed row taking its place, because the ledger
        // still holds the key.
        assertEquals(3, repo.observeMuscles().first().size)
    }

    @Test
    fun aRenamedGroupIsNeverRenamedAgain() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)
        val chest = repo.observeGroups().first().first { it.name == "Chest" }

        repo.renameGroup(chest.id, "Pectorals")
        repo.syncSeed(spanish(), now = 1L)

        val groups = repo.observeGroups().first()
        assertEquals(listOf("Espalda", "Pectorals"), groups.map { it.name })
        assertEquals(2, groups.size)
    }

    @Test
    fun aDeletedMuscleStaysDeleted() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)
        val upper = repo.observeMuscles().first().first { it.name == "Upper chest" }

        assertIs<DeleteOutcome.Deleted>(repo.deleteMuscle(upper.id))
        repo.syncSeed(english(), now = 1L)

        // The ledger, not the row, is what says "already done".
        assertEquals(listOf("General", "General"), repo.observeMuscles().first().map { it.name })
    }

    @Test
    fun aDeletedGroupTakesItsLeavesAndStaysDeleted() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)
        val chest = repo.observeGroups().first().first { it.name == "Chest" }

        // The delete itself is the regression: muscle.group_id has no ON DELETE
        // clause and the driver enforces foreign keys, so a group with leaves used to
        // fail outright — which every shipped group has.
        assertIs<DeleteOutcome.Deleted>(repo.deleteGroup(chest.id))

        repo.syncSeed(english(), now = 1L)

        assertEquals(listOf("Back"), repo.observeGroups().first().map { it.name })
        // Its leaves went with it, and the sync did not put them back with no parent.
        assertEquals(listOf("General"), repo.observeMuscles().first().map { it.name })
    }

    @Test
    fun aDeletedGroupIsStillBlockedByHistory() = runTest(dispatcher) {
        val db = db()
        val repo = muscles(db)
        repo.syncSeed(english(), now = 0L)
        val chest = repo.observeGroups().first().first { it.name == "Chest" }
        val upper = repo.observeMusclesInGroup(chest.id).first().first { it.name == "Upper chest" }

        val activityId = ActivityRepositoryImpl(db, dispatcher)
            .create("Gym", MeasurementType.STRENGTH, createdAt = 0L)
        val workouts = WorkoutRepositoryImpl(db, dispatcher)
        val session = workouts.startOrResumeSession(activityId, epochDay = 20_000L, createdAt = 0L)
        workouts.addSets(session, upper.id, effectiveSets = 3L, warmupSets = 0L)

        // Deleting the leaves first must not have widened what a delete is allowed to
        // destroy: the history check still comes before any of it.
        assertIs<DeleteOutcome.BlockedByHistory>(repo.deleteGroup(chest.id))
        assertEquals(2, repo.observeGroups().first().size)
        assertNotNull(repo.getMuscle(upper.id))
    }

    @Test
    fun whatTheUserMadeIsLeftAlone() = runTest(dispatcher) {
        val repo = muscles(db())
        // A catalogue built by hand before the app shipped one, including a group
        // named exactly like a seeded one.
        val mine = repo.createGroup("Chest", 0L)
        val myLeaf = repo.createMuscle(mine, "Upper chest", 0L)

        repo.syncSeed(english(), now = 1L)

        // Added alongside: matching by name would have merged two things the user
        // never said were the same, and there is no undo for that.
        val groups = repo.observeGroups().first()
        assertEquals(3, groups.size)
        assertEquals(2, groups.count { it.name == "Chest" })
        assertEquals(1, groups.count { it.id == mine })

        // Theirs still has no key, so no sync will ever touch it.
        assertNull(groups.first { it.id == mine }.seedKey)
        assertNull(repo.getMuscle(myLeaf)!!.seedKey)

        repo.syncSeed(spanish(), now = 2L)
        assertEquals("Chest", repo.observeGroups().first().first { it.id == mine }.name)
        assertEquals("Upper chest", repo.getMuscle(myLeaf)!!.name)
    }

    @Test
    fun aGroupAddedInALaterVersionArrivesWithoutAMigration() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)

        val extended = english() + SeededGroup(
            key = "legs",
            name = "Legs",
            leaves = listOf(SeededMuscle("legs.general", "General")),
        )
        val result = repo.syncSeed(extended, now = 1L)

        // The ledger is keyed by seed key, not by schema version, so growing the
        // catalogue is an edit to one Kotlin file.
        assertEquals(1, result.groupsAdded)
        assertEquals(1, result.musclesAdded)
        assertEquals(listOf("Back", "Chest", "Legs"), repo.observeGroups().first().map { it.name })
    }

    @Test
    fun aLeafAddedToAnExistingGroupJoinsIt() = runTest(dispatcher) {
        val repo = muscles(db())
        repo.syncSeed(english(), now = 0L)

        val extended = english().map { group ->
            if (group.key == "chest") {
                group.copy(leaves = group.leaves + SeededMuscle("chest.lower", "Lower chest"))
            } else {
                group
            }
        }
        repo.syncSeed(extended, now = 1L)

        val chest = repo.observeGroups().first().first { it.name == "Chest" }
        assertEquals(
            listOf("General", "Lower chest", "Upper chest"),
            repo.observeMusclesInGroup(chest.id).first().map { it.name },
        )
    }

    @Test
    fun seededMusclesAreLoggableLikeAnyOther() = runTest(dispatcher) {
        val db = db()
        val repo = muscles(db)
        repo.syncSeed(english(), now = 0L)

        // The picker is what the log screen actually reads, and it joins on the group
        // — a seeded leaf whose group did not land would simply be missing here.
        val picker = repo.observePicker().first()
        assertEquals(3, picker.size)
        assertEquals(setOf("Back", "Chest"), picker.map { it.groupName }.toSet())
        assertTrue(picker.all { it.usageCount == 0L })
    }

    @Test
    fun theUseCaseStampsTheClockAndPassesTheSourceThrough() = runTest(dispatcher) {
        val repo = muscles(db())
        val source = MuscleSeedSource { english() }

        val result = SyncMuscleSeedUseCase(repo, source, FixedClock(millis = 42L))()

        assertEquals(2, result.groupsAdded)
        assertEquals(42L, repo.observeGroups().first().first().createdAt)
    }
}
