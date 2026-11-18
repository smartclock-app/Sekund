package uk.dnpk.sekund.sdk.auth

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import uk.dnpk.sekund.sdk.settings.AccountRef

/**
 * Signed-in HTTP access for a plugin instance (`context.auth`). Plugins never see tokens or keys.
 *
 * Typical use, with an `account(...)` setting:
 * ```kotlin
 * val account = context.settings.account ?: return Loadable.Failed("Pick a Trakt account in settings")
 * val trakt = context.auth.client(account)
 * val shows = trakt.get("https://api.trakt.tv/users/me/watchlist/shows").bodyAsText()
 * ```
 *
 * The client attaches the account's credential to each request, refreshes it shortly before it expires,
 * and on a `401 Unauthorized` refreshes once and retries. If two requests need a refresh at the same time,
 * only one refresh happens. New tokens are saved straight away.
 *
 * A plugin may only use accounts of providers it lists in its manifest (`requires.auth`).
 */
interface AuthAccess {
    /** The account's current state, updated live. */
    fun state(account: AccountRef): Flow<AuthState>

    /**
     * An HTTP client that signs requests as [account]. Get it once (e.g. when the instance is created)
     * and reuse it.
     *
     * Requests through it fail with [AccountUnavailableException] if the account is missing or needs
     * reconnecting, and with [CredentialHostException] if the URL isn't one of the provider's `baseUrls`.
     *
     * @throws ProviderNotDeclaredException if the plugin's manifest doesn't require [AccountRef.provider].
     */
    fun client(account: AccountRef): HttpClient

    /**
     * The account's non-secret details from its provider definition's `fields`, e.g. a self-hosted
     * server's `instance_url` or Amazon's `domain`. Empty if the account doesn't exist (or isn't the
     * plugin's to use). Never contains credentials.
     */
    fun fields(account: AccountRef): Map<String, String>

    /**
     * The account's token, for the rare connection that carries it *inside* the connection rather than on
     * HTTP requests. Home Assistant's WebSocket is the example: its first message must contain the token.
     * Use [client] for everything else; it never shows you the token.
     *
     * Only works for providers whose definition says `"connectionToken": true`. [url] is where the token
     * will be sent; it must be one of the provider's `baseUrls` (`ws://` and `wss://` count as `http://`
     * and `https://`). Ask again for each new connection: the token may have been refreshed.
     *
     * @throws ProviderNotDeclaredException if the plugin's manifest doesn't require [AccountRef.provider].
     * @throws ConnectionTokenNotAllowedException if the provider doesn't allow it, or has no single token.
     * @throws CredentialHostException if [url] isn't one of the provider's `baseUrls`.
     * @throws AccountUnavailableException if the account is missing or needs reconnecting.
     */
    suspend fun connectionToken(account: AccountRef, url: String): String
}

/** The plugin asked for an account of a provider it didn't declare in its manifest's `requires.auth`. */
class ProviderNotDeclaredException(provider: String, pluginId: String) :
    IllegalStateException("Plugin '$pluginId' didn't declare the auth provider '$provider' in its manifest")

/** The provider doesn't hand out tokens for connections (see [AuthAccess.connectionToken]). */
class ConnectionTokenNotAllowedException(provider: String, reason: String) :
    IllegalStateException("Can't use a '$provider' token for a connection: $reason")

/** The account can't be used right now: it doesn't exist, or the user has to reconnect it. */
class AccountUnavailableException(val accountId: String, reason: String) :
    IllegalStateException("Account '$accountId' can't be used: $reason")

/** A signed-in request went to a URL the provider doesn't allow credentials to be sent to. */
class CredentialHostException(url: String, provider: String) :
    IllegalStateException("Won't send '$provider' credentials to $url (not in the provider's baseUrls)")
