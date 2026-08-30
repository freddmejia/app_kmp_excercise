package botix.dev.myweeklyroutinegymapp.db

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The SQLCipher passphrase on iOS, held in the Keychain.
 *
 * The Android side wraps a passphrase with a non-extractable Keystore key and stores the
 * blob itself. iOS needs no such indirection: the Keychain *is* the protected store, and
 * an item in it is already encrypted under a key the app cannot read.
 *
 * ### `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
 * Two decisions in one constant:
 *
 *  * **AfterFirstUnlock**, not WhenUnlocked, because the database is opened on cold
 *    start and from background work, both of which can happen with the screen locked.
 *    The stricter constant would make those paths fail rather than merely wait.
 *  * **ThisDeviceOnly**, so the item is excluded from iCloud Keychain and from encrypted
 *    backups. A passphrase that syncs to a second device turns one stolen backup into a
 *    readable database, which would undo the point of encrypting the file.
 *
 * The consequence matches Android's: restoring this app to a new phone brings the
 * database file but not the key, and the data is gone. That is inherent to device-bound
 * encryption, not an oversight.
 *
 * ⚠️ **NEVER COMPILED.** Written on a Windows machine with no Apple toolchain — there is
 * no `konan` target here that can even parse it. Every signature below was taken from
 * the Security and CoreFoundation headers rather than from a successful build. Treat it
 * as a reviewed proposal, not as working code, until it compiles on a Mac.
 */
@OptIn(ExperimentalForeignApi::class)
internal object DatabasePassphrase {

    private const val SERVICE = "botix.dev.myweeklyroutinegymapp.db"
    private const val ACCOUNT = "gym_db_passphrase"
    private const val PASSPHRASE_LENGTH_BYTES = 32

    /**
     * The passphrase for this install, created on first call and stable thereafter.
     *
     * Returned as lowercase hex rather than raw bytes: SQLCipher takes this as a text
     * passphrase and runs PBKDF2 over it, and pushing arbitrary key bytes through a C
     * string would truncate at the first zero byte.
     */
    fun getOrCreate(): String = read() ?: create()

    private fun read(): String? = memScoped {
        val found = alloc<CFTypeRefVar>()
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE.toCFString(),
            kSecAttrAccount to ACCOUNT.toCFString(),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )

        try {
            when (val status = SecItemCopyMatching(query, found.ptr)) {
                errSecSuccess -> (found.value as? CFDataRef)?.toByteArray()?.decodeToString()
                errSecItemNotFound -> null
                else -> error("Keychain read failed for the database passphrase: OSStatus $status")
            }
        } finally {
            CFRelease(query)
            found.value?.let { CFRelease(it) }
        }
    }

    private fun create(): String {
        val passphrase = randomHex(PASSPHRASE_LENGTH_BYTES)
        val data = passphrase.encodeToByteArray().toCFData()
        val attributes = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE.toCFString(),
            kSecAttrAccount to ACCOUNT.toCFString(),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to data,
        )

        try {
            val status = SecItemAdd(attributes, null)
            check(status == errSecSuccess) {
                "Keychain write failed for the database passphrase: OSStatus $status"
            }
        } finally {
            CFRelease(attributes)
            CFRelease(data)
        }
        return passphrase
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        val status = bytes.usePinned {
            SecRandomCopyBytes(null, byteCount.convert(), it.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: OSStatus $status" }
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    // ---- CoreFoundation plumbing -------------------------------------------------
    // Built with CFDictionaryCreate rather than bridged through NSDictionary: the
    // Security keys are CFStringRef constants, and bridging them into an NSDictionary
    // costs a retain/release dance for no benefit when the dictionary is this short
    // lived. Every object created here is released by the caller's `finally`.

    private fun cfDictionaryOf(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef =
        memScoped {
            val keys = allocArrayOf(*pairs.map { it.first }.toTypedArray())
            val values = allocArrayOf(*pairs.map { it.second }.toTypedArray())
            CFDictionaryCreate(
                kCFAllocatorDefault,
                keys.reinterpret(),
                values.reinterpret(),
                pairs.size.convert(),
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ) ?: error("CFDictionaryCreate returned null")
        }

    private fun ByteArray.toCFData(): CFDataRef = usePinned {
        CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret(), size.convert())
            ?: error("CFDataCreate returned null")
    }

    private fun CFDataRef.toByteArray(): ByteArray {
        val length = CFDataGetLength(this).toInt()
        val bytes: CPointer<ByteVar> = CFDataGetBytePtr(this)?.reinterpret()
            ?: error("CFDataGetBytePtr returned null")
        return ByteArray(length) { bytes[it] }
    }
}
