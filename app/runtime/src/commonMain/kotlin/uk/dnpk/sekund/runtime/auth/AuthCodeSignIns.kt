package uk.dnpk.sekund.runtime.auth

import io.ktor.http.URLBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The start of an authorization-code sign-in: open [authorizeUrl] in a browser (on the phone, through
 * the companion app), then pass the `code` and `state` it comes back with to [AuthCodeSignIns.complete].
 */
data class AuthCodeRequest(
    val flowId: String,
    val authorizeUrl: String,
    val redirectUri: String,
    val expiresAt: Instant,
)

/**
 * Authorization-code sign-ins (with PKCE) that have been started but not finished.
 *
 * The display can't show a browser, so the browser part happens elsewhere (the companion app, later the
 * LAN API); this class only builds the URL and later swaps the returned code for tokens. Unfinished
 * sign-ins are forgotten after 10 minutes.
 */
class AuthCodeSignIns(private val registry: AuthRegistry) {
    private val lock = Mutex()
    private val pending = mutableMapOf<String, Pending>()

    private class Pending(
        val definition: AuthProviderDefinition,
        val method: AuthMethodDefinition.OAuthAuthorizationCode,
        val methodIndex: Int,
        val fields: Map<String, String>,
        val startingCredential: Credential,
        val redirectUri: String,
        val state: String,
        val pkce: Pkce?,
        val expiresAt: Instant,
        val reconnect: String?,
    )

    /**
     * Builds the authorize URL and remembers the sign-in.
     *
     * @param redirectUri where the service should send the browser back to (decided by whoever opens it).
     */
    suspend fun start(
        providerId: String,
        methodIndex: Int,
        fields: Map<String, String>,
        clientId: String?,
        clientSecret: String?,
        redirectUri: String,
        reconnect: String? = null,
    ): AuthCodeRequest {
        val definition = registry.requireProvider(providerId)
        val method = definition.methods.getOrNull(methodIndex)
        if (method !is AuthMethodDefinition.OAuthAuthorizationCode) error("Method $methodIndex of $providerId isn't an authorization-code sign-in")

        val startingCredential = Credential(clientId = clientId?.trim(), clientSecret = clientSecret?.trim())
        val templates = AccountTemplates(definition, fields, startingCredential)
        val resolvedClientId = templates.clientId() ?: error("${definition.name} needs a client id")
        val state = randomUrlSafe(16) // ties the answer to this request, so a forged redirect is rejected
        val pkce = if (method.pkce) Pkce.create() else null

        val url = URLBuilder(templates.fill(method.authorizeUrl))
        url.parameters.append("response_type", "code")
        url.parameters.append("client_id", resolvedClientId)
        url.parameters.append("redirect_uri", redirectUri)
        url.parameters.append("state", state)
        if (method.scopes.isNotEmpty()) url.parameters.append("scope", method.scopes.joinToString(method.scopeSeparator))
        if (pkce != null) {
            url.parameters.append("code_challenge", pkce.challenge)
            url.parameters.append("code_challenge_method", pkce.method)
        }
        for ((name, value) in method.extraAuthorizeParams) {
            url.parameters.append(name, value)
        }

        val flowId = randomUrlSafe(12)
        val expiresAt = registry.clock.now() + 10.minutes
        lock.withLock {
            forgetExpired()
            pending[flowId] = Pending(definition, method, methodIndex, fields, startingCredential, redirectUri, state, pkce, expiresAt, reconnect)
        }
        return AuthCodeRequest(flowId, url.buildString(), redirectUri, expiresAt)
    }

    /**
     * Swaps the code from the redirect for tokens and connects the account.
     *
     * @throws IllegalArgumentException if [flowId] is unknown or expired, or [state] doesn't match.
     * @throws IllegalStateException if the service refuses the code.
     */
    suspend fun complete(flowId: String, code: String, state: String): ConnectedAccount {
        val flow = lock.withLock {
            forgetExpired()
            val found = pending[flowId] ?: throw IllegalArgumentException("This sign-in expired or doesn't exist. Start again.")
            if (found.state != state) throw IllegalArgumentException("The sign-in answer doesn't match the request")
            pending.remove(flowId) // a code can only be used once
            found
        }

        val templates = AccountTemplates(flow.definition, flow.fields, flow.startingCredential)
        val params = mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to flow.redirectUri,
        )
        val pkce = flow.pkce
        if (pkce != null) params["code_verifier"] = pkce.verifier

        val answer = registry.tokenEndpoint.post(
            url = templates.fill(flow.method.tokenUrl),
            params = params,
            encoding = flow.method.bodyEncoding,
            clientAuth = flow.method.clientAuth,
            clientId = templates.clientId(),
            clientSecret = templates.clientSecret(),
        )
        val tokens = answer.tokens()
        val accessToken = tokens.accessToken
        if (!answer.isSuccess || accessToken == null) error("${flow.definition.name} refused the sign-in (${answer.describe()})")

        val credential = flow.startingCredential.copy(
            accessToken = accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtEpochSeconds = registry.expiryFrom(tokens.expiresIn),
        )
        return registry.saveSignIn(flow.definition, flow.methodIndex, flow.fields, credential, flow.reconnect)
    }

    /** Must be called with [lock] held. */
    private fun forgetExpired() {
        val now = registry.clock.now()
        val expired = pending.filter { entry -> entry.value.expiresAt <= now }.keys
        for (id in expired) pending.remove(id)
    }
}
