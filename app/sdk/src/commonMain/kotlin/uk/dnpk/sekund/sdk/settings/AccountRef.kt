package uk.dnpk.sekund.sdk.settings

/**
 * Points at one connected account in the auth registry, e.g. "the Google account `acc_7f3k`".
 *
 * Settings only ever store this reference, never tokens or passwords, so config files and backups
 * are safe to share. The plugin hands the reference to the auth registry to get an authenticated
 * HTTP client.
 */
data class AccountRef(
    val provider: String,
    val accountId: String,
)
