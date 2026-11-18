package uk.dnpk.sekund.server.relay

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.devices.DeviceStore
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one device's relay WebSocket (`/v1/device/connect`) from `hello` until it closes:
 *
 * 1. The device says `hello`. With a valid device token it's welcomed; with no token, and if nobody
 *    owns it yet, it's sent a claim code (renewed every 10 minutes until someone claims it).
 * 2. Afterwards, every message from the device is passed to [handleMessage].
 *
 * @param serverUrl this server's address, shown on the device next to the claim code.
 * @param handleMessage handles the device's messages other than replies (webhook registrations,
 *   sign-ins, token refreshes...). Replies to the server's own requests are matched up here.
 */
class DeviceSocket(
    private val hub: RelayHub,
    private val devices: DeviceStore,
    private val serverUrl: String,
    private val handleMessage: suspend (DeviceConnection, RelayMessage) -> Unit,
) {
    private val log = LoggerFactory.getLogger("Relay")

    suspend fun run(session: DefaultWebSocketServerSession) {
        val hello = readHello(session)
        if (hello == null) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Send hello first"))
            return
        }
        val connection = DeviceConnection(hello.deviceId, hello.name.take(60), hello.appVersion.take(40), session)
        if (!start(connection, hello, session)) return

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = decode(frame.readText()) ?: continue // a type from a newer device, or garbage
                dispatch(connection, message)
            }
        } finally {
            hub.disconnected(connection)
        }
    }

    /** Authenticates the device or starts claiming it. Returns false if the connection was refused. */
    private suspend fun start(connection: DeviceConnection, hello: RelayMessage.Hello, session: DefaultWebSocketServerSession): Boolean {
        val token = hello.deviceToken
        if (token != null) {
            val device = devices.authenticate(hello.deviceId, token)
            if (device == null) {
                refuse(connection, RelayMessage.Rejected("This device was removed from its owner's account. Reconnect to claim it again.", forgetToken = true))
                return false
            }
            hub.authenticated(connection, device.ownerId)
            return true
        }
        if (devices.find(hello.deviceId) != null) {
            refuse(connection, RelayMessage.Rejected("This device already belongs to someone. Remove it from their account first."))
            return false
        }
        // Unclaimed: show a code, and a new one whenever it expires, until someone claims the device.
        session.launch {
            while (connection.ownerId == null) {
                hub.offerClaim(connection, serverUrl)
                delay(RelayHub.CLAIM_CODE_LIFETIME)
            }
        }
        return true
    }

    private suspend fun dispatch(connection: DeviceConnection, message: RelayMessage) {
        // Until it's claimed or authenticated a device may only wait.
        if (connection.ownerId == null) return
        when (message) {
            is RelayMessage.RpcResponse -> connection.replyArrived(message.replyTo, message)
            is RelayMessage.Ack -> connection.replyArrived(message.replyTo, message)
            is RelayMessage.Error -> connection.replyArrived(message.replyTo, message)
            is RelayMessage.Event -> hub.eventArrived(connection.deviceId, message.event)
            else -> handleMessage(connection, message)
        }
    }

    private suspend fun refuse(connection: DeviceConnection, rejection: RelayMessage.Rejected) {
        log.info("Refused device ${connection.deviceId}: ${rejection.reason}")
        connection.send(rejection)
        connection.close(rejection.reason)
    }

    private suspend fun readHello(session: DefaultWebSocketServerSession): RelayMessage.Hello? {
        val frame = withTimeoutOrNull(10.seconds) { session.incoming.receiveCatching().getOrNull() } ?: return null
        if (frame !is Frame.Text) return null
        val message = decode(frame.readText())
        if (message !is RelayMessage.Hello) return null
        if (message.deviceId.isBlank() || message.deviceId.length > 64) return null
        return message
    }

    private fun decode(text: String): RelayMessage? {
        try {
            return SekundJson.decodeFromString(RelayMessage.serializer(), text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
    }
}
