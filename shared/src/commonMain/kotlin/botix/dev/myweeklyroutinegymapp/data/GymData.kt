package botix.dev.myweeklyroutinegymapp.data

import botix.dev.myweeklyroutinegymapp.data.repository.ActivityCategoryRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.MuscleCatalogRepositoryImpl
import botix.dev.myweeklyroutinegymapp.data.repository.WorkoutRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.DriverFactory
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository

/**
 * Composition root for the data layer: opens the database once and exposes the
 * repositories behind their domain interfaces.
 *
 * Deliberately plain — no DI framework — so nothing above this is coupled to one.
 * Swapping in Koin or Hilt later means replacing this class, not the repositories.
 *
 * **Create exactly one per process.** Each instance opens its own driver and
 * connection; a second instance would not share the first's query notifications, so
 * writes through one would not refresh Flows collected from the other.
 */
class GymData(
    private val database: GymDatabase,
) {
    constructor(driverFactory: DriverFactory) : this(createDatabase(driverFactory))

    val activityCategories: ActivityCategoryRepository by lazy { ActivityCategoryRepositoryImpl(database) }
    val bodyWeight: BodyWeightRepository by lazy { BodyWeightRepositoryImpl(database) }
    val muscles: MuscleCatalogRepository by lazy { MuscleCatalogRepositoryImpl(database) }
    val workouts: WorkoutRepository by lazy { WorkoutRepositoryImpl(database) }
}
