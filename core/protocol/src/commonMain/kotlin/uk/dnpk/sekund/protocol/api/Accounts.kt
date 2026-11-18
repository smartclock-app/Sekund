package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A connected account (`GET /v1/accounts`). Never includes credentials.
 *
 * @property state `valid`, `refreshing`, `needsReauth` or `notFound`; [reason] explains `needsReauth`.
 */
@Serializable
data class AccountSummary(
    val id: String,
    val provider: String,
    val label: String,
    val state: String,
    val reason: String? = null,
)

/**
 * `POST /v1/accounts`: connect an account of [provider].
 *
 * @property method the index into the provider's `methods`. Defaults to the first (most preferred).
 * @property fields the provider's account fields, e.g. `instance_url`.
 * @property key for `apiKey`/`bearer` methods: the key or token.
 * @property clientId/clientSecret the person's own OAuth client, for providers that need one.
 * @property redirectUri for `oauth2.authorizationCode`: where the provider sends the browser back to
 *   (the companion's redirect).
 * @property reconnect the id of an existing account to update instead of adding one.
 */
@Serializable
data class ConnectAccount(
    val provider: String,
    val method: Int = 0,
    val fields: Map<String, String> = emptyMap(),
    val key: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val redirectUri: String? = null,
    val reconnect: String? = null,
)

/** Where a sign-in has got to. Returned by `POST /v1/accounts` and `GET /v1/accounts/flows/{flowId}`. */
@Serializable
sealed class AccountFlow {
    /** Done: the account is connected. */
    @Serializable
    @SerialName("connected")
    data class Connected(val account: AccountSummary) : AccountFlow()

    /**
     * Device code: the person opens [verificationUrl] (or scans [qrUrl]) and enters [userCode].
     * Poll `GET /v1/accounts/flows/{flowId}` or watch the events stream for `accountStateChanged`.
     */
    @Serializable
    @SerialName("deviceCode")
    data class DeviceCode(
        val flowId: String,
        val userCode: String,
        val verificationUrl: String,
        val qrUrl: String,
    ) : AccountFlow()

    /**
     * Authorization code (companion-assisted): open [authorizeUrl] in a browser; when it redirects to
     * [redirectUri], send its `code` and `state` to `POST /v1/accounts/flows/{flowId}/code`.
     */
    @Serializable
    @SerialName("authorizationCode")
    data class AuthorizationCode(
        val flowId: String,
        val authorizeUrl: String,
        val redirectUri: String,
        val expiresAt: String,
    ) : AccountFlow()

    /** The sign-in didn't work. */
    @Serializable
    @SerialName("failed")
    data class Failed(val reason: String) : AccountFlow()
}

/** `POST /v1/accounts/flows/{flowId}/code`: what the provider's redirect brought back. */
@Serializable
data class AuthorizationCodeResult(
    val code: String,
    val state: String,
)

/**
 * `PUT /v1/accounts/{id}/credential`: the last resort, for providers a companion signs in to itself.
 * Only the fields that apply need to be sent.
 */
@Serializable
data class CredentialUpload(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresInSeconds: Long? = null,
    val apiKey: String? = null,
)
