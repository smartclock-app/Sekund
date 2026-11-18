package uk.dnpk.sekund.protocol.relay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import uk.dnpk.sekund.protocol.api.DeviceEvent

/**
 * One message on the relay WebSocket between a device and the optional Sekund server
 * (`wss://server/v1/device/connect`). Written up in docs/api/relay-protocol.md.
 *
 * Each message is one JSON text frame, with its kind in `"type"`, e.g.
 * `{"type": "rpc.request", "id": "r1", "method": "GET", "path": "/v1/config"}`.
 *
 * Requests carry an [id]; the answer to a request carries the same value in `replyTo`. Messages that
 * must not be lost (webhook events, delivered credentials) are sent again after a reconnect until the
 * device answers with [Ack] — so a device may see the same one twice, and should ignore repeats by id.
 *
 * Both sides must ignore message types they don't know (newer versions may add some).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class RelayMessage {

    // ---- Connecting and claiming ----------------------------------------------------------------

    /**
     * Device → server, always the first message.
     *
     * @property deviceToken the token from [Claimed], or null if this device hasn't been claimed yet.
     */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val deviceId: String,
        val deviceToken: String? = null,
        val name: String,
        val appVersion: String,
        val apiVersion: Int,
    ) : RelayMessage()

    /**
     * Server → device: nobody owns this device yet. Show [code] on screen; someone signed in to the
     * server enters it (in the companion, or at [claimUrl]) to make the device theirs.
     */
    @Serializable
    @SerialName("claim.code")
    data class ClaimCode(
        val code: String,
        val expiresAt: String,
        val claimUrl: String,
    ) : RelayMessage()

    /**
     * Server → device: someone claimed this device. Keep [deviceToken] secret and send it in every
     * later [Hello]. A [Welcome] follows straight away.
     */
    @Serializable
    @SerialName("claimed")
    data class Claimed(val deviceToken: String) : RelayMessage()

    /**
     * Server → device: the connection is authenticated and ready.
     *
     * @property owner the username of the person the device belongs to.
     * @property oauthProviders auth provider ids the server can sign in to for the device (see [OAuthStart]).
     * @property webhooks whether the server relays webhooks (it needs a public URL for that).
     */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val owner: String,
        val oauthProviders: List<String> = emptyList(),
        val webhooks: Boolean = false,
    ) : RelayMessage()

    /**
     * Server → device, then the server closes the connection.
     *
     * @property forgetToken true if the device token is no longer valid (the owner removed the device):
     *   the device should delete it and connect again without one to get a new claim code.
     */
    @Serializable
    @SerialName("rejected")
    data class Rejected(
        val reason: String,
        val forgetToken: Boolean = false,
    ) : RelayMessage()

    // ---- The RPC tunnel: device API requests from a companion, through the server ---------------

    /**
     * Server → device: a companion called `https://server/v1/devices/{deviceId}{path}`. The device
     * handles it exactly like the same request on its LAN API and answers with [RpcResponse].
     *
     * @property query the raw query string without `?`, e.g. `tail=50`.
     * @property headers only the ones that matter to the device API: Content-Type, If-Match, Accept.
     * @property body the request body as text (the device API only uses JSON text).
     */
    @Serializable
    @SerialName("rpc.request")
    data class RpcRequest(
        val id: String,
        val method: String,
        val path: String,
        val query: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    ) : RelayMessage()

    /**
     * Device → server: the answer to [RpcRequest] [replyTo].
     *
     * @property headers only the ones that matter: Content-Type and ETag.
     */
    @Serializable
    @SerialName("rpc.response")
    data class RpcResponse(
        val replyTo: String,
        val status: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    ) : RelayMessage()

    /** Server → device: a companion is watching the events stream; send [Event]s until [EventsStop]. */
    @Serializable
    @SerialName("events.start")
    data object EventsStart : RelayMessage()

    /** Server → device: nobody is watching any more; stop sending [Event]s. */
    @Serializable
    @SerialName("events.stop")
    data object EventsStop : RelayMessage()

    /** Device → server: one event from the device's events stream, passed on to every watching companion. */
    @Serializable
    @SerialName("event")
    data class Event(val event: DeviceEvent) : RelayMessage()

    // ---- Webhooks -------------------------------------------------------------------------------

    /**
     * Device → server: "give me a public URL for [key]" (e.g. `"google-cal/calendar"`: an instance id
     * and the plugin's name for the hook). Asking again for the same key gives the same URL, so URLs
     * registered with other services keep working across restarts.
     */
    @Serializable
    @SerialName("webhook.register")
    data class WebhookRegister(
        val id: String,
        val key: String,
    ) : RelayMessage()

    /** Server → device: anything POSTed to [url] will arrive as a [WebhookEvent] for [key]. */
    @Serializable
    @SerialName("webhook.registered")
    data class WebhookRegistered(
        val replyTo: String,
        val key: String,
        val url: String,
    ) : RelayMessage()

    /**
     * Server → device: someone POSTed to the hook for [key]. Answer with [Ack].
     *
     * @property headers the request's headers, names in lower case.
     */
    @Serializable
    @SerialName("webhook.event")
    data class WebhookEvent(
        val id: String,
        val key: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String = "",
    ) : RelayMessage()

    // ---- Signing in through the server (server-managed OAuth clients) ---------------------------

    /**
     * Device → server: start an authorization-code sign-in to [provider] with the server's own OAuth
     * client. The server answers [OAuthStarted], and later [CredentialDelivered] or [OAuthFailed].
     *
     * @property scopes and [extraAuthorizeParams] come from the device's auth provider definition.
     */
    @Serializable
    @SerialName("oauth.start")
    data class OAuthStart(
        val id: String,
        val provider: String,
        val scopes: List<String> = emptyList(),
        val scopeSeparator: String = " ",
        val extraAuthorizeParams: Map<String, String> = emptyMap(),
    ) : RelayMessage()

    /** Server → device: open [url] in any browser (e.g. on a phone) to sign in. */
    @Serializable
    @SerialName("oauth.started")
    data class OAuthStarted(
        val replyTo: String,
        val flowId: String,
        val url: String,
        val expiresAt: String,
    ) : RelayMessage()

    /**
     * Server → device: the sign-in [flowId] worked. The server doesn't keep these tokens. Answer with [Ack].
     */
    @Serializable
    @SerialName("credential.delivered")
    data class CredentialDelivered(
        val id: String,
        val flowId: String,
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresInSeconds: Long? = null,
    ) : RelayMessage()

    /** Server → device: the sign-in [flowId] didn't work (the person said no, or the provider refused). */
    @Serializable
    @SerialName("oauth.failed")
    data class OAuthFailed(
        val id: String,
        val flowId: String,
        val reason: String,
    ) : RelayMessage()

    /**
     * Device → server: refresh a token that came from the server's OAuth client (only the server knows
     * that client's secret). Answered by [TokenRefreshed].
     */
    @Serializable
    @SerialName("token.refresh")
    data class TokenRefresh(
        val id: String,
        val provider: String,
        val refreshToken: String,
    ) : RelayMessage()

    /** Server → device: the provider's token endpoint's answer to [TokenRefresh], passed on as it was. */
    @Serializable
    @SerialName("token.refreshed")
    data class TokenRefreshed(
        val replyTo: String,
        val status: Int,
        val body: String,
    ) : RelayMessage()

    // ---- General --------------------------------------------------------------------------------

    /** Either side: "I've handled message [replyTo]; don't send it again." */
    @Serializable
    @SerialName("ack")
    data class Ack(val replyTo: String) : RelayMessage()

    /** Either side: the request [replyTo] couldn't be handled. */
    @Serializable
    @SerialName("error")
    data class Error(
        val replyTo: String,
        val message: String,
    ) : RelayMessage()
}
