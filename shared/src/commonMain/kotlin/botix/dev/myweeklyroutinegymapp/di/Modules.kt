package botix.dev.myweeklyroutinegymapp.di

import botix.dev.myweeklyroutinegymapp.data.GymData
import botix.dev.myweeklyroutinegymapp.data.SystemClock
import botix.dev.myweeklyroutinegymapp.db.DriverFactory
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.AssignActivityCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ChangeMeasurementTypeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivityPickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoryUsageUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.DeleteBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.GetBodyWeightForDayUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightHistoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightWeekUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ArchiveMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleLinksUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleSectionsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetActivityMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleGroupUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveGroupVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleGroupsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleProgressionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionTotalsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveTrainedDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RenameMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartOrResumeSessionUseCase
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityListViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryViewModel
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightViewModel
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleCatalogViewModel
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
    single { get<GymData>().activities }
    single { get<GymData>().activityCategories }
    single { get<GymData>().activityMuscles }
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
    // Activity catalogue
    factoryOf(::ObserveActivitiesUseCase)
    factoryOf(::ObserveActivityPickerUseCase)
    factoryOf(::ObserveActivityUseCase)
    factoryOf(::CreateActivityUseCase)
    factoryOf(::UpdateActivityUseCase)
    factoryOf(::ArchiveActivityUseCase)
    factoryOf(::DeleteActivityUseCase)
    factoryOf(::ChangeMeasurementTypeUseCase)

    // Activity categories
    factoryOf(::ObserveCategoriesUseCase)
    factoryOf(::ObserveCategoryUsageUseCase)
    factoryOf(::CreateCategoryUseCase)
    factoryOf(::UpdateCategoryUseCase)
    factoryOf(::ArchiveCategoryUseCase)
    factoryOf(::DeleteCategoryUseCase)
    factoryOf(::AssignActivityCategoryUseCase)

    // Activity -> muscle link
    factoryOf(::ObserveActivityMuscleSectionsUseCase)
    factoryOf(::ObserveActivityMuscleLinksUseCase)
    factoryOf(::SetActivityMusclesUseCase)

    // Body weight
    factoryOf(::ObserveBodyWeightChartUseCase)
    factoryOf(::ObserveBodyWeightHistoryUseCase)
    factoryOf(::ObserveBodyWeightWeekUseCase)
    factoryOf(::LogBodyWeightUseCase)
    factoryOf(::GetBodyWeightForDayUseCase)
    factoryOf(::DeleteBodyWeightUseCase)

    // Workout logging
    factoryOf(::StartOrResumeSessionUseCase)
    factoryOf(::LogMuscleSetsUseCase)
    factoryOf(::LogDistanceTimeUseCase)
    factoryOf(::SetMuscleSetsUseCase)
    factoryOf(::RemoveMuscleFromSessionUseCase)
    factoryOf(::ObserveSessionDetailUseCase)
    factoryOf(::ObserveSessionDaysUseCase)
    factoryOf(::ObserveDayLogUseCase)
    factoryOf(::DeleteSessionUseCase)
    factoryOf(::ObserveSessionTotalsUseCase)
    factoryOf(::ObserveMusclePickerUseCase)
    factoryOf(::ObserveGroupVolumeUseCase)
    factoryOf(::ObserveActivityVolumeUseCase)
    factoryOf(::ObserveMuscleVolumeUseCase)
    factoryOf(::ObserveUntrainedMusclesUseCase)
    factoryOf(::ObserveMuscleProgressionUseCase)
    factoryOf(::ObserveTrainedDaysUseCase)

    // Muscle catalogue
    factoryOf(::ObserveMuscleGroupsUseCase)
    factoryOf(::ObserveMusclesUseCase)
    factoryOf(::CreateMuscleGroupUseCase)
    factoryOf(::CreateMuscleUseCase)
    factoryOf(::RenameMuscleUseCase)
    factoryOf(::ArchiveMuscleUseCase)
    factoryOf(::DeleteMuscleUseCase)
}

val viewModelModule = module {
    viewModelOf(::ActivityListViewModel)
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
    viewModel { (activityId: Long, epochDay: Long) ->
        WorkoutEntryViewModel(
            activityId = activityId,
            epochDay = epochDay,
            observeActivity = get(),
            startOrResumeSession = get(),
            logMuscleSets = get(),
            setMuscleSets = get(),
            removeMuscle = get(),
            observeSessionDetail = get(),
            observeMusclePicker = get(),
            observeActivityMuscleSections = get(),
            observeUntrained = get(),
            observeDayLog = get(),
            logDistanceTime = get(),
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
