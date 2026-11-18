package uk.dnpk.sekund.runtime.platform

/**
 * True if [signature] (DER-encoded ECDSA, as `openssl dgst -sha256 -sign` writes it) is a valid
 * signature of [data] by [publicKey] (an ECDSA P-256 key in DER form). False for anything malformed.
 */
expect fun verifyEcdsaSignature(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean

/** SHA-256 of data that arrives in pieces (e.g. a large download), without holding it all in memory. */
expect class Sha256Hasher() {
    fun update(bytes: ByteArray, count: Int)

    /** The hash as lowercase hex. Call once, after the last [update]. */
    fun finishHex(): String
}

/**
 * Which `assets` entry of an update manifest fits this device (`android`, `linux-arm64`...),
 * or null if releases aren't built for it.
 */
expect fun updatePlatformKey(): String?
