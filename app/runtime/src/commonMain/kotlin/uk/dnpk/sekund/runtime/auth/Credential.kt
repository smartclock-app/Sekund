package uk.dnpk.sekund.runtime.auth

import kotlinx.serialization.Serializable

/**
 * The secret part of a connected account. Only ever stored in the [SecretStore][uk.dnpk.sekund.runtime.auth.secrets.SecretStore]
 * (encrypted), never in config.json or accounts.json, and never handed to plugins.
 *
 * Which fields are set depends on how the account signed in: an API key account only has [apiKey];
 * an OAuth account has the tokens (and the user's own client id/secret if they brought one).
 *
 * @property expiresAtEpochSeconds when [accessToken] stops working, or null if it doesn't expire.
 */
@Serializable
data class Credential(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val apiKey: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    /** A custom sign-in's session values (e.g. Amazon's cookies and CSRF token). */
    val extra: Map<String, String> = emptyMap(),
)
