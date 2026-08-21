package botix.dev.myweeklyroutinegymapp.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.GymData
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityMuscleRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.AssignActivityCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoryUsageUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleLinksUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleSectionsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetActivityMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartOrResumeSessionUseCase
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityListViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogViewModel
import org.koin.core.context.stopKoin
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Checks that the dependency graph actually resolves.
 *
 * A missing Koin binding is a runtime crash, not a compile error, so without a test
 * like this the first sign of a broken graph is the app dying on launch. Everything
 * below the platform layer is exercised for real — only [databaseModule] is replaced,
 * because it is the one piece that needs an Android `Context`.
 */
class KoinGraphTest {

    private fun testDatabase(): GymDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return createDatabase(driver)
    }

    /** Stands in for [databaseModule], which would need a Context on Android. */
    private fun inMemoryDatabaseModule() = module {
        single { GymData(testDatabase()) }
    }

    private fun koin() = koinApplication {
        modules(inMemoryDatabaseModule(), repositoryModule, domainModule, viewModelModule)
    }.koin

    @AfterTest
    fun tearDown() {
        // koinApplication does not register globally, but stop defensively so an
        // earlier failure cannot leak into the next test.
        runCatching { stopKoin() }
    }

    @Test
    fun everyViewModelResolves() {
        val koin = koin()
        assertNotNull(koin.get<ActivityListViewModel>())
        assertNotNull(koin.get<BodyWeightViewModel>())
        assertNotNull(koin.get<WorkoutLogViewModel>())
        assertNotNull(koin.get<ActivityCategoryViewModel>())
    }

    @Test
    fun everyRepositoryResolvesBehindItsInterface() {
        val koin = koin()
        assertNotNull(koin.get<ActivityRepository>())
        assertNotNull(koin.get<ActivityCategoryRepository>())
        assertNotNull(koin.get<ActivityMuscleRepository>())
        assertNotNull(koin.get<BodyWeightRepository>())
        assertNotNull(koin.get<MuscleCatalogRepository>())
        assertNotNull(koin.get<WorkoutRepository>())
        assertNotNull(koin.get<Clock>())
    }

    @Test
    fun useCasesResolveIncludingTheMultiDependencyOnes() {
        val koin = koin()
        assertNotNull(koin.get<ObserveActivitiesUseCase>())
        assertNotNull(koin.get<CreateActivityUseCase>())
        assertNotNull(koin.get<DeleteActivityUseCase>())
        assertNotNull(koin.get<ObserveCategoryUsageUseCase>())
        assertNotNull(koin.get<CreateCategoryUseCase>())
        assertNotNull(koin.get<DeleteCategoryUseCase>())
        // Reaches across to the *activity* repository, not the category one — the
        // row it writes is the activity.
        assertNotNull(koin.get<AssignActivityCategoryUseCase>())
        assertNotNull(koin.get<ObserveActivityMuscleLinksUseCase>())
        assertNotNull(koin.get<ObserveActivityVolumeUseCase>())
        assertNotNull(koin.get<SetActivityMusclesUseCase>())
        // Spans both catalogues: the link repository and the muscle one.
        assertNotNull(koin.get<ObserveActivityMuscleSectionsUseCase>())
        assertNotNull(koin.get<LogBodyWeightUseCase>())
        assertNotNull(koin.get<ObserveBodyWeightChartUseCase>())
        assertNotNull(koin.get<ObserveMusclePickerUseCase>())
        // Three dependencies across two repositories plus the clock.
        assertNotNull(koin.get<StartOrResumeSessionUseCase>())
    }

    @Test
    fun theDatabaseAndRepositoriesAreSingletons() {
        val koin = koin()

        // This is the binding that must not be duplicated: two GymData would each
        // open a connection and neither would see the other's writes.
        assertSame(koin.get<GymData>(), koin.get<GymData>())
        assertSame(koin.get<ActivityRepository>(), koin.get<ActivityRepository>())
        assertSame(koin.get<ActivityCategoryRepository>(), koin.get<ActivityCategoryRepository>())
        assertSame(koin.get<ActivityMuscleRepository>(), koin.get<ActivityMuscleRepository>())
        assertSame(koin.get<Clock>(), koin.get<Clock>())
    }

    @Test
    fun useCasesAreFactoriesNotSingletons() {
        val koin = koin()
        // Stateless, so a fresh instance each time is correct and cheap.
        assertTrue(koin.get<ObserveActivitiesUseCase>() !== koin.get<ObserveActivitiesUseCase>())
    }

    @Test
    fun theGraphFailsLoudlyForSomethingItDoesNotProvide() {
        // Guards the test itself: if resolution silently returned stubs, every
        // assertion above would pass regardless of the real wiring.
        assertFailsWith<NoDefinitionFoundException> { koin().get<UnregisteredType>() }
    }

    private class UnregisteredType
}
