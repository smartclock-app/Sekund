package uk.dnpk.sekund.runtime.relay

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.ClientMode
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.ConnectedAccount
import uk.dnpk.sekund.runtime.auth.Credential
import uk.dnpk.sekund.runtime.auth.EndpointAnswer
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.context.Alert
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/** Where a sign-in through the server has got to. */
sealed class ServerSignInStatus {
    /** Waiting for the person to open [url] (shown as a QR code too) and sign in. */
    data class Waiting(val url: String, val expiresAt: String) : ServerSignInStatus()

    data class Connected(val account: ConnectedAccount) : ServerSignInStatus()

    data class Failed(val reason: String) : ServerSignInStatus()
}

/** One sign-in through the server, followed by the Accounts screen or the device API. */
class ServerSignIn(val flowId: String, initial: ServerSignInStatus) {
    internal val mutableStatus = MutableStateFlow(initial)

    val status: StateFlow<ServerSignInStatus> = mutableStatus
}

/**
 * Signing in to services (e.g. Google) with the Sekund server's own OAuth client, for providers whose
 * definition allows it (`"client": {"mode": "byoOrServer"}`) and that the server offers.
 *
 * The person opens a link from the server on their phone and signs in there; the server then delivers
 * the tokens over the relay (`credential.delivered`) and they're stored like any other sign-in, marked
 * [Credential.viaServer] so that later refreshes also go through the server ([refresh]).
 */
class ServerSignIns(
    private val auth: AuthRegistry,
    private val alerts: AlertCenter,
) : RelayFeature {
    private val log = Logger.withTag("ServerSignIn")

    /** What's needed to finish a sign-in once the tokens arrive. */
    private class Pending(
        val signIn: ServerSignIn,
        val providerId: String,
        val methodIndex: Int,
        val fields: Map<String, String>,
        val reconnect: String?,
    )

    private val lock = Mutex()
    private val pending = mutableMapOf<String, Pending>()

    @Volatile
    private var link: RelayLink? = null

    private val offered = MutableStateFlow<List<String>>(emptyList())

    /** The providers the connected server can sign in to (empty without a server). */
    val providers: StateFlow<List<String>> = offered

    override suspend fun connected(link: RelayLink, welcome: RelayMessage.Welcome) {
        this.link = link
        offered.value = welcome.oauthProviders
    }

    override fun disconnected() {
        link = null
        offered.value = emptyList()
    }

    /** Whether method [methodIndex] of [providerId] can be done through the connected server. */
    fun canSignIn(providerId: String, methodIndex: Int): Boolean {
        val method = auth.provider(providerId)?.methods?.getOrNull(methodIndex)
        if (method !is AuthMethodDefinition.OAuthAuthorizationCode) return false
        return method.client.mode == ClientMode.ByoOrServer && providerId in offered.value
    }

    /**
     * Starts a sign-in: asks the server for a link and shows it on screen.
     *
     * @throws IllegalStateException if the server can't do it (not connected, or doesn't offer the provider).
     */
    suspend fun start(providerId: String, methodIndex: Int, fields: Map<String, String>, reconnect: String?): ServerSignIn {
        val definition = auth.requireProvider(providerId)
        val method = definition.methods.getOrNull(methodIndex)
        if (method !is AuthMethodDefinition.OAuthAuthorizationCode || !canSignIn(providerId, methodIndex)) {
            throw IllegalStateException("The Sekund server can't sign in to ${definition.name}")
        }
        val currentLink = link ?: throw IllegalStateException("Not connected to the Sekund server")

        val id = randomUrlSafe(9)
        val request = RelayMessage.OAuthStart(id, providerId, method.scopes, method.scopeSeparator, method.extraAuthorizeParams)
        val answer = currentLink.request(id, request, 30.seconds)
        if (answer is RelayMessage.Error) throw IllegalStateException(answer.message)
        if (answer !is RelayMessage.OAuthStarted) throw IllegalStateException("The Sekund server didn't answer")

        val signIn = ServerSignIn(answer.flowId, ServerSignInStatus.Waiting(answer.url, answer.expiresAt))
        lock.withLock { pending[answer.flowId] = Pending(signIn, providerId, methodIndex, fields, reconnect) }
        alerts.show(alertOwner(answer.flowId), Alert(title = "Sign in to ${definition.name}", subtitle = "Scan the code or open ${answer.url}", qrCode = answer.url))
        return signIn
    }

    /** The sign-in [flowId], if it was started since the app started. */
    suspend fun find(flowId: String): ServerSignIn? = lock.withLock { pending[flowId]?.signIn }

    /**
     * Refreshes a token that came from the server's OAuth client.
     *
     * @throws IllegalStateException if there's no server to ask, or it didn't answer (treated like a
     *   network error: the account stays usable and is refreshed again later).
     */
    suspend fun refresh(providerId: String, refreshToken: String): EndpointAnswer {
        val currentLink = link ?: throw IllegalStateException("Not connected to the Sekund server, which refreshes this account")
        val id = randomUrlSafe(9)
        val answer = currentLink.request(id, RelayMessage.TokenRefresh(id, providerId, refreshToken), 30.seconds)
        if (answer is RelayMessage.TokenRefreshed) return EndpointAnswer(answer.status, answer.body)
        if (answer is RelayMessage.Error) throw IllegalStateException(answer.message)
        throw IllegalStateException("The Sekund server didn't answer the refresh")
    }

    override suspend fun handle(link: RelayLink, message: RelayMessage): Boolean {
        when (message) {
            is RelayMessage.CredentialDelivered -> {
                finish(message)
                link.send(RelayMessage.Ack(message.id))
                return true
            }
            is RelayMessage.OAuthFailed -> {
                val waiting = lock.withLock { pending.remove(message.flowId) }
                if (waiting != null) {
                    waiting.signIn.mutableStatus.value = ServerSignInStatus.Failed(message.reason)
                    alerts.clear(alertOwner(message.flowId))
                }
                link.send(RelayMessage.Ack(message.id))
                return true
            }
            else -> return false
        }
    }

    /** Stores the delivered tokens as the account. A repeat delivery (or one from before a restart) is ignored. */
    private suspend fun finish(delivery: RelayMessage.CredentialDelivered) {
        val waiting = lock.withLock { pending.remove(delivery.flowId) }
        if (waiting == null) {
            log.i { "Ignoring a sign-in this device doesn't know about (${delivery.flowId})" }
            return
        }
        alerts.clear(alertOwner(delivery.flowId))
        try {
            val definition = auth.requireProvider(waiting.providerId)
            val credential = Credential(
                accessToken = delivery.accessToken,
                refreshToken = delivery.refreshToken,
                expiresAtEpochSeconds = auth.expiryFrom(delivery.expiresInSeconds),
                viaServer = true,
            )
            val account = auth.saveSignIn(definition, waiting.methodIndex, waiting.fields, credential, waiting.reconnect)
            waiting.signIn.mutableStatus.value = ServerSignInStatus.Connected(account)
        } catch (error: IllegalStateException) {
            waiting.signIn.mutableStatus.value = ServerSignInStatus.Failed(error.message ?: "Couldn't save the sign-in")
        } catch (error: IllegalArgumentException) {
            waiting.signIn.mutableStatus.value = ServerSignInStatus.Failed(error.message ?: "Couldn't save the sign-in")
        }
    }

    private fun alertOwner(flowId: String) = "signin:$flowId"
}
