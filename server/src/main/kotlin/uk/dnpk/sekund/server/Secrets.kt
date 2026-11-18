package uk.dnpk.sekund.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Random tokens and ids, and the hashes the server stores instead of tokens. */
object Secrets {
    private val random = SecureRandom()
    private val urlSafe = Base64.getUrlEncoder().withoutPadding()

    /** [bytes] random bytes as URL-safe text, e.g. for session tokens (32 bytes) or ids (9 bytes). */
    fun randomToken(bytes: Int = 32): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return urlSafe.encodeToString(buffer)
    }

    /**
     * SHA-256 of [token], as text. Tokens are long and random, so (unlike passwords) a plain hash is
     * enough: a stolen database doesn't give anyone a working token.
     */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray())
        return urlSafe.encodeToString(digest)
    }

    /** A random code that's easy to read off a screen and type, e.g. `"K7QM-3XRP"` (no 0/O or 1/I). */
    fun claimCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val text = StringBuilder()
        for (index in 0 until 8) {
            if (index == 4) text.append('-')
            text.append(alphabet[random.nextInt(alphabet.length)])
        }
        return text.toString()
    }
}
