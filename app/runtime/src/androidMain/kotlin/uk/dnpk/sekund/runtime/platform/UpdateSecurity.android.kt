package uk.dnpk.sekund.runtime.platform

// Android: the same java.security code as desktop (both run on a JVM).
import uk.dnpk.sekund.protocol.update.UpdatePlatforms
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

actual fun verifyEcdsaSignature(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
    try {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(data)
        return verifier.verify(signature)
    } catch (error: GeneralSecurityException) {
        return false
    }
}

actual class Sha256Hasher actual constructor() {
    private val digest = MessageDigest.getInstance("SHA-256")

    actual fun update(bytes: ByteArray, count: Int) {
        digest.update(bytes, 0, count)
    }

    actual fun finishHex(): String = digest.digest().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

actual fun updatePlatformKey(): String? = UpdatePlatforms.ANDROID
