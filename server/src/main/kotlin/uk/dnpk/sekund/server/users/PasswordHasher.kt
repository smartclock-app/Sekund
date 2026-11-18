package uk.dnpk.sekund.server.users

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Hashes passwords with Argon2id (the current recommendation for password storage), using
 * Bouncy Castle's pure-Java implementation so no native library is needed.
 *
 * A stored hash looks like `argon2id$19456$2$1$<salt>$<hash>`: the settings it was made with come first,
 * so they can be raised later without breaking existing passwords.
 */
class PasswordHasher(
    private val memoryKiB: Int = 19_456, // OWASP's minimum recommendation: 19 MiB, 2 passes, 1 lane
    private val iterations: Int = 2,
    private val parallelism: Int = 1,
) {
    private val random = SecureRandom()
    private val base64 = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = argon2(password, salt, memoryKiB, iterations, parallelism)
        return "argon2id$$memoryKiB$$iterations$$parallelism$" + base64.encodeToString(salt) + "$" + base64.encodeToString(hash)
    }

    /** Whether [password] matches [stored] (a value from [hash]). False for anything malformed. */
    fun matches(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 6 || parts[0] != "argon2id") return false
        val memory = parts[1].toIntOrNull() ?: return false
        val passes = parts[2].toIntOrNull() ?: return false
        val lanes = parts[3].toIntOrNull() ?: return false
        val salt = decoder.decode(parts[4])
        val expected = decoder.decode(parts[5])
        val actual = argon2(password, salt, memory, passes, lanes)
        // Compares in constant time, so the time taken doesn't hint at how much matched.
        return MessageDigest.isEqual(expected, actual)
    }

    private fun argon2(password: String, salt: ByteArray, memory: Int, passes: Int, lanes: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memory)
            .withIterations(passes)
            .withParallelism(lanes)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val output = ByteArray(32)
        generator.generateBytes(password.toCharArray(), output)
        return output
    }
}
