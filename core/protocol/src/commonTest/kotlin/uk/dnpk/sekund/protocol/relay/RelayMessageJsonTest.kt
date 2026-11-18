package uk.dnpk.sekund.protocol.relay

import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What relay messages look like on the wire (these are the examples in docs/api/relay-protocol.md). */
class RelayMessageJsonTest {
    private fun encode(message: RelayMessage) = SekundJson.encodeToString(RelayMessage.serializer(), message)

    private fun decode(text: String) = SekundJson.decodeFromString(RelayMessage.serializer(), text)

    @Test
    fun `the message kind is written as type`() {
        val text = encode(RelayMessage.RpcRequest(id = "r1", method = "GET", path = "/v1/config"))

        assertTrue(text.startsWith("""{"type":"rpc.request","id":"r1","method":"GET","path":"/v1/config""""), text)
    }

    @Test
    fun `messages read back the same`() {
        val messages = listOf(
            RelayMessage.Hello("dev_1", null, "Kitchen", "1.0.0", 1),
            RelayMessage.ClaimCode("ABCD-1234", "2026-11-18T12:00:00Z", "https://example.org"),
            RelayMessage.Welcome(owner = "sam", oauthProviders = listOf("google"), webhooks = true),
            RelayMessage.RpcResponse(replyTo = "r1", status = 200, headers = mapOf("ETag" to "\"abc\""), body = "{}"),
            RelayMessage.EventsStart,
            RelayMessage.Event(DeviceEvent.ConfigChanged("abc")),
            RelayMessage.WebhookEvent(id = "w1", key = "cal/calendar", headers = mapOf("x-goog-resource-state" to "exists")),
            RelayMessage.Ack("w1"),
        )
        for (message in messages) {
            assertEquals(message, decode(encode(message)))
        }
    }

    @Test
    fun `a device event inside a relay message keeps its own kind`() {
        val text = encode(RelayMessage.Event(DeviceEvent.AlertCleared("weather")))

        assertEquals("""{"type":"event","event":{"kind":"alertCleared","owner":"weather"}}""", text)
    }

    @Test
    fun `unknown fields from a newer server are ignored`() {
        val message = decode("""{"type":"ack","replyTo":"x","somethingNew":true}""")

        assertEquals(RelayMessage.Ack("x"), message)
    }
}
