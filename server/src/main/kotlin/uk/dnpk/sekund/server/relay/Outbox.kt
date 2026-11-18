package uk.dnpk.sekund.server.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import uk.dnpk.sekund.protocol.relay.RelayMessage
import kotlin.time.Duration.Companion.seconds

/**
 * Messages that must reach a device even if it's offline right now: webhook events and delivered
 * sign-ins. Each waits here until the device answers it with an `ack`; whatever is still waiting is
 * sent again every time the device reconnects ("at-least-once": a device may see a message twice and
 * ignores repeats by id).
 *
 * Kept in memory, at most [MAX_PER_DEVICE] per device (the oldest are dropped), so a server restart
 * loses what was waiting. For webhooks that's harmless: devices still poll as a fallback.
 */
class Outbox(
    private val connectionOf: suspend (deviceId: String) -> DeviceConnection?,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger("Outbox")
    private val lock = Mutex()
    private val waiting = mutableMapOf<String, LinkedHashMap<String, RelayMessage>>()

    /** Queues [message] (whose id is [id]) for [deviceId] and sends it now if the device is connected. */
    suspend fun deliver(deviceId: String, id: String, message: RelayMessage) {
        lock.withLock {
            val queue = waiting.getOrPut(deviceId) { LinkedHashMap() }
            queue[id] = message
            while (queue.size > MAX_PER_DEVICE) {
                val oldest = queue.keys.first()
                queue.remove(oldest)
                log.warn("Dropped an undelivered message for $deviceId (too many waiting)")
            }
        }
        val connection = connectionOf(deviceId) ?: return
        scope.launch { send(connection, id, message) }
    }

    /** The device just (re)connected: sends everything still waiting for it. */
    suspend fun resend(connection: DeviceConnection) {
        val pending = lock.withLock { waiting[connection.deviceId]?.toList() ?: emptyList() }
        for ((id, message) in pending) {
            scope.launch { send(connection, id, message) }
        }
    }

    /** How many messages are waiting for [deviceId] (for tests and logs). */
    suspend fun waitingFor(deviceId: String): Int = lock.withLock { waiting[deviceId]?.size ?: 0 }

    private suspend fun send(connection: DeviceConnection, id: String, message: RelayMessage) {
        try {
            val reply = connection.request(id, message, ACK_TIMEOUT)
            if (reply is RelayMessage.Ack) {
                lock.withLock { waiting[connection.deviceId]?.remove(id) }
            }
        } catch (error: Exception) {
            // The connection broke; it'll be sent again when the device is back.
            log.debug("Couldn't deliver $id to ${connection.deviceId}: ${error.message}")
        }
    }

    companion object {
        const val MAX_PER_DEVICE = 100
        val ACK_TIMEOUT = 30.seconds
    }
}
