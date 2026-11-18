package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.api.AccountFlow
import uk.dnpk.sekund.protocol.api.AccountSummary
import uk.dnpk.sekund.protocol.api.ConnectAccount
import uk.dnpk.sekund.protocol.api.CredentialUpload
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.runtime.auth.AuthCodeSignIns
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.ConnectedAccount
import uk.dnpk.sekund.runtime.auth.Credential
import uk.dnpk.sekund.runtime.auth.DeviceSignIn
import uk.dnpk.sekund.runtime.auth.DeviceSignInStatus
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.runtime.relay.ServerSignIn
import uk.dnpk.sekund.runtime.relay.ServerSignInStatus
import uk.dnpk.sekund.runtime.relay.ServerSignIns
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.auth.AuthState

/**
 * Connecting accounts from the device API. The real work is done by the [AuthRegistry] and the sign-in
 * classes; this keeps track of sign-ins by `flowId` so a companion can follow them.
 */
class AccountFlows(
    private val auth: AuthRegistry,
    private val authCodes: AuthCodeSignIns,
    private val alerts: AlertCenter,
    private val serverSignIns: ServerSignIns,
) {
    private val lock = Mutex()
    private val deviceSignIns = mutableMapOf<String, DeviceSignIn>()
    private val authCodeFlows = mutableMapOf<String, AccountFlow>()

    suspend fun summaries(): List<AccountSummary> = auth.accounts.value.map { account -> summary(account) }

    suspend fun summary(account: ConnectedAccount): AccountSummary {
        val state = auth.state(account.id).first()
        return summaryOf(account, state)
    }

    /** Starts (or, for a pasted key, completes) a sign-in. */
    suspend fun connect(request: ConnectAccount): AccountFlow {
        val definition = auth.provider(request.provider) ?: throw ApiProblem.notFound("No auth provider called '${request.provider}'")
        val method = definition.methods.getOrNull(request.method)
            ?: throw ApiProblem.badRequest("${definition.name} has no sign-in method ${request.method}")
        val reconnect = request.reconnect
        if (reconnect != null && auth.account(reconnect) == null) throw ApiProblem.notFound("No account called '$reconnect'")

        when (method) {
            is AuthMethodDefinition.ApiKey, is AuthMethodDefinition.Bearer, is AuthMethodDefinition.Custom -> {
                val key = request.key ?: throw ApiProblem.badRequest("Send the key or token in \"key\"")
                val account = auth.connectWithKey(request.provider, request.method, request.fields, key, reconnect)
                return AccountFlow.Connected(summary(account))
            }
            is AuthMethodDefinition.OAuthDeviceCode -> {
                val signIn = DeviceSignIn.start(auth, alerts, request.provider, request.method, request.fields, request.clientId, request.clientSecret, reconnect)
                val flowId = randomUrlSafe(12)
                lock.withLock { deviceSignIns[flowId] = signIn }
                return deviceFlow(flowId, signIn)
            }
            is AuthMethodDefinition.OAuthAuthorizationCode -> {
                if (request.viaServer) return startThroughServer(request, reconnect)
                val redirectUri = request.redirectUri ?: throw ApiProblem.badRequest("Send the companion's \"redirectUri\"")
                val started = authCodes.start(request.provider, request.method, request.fields, request.clientId, request.clientSecret, redirectUri, reconnect)
                val flow = AccountFlow.AuthorizationCode(started.flowId, started.authorizeUrl, started.redirectUri, started.expiresAt.toString())
                lock.withLock { authCodeFlows[started.flowId] = flow }
                return flow
            }
        }
    }

    /** Signing in with the Sekund server's own OAuth client: the person opens the server's link. */
    private suspend fun startThroughServer(request: ConnectAccount, reconnect: String?): AccountFlow {
        if (!serverSignIns.canSignIn(request.provider, request.method)) {
            throw ApiProblem.conflict("The Sekund server this device uses can't sign in to '${request.provider}'")
        }
        val signIn = try {
            serverSignIns.start(request.provider, request.method, request.fields, reconnect)
        } catch (unavailable: IllegalStateException) {
            throw ApiProblem(503, "unavailable", unavailable.message ?: "The Sekund server isn't available")
        }
        return serverFlow(signIn)
    }

    /** Where the sign-in [flowId] has got to. */
    suspend fun status(flowId: String): AccountFlow {
        val throughServer = serverSignIns.find(flowId)
        if (throughServer != null) return serverFlow(throughServer)
        lock.withLock {
            val signIn = deviceSignIns[flowId]
            if (signIn != null) return deviceFlow(flowId, signIn)
            return authCodeFlows[flowId] ?: throw ApiProblem.notFound("No sign-in called '$flowId'")
        }
    }

    private suspend fun serverFlow(signIn: ServerSignIn): AccountFlow = when (val status = signIn.status.value) {
        is ServerSignInStatus.Waiting -> AccountFlow.OpenUrl(signIn.flowId, status.url, status.expiresAt)
        is ServerSignInStatus.Connected -> AccountFlow.Connected(summary(status.account))
        is ServerSignInStatus.Failed -> AccountFlow.Failed(status.reason)
    }

    /** The companion-assisted authorization-code step: swap the redirect's code for tokens. */
    suspend fun completeAuthorizationCode(flowId: String, code: String, state: String): AccountFlow {
        val account = try {
            authCodes.complete(flowId, code, state)
        } catch (wrong: IllegalArgumentException) {
            throw ApiProblem.badRequest(wrong.message ?: "That sign-in can't be completed")
        } catch (refused: IllegalStateException) {
            val failed = AccountFlow.Failed(refused.message ?: "The service refused the sign-in")
            lock.withLock { authCodeFlows[flowId] = failed }
            return failed
        }
        val connected = AccountFlow.Connected(summary(account))
        lock.withLock { authCodeFlows[flowId] = connected }
        return connected
    }

    /** The fallback: store a credential the companion obtained itself, for an existing account. */
    suspend fun uploadCredential(accountId: String, upload: CredentialUpload): AccountSummary {
        val account = auth.account(accountId) ?: throw ApiProblem.notFound("No account called '$accountId'")
        val definition = auth.provider(account.provider) ?: throw ApiProblem.conflict("The '${account.provider}' provider isn't installed")
        val previous = auth.readCredential(account)
        val credential = Credential(
            accessToken = upload.accessToken,
            refreshToken = upload.refreshToken,
            expiresAtEpochSeconds = auth.expiryFrom(upload.expiresInSeconds),
            apiKey = upload.apiKey,
            // Keep the person's own OAuth client, so refreshing keeps working.
            clientId = previous?.clientId,
            clientSecret = previous?.clientSecret,
        )
        val saved = auth.saveSignIn(definition, account.methodIndex, account.fields, credential, reconnect = accountId)
        return summary(saved)
    }

    suspend fun remove(accountId: String) {
        if (auth.account(accountId) == null) throw ApiProblem.notFound("No account called '$accountId'")
        auth.remove(accountId)
    }

    private suspend fun deviceFlow(flowId: String, signIn: DeviceSignIn): AccountFlow = when (val status = signIn.status.value) {
        is DeviceSignInStatus.Waiting -> AccountFlow.DeviceCode(flowId, status.userCode, status.verificationUrl, status.qrUrl)
        is DeviceSignInStatus.Connected -> AccountFlow.Connected(summary(status.account))
        is DeviceSignInStatus.Failed -> AccountFlow.Failed(status.reason)
    }

    companion object {
        fun summaryOf(account: ConnectedAccount, state: AuthState): AccountSummary = when (state) {
            AuthState.Valid -> AccountSummary(account.id, account.provider, account.label, "valid")
            AuthState.Refreshing -> AccountSummary(account.id, account.provider, account.label, "refreshing")
            is AuthState.NeedsReauth -> AccountSummary(account.id, account.provider, account.label, "needsReauth", state.reason)
            AuthState.NotFound -> AccountSummary(account.id, account.provider, account.label, "notFound")
        }
    }
}
