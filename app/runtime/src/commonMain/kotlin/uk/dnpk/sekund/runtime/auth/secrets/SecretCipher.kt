package uk.dnpk.sekund.runtime.auth.secrets

/**
 * Encrypts and decrypts the secrets file. Each platform keeps the key somewhere safe:
 * - Android: the Android Keystore (`AndroidKeystoreCipher`), so the key never leaves secure hardware.
 * - Desktop: the OS keychain, or a key file only the current user can read (`DesktopSecretCipher`).
 */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** @throws Exception if [encrypted] wasn't made with this key or has been tampered with. */
    fun decrypt(encrypted: ByteArray): ByteArray
}
