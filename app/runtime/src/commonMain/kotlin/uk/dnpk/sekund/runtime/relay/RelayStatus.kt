package uk.dnpk.sekund.runtime.relay

import kotlin.time.Duration
import kotlin.time.Instant

/** Where the link to the optional Sekund server has got to. Shown in settings. */
sealed class RelayStatus {
    /** No server is set up. */
    data object Off : RelayStatus()

    data class Connecting(val serverUrl: String) : RelayStatus()

    /** Nobody owns this device on the server yet: someone has to enter [code] in the companion. */
    data class WaitingToBeClaimed(val code: String, val serverUrl: String, val expiresAt: Instant) : RelayStatus()

    /**
     * Connected and working.
     *
     * @property owner the username the device belongs to on the server.
     * @property oauthProviders auth providers the server can sign in to for this device.
     * @property webhooks whether the server relays webhooks.
     */
    data class Connected(
        val owner: String,
        val oauthProviders: List<String>,
        val webhooks: Boolean,
    ) : RelayStatus()

    /** The server refused the device (e.g. someone else already owns it). Retried every [retryIn]. */
    data class Refused(val reason: String, val retryIn: Duration) : RelayStatus()

    /** The server can't be reached right now; trying again in [retryIn]. */
    data class Offline(val reason: String, val retryIn: Duration) : RelayStatus()
}
