package botix.dev.myweeklyroutinegymapp.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import botix.dev.myweeklyroutinegymapp.config.AppConfig
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Pass the *application* context, not an Activity — the driver outlives any screen.
 */
actual class DriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver {
        // Whichever file this build is not using. deleteDatabase() takes the -wal and
        // -shm sidecars with it, which a bare File.delete() would leave behind holding
        // readable pages — the whole point when the leftover is the plaintext one.
        context.deleteDatabase(UNUSED_DATABASE_NAME)

        val driver =
            if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) createEncryptedDriver() else createPlainDriver()

        // Off by default in SQLite. Without it the ON DELETE CASCADE from
        // workout_session to session_muscle silently does nothing.
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)

        // Only meaningful when encryption was asked for. Running it on a deliberately
        // plain build would fail every time and say nothing useful.
        if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) driver.verifyEncryptionIsActive()

        return driver
    }

    private fun createEncryptedDriver(): SqlDriver {
        // SQLCipher is a native library and nothing loads it for us. Without this the
        // first call into SupportOpenHelperFactory dies with UnsatisfiedLinkError.
        System.loadLibrary("sqlcipher")

        return AndroidSqliteDriver(
            schema = GymDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
            // copyOf() because SQLCipher zeroes the array it is given once the database
            // is open; handing it the caller's copy would blank it for everyone else.
            factory = SupportOpenHelperFactory(DatabasePassphrase.getOrCreate(context).copyOf()),
        )
    }

    /**
     * The pre-encryption driver, kept for `database_available_encrypted=false`.
     *
     * Note what this does *not* do: it never touches [DatabasePassphrase]. A plain build
     * neither creates the Keystore key nor reads it, so switching encryption off and back
     * on reuses the same passphrase rather than orphaning one.
     */
    private fun createPlainDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = GymDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
        )
}
