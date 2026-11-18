package uk.dnpk.sekund.server.relay

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.protocol.server.ServerUser
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.devices.DeviceStore
import uk.dnpk.sekund.server.devices.StoredDevice
import uk.dnpk.sekund.server.http.ServerProblem
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Keeps track of the devices connected right now, and of the claim codes shown on unclaimed ones.
 * Everything that talks to a connected device goes through here:
 *
 * - [claim]: a signed-in user enters the code a device shows, and the device becomes theirs.
 * - [rpc]: a companion's device API request, tunnelled to the device and back.
 * - [events]: the device's events stream, shared by every companion watching it.
 *
 * @param welcome builds the `welcome` message for a device owned by a given username (what the server
 *   offers: OAuth providers, webhooks).
 */
class RelayHub(
    private val devices: DeviceStore,
    private val welcome: (owner: String) -> RelayMessage.Welcome,
    private val ownerName: (ownerId: String) -> String?,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger("Relay")

    /** A code an unclaimed device is showing, and the connection to tell when it's claimed. */
    private class PendingClaim(val connection: DeviceConnection, val expiresAt: Instant)

    private val lock = Mutex()
    private val online = mutableMapOf<String, DeviceConnection>()
    private val claims = mutableMapOf<String, PendingClaim>()

    /** Each device's events, while at least one companion watches. */
    private val eventFlows = mutableMapOf<String, MutableSharedFlow<DeviceEvent>>()
    private val watcherCounts = mutableMapOf<String, Int>()

    suspend fun isOnline(deviceId: String): Boolean = lock.withLock { online.containsKey(deviceId) }

    /**
     * The device on [connection] proved who it is (or was just claimed): it's now online. Replaces any
     * older connection from the same device, and restarts its events if companions are watching.
     */
    suspend fun authenticated(connection: DeviceConnection, ownerId: String) {
        connection.ownerId = ownerId
        val previous = lock.withLock { online.put(connection.deviceId, connection) }
        if (previous != null && previous !== connection) previous.close("Replaced by a newer connection")
        devices.seen(connection.deviceId, connection.name, connection.appVersion)

        connection.send(welcome(ownerName(ownerId) ?: "unknown"))
        val watched = lock.withLock { (watcherCounts[connection.deviceId] ?: 0) > 0 }
        if (watched) connection.send(RelayMessage.EventsStart)
        log.info("Device ${connection.deviceId} connected")
    }

    /** The connection closed. */
    suspend fun disconnected(connection: DeviceConnection) {
        val wasOnline = lock.withLock {
            claims.entries.removeAll { entry -> entry.value.connection === connection }
            if (online[connection.deviceId] === connection) {
                online.remove(connection.deviceId)
                true
            } else {
                false
            }
        }
        if (wasOnline) {
            devices.seen(connection.deviceId, connection.name, connection.appVersion)
            log.info("Device ${connection.deviceId} disconnected")
        }
    }

    /** Gives the unclaimed device on [connection] a new claim code (replacing any older one) and sends it. */
    suspend fun offerClaim(connection: DeviceConnection, serverUrl: String): RelayMessage.ClaimCode {
        val code = Secrets.claimCode()
        val expiresAt = clock.now() + CLAIM_CODE_LIFETIME
        lock.withLock {
            claims.entries.removeAll { entry -> entry.value.connection === connection || entry.value.expiresAt <= clock.now() }
            claims[code] = PendingClaim(connection, expiresAt)
        }
        val message = RelayMessage.ClaimCode(code, expiresAt.toString(), serverUrl)
        connection.send(message)
        return message
    }

    /** [user] entered [code]: the device showing it becomes theirs. */
    suspend fun claim(code: String, user: ServerUser): StoredDevice {
        val cleanCode = code.trim().uppercase().replace(" ", "")
        val withDash = if (cleanCode.length == 8) cleanCode.substring(0, 4) + "-" + cleanCode.substring(4) else cleanCode
        val pending = lock.withLock {
            val found = claims[withDash]
            if (found == null || found.expiresAt <= clock.now()) throw ServerProblem.notFound("No device is showing that code (codes change every 10 minutes)")
            claims.remove(withDash)
            found
        }
        val connection = pending.connection
        val token = devices.claim(connection.deviceId, user.id, connection.name, connection.appVersion)
            ?: throw ServerProblem.conflict("That device already belongs to someone")
        connection.send(RelayMessage.Claimed(token))
        authenticated(connection, user.id)
        log.info("Device ${connection.deviceId} claimed by ${user.username}")
        return devices.find(connection.deviceId) ?: error("The device was just stored")
    }

    /** Disconnects a device that was removed from its owner's account. */
    suspend fun forget(deviceId: String) {
        val connection = lock.withLock { online.remove(deviceId) } ?: return
        connection.send(RelayMessage.Rejected("This device was removed from its owner's account", forgetToken = true))
        connection.close("Removed")
    }

    /**
     * Sends a device API request to [deviceId] and waits for its answer.
     *
     * @throws ServerProblem 503 if the device isn't connected, 504 if it doesn't answer in time.
     */
    suspend fun rpc(deviceId: String, request: RelayMessage.RpcRequest): RelayMessage.RpcResponse {
        val connection = connectionOf(deviceId)
        val reply = connection.request(request.id, request, RPC_TIMEOUT)
            ?: throw ServerProblem(504, "timeout", "The device didn't answer in time")
        if (reply is RelayMessage.RpcResponse) return reply
        if (reply is RelayMessage.Error) throw ServerProblem(502, "device_error", reply.message)
        throw ServerProblem(502, "device_error", "The device gave an unexpected answer")
    }

    /** The open connection to [deviceId]. @throws ServerProblem 503 if it isn't connected. */
    suspend fun connectionOf(deviceId: String): DeviceConnection =
        lock.withLock { online[deviceId] } ?: throw ServerProblem.unavailable("The device isn't connected to the server right now")

    /**
     * The device's events, for as long as the returned flow is collected. Each new watcher asks the
     * device to (re)start its events, so the newcomer gets the current state first (the others see it
     * again, which is harmless). The last watcher to leave asks the device to stop.
     */
    fun events(deviceId: String): Flow<DeviceEvent> = flow {
        val shared = startWatching(deviceId)
        try {
            // onSubscription: only ask for events once we're listening, so the first ones aren't missed.
            shared
                .onSubscription { lock.withLock { online[deviceId] }?.send(RelayMessage.EventsStart) }
                .collect { event -> emit(event) }
        } finally {
            // NonCancellable: this runs because the watcher went away (was cancelled), but must still finish.
            withContext(NonCancellable) { stopWatching(deviceId) }
        }
    }

    /** Called for every `event` message from a device. */
    suspend fun eventArrived(deviceId: String, event: DeviceEvent) {
        val shared = lock.withLock { eventFlows[deviceId] } ?: return
        shared.emit(event)
    }

    private suspend fun startWatching(deviceId: String): MutableSharedFlow<DeviceEvent> = lock.withLock {
        watcherCounts[deviceId] = (watcherCounts[deviceId] ?: 0) + 1
        eventFlows.getOrPut(deviceId) { MutableSharedFlow(extraBufferCapacity = 256) }
    }

    private suspend fun stopWatching(deviceId: String) {
        val isLast = lock.withLock {
            val count = (watcherCounts[deviceId] ?: 1) - 1
            if (count <= 0) {
                watcherCounts.remove(deviceId)
                eventFlows.remove(deviceId)
                true
            } else {
                watcherCounts[deviceId] = count
                false
            }
        }
        if (isLast) {
            try {
                lock.withLock { online[deviceId] }?.send(RelayMessage.EventsStop)
            } catch (_: Exception) {
                // The device is gone already; nothing to stop.
            }
        }
    }

    companion object {
        val CLAIM_CODE_LIFETIME = 10.minutes
        val RPC_TIMEOUT = 30.seconds
    }
}
