package uk.dnpk.sekund.runtime.auth.secrets

import co.touchlab.kermit.Logger
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import kotlinx.io.files.Path
import uk.dnpk.sekund.runtime.platform.restrictToOwner
import uk.dnpk.sekund.runtime.platform.secureRandomBytes
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * Encrypts secrets on desktop with an AES-256 key that is kept:
 * 1. in the OS keychain (macOS Keychain, Windows Credential Manager, Linux Secret Service), if there is one;
 * 2. otherwise in [keyFile], readable only by the current user. That's the usual case on a Raspberry Pi
 *    running without a desktop session.
 *
 * Output format: 12-byte random IV, then the AES-GCM ciphertext (which includes its authentication tag).
 *
 * @param useKeychain false skips the OS keychain (tests use this so they don't touch the real one).
 */
class DesktopSecretCipher(
    private val keyFile: Path,
    private val useKeychain: Boolean = true,
) : SecretCipher {
    private val log = Logger.withTag("Secrets")

    // `by lazy`: worked out the first time it's used, then remembered.
    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = secureRandomBytes(IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val iv = encrypted.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE)
    }

    private fun loadOrCreateKey(): SecretKey {
        if (useKeychain) {
            val fromKeychain = keyFromKeychain()
            if (fromKeychain != null) return SecretKeySpec(fromKeychain, "AES")
        }
        return SecretKeySpec(keyFromFile(), "AES")
    }

    /** The key from the OS keychain (created there the first time), or null if there's no usable keychain. */
    private fun keyFromKeychain(): ByteArray? {
        try {
            Keyring.create().use { keyring ->
                try {
                    return Base64.decode(keyring.getPassword(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT))
                } catch (_: PasswordAccessException) {
                    // Not there yet: create it.
                    val fresh = secureRandomBytes(KEY_SIZE)
                    keyring.setPassword(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT, Base64.encode(fresh))
                    log.i { "Created the secrets key in the OS keychain" }
                    return fresh
                }
            }
        } catch (error: Exception) {
            // No keychain on this system (common on a headless Raspberry Pi), or access was refused.
            log.w { "OS keychain unavailable (${error.message}); using a key file instead" }
            return null
        }
    }

    private fun keyFromFile(): ByteArray {
        val file = File(keyFile.toString())
        if (file.exists()) return Base64.decode(file.readText().trim())

        file.parentFile?.mkdirs()
        val fresh = secureRandomBytes(KEY_SIZE)
        file.writeText(Base64.encode(fresh))
        restrictToOwner(keyFile)
        log.i { "Created the secrets key file $keyFile" }
        return fresh
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "Sekund"
        const val KEYCHAIN_ACCOUNT = "secret-store-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 32
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
