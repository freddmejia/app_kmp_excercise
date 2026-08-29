package botix.dev.myweeklyroutinegymapp.di

import botix.dev.myweeklyroutinegymapp.data.GymData
import botix.dev.myweeklyroutinegymapp.data.ResourceCategorySeed
import botix.dev.myweeklyroutinegymapp.data.ResourceMuscleSeed
import botix.dev.myweeklyroutinegymapp.data.SystemClock
import botix.dev.myweeklyroutinegymapp.db.DriverFactory
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.repository.CategorySeedSource
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleSeedSource
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoryUsageUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.DeleteBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.GetBodyWeightForDayUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightHistoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightWeekUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ArchiveMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleGroupUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveGroupVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveCategoryVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleGroupsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleProgressionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionSummaryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionTotalsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveTrainedDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RenameMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetSessionDistanceTimeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SyncCatalogSeedsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SyncCategorySeedUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SyncMuscleSeedUseCase
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleCatalogViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleEditorViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Supplies the one binding that cannot be written once: [DriverFactory] needs a
 * `Context` on Android and nothing on iOS.
 */
expect val platformModule: Module

/**
 * The database itself, which *must* be a `single`: a second instance would open its
 * own connection and would not receive the first's query notifications, so writes
 * through one would never refresh Flows collected from the other.
 */
val databaseModule = module {
    single { GymData(get<DriverFactory>()) }
}

/**
 * Clock and repositories, resolved through [GymData] rather than constructed again
 * here, so there is exactly one place that knows how to build them.
 *
 * Split from [databaseModule] on purpose: this half has no platform dependency, so a
 * test can swap in an in-memory database and still exercise the real graph.
 */
val repositoryModule = module {
    single<Clock> { SystemClock() }
    // The catalogue the app ships. A `single` because it is a constant list plus a
    // resource lookup, and nothing about it is per-caller.
    single<MuscleSeedSource> { ResourceMuscleSeed() }
    single<CategorySeedSource> { ResourceCategorySeed() }
    single { get<GymData>().activityCategories }
    single { get<GymData>().bodyWeight }
    single { get<GymData>().muscles }
    single { get<GymData>().workouts }
}

/**
 * Use cases, all `factory`.
 *
 * They hold no state — each is a repository reference plus a rule — so scoping them
 * as singletons would buy nothing and only make them harder to reason about.
 */
val domainModule = module {
    // Categories: the kind of training, and the only catalogue logging needs
    factoryOf(::ObserveCategoriesUseCase)
    factoryOf(::ObserveCategoryUsageUseCase)
    factoryOf(::CreateCategoryUseCase)
    factoryOf(::UpdateCategoryUseCase)
    factoryOf(::ArchiveCategoryUseCase)
    factoryOf(::DeleteCategoryUseCase)

    // Body weight
    factoryOf(::ObserveBodyWeightChartUseCase)
    factoryOf(::ObserveBodyWeightHistoryUseCase)
    factoryOf(::ObserveBodyWeightWeekUseCase)
    factoryOf(::LogBodyWeightUseCase)
    factoryOf(::GetBodyWeightForDayUseCase)
    factoryOf(::DeleteBodyWeightUseCase)

    // Workout logging
    factoryOf(::StartSessionUseCase)
    factoryOf(::LogMuscleSetsUseCase)
    factoryOf(::SetSessionDistanceTimeUseCase)
    factoryOf(::SetMuscleSetsUseCase)
    factoryOf(::RemoveMuscleFromSessionUseCase)
    factoryOf(::ObserveSessionDetailUseCase)
    factoryOf(::ObserveSessionSummaryUseCase)
    factoryOf(::ObserveSessionDaysUseCase)
    factoryOf(::ObserveDayLogUseCase)
    factoryOf(::ObserveDayMusclesUseCase)
    factoryOf(::DeleteSessionUseCase)
    factoryOf(::ObserveSessionTotalsUseCase)
    factoryOf(::ObserveMusclePickerUseCase)
    factoryOf(::ObserveGroupVolumeUseCase)
    factoryOf(::ObserveCategoryVolumeUseCase)
    factoryOf(::ObserveMuscleVolumeUseCase)
    factoryOf(::ObserveUntrainedMusclesUseCase)
    factoryOf(::ObserveMuscleProgressionUseCase)
    factoryOf(::ObserveTrainedDaysUseCase)

    // Muscle catalogue
    factoryOf(::SyncMuscleSeedUseCase)
    factoryOf(::SyncCategorySeedUseCase)
    factoryOf(::SyncCatalogSeedsUseCase)
    factoryOf(::ObserveMuscleGroupsUseCase)
    factoryOf(::ObserveMusclesUseCase)
    factoryOf(::CreateMuscleGroupUseCase)
    factoryOf(::CreateMuscleUseCase)
    factoryOf(::RenameMuscleUseCase)
    factoryOf(::ArchiveMuscleUseCase)
    factoryOf(::DeleteMuscleUseCase)
}

val viewModelModule = module {
    viewModelOf(::ActivityCategoryViewModel)
    viewModelOf(::BodyWeightViewModel)
    // The two that take runtime arguments — the day being edited, and the (activity,
    // day) pair being logged. Written long-hand because `viewModelOf` binds every
    // constructor parameter from the graph, and the leading ones here come from the
    // caller instead.
    viewModel { params -> BodyWeightEntryViewModel(params.get(), get(), get(), get()) }
    // Destructured, not two `get()` calls: ParametersHolder.get() resolves by type
    // and would hand back the same first Long twice, quietly logging every entry
    // against day = activityId.
    viewModel { (categoryId: Long) ->
        CategoryEditorViewModel(categoryId, get(), get(), get(), get())
    }
    viewModel { (groupId: Long, muscleId: Long, isGroup: Boolean) ->
        MuscleEditorViewModel(
            groupId = groupId,
            muscleId = muscleId,
            isGroup = isGroup,
            observeGroups = get(),
            observeMuscles = get(),
            createGroup = get(),
            createMuscle = get(),
            rename = get(),
        )
    }
    // (session, day) rather than (activity, day): a day can hold several logs of the
    // same kind now, so only the session id identifies one.
    viewModel { (sessionId: Long, epochDay: Long) ->
        WorkoutEntryViewModel(
            sessionId = sessionId,
            epochDay = epochDay,
            logMuscleSets = get(),
            setMuscleSets = get(),
            removeMuscle = get(),
            observeSessionDetail = get(),
            observeSessionSummary = get(),
            observeMusclePicker = get(),
            observeUntrained = get(),
            setDistanceTime = get(),
        )
    }
    viewModelOf(::WorkoutLogViewModel)
    viewModelOf(::MuscleCatalogViewModel)
}

fun appModules(): List<Module> =
    listOf(platformModule, databaseModule, repositoryModule, domainModule, viewModelModule)

/**
 * Starts Koin. Call once per process, before anything resolves a dependency.
 *
 * [config] lets each platform add what only it can supply — `androidContext(this)` on
 * Android, a logger, or test overrides.
 */
fun initKoin(config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    config?.invoke(this)
    modules(appModules())
}
