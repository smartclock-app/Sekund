package uk.dnpk.sekund.runtime.auth

import kotlinx.serialization.Serializable

/**
 * One connected account, as saved in `<data>/accounts.json`. Holds nothing secret, so the file is safe
 * to read; the credential is kept in the secret store under [secretKey].
 *
 * Plugin settings point at an account by its [id] (an `AccountRef`), so reconnecting an account keeps
 * the same id and every widget using it carries on working.
 *
 * @property label what the UI shows, e.g. the account's email address.
 * @property methodIndex which entry of the provider's `methods` it signed in with.
 * @property fields the provider's extra fields, e.g. `instance_url` for a self-hosted server.
 * @property needsReauth why the user must reconnect, or null if the account works. Saved so the
 *   reminder survives a restart.
 */
@Serializable
data class ConnectedAccount(
    val id: String,
    val provider: String,
    val label: String,
    val methodIndex: Int,
    val fields: Map<String, String> = emptyMap(),
    val needsReauth: String? = null,
) {
    /** Where this account's [Credential] lives in the secret store. */
    val secretKey: String
        get() = "account:$id"
}
