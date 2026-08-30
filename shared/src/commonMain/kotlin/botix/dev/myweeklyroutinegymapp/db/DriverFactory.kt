package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.db.SqlDriver
import botix.dev.myweeklyroutinegymapp.config.AppConfig

/** The SQLCipher database, used when encryption is on. */
internal const val ENCRYPTED_DATABASE_NAME: String = "gym-encrypted.db"

/** The plain SQLite database, used when encryption is off. */
internal const val PLAINTEXT_DATABASE_NAME: String = "gym.db"

/**
 * The file this build actually opens, chosen by `database_available_encrypted` in
 * app.properties (see the generated [AppConfig]).
 *
 * The two modes deliberately use different filenames. SQLCipher cannot open a plaintext
 * file and plain SQLite cannot open a ciphertext one, so sharing a name would turn every
 * flag flip into "file is not a database" on a file that is perfectly intact.
 */
internal val DATABASE_NAME: String =
    if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) ENCRYPTED_DATABASE_NAME else PLAINTEXT_DATABASE_NAME

/**
 * The file this build does *not* open, deleted on every start.
 *
 * Two reasons, and the first is the important one:
 *
 *  * **Turning encryption on must remove the plaintext copy.** Leaving `gym.db` on disk
 *    while writing to `gym-encrypted.db` would mean the app encrypts new data and leaves
 *    every earlier row readable beside it — worse than not encrypting at all, because it
 *    looks solved.
 *  * Symmetrically, turning encryption off drops the encrypted file, which is now
 *    unopenable dead weight.
 *
 * The cost is that flipping the flag discards the data, every time. That follows from
 * the start-fresh policy chosen when encryption was added; a migration would need
 * `sqlcipher_export()` in one direction and a plain copy in the other.
 */
internal val UNUSED_DATABASE_NAME: String =
    if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) PLAINTEXT_DATABASE_NAME else ENCRYPTED_DATABASE_NAME

/**
 * Platform-specific SQLite driver. Android needs a Context to resolve the database
 * file, iOS does not, so construction differs per platform while [createDriver] does not.
 *
 * When encryption is on, both platforms open the file through SQLCipher and source
 * their passphrase from the platform keystore. The encryption is whole-database and
 * page-level, which is why nothing above this class changed: `ORDER BY name`, `BETWEEN`
 * on dates, the unique index on category names and every `SUM`/`MIN`/`MAX` still run
 * inside SQLite against plaintext values held only in memory.
 *
 * `database_available_encrypted=false` in app.properties turns that off and opens a
 * plain database instead. It exists so Android Studio's Database Inspector can see the
 * data: the inspector hooks the framework `android.database.sqlite.SQLiteDatabase`, and
 * SQLCipher is a separate fork of that class, so an encrypted build shows no databases
 * at all rather than showing locked ones.
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
