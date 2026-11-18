package uk.dnpk.sekund.sdk.auth

/**
 * Whether a connected account can currently be used. Plugins watch this (via `context.auth.state(...)`)
 * to show a "connect your account" card instead of an error.
 *
 * Plugins don't need to do anything about [NeedsReauth]: Sekund shows one central alert asking the user
 * to reconnect, and the account becomes [Valid] again once they have.
 */
sealed class AuthState {
    /** Ready to use. */
    data object Valid : AuthState()

    /** Getting a fresh access token. Requests wait for it automatically. */
    data object Refreshing : AuthState()

    /** The service no longer accepts the credential (revoked, expired, password changed...). */
    data class NeedsReauth(val reason: String) : AuthState()

    /** No account with that id (it was removed, or none was chosen yet). */
    data object NotFound : AuthState()
}
