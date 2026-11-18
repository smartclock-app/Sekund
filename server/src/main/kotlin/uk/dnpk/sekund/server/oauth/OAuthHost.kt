package uk.dnpk.sekund.server.oauth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.config.OAuthClientConfig
import uk.dnpk.sekund.server.relay.Outbox
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Signing in to services with the server's own OAuth clients, for devices (the "OAuth host"):
 *
 * 1. The device sends `oauth.start`; [start] answers with a link to `/oauth/{provider}/start?flow=...`,
 *    which the device shows (as a QR code) for the person to open on their phone.
 * 2. That page redirects to the provider ([authorizeRedirect]), where the person signs in.
 * 3. The provider sends the browser back to `/oauth/{provider}/callback`; [callback] swaps the code for
 *    tokens using the client secret (which never leaves the server) and delivers them to the device.
 *    The server doesn't keep them.
 * 4. Later, the device asks the server to refresh its token ([refresh]), again because only the
 *    server knows the client secret.
 *
 * @param clients the server's OAuth clients, by auth provider id.
 * @param publicUrl the server's public address (the callback has to be reachable by browsers).
 */
class OAuthHost(
    private val clients: Map<String, OAuthClientConfig>,
    private val publicUrl: String?,
    private val outbox: Outbox,
    private val http: HttpClient,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger("OAuth")

    /** A sign-in that's been started but not finished. */
    private class Flow(
        val flowId: String,
        val deviceId: String,
        val provider: String,
        val request: RelayMessage.OAuthStart,
        val state: String,
        val codeVerifier: String,
        val expiresAt: Instant,
    )

    private val lock = Mutex()
    private val flows = mutableMapOf<String, Flow>()

    /** The provider ids this server can sign in to. */
    val providers: List<String>
        get() = if (publicUrl == null) emptyList() else clients.keys.sorted()

    /** Answers a device's `oauth.start`. */
    suspend fun start(deviceId: String, request: RelayMessage.OAuthStart): RelayMessage {
        if (publicUrl == null) return RelayMessage.Error(request.id, "This server has no publicUrl, so it can't host sign-ins")
        if (clients[request.provider] == null) return RelayMessage.Error(request.id, "This server has no OAuth client for '${request.provider}'")

        val flow = Flow(
            flowId = "fl_" + Secrets.randomToken(12),
            deviceId = deviceId,
            provider = request.provider,
            request = request,
            state = Secrets.randomToken(16),
            codeVerifier = Secrets.randomToken(32),
            expiresAt = clock.now() + 10.minutes,
        )
        lock.withLock {
            forgetExpired()
            flows[flow.flowId] = flow
        }
        val url = "$publicUrl/oauth/${request.provider}/start?flow=${flow.flowId}"
        return RelayMessage.OAuthStarted(replyTo = request.id, flowId = flow.flowId, url = url, expiresAt = flow.expiresAt.toString())
    }

    /**
     * Where `/oauth/{provider}/start?flow=...` sends the browser: the provider's sign-in page. Null if
     * the sign-in doesn't exist (or expired).
     */
    suspend fun authorizeRedirect(provider: String, flowId: String): String? {
        val flow = lock.withLock {
            forgetExpired()
            flows[flowId]
        } ?: return null
        if (flow.provider != provider) return null
        val client = clients[provider] ?: return null

        val url = URLBuilder(client.authorizeUrl)
        url.parameters.append("response_type", "code")
        url.parameters.append("client_id", client.clientId)
        url.parameters.append("redirect_uri", callbackUrl(provider))
        url.parameters.append("state", flow.state)
        url.parameters.append("code_challenge", challengeOf(flow.codeVerifier))
        url.parameters.append("code_challenge_method", "S256")
        if (flow.request.scopes.isNotEmpty()) url.parameters.append("scope", flow.request.scopes.joinToString(flow.request.scopeSeparator))
        for ((name, value) in flow.request.extraAuthorizeParams) url.parameters.append(name, value)
        return url.buildString()
    }

    /**
     * The provider sent the browser back with [code] (or [error]) and [state]. Swaps the code for
     * tokens and delivers them to the device. Returns a sentence to show in the browser.
     */
    suspend fun callback(provider: String, code: String?, state: String?, error: String?): String {
        val flow = lock.withLock {
            val found = flows.values.firstOrNull { candidate -> candidate.state == state && candidate.provider == provider }
            if (found != null) flows.remove(found.flowId) // each sign-in can finish only once
            found
        } ?: return "This sign-in has expired or was already used. Start again on your Sekund."
        if (flow.expiresAt <= clock.now()) return "This sign-in has expired. Start again on your Sekund."

        if (error != null || code == null) {
            fail(flow, "The sign-in was cancelled (${error ?: "no code"})")
            return "The sign-in was cancelled. You can close this page."
        }
        val client = clients[provider] ?: return "This server can no longer sign in to $provider."

        val params = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to callbackUrl(provider),
            "code_verifier" to flow.codeVerifier,
        )
        val (status, body) = postToTokenEndpoint(client, params)
        val tokens = parseTokens(body)
        val accessToken = tokens?.accessToken
        if (status !in 200..299 || accessToken == null) {
            log.warn("$provider refused a sign-in: HTTP $status ${tokens?.error ?: ""}")
            fail(flow, "$provider refused the sign-in (HTTP $status)")
            return "$provider refused the sign-in. Start again on your Sekund."
        }
        val id = "cr_" + Secrets.randomToken(9)
        val delivery = RelayMessage.CredentialDelivered(id, flow.flowId, accessToken, tokens.refreshToken, tokens.expiresIn)
        outbox.deliver(flow.deviceId, id, delivery)
        log.info("Delivered a $provider sign-in to ${flow.deviceId}")
        return "Signed in. Your Sekund will finish connecting by itself; you can close this page."
    }

    /** Answers a device's `token.refresh` with whatever the provider's token endpoint says. */
    suspend fun refresh(request: RelayMessage.TokenRefresh): RelayMessage {
        val client = clients[request.provider] ?: return RelayMessage.Error(request.id, "This server has no OAuth client for '${request.provider}'")
        val params = mapOf("grant_type" to "refresh_token", "refresh_token" to request.refreshToken)
        val (status, body) = postToTokenEndpoint(client, params)
        return RelayMessage.TokenRefreshed(replyTo = request.id, status = status, body = body)
    }

    private suspend fun fail(flow: Flow, reason: String) {
        val id = "of_" + Secrets.randomToken(9)
        outbox.deliver(flow.deviceId, id, RelayMessage.OAuthFailed(id, flow.flowId, reason))
    }

    /** Sends [params] to the client's token endpoint, with the client id and secret. */
    private suspend fun postToTokenEndpoint(client: OAuthClientConfig, params: Map<String, String>): Pair<Int, String> {
        val form = ParametersBuilder()
        for ((name, value) in params) form.append(name, value)
        val useBasic = client.clientAuth == "basic"
        if (!useBasic) {
            form.append("client_id", client.clientId)
            form.append("client_secret", client.clientSecret)
        }
        val body: Parameters = form.build()
        val response = http.post(client.tokenUrl) {
            header(HttpHeaders.Accept, "application/json")
            if (useBasic) {
                val pair = client.clientId + ":" + client.clientSecret
                header(HttpHeaders.Authorization, "Basic " + Base64.getEncoder().encodeToString(pair.encodeToByteArray()))
            }
            setBody(FormDataContent(body))
        }
        return Pair(response.status.value, response.bodyAsText())
    }

    private fun callbackUrl(provider: String) = "$publicUrl/oauth/$provider/callback"

    /** Must be called with [lock] held. */
    private fun forgetExpired() {
        val now = clock.now()
        flows.entries.removeAll { entry -> entry.value.expiresAt <= now }
    }

    /** What a token endpoint answers (RFC 6749 section 5). */
    @Serializable
    private data class Tokens(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("error") val error: String? = null,
    )

    private fun parseTokens(body: String): Tokens? {
        try {
            return SekundJson.decodeFromString(Tokens.serializer(), body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
    }

    companion object {
        /** PKCE: the challenge sent with the authorize request is the SHA-256 of the verifier. */
        fun challengeOf(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
