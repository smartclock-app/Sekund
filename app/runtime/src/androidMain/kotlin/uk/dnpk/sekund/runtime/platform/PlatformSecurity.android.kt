package uk.dnpk.sekund.runtime.platform

import kotlinx.io.files.Path
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
    // Nothing to do: secrets live in the app's internal storage, which only this app can read.
}
