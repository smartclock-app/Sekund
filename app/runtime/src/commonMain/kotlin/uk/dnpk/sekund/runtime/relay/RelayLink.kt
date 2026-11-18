package uk.dnpk.sekund.runtime.relay

import uk.dnpk.sekund.protocol.relay.RelayMessage
import kotlin.time.Duration

/**
 * The open connection to the server, as seen by the parts of the app that use it (see [RelayFeature]).
 * Only valid until the connection closes; a new one is handed out after each reconnect.
 */
interface RelayLink {
    /** Sends [message]. Throws if the connection has closed meanwhile. */
    suspend fun send(message: RelayMessage)

    /**
     * Sends [message] (whose id is [id]) and waits for the server's answer to it. Returns null if no
     * answer came within [timeout] or the connection closed.
     */
    suspend fun request(id: String, message: RelayMessage, timeout: Duration): RelayMessage?
}

/**
 * Something that uses the server link, e.g. webhooks or signing in through the server. The relay
 * client tells each feature when the link comes and goes, and hands it the messages it doesn't handle
 * itself.
 */
interface RelayFeature {
    /** The device is connected and authenticated. Runs in the background; may use [link] straight away. */
    suspend fun connected(link: RelayLink, welcome: RelayMessage.Welcome)

    /** The connection closed (it may come back later; [connected] is called again then). */
    fun disconnected()

    /** A message from the server. Returns true if this feature handled it. */
    suspend fun handle(link: RelayLink, message: RelayMessage): Boolean
}
