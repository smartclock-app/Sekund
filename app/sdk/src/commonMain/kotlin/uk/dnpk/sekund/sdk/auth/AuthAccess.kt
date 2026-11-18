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
}

/** The plugin asked for an account of a provider it didn't declare in its manifest's `requires.auth`. */
class ProviderNotDeclaredException(provider: String, pluginId: String) :
    IllegalStateException("Plugin '$pluginId' didn't declare the auth provider '$provider' in its manifest")

/** The account can't be used right now: it doesn't exist, or the user has to reconnect it. */
class AccountUnavailableException(val accountId: String, reason: String) :
    IllegalStateException("Account '$accountId' can't be used: $reason")

/** A signed-in request went to a URL the provider doesn't allow credentials to be sent to. */
class CredentialHostException(url: String, provider: String) :
    IllegalStateException("Won't send '$provider' credentials to $url (not in the provider's baseUrls)")
