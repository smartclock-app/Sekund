package uk.dnpk.sekund.runtime.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.auth.AccountUnavailableException
import uk.dnpk.sekund.sdk.auth.CustomAuthHandler
import uk.dnpk.sekund.sdk.auth.CustomAuthRejectedException
import uk.dnpk.sekund.sdk.auth.CustomSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private const val COOKIE_PROVIDER = """
{
  "id": "cookies",
  "name": "Cookie Service",
  "methods": [{ "type": "custom", "handler": "cookie-swap", "label": "Refresh token" }],
  "baseUrls": ["https://api.cookies.example/"]
}
"""

/**
 * A handler like Amazon's: swaps the secret for a numbered session cookie. The secret "revoked" is refused.
 * Each refresh makes a new cookie (session-1, session-2...), so the tests can see when it refreshed.
 */
private class CookieSwapHandler : CustomAuthHandler {
    var refreshes = 0

    override suspend fun refresh(http: HttpClient, fields: Map<String, String>, session: CustomSession): CustomSession {
        if (session.secret == "revoked") throw CustomAuthRejectedException("Cookie Service refused the token")
        refreshes++
        return session.copy(values = mapOf("cookie" to "session-$refreshes"))
    }

    override fun sign(request: HttpRequestBuilder, fields: Map<String, String>, session: CustomSession) {
        request.headers.set("Cookie", session.values["cookie"] ?: "")
    }
}

class CustomAuthTest {
    @Test
    fun `connecting makes a session and requests carry it`() = runTest {
        val handler = CookieSwapHandler()
        val seenCookies = mutableListOf<String?>()
        val rig = AuthTestRig(backgroundScope, mapOf("cookie-swap" to handler)) { request ->
            seenCookies.add(request.headers["Cookie"])
            respond("ok")
        }
        rig.addProvider(COOKIE_PROVIDER)

        val account = rig.registry.connectWithKey("cookies", 0, emptyMap(), " my-refresh-token ")
        SignedInClient.create(rig.registry, account.id).get("https://api.cookies.example/me")

        assertEquals(listOf<String?>("session-1"), seenCookies)
        assertEquals(1, handler.refreshes)
    }

    @Test
    fun `a 401 makes a new session once and retries`() = runTest {
        val handler = CookieSwapHandler()
        val rig = AuthTestRig(backgroundScope, mapOf("cookie-swap" to handler)) { request ->
            if (request.headers["Cookie"] == "session-1") respond("expired", HttpStatusCode.Unauthorized) else respond("ok")
        }
        rig.addProvider(COOKIE_PROVIDER)
        val account = rig.registry.connectWithKey("cookies", 0, emptyMap(), "token")

        val answer = SignedInClient.create(rig.registry, account.id).get("https://api.cookies.example/me")

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(2, handler.refreshes)
    }

    @Test
    fun `a refused secret fails while connecting`() = runTest {
        val rig = AuthTestRig(backgroundScope, mapOf("cookie-swap" to CookieSwapHandler())) { respond("ok") }
        rig.addProvider(COOKIE_PROVIDER)

        assertFailsWith<CustomAuthRejectedException> { rig.registry.connectWithKey("cookies", 0, emptyMap(), "revoked") }
    }

    @Test
    fun `an imported account without a session gets one, and a refused one must reconnect`() = runTest {
        val rig = AuthTestRig(backgroundScope, mapOf("cookie-swap" to CookieSwapHandler())) { respond("ok") }
        rig.addProvider(COOKIE_PROVIDER)
        val good = rig.registry.importAccount("acc_good", "cookies", 0, "Good", emptyMap(), Credential(refreshToken = "token"))
        val bad = rig.registry.importAccount("acc_bad", "cookies", 0, "Bad", emptyMap(), Credential(refreshToken = "revoked"))

        val signed = rig.registry.credentialForRequest(good.id)
        assertEquals(mapOf("cookie" to "session-1"), signed.credential.extra)

        assertFailsWith<AccountUnavailableException> { rig.registry.credentialForRequest(bad.id) }
        assertNotNull(rig.registry.account(bad.id)?.needsReauth)
    }
}
