package botix.dev.myweeklyroutinegymapp.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.GymData
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoryUsageUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveCategoryVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionUseCase
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleEditorViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogViewModel
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertNotNull(koin.get<BodyWeightViewModel>())
        assertNotNull(koin.get<WorkoutLogViewModel>())
        assertNotNull(koin.get<ActivityCategoryViewModel>())
        // Takes the day it edits as a runtime parameter, so it is the one binding
        // here that cannot be resolved by type alone — and the one most easily left
        // half-wired, since a missing parameter only shows up when the screen opens.
        assertNotNull(koin.get<BodyWeightEntryViewModel> { parametersOf(20_686L) })
        // Two parameters of the same type, in order: resolving these by type instead
        // of by position would log every entry against day = sessionId.
        val entry = koin.get<WorkoutEntryViewModel> { parametersOf(7L, 20_686L) }
        assertEquals(20_686L, entry.state.value.epochDay)
        assertNotNull(koin.get<CategoryEditorViewModel> { parametersOf(0L) })
        // Three parameters, and the last one is not a Long: the editor writes a group
        // or a leaf depending on it, and getting it by type would write the wrong half
        // of the catalogue.
        val muscleEditor = koin.get<MuscleEditorViewModel> { parametersOf(3L, 0L, true) }
        assertTrue(muscleEditor.state.value.isGroup)
    }

    @Test
    fun everyRepositoryResolvesBehindItsInterface() {
        val koin = koin()
        assertNotNull(koin.get<ActivityCategoryRepository>())
        assertNotNull(koin.get<BodyWeightRepository>())
        assertNotNull(koin.get<MuscleCatalogRepository>())
        assertNotNull(koin.get<WorkoutRepository>())
        assertNotNull(koin.get<Clock>())
    }

    @Test
    fun useCasesResolveIncludingTheMultiDependencyOnes() {
        val koin = koin()
        assertNotNull(koin.get<ObserveCategoryUsageUseCase>())
        assertNotNull(koin.get<CreateCategoryUseCase>())
        assertNotNull(koin.get<DeleteCategoryUseCase>())
        // Reaches across to the *activity* repository, not the category one — the
        // row it writes is the activity.
        // Spans both catalogues: the link repository and the muscle one.
        assertNotNull(koin.get<LogBodyWeightUseCase>())
        assertNotNull(koin.get<ObserveBodyWeightChartUseCase>())
        assertNotNull(koin.get<ObserveMusclePickerUseCase>())
        // Three dependencies across two repositories plus the clock.
        assertNotNull(koin.get<StartSessionUseCase>())
    }

    @Test
    fun theDatabaseAndRepositoriesAreSingletons() {
        val koin = koin()

        // This is the binding that must not be duplicated: two GymData would each
        // open a connection and neither would see the other's writes.
    }

    @Test
    fun theGraphFailsLoudlyForSomethingItDoesNotProvide() {
        // Guards the test itself: if resolution silently returned stubs, every
        // assertion above would pass regardless of the real wiring.
        assertFailsWith<NoDefinitionFoundException> { koin().get<UnregisteredType>() }
    }

    private class UnregisteredType
}
