package uk.dnpk.sekund.sdk.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Instant

/**
 * Sign-in code for a service that standard OAuth or API keys can't describe, such as Amazon (Alexa),
 * which swaps a refresh token for browser cookies plus a CSRF token.
 *
 * The provider's `*.auth.json` uses `{"type": "custom", "handler": "<name>"}` and the plugin registers the
 * handler under that name with `registrar.authHandler(name, handler)`. Everything else stays shared with
 * the other sign-in methods: the encrypted storage, one refresh at a time, retrying once after a
 * `401 Unauthorized`, and the "reconnect your account" alert.
 *
 * The user connects by pasting a secret (e.g. the Amazon refresh token); [refresh] turns it into a session.
 */
interface CustomAuthHandler {
    /**
     * Makes a fresh session from [session]'s secret. Called when connecting, when there's no session yet,
     * when it's about to expire, and after a `401`.
     *
     * @param http a plain client without credentials.
     * @param fields the account's fields from the auth definition (e.g. the Amazon site).
     * @throws CustomAuthRejectedException if the secret itself no longer works (the user must reconnect).
     *   Any other exception counts as a temporary failure (e.g. no network) and the account stays connected.
     */
    suspend fun refresh(http: HttpClient, fields: Map<String, String>, session: CustomSession): CustomSession

    /**
     * Puts [session] on [request] (cookies, headers...). Use `headers.set`, not `header(...)`: after a `401`
     * this runs again on the same request with the new session, and must replace the old values.
     */
    fun sign(request: HttpRequestBuilder, fields: Map<String, String>, session: CustomSession)
}

/**
 * A custom sign-in's stored state.
 *
 * @property secret what the user pasted when connecting. Long-lived.
 * @property values the current session, e.g. `cookie` and `csrf`. Empty until the first [CustomAuthHandler.refresh].
 * @property expiresAt when the session stops working, if known; it's refreshed shortly before.
 */
data class CustomSession(
    val secret: String,
    val values: Map<String, String> = emptyMap(),
    val expiresAt: Instant? = null,
)

/** Thrown by [CustomAuthHandler.refresh] when the service refuses the secret itself. */
class CustomAuthRejectedException(message: String) : Exception(message)
