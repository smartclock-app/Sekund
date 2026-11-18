package uk.dnpk.sekund.runtime.auth.secrets

/**
 * Where tokens, API keys and passwords are kept: encrypted, and never in config.json, so config files
 * and backups are safe to share.
 *
 * Keys are chosen by the auth registry (one entry per account). Values are plain strings; the registry
 * stores each account's credential as a small JSON document.
 */
interface SecretStore {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun delete(key: String)
}
