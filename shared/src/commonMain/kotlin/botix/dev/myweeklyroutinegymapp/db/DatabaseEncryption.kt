package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Fails loudly if the connection is not actually running SQLCipher.
 *
 * ### Why this exists
 * The dangerous failure mode for whole-database encryption is not a crash — it is a
 * database that opens perfectly and is stored in the clear. `PRAGMA key` on a plain
 * SQLite build is not an error; it is ignored. The app would work, every test would
 * pass, and the file on disk would be readable with any SQLite browser.
 *
 * That is a live risk on iOS specifically: SQLiter's default cinterop links the system
 * `sqlite3`, which has no cipher at all, so an incomplete CocoaPods setup silently
 * produces exactly this. It is much harder to reach on Android, where SQLCipher ships
 * its own `libsqlcipher.so` and a mislink is an UnsatisfiedLinkError instead.
 *
 * `PRAGMA cipher_version` is the cheap discriminator: SQLCipher answers with its
 * version string, stock SQLite returns no row at all. Crashing on startup is the right
 * response — shipping an app that believes it encrypts and does not is worse than an
 * app that will not start.
 */
internal fun SqlDriver.verifyEncryptionIsActive() {
    val cipherVersion = executeQuery(
        identifier = null,
        sql = "PRAGMA cipher_version;",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        },
        parameters = 0,
    ).value

    check(!cipherVersion.isNullOrBlank()) {
        "Database is NOT encrypted: PRAGMA cipher_version returned no value, which " +
            "means this connection is plain SQLite rather than SQLCipher. Refusing to " +
            "continue, because the data would be written to disk in the clear."
    }
}
