package uk.dnpk.sekund.server.relay

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.relay.RelayMessage
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * One device's open relay WebSocket. Sends [RelayMessage]s to the device, and matches the device's
 * replies to the requests waiting for them (see [request]).
 *
 * @property name/appVersion as the device said in its `hello`.
 */
class DeviceConnection(
    val deviceId: String,
    val name: String,
    val appVersion: String,
    private val session: WebSocketSession,
) {
    /** Requests waiting for an answer, by the id the answer will carry in `replyTo`. */
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<RelayMessage>>()

    /** Who owns the device, once the connection is authenticated (or the device has been claimed). */
    @Volatile
    var ownerId: String? = null

    suspend fun send(message: RelayMessage) {
        session.send(Frame.Text(SekundJson.encodeToString(RelayMessage.serializer(), message)))
    }

    /**
     * Sends [message] (whose id is [id]) and waits for the device's reply to it. Returns null if no
     * reply came within [timeout] (or the device disconnected).
     */
    suspend fun request(id: String, message: RelayMessage, timeout: Duration): RelayMessage? {
        val reply = CompletableDeferred<RelayMessage>()
        waiting[id] = reply
        try {
            send(message)
            return withTimeoutOrNull(timeout) { reply.await() }
        } finally {
            waiting.remove(id)
        }
    }

    /** Called for every message from the device that answers an earlier request. */
    fun replyArrived(replyTo: String, message: RelayMessage) {
        waiting[replyTo]?.complete(message)
    }

    /** Closes the connection (e.g. a newer connection from the same device replaced this one). */
    suspend fun close(reason: String) {
        session.close(CloseReason(CloseReason.Codes.NORMAL, reason))
    }
}
