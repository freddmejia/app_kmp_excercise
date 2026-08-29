package botix.dev.myweeklyroutinegymapp.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Pass the *application* context, not an Activity — the driver outlives any screen.
 */
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = GymDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
        ).also {
            // Off by default in SQLite. Without it the ON DELETE CASCADE from
            // workout_session to session_muscle silently does nothing.
            it.execute(null, "PRAGMA foreign_keys=ON;", 0)
        }
}
