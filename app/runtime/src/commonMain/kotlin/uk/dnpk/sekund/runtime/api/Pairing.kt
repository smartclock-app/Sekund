package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.api.PairResult
import uk.dnpk.sekund.protocol.api.PairStarted
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.runtime.platform.secureRandomBytes
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.context.Alert
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Pairing a companion with this device, by PIN:
 *
 * 1. The companion calls [start] (`POST /v1/pair`). The device shows a 6-digit PIN on screen.
 * 2. The person types the PIN into the companion, which calls [confirm] (`POST /v1/pair/confirm`).
 * 3. If it matches, the companion gets a token for every other request.
 *
 * Only someone who can see the screen can pair. Guessing is limited: each pairing allows
 * [MAX_ATTEMPTS] wrong PINs and lasts 5 minutes, and at most [MAX_PENDING] can wait at once.
 */
class Pairing(
    private val clients: ApiClients,
    private val alerts: AlertCenter,
    private val deviceId: () -> String,
    private val clock: Clock = Clock.System,
) {
    private class Pending(val clientName: String, val pin: String, val expiresAt: Instant, var attemptsLeft: Int)

    private val lock = Mutex()
    private val pending = mutableMapOf<String, Pending>()

    suspend fun start(clientName: String): PairStarted {
        val name = clientName.trim().take(60)
        if (name.isEmpty()) throw ApiProblem.badRequest("clientName must not be empty")

        lock.withLock {
            forgetExpired()
            if (pending.size >= MAX_PENDING) {
                throw ApiProblem(429, "too_many_attempts", "Too many pairing requests at once. Try again in a few minutes.")
            }
            val pairingId = randomUrlSafe(9)
            val pin = newPin()
            val expiresAt = clock.now() + 5.minutes
            pending[pairingId] = Pending(name, pin, expiresAt, MAX_ATTEMPTS)
            alerts.show(
                alertOwner(pairingId),
                Alert(title = "Pair with $name?", subtitle = "PIN ${pin.substring(0, 3)} ${pin.substring(3)}. Enter it in the app to allow it."),
            )
            return PairStarted(pairingId = pairingId, expiresAt = expiresAt.toString())
        }
    }

    suspend fun confirm(pairingId: String, pin: String): PairResult {
        val clientName = lock.withLock {
            forgetExpired()
            val waiting = pending[pairingId] ?: throw ApiProblem.notFound("This pairing expired or doesn't exist. Start again.")
            if (waiting.pin != pin.filter { char -> char.isDigit() }) {
                waiting.attemptsLeft -= 1
                if (waiting.attemptsLeft <= 0) {
                    remove(pairingId)
                    throw ApiProblem(429, "too_many_attempts", "Too many wrong PINs. Start again.")
                }
                throw ApiProblem(401, "unauthorized", "Wrong PIN")
            }
            remove(pairingId)
            waiting.clientName
        }
        val (client, token) = clients.add(clientName)
        return PairResult(token = token, clientId = client.id, deviceId = deviceId())
    }

    /** Must be called with [lock] held. */
    private fun forgetExpired() {
        val now = clock.now()
        val expired = pending.filter { entry -> entry.value.expiresAt <= now }.keys
        for (id in expired) remove(id)
    }

    /** Must be called with [lock] held. */
    private fun remove(pairingId: String) {
        pending.remove(pairingId)
        alerts.clear(alertOwner(pairingId))
    }

    /** A random 6-digit PIN, e.g. "042917". */
    private fun newPin(): String {
        val bytes = secureRandomBytes(4)
        var number = 0L
        for (byte in bytes) {
            number = (number shl 8) or (byte.toLong() and 0xFF)
        }
        return (number % 1_000_000).toString().padStart(6, '0')
    }

    private fun alertOwner(pairingId: String) = "pairing:$pairingId"

    companion object {
        const val MAX_ATTEMPTS = 5
        const val MAX_PENDING = 3
    }
}
