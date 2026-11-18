package uk.dnpk.sekund.runtime.auth

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.runtime.auth.secrets.SecretStore
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.auth.AccountUnavailableException
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.context.Alert
import kotlin.time.Clock

/**
 * The one place that knows about accounts, credentials and how to sign in. Plugins reach it only
 * through [PluginAuthAccess], which hands out signed-in HTTP clients and never the tokens themselves.
 *
 * Responsibilities:
 * - the auth provider definitions ([providers]) and connected [accounts] (`accounts.json`),
 * - credentials, kept encrypted in the [SecretStore],
 * - connecting accounts (API key, device code, authorization code) and removing them,
 * - refreshing tokens: at most one refresh per account at a time, new tokens saved straight away,
 * - one central "reconnect your account" alert when a credential stops working.
 *
 * @param scope where background work (device-code polling) runs; the app scope in production.
 */
class AuthRegistry(
    http: HttpClient,
    private val secrets: SecretStore,
    private val accountsFile: AccountsFile,
    private val alerts: AlertCenter,
    internal val scope: CoroutineScope,
    internal val clock: Clock = Clock.System,
) {
    private val log = Logger.withTag("Auth")

    /** The shared HTTP client, without credentials. Signed-in clients are built from it. */
    internal val baseHttp: HttpClient = http
    internal val tokenEndpoint = TokenEndpoint(http)

    private val currentProviders = MutableStateFlow<Map<String, AuthProviderDefinition>>(emptyMap())
    private val currentAccounts = MutableStateFlow<List<ConnectedAccount>>(emptyList())
    private val refreshing = MutableStateFlow<Set<String>>(emptySet())

    /** Guards changes to the account list (and accounts.json) and the map of refresh locks below. */
    private val accountsLock = Mutex()

    /**
     * One lock per account, held while checking and refreshing its token. This is what makes refresh
     * "single-flight": when several requests find the token expired at once, the first refreshes and the
     * others wait, then see the new token and use it.
     */
    private val refreshLocks = mutableMapOf<String, Mutex>()

    /** Every usable provider definition, by id. */
    val providers: StateFlow<Map<String, AuthProviderDefinition>> = currentProviders

    /** Every connected account, in the order they were added. */
    val accounts: StateFlow<List<ConnectedAccount>> = currentAccounts

    /** Replaces the provider definitions (at startup, from [AuthProviderLoader]). */
    fun setProviders(definitions: Map<String, AuthProviderDefinition>) {
        currentProviders.value = definitions
    }

    /** Reads accounts.json and shows a reconnect alert for every account that needs one. */
    suspend fun loadAccounts() {
        val saved = accountsFile.read()
        accountsLock.withLock { currentAccounts.value = saved }
        for (account in saved) {
            val reason = account.needsReauth
            if (reason != null) showReconnectAlert(account, reason)
        }
    }

    fun provider(id: String): AuthProviderDefinition? = currentProviders.value[id]

    fun account(id: String): ConnectedAccount? = currentAccounts.value.firstOrNull { account -> account.id == id }

    /** The live state of one account. */
    fun state(accountId: String): Flow<AuthState> =
        combine(currentAccounts, refreshing, currentProviders) { accounts, refreshingIds, providers ->
            stateOf(accounts.firstOrNull { account -> account.id == accountId }, refreshingIds, providers)
        }.distinctUntilChanged()

    private fun stateOf(account: ConnectedAccount?, refreshingIds: Set<String>, providers: Map<String, AuthProviderDefinition>): AuthState {
        if (account == null) return AuthState.NotFound
        if (!providers.containsKey(account.provider)) return AuthState.NeedsReauth("The '${account.provider}' sign-in provider isn't installed")
        val reason = account.needsReauth
        if (reason != null) return AuthState.NeedsReauth(reason)
        if (refreshingIds.contains(account.id)) return AuthState.Refreshing
        return AuthState.Valid
    }

    // ---- Connecting and removing ----------------------------------------------------------------

    /**
     * Connects an account with a pasted API key or access token (`apiKey` and `bearer` methods).
     *
     * @param reconnect the id of an existing account to update instead of adding a new one.
     */
    suspend fun connectWithKey(
        providerId: String,
        methodIndex: Int,
        fields: Map<String, String>,
        key: String,
        reconnect: String? = null,
    ): ConnectedAccount {
        val definition = requireProvider(providerId)
        val method = definition.methods.getOrNull(methodIndex) ?: error("$providerId has no method $methodIndex")
        val credential = when (method) {
            is AuthMethodDefinition.ApiKey -> Credential(apiKey = key.trim())
            is AuthMethodDefinition.Bearer -> Credential(accessToken = key.trim())
            else -> error("Method $methodIndex of $providerId doesn't take a pasted key")
        }
        return saveSignIn(definition, methodIndex, fields, credential, reconnect)
    }

    /**
     * Stores a finished sign-in: saves the credential, adds (or updates) the account, clears any
     * reconnect alert. Used by every sign-in method.
     */
    internal suspend fun saveSignIn(
        definition: AuthProviderDefinition,
        methodIndex: Int,
        fields: Map<String, String>,
        credential: Credential,
        reconnect: String?,
    ): ConnectedAccount {
        val cleanFields = AccountTemplates.cleanFields(fields)
        val label = fetchLabel(definition, cleanFields, credential) ?: defaultLabel(definition, cleanFields)
        val existing = if (reconnect == null) null else account(reconnect)
        val account = ConnectedAccount(
            id = existing?.id ?: newAccountId(),
            provider = definition.id,
            label = label,
            methodIndex = methodIndex,
            fields = cleanFields,
            needsReauth = null,
        )
        writeCredential(account, credential)
        accountsLock.withLock {
            val updated = currentAccounts.value.filter { other -> other.id != account.id } + account
            saveAccounts(updated)
        }
        alerts.clear(alertOwner(account.id))
        log.i { "Connected ${definition.id} account ${account.id}" }
        return account
    }

    /**
     * Adds an account brought over from somewhere else (the old Tauri app's config), with a credential
     * that's already been obtained. Unlike a sign-in this works even if the provider's definition isn't
     * installed yet: the account then shows "provider isn't installed" until it is, and nothing is lost.
     *
     * @param id the account id to use; an existing account with this id is replaced.
     */
    suspend fun importAccount(
        id: String,
        provider: String,
        methodIndex: Int,
        label: String,
        fields: Map<String, String>,
        credential: Credential,
    ): ConnectedAccount {
        val account = ConnectedAccount(
            id = id,
            provider = provider,
            label = label,
            methodIndex = methodIndex,
            fields = AccountTemplates.cleanFields(fields),
        )
        writeCredential(account, credential)
        accountsLock.withLock {
            saveAccounts(currentAccounts.value.filter { other -> other.id != id } + account)
        }
        log.i { "Imported $provider account $id" }
        return account
    }

    /** Removes an account: tells the service to revoke the token (if it can), then forgets it. */
    suspend fun remove(accountId: String) {
        val account = account(accountId) ?: return
        val definition = provider(account.provider)
        val credential = readCredential(account)
        if (definition != null && credential != null) revoke(definition, account, credential)

        secrets.delete(account.secretKey)
        accountsLock.withLock {
            saveAccounts(currentAccounts.value.filter { other -> other.id != accountId })
            refreshLocks.remove(accountId)
        }
        alerts.clear(alertOwner(accountId))
    }

    /** Best effort: if revoking fails the account is still removed here. */
    private suspend fun revoke(definition: AuthProviderDefinition, account: ConnectedAccount, credential: Credential) {
        val method = definition.methods.getOrNull(account.methodIndex) ?: return
        val oauth = OAuthSettings.of(method) ?: return
        val revokeUrl = oauth.revokeUrl ?: return
        val token = credential.refreshToken ?: credential.accessToken ?: return
        val templates = AccountTemplates(definition, account.fields, credential)
        try {
            val answer = tokenEndpoint.post(
                url = templates.fill(revokeUrl),
                params = mapOf("token" to token),
                encoding = oauth.encoding,
                clientAuth = oauth.clientAuth,
                clientId = templates.clientId(),
                clientSecret = templates.clientSecret(),
            )
            if (!answer.isSuccess) log.w { "Revoking ${account.id} answered ${answer.describe()}" }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Couldn't revoke ${account.id}; removing it anyway" }
        }
    }

    // ---- Credentials for requests ---------------------------------------------------------------

    /**
     * The credential to put on a request now. Refreshes first if the access token expires within the
     * provider's `skewSeconds`.
     *
     * @throws AccountUnavailableException if the account is missing or must be reconnected.
     */
    internal suspend fun credentialForRequest(accountId: String): SigningDetails {
        val details = signingDetails(accountId)
        val credential = details.credential
        if (!expiresSoon(details.definition, details.account, credential)) return details

        val fresh = refreshIfStill(accountId) { stored -> expiresSoon(details.definition, details.account, stored) }
        return details.copy(credential = fresh ?: credential)
    }

    /**
     * The service answered `401` to a request made with [rejected]. Returns a credential worth retrying
     * with, or null if there isn't one (the account is then marked as needing reconnection).
     *
     * If another request already refreshed in the meantime, its new token is returned without refreshing again.
     */
    internal suspend fun credentialAfterRejection(accountId: String, rejected: Credential): Credential? {
        return refreshIfStill(accountId) { stored -> stored.accessToken == rejected.accessToken && stored.apiKey == rejected.apiKey }
    }

    /**
     * Under the account's refresh lock: re-reads the stored credential and refreshes only if [stillNeeded]
     * says so. Returns the credential to use, or null if the account can't be used any more.
     */
    private suspend fun refreshIfStill(accountId: String, stillNeeded: (Credential) -> Boolean): Credential? {
        val lock = refreshLockFor(accountId)
        lock.withLock {
            val details = signingDetails(accountId)
            val stored = details.credential
            if (!stillNeeded(stored)) return stored // someone else refreshed while we waited

            val refreshToken = stored.refreshToken
            val oauth = OAuthSettings.of(details.definition.methods[details.account.methodIndex])
            if (refreshToken == null || oauth == null) {
                markNeedsReauth(accountId, "${details.definition.name} no longer accepts the saved credential")
                return null
            }
            return refresh(details, oauth, refreshToken)
        }
    }

    private suspend fun refresh(details: SigningDetails, oauth: OAuthSettings, refreshToken: String): Credential? {
        val account = details.account
        val templates = AccountTemplates(details.definition, account.fields, details.credential)
        val params = mutableMapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken)
        params.putAll(oauth.refresh.extraParams)

        refreshing.update { ids -> ids + account.id }
        try {
            // Network errors are thrown from here: the account stays usable and the request fails as offline.
            val answer = tokenEndpoint.post(
                url = templates.fill(oauth.refreshUrl),
                params = params,
                encoding = oauth.encoding,
                clientAuth = oauth.clientAuth,
                clientId = templates.clientId(),
                clientSecret = templates.clientSecret(),
            )
            val tokens = answer.tokens()
            val newAccessToken = tokens.accessToken
            if (answer.isSuccess && newAccessToken != null) {
                val updated = details.credential.copy(
                    accessToken = newAccessToken,
                    // Services that rotate refresh tokens send a new one; others send none and the old one stays valid.
                    refreshToken = tokens.refreshToken ?: refreshToken,
                    expiresAtEpochSeconds = expiryFrom(tokens.expiresIn),
                )
                writeCredential(account, updated)
                log.i { "Refreshed ${account.id}" }
                return updated
            }

            // 400/401 (usually `invalid_grant`): the refresh token was revoked or expired. Only the user can fix that.
            if (answer.status == 400 || answer.status == 401 || tokens.error == "invalid_grant") {
                log.w { "Refreshing ${account.id} was refused: ${answer.describe()}" }
                markNeedsReauth(account.id, "${details.definition.name} asked you to sign in again")
                return null
            }
            throw IllegalStateException("Refreshing ${account.id} failed: ${answer.describe()}")
        } finally {
            refreshing.update { ids -> ids - account.id }
        }
    }

    /** Everything needed to sign a request for one account. */
    internal data class SigningDetails(
        val account: ConnectedAccount,
        val definition: AuthProviderDefinition,
        val credential: Credential,
    )

    private suspend fun signingDetails(accountId: String): SigningDetails {
        val account = account(accountId) ?: throw AccountUnavailableException(accountId, "it isn't connected")
        val reason = account.needsReauth
        if (reason != null) throw AccountUnavailableException(accountId, reason)
        val definition = provider(account.provider)
            ?: throw AccountUnavailableException(accountId, "the '${account.provider}' provider isn't installed")
        val credential = readCredential(account)
            ?: throw AccountUnavailableException(accountId, "its saved credential is missing")
        return SigningDetails(account, definition, credential)
    }

    private fun expiresSoon(definition: AuthProviderDefinition, account: ConnectedAccount, credential: Credential): Boolean {
        val expiresAt = credential.expiresAtEpochSeconds ?: return false
        if (credential.refreshToken == null) return false // nothing we could do about it
        val method = definition.methods.getOrNull(account.methodIndex) ?: return false
        val skew = OAuthSettings.of(method)?.refresh?.skewSeconds ?: 60
        return clock.now().epochSeconds >= expiresAt - skew
    }

    internal fun expiryFrom(expiresInSeconds: Long?): Long? {
        if (expiresInSeconds == null) return null
        return clock.now().epochSeconds + expiresInSeconds
    }

    private suspend fun markNeedsReauth(accountId: String, reason: String) {
        val account = accountsLock.withLock {
            val current = currentAccounts.value.firstOrNull { account -> account.id == accountId } ?: return
            val changed = current.copy(needsReauth = reason)
            saveAccounts(currentAccounts.value.map { other -> if (other.id == accountId) changed else other })
            changed
        }
        showReconnectAlert(account, reason)
    }

    private fun showReconnectAlert(account: ConnectedAccount, reason: String) {
        alerts.show(
            alertOwner(account.id),
            Alert(title = "Reconnect ${account.label}", subtitle = "$reason. Open Accounts from the menu to reconnect."),
        )
    }

    // ---- Storage helpers ------------------------------------------------------------------------

    private suspend fun refreshLockFor(accountId: String): Mutex = accountsLock.withLock {
        refreshLocks.getOrPut(accountId) { Mutex() }
    }

    /** Must be called with [accountsLock] held. */
    private fun saveAccounts(accounts: List<ConnectedAccount>) {
        accountsFile.write(accounts)
        currentAccounts.value = accounts
    }

    internal suspend fun readCredential(account: ConnectedAccount): Credential? {
        val text = secrets.read(account.secretKey) ?: return null
        return SekundJson.decodeFromString(Credential.serializer(), text)
    }

    private suspend fun writeCredential(account: ConnectedAccount, credential: Credential) {
        secrets.write(account.secretKey, SekundJson.encodeToString(Credential.serializer(), credential))
    }

    internal fun requireProvider(id: String): AuthProviderDefinition =
        provider(id) ?: throw IllegalArgumentException("No auth provider called '$id'")

    /** Asks the service who signed in (e.g. their email) to label the account. Optional, so failures are ignored. */
    private suspend fun fetchLabel(definition: AuthProviderDefinition, fields: Map<String, String>, credential: Credential): String? {
        val info = definition.accountInfo ?: return null
        val templates = AccountTemplates(definition, fields, credential)
        try {
            val response = baseHttp.get(templates.fill(info.url)) {
                CredentialInjector.apply(this, definition, templates)
            }
            if (response.status.value !in 200..299) return null
            val json = SekundJson.decodeFromString(JsonElement.serializer(), response.bodyAsText())
            return JsonPath.text(json, info.label)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Couldn't fetch the account name for ${definition.id}" }
            return null
        }
    }

    private fun defaultLabel(definition: AuthProviderDefinition, fields: Map<String, String>): String {
        // Self-hosted services: "Immich (photos.example.com)" tells two servers apart.
        for (value in fields.values) {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                val host = value.substringAfter("://").substringBefore('/')
                return "${definition.name} ($host)"
            }
        }
        return definition.name
    }

    private fun newAccountId(): String = "acc_" + randomUrlSafe(6)

    companion object {
        /** The alert owner for one account's reconnect reminder. */
        fun alertOwner(accountId: String): String = "auth:$accountId"
    }
}
