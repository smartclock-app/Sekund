package uk.dnpk.sekund.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import uk.dnpk.sekund.protocol.server.ClaimDevice
import uk.dnpk.sekund.protocol.server.SessionStarted
import kotlin.test.assertEquals
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.relay.RelayMessage
import kotlin.time.Duration.Companion.seconds

/** Plays the part of a Sekund device on the relay WebSocket, for server tests. */
class FakeDevice(private val session: DefaultClientWebSocketSession) {
    suspend fun send(message: RelayMessage) {
        session.send(Frame.Text(SekundJson.encodeToString(RelayMessage.serializer(), message)))
    }

    /** The next message from the server (failing the test if none comes within 5 seconds). */
    suspend fun receive(): RelayMessage = withTimeout(5.seconds) {
        while (true) {
            val frame = session.incoming.receive()
            if (frame is Frame.Text) return@withTimeout SekundJson.decodeFromString(RelayMessage.serializer(), frame.readText())
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /** The next message, which must be a [T]. */
    suspend inline fun <reified T : RelayMessage> expect(): T {
        val message = receive()
        if (message !is T) throw AssertionError("Expected ${T::class.simpleName} but got $message")
        return message
    }

    suspend fun close() {
        session.close()
    }

    companion object {
        /** Connects and says hello. */
        suspend fun connect(client: HttpClient, deviceId: String = "dev_test", token: String? = null): FakeDevice {
            val device = FakeDevice(client.webSocketSession("/v1/device/connect"))
            device.send(RelayMessage.Hello(deviceId = deviceId, deviceToken = token, name = "Kitchen", appVersion = "1.0.0", apiVersion = 1))
            return device
        }
    }
}

/** Connects a new device and claims it as [owner]; returns the device and its token. */
suspend fun claimedDevice(client: HttpClient, owner: SessionStarted, deviceId: String = "dev_test"): Pair<FakeDevice, String> {
    val device = FakeDevice.connect(client, deviceId)
    val code = device.expect<RelayMessage.ClaimCode>()
    val claimed = client.post("/v1/devices/claim") {
        bearerAuth(owner.token)
        contentType(ContentType.Application.Json)
        setBody(ClaimDevice(code.code.lowercase().replace("-", " "))) // people type codes sloppily
    }
    assertEquals(HttpStatusCode.Created, claimed.status)
    val token = device.expect<RelayMessage.Claimed>().deviceToken
    device.expect<RelayMessage.Welcome>()
    return Pair(device, token)
}
