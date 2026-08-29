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
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryForm
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryMessage
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The screen that writes one log.
 *
 * It is keyed by (activity, day) rather than by a session id, and it is two forms in
 * one: the muscle picker writes as it goes, the distance form writes once and closes.
 * These pin what that costs — that opening a strength log twice resumes rather than
 * duplicating, that opening a distance log writes nothing at all until Save, and that
 * a second visit is not handed the first one's leftovers.
 *
 * Built through the real Koin graph, so the parameter wiring is exercised too: the
 * ViewModel takes two `Long`s in a fixed order, and resolving them by type instead of
 * by position would date every log to its own activity id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEntryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Tue 25 Aug 2026. */
    private val today = 20_690L

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FixedClock(private val day: Long) : Clock {
        override fun today(): Long = day
        override fun nowMillis(): Long = 0L
    }

    private fun database(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    /**
     * The real graph on an in-memory database.
     *
     * The repositories are rebuilt on the test dispatcher rather than taken from
     * `repositoryModule`, which would leave them on the IO dispatcher — emissions
     * would then arrive on their own threads while `runTest` raced ahead through
     * virtual time.
     */
    private fun koin(db: GymDatabase): Koin = koinApplication {
        modules(
            module {
                single<Clock> { FixedClock(today) }
                single<ActivityCategoryRepository> { ActivityCategoryRepositoryImpl(db, dispatcher) }
                single<BodyWeightRepository> { BodyWeightRepositoryImpl(db, dispatcher) }
                single<MuscleCatalogRepository> { MuscleCatalogRepositoryImpl(db, dispatcher) }
                single<WorkoutRepository> { WorkoutRepositoryImpl(db, dispatcher) }
            },
            domainModule,
            viewModelModule,
        )
    }.koin

    /**
     * Built, collected, and opened — the three things arriving on the screen does.
     *
     * The collection is not incidental: this state is a `stateIn` with
     * `WhileSubscribed`, so with nobody collecting it the upstream never runs and
     * every read comes back as the initial value. A test that skips it is testing a
     * screen nobody is looking at.
     */
    private fun TestScope.viewModel(
        koin: Koin,
        sessionId: Long,
        day: Long = today,
    ): WorkoutEntryViewModel {
        val viewModel = koin.get<WorkoutEntryViewModel> { parametersOf(sessionId, day) }
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
        viewModel.onOpened()
        return viewModel
    }

    private suspend fun category(db: GymDatabase, name: String, type: MeasurementType): Long =
        ActivityCategoryRepositoryImpl(db, dispatcher).create(name, type, createdAt = 0L)

    /**
     * A log of that kind, on that day, ready to open.
     *
     * The screen no longer creates its own session — the New-log flow does, before the
     * route is pushed — so every test here starts from a row that already exists.
     */
    private suspend fun startedLog(
        db: GymDatabase,
        categoryId: Long,
        day: Long = today,
    ): Long = WorkoutRepositoryImpl(db, dispatcher).startSession(categoryId, day, createdAt = 0L)

    private suspend fun muscle(db: GymDatabase, group: String, name: String): Long {
        val muscles = MuscleCatalogRepositoryImpl(db, dispatcher)
        val groupId = muscles.createGroup(group, createdAt = 0L)
        return muscles.createMuscle(groupId, name, createdAt = 0L)
    }

    private fun dayLog(db: GymDatabase) = ObserveDayLogUseCase(WorkoutRepositoryImpl(db, dispatcher))

    /** Collects the save signal the way the screen does — while the screen is open. */
    private fun TestScope.savedSignals(viewModel: WorkoutEntryViewModel): List<Unit> {
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { viewModel.saved.collect { signals += it } }
        return signals
    }

    @Test
    fun aStrengthLogOpensTheMusclePicker() = runTest(dispatcher) {
        val db = database()
        val gym = category(db, "Gym", MeasurementType.STRENGTH)
        muscle(db, "chest", "pecs")
        val session = startedLog(db, gym)

        val state = viewModel(koin(db), session).state.value

        assertEquals(WorkoutEntryForm.STRENGTH, state.form)
        // The row already existed when the screen opened — New log made it. This
        // screen creates nothing, which is why opening one by mistake leaves nothing
        // behind that opening it did not already have.
        assertEquals("Gym", state.categoryName)
        assertEquals(1, dayLog(db)(today).first().size)
    }

    @Test
    fun openingTheSameLogTwiceIsTheSameRow() = runTest(dispatcher) {
        val db = database()
        val gym = category(db, "Gym", MeasurementType.STRENGTH)
        val session = startedLog(db, gym)
        val graph = koin(db)

        val first = viewModel(graph, session).state.value.session
        val second = viewModel(graph, session).state.value.session

        // Identified by session id now, so "the same log" is a fact about the argument
        // rather than something resolved from (activity, day) on each visit.
        assertEquals(first?.sessionId, second?.sessionId)
        assertEquals(1, dayLog(db)(today).first().size)
    }

    @Test
    fun twoLogsOfTheSameKindOnOneDayAreSeparate() = runTest(dispatcher) {
        val db = database()
        val gym = category(db, "Gym", MeasurementType.STRENGTH)
        val muscleId = muscle(db, "chest", "pecs")
        val morning = startedLog(db, gym)
        val evening = startedLog(db, gym)
        val graph = koin(db)

        viewModel(graph, morning).addSets(muscleId, effectiveSets = 3)
        val eveningModel = viewModel(graph, evening)

        // The whole reason the screen is keyed by session: sets written into one log
        // must not appear in the other, even though both are Gym on the same day.
        assertTrue(morning != evening)
        assertEquals(0L, eveningModel.state.value.totals.effectiveSets)
        assertEquals(3L, viewModel(graph, morning).state.value.totals.effectiveSets)
        assertEquals(2, dayLog(db)(today).first().size)
    }

    @Test
    fun checkingAMuscleWritesASetAndUncheckingTakesItBack() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Gym", MeasurementType.STRENGTH))
        val muscleId = muscle(db, "chest", "pecs")

        val viewModel = viewModel(koin(db), id)
        viewModel.addSets(muscleId, effectiveSets = 1)
        assertEquals(1L, viewModel.state.value.totals.effectiveSets)

        viewModel.setSets(muscleId, effectiveSets = 4, warmupSets = 2)
        assertEquals(4L, viewModel.state.value.totals.effectiveSets)
        // Warmup is tracked but stays out of volume.
        assertEquals(2L, viewModel.state.value.totals.warmupSets)

        viewModel.uncheck(muscleId)
        assertEquals(0L, viewModel.state.value.totals.effectiveSets)
        assertEquals(WorkoutEntryMessage.Removed, viewModel.state.value.message)
    }

    @Test
    fun aDistanceActivityOpensTheOtherFormAndWritesNothingUntilItIsSaved() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val state = viewModel(koin(db), id).state.value

        assertEquals(WorkoutEntryForm.DISTANCE_TIME, state.form)
        // The row exists — New log made it — but it holds no measurements yet. Both
        // forms start from a row now; what differs is that a distance log has nothing
        // recorded in it until Save, where a strength log is written a tick at a time.
        assertEquals("Bicycle", state.categoryName)
        val session = dayLog(db)(today).first().single()
        assertNull(session.distanceMeters)
        assertNull(session.durationSeconds)
    }

    @Test
    fun savingADistanceLogStoresMetresAndSecondsAndClosesTheScreen() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val viewModel = viewModel(koin(db), id)
        val saved = savedSignals(viewModel)
        viewModel.setDistance("20.5")
        viewModel.setDuration("45")
        viewModel.save()

        assertEquals(1, saved.size)
        val session = dayLog(db)(today).first().single()
        assertEquals(20_500L, session.distanceMeters)
        assertEquals(2_700L, session.durationSeconds)
    }

    @Test
    fun aCommaIsADecimalPoint() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val viewModel = viewModel(koin(db), id)
        viewModel.setDistance("20,5")
        viewModel.save()

        assertEquals(20_500L, dayLog(db)(today).first().single().distanceMeters)
    }

    @Test
    fun anEmptyDistanceFormIsRefusedAndNothingIsWritten() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val viewModel = viewModel(koin(db), id)
        val saved = savedSignals(viewModel)
        assertTrue(!viewModel.state.value.canSave)

        viewModel.save()

        assertEquals(WorkoutEntryMessage.NothingMeasured, viewModel.state.value.message)
        assertTrue(saved.isEmpty())
        assertNull(dayLog(db)(today).first().single().distanceMeters)
    }

    @Test
    fun eitherMeasureOnItsOwnIsARealRecord() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val viewModel = viewModel(koin(db), id)
        // A swim nobody timed.
        viewModel.setDistance("1.2")
        viewModel.save()

        val session = dayLog(db)(today).first().single()
        assertEquals(1_200L, session.distanceMeters)
        assertNull(session.durationSeconds)
    }

    @Test
    fun reopeningADistanceLogShowsItInTheUnitsItWasTypedIn() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))
        val graph = koin(db)

        viewModel(graph, id).apply {
            setDistance("20.5")
            setDuration("45")
            save()
        }

        val second = viewModel(graph, id)

        // Metres and seconds back out to km and minutes: 20500 has to read as "20.5",
        // and 45 minutes as "45" rather than "45.0".
        assertEquals("20.5", second.state.value.distanceInput)
        assertEquals("45", second.state.value.durationInput)
    }

    @Test
    fun reopeningALogThatWasSavedDoesNotCloseTheScreenOnArrival() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))
        val graph = koin(db)

        // The same instance the store hands back: this ViewModel is keyed by
        // (activity, day) and outlives the visit that created it.
        val viewModel = viewModel(graph, id)
        viewModel.setDistance("20.5")
        viewModel.save()

        val saved = savedSignals(viewModel)
        viewModel.onOpened()

        // Nothing from the first visit may reach the second: a retained "this was
        // saved" flag would close the screen the instant it opened.
        assertTrue(saved.isEmpty())
    }

    @Test
    fun aSecondVisitStartsFromTheDatabaseRatherThanAnAbandonedDraft() = runTest(dispatcher) {
        val db = database()
        val id = startedLog(db, category(db, "Bicycle", MeasurementType.DISTANCE_TIME))

        val viewModel = viewModel(koin(db), id)
        // Typed, then left without saving.
        viewModel.setDistance("99")
        viewModel.onOpened()

        assertEquals("", viewModel.state.value.distanceInput)
        // The row is still there — it was created before the screen opened — and it
        // still holds nothing, because the draft was never saved.
        assertNull(dayLog(db)(today).first().single().distanceMeters)
    }
}
