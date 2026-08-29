package botix.dev.myweeklyroutinegymapp.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityMuscleRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.di.domainModule
import botix.dev.myweeklyroutinegymapp.di.viewModelModule
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityMuscleRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityEditorError
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The activity form, which is now one screen rather than three dialogs.
 *
 * The parts worth pinning are the ones that used to be spread across those dialogs and
 * could disagree: what a save actually writes, that muscles follow the *type* rather
 * than what happens to be ticked, and that a type the domain refuses to change leaves
 * the activity exactly as it was rather than half-updated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityEditorTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FixedClock : Clock {
        override fun today(): Long = 20_690L
        override fun nowMillis(): Long = 0L
    }

    private fun database(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun koin(db: GymDatabase): Koin = koinApplication {
        modules(
            module {
                single<Clock> { FixedClock() }
                single<ActivityRepository> { ActivityRepositoryImpl(db, dispatcher) }
                single<ActivityCategoryRepository> { ActivityCategoryRepositoryImpl(db, dispatcher) }
                single<ActivityMuscleRepository> { ActivityMuscleRepositoryImpl(db, dispatcher) }
                single<BodyWeightRepository> { BodyWeightRepositoryImpl(db, dispatcher) }
                single<MuscleCatalogRepository> { MuscleCatalogRepositoryImpl(db, dispatcher) }
                single<WorkoutRepository> { WorkoutRepositoryImpl(db, dispatcher) }
            },
            domainModule,
            viewModelModule,
        )
    }.koin

    private fun activities(db: GymDatabase) = ActivityRepositoryImpl(db, dispatcher)

    private fun links(db: GymDatabase) = ActivityMuscleRepositoryImpl(db, dispatcher)

    private fun muscles(db: GymDatabase) = MuscleCatalogRepositoryImpl(db, dispatcher)

    /**
     * Built, collected, and opened — the three things arriving on the screen does.
     *
     * The collection matters: this state is a `stateIn` with `WhileSubscribed`, so
     * with nobody collecting it the upstream never runs and every read comes back as
     * the initial value.
     */
    private fun TestScope.editor(koin: Koin, activityId: Long): ActivityEditorViewModel {
        val viewModel = koin.get<ActivityEditorViewModel> { parametersOf(activityId) }
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
        viewModel.onOpened()
        return viewModel
    }

    private fun TestScope.savedSignals(viewModel: ActivityEditorViewModel): List<Unit> {
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { viewModel.saved.collect { signals += it } }
        return signals
    }

    @Test
    fun aNewActivityOpensOnAnEmptyStrengthForm() = runTest(dispatcher) {
        val db = database()
        val state = editor(koin(db), ActivityEditorViewModel.NEW).state.value

        assertEquals("", state.name)
        // Strength is the default because it is the type with a picker behind it; the
        // other two are a form each and neither is the common case.
        assertEquals(MeasurementType.STRENGTH, state.type)
        assertTrue(state.showsMuscles)
        assertFalse(state.canSave)
    }

    @Test
    fun savingANewActivityWritesItWithItsCategoryAndMuscles() = runTest(dispatcher) {
        val db = database()
        val categoryId = ActivityCategoryRepositoryImpl(db, dispatcher).create("gym", createdAt = 0L)
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        val muscleId = muscles(db).createMuscle(groupId, "pecs", createdAt = 0L)

        val viewModel = editor(koin(db), ActivityEditorViewModel.NEW)
        val saved = savedSignals(viewModel)
        viewModel.setName("bench press")
        viewModel.setIcon("🏋")
        viewModel.setCategory(categoryId)
        viewModel.toggleMuscle(muscleId)
        viewModel.save()

        assertEquals(1, saved.size)
        val stored = activities(db).observeActive().first().single()
        assertEquals("bench press", stored.name)
        assertEquals("🏋", stored.icon)
        assertEquals(categoryId, stored.categoryId)
        assertEquals(setOf(muscleId), links(db).observeAllLinks().first()[stored.id])
    }

    @Test
    fun anExistingActivityOpensOnEverythingItHolds() = runTest(dispatcher) {
        val db = database()
        val categoryId = ActivityCategoryRepositoryImpl(db, dispatcher).create("gym", createdAt = 0L)
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        val muscleId = muscles(db).createMuscle(groupId, "pecs", createdAt = 0L)
        val id = activities(db).create(
            name = "bench press",
            measurementType = MeasurementType.STRENGTH,
            icon = "🏋",
            categoryId = categoryId,
            createdAt = 0L,
        )
        links(db).setMusclesFor(id, setOf(muscleId))

        val state = editor(koin(db), id).state.value

        // All four fields at once. Two of them used to live in dialogs of their own,
        // each showing one field with the rest out of sight.
        assertEquals("bench press", state.name)
        assertEquals("🏋", state.icon)
        assertEquals(categoryId, state.categoryId)
        assertEquals(setOf(muscleId), state.muscleIds)
    }

    @Test
    fun editingRenamesInPlaceRatherThanAddingASecondActivity() = runTest(dispatcher) {
        val db = database()
        val id = activities(db).create("press", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = editor(koin(db), id)
        viewModel.setName("bench press")
        viewModel.save()

        val stored = activities(db).observeActive().first()
        assertEquals(1, stored.size)
        // Sessions reference the id, so a rename is invisible to history — which is
        // exactly why renaming is unrestricted and deleting is not.
        assertEquals(id, stored.single().id)
        assertEquals("bench press", stored.single().name)
    }

    @Test
    fun aNameAnotherActivityAlreadyHoldsIsRefused() = runTest(dispatcher) {
        val db = database()
        activities(db).create("press", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = editor(koin(db), ActivityEditorViewModel.NEW)
        val saved = savedSignals(viewModel)
        viewModel.setName("press")
        viewModel.save()

        assertEquals(ActivityEditorError.NameTaken, viewModel.state.value.error)
        assertTrue(saved.isEmpty())
        assertEquals(1, activities(db).observeActive().first().size)
    }

    @Test
    fun musclesFollowTheTypeRatherThanWhatIsTicked() = runTest(dispatcher) {
        val db = database()
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        val muscleId = muscles(db).createMuscle(groupId, "pecs", createdAt = 0L)

        val viewModel = editor(koin(db), ActivityEditorViewModel.NEW)
        viewModel.setName("bici")
        viewModel.toggleMuscle(muscleId)
        // Switching type keeps the ticks — flipping back and forth must not silently
        // undo a selection — but a distance activity has no muscle links to save.
        viewModel.setType(MeasurementType.DISTANCE_TIME)
        assertEquals(setOf(muscleId), viewModel.state.value.muscleIds)
        assertFalse(viewModel.state.value.showsMuscles)

        viewModel.save()

        val stored = activities(db).observeActive().first().single()
        assertEquals(MeasurementType.DISTANCE_TIME, stored.measurementType)
        assertNull(links(db).observeAllLinks().first()[stored.id])
    }

    @Test
    fun theTypeCanStillChangeWhileNothingHasBeenLoggedAgainstIt() = runTest(dispatcher) {
        val db = database()
        val id = activities(db).create("bici", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = editor(koin(db), id)
        viewModel.setType(MeasurementType.DISTANCE_TIME)
        viewModel.save()

        assertNull(viewModel.state.value.error)
        assertEquals(
            MeasurementType.DISTANCE_TIME,
            activities(db).observeActive().first().single().measurementType,
        )
    }

    @Test
    fun aTypeTheDomainRefusesLeavesTheActivityUntouched() = runTest(dispatcher) {
        val db = database()
        val id = activities(db).create("press", MeasurementType.STRENGTH, createdAt = 0L)
        // One session under it is enough: the type is the shape its history was
        // recorded in.
        WorkoutRepositoryImpl(db, dispatcher).startOrResumeSession(id, epochDay = 20_690L, createdAt = 0L)

        val viewModel = editor(koin(db), id)
        val saved = savedSignals(viewModel)
        viewModel.setName("bench press")
        viewModel.setType(MeasurementType.DISTANCE_TIME)
        viewModel.save()

        assertEquals(ActivityEditorError.TypeLocked(1L), viewModel.state.value.error)
        assertTrue(saved.isEmpty())
        // The rename went nowhere either: the type is attempted first precisely so a
        // refusal cannot leave half the form applied.
        val stored = activities(db).observeActive().first().single()
        assertEquals("press", stored.name)
        assertEquals(MeasurementType.STRENGTH, stored.measurementType)
    }

    @Test
    fun clearingTheMusclesIsAMeaningfulSave() = runTest(dispatcher) {
        val db = database()
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        val muscleId = muscles(db).createMuscle(groupId, "pecs", createdAt = 0L)
        val id = activities(db).create("press", MeasurementType.STRENGTH, createdAt = 0L)
        links(db).setMusclesFor(id, setOf(muscleId))

        val viewModel = editor(koin(db), id)
        viewModel.toggleMuscle(muscleId)
        viewModel.save()

        // An empty set is not "no change": it returns the activity to no opinion, and
        // the log picker falls back to the whole catalogue.
        assertNull(links(db).observeAllLinks().first()[id])
    }

    @Test
    fun reopeningAnActivityThatWasSavedDoesNotCloseTheScreenOnArrival() = runTest(dispatcher) {
        val db = database()
        val graph = koin(db)
        val viewModel = editor(graph, ActivityEditorViewModel.NEW)
        viewModel.setName("bench press")
        viewModel.save()

        val saved = savedSignals(viewModel)
        viewModel.onOpened()

        assertTrue(saved.isEmpty())
        // And a new-activity form comes back empty, not holding the last one it wrote.
        assertEquals("", viewModel.state.value.name)
    }
}
