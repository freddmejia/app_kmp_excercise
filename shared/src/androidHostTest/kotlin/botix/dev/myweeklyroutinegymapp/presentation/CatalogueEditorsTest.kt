package botix.dev.myweeklyroutinegymapp.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.di.domainModule
import botix.dev.myweeklyroutinegymapp.di.viewModelModule
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorError
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleEditorViewModel
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
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
 * The two catalogue editors that moved out of dialogs and into Settings.
 *
 * Both are keyed by the id they are editing — zero for something that does not exist
 * yet — and both are the same shape as the log and weight editors: reload on every
 * visit, and a save signal with no replay. These pin the parts that are specific to
 * them: which of four things the muscle form writes, and the name rules the category
 * form only reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueEditorsTest {

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
                single<ActivityCategoryRepository> { ActivityCategoryRepositoryImpl(db, dispatcher) }
                single<BodyWeightRepository> { BodyWeightRepositoryImpl(db, dispatcher) }
                single<MuscleCatalogRepository> { MuscleCatalogRepositoryImpl(db, dispatcher) }
                single<WorkoutRepository> { WorkoutRepositoryImpl(db, dispatcher) }
            },
            domainModule,
            viewModelModule,
        )
    }.koin

    private fun categories(db: GymDatabase) = ActivityCategoryRepositoryImpl(db, dispatcher)

    private fun muscles(db: GymDatabase) = MuscleCatalogRepositoryImpl(db, dispatcher)

    private fun categoryEditor(koin: Koin, categoryId: Long): CategoryEditorViewModel =
        koin.get<CategoryEditorViewModel> { parametersOf(categoryId) }.also { it.onOpened() }

    private fun muscleEditor(
        koin: Koin,
        groupId: Long,
        muscleId: Long,
        isGroup: Boolean,
    ): MuscleEditorViewModel =
        koin.get<MuscleEditorViewModel> { parametersOf(groupId, muscleId, isGroup) }
            .also { it.onOpened() }

    private fun TestScope.savedSignals(saved: kotlinx.coroutines.flow.Flow<Unit>): List<Unit> {
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { saved.collect { signals += it } }
        return signals
    }

    // --- Categories ---------------------------------------------------------

    @Test
    fun aNewCategoryOpensOnAnEmptyForm() = runTest(dispatcher) {
        val db = database()
        categories(db).create("cardio", MeasurementType.STRENGTH, icon = "🏃", createdAt = 0L)

        val state = categoryEditor(koin(db), CategoryEditorViewModel.NEW).state.value

        assertEquals("", state.name)
        assertEquals("", state.icon)
        assertFalse(state.canSave)
    }

    @Test
    fun anExistingCategoryOpensOnWhatItHolds() = runTest(dispatcher) {
        val db = database()
        val id = categories(db).create("cardio", MeasurementType.STRENGTH, icon = "🏃", createdAt = 0L)

        val state = categoryEditor(koin(db), id).state.value

        assertEquals("cardio", state.name)
        assertEquals("🏃", state.icon)
        assertTrue(state.canSave)
    }

    @Test
    fun savingANewCategoryWritesItAndClosesTheScreen() = runTest(dispatcher) {
        val db = database()
        val viewModel = categoryEditor(koin(db), CategoryEditorViewModel.NEW)
        val saved = savedSignals(viewModel.saved)
        viewModel.setName("movilidad")
        viewModel.setIcon("🧘")
        viewModel.save()

        assertEquals(1, saved.size)
        val stored = categories(db).observeActive().first().single()
        assertEquals("movilidad", stored.name)
        assertEquals("🧘", stored.icon)
    }

    @Test
    fun aNameAnotherCategoryAlreadyHoldsIsRefused() = runTest(dispatcher) {
        val db = database()
        categories(db).create("cardio", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = categoryEditor(koin(db), CategoryEditorViewModel.NEW)
        val saved = savedSignals(viewModel.saved)
        viewModel.setName("cardio")
        viewModel.save()

        assertEquals(CategoryEditorError.NameTaken, viewModel.state.value.error)
        assertTrue(saved.isEmpty())
        // The rule is the use case's; this only reports it — and nothing was written.
        assertEquals(1, categories(db).observeActive().first().size)
    }

    @Test
    fun keepingItsOwnNameIsNotAClashWithItself() = runTest(dispatcher) {
        val db = database()
        val id = categories(db).create("cardio", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = categoryEditor(koin(db), id)
        viewModel.setIcon("🚴")
        viewModel.save()

        assertNull(viewModel.state.value.error)
        assertEquals("🚴", categories(db).observeActive().first().single().icon)
    }

    @Test
    fun typingAgainClearsTheErrorTheOldNameEarned() = runTest(dispatcher) {
        val db = database()
        categories(db).create("cardio", MeasurementType.STRENGTH, createdAt = 0L)

        val viewModel = categoryEditor(koin(db), CategoryEditorViewModel.NEW)
        viewModel.setName("cardio")
        viewModel.save()
        viewModel.setName("cardio ligero")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun reopeningACategoryThatWasSavedDoesNotCloseTheScreenOnArrival() = runTest(dispatcher) {
        val db = database()
        val graph = koin(db)
        val viewModel = categoryEditor(graph, CategoryEditorViewModel.NEW)
        viewModel.setName("movilidad")
        viewModel.save()

        val saved = savedSignals(viewModel.saved)
        viewModel.onOpened()

        assertTrue(saved.isEmpty())
        // And a new-category form comes back empty, not holding the last one it wrote.
        assertEquals("", viewModel.state.value.name)
    }

    // --- Muscles ------------------------------------------------------------

    @Test
    fun savingTheGroupFormCreatesAGroup() = runTest(dispatcher) {
        val db = database()
        val viewModel = muscleEditor(koin(db), MuscleEditorViewModel.NEW, MuscleEditorViewModel.NEW, isGroup = true)
        val saved = savedSignals(viewModel.saved)
        viewModel.setName("chest")
        viewModel.save()

        assertEquals(1, saved.size)
        assertEquals("chest", muscles(db).observeActiveGroups().first().single().name)
    }

    @Test
    fun theGroupFormOpensOnTheNameItIsRenaming() = runTest(dispatcher) {
        val db = database()
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)

        val viewModel = muscleEditor(koin(db), groupId, MuscleEditorViewModel.NEW, isGroup = true)
        assertEquals("chest", viewModel.state.value.name)

        viewModel.setName("pecho")
        viewModel.save()

        assertEquals("pecho", muscles(db).observeActiveGroups().first().single().name)
    }

    @Test
    fun theLeafFormLandsInTheGroupItWasOpenedFrom() = runTest(dispatcher) {
        val db = database()
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        muscles(db).createGroup("back", createdAt = 0L)

        val viewModel = muscleEditor(koin(db), groupId, MuscleEditorViewModel.NEW, isGroup = false)
        // The form names the group, because the app bar only says "New muscle" and
        // every group looks the same once you are inside the form.
        assertEquals("chest", viewModel.state.value.groupName)
        assertEquals("", viewModel.state.value.name)

        viewModel.setName("upper chest")
        viewModel.save()

        val leaves = muscles(db).observeMusclesInGroup(groupId).first()
        assertEquals(listOf("upper chest"), leaves.map { it.name })
    }

    @Test
    fun theLeafFormOpensOnTheNameItIsRenaming() = runTest(dispatcher) {
        val db = database()
        val groupId = muscles(db).createGroup("chest", createdAt = 0L)
        val muscleId = muscles(db).createMuscle(groupId, "peks", createdAt = 0L)

        val viewModel = muscleEditor(koin(db), groupId, muscleId, isGroup = false)
        assertEquals("peks", viewModel.state.value.name)

        viewModel.setName("pecs")
        viewModel.save()

        assertEquals("pecs", muscles(db).observeMusclesInGroup(groupId).first().single().name)
        // A rename, not a second leaf: entries point at the id, and the id did not move.
        assertEquals(1, muscles(db).observeMusclesInGroup(groupId).first().size)
    }

    @Test
    fun aBlankNameIsRefusedAndNothingIsWritten() = runTest(dispatcher) {
        val db = database()
        val viewModel = muscleEditor(koin(db), MuscleEditorViewModel.NEW, MuscleEditorViewModel.NEW, isGroup = true)
        val saved = savedSignals(viewModel.saved)

        // The button is inert on blank, so this is the whitespace case the use case
        // owns: it trims first, and only then decides there is no name.
        viewModel.setName("   ")
        viewModel.save()

        assertTrue(viewModel.state.value.isNameBlank)
        assertTrue(saved.isEmpty())
        assertTrue(muscles(db).observeActiveGroups().first().isEmpty())
    }
}
