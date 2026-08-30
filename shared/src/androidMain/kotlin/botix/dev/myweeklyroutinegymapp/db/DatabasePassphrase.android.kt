package botix.dev.myweeklyroutinegymapp.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The SQLCipher passphrase, and where it lives.
 *
 * Two keys, not one, and the indirection is the point:
 *
 *  * a random 32-byte **passphrase** opens the database, and
 *  * an AES-256-GCM **wrapping key** held in the Android Keystore encrypts that
 *    passphrase at rest.
 *
 * The wrapping key is generated inside the Keystore and is not extractable — on a
 * device with a secure element it never exists in app memory at all. So the only thing
 * this app ever stores is the wrapped blob, which is useless on its own: lifting it out
 * of SharedPreferences off a rooted device buys nothing without the hardware that holds
 * the key it was wrapped with.
 *
 * Storing the passphrase directly in SharedPreferences would have been fewer lines and
 * no security at all — app-private storage is readable by root, which is precisely the
 * attacker this whole exercise exists to stop.
 *
 * ### Losing this key loses the data
 * The wrapping key dies with the app's data (uninstall, "clear data") and with the
 * Keystore itself (factory reset, and on some devices a change of lock screen). When it
 * goes, the passphrase cannot be unwrapped and the database cannot be opened, ever.
 * There is no recovery path and by design there cannot be one — that is the same
 * property that makes the file safe on a stolen phone.
 */
internal object DatabasePassphrase {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val WRAPPING_KEY_ALIAS = "gym_db_passphrase_wrapping_key"
    private const val PREFS_NAME = "gym_db_security"
    private const val PREF_WRAPPED_PASSPHRASE = "wrapped_passphrase"

    private const val PASSPHRASE_LENGTH_BYTES = 32
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * The passphrase for this install, created on first call and stable thereafter.
     *
     * The returned array is a fresh copy each time: SQLCipher zeroes the passphrase it
     * is handed once the database is open, so a shared array would come back blank on
     * the second call.
     */
    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED_PASSPHRASE, null)
        if (stored != null) {
            return unwrap(Base64.decode(stored, Base64.NO_WRAP))
        }

        val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(PREF_WRAPPED_PASSPHRASE, Base64.encodeToString(wrap(passphrase), Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    /** Prepends the GCM IV to the ciphertext; [unwrap] splits them back apart. */
    private fun wrap(passphrase: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        // Let the provider choose the IV. Supplying one to an AndroidKeyStore GCM key
        // is rejected outright, and reusing an IV with GCM is a catastrophic break.
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH_BYTES) { "unexpected GCM IV length: ${iv.size}" }
        return iv + cipher.doFinal(passphrase)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_LENGTH_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(WRAPPING_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    WRAPPING_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // Deliberately NOT setUserAuthenticationRequired(true): the app reads
                    // and writes the database from background work and on cold start,
                    // where there is no unlocked-in-the-last-N-seconds guarantee. Gating
                    // the key on biometrics would break those paths, and this app holds
                    // training logs rather than credentials.
                    .build(),
            )
        }.generateKey()
    }
}
