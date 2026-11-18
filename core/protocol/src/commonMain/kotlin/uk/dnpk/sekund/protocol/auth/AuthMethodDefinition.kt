package uk.dnpk.sekund.protocol.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One way of signing in to a provider. In JSON the kind is written as `"type"`, e.g. `{"type": "apiKey"}`.
 *
 * | type                       | what the user does                                             | example           |
 * |----------------------------|----------------------------------------------------------------|-------------------|
 * | `apiKey`                   | pastes a key                                                   | Immich, OpenWeather |
 * | `bearer`                   | pastes a long-lived token                                      | Home Assistant    |
 * | `oauth2.deviceCode`        | scans a QR / opens a URL on their phone and types a short code | Trakt             |
 * | `oauth2.authorizationCode` | signs in through a browser (via the companion app)             | Google            |
 * | `custom`                   | something only Kotlin can do, handled by a plugin              | Amazon (Alexa)    |
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class AuthMethodDefinition {

    /**
     * A key the user pastes in. Used as `{api_key}` in [AuthProviderDefinition.inject].
     *
     * @property label what to call the key in the UI, e.g. "API key".
     */
    @Serializable
    @SerialName("apiKey")
    data class ApiKey(
        val label: String = "API key",
        val help: String? = null,
    ) : AuthMethodDefinition()

    /**
     * A long-lived access token the user pastes in (e.g. a Home Assistant "long-lived access token").
     * Used as `{access_token}`, so it works with the default `Authorization: Bearer {access_token}`.
     */
    @Serializable
    @SerialName("bearer")
    data class Bearer(
        val label: String = "Access token",
        val help: String? = null,
    ) : AuthMethodDefinition()

    /**
     * OAuth 2.0 device authorization (RFC 8628): the device shows a code, the user approves on their phone.
     * The preferred method for a wall display.
     *
     * @property pollStatusMap for services that answer polls with HTTP status codes instead of the standard
     *   `{"error": "authorization_pending"}` body, e.g. Trakt: `{"400": "pending", "429": "slowDown", ...}`.
     * @property deviceCodeParam the name of the device code in poll requests. Standard is `device_code`;
     *   Trakt uses `code`.
     */
    @Serializable
    @SerialName("oauth2.deviceCode")
    data class OAuthDeviceCode(
        val deviceCodeUrl: String,
        val tokenUrl: String,
        val revokeUrl: String? = null,
        val scopes: List<String> = emptyList(),
        val scopeSeparator: String = " ",
        val client: OAuthClient = OAuthClient(),
        val clientAuth: ClientAuth = ClientAuth.Body,
        val bodyEncoding: BodyEncoding = BodyEncoding.Form,
        val refresh: RefreshSettings = RefreshSettings(),
        val pollStatusMap: Map<String, DevicePollStatus> = emptyMap(),
        val deviceCodeParam: String = "device_code",
    ) : AuthMethodDefinition()

    /**
     * OAuth 2.0 authorization code flow, with PKCE by default. Needs a browser, so on a wall display it's
     * completed through the companion app (or, later, the optional server).
     *
     * @property extraAuthorizeParams added to the authorize URL, e.g. Google's `access_type=offline`.
     * @property companion the client profile used when the companion app does the browser part.
     */
    @Serializable
    @SerialName("oauth2.authorizationCode")
    data class OAuthAuthorizationCode(
        val authorizeUrl: String,
        val tokenUrl: String,
        val revokeUrl: String? = null,
        val scopes: List<String> = emptyList(),
        val scopeSeparator: String = " ",
        val pkce: Boolean = true,
        val extraAuthorizeParams: Map<String, String> = emptyMap(),
        val client: OAuthClient = OAuthClient(),
        val clientAuth: ClientAuth = ClientAuth.Body,
        val bodyEncoding: BodyEncoding = BodyEncoding.Form,
        val refresh: RefreshSettings = RefreshSettings(),
        val companion: CompanionClient? = null,
    ) : AuthMethodDefinition()

    /**
     * Handled by Kotlin code that a plugin registers under [handler] (e.g. Alexa's cookie exchange).
     * The rest (storage, refresh locking, alerts, UI) is still shared.
     */
    @Serializable
    @SerialName("custom")
    data class Custom(
        val handler: String,
    ) : AuthMethodDefinition()
}

/**
 * Which OAuth client (app registration) to use.
 *
 * @property id/secret only for [ClientMode.Fixed]. May use placeholders, e.g. Home Assistant's
 *   `"id": "{instance_url}"`.
 */
@Serializable
data class OAuthClient(
    val mode: ClientMode = ClientMode.Byo,
    val id: String? = null,
    val secret: String? = null,
)

@Serializable
enum class ClientMode {
    /** "Bring your own": the user enters their own client id (and secret) when connecting. */
    @SerialName("byo")
    Byo,

    /** The client is written in the definition itself. */
    @SerialName("fixed")
    Fixed,

    /** The user's own client, or the optional server's managed one once a server is set up. */
    @SerialName("byoOrServer")
    ByoOrServer,
}

/** How the client id/secret are sent to the token endpoint. */
@Serializable
enum class ClientAuth {
    /** As `client_id` / `client_secret` fields in the request body. */
    @SerialName("body")
    Body,

    /** As an `Authorization: Basic` header. */
    @SerialName("basic")
    Basic,

    /** Only `client_id` in the body (public clients with PKCE). */
    @SerialName("none")
    None,
}

/** How requests to the token endpoints are encoded. Standard OAuth uses a form; some services (Trakt) want JSON. */
@Serializable
enum class BodyEncoding {
    @SerialName("form")
    Form,

    @SerialName("json")
    Json,
}

/**
 * @property rotateRefreshToken true if the service issues a new refresh token on every refresh. Sekund
 *   always stores a new refresh token when one is returned; this flag only documents the behaviour.
 * @property skewSeconds refresh this many seconds *before* the token expires, so requests don't race expiry.
 * @property url where to send refresh requests, if not the method's `tokenUrl` (Trakt uses a different one).
 * @property extraParams added to every refresh request, e.g. Trakt's `redirect_uri`.
 */
@Serializable
data class RefreshSettings(
    val rotateRefreshToken: Boolean = false,
    val skewSeconds: Int = 60,
    val url: String? = null,
    val extraParams: Map<String, String> = emptyMap(),
)

/** What a device-code poll answer means. */
@Serializable
enum class DevicePollStatus {
    /** The user hasn't approved yet; keep polling. */
    @SerialName("pending")
    Pending,

    /** Polling too fast; wait longer between polls. */
    @SerialName("slowDown")
    SlowDown,

    /** The code expired; start again. */
    @SerialName("expired")
    Expired,

    /** The user said no. */
    @SerialName("denied")
    Denied,

    /** The code is unknown or was already used. */
    @SerialName("invalid")
    Invalid,
}

/**
 * The OAuth client the companion app uses when it completes an authorization-code sign-in for the device.
 *
 * @property clientId a public client id registered for the companion.
 * @property redirectUri where the provider sends the browser back to, e.g. `sekundcompanion://oauth`.
 */
@Serializable
data class CompanionClient(
    val clientId: String,
    val redirectUri: String,
)
