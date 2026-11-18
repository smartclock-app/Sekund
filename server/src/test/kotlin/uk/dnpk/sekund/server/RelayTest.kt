package uk.dnpk.sekund.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.protocol.server.ClaimDevice
import uk.dnpk.sekund.protocol.server.ServerDevice
import uk.dnpk.sekund.protocol.server.SessionStarted
import uk.dnpk.sekund.server.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RelayTest {
    private val config = ServerConfig(publicUrl = "https://sekund.example.org", allowSignups = true)

    /** Connects a new device and claims it as [owner]; returns the device and its token. */
    private suspend fun claimedDevice(client: HttpClient, owner: SessionStarted, deviceId: String = "dev_test"): Pair<FakeDevice, String> {
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

    @Test
    fun `an unclaimed device shows a code that its new owner enters`() = testApplication {
        val client = startServer(newTestServer(config))
        val sam = client.signUp("sam")

        val device = FakeDevice.connect(client)
        val code = device.expect<RelayMessage.ClaimCode>()
        assertEquals("https://sekund.example.org", code.serverUrl)
        client.post("/v1/devices/claim") {
            bearerAuth(sam.token)
            contentType(ContentType.Application.Json)
            setBody(ClaimDevice(code.code))
        }

        assertTrue(device.expect<RelayMessage.Claimed>().deviceToken.isNotBlank())
        val welcome = device.expect<RelayMessage.Welcome>()
        assertEquals("sam", welcome.owner)
        assertTrue(welcome.webhooks)
        val listed = client.get("/v1/devices") { bearerAuth(sam.token) }.body<List<ServerDevice>>()
        assertEquals(listOf("dev_test"), listed.map { found -> found.id })
        assertTrue(listed.single().online)
    }

    @Test
    fun `a claimed device reconnects with its token, and a wrong token is refused`() = testApplication {
        val client = startServer(newTestServer(config))
        val sam = client.signUp("sam")
        val (_, token) = claimedDevice(client, sam)

        val again = FakeDevice.connect(client, token = token)
        assertEquals("sam", again.expect<RelayMessage.Welcome>().owner)

        val impostor = FakeDevice.connect(client, token = "guessed")
        assertTrue(impostor.expect<RelayMessage.Rejected>().forgetToken)

        // Without a token, a device that's already claimed can't get a claim code either.
        val noToken = FakeDevice.connect(client)
        val rejected = noToken.expect<RelayMessage.Rejected>()
        assertEquals(false, rejected.forgetToken)
    }

    @Test
    fun `device API requests are tunnelled to the device and back`() = testApplication {
        val client = startServer(newTestServer(config))
        val sam = client.signUp("sam")
        val (device, _) = claimedDevice(client, sam)

        coroutineScope {
            val answer = async {
                client.patch("/v1/devices/dev_test/v1/config?dryRun=true") {
                    bearerAuth(sam.token)
                    contentType(ContentType.Application.Json)
                    headers.append(HttpHeaders.IfMatch, "\"abc\"")
                    setBody("""{"clockFace":"big"}""")
                }
            }

            val request = device.expect<RelayMessage.RpcRequest>()
            assertEquals("PATCH", request.method)
            assertEquals("/v1/config", request.path)
            assertEquals("dryRun=true", request.query)
            assertEquals("\"abc\"", request.headers[HttpHeaders.IfMatch])
            assertEquals("""{"clockFace":"big"}""", request.body)
            assertNull(request.headers[HttpHeaders.Authorization]) // the server session never reaches the device
            device.send(RelayMessage.RpcResponse(request.id, 200, mapOf("ETag" to "\"def\"", "Content-Type" to "application/json"), """{"etag":"def"}"""))

            val response = answer.await()
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("\"def\"", response.headers[HttpHeaders.ETag])
            assertEquals("""{"etag":"def"}""", response.bodyAsText())
        }
    }

    @Test
    fun `only the owner can reach a device, and only while it's connected`() = testApplication {
        val client = startServer(newTestServer(config))
        val sam = client.signUp("sam")
        val alex = client.signUp("alex")
        val (device, _) = claimedDevice(client, sam)

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/devices/dev_test/v1/config") { bearerAuth(alex.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/devices/dev_test/v1/config").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/devices/dev_test/v1/pair") { bearerAuth(sam.token) }.status)

        device.send(RelayMessage.Ack("bye"))
        device.close()
        val offline = client.get("/v1/devices/dev_test/v1/config") { bearerAuth(sam.token) }
        assertEquals(HttpStatusCode.ServiceUnavailable, offline.status)
    }

    @Test
    fun `companions watching events make the device send them`() = testApplication {
        val client = startServer(newTestServer(config))
        val sam = client.signUp("sam")
        val (device, _) = claimedDevice(client, sam)

        val watcher = client.webSocketSession("/v1/devices/dev_test/v1/events?token=${sam.token}")
        device.expect<RelayMessage.EventsStart>()
        device.send(RelayMessage.Event(DeviceEvent.ConfigChanged("abc")))

        val frame = withTimeout(5.seconds) { watcher.incoming.receive() } as Frame.Text
        assertEquals(DeviceEvent.ConfigChanged("abc"), SekundJson.decodeFromString(DeviceEvent.serializer(), frame.readText()))

        watcher.close()
        device.expect<RelayMessage.EventsStop>()
    }
}
