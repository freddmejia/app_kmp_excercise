package botix.dev.myweeklyroutinegymapp.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityMuscleRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.MusclePickerView
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleSectionsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.includingChecked
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule that makes the activity -> muscle link safe to leave empty.
 *
 * "No links" has to mean *not specified*, so the log picker falls back to the whole
 * catalogue. Getting this backwards would make an activity with no links impossible
 * to log against — the exact opposite of what an optional field should be able to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityMusclePickerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun db(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun sectionsUseCase(db: GymDatabase) = ObserveActivityMuscleSectionsUseCase(
        links = ActivityMuscleRepositoryImpl(db, dispatcher),
        catalog = MuscleCatalogRepositoryImpl(db, dispatcher),
    )

    /** A strength activity, Chest with two leaves, and Legs with one. */
    private suspend fun seed(db: GymDatabase): Triple<Long, Long, Long> {
        val activities = ActivityRepositoryImpl(db, dispatcher)
        val muscles = MuscleCatalogRepositoryImpl(db, dispatcher)
        val activityId = activities.create("Gym", MeasurementType.STRENGTH, createdAt = 0L)
        val chest = muscles.createGroup("Chest", 0L)
        val upper = muscles.createMuscle(chest, "Upper chest", 0L)
        muscles.createMuscle(chest, "Lower chest", 0L)
        val legs = muscles.createGroup("Legs", 0L)
        val quads = muscles.createMuscle(legs, "Quads", 0L)
        return Triple(activityId, upper, quads)
    }

    @Test
    fun withNoLinksThePickerFallsBackToTheWholeCatalogue() = runTest(dispatcher) {
        val database = db()
        val (activityId, _, _) = seed(database)

        val view = sectionsUseCase(database)(activityId).first()

        assertFalse(view.isScopedToActivity)
        assertEquals(listOf("Chest", "Legs"), view.sections.map { it.groupName })
        assertEquals(3, view.sections.sumOf { it.muscles.size })
    }

    @Test
    fun withLinksThePickerNarrowsToThemAndSaysSo() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)
        ActivityMuscleRepositoryImpl(database, dispatcher).setMusclesFor(activityId, setOf(upper))

        val view = sectionsUseCase(database)(activityId).first()

        // isScopedToActivity is the flag the screen renders its banner from: a
        // narrowed list and a short catalogue look identical without it.
        assertTrue(view.isScopedToActivity)
        assertEquals(listOf("Chest"), view.sections.map { it.groupName })
        assertEquals(listOf("Upper chest"), view.sections.single().muscles.map { it.muscleName })
    }

    @Test
    fun linksSpanningGroupsKeepTheirSections() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, quads) = seed(database)
        ActivityMuscleRepositoryImpl(database, dispatcher).setMusclesFor(activityId, setOf(upper, quads))

        val view = sectionsUseCase(database)(activityId).first()

        assertTrue(view.isScopedToActivity)
        assertEquals(listOf("Chest", "Legs"), view.sections.map { it.groupName })
    }

    @Test
    fun archivingEveryLinkedMuscleFallsBackRatherThanShowingNothing() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)
        ActivityMuscleRepositoryImpl(database, dispatcher).setMusclesFor(activityId, setOf(upper))

        MuscleCatalogRepositoryImpl(database, dispatcher).setMuscleArchived(upper, true)

        // The scoped query filters archived leaves out, so without the fallback the
        // picker would go empty and the screen would look broken rather than degraded.
        val view = sectionsUseCase(database)(activityId).first()
        assertFalse(view.isScopedToActivity)
        assertEquals(2, view.sections.sumOf { it.muscles.size })
    }

    private fun pickerItem(id: Long, name: String, groupId: Long, groupName: String) =
        MusclePickerItem(id, name, groupId, groupName, usageCount = 0)

    private fun entry(muscleId: Long, name: String, groupId: Long, groupName: String) =
        SessionEntry(
            id = muscleId,
            sessionId = 1L,
            muscleId = muscleId,
            muscleName = name,
            groupId = groupId,
            groupName = groupName,
            effectiveSets = 2,
        )

    @Test
    fun aCheckedMuscleOutsideTheScopeIsPutBackIntoThePicker() {
        val scoped = MusclePickerView(
            sections = listOf(
                MuscleSection(1L, "Chest", listOf(pickerItem(1L, "Upper chest", 1L, "Chest"))),
            ),
            isScopedToActivity = true,
        )

        // Logged during the session from the widened list, then the picker narrowed
        // again. Its sets are already in the totals, so the row has to come back —
        // otherwise there is volume on screen that nothing accounts for and no way to
        // uncheck it.
        val merged = scoped.includingChecked(listOf(entry(9L, "Biceps", 2L, "Arms")))

        assertEquals(listOf("Arms", "Chest"), merged.sections.map { it.groupName })
        assertEquals(listOf("Biceps"), merged.sections.first { it.groupName == "Arms" }.muscles.map { it.muscleName })
        // Still flagged as scoped: one re-added row does not mean the user asked for
        // the whole catalogue, and the banner must keep saying what it is showing.
        assertTrue(merged.isScopedToActivity)
    }

    @Test
    fun aCheckedMuscleAlreadyInScopeIsNotDuplicated() {
        val scoped = MusclePickerView(
            sections = listOf(
                MuscleSection(1L, "Chest", listOf(pickerItem(1L, "Upper chest", 1L, "Chest"))),
            ),
            isScopedToActivity = true,
        )

        val merged = scoped.includingChecked(listOf(entry(1L, "Upper chest", 1L, "Chest")))

        assertEquals(1, merged.sections.single().muscles.size)
    }

    @Test
    fun theUnscopedPickerIsLeftAloneBecauseItAlreadyShowsEverything() {
        val all = MusclePickerView(
            sections = listOf(
                MuscleSection(1L, "Chest", listOf(pickerItem(1L, "Upper chest", 1L, "Chest"))),
            ),
            isScopedToActivity = false,
        )

        assertEquals(all, all.includingChecked(listOf(entry(9L, "Biceps", 2L, "Arms"))))
    }

    @Test
    fun thePickerReEmitsWhenTheLinkChanges() = runTest(dispatcher) {
        val database = db()
        val (activityId, upper, _) = seed(database)
        val links = ActivityMuscleRepositoryImpl(database, dispatcher)
        assertFalse(sectionsUseCase(database)(activityId).first().isScopedToActivity)

        links.setMusclesFor(activityId, setOf(upper))

        // Editing the activity's muscles has to reach the open log screen with no
        // manual refresh — that is the whole point of the link being observable.
        assertTrue(sectionsUseCase(database)(activityId).first().isScopedToActivity)
    }
}
