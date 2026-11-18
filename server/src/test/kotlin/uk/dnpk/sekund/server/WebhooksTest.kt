package uk.dnpk.sekund.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhooksTest {
    private val config = ServerConfig(publicUrl = "https://sekund.example.org")

    @Test
    fun `a device gets a stable public URL and what is posted to it`() = testApplication {
        val server = newTestServer(config)
        val client = startServer(server)
        val (device, _) = claimedDevice(client, client.signUp("sam"))

        device.send(RelayMessage.WebhookRegister(id = "1", key = "google-cal/calendar"))
        val registered = device.expect<RelayMessage.WebhookRegistered>()
        assertEquals("1", registered.replyTo)
        assertTrue(registered.url.startsWith("https://sekund.example.org/hooks/"))
        device.send(RelayMessage.WebhookRegister(id = "2", key = "google-cal/calendar"))
        assertEquals(registered.url, device.expect<RelayMessage.WebhookRegistered>().url)

        val posted = client.post(registered.url.removePrefix("https://sekund.example.org")) {
            header("X-Goog-Resource-State", "exists")
            setBody("hello")
        }
        assertEquals(HttpStatusCode.OK, posted.status)

        val event = device.expect<RelayMessage.WebhookEvent>()
        assertEquals("google-cal/calendar", event.key)
        assertEquals("exists", event.headers["x-goog-resource-state"])
        assertEquals("hello", event.body)
        device.send(RelayMessage.Ack(event.id))
    }

    @Test
    fun `events for an offline device wait until it reconnects`() = testApplication {
        val server = newTestServer(config)
        val client = startServer(server)
        val (device, token) = claimedDevice(client, client.signUp("sam"))
        device.send(RelayMessage.WebhookRegister(id = "1", key = "a/b"))
        val path = device.expect<RelayMessage.WebhookRegistered>().url.removePrefix("https://sekund.example.org")
        device.close()

        client.post(path) { setBody("while you were out") }

        val back = FakeDevice.connect(client, token = token)
        back.expect<RelayMessage.Welcome>()
        val event = back.expect<RelayMessage.WebhookEvent>()
        assertEquals("while you were out", event.body)
        back.send(RelayMessage.Ack(event.id))
    }

    @Test
    fun `unknown hooks are 404 and webhooks need a public URL`() = testApplication {
        val server = newTestServer(ServerConfig())
        val client = startServer(server)
        val (device, _) = claimedDevice(client, client.signUp("sam"))

        assertEquals(HttpStatusCode.NotFound, client.post("/hooks/nope") { setBody("x") }.status)
        device.send(RelayMessage.WebhookRegister(id = "1", key = "a/b"))
        device.expect<RelayMessage.Error>()
    }
}
