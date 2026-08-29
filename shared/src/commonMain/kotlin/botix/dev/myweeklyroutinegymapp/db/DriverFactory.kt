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
 * One column needs one — `activity_category.measurement_type`. It was two until
 * activities were removed (6.sqm) and the category became the only thing carrying a
 * measurement type. Booleans map natively in the SQLite dialect.
 *
 * Prefer a single long-lived instance: each driver opens its own connection.
 */
fun createDatabase(driverFactory: DriverFactory): GymDatabase =
    createDatabase(driverFactory.createDriver())

fun createDatabase(driver: SqlDriver): GymDatabase =
    GymDatabase(
        driver = driver,
        activity_categoryAdapter = Activity_category.Adapter(
            measurement_typeAdapter = app.cash.sqldelight.EnumColumnAdapter(),
        ),
    )
