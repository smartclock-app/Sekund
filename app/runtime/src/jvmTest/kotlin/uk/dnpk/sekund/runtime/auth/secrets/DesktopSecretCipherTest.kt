package uk.dnpk.sekund.runtime.auth.secrets

import kotlinx.io.files.Path
import uk.dnpk.sekund.runtime.newTestPaths
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DesktopSecretCipherTest {
    private val keyFile = Path(newTestPaths().secretsDir, "secrets.key")

    @Test
    fun `encrypts and decrypts with a key file only the owner can read`() {
        val cipher = DesktopSecretCipher(keyFile, useKeychain = false)
        val encrypted = cipher.encrypt("hello".encodeToByteArray())

        assertFalse(encrypted.decodeToString().contains("hello"))
        assertContentEquals("hello".encodeToByteArray(), cipher.decrypt(encrypted))

        // A second cipher (the app restarted) reuses the same key file.
        assertContentEquals("hello".encodeToByteArray(), DesktopSecretCipher(keyFile, useKeychain = false).decrypt(encrypted))

        if (!System.getProperty("os.name").lowercase().contains("win")) {
            val permissions = Files.getPosixFilePermissions(Paths.get(keyFile.toString()))
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
        }
    }

    @Test
    fun `tampered data is rejected`() {
        val cipher = DesktopSecretCipher(keyFile, useKeychain = false)
        val encrypted = cipher.encrypt("hello".encodeToByteArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

        assertFailsWith<Exception> { cipher.decrypt(encrypted) }
    }

    @Test
    fun `a different key can't read it`() {
        val encrypted = DesktopSecretCipher(keyFile, useKeychain = false).encrypt("hello".encodeToByteArray())
        File(keyFile.toString()).delete()

        assertFailsWith<Exception> { DesktopSecretCipher(keyFile, useKeychain = false).decrypt(encrypted) }
    }
}
