package uk.dnpk.sekund.runtime.auth

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.auth.ProviderNotDeclaredException
import uk.dnpk.sekund.sdk.settings.AccountRef

/**
 * The [AuthAccess] given to one plugin instance (`context.auth`).
 *
 * Enforces the manifest: a plugin can only use accounts of providers it declared in `requires.auth`.
 * Clients are cached per account, so asking twice returns the same client.
 */
class PluginAuthAccess(
    private val registry: AuthRegistry,
    private val pluginId: String,
    private val declaredProviders: Set<String>,
) : AuthAccess {
    private val clients = mutableMapOf<String, HttpClient>()

    override fun state(account: AccountRef): Flow<AuthState> {
        if (!declaredProviders.contains(account.provider)) return flowOf(AuthState.NotFound)
        val saved = registry.account(account.accountId)
        if (saved != null && saved.provider != account.provider) return flowOf(AuthState.NotFound)
        return registry.state(account.accountId)
    }

    override fun client(account: AccountRef): HttpClient {
        if (!declaredProviders.contains(account.provider)) throw ProviderNotDeclaredException(account.provider, pluginId)
        val saved = registry.account(account.accountId)
        if (saved != null && saved.provider != account.provider) {
            throw IllegalArgumentException("Account '${account.accountId}' is a ${saved.provider} account, not ${account.provider}")
        }
        val existing = clients[account.accountId]
        if (existing != null) return existing
        val created = SignedInClient.create(registry, account.accountId)
        clients[account.accountId] = created
        return created
    }
}
