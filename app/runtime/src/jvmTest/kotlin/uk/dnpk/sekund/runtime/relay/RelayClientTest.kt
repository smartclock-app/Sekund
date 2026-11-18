package uk.dnpk.sekund.runtime.relay

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.auth.InMemorySecretStore
import uk.dnpk.sekund.runtime.services.AlertCenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.application.install

/**
 * The device's side of the relay, against a scripted stand-in for the server: each test plays the
 * server's part by hand through [ServerSide].
 */
class RelayClientTest {
    /** The server's end of the device's connection, driven by the test. */
    private class ServerSide(private val session: DefaultWebSocketServerSession) {
        suspend fun send(message: RelayMessage) {
            session.send(Frame.Text(SekundJson.encodeToString(RelayMessage.serializer(), message)))
        }

        suspend fun close() {
            session.close()
        }

        suspend fun receive(): RelayMessage = withTimeout(5.seconds) {
            val frame = session.incoming.receive() as Frame.Text
            SekundJson.decodeFromString(RelayMessage.serializer(), frame.readText())
        }
    }

    private val secrets = InMemorySecretStore()
    private val alerts = AlertCenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Runs a test with a fake server; [script] gets each new device connection. */
    private fun withFakeServer(
        rpc: RpcHandler = RpcHandler { request -> RelayMessage.RpcResponse(request.id, 404) },
        events: MutableSharedFlow<DeviceEvent> = MutableSharedFlow(),
        script: suspend (connections: Channel<ServerSide>, client: RelayClient) -> Unit,
    ) = testApplication {
        val connections = Channel<ServerSide>(Channel.UNLIMITED)
        application {
            install(ServerWebSockets)
            routing {
                webSocket("/v1/device/connect") {
                    val finished = CompletableDeferred<Unit>()
                    connections.send(ServerSide(this))
                    // Keep the connection open until the test ends.
                    try {
                        finished.await()
                    } finally {
                        finished.cancel()
                    }
                }
            }
        }
        val http = createClient { install(WebSockets) }
        val client = RelayClient(
            deviceId = { "dev_kitchen" },
            deviceName = { "Kitchen" },
            appVersion = "1.2.3",
            secrets = secrets,
            rpc = rpc,
            events = { events },
            alerts = alerts,
            scope = scope,
            http = http,
        )
        try {
            client.follow(flowOf("http://localhost"))
            script(connections, client)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a new device shows the claim code, then keeps the token it's given`() = withFakeServer { connections, client ->
        val server = connections.receive()

        val hello = server.receive() as RelayMessage.Hello
        assertEquals("dev_kitchen", hello.deviceId)
        assertNull(hello.deviceToken)

        server.send(RelayMessage.ClaimCode("K7QM-3XRP", "2030-01-01T00:00:00Z", "https://sekund.example.org"))
        val waiting = withTimeout(5.seconds) { client.status.first { status -> status is RelayStatus.WaitingToBeClaimed } }
        assertEquals("K7QM-3XRP", (waiting as RelayStatus.WaitingToBeClaimed).code)
        assertTrue(alerts.alerts.value.getValue(RelayClient.ALERT_OWNER).title.contains("K7QM-3XRP"))

        server.send(RelayMessage.Claimed("device-token"))
        server.send(RelayMessage.Welcome(owner = "sam", oauthProviders = listOf("google"), webhooks = true))
        val connected = withTimeout(5.seconds) { client.status.first { status -> status is RelayStatus.Connected } }
        assertEquals("sam", (connected as RelayStatus.Connected).owner)
        assertEquals("device-token", secrets.read(RelayClient.TOKEN_KEY))
        assertNull(alerts.alerts.value[RelayClient.ALERT_OWNER])
    }

    @Test
    fun `a device that has a token sends it, and forgets it when told to`() = withFakeServer { connections, _ ->
        secrets.write(RelayClient.TOKEN_KEY, "old-token")
        val first = connections.receive()
        assertEquals("old-token", (first.receive() as RelayMessage.Hello).deviceToken)

        first.send(RelayMessage.Rejected("Removed", forgetToken = true))
        first.close()

        // It comes straight back without a token, to be claimed again.
        val second = withTimeout(10.seconds) { connections.receive() }
        assertNull((second.receive() as RelayMessage.Hello).deviceToken)
        assertNull(secrets.read(RelayClient.TOKEN_KEY))
    }

    @Test
    fun `requests from the server are answered by the device API`() = withFakeServer(
        rpc = RpcHandler { request -> RelayMessage.RpcResponse(request.id, 200, body = "you asked for ${request.path}") },
    ) { connections, _ ->
        secrets.write(RelayClient.TOKEN_KEY, "token")
        val server = connections.receive()
        server.receive() // hello
        server.send(RelayMessage.Welcome(owner = "sam"))

        server.send(RelayMessage.RpcRequest(id = "r1", method = "GET", path = "/v1/config"))

        val response = server.receive() as RelayMessage.RpcResponse
        assertEquals("r1", response.replyTo)
        assertEquals(200, response.status)
        assertEquals("you asked for /v1/config", response.body)
    }

    @Test
    fun `events are sent only between start and stop`() {
        val events = MutableSharedFlow<DeviceEvent>()
        withFakeServer(events = events) { connections, _ ->
            secrets.write(RelayClient.TOKEN_KEY, "token")
            val server = connections.receive()
            server.receive() // hello
            server.send(RelayMessage.Welcome(owner = "sam"))

            server.send(RelayMessage.EventsStart)
            withTimeout(5.seconds) { events.subscriptionCount.first { count -> count > 0 } }
            events.emit(DeviceEvent.ConfigChanged("abc"))
            assertEquals(RelayMessage.Event(DeviceEvent.ConfigChanged("abc")), server.receive())

            server.send(RelayMessage.EventsStop)
            withTimeout(5.seconds) { events.subscriptionCount.first { count -> count == 0 } }
        }
    }

    @Test
    fun `server addresses become WebSocket addresses`() {
        assertEquals("wss://sekund.example.org/v1/device/connect", RelayClient.socketUrlOf("https://sekund.example.org"))
        assertEquals("ws://192.168.1.5:8080/v1/device/connect", RelayClient.socketUrlOf("http://192.168.1.5:8080"))
        assertNull(RelayClient.socketUrlOf("sekund.example.org"))
    }
}
