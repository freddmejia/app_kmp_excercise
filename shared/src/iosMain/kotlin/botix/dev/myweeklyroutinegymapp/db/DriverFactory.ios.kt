package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import botix.dev.myweeklyroutinegymapp.config.AppConfig
import co.touchlab.sqliter.DatabaseConfiguration
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSUserDomainMask

/**
 * ⚠️ **NEVER COMPILED.** See [DatabasePassphrase] — no Apple toolchain exists on the
 * machine this was written on.
 *
 * ### This file alone does not encrypt anything
 * SQLiter exposes `encryptionConfig`, and setting it emits `PRAGMA key`. But SQLiter's
 * default cinterop links the **system `sqlite3`**, which has no cipher and treats
 * `PRAGMA key` as a no-op it does not even complain about. Wiring this up without also
 * linking SQLCipher produces an app that looks encrypted, tests clean, and writes
 * plaintext to disk.
 *
 * The Xcode project must therefore link SQLCipher ahead of the system library:
 *
 *  1. `pod 'SQLCipher', '~> 4.6'` in the iOS app's Podfile, and `pod install`.
 *  2. Build the app from the generated `.xcworkspace`, never the bare `.xcodeproj`.
 *  3. Add `SQLITE_HAS_CODEC` and `SQLCIPHER_CRYPTO_CC` to the target's preprocessor
 *     macros, and `-framework Security` to Other Linker Flags.
 *  4. Confirm the SQLCipher static library precedes any system `libsqlite3.tbd` in
 *     Link Binary With Libraries — link order decides which `sqlite3_key` symbol wins,
 *     and the wrong order fails silently rather than at build time.
 *
 * [verifyEncryptionIsActive] is what catches a botched setup: it asks for
 * `PRAGMA cipher_version` and refuses to start when the answer is empty. Do not remove
 * it to make a build pass — an empty answer means the data is being written in the clear.
 */
actual class DriverFactory {

    actual fun createDriver(): SqlDriver {
        deleteUnusedDatabase()

        val driver = NativeSqliteDriver(
            schema = GymDatabase.Schema,
            name = DATABASE_NAME,
            onConfiguration = { config ->
                val withForeignKeys = config.copy(
                    extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true),
                )
                if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) {
                    withForeignKeys.copy(
                        encryptionConfig = DatabaseConfiguration.Encryption(
                            key = DatabasePassphrase.getOrCreate(),
                        ),
                    )
                } else {
                    withForeignKeys
                }
            },
        )

        if (AppConfig.DATABASE_AVAILABLE_ENCRYPTED) driver.verifyEncryptionIsActive()
        return driver
    }

    /**
     * Drops whichever database this build is not using, matching Android's
     * `deleteDatabase` call.
     *
     * SQLiter puts databases under Library/databases. The `-wal` and `-shm` sidecars are
     * removed explicitly: leaving them behind would leave readable pages on disk, which
     * is exactly what this change exists to prevent.
     */
    private fun deleteUnusedDatabase() {
        val manager = NSFileManager.defaultManager
        val library = manager.URLsForDirectory(NSLibraryDirectory, NSUserDomainMask)
            .firstOrNull() as? platform.Foundation.NSURL ?: return
        val databases = library.URLByAppendingPathComponent("databases") ?: return

        listOf(
            UNUSED_DATABASE_NAME,
            "$UNUSED_DATABASE_NAME-wal",
            "$UNUSED_DATABASE_NAME-shm",
        ).forEach { name ->
            databases.URLByAppendingPathComponent(name)?.let { url ->
                if (manager.fileExistsAtPath(url.path ?: return@let)) {
                    manager.removeItemAtURL(url, null)
                }
            }
        }
    }
}
