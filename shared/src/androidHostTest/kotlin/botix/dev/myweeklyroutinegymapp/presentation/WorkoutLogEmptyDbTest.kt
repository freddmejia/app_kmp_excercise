package botix.dev.myweeklyroutinegymapp.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.SystemClock
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
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityMuscleRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Log screen on a database with nothing in it — a first launch.
 *
 * Its state is a combine, and `combine` emits nothing at all until *every* source has
 * emitted once. Each is a `mapToList` that yields an empty list, so an empty database
 * does settle; add a source that stays silent when it has no rows and the screen would
 * hold its spinner forever, with no failure to point at. (The session half of this
 * screen moved to `WorkoutEntryViewModel`, so there are fewer sources than there were
 * — the trap is the same one either way.)
 *
 * The repositories are rebuilt on the test dispatcher rather than taken from
 * `repositoryModule`, which would leave them on the real IO dispatcher — emissions
 * would then arrive on their own threads while `runTest` raced ahead through virtual
 * time, and the result would depend on scheduling. [KoinGraphTest] covers the real
 * wiring; this covers what the flows do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogEmptyDbTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun emptyDatabase(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    private fun viewModel(): WorkoutLogViewModel {
        val db = emptyDatabase()
        return koinApplication {
            modules(
                module {
                    single<Clock> { SystemClock() }
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
        }.koin.get()
    }

    @Test
    fun stateLeavesLoadingOnACompletelyEmptyDatabase() = runTest(dispatcher) {
        val settled = withTimeout(5_000) { viewModel().state.first { !it.isLoading } }

        assertFalse(settled.isLoading)
        // Nothing to show, but the day is still resolved — the header and the strip
        // render from these whether or not anything has ever been logged.
        assertTrue(settled.dayLog.isEmpty())
        assertTrue(settled.strengthActivities.isEmpty())
        assertEquals(settled.today, settled.selectedDay)
    }
}
