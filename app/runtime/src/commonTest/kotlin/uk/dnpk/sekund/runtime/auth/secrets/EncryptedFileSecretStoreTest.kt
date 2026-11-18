package uk.dnpk.sekund.runtime.auth.secrets

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import uk.dnpk.sekund.runtime.newTestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A stand-in cipher: flips every byte. Enough to prove the file isn't stored as plain text. */
private class FlipCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = ByteArray(plain.size) { index -> plain[index].toInt().inv().toByte() }

    override fun decrypt(encrypted: ByteArray): ByteArray = encrypt(encrypted)
}

/** A cipher that can't read anything, like after the key was lost. */
private class BrokenCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain

    override fun decrypt(encrypted: ByteArray): ByteArray = error("wrong key")
}

class EncryptedFileSecretStoreTest {
    private val file = Path(newTestPaths().secretsDir, "secrets.bin")

    @Test
    fun `secrets survive a restart and are not stored as plain text`() = runTest {
        EncryptedFileSecretStore(file, FlipCipher()).write("account-1", "super-secret-token")

        val reopened = EncryptedFileSecretStore(file, FlipCipher())
        assertEquals("super-secret-token", reopened.read("account-1"))

        val raw = SystemFileSystem.source(file).buffered().use { source -> source.readByteArray() }.decodeToString()
        assertFalse(raw.contains("super-secret-token"))
    }

    @Test
    fun `deleted secrets are gone`() = runTest {
        val store = EncryptedFileSecretStore(file, FlipCipher())
        store.write("a", "1")
        store.delete("a")

        assertNull(EncryptedFileSecretStore(file, FlipCipher()).read("a"))
    }

    @Test
    fun `an unreadable file is moved aside instead of crashing`() = runTest {
        EncryptedFileSecretStore(file, FlipCipher()).write("a", "1")

        val withWrongKey = EncryptedFileSecretStore(file, BrokenCipher())

        assertNull(withWrongKey.read("a"))
        assertTrue(SystemFileSystem.exists(Path(file.toString() + ".unreadable")))
    }
}
