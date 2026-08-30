package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.db.SqlDriver

/**
 * The encrypted database.
 *
 * Deliberately a different filename from [LEGACY_PLAINTEXT_DATABASE_NAME]. SQLCipher
 * cannot open a plaintext file, so reusing "gym.db" would mean every launch tried to
 * decrypt something that was never encrypted and failed with a bare "file is not a
 * database". A new name makes the two states distinguishable on disk.
 */
internal const val DATABASE_NAME: String = "gym-encrypted.db"

/**
 * The unencrypted database shipped before encryption existed.
 *
 * Deleted on first launch rather than migrated — a deliberate choice, taken knowing it
 * discards whatever the user had logged. `sqlcipher_export()` could have carried it
 * across; it was not asked for. Nothing else in the app refers to this name.
 */
internal const val LEGACY_PLAINTEXT_DATABASE_NAME: String = "gym.db"

/**
 * Platform-specific SQLite driver. Android needs a Context to resolve the database
 * file, iOS does not, so construction differs per platform while [createDriver] does not.
 *
 * Both platforms now open the file through SQLCipher, and both source their passphrase
 * from the platform keystore. The encryption is whole-database and page-level, which is
 * why nothing above this class changed: `ORDER BY name`, `BETWEEN` on dates, the unique
 * index on category names and every `SUM`/`MIN`/`MAX` still run inside SQLite against
 * plaintext values held only in memory.
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
