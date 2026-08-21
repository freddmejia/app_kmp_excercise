package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_NAME: String = "gym.db"

/**
 * Platform-specific SQLite driver. Android needs a Context to resolve the database
 * file, iOS does not, so construction differs per platform while [createDriver] does not.
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Builds the database with its column adapters.
 *
 * Only `activity.measurement_type` needs one — booleans map natively in the SQLite
 * dialect. Prefer a single long-lived instance: each driver opens its own connection.
 */
fun createDatabase(driverFactory: DriverFactory): GymDatabase =
    createDatabase(driverFactory.createDriver())

fun createDatabase(driver: SqlDriver): GymDatabase =
    GymDatabase(
        driver = driver,
        activityAdapter = Activity.Adapter(
            measurement_typeAdapter = app.cash.sqldelight.EnumColumnAdapter(),
        ),
    )
