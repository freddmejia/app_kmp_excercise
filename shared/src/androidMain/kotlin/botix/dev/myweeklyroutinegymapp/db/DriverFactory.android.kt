package botix.dev.myweeklyroutinegymapp.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Pass the *application* context, not an Activity — the driver outlives any screen.
 */
actual class DriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver {
        // SQLCipher is a native library and nothing loads it for us. Without this the
        // first call into SupportOpenHelperFactory dies with UnsatisfiedLinkError.
        System.loadLibrary("sqlcipher")

        // The plaintext database from before encryption. Dropped rather than migrated,
        // by explicit decision — see LEGACY_PLAINTEXT_DATABASE_NAME. deleteDatabase()
        // takes the -wal and -shm sidecars with it, which a bare File.delete() would
        // leave behind holding readable pages.
        context.deleteDatabase(LEGACY_PLAINTEXT_DATABASE_NAME)

        val driver = AndroidSqliteDriver(
            schema = GymDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
            // copyOf() because SQLCipher zeroes the array it is given once the database
            // is open; handing it the caller's copy would blank it for everyone else.
            factory = SupportOpenHelperFactory(DatabasePassphrase.getOrCreate(context).copyOf()),
        )

        // Off by default in SQLite. Without it the ON DELETE CASCADE from
        // workout_session to session_muscle silently does nothing.
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)

        driver.verifyEncryptionIsActive()
        return driver
    }
}
