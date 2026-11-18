package uk.dnpk.sekund.runtime.platform

import kotlinx.io.files.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    random.nextBytes(bytes)
    return bytes
}

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

actual fun restrictToOwner(file: Path) {
    val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    try {
        Files.setPosixFilePermissions(Paths.get(file.toString()), permissions)
    } catch (_: UnsupportedOperationException) {
        // Windows has no POSIX permissions; files under %LOCALAPPDATA% are already private to the user.
    }
}
