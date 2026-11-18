package uk.dnpk.sekund.runtime.relay

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.sdk.context.WebhookCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayWebhooksTest {
    /** Plays the server: every hook gets the URL `https://server/hooks/<key>`. */
    private val server = FakeRelayLink { message ->
        if (message is RelayMessage.WebhookRegister) {
            RelayMessage.WebhookRegistered(message.id, message.key, "https://server/hooks/${message.key}")
        } else {
            null
        }
    }

    @Test
    fun `an instance's hook gets a URL once a server that relays webhooks is connected`() = runTest {
        val webhooks = RelayWebhooks()
        val calendar = webhooks.forInstance("cal")

        // No server yet: no URL.
        assertNull(calendar.url("events").first())

        webhooks.connected(server, RelayMessage.Welcome(owner = "sam", webhooks = true))

        assertEquals("https://server/hooks/cal/events", calendar.url("events").first())
        assertEquals(RelayMessage.WebhookRegister::class, server.sent.single()::class)
    }

    @Test
    fun `a server without webhooks never gives URLs`() = runTest {
        val webhooks = RelayWebhooks()
        val calendar = webhooks.forInstance("cal")
        assertNull(calendar.url("events").first())

        webhooks.connected(server, RelayMessage.Welcome(owner = "sam", webhooks = false))

        assertNull(calendar.url("events").first())
        assertEquals(emptyList<RelayMessage>(), server.sent)
    }

    @Test
    fun `calls reach only their own hook, repeats are ignored, and every call is acknowledged`() = runTest {
        val webhooks = RelayWebhooks()
        val received = mutableListOf<WebhookCall>()
        val listening = launch {
            webhooks.forInstance("cal").calls("events").collect { call -> received.add(call) }
        }
        yield() // let the listener start

        val call = RelayMessage.WebhookEvent(id = "w1", key = "cal/events", headers = mapOf("x-goog-resource-state" to "exists"))
        webhooks.handle(server, call)
        webhooks.handle(server, call) // delivered twice (at-least-once)
        webhooks.handle(server, RelayMessage.WebhookEvent(id = "w2", key = "other/events"))
        yield()

        assertEquals(listOf(WebhookCall(mapOf("x-goog-resource-state" to "exists"), "")), received)
        assertEquals(listOf<RelayMessage>(RelayMessage.Ack("w1"), RelayMessage.Ack("w1"), RelayMessage.Ack("w2")), server.sent)
        listening.cancel()
    }
}
