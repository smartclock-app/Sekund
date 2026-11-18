package uk.dnpk.sekund.runtime.relay

import uk.dnpk.sekund.protocol.relay.RelayMessage
import kotlin.time.Duration

/**
 * A [RelayLink] for tests: records what's sent, and answers requests with [answer] (the test's
 * stand-in for the server).
 */
class FakeRelayLink(
    private val answer: (RelayMessage) -> RelayMessage? = { null },
) : RelayLink {
    val sent = mutableListOf<RelayMessage>()

    override suspend fun send(message: RelayMessage) {
        sent.add(message)
    }

    override suspend fun request(id: String, message: RelayMessage, timeout: Duration): RelayMessage? {
        sent.add(message)
        return answer(message)
    }
}
