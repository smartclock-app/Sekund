package uk.dnpk.sekund.runtime.auth

import uk.dnpk.sekund.runtime.platform.secureRandomBytes
import uk.dnpk.sekund.runtime.platform.sha256
import kotlin.io.encoding.Base64

/**
 * PKCE (RFC 7636): proves that whoever finishes a sign-in is whoever started it, without a client secret.
 *
 * We keep a random [verifier] and send only its hash (the [challenge]) in the authorize URL. When we
 * exchange the code for tokens we send the verifier itself; the service checks it hashes to the challenge.
 */
class Pkce private constructor(val verifier: String, val challenge: String) {
    /** Always `S256` (SHA-256), the only method worth using. */
    val method: String = "S256"

    companion object {
        /** A new random verifier and its challenge. */
        fun create(): Pkce {
            val verifier = randomUrlSafe(32)
            val challenge = urlSafeBase64.encode(sha256(verifier.encodeToByteArray()))
            return Pkce(verifier, challenge)
        }
    }
}

/** Base64 with `-` and `_` instead of `+` and `/`, and no `=` padding: safe to put in URLs. */
internal val urlSafeBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** [byteCount] random bytes as URL-safe text. Used for PKCE verifiers, `state` values and ids. */
internal fun randomUrlSafe(byteCount: Int): String = urlSafeBase64.encode(secureRandomBytes(byteCount))
